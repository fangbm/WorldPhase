package dev.fangbm.worldphase.worldgen.noise;

/**
 * One warp layer followed by gradient noise, conservatively in [-1, 1].
 * Warp strength is in input coordinate units; frequencies scale base and warp fields independently.
 */
public final class DomainWarpNoise implements PhaseNoise {
    static final long WARP_X_CHANNEL = 0x574152505f585f31L; // "WARP_X_1"
    static final long WARP_Z_CHANNEL = 0x574152505f5a5f31L; // "WARP_Z_1"

    private final GradientNoise base;
    private final GradientNoise warp;
    private final double strength;

    public DomainWarpNoise(double baseFrequency, double warpFrequency, double strength) {
        if (!Double.isFinite(strength) || strength < 0.0) {
            throw new IllegalArgumentException("strength must be finite and nonnegative");
        }
        this.base = new GradientNoise(baseFrequency);
        this.warp = new GradientNoise(warpFrequency);
        this.strength = strength;
    }

    @Override
    public double sample(long seed, double x, double z) {
        double warpX = warp.sample(NoiseHash.channel(seed, WARP_X_CHANNEL), x, z);
        double warpZ = warp.sample(NoiseHash.channel(seed, WARP_Z_CHANNEL), x, z);
        return base.sample(seed, x + warpX * strength, z + warpZ * strength);
    }
}
