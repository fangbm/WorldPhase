package dev.fangbm.worldphase.worldgen.noise;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NoiseKernelsTest {
    record KernelCase(String name, Supplier<PhaseNoise> factory, double lower, double upper,
                      double goldenNearOrigin, double goldenNegativeCell) {
        @Override public String toString() { return name; }
    }

    static Stream<KernelCase> kernels() {
        return Stream.of(
                new KernelCase("Smooth", () -> new GradientNoise(0.125), -1, 1,
                        0x1.b4ac1a7f791b8p-6, 0x1.c01c8ce1b994ap-4),
                new KernelCase("Ridged", () -> new RidgedNoise(0.125, 3), 0, 1,
                        0x1.dfb264afa0779p-1, 0x1.b6370d3f4bdd5p-1),
                new KernelCase("Worley", () -> new WorleyNoise(0.125), 0, StrictMath.sqrt(2),
                        0x1.a48f3e0f08bb3p-2, 0x1.c5816c5a598f7p-3),
                new KernelCase("DomainWarp", () -> new DomainWarpNoise(0.125, 0.03125, 4), -1, 1,
                        0x1.141a70e1bffbdp-5, 0x1.dffaef710160ep-4));
    }

    @ParameterizedTest(name = "{0}: fixed seed golden bits")
    @MethodSource("kernels")
    void goldenValues(KernelCase config) {
        PhaseNoise kernel = config.factory.get();
        // Two non-lattice points pin hashing/interpolation, including negative floor semantics.
        // Hex literals preserve exact binary results across fresh test processes, not rounded decimals.
        assertBits(config.goldenNearOrigin, kernel.sample(42, 0.25, -0.75));
        assertBits(config.goldenNegativeCell, kernel.sample(-1729, 123.125, -456.5));
    }

    @ParameterizedTest(name = "{0}: repeated and fresh instances")
    @MethodSource("kernels")
    void repeatedCallsAndInstances(KernelCase config) {
        PhaseNoise kernel = config.factory.get();
        double expected = kernel.sample(Long.MIN_VALUE, -43.125, 87.75);
        for (int i = 0; i < 100; i++) {
            kernel.sample(i, i * 0.125, -i * 0.75);
            assertBits(expected, kernel.sample(Long.MIN_VALUE, -43.125, 87.75));
            assertBits(expected, config.factory.get().sample(Long.MIN_VALUE, -43.125, 87.75));
        }
    }

    @ParameterizedTest(name = "{0}: sampling order")
    @MethodSource("kernels")
    void samplingOrderIndependent(KernelCase config) {
        PhaseNoise kernel = config.factory.get();
        double[] expected = new double[101];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = kernel.sample(9123, i * 0.37 - 17.2, i * -0.41 + 9.5);
        }
        // Reverse order, then a permutation whose stride is coprime to the point count.
        for (int i = expected.length - 1; i >= 0; i--) {
            assertBits(expected[i], kernel.sample(9123, i * 0.37 - 17.2, i * -0.41 + 9.5));
        }
        for (int step = 0; step < expected.length; step++) {
            int i = step * 37 % expected.length;
            assertBits(expected[i], kernel.sample(9123, i * 0.37 - 17.2, i * -0.41 + 9.5));
        }
    }

    @ParameterizedTest(name = "{0}: distinct seeds")
    @MethodSource("kernels")
    void seedChangesField(KernelCase config) {
        PhaseNoise kernel = config.factory.get();
        boolean differs = false;
        for (int i = 0; i < 20; i++) {
            differs |= Double.doubleToLongBits(kernel.sample(42, i + 0.375, -i - 0.625))
                    != Double.doubleToLongBits(kernel.sample(43, i + 0.375, -i - 0.625));
        }
        assertTrue(differs, "Seed must affect the spatial field");
    }

    @ParameterizedTest(name = "{0}: finite and bounded")
    @MethodSource("kernels")
    void finiteAndWithinDocumentedRange(KernelCase config) {
        PhaseNoise kernel = config.factory.get();
        for (long seed : new long[]{0, 42, -1729, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    assertRange(kernel.sample(seed, x * 1.37, z * 1.91), config.lower, config.upper);
                }
            }
            for (double coordinate : new double[]{-30_000_000, -0.0, 0, Double.MIN_VALUE, 30_000_000}) {
                assertRange(kernel.sample(seed, coordinate, -coordinate), config.lower, config.upper);
            }
        }
    }

    @ParameterizedTest(name = "{0}: unsupported coordinates")
    @MethodSource("kernels")
    void rejectsUnsupportedCoordinates(KernelCase config) {
        PhaseNoise kernel = config.factory.get();
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MAX_VALUE, -Double.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> kernel.sample(42, invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> kernel.sample(42, 0, invalid));
        }
    }

    @Test
    void smoothIsLocallyContinuousIncludingLatticeBoundaries() {
        GradientNoise kernel = new GradientNoise(1);
        double epsilon = 1e-6;
        for (long seed : new long[]{0, 42, -1729}) {
            for (double x : new double[]{-3, -1, 0, 1, 7, 0.375}) {
                for (double z : new double[]{-2, 0, 1, 0.625}) {
                    double center = kernel.sample(seed, x, z);
                    assertEquals(center, kernel.sample(seed, x + epsilon, z), 4 * epsilon);
                    assertEquals(center, kernel.sample(seed, x - epsilon, z), 4 * epsilon);
                    assertEquals(center, kernel.sample(seed, x, z + epsilon), 4 * epsilon);
                    assertEquals(center, kernel.sample(seed, x, z - epsilon), 4 * epsilon);
                }
            }
        }
        // Derivatives agree on both sides of a lattice boundary (quintic fade).
        double center = kernel.sample(42, -1, 0.625);
        double leftSlope = (center - kernel.sample(42, -1 - epsilon, 0.625)) / epsilon;
        double rightSlope = (kernel.sample(42, -1 + epsilon, 0.625) - center) / epsilon;
        assertEquals(leftSlope, rightSlope, 1e-5);
    }

    @Test
    void ridgedTransformPeaksAtBaseZeroAndNormalizesOctaves() {
        GradientNoise base = new GradientNoise(1);
        RidgedNoise single = new RidgedNoise(1, 1);
        RidgedNoise accumulated = new RidgedNoise(1, 3);
        assertBits(1, single.sample(42, 0, 0));
        assertBits(1, accumulated.sample(42, -3, 7));
        double x = 0.375;
        double z = -0.625;
        assertBits(1 - StrictMath.abs(base.sample(42, x, z)), single.sample(42, x, z));
        assertTrue(single.sample(42, x, z) < 1);
        double expected = ((1 - StrictMath.abs(base.sample(42, x, z)))
                + 0.5 * (1 - StrictMath.abs(base.sample(42, x * 2, z * 2)))
                + 0.25 * (1 - StrictMath.abs(base.sample(42, x * 4, z * 4)))) / 1.75;
        assertBits(expected, accumulated.sample(42, x, z));
        assertNotEquals(single.sample(42, x, z), accumulated.sample(42, x, z));
    }

    @Test
    void worleyFeaturesAreFixedPerSeedAndCell() {
        // Pin both channel assignments and negative cell coordinate hashing.
        assertBits(0x1.f2c18a6efee3fp-1, WorleyNoise.featureX(42, -3, 7));
        assertBits(0x1.edc3257b71184p-3, WorleyNoise.featureZ(42, -3, 7));
        WorleyNoise kernel = new WorleyNoise(1);
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                double x = WorleyNoise.featureX(42, cx, cz);
                double z = WorleyNoise.featureZ(42, cx, cz);
                assertTrue(x >= 0 && x < 1 && z >= 0 && z < 1);
                assertBits(x, WorleyNoise.featureX(42, cx, cz));
                assertBits(z, WorleyNoise.featureZ(42, cx, cz));
                assertEquals(0, kernel.sample(42, cx + x, cz + z), 1e-15);
            }
        }
        assertNotEquals(WorleyNoise.featureX(42, -3, 7), WorleyNoise.featureX(43, -3, 7));
        assertNotEquals(WorleyNoise.featureX(42, -3, 7), WorleyNoise.featureX(42, -2, 7));
    }

    @Test
    void worleySearchMatchesUnprunedWideSearchAtCellBoundaries() {
        WorleyNoise kernel = new WorleyNoise(1);
        for (long seed : new long[]{0, 42, -1729, Long.MIN_VALUE}) {
            for (double x : new double[]{-3.000001, -3, -2.999999, -0.000001, 0, 0.000001, 1.5}) {
                for (double z : new double[]{-1, -0.000001, 0, 0.000001, 0.5, 2.999999}) {
                    assertEquals(referenceF1(seed, x, z, 4), kernel.sample(seed, x, z), 1e-15);
                }
            }
        }
    }

    @Test
    void worleyNeedsSomeCellsBeyondThreeByThree() {
        // A near-edge point whose closest feature is two cells away: protects exact F1 search.
        long seed = 345228;
        double x = 0.0001;
        double z = 0.5;
        double actual = new WorleyNoise(1).sample(seed, x, z);
        assertBits(0x1.06297513bd113p0, actual);
        assertEquals(referenceF1(seed, x, z, 4), actual, 1e-15);
        assertTrue(referenceF1(seed, x, z, 1) > actual + 1e-4);
    }

    @Test
    void worleyIsContinuousAcrossPositiveAndNegativeCellBoundaries() {
        WorleyNoise kernel = new WorleyNoise(1);
        double epsilon = 1e-6;
        for (double boundary : new double[]{-3, -1, 0, 1, 4}) {
            // Distance to a fixed feature set is 1-Lipschitz, even where the nearest feature changes.
            assertEquals(kernel.sample(42, boundary - epsilon, 0.37),
                    kernel.sample(42, boundary + epsilon, 0.37), 2 * epsilon + 1e-14);
            assertEquals(kernel.sample(42, 0.37, boundary - epsilon),
                    kernel.sample(42, 0.37, boundary + epsilon), 2 * epsilon + 1e-14);
        }
    }

    @Test
    void domainWarpUsesStableIndependentChannelsAndOriginalBaseSeed() {
        assertEquals(0xf6f7aa56a40a1411L, NoiseHash.channel(42, DomainWarpNoise.WARP_X_CHANNEL));
        assertEquals(0x3b86712b321c50e8L, NoiseHash.channel(42, DomainWarpNoise.WARP_Z_CHANNEL));
        GradientNoise base = new GradientNoise(0.125);
        GradientNoise warp = new GradientNoise(0.03125);
        DomainWarpNoise kernel = new DomainWarpNoise(0.125, 0.03125, 4);
        long seed = 42;
        double x = 12.375;
        double z = -4.625;
        long channelX = NoiseHash.channel(seed, DomainWarpNoise.WARP_X_CHANNEL);
        long channelZ = NoiseHash.channel(seed, DomainWarpNoise.WARP_Z_CHANNEL);
        double wx = warp.sample(channelX, x, z);
        double wz = warp.sample(channelZ, x, z);
        assertNotEquals(channelX, channelZ);
        assertNotEquals(wx, wz);
        assertBits(wx, warp.sample(channelX, x, z));
        assertBits(wz, warp.sample(channelZ, x, z));
        assertBits(base.sample(seed, x + wx * 4, z + wz * 4), kernel.sample(seed, x, z));
        assertNotEquals(base.sample(seed, x, z), kernel.sample(seed, x, z));
    }

    @Test
    void zeroWarpStrengthMatchesBase() {
        GradientNoise base = new GradientNoise(0.125);
        DomainWarpNoise kernel = new DomainWarpNoise(0.125, 0.03125, 0);
        for (double x : new double[]{-3, 0, 0.375, 123.625}) {
            assertBits(base.sample(42, x, -x), kernel.sample(42, x, -x));
        }
    }

    @Test
    void constructorsRejectInvalidConfiguration() {
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new GradientNoise(invalid));
            assertThrows(IllegalArgumentException.class, () -> new RidgedNoise(invalid, 3));
            assertThrows(IllegalArgumentException.class, () -> new WorleyNoise(invalid));
            assertThrows(IllegalArgumentException.class, () -> new DomainWarpNoise(invalid, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new DomainWarpNoise(1, invalid, 1));
        }
        for (int invalid : new int[]{0, -1, 9}) {
            assertThrows(IllegalArgumentException.class, () -> new RidgedNoise(1, invalid));
        }
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new DomainWarpNoise(1, 1, invalid));
        }
    }

    @Test
    void scaledCoordinateLimitsAreExplicit() {
        assertTrue(Double.isFinite(new GradientNoise(1).sample(42, 0x1.0p50, -0x1.0p50)));
        assertTrue(Double.isFinite(new WorleyNoise(1).sample(42, 0x1.0p50, -0x1.0p50)));
        assertThrows(IllegalArgumentException.class, () -> new GradientNoise(1).sample(42, 0x1.0p51, 0));
        assertThrows(IllegalArgumentException.class, () -> new RidgedNoise(1, 8).sample(42, 0x1.0p50, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DomainWarpNoise(1, 1, Double.MAX_VALUE).sample(42, 0.375, -0.625));
    }

    private static double referenceF1(long seed, double x, double z, int radius) {
        long cx = (long) StrictMath.floor(x);
        long cz = (long) StrictMath.floor(z);
        double best = Double.POSITIVE_INFINITY;
        for (long cellX = cx - radius; cellX <= cx + radius; cellX++) {
            for (long cellZ = cz - radius; cellZ <= cz + radius; cellZ++) {
                double dx = cellX + WorleyNoise.featureX(seed, cellX, cellZ) - x;
                double dz = cellZ + WorleyNoise.featureZ(seed, cellX, cellZ) - z;
                best = StrictMath.min(best, dx * dx + dz * dz);
            }
        }
        return StrictMath.sqrt(best);
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual),
                () -> "expected " + Double.toHexString(expected) + ", actual " + Double.toHexString(actual));
    }

    private static void assertRange(double value, double lower, double upper) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= lower && value <= upper, () -> "Out of range: " + value);
    }
}
