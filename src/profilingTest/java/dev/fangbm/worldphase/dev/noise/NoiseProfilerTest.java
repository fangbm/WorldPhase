package dev.fangbm.worldphase.dev.noise;

import static org.junit.jupiter.api.Assertions.*;

import dev.fangbm.worldphase.worldgen.noise.PhaseNoise;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoiseProfilerTest {
    @Test
    void sampleCountAndAggregationAreCorrect() {
        NoiseProfiler.Workload workload = new NoiseProfiler.Workload(3);
        int[] calls = {0};
        PhaseNoise noise = (seed, x, z) -> {
            calls[0]++;
            return x + 32700;
        };
        NoiseProfiler.Result result = NoiseProfiler.profile("linear", noise, workload);
        assertEquals("linear", result.kernelName());
        assertEquals(3, result.sampleCount());
        assertEquals(3, calls[0]);
        // Exact binary-fraction fixture: -67.625, 5.375, 78.375.
        assertEquals(-67.625, result.min());
        assertEquals(78.375, result.max());
        assertEquals(5.375, result.mean());
        // No assertion on elapsed time or throughput: those are machine/JIT dependent.
    }

    @Test
    void singleSampleHasMatchingMinMaxMean() {
        NoiseProfiler.Result result = NoiseProfiler.profile("constant", (seed, x, z) -> -0.25,
                new NoiseProfiler.Workload(1));
        assertEquals(1, result.sampleCount());
        assertEquals(-0.25, result.min());
        assertEquals(-0.25, result.max());
        assertEquals(-0.25, result.mean());
    }

    @Test
    void workloadIsDeterministicAndSpansPositiveAndNegativeCells() {
        NoiseProfiler.Workload first = new NoiseProfiler.Workload(1024);
        NoiseProfiler.Workload second = new NoiseProfiler.Workload(1024);
        assertEquals(1024, first.sampleCount());
        assertEquals(-32767.625, first.x(0));
        assertEquals(-32767.375, first.z(0));
        assertEquals(-32694.625, first.x(1));
        assertEquals(-32574.375, first.z(1));
        boolean positiveX = false;
        boolean positiveZ = false;
        for (int i = 0; i < first.sampleCount(); i++) {
            assertEquals(Double.doubleToLongBits(first.x(i)), Double.doubleToLongBits(second.x(i)));
            assertEquals(Double.doubleToLongBits(first.z(i)), Double.doubleToLongBits(second.z(i)));
            assertTrue(Double.isFinite(first.x(i)) && Double.isFinite(first.z(i)));
            positiveX |= first.x(i) > 0;
            positiveZ |= first.z(i) > 0;
        }
        assertTrue(positiveX && positiveZ);
    }

    @Test
    void everyKernelReceivesSameSeedAndOrderedCoordinates() {
        NoiseProfiler.Workload workload = new NoiseProfiler.Workload(17);
        record Point(long seed, double x, double z) {}
        List<Point> first = new ArrayList<>();
        List<Point> second = new ArrayList<>();
        NoiseProfiler.profile("first", (seed, x, z) -> { first.add(new Point(seed, x, z)); return x; }, workload);
        NoiseProfiler.profile("second", (seed, x, z) -> { second.add(new Point(seed, x, z)); return z; }, workload);
        assertEquals(first, second);
        assertEquals(17, first.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(new Point(NoiseProfiler.SEED, workload.x(i), workload.z(i)), first.get(i));
        }
    }

    @Test
    void warmUpIsFixedAndDoesNotAffectReportedSampleCount() {
        int[] calls = {0};
        PhaseNoise noise = (seed, x, z) -> { calls[0]++; return 0.25; };
        NoiseProfiler.Workload workload = new NoiseProfiler.Workload(7);
        NoiseProfiler.warmUp(noise, workload);
        assertEquals(NoiseProfiler.WARMUP_SAMPLES, calls[0]);
        NoiseProfiler.Result result = NoiseProfiler.profile("constant", noise, workload);
        assertEquals(7, result.sampleCount());
        assertEquals(NoiseProfiler.WARMUP_SAMPLES + 7, calls[0]);
        assertEquals(0.25, result.mean());
    }

    @Test
    void allFourRealKernelsProduceFiniteDeterministicAggregates() {
        NoiseProfiler.Workload workload = new NoiseProfiler.Workload(256);
        List<NoiseProfiler.Kernel> kernels = NoiseProfiler.kernels();
        assertEquals(List.of("GradientNoise", "RidgedNoise", "WorleyNoise", "DomainWarpNoise"),
                kernels.stream().map(NoiseProfiler.Kernel::name).toList());
        for (NoiseProfiler.Kernel kernel : kernels) {
            NoiseProfiler.Result first = NoiseProfiler.profile(kernel.name(), kernel.noise(), workload);
            NoiseProfiler.Result second = NoiseProfiler.profile(kernel.name(), kernel.noise(), workload);
            assertEquals(256, first.sampleCount());
            assertTrue(Double.isFinite(first.min()) && Double.isFinite(first.max())
                    && Double.isFinite(first.mean()));
            assertTrue(first.min() <= first.mean() && first.mean() <= first.max());
            assertEquals(first.min(), second.min());
            assertEquals(first.max(), second.max());
            assertEquals(first.mean(), second.mean());
        }
    }

    @Test
    void invalidSampleCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NoiseProfiler.Workload(0));
        assertThrows(IllegalArgumentException.class, () -> new NoiseProfiler.Workload(-1));
    }

    @Test
    void nonFiniteResultsAndAggregatesAreRejected() {
        NoiseProfiler.Workload workload = new NoiseProfiler.Workload(3);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalStateException.class,
                    () -> NoiseProfiler.profile("invalid", (seed, x, z) -> invalid, workload));
        }
        assertThrows(IllegalStateException.class,
                () -> NoiseProfiler.profile("overflow", (seed, x, z) -> Double.MAX_VALUE, workload));
    }
}
