package dev.fangbm.worldphase.worldgen.density;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.registry.ModDensityFunctions;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunction.Algorithm;
import dev.fangbm.worldphase.worldgen.noise.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhaseNoiseDensityFunctionTest {
    private static HolderLookup.Provider lookup;

    @BeforeAll
    static void vanillaWorldgen() {
        lookup = VanillaRegistries.createLookup();
    }

    private static JsonObject json(String algorithm) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "worldphase:phase_noise");
        json.addProperty("algorithm", algorithm);
        json.addProperty("frequency", 0.004);
        json.addProperty("amplitude", 1.0);
        return json;
    }

    private static PhaseNoiseDensityFunction decode(JsonObject json) {
        return (PhaseNoiseDensityFunction) DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static PhaseNoise direct(PhaseNoiseDensityFunction.Config config) {
        return switch (config.algorithm()) {
            case SMOOTH -> new GradientNoise(config.frequency());
            case RIDGE -> new RidgedNoise(config.frequency(), config.octaves());
            case CELL -> new WorleyNoise(config.frequency());
            case FRACTURE -> new DomainWarpNoise(config.frequency(), config.warpFrequency(), config.warpStrength());
        };
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void registeredCodecConstructsEveryAlgorithmWithDefaults(Algorithm algorithm) {
        var template = decode(json(algorithm.name().toLowerCase(java.util.Locale.ROOT)));
        assertEquals(algorithm, template.config().algorithm());
        assertEquals(2, template.config().octaves());
        assertEquals(0.008, template.config().warpFrequency());
        assertEquals(32, template.config().warpStrength());
        assertSame(PhaseNoiseDensityFunction.CODEC, template.codec());
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void customParametersRoundTripWithoutSerializingSeed(Algorithm algorithm) {
        JsonObject input = json(algorithm.name().toLowerCase(java.util.Locale.ROOT));
        input.addProperty("octaves", 3);
        input.addProperty("warp_frequency", 0.03125);
        input.addProperty("warp_strength", 4.0);
        var first = decode(input).withSeed(-1729);
        var encoded = DensityFunction.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, first).getOrThrow();
        assertEquals("worldphase:phase_noise", encoded.getAsJsonObject().get("type").getAsString());
        assertFalse(encoded.getAsJsonObject().has("seed"));
        var second = decode(encoded.getAsJsonObject());
        assertEquals(first.config(), second.config());
        assertThrows(IllegalStateException.class, () -> second.compute(new DensityFunction.SinglePointContext(1, 0, 2)));
        var context = new DensityFunction.SinglePointContext(-513, 70, 381);
        assertBits(first.compute(context), second.withSeed(-1729).compute(context));
    }

    @ParameterizedTest
    @CsvSource({"smooth, 0", "smooth, -2.5", "smooth, 3", "ridge, 0", "ridge, -2.5", "ridge, 3",
            "cell, 0", "cell, -2.5", "cell, 3", "fracture, 0", "fracture, -2.5", "fracture, 3"})
    void matchesDirectKernelAndOnlyScalesFinalOutput(String algorithm, double amplitude) {
        JsonObject input = json(algorithm);
        input.addProperty("amplitude", amplitude);
        var template = decode(input);
        PhaseNoise kernel = direct(template.config());
        for (long seed : new long[]{0, 42, -1729, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var bound = template.withSeed(seed);
            for (int x : new int[]{-513, -1, 0, 17, 381}) {
                var context = new DensityFunction.SinglePointContext(x, 70, x - 13);
                double expected = kernel.sample(seed, x, x - 13) * amplitude;
                assertBits(expected, bound.compute(context));
                bound.compute(new DensityFunction.SinglePointContext(87, -64, 19));
                assertBits(expected, bound.compute(context));
                assertBits(expected, bound.compute(new DensityFunction.SinglePointContext(x, -64, x - 13)));
                assertTrue(expected >= bound.minValue() && expected <= bound.maxValue());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"frequency, 0", "frequency, -1", "frequency, 1e100", "amplitude, 1000001",
            "amplitude, -1000001", "octaves, 0", "octaves, 9", "warp_frequency, 0",
            "warp_frequency, -1", "warp_frequency, 1e100", "warp_strength, -1", "warp_strength, 1e100"})
    void invalidParametersFailDuringDecode(String field, double value) {
        JsonObject input = json("fracture");
        input.addProperty(field, value);
        var error = DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, input).error().orElseThrow();
        assertTrue(error.message().contains(field), error::message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"frequency", "amplitude", "warp_frequency", "warp_strength"})
    void nonFiniteParametersFailDuringDecode(String field) {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            JsonObject input = json("fracture");
            input.addProperty(field, value);
            assertTrue(DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, input).error().isPresent());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"algorithm", "frequency", "amplitude"})
    void missingRequiredFieldsAreRejected(String field) {
        JsonObject input = json("smooth");
        input.remove(field);
        assertTrue(DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, input).error().isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"octaves", "warp_frequency", "warp_strength"})
    void malformedOptionalFieldsAreNotSilentlyDefaulted(String field) {
        JsonObject input = json("fracture");
        input.addProperty(field, "invalid");
        assertTrue(DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, input).error().isPresent());
    }

    @Test
    void unknownAlgorithmIsRejectedWithUsefulError() {
        var error = DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, json("warp")).error().orElseThrow();
        assertTrue(error.message().contains("Unknown phase_noise algorithm 'warp'"), error::message);
    }

    @Test
    void fractionalOctavesAreRejectedInsteadOfTruncated() {
        JsonObject input = json("ridge");
        input.addProperty("octaves", 2.5);
        assertTrue(DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, input).error().isPresent());
    }

    @Test
    void registeredTypeAndFixtureUseVanillaDispatchCodec() throws Exception {
        Identifier id = Identifier.fromNamespaceAndPath("worldphase", "phase_noise");
        assertEquals(id, ModDensityFunctions.PHASE_NOISE.getId());
        assertSame(PhaseNoiseDensityFunction.CODEC.codec(), ModDensityFunctions.PHASE_NOISE.get());
        assertEquals(id, BuiltInRegistries.DENSITY_FUNCTION_TYPE.getKey(ModDensityFunctions.PHASE_NOISE.get()));
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/datapacks/phase-noise-fixture/data/worldphase/worldgen/density_function/wph005_fixture.json"), StandardCharsets.UTF_8)) {
            var template = decode(JsonParser.parseReader(reader).getAsJsonObject());
            assertEquals(Algorithm.SMOOTH, template.config().algorithm());
        }
    }

    @SuppressWarnings("deprecation") // 26.1's settings record still includes the deprecated mob-generation flag.
    private static NoiseGeneratorSettings withRouter(NoiseGeneratorSettings base, NoiseRouter router) {
        return new NoiseGeneratorSettings(base.noiseSettings(), base.defaultBlock(), base.defaultFluid(), router,
                base.surfaceRule(), base.spawnTarget(), base.seaLevel(), base.disableMobGeneration(),
                base.isAquifersEnabled(), base.oreVeinsEnabled(), base.useLegacyRandomSource());
    }

    private static NoiseRouter modifiedRouter(NoiseRouter vanilla, DensityFunction phase) {
        return new NoiseRouter(vanilla.barrierNoise(), vanilla.fluidLevelFloodednessNoise(), vanilla.fluidLevelSpreadNoise(),
                vanilla.lavaNoise(), DensityFunctions.add(vanilla.temperature(), phase), vanilla.vegetation(),
                vanilla.continents(), vanilla.erosion(), vanilla.depth(), vanilla.ridges(), vanilla.preliminarySurfaceLevel(),
                DensityFunctions.add(vanilla.finalDensity(), new DensityFunctions.HolderHolder(Holder.direct(phase))),
                vanilla.veinToggle(), vanilla.veinRidged(), vanilla.veinGap());
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void realRandomStateAutomaticallyBindsWorldSeedAndPreservesVanillaComposition(Algorithm algorithm) {
        var template = decode(json(algorithm.name().toLowerCase(java.util.Locale.ROOT)));
        var baseSettings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var settings = withRouter(baseSettings, modifiedRouter(baseSettings.noiseRouter(), template));
        var noises = lookup.lookupOrThrow(Registries.NOISE);
        var context = new DensityFunction.SinglePointContext(-513, 70, 381);
        double previous = Double.NaN;
        for (long seed : new long[]{0, 42, -1729}) {
            // No manual withSeed here: tests the real transformed RandomState constructor.
            RandomState state = RandomState.create(settings, noises, seed);
            RandomState vanilla = RandomState.create(baseSettings, noises, seed);
            double phase = direct(template.config()).sample(seed, context.blockX(), context.blockZ());
            double actual = state.router().finalDensity().compute(context);
            assertBits(vanilla.router().finalDensity().compute(context) + phase, actual);
            assertBits(vanilla.router().temperature().compute(context) + phase, state.router().temperature().compute(context));
            assertBits(vanilla.router().vegetation().compute(context), state.router().vegetation().compute(context));
            assertBits(actual, RandomState.create(settings, noises, seed).router().finalDensity().compute(context));
            assertTrue(Double.isFinite(actual));
            if (Double.isFinite(previous)) assertNotEquals(previous, actual);
            previous = actual;
        }
        assertThrows(IllegalStateException.class, () -> template.compute(context));
        assertSame(baseSettings.surfaceRule(), settings.surfaceRule());
        assertEquals(baseSettings.defaultBlock(), settings.defaultBlock());
        assertNotNull(RandomState.create(settings, noises, 42).surfaceSystem());
    }

    @Test
    void fillArrayAndMapAllUseNormalDensityFunctionContract() {
        var template = decode(json("smooth"));
        var seeded = template.mapAll(new PhaseNoiseSeedVisitor(function -> function, 42));
        double[] values = new double[3];
        DensityFunction.ContextProvider provider = new DensityFunction.ContextProvider() {
            public DensityFunction.FunctionContext forIndex(int index) {
                return new DensityFunction.SinglePointContext(index - 13, 70, 17);
            }
            public void fillAllDirectly(double[] output, DensityFunction function) {
                for (int i = 0; i < output.length; i++) output[i] = function.compute(forIndex(i));
            }
        };
        seeded.fillArray(values, provider);
        for (int i = 0; i < values.length; i++) assertBits(seeded.compute(provider.forIndex(i)), values[i]);
        assertSame(seeded, seeded.mapAll(function -> function));
    }

    @Test
    void additiveModifierChangesAnIsolatedVanillaNoiseColumn() {
        // In-memory generator test only: no dimension registration or noise-settings JSON/preset.
        var base = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        JsonObject input = json("smooth");
        input.addProperty("amplitude", 64.0);
        var modified = withRouter(base, modifiedRouter(base.noiseRouter(), decode(input)));
        var biomeSource = new FixedBiomeSource(lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        var vanillaGenerator = new NoiseBasedChunkGenerator(biomeSource, Holder.direct(base));
        var modifiedGenerator = new NoiseBasedChunkGenerator(biomeSource, Holder.direct(modified));
        var height = LevelHeightAccessor.create(-64, 384);
        var noises = lookup.lookupOrThrow(Registries.NOISE);
        var vanillaState = RandomState.create(base, noises, 42);
        var modifiedState = RandomState.create(modified, noises, 42);
        int differences = 0;
        for (int x : new int[]{-513, 17, 481}) {
            var original = vanillaGenerator.getBaseColumn(x, 381, height, vanillaState);
            var changed = modifiedGenerator.getBaseColumn(x, 381, height, modifiedState);
            for (int y = -64; y < 320; y++) {
                if (original.getBlock(y).isAir() != changed.getBlock(y).isAir()) differences++;
            }
        }
        assertTrue(differences > 0, "Additive phase signal should change generated stone/air decisions");
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
    }
}
