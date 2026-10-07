package dev.fangbm.worldphase.worldgen.noise;

final class NoiseMath {
    // Keep lattice coordinates and adjacent cells exactly representable; well beyond Minecraft's bounds.
    private static final double MAX_COORDINATE = 0x1.0p50;

    private NoiseMath() {}

    static double positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    static double coordinate(double value) {
        if (!Double.isFinite(value) || StrictMath.abs(value) > MAX_COORDINATE) {
            throw new IllegalArgumentException("Noise coordinate must be finite and within +/- 2^50");
        }
        return value;
    }

    static long floor(double value) {
        return (long) StrictMath.floor(value);
    }
}
