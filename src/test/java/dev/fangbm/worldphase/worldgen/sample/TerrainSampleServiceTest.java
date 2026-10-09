package dev.fangbm.worldphase.worldgen.sample;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.tutorial.*;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunction;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

/** Real preset codecs, registry entries, generators and seeded RandomStates; only server shells are mocked. */
class TerrainSampleServiceTest {
    private static final long SEED = 123456789L;
    // WPH-007 dispersed coordinates plus a far, negative coordinate; no exact height snapshots.
    private static final int[][] POINTS = {{0,0},{256,256},{-512,768},{1024,-1024},{2048,2048},
            {-2048,-2048},{4096,0},{0,4096},{-4096,2048},{8192,-4096},{-1000000,2000000}};
    private record World(NoiseBasedChunkGenerator generator, RandomState state) {}
    private static final Map<PhaseId, World> WORLDS = new EnumMap<>(PhaseId.class);
    private static HolderLookup.Provider vanilla;
    private MinecraftServer server;
    private TerrainSampleService service;
    private final Map<PhaseId, ServerLevel> levels = new EnumMap<>(PhaseId.class);
    private final Map<PhaseId, ServerChunkCache> chunks = new EnumMap<>(PhaseId.class);
    private RegistryAccess registry;

    @BeforeAll static void decodeWorlds() {
        vanilla = VanillaRegistries.createLookup();
        var ops = RegistryOps.create(JsonOps.INSTANCE, vanilla);
        for (PhaseId phase : PhaseId.values()) {
            var stem = LevelStem.CODEC.parse(ops, resolve(resource("/data/worldphase/dimension/" + name(phase) + ".json"))).getOrThrow();
            var generator = (NoiseBasedChunkGenerator) stem.generator();
            WORLDS.put(phase, new World(generator, RandomState.create(generator.generatorSettings().value(),
                    vanilla.lookupOrThrow(Registries.NOISE), SEED)));
        }
    }

    @BeforeEach void setupServerShells() {
        registry = densityRegistry(null, null);
        server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        for (PhaseId phase : PhaseId.values()) {
            var level = mock(ServerLevel.class);
            var cache = mock(ServerChunkCache.class);
            when(level.dimension()).thenReturn(PhaseWorldResolver.levelKey(phase));
            when(level.getSeed()).thenReturn(SEED);
            when(level.getMinY()).thenReturn(-64);
            when(level.getHeight()).thenReturn(384);
            when(level.getMaxY()).thenReturn(319);
            when(level.registryAccess()).thenReturn(registry);
            when(level.getChunkSource()).thenReturn(cache);
            when(cache.getGenerator()).thenReturn(WORLDS.get(phase).generator());
            when(cache.randomState()).thenReturn(WORLDS.get(phase).state());
            when(server.getLevel(PhaseWorldResolver.levelKey(phase))).thenReturn(level);
            levels.put(phase, level);
            chunks.put(phase, cache);
        }
        service = new TerrainSampleService(server);
    }

    @AfterEach void neverRequestsChunksOrLiveHeightmaps() {
        for (var level : levels.values()) for (var call : mockingDetails(level).getInvocations()) {
            String method = call.getMethod().getName();
            assertFalse(method.startsWith("getChunk") && !method.equals("getChunkSource"), method);
            assertFalse(method.equals("getHeight") && call.getArguments().length > 0, "Live heightmap access");
        }
        for (var cache : chunks.values()) for (var call : mockingDetails(cache).getInvocations()) {
            assertTrue(Set.of("getGenerator", "randomState").contains(call.getMethod().getName()),
                    "Only generator/state access is allowed: " + call);
        }
    }

