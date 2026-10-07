package dev.fangbm.worldphase.worldgen.noise;

/**
 * Normalized accumulation of {@code 1 - abs(gradientNoise)} in [0, 1].
 * One to eight octaves share the base seed, with fixed lacunarity 2 and persistence 0.5.
 */
public final class RidgedNoise implements PhaseNoise {
    private final GradientNoise base;
    private final int octaves;

    public RidgedNoise(double frequency, int octaves) {
        if (octaves < 1 || octaves > 8) {
            throw new IllegalArgumentException("octaves must be between 1 and 8");
        }
        this.base = new GradientNoise(frequency);
        this.octaves = octaves;
    }

    @Override
    public double sample(long seed, double x, double z) {
        double amplitude = 1.0;
        double sum = 0.0;
        double weight = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += (1.0 - StrictMath.abs(base.sample(seed, x, z))) * amplitude;
            weight += amplitude;
            amplitude *= 0.5;
            x *= 2.0;
            z *= 2.0;
        }
        return sum / weight;
    }
}
