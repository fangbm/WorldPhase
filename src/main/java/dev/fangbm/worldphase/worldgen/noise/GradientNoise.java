package dev.fangbm.worldphase.worldgen.noise;

/**
 * Smooth coherent gradient noise: eight unit gradients, quintic interpolation, no permutation table.
 * Output is conservatively bounded by [-1, 1]; the field need not reach either endpoint.
 * Frequency scales input coordinates, with one lattice cell per unit of scaled space.
 */
public final class GradientNoise implements PhaseNoise {
    private static final long GRADIENT_CHANNEL = 0x4752414449454e54L; // "GRADIENT"
    private static final double INV_SQRT_TWO = 0x1.6a09e667f3bccp-1;

    private final double frequency;

    public GradientNoise(double frequency) {
        this.frequency = NoiseMath.positive(frequency, "frequency");
    }

    @Override
    public double sample(long seed, double x, double z) {
        double sx = NoiseMath.coordinate(x * frequency);
        double sz = NoiseMath.coordinate(z * frequency);
        long cx = NoiseMath.floor(sx);
        long cz = NoiseMath.floor(sz);
        double fx = sx - cx;
        double fz = sz - cz;
        double wx = fade(fx);
        double wz = fade(fz);
        double lower = blend(dot(seed, cx, cz, fx, fz), dot(seed, cx + 1, cz, fx - 1, fz), wx);
        double upper = blend(dot(seed, cx, cz + 1, fx, fz - 1),
                dot(seed, cx + 1, cz + 1, fx - 1, fz - 1), wx);
        return blend(lower, upper, wz) * INV_SQRT_TWO;
    }

    private static double dot(long seed, long cx, long cz, double x, double z) {
        return switch ((int) (NoiseHash.cell(seed, cx, cz, GRADIENT_CHANNEL) & 7)) {
            case 0 -> x;
            case 1 -> -x;
            case 2 -> z;
            case 3 -> -z;
            case 4 -> (x + z) * INV_SQRT_TWO;
            case 5 -> (x - z) * INV_SQRT_TWO;
            case 6 -> (-x + z) * INV_SQRT_TWO;
            default -> (-x - z) * INV_SQRT_TWO;
        };
    }

    private static double fade(double t) {
        return t * t * t * (10.0 + t * (-15.0 + 6.0 * t));
    }

    private static double blend(double a, double b, double weight) {
        return (1.0 - weight) * a + weight * b;
    }
}