    @ParameterizedTest @EnumSource(PhaseId.class)
    void heightsAndSignalsMatchIndependentGeneratorAndRegistryCalls(PhaseId phase) {
        var world = WORLDS.get(phase);
        var template = (PhaseNoiseDensityFunction) registry.lookupOrThrow(Registries.DENSITY_FUNCTION).getOrThrow(key(phase)).value();
        var level = levels.get(phase);
        for (int[] p : POINTS) {
            int height = world.generator().getBaseHeight(p[0], p[1], Heightmap.Types.OCEAN_FLOOR_WG, level, world.state()) - 1;
            double noise = template.withSeed(level.getSeed()).compute(new DensityFunction.SinglePointContext(p[0], 0, p[1]));
            var expected = new TerrainSample(phase, p[0], p[1], height, noise);
            assertEquals(height, service.sampleHeight(phase, p[0], p[1]));
            assertEquals(noise, service.sampleNoise(phase, p[0], p[1]));
            assertEquals(expected, service.sample(phase, p[0], p[1]));
            assertTrue(Double.isFinite(noise));
        }
        // Registry templates remain unbound; the service does not mutate shared data.
        assertThrows(IllegalStateException.class, () -> template.compute(new DensityFunction.SinglePointContext(0,0,0)));
    }

    @ParameterizedTest @EnumSource(PhaseId.class)
    void repeatsAndReverseSamplingOrderAreExactlyDeterministic(PhaseId phase) {
        var first = Arrays.stream(POINTS).map(p -> service.sample(phase, p[0], p[1])).toList();
        for (int i = POINTS.length - 1; i >= 0; i--) {
            var p = POINTS[i];
            assertEquals(first.get(i), service.sample(phase, p[0], p[1]));
            assertEquals(first.get(i), service.sample(phase, p[0], p[1]));
        }
    }

    @ParameterizedTest @CsvSource({"SMOOTH,RIDGE", "SMOOTH,CELL", "SMOOTH,FRACTURE", "RIDGE,CELL"})
    void comparisonsKeepSamplesAndSignedDeltasWithObservableDifferences(PhaseId a, PhaseId b) {
        int different = 0;
        for (int[] p : POINTS) {
            var result = service.compare(a, b, p[0], p[1]);
            var first = service.sample(a, p[0], p[1]);
            var second = service.sample(b, p[0], p[1]);
            assertEquals(first, result.firstSample());
            assertEquals(second, result.secondSample());
            assertEquals(second.baseHeight() - first.baseHeight(), result.heightDelta());
            assertEquals(second.rawNoise() - first.rawNoise(), result.noiseDelta());
            if (result.heightDelta() != 0) different++;
        }
        assertTrue(different >= 7, "Broad Phase differences, not a particular final height");
        var same = service.compare(a, a, -512, 768);
        assertEquals(0, same.heightDelta());
        assertEquals(0.0, same.noiseDelta());
    }

