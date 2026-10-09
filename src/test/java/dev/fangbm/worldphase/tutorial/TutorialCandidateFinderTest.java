package dev.fangbm.worldphase.tutorial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.levelgen.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TutorialCandidateFinderTest {
    private MinecraftServer server;
    private TerrainSampleService sampling;
    private TutorialCandidateFinder finder;
    private final Map<PhaseId,ServerLevel> levels=new EnumMap<>(PhaseId.class);
    private final Map<PhaseId,NoiseBasedChunkGenerator> generators=new EnumMap<>(PhaseId.class);
    private final AtomicInteger heightReads=new AtomicInteger();

    @BeforeEach void setup() {
        server=mock(MinecraftServer.class); when(server.isSameThread()).thenReturn(true);
        sampling=new TerrainSampleService(server);
        for(var phase:PhaseId.values()) {
            var level=mock(ServerLevel.class); levels.put(phase,level);
            when(server.getLevel(PhaseWorldResolver.levelKey(phase))).thenReturn(level);
            when(level.getSeed()).thenReturn(42L); when(level.getMinY()).thenReturn(-64);
            when(level.getMaxY()).thenReturn(319); when(level.getSeaLevel()).thenReturn(63);
            var cache=mock(ServerChunkCache.class); var generator=mock(NoiseBasedChunkGenerator.class);
            generators.put(phase,generator); when(level.getChunkSource()).thenReturn(cache); when(cache.getGenerator()).thenReturn(generator);
            when(generator.getBaseHeight(anyInt(),anyInt(),eq(Heightmap.Types.OCEAN_FLOOR_WG),eq(level),any()))
                    .thenAnswer(c->{ heightReads.incrementAndGet(); return phase==PhaseId.FRACTURE?81:73; });
        }
        // Hand-constructed flat terrain: tests the search machinery, not copied generator mathematics.
        finder=new TutorialCandidateFinder(server,sampling);
    }
    private TutorialCandidateFinder.Result run(int batch) {
        var search=finder.begin(-17,16,TutorialSearchConfig.defaults());
        while(!search.done()) search.advance(batch);
        return search.result();
    }
    @Test void fixedSeedCentersAndRankingAreRepeatableAcrossBatchSizes() {
        var a=run(1); var b=run(4);
        assertEquals(a.candidates(),b.candidates()); assertEquals(a.heightSamples(),b.heightSamples());
        assertEquals(64,a.evaluatedCount()); assertTrue(a.heightSamples()<=64*18);
        assertEquals(42,a.seed()); assertEquals(-17,a.centerX()); assertEquals(16,a.centerZ());
    }
    @Test void fixedHashSequenceHasSmallDocumentedGoldenPrefix() {
        // Freeze the uniform-disk sequence/salts and signed floorMod behavior, not terrain heights.
        assertEquals(List.of(new TutorialCandidateFinder.Point(-328,-141),
                        new TutorialCandidateFinder.Point(-6,-151),new TutorialCandidateFinder.Point(-160,399)),
                TutorialCandidateFinder.positions(42,-17,16,new TutorialSearchConfig(512,8)).subList(0,3));
    }
    @ParameterizedTest @ValueSource(longs={0,1,-1,42,Long.MIN_VALUE,Long.MAX_VALUE})
    void positionsStayInDiskAreUniqueAndStable(long seed) {
        var config=new TutorialSearchConfig(512,256);
        var points=TutorialCandidateFinder.positions(seed,-17,16,config);
        assertEquals(256,points.size()); assertEquals(256,new HashSet<>(points).size());
        assertEquals(points,TutorialCandidateFinder.positions(seed,-17,16,config));
        assertTrue(points.stream().allMatch(p->StrictMath.hypot(p.x()+17.0,p.z()-16.0)<=512));
    }
    @Test void seedsAndCentersAffectDistributionWithStablePrefix() {
        var config=TutorialSearchConfig.defaults();
        var points=TutorialCandidateFinder.positions(42,0,0,config);
        assertNotEquals(points,TutorialCandidateFinder.positions(43,0,0,config));
        assertNotEquals(points,TutorialCandidateFinder.positions(42,1,0,config));
        assertEquals(points,TutorialCandidateFinder.positions(42,0,0,new TutorialSearchConfig(512,256)).subList(0,64));
    }
    @Test void tiesUseLexicographicXZAndDedupKeepsHighestScore() {
        var a=candidate(100,0,10); var b=candidate(-100,100,10); var c=candidate(-100,0,10);
        assertEquals(List.of(c,b,a),TutorialCandidateFinder.rank(List.of(a,b,c),512));
        assertEquals(List.of(candidate(1,1,20),candidate(65,1,9)),
                TutorialCandidateFinder.rank(List.of(candidate(0,0,10),candidate(1,1,20),candidate(65,1,9)),512));
    }
    @Test void declaredMinimumPrecedesHighScoringHazardsAndUnsuitableTerrain() {
        var useful=new TutorialCandidate(0,0,80,70,new TutorialScore(.5,.125,10,.375,0,1,10,0,0));
        var unsafe=new TutorialCandidate(100,0,80,70,new TutorialScore(1,.125,10,.875,0,1,100,0,60));
        var flat=candidate(200,0,50);
        assertEquals(List.of(useful,flat,unsafe),TutorialCandidateFinder.rank(List.of(unsafe,flat,useful),512));
    }
    @Test void returnedCandidatesMaintainMinimumSpacing() {
        var result=run(4).candidates();
        for(var a:result) for(var b:result) if(a!=b)
            assertTrue(StrictMath.hypot((double)a.x()-b.x(),(double)a.z()-b.z())>=64);
        assertThrows(UnsupportedOperationException.class,()->result.add(candidate(0,0,0)));
    }
    @ParameterizedTest @ValueSource(ints={-1,0,7,257,Integer.MAX_VALUE})
    void countsOutsideHardLimitsFail(int count) { assertThrows(IllegalArgumentException.class,()->new TutorialSearchConfig(512,count)); }
    @ParameterizedTest @ValueSource(ints={0,63,1025,Integer.MAX_VALUE})
    void radiiOutsideHardLimitsFail(int radius) { assertThrows(IllegalArgumentException.class,()->new TutorialSearchConfig(radius,64)); }
    @Test void batchLimitAndEarlyResultAreExplicit() {
        var search=finder.begin(0,0,TutorialSearchConfig.defaults());
        assertThrows(IllegalStateException.class,search::result);
        assertThrows(IllegalArgumentException.class,()->search.advance(0));
        assertThrows(IllegalArgumentException.class,()->search.advance(5));
        search.advance(1); assertEquals(0,search.evaluatedCount()); assertEquals(1,heightReads.get());
        for(int i=0;i<17;i++) search.advance(1);
        assertEquals(1,search.evaluatedCount()); assertEquals(18,heightReads.get());
    }
    @Test void missingLevelMismatchedSeedsAndOffThreadFailBeforeSampling() {
        when(server.isSameThread()).thenReturn(false);
        assertTrue(assertThrows(IllegalStateException.class,()->run(1)).getMessage().contains("server thread"));
        when(server.isSameThread()).thenReturn(true); when(levels.get(PhaseId.SMOOTH).getSeed()).thenReturn(99L);
        assertTrue(assertThrows(IllegalStateException.class,()->run(1)).getMessage().contains("seeds differ"));
        when(levels.get(PhaseId.SMOOTH).getSeed()).thenReturn(42L);
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.FRACTURE))).thenReturn(null);
        assertTrue(assertThrows(IllegalStateException.class,()->run(1)).getMessage().contains("worldphase:fracture"));
        assertEquals(0,heightReads.get());
    }
    @Test void seedChangeBetweenBatchesCannotMixWorkloads() {
        var search=finder.begin(0,0,TutorialSearchConfig.defaults()); search.advance(1);
        for(var level:levels.values()) when(level.getSeed()).thenReturn(43L);
        assertTrue(assertThrows(IllegalStateException.class,()->search.advance(1)).getMessage().contains("changed"));
        assertEquals(1,heightReads.get());
    }
    @Test void maximumSearchHasFiniteWorkAndOnlyCallsHeightService() {
        var search=finder.begin(0,0,new TutorialSearchConfig(512,256));
        for(int batch=0;batch<1152;batch++) search.advance(4);
        assertTrue(search.done()); assertEquals(256,search.result().evaluatedCount());
        assertTrue(search.result().heightSamples()<=4608);
        for(var generator:generators.values()) for(var invocation:mockingDetails(generator).getInvocations())
            assertEquals("getBaseHeight",invocation.getMethod().getName());
    }
    @Test void worldCoordinateOverflowIsRejected() {
        assertThrows(IllegalArgumentException.class,()->finder.begin(Integer.MAX_VALUE,0,TutorialSearchConfig.defaults()));
        assertThrows(IllegalArgumentException.class,()->finder.score(0,Integer.MIN_VALUE,0,0,TutorialSearchConfig.defaults()));
    }
    private static TutorialCandidate candidate(int x,int z,double total) {
        return new TutorialCandidate(x,z,80,70,new TutorialScore(0,0,10,0,0,total,0,0,0));
    }
}
