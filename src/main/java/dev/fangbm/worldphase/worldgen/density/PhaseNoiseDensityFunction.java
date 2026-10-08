package dev.fangbm.worldphase.worldgen.density;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fangbm.worldphase.worldgen.noise.DomainWarpNoise;
import dev.fangbm.worldphase.worldgen.noise.GradientNoise;
import dev.fangbm.worldphase.worldgen.noise.PhaseNoise;
import dev.fangbm.worldphase.worldgen.noise.RidgedNoise;
import dev.fangbm.worldphase.worldgen.noise.WorleyNoise;
import java.util.OptionalLong;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Immutable 2D kernel adapter. Datapack templates are seeded by RandomState during router wiring. */
public final class PhaseNoiseDensityFunction implements DensityFunction.SimpleFunction {
    public enum Algorithm {
        SMOOTH("smooth", -1, 1), RIDGE("ridge", 0, 1),
        CELL("cell", 0, StrictMath.sqrt(2)), FRACTURE("fracture", -1, 1);

        public static final Codec<Algorithm> CODEC = Codec.STRING.comapFlatMap(name -> {
            for (Algorithm algorithm : values()) {
                if (algorithm.id.equals(name)) return DataResult.success(algorithm);
            }
            return DataResult.error(() -> "Unknown phase_noise algorithm '" + name
                    + "'; expected smooth, ridge, cell or fracture");
        }, algorithm -> algorithm.id);

        private final String id;
        private final double min;
        private final double max;

        Algorithm(String id, double min, double max) {
            this.id = id;
            this.min = min;
            this.max = max;
        }
    }

    /** Algorithm-local settings; no seed is serialized or stored in PhaseDefinition. */
    public record Config(Algorithm algorithm, double frequency, double amplitude, int octaves,
                         double warpFrequency, double warpStrength) {
        private static final Codec<Integer> OCTAVES_CODEC = Codec.DOUBLE.comapFlatMap(value ->
                Double.isFinite(value) && value == Math.rint(value) && value >= 1 && value <= 8
                        ? DataResult.success(value.intValue())
                        : DataResult.error(() -> "octaves must be an integer between 1 and 8"), Integer::doubleValue);
        public static final MapCodec<Config> CODEC = RecordCodecBuilder.<Config>mapCodec(instance -> instance.group(
                Algorithm.CODEC.fieldOf("algorithm").forGetter(Config::algorithm),
                Codec.DOUBLE.fieldOf("frequency").forGetter(Config::frequency),
                Codec.DOUBLE.fieldOf("amplitude").forGetter(Config::amplitude),
                OCTAVES_CODEC.optionalFieldOf("octaves", 2).forGetter(Config::octaves),
                Codec.DOUBLE.optionalFieldOf("warp_frequency", 0.008).forGetter(Config::warpFrequency),
                Codec.DOUBLE.optionalFieldOf("warp_strength", 32.0).forGetter(Config::warpStrength)
        ).apply(instance, Config::new)).flatXmap(Config::validated, Config::validated);

        private DataResult<Config> validated() {
            String error = validationError();
            return error == null ? DataResult.success(this) : DataResult.error(() -> error);
        }

        private String validationError() {
            if (algorithm == null) return "algorithm is required";
            if (!Double.isFinite(frequency) || frequency <= 0) return "frequency must be finite and positive";
            // Same reasonable coefficient scale as Vanilla DensityFunctions' numeric codec; negatives invert the signal.
            if (!Double.isFinite(amplitude) || Math.abs(amplitude) > 1_000_000) {
                return "amplitude must be finite and within [-1000000, 1000000]";
            }
            if (octaves < 1 || octaves > 8) return "octaves must be between 1 and 8";
            if (!Double.isFinite(warpFrequency) || warpFrequency <= 0) return "warp_frequency must be finite and positive";
            if (!Double.isFinite(warpStrength) || warpStrength < 0) return "warp_strength must be finite and nonnegative";
            // Validate against ALL int-valued FunctionContexts, not only the Vanilla world border.
            double maxPosition = 0x1.0p31;
            double maxNoiseCoordinate = 0x1.0p50;
            double maxInput = algorithm == Algorithm.RIDGE ? Math.scalb(maxPosition, octaves - 1) : maxPosition;
            if (algorithm == Algorithm.FRACTURE) {
                maxInput += warpStrength;
                if (warpFrequency > maxNoiseCoordinate / maxPosition) return "warp_frequency exceeds the kernel coordinate limit";
            }
            if (frequency > maxNoiseCoordinate / maxInput) return "frequency exceeds the kernel coordinate limit for this configuration";
            return null;
        }

        private PhaseNoise createKernel() {
            return switch (algorithm) {
                case SMOOTH -> new GradientNoise(frequency);
                case RIDGE -> new RidgedNoise(frequency, octaves);
                case CELL -> new WorleyNoise(frequency);
                case FRACTURE -> new DomainWarpNoise(frequency, warpFrequency, warpStrength);
            };
        }
    }

    public static final KeyDispatchDataCodec<PhaseNoiseDensityFunction> CODEC = KeyDispatchDataCodec.of(
            Config.CODEC.xmap(PhaseNoiseDensityFunction::new, PhaseNoiseDensityFunction::config));

    private final Config config;
    private final PhaseNoise kernel;
    private final OptionalLong seed;

    public PhaseNoiseDensityFunction(Config config) {
        this.config = config.validated().getOrThrow();
        this.kernel = config.createKernel();
        this.seed = OptionalLong.empty();
    }

    private PhaseNoiseDensityFunction(PhaseNoiseDensityFunction template, long seed) {
        this.config = template.config;
        this.kernel = template.kernel;
        this.seed = OptionalLong.of(seed);
    }

    public Config config() { return config; }

    /** Returns a new per-RandomState instance; never mutates shared datapack templates. */
    public PhaseNoiseDensityFunction withSeed(long worldSeed) {
        return new PhaseNoiseDensityFunction(this, worldSeed);
    }

    @Override
    public double compute(FunctionContext context) {
        if (seed.isEmpty()) throw new IllegalStateException("worldphase:phase_noise must be bound to the world seed by RandomState before sampling");
        return kernel.sample(seed.getAsLong(), context.blockX(), context.blockZ()) * config.amplitude;
    }

    @Override
    public double minValue() { return Math.min(config.algorithm.min * config.amplitude, config.algorithm.max * config.amplitude); }

    @Override
    public double maxValue() { return Math.max(config.algorithm.min * config.amplitude, config.algorithm.max * config.amplitude); }

    @Override
    public KeyDispatchDataCodec<PhaseNoiseDensityFunction> codec() { return CODEC; }
}
