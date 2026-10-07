package dev.fangbm.worldphase.worldgen.noise;

/**
 * Euclidean F1 distance in scaled cell units, conservatively in [0, sqrt(2)].
 * Each cell contains one seed-derived feature point, without a stored point table.
 */
public final class WorleyNoise implements PhaseNoise {
    private static final long FEATURE_X_CHANNEL = 0x574f524c45595f58L; // "WORLEY_X"
    private static final long FEATURE_Z_CHANNEL = 0x574f524c45595f5aL; // "WORLEY_Z"

    private final double frequency;

    public WorleyNoise(double frequency) {
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
        double best = distanceSquared(seed, cx, cz, 0, 0, fx, fz);
        // Own-cell distance is < sqrt(2). A cell beyond radius 2 is at least 2 away.
        // A 3x3 search alone can miss features when full-cell jitter is allowed.
        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (StrictMath.max(StrictMath.abs(dx), StrictMath.abs(dz)) != radius) {
                        continue;
                    }
                    double lowerX = distanceToCell(dx, fx);
                    double lowerZ = distanceToCell(dz, fz);
                    if (lowerX * lowerX + lowerZ * lowerZ >= best) {
                        continue;
                    }
                    best = StrictMath.min(best, distanceSquared(seed, cx, cz, dx, dz, fx, fz));
                }
            }
        }
        return StrictMath.sqrt(best);
    }

    private static double distanceToCell(int offset, double fraction) {
        return StrictMath.max(0.0, StrictMath.max(offset - fraction, fraction - (offset + 1.0)));
    }

    private static double distanceSquared(long seed, long cx, long cz, int dx, int dz,
                                          double fx, double fz) {
        double x = dx + featureX(seed, cx + dx, cz + dz) - fx;
        double z = dz + featureZ(seed, cx + dx, cz + dz) - fz;
        return x * x + z * z;
    }

    static double featureX(long seed, long cx, long cz) {
        return NoiseHash.unit(NoiseHash.cell(seed, cx, cz, FEATURE_X_CHANNEL));
    }

    static double featureZ(long seed, long cx, long cz) {
        return NoiseHash.unit(NoiseHash.cell(seed, cx, cz, FEATURE_Z_CHANNEL));
    }
}
