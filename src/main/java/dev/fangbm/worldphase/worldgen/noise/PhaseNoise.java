package dev.fangbm.worldphase.worldgen.noise;

/**
 * A deterministic 2D spatial field. Implementations are immutable and safe to share between threads.
 * Identical seed, configuration and coordinates yield identical double bits, regardless of sample order.
 * Coordinates must be finite and each kernel's scaled coordinates must be within +/- 2^50.
 * Unsupported coordinates are rejected with {@link IllegalArgumentException}.
 */
@FunctionalInterface
public interface PhaseNoise {
    double sample(long seed, double x, double z);
}