    @ParameterizedTest @EnumSource(PhaseId.class)
    void missingPhaseFailsWithoutFallbackEvenWhenSamplingAnotherPhase(PhaseId missing) {
        when(server.getLevel(PhaseWorldResolver.levelKey(missing))).thenReturn(null);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.sample(PhaseId.SMOOTH, 0, 0))
                .getMessage().contains(PhaseWorldResolver.levelKey(missing).identifier().toString()));
    }

    @ParameterizedTest @EnumSource(PhaseId.class)
    void inconsistentRawSeedsFailBeforeSampling(PhaseId changed) {
        when(levels.get(changed).getSeed()).thenReturn(SEED + 99);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.compare(PhaseId.SMOOTH, PhaseId.RIDGE, 0, 0))
                .getMessage().contains("Phase seeds differ"));
        for (var cache : chunks.values()) verifyNoInteractions(cache);
    }

    @Test void missingNoiseEntryIsExplicit() {
        when(levels.get(PhaseId.CELL).registryAccess()).thenReturn(densityRegistry(PhaseId.CELL, null));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.sampleNoise(PhaseId.CELL, 0, 0))
                .getMessage().contains("Missing Phase noise density function: worldphase:cell_noise"));
    }

    @Test void wrongDensityTypeIsExplicit() {
        when(levels.get(PhaseId.CELL).registryAccess()).thenReturn(densityRegistry(PhaseId.CELL, DensityFunctions.constant(0)));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.sampleNoise(PhaseId.CELL, 0, 0))
                .getMessage().contains("Expected worldphase:phase_noise"));
    }

    @Test void wrongGeneratorIsExplicit() {
        when(chunks.get(PhaseId.CELL).getGenerator()).thenReturn(null);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.sampleHeight(PhaseId.CELL, 0, 0))
                .getMessage().contains("NoiseBasedChunkGenerator"));
    }

    @Test void offServerThreadFailsBeforeReadingWorlds() {
        when(server.isSameThread()).thenReturn(false);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.sample(PhaseId.SMOOTH, 0, 0))
                .getMessage().contains("server thread"));
        verify(server, never()).getLevel(any());
    }

    @Test void readsCurrentLevelRegistryAndSeedInsteadOfCachingOldInstances() {
        double first = service.sampleNoise(PhaseId.SMOOTH, 256, 256);
        // New server world state: all four seeds change, without constructing another service.
        for (var level : levels.values()) when(level.getSeed()).thenReturn(SEED + 77);
        var template = (PhaseNoiseDensityFunction) registry.lookupOrThrow(Registries.DENSITY_FUNCTION).getOrThrow(key(PhaseId.SMOOTH)).value();
        double expected = template.withSeed(SEED + 77).compute(new DensityFunction.SinglePointContext(256,0,256));
        assertNotEquals(first, expected);
        assertEquals(expected, service.sampleNoise(PhaseId.SMOOTH, 256, 256));
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.SMOOTH))).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.sampleNoise(PhaseId.SMOOTH, 256, 256));
    }

    @Test void changingDataDrivenConfigChangesSignalWithoutServiceChanges() {
        var json = resource("/data/worldphase/worldgen/density_function/smooth_noise.json").getAsJsonObject();
        double first = service.sampleNoise(PhaseId.SMOOTH, 256, 256);
        json.addProperty("amplitude", json.get("amplitude").getAsDouble() * 2);
        var replacement = DensityFunction.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, vanilla), json).getOrThrow();
        when(levels.get(PhaseId.SMOOTH).registryAccess()).thenReturn(densityRegistry(PhaseId.SMOOTH, replacement));
        assertEquals(first * 2, service.sampleNoise(PhaseId.SMOOTH, 256, 256));
    }

    @Test void heightReadsTheCurrentRandomStateOnEachCall() {
        var world = WORLDS.get(PhaseId.SMOOTH);
        var before = Arrays.stream(POINTS).mapToInt(p -> service.sampleHeight(PhaseId.SMOOTH, p[0], p[1])).toArray();
        for (var level : levels.values()) when(level.getSeed()).thenReturn(SEED + 77);
        var state = RandomState.create(world.generator().generatorSettings().value(), vanilla.lookupOrThrow(Registries.NOISE), SEED + 77);
        when(chunks.get(PhaseId.SMOOTH).randomState()).thenReturn(state);
        int differences = 0;
        for (int i = 0; i < POINTS.length; i++) {
            var p = POINTS[i];
            int expected = world.generator().getBaseHeight(p[0], p[1], Heightmap.Types.OCEAN_FLOOR_WG, levels.get(PhaseId.SMOOTH), state) - 1;
            assertEquals(expected, service.sampleHeight(PhaseId.SMOOTH, p[0], p[1]));
            if (expected != before[i]) differences++;
        }
        assertTrue(differences > 0);
    }

    @Test void baseHeightExcludesTheWaterSurface() {
        var world = WORLDS.get(PhaseId.SMOOTH);
        var level = levels.get(PhaseId.SMOOTH);
        int floor = service.sampleHeight(PhaseId.SMOOTH, 0, 0);
        var column = world.generator().getBaseColumn(0, 0, level, world.state());
        assertTrue(Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(column.getBlock(floor)));
        assertFalse(column.getBlock(floor + 1).getFluidState().isEmpty());
        assertTrue(world.generator().getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE_WG, level, world.state()) - 1 > floor);
    }

    @Test void emptyGenerationRangeReturnsMinYMinusOne() {
        var level = levels.get(PhaseId.SMOOTH);
        when(level.getMinY()).thenReturn(320);
        when(level.getHeight()).thenReturn(0);
        when(level.getMaxY()).thenReturn(319);
        assertEquals(level.getMinY() - 1, service.sampleHeight(PhaseId.SMOOTH, 0, 0));
    }

    @Test void comparisonRejectsMismatchedCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainComparison(
                new TerrainSample(PhaseId.SMOOTH,0,0,10,0), new TerrainSample(PhaseId.RIDGE,1,0,20,1)));
    }

    @Test void tutorialUsesRealServiceAndGeneratorWithoutAdditionalChunkRequests() {
        for(var level:levels.values()) when(level.getSeaLevel()).thenReturn(63);
        var finder=new TutorialCandidateFinder(server);
        var first=finder.begin(0,0,new TutorialSearchConfig(512,8));
        while(!first.done()) first.advance(1);
        var second=finder.begin(0,0,new TutorialSearchConfig(512,8));
        while(!second.done()) second.advance(4);
        assertEquals(first.result().candidates(),second.result().candidates());
        for(var candidate:first.result().candidates()) {
            assertEquals(service.sampleHeight(PhaseId.FRACTURE,candidate.x(),candidate.z()),candidate.fractureHeight());
            assertEquals(service.sampleHeight(PhaseId.SMOOTH,candidate.x(),candidate.z()),candidate.smoothHeight());
            assertTrue(Double.isFinite(candidate.score().totalScore()));
        }
        assertTrue(first.result().heightSamples()<=144);
        // Existing @AfterEach audits every Level/cache call and forbids chunk/live-heightmap requests.
    }

    private static RegistryAccess densityRegistry(PhaseId changed, DensityFunction replacement) {
        var registry = new MappedRegistry<DensityFunction>(Registries.DENSITY_FUNCTION, Lifecycle.stable());
        for (PhaseId phase : PhaseId.values()) {
            var value = phase == changed ? replacement : DensityFunction.DIRECT_CODEC.parse(
                    RegistryOps.create(JsonOps.INSTANCE, vanilla), resource("/data/worldphase/worldgen/density_function/" + name(phase) + "_noise.json")).getOrThrow();
            if (value != null) registry.register(key(phase), value, RegistrationInfo.BUILT_IN);
        }
        return new RegistryAccess.ImmutableRegistryAccess(List.of(registry)).freeze();
    }

    private static String name(PhaseId phase) { return phase.name().toLowerCase(Locale.ROOT); }
    private static ResourceKey<DensityFunction> key(PhaseId phase) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath("worldphase", name(phase) + "_noise"));
    }

    private static JsonElement resource(String path) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(TerrainSampleServiceTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception error) { throw new IllegalStateException(path, error); }
    }

    // Test-only expansion because the transforming loader cannot load datapack resources itself.
    // The dedicated smoke exercises real dynamic registry loading without this helper.
    private static JsonElement resolve(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().startsWith("worldphase:")) {
            String name = element.getAsString().substring(11);
            boolean settings = Arrays.stream(PhaseId.values()).anyMatch(id -> name(id).equals(name));
            return resolve(resource("/data/worldphase/worldgen/" + (settings ? "noise_settings" : "density_function") + "/" + name + ".json"));
        }
        if (element.isJsonObject()) {
            var out = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), e.getKey().equals("type") ? e.getValue() : resolve(e.getValue())));
            return out;
        }
        if (element.isJsonArray()) {
            var out = new JsonArray(); element.getAsJsonArray().forEach(e -> out.add(resolve(e))); return out;
        }
        return element;
    }
}
