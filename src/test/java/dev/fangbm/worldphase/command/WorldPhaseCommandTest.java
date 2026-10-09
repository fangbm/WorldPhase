package dev.fangbm.worldphase.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Lifecycle;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunction;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Actual dispatcher/permission/source APIs and Vanilla collision shapes; mocked server shells. */
class WorldPhaseCommandTest {
    private MinecraftServer server;
    private ServerPlayer player;
    private CommandSource output;
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private final List<String> messages=new ArrayList<>();
    private final Map<PhaseId,ServerLevel> levels=new EnumMap<>(PhaseId.class);
    private final Map<PhaseId,ServerChunkCache> caches=new EnumMap<>(PhaseId.class);
    private final Map<PhaseId,BlockState> floors=new EnumMap<>(PhaseId.class);
    private final AtomicReference<ServerLevel> current=new AtomicReference<>();
    private final AtomicReference<Vec3> position=new AtomicReference<>();

    @BeforeEach void setup() {
        server=mock(MinecraftServer.class); when(server.isSameThread()).thenReturn(true);
        var registry=new MappedRegistry<DensityFunction>(Registries.DENSITY_FUNCTION,Lifecycle.stable());
        for(PhaseId p:PhaseId.values()) {
            var algorithm=PhaseNoiseDensityFunction.Algorithm.valueOf(p.name());
            registry.register(ResourceKey.create(Registries.DENSITY_FUNCTION,Identifier.fromNamespaceAndPath("worldphase",WorldPhaseCommand.token(p)+"_noise")),
                    new PhaseNoiseDensityFunction(new PhaseNoiseDensityFunction.Config(algorithm,.004,1,2,.008,32)),RegistrationInfo.BUILT_IN);
        }
        var access=new RegistryAccess.ImmutableRegistryAccess(List.of(registry)).freeze();
        int height=70;
        for(PhaseId p:PhaseId.values()) {
            var level=mock(ServerLevel.class); var cache=mock(ServerChunkCache.class); var getter=mock(BlockGetter.class);
            when(level.getServer()).thenReturn(server); when(level.dimension()).thenReturn(PhaseWorldResolver.levelKey(p));
            when(server.getLevel(PhaseWorldResolver.levelKey(p))).thenReturn(level);
            when(level.getSeed()).thenReturn(123456789L); when(level.getMinY()).thenReturn(-64); when(level.getMaxY()).thenReturn(319);
            when(level.getSeaLevel()).thenReturn(63);
            when(level.getWorldBorder()).thenReturn(new WorldBorder()); when(level.getChunkSource()).thenReturn(cache);
            when(level.registryAccess()).thenReturn(access); when(level.getChunkForCollisions(anyInt(),anyInt())).thenReturn(getter);
            when(cache.getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(true))).thenReturn(mock(LevelChunk.class));
            floors.put(p,Blocks.STONE.defaultBlockState());
            org.mockito.stubbing.Answer<BlockState> state=call -> ((BlockPos)call.getArgument(0)).getY()==80?floors.get(p):Blocks.AIR.defaultBlockState();
            when(level.getBlockState(any())).thenAnswer(state); when(getter.getBlockState(any())).thenAnswer(state);
            when(level.noEntityCollision(any(),any())).thenReturn(true); when(level.noCollision(any(),any(AABB.class))).thenReturn(true);
            when(level.getHeight(eq(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES),anyInt(),anyInt())).thenReturn(81);
            var generator=mock(NoiseBasedChunkGenerator.class); when(cache.getGenerator()).thenReturn(generator);
            when(generator.getBaseHeight(anyInt(),anyInt(),eq(Heightmap.Types.OCEAN_FLOOR_WG),eq(level),any())).thenReturn(height++);
            levels.put(p,level); caches.put(p,cache);
        }
        when(server.getLevel(Level.OVERWORLD)).thenReturn(levels.get(PhaseId.SMOOTH));
        when(levels.get(PhaseId.SMOOTH).getRespawnData()).thenReturn(net.minecraft.world.level.storage.LevelData.RespawnData.of(Level.OVERWORLD,new BlockPos(32,70,-32),0,0));
        player=mock(ServerPlayer.class); current.set(levels.get(PhaseId.SMOOTH)); position.set(new Vec3(-16.125,81,15.9375));
        when(player.level()).thenAnswer(call -> current.get()); when(player.position()).thenAnswer(call -> position.get());
        when(player.isAlive()).thenReturn(true); when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getYRot()).thenReturn(123.25f); when(player.getXRot()).thenReturn(-37.5f);
        setPosition(position.get());
        when(player.teleport(any(TeleportTransition.class))).thenAnswer(call -> {
            TeleportTransition t=call.getArgument(0); current.set(t.newLevel()); setPosition(t.position()); return player;
        });
        output=mock(CommandSource.class); when(output.acceptsSuccess()).thenReturn(true); when(output.acceptsFailure()).thenReturn(true);
        doAnswer(call -> { messages.add(((Component)call.getArgument(0)).getString()); return null; }).when(output).sendSystemMessage(any());
        dispatcher=new CommandDispatcher<>(); WorldPhaseCommand.register(dispatcher);
    }
    private void setPosition(Vec3 pos) {
        position.set(pos); player.setBoundingBox(new AABB(pos.x-.3,pos.y,pos.z-.3,pos.x+.3,pos.y+1.8,pos.z+.3));
    }
    private CommandSourceStack source(PermissionSet permissions, boolean isPlayer) {
        return new CommandSourceStack(output,position.get(),Vec2.ZERO,current.get(),permissions,"Probe",Component.literal("Probe"),server,isPlayer?player:null);
    }
    private int run(String command) throws CommandSyntaxException { messages.clear(); return dispatcher.execute(command,source(LevelBasedPermissionSet.GAMEMASTER,true)); }
    private String text() { return String.join("\n",messages); }

    @AfterEach void cleanupTutorialSearch() { TutorialCommand.onStopped(new net.neoforged.neoforge.event.server.ServerStoppedEvent(server)); }

    @ParameterizedTest @ValueSource(strings={"wp tutorial score","wp tutorial find","wp tutorial find 256"})
    void tutorialRequiresOp(String command) {
        assertThrows(CommandSyntaxException.class,()->dispatcher.execute(command,source(PermissionSet.NO_PERMISSIONS,true)));
    }
    @ParameterizedTest @ValueSource(strings={"wp tutorial find 7","wp tutorial find 257","wp tutorial find -1","wp tutorial find unknown"})
    void tutorialCountBoundsAreRejectedByParser(String command) { assertThrows(CommandSyntaxException.class,()->run(command)); }
    @Test void tutorialScoreShowsEverySignalAndDoesNotTeleportOrRequestChunks() throws Exception {
        assertEquals(1,run("wp tutorial score"));
        assertFalse(text().contains("#1")); for(int tick=0;tick<33;tick++) TutorialCommand.advance(server);
        for(String field:List.of("seed=123456789","(32,-32)","x=-17 z=15","slope F=","heightContrast=","height=","slope=","distancePenalty=","hazardPenalty=","total=","usefulHeuristic=","heightSamples=66","corridor axis=","risk=","UNVERIFIED"))
            assertTrue(text().contains(field),field);
        verify(player,never()).teleport(any());
        for(var cache:caches.values()) verify(cache,never()).getChunk(anyInt(),anyInt(),any(),anyBoolean());
    }
    @Test void tutorialScoreRejectsConsoleButFindUsesSharedSpawnAndBatches() throws Exception {
        var console=source(LevelBasedPermissionSet.OWNER,false);
        assertSame(CommandSourceStack.ERROR_NOT_PLAYER,
                assertThrows(CommandSyntaxException.class,()->dispatcher.execute("wp tutorial score",console)).getType());
        assertEquals(1,dispatcher.execute("wp tutorial find 8",console));
        assertTrue(text().contains("(32,-32)")); assertFalse(text().contains("#1"));
        assertEquals(0,dispatcher.execute("wp tutorial find 8",console)); assertTrue(text().contains("already active"));
        for(int i=0;i<71;i++) TutorialCommand.advance(server);
        assertFalse(text().contains("#1")); for(int i=71;i<264;i++) TutorialCommand.advance(server);
        assertTrue(text().contains("evaluated=8")); assertTrue(text().contains("heightSamples=")); assertTrue(text().contains("#1"));
        assertTrue(text().contains("maxBatchMs=")); verify(player,never()).teleport(any());
        for(var cache:caches.values()) verify(cache,never()).getChunk(anyInt(),anyInt(),any(),anyBoolean());
    }
    @Test void tutorialDefaultAndStopCancellationLeaveNoPersistentSearch() throws Exception {
        assertEquals(1,run("wp tutorial find")); assertTrue(text().contains("candidates=64"));
        cleanupTutorialSearch(); TutorialCommand.advance(server); assertFalse(text().contains("#1"));
        assertEquals(1,run("wp tutorial find 8"));
        for(int i=0;i<264;i++) TutorialCommand.advance(server);
        assertTrue(text().contains("evaluated=8"));
    }
    @Test void tutorialMissingWorldAndInvalidSpawnAreClearFailures() throws Exception {
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.FRACTURE))).thenReturn(null);
        assertEquals(0,run("wp tutorial find")); assertTrue(text().contains("worldphase:fracture"));
        when(levels.get(PhaseId.SMOOTH).getRespawnData()).thenReturn(net.minecraft.world.level.storage.LevelData.RespawnData.of(Level.NETHER,BlockPos.ZERO,0,0));
        assertEquals(0,run("wp tutorial score")); assertTrue(text().contains("Overworld shared spawn"));
    }
    @Test void tutorialBatchFailureReleasesSearchSlotForRecovery() throws Exception {
        assertEquals(1,run("wp tutorial find 8"));
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.FRACTURE))).thenReturn(null);
        TutorialCommand.advance(server);
        assertTrue(text().contains("tutorial search failed")); assertTrue(text().contains("worldphase:fracture"));
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.FRACTURE))).thenReturn(levels.get(PhaseId.FRACTURE));
        assertEquals(1,run("wp tutorial find 8"));
        for(int tick=0;tick<264;tick++) TutorialCommand.advance(server);
        assertTrue(text().contains("evaluated=8")); verify(player,never()).teleport(any());
    }

    @Test void treeHasAllRequiredPathsAndNoAmbiguities() throws Exception {
        var ambiguities=new ArrayList<String>(); dispatcher.findAmbiguities((a,b,c,inputs)->ambiguities.add(inputs.toString()));
        assertTrue(ambiguities.isEmpty());
        for(String cmd:List.of("wp phase list","wp phase current","wp seedcheck","wp compare all","wp sample ridge","wp compare smooth cell","wp safepos cell")) {
            assertTrue(run(cmd)>0,cmd);
        }
    }
    @ParameterizedTest @ValueSource(strings={"wp phase list","wp seedcheck","wp phase goto ridge --surface","wp compare all"})
    void nonOpIsDenied(String cmd) {
        assertThrows(CommandSyntaxException.class,()->dispatcher.execute(cmd,source(PermissionSet.NO_PERMISSIONS,true)));
        verify(player,never()).teleport(any());
    }
    @Test void phaseTokensAreCompletedAndUnknownHasNoFallback() throws Exception {
        var src=source(LevelBasedPermissionSet.GAMEMASTER,true);
        var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("wp phase goto ",src)).join();
        assertEquals(Set.of("smooth","ridge","cell","fracture"),new HashSet<>(suggestions.getList().stream().map(s -> s.getText()).toList()));
        assertThrows(CommandSyntaxException.class,()->run("wp phase goto unknown"));
        assertThrows(CommandSyntaxException.class,()->run("wp compare all ridge"));
        verify(player,never()).teleport(any());
    }
    @ParameterizedTest @ValueSource(strings={"wp phase goto ridge","wp phase goto ridge --surface","wp phase cycle","wp sample ridge","wp compare all","wp compare smooth ridge","wp safepos cell"})
    void playerOnlyCommandsRejectConsoleCleanly(String cmd) {
        var error=assertThrows(CommandSyntaxException.class,()->dispatcher.execute(cmd,source(LevelBasedPermissionSet.OWNER,false)));
        assertSame(CommandSourceStack.ERROR_NOT_PLAYER,error.getType());
    }
    @Test void authorizedConsoleCanListCurrentAndCheckSeeds() throws Exception {
        var src=source(LevelBasedPermissionSet.OWNER,false);
        assertEquals(4,dispatcher.execute("wp phase list",src)); assertEquals(1,dispatcher.execute("wp phase current",src));
        assertEquals(1,dispatcher.execute("wp seedcheck",src)); assertTrue(text().contains("seedcheck PASS"));
    }
    @ParameterizedTest @ValueSource(strings={"minecraft:overworld","minecraft:the_nether","minecraft:the_end"})
    void vanillaCurrentIsExplicitNonPhase(String key) throws Exception {
        when(current.get().dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,Identifier.parse(key)));
        run("wp phase current"); assertTrue(text().contains("NON_PHASE | "+key));
    }
    @Test void phaseCurrentAndListIncludeKeysAndLoadedState() throws Exception {
        run("wp phase current"); assertTrue(text().contains("current=smooth | worldphase:smooth"));
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.CELL))).thenReturn(null);
        run("wp phase list"); assertTrue(text().contains("cell | worldphase:cell | MISSING")); assertEquals(4,messages.size());
    }
    @Test void gotoAndContinuousCycleUseFixedOrderWithoutChangingXZOrRotation() throws Exception {
        current.set(levels.get(PhaseId.FRACTURE)); var original=position.get();
        for(int i=0;i<2;i++) for(PhaseId p:List.of(PhaseId.SMOOTH,PhaseId.RIDGE,PhaseId.CELL,PhaseId.FRACTURE)) {
            assertEquals(1,run("wp phase cycle")); assertSame(levels.get(p),current.get()); assertEquals(original,position.get());
            assertTrue(text().contains("BOUNDED_LANDING SUCCESS"));
        }
        verify(player,times(8)).teleport(argThat(t -> t.yRot()==123.25f && t.xRot()==-37.5f));
    }
    @Test void unsafeCycleReportsAndLeavesSourceUntouched() throws Exception {
        floors.put(PhaseId.RIDGE,Blocks.AIR.defaultBlockState()); var original=position.get();
        assertEquals(0,run("wp phase cycle")); assertTrue(text().contains("NO_SAFE_POSITION"));
        assertSame(levels.get(PhaseId.SMOOTH),current.get()); assertEquals(original,position.get()); verify(player,never()).teleport(any());
    }
    @Test void gotoReportsSamePhaseMissingAndNativeVeto() throws Exception {
        run("wp phase goto smooth"); assertTrue(text().contains("SAME_PHASE"));
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.CELL))).thenReturn(null);
        run("wp phase goto cell"); assertTrue(text().contains("TARGET_LEVEL_MISSING"));
        when(player.teleport(any())).thenReturn(null); assertEquals(0,run("wp phase goto ridge")); assertTrue(text().contains("ERROR:"));
    }
    @ParameterizedTest @EnumSource(TransferResult.class)
    void everyTransferResultHasExplicitReadableFeedback(TransferResult result) {
        assertTrue(WorldPhaseCommand.describe(result).startsWith(result+": ")); assertTrue(WorldPhaseCommand.describe(result).length()>result.name().length()+5);
    }
    @Test void compareAllUsesFourActualServiceResultsAndFloorCoordinates() throws Exception {
        assertEquals(4,run("wp compare all")); assertTrue(text().contains("block x=-17 z=15"));
        var sampling=new TerrainSampleService(server);
        for(PhaseId p:PhaseId.values()) {
            var expected=sampling.sample(p,-17,15);
            assertTrue(text().contains(WorldPhaseCommand.token(p))); assertTrue(text().contains("baseHeight="+expected.baseHeight()));
            assertTrue(text().contains(String.format(Locale.ROOT,"rawNoise=%+.6f",expected.rawNoise())));
        }
        for(var cache:caches.values()) verify(cache,never()).getChunk(anyInt(),anyInt(),any(),anyBoolean());
    }
    @Test void sampleLabelsConfiguredSignalAndComparisonIsSecondMinusFirst() throws Exception {
        run("wp sample ridge"); assertTrue(text().contains("not final density")); assertTrue(text().contains("ridge"));
        var expected=new TerrainSampleService(server).compare(PhaseId.CELL,PhaseId.SMOOTH,-17,15);
        run("wp compare cell smooth"); assertTrue(text().contains("delta smooth - cell: height="+String.format(Locale.ROOT,"%+d",expected.heightDelta())));
        assertTrue(text().contains(String.format(Locale.ROOT,"rawNoise=%+.6f",expected.noiseDelta())));
    }
    @Test void mismatchedAndMissingSeedsAreActualLevelFailures() throws Exception {
        run("wp seedcheck"); assertTrue(text().contains("seedcheck PASS"));
        when(levels.get(PhaseId.CELL).getSeed()).thenReturn(99L); assertEquals(0,run("wp seedcheck"));
        assertTrue(text().contains("cell seed=99 MISMATCH")); assertTrue(text().contains("seedcheck FAIL"));
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.CELL))).thenReturn(null);
        assertEquals(0,run("wp seedcheck")); assertTrue(text().contains("cell MISSING"));
        assertEquals(0,run("wp compare all")); assertTrue(text().contains("Sampling unavailable"));
    }
    @Test void safeposPreparesRealChunksWithoutTeleportAndUsesSameLandingRules() throws Exception {
        var original=position.get(); assertEquals(1,run("wp safepos cell"));
        assertTrue(text().contains("exactSafe=true")); assertTrue(text().contains("deltaY=+0.000000"));
        assertTrue(text().contains("may load/generate")); assertEquals(original,position.get());
        verify(player,never()).teleport(any()); verify(caches.get(PhaseId.CELL),times(4)).getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(true));
        floors.put(PhaseId.CELL,Blocks.MAGMA_BLOCK.defaultBlockState());
        assertEquals(0,run("wp safepos cell")); assertTrue(text().contains("NO_SAFE_POSITION"));
    }
    @Test void explicitSurfaceCanMoveFarVerticallyButDefaultStillRejects() throws Exception {
        var original=new Vec3(-16.125,200.375,15.9375); setPosition(original);
        assertEquals(0,run("wp phase goto ridge")); assertEquals(original,position.get()); assertTrue(text().contains("NO_SAFE_POSITION"));
        assertEquals(1,run("wp phase goto ridge --surface")); assertEquals(new Vec3(original.x,81,original.z),position.get());
        assertTrue(text().contains("DEV_SURFACE SUCCESS"));
    }
    @Test void surfaceNeverAcceptsHazardousFloor() throws Exception {
        floors.put(PhaseId.RIDGE,Blocks.MAGMA_BLOCK.defaultBlockState());
        assertEquals(0,run("wp phase goto ridge --surface")); assertTrue(text().contains("NO_SAFE_POSITION")); verify(player,never()).teleport(any());
    }
    @Test void explicitSurfaceCanRepositionWithinSamePhaseWhileDefaultRemainsNoOp() throws Exception {
        setPosition(new Vec3(-16.125,200.375,15.9375));
        assertEquals(1,run("wp phase goto smooth")); assertTrue(text().contains("SAME_PHASE"));
        assertEquals(1,run("wp phase goto smooth --surface")); assertEquals(81,position.get().y);
        assertSame(levels.get(PhaseId.SMOOTH),current.get());
    }
    @Test void vanillaDiagnosticWorksButTransferAndCycleRemainInvalidSource() throws Exception {
        when(current.get().dimension()).thenReturn(Level.OVERWORLD); when(server.getLevel(Level.OVERWORLD)).thenReturn(current.get());
        assertEquals(1,run("wp safepos ridge")); assertTrue(text().contains("exactSafe=true"));
        assertEquals(0,run("wp phase goto ridge --surface")); assertTrue(text().contains("INVALID_SOURCE"));
        assertEquals(0,run("wp phase cycle")); assertTrue(text().contains("INVALID_SOURCE")); verify(player,never()).teleport(any());
    }
}
