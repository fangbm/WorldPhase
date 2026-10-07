package dev.fangbm.worldphase.dev.noise;

import dev.fangbm.worldphase.worldgen.noise.DomainWarpNoise;
import dev.fangbm.worldphase.worldgen.noise.GradientNoise;
import dev.fangbm.worldphase.worldgen.noise.PhaseNoise;
import dev.fangbm.worldphase.worldgen.noise.RidgedNoise;
import dev.fangbm.worldphase.worldgen.noise.WorleyNoise;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Lightweight local comparison, not a precise benchmark or gameplay service. */
public final class NoiseProfiler {
    public static final long SEED = 0x574f524c44504841L;
    public static final int WARMUP_SAMPLES = 1_000_000;
    private static final int DEFAULT_SAMPLES = 1_000_000;
    // Publish warm-up and measured results outside the loop, so no sample batch is dead code.
    private static volatile double consumedSum;

    private NoiseProfiler() {}

    /** Precomputed workload, shared by every kernel; generation/allocation is outside the timer. */
    public static final class Workload {
        private final double[] x;
        private final double[] z;

        public Workload(int sampleCount) {
            if (sampleCount <= 0) {
                throw new IllegalArgumentException("sample count must be positive");
            }
            x = new double[sampleCount];
            z = new double[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                // Fixed strides scan positive/negative cells without hitting integer lattice points.
                // Period 65536 is intentional: repeated traversals use the same spatial workload.
                x[i] = ((i * 73L) & 0xffffL) - 32768.0 + 0.375;
                z[i] = ((i * 193L) & 0xffffL) - 32768.0 + 0.625;
            }
        }

        public int sampleCount() { return x.length; }
        public double x(int index) { return x[index]; }
        public double z(int index) { return z[index]; }
    }

    public record Result(String kernelName, int sampleCount, long elapsedNanos,
                         double min, double max, double mean) {
        public double nanosPerSample() { return (double) elapsedNanos / sampleCount; }
    }

    public record Kernel(String name, PhaseNoise noise, String configuration) {}

    /** Local profiling fixtures, not Phase/worldgen presets. */
    public static List<Kernel> kernels() {
        return List.of(
                new Kernel("GradientNoise", new GradientNoise(0.004), "frequency=0.004"),
                new Kernel("RidgedNoise", new RidgedNoise(0.004, 2), "frequency=0.004, octaves=2"),
                new Kernel("WorleyNoise", new WorleyNoise(0.004), "frequency=0.004"),
                new Kernel("DomainWarpNoise", new DomainWarpNoise(0.004, 0.008, 32),
                        "baseFrequency=0.004, warpFrequency=0.008, strength=32"));
    }

    public static void warmUp(PhaseNoise noise, Workload workload) {
        sampleBatch(noise, workload, WARMUP_SAMPLES);
    }

    public static Result profile(String name, PhaseNoise noise, Workload workload) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(noise, "noise");
        Objects.requireNonNull(workload, "workload");
        long start = System.nanoTime();
        Summary summary = sampleBatch(noise, workload, workload.sampleCount());
        long elapsed = System.nanoTime() - start;
        return new Result(name, workload.sampleCount(), elapsed,
                summary.min, summary.max, summary.sum / workload.sampleCount());
    }

    private record Summary(double min, double max, double sum) {}

    // Both warm-up and measurements exercise this SAME sampling/aggregation call site.
    private static Summary sampleBatch(PhaseNoise noise, Workload workload, int count) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        int point = 0;
        for (int i = 0; i < count; i++) {
            double value = noise.sample(SEED, workload.x[point], workload.z[point]);
            if (!Double.isFinite(value)) {
                throw new IllegalStateException("Non-finite noise result at sample " + i);
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
            if (++point == workload.sampleCount()) {
                point = 0;
            }
        }
        if (!Double.isFinite(sum)) {
            throw new IllegalStateException("Non-finite noise aggregate");
        }
        consumedSum = sum;
        return new Summary(min, max, sum);
    }

    public static void main(String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: NoiseProfiler [all|kernelName] [sampleCount]");
        }
        String selection = args.length > 0 ? args[0] : "all";
        int count = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_SAMPLES;
        List<Kernel> kernels = kernels();
        if (!selection.equalsIgnoreCase("all")
                && kernels.stream().noneMatch(kernel -> kernel.name.equalsIgnoreCase(selection))) {
            throw new IllegalArgumentException("Unknown kernel: " + selection
                    + "; use all, GradientNoise, RidgedNoise, WorleyNoise, DomainWarpNoise");
        }
        Workload workload = new Workload(count);
        System.out.printf(Locale.ROOT, "Java %s; %s; %s; processors=%d%n",
                System.getProperty("java.runtime.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
        System.out.printf(Locale.ROOT, "seed=0x%x, samples/kernel=%d, fixed warm-up/kernel=%d%n",
                SEED, count, WARMUP_SAMPLES);
        System.out.println("Elapsed includes sampling, finite checks and min/max/sum aggregation.");
        // Warm ALL implementations before ANY timing to avoid first-type/class-loading bias.
        for (Kernel kernel : kernels) {
            System.out.println(kernel.name + ": " + kernel.configuration);
            warmUp(kernel.noise, workload);
        }
        Result baseline = profile(kernels.getFirst().name, kernels.getFirst().noise, workload);
        System.out.printf(Locale.ROOT, "%n%-18s %10s %12s %12s %11s %11s %11s %9s%n",
                "kernel", "samples", "elapsed(ms)", "ns/sample", "min", "max", "mean", "x Smooth");
        print(baseline, baseline.nanosPerSample());
        for (Kernel kernel : kernels.subList(1, kernels.size())) {
            if (selection.equalsIgnoreCase("all") || kernel.name.equalsIgnoreCase(selection)) {
                print(profile(kernel.name, kernel.noise, workload), baseline.nanosPerSample());
            }
        }
    }

    private static void print(Result result, double baselineNanos) {
        System.out.printf(Locale.ROOT, "%-18s %10d %12.3f %12.2f %11.6f %11.6f %11.6f %8.2fx%n",
                result.kernelName, result.sampleCount, result.elapsedNanos / 1_000_000.0,
                result.nanosPerSample(), result.min, result.max, result.mean,
                result.nanosPerSample() / baselineNanos);
    }
}
