# WPH-004 noise profiling helper

Run the development-only helper with JDK 25:

```sh
./gradlew noiseProfile
./gradlew noiseProfile -PnoiseProfileSamples=5000000
./gradlew noiseProfile -PnoiseProfileKernel=WorleyNoise -PnoiseProfileSamples=1000000
```

The default is all four kernels and **1,000,000 measured samples per kernel**.
The sample count must be a positive integer. Kernel selection is case-insensitive:
`all`, `GradientNoise`, `RidgedNoise`, `WorleyNoise`, `DomainWarpNoise`.
Smooth/Gradient is always measured as the comparison baseline, including when
selecting a single other kernel. PowerShell can use `gradlew.bat` with the same
task and properties.

The helper is in `src/profiling/java/dev/fangbm/worldphase/dev/noise/NoiseProfiler.java`,
a separate Gradle source set. `noiseProfile` launches a fresh Java 25 process
with the main and profiling outputs only; it does not start Minecraft, download
game assets or need a NeoForge runtime. Compiling the production sources still
uses the project's existing Minecraft compile setup. Profiling classes are
available to the separate `profilingTest` source set but excluded from the mod JAR.
`testNoiseProfiler` runs ordinary JUnit without the NeoForge test loader and is
included in `check`/`build`; existing mod tests still use the original `test` task.
The existing JUnit versions are reused. No new library dependency is
added, and `noiseProfile` is not part of `build`/CI timing assertions.

## Workload and timing

- All kernels share seed `0x574f524c44504841` and identical precomputed x/z arrays.
- Coordinates use fixed integer strides, with negative/positive values and
  non-integer offsets: `x=((i*73)&65535)-32768+0.375`,
  `z=((i*193)&65535)-32768+0.625`. The 65,536-point period is intentional;
  increasing the count repeats the same traversal rather than adding random work.
- Allocation, coordinate generation, class setup and printing are outside the timer.
- **1,000,000 fixed warm-up samples for each of all four kernels** finish before
  any measurement, even in single-kernel mode. Warm-up cycles the requested
  coordinate array. It uses the same sampling/aggregation loop as timing, so
  its shared call site sees every implementation before results are compared.
- `System.nanoTime()` measures one batch per kernel. The timed batch includes
  sampling, finite-value checks, min/max/sum aggregation and a final volatile
  checksum publication. Warm-up sums are also published; measured statistics
  are printed. This makes the computed results observable and prevents a dead
  sample loop from being optimized away. There are no per-sample volatile writes.
- Output includes kernel name, sample count, total elapsed milliseconds,
  ns/sample, min, max, mean and the ratio to Smooth. The JVM and fixture settings
  are printed as context. No absolute speed or guardrail is a test assertion.

Fixtures are local profiling inputs, **not worldgen/Phase presets**:

| Kernel | Configuration |
| --- | --- |
| GradientNoise | frequency=0.004 |
| RidgedNoise | frequency=0.004, octaves=2 (fixed kernel lacunarity/persistence) |
| WorleyNoise | frequency=0.004 |
| DomainWarpNoise | baseFrequency=0.004, warpFrequency=0.008, strength=32 |

For regression comparisons, keep JVM, sample count and configuration unchanged
and repeat the task in separate processes when a result looks suspicious.
Scheduler load, CPU boost and JIT compilation still affect timings; a small
difference is not proof of a kernel regression. Shared statistics/array-access
overhead is included and can compress ratios. This helper catches large local
regressions; it does not measure pure kernel nanoseconds, chunk-generation time
or steady-state gameplay performance, and is not a JMH replacement.

## Local validation record

2026-10-07 (Asia/Shanghai), Windows x64, AMD Ryzen 9 8945HX (16 cores / 32 logical
processors), Temurin 25.0.3+9-LTS / OpenJDK 64-Bit Server VM. Used the existing
machine-local ASCII Gradle cache and Java temporary directory documented in
[Development setup](DEVELOPMENT.md). No machine paths are added to build configuration.

Representative command, repeated in two separate JVMs:

```sh
./gradlew noiseProfile -PnoiseProfileSamples=5000000
```

First run, with 1,000,000 warm-up calls per kernel:

| Kernel | Samples | Total ms | ns/sample | Min | Max | Mean | × Smooth |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| GradientNoise | 5,000,000 | 259.466 | 51.89 | -0.465400 | 0.495328 | 0.000126 | 1.00 |
| RidgedNoise | 5,000,000 | 565.451 | 113.09 | 0.614036 | 0.999657 | 0.875418 | 2.18 |
| WorleyNoise | 5,000,000 | 424.346 | 84.87 | 0.003490 | 1.100839 | 0.428436 | 1.64 |
| DomainWarpNoise | 5,000,000 | 835.262 | 167.05 | -0.464881 | 0.495607 | 0.000101 | 3.22 |

Second run:

| Kernel | Total ms | ns/sample | × Smooth |
| --- | ---: | ---: | ---: |
| GradientNoise | 248.019 | 49.60 | 1.00 |
| RidgedNoise | 549.150 | 109.83 | 2.21 |
| WorleyNoise | 402.341 | 80.47 | 1.62 |
| DomainWarpNoise | 820.588 | 164.12 | 3.31 |

The printed statistics agree across both runs. The initial default 1,000,000
sample run gave Ridge 2.29×, Cell 1.69× and Fracture 3.47×. Increasing the timed
batch reduced its exposure to short timing interruptions while preserving
the workload definition, seed and kernel configurations.

Cell is below the approximate 3× target and well below a pathological 10–15×.
Ridge and Fracture remain **slightly above** the approximate 2× / 3× guardrails
on this machine; these targets are not claimed to pass. All kernels use the
same workload/aggregation and finish warm-up before timing. Code inspection
finds two base evaluations per Ridge sample, three per DomainWarp sample plus
channel mixing; there is no recursive warp, point table allocation or redundant
sample pass. Those evaluation counts and small wrapper costs are consistent
with the observed ratios, though this lightweight measurement cannot attribute
individual JIT/CPU effects. There is no clear algorithm bug to justify rewriting
kernels to chase these small differences. **No kernel source, mathematical
behavior or golden fixture changes are made in WPH-004.**

## Tests and scope

```sh
./gradlew testNoiseProfiler
./gradlew test --tests dev.fangbm.worldphase.worldgen.noise.NoiseKernelsTest
./gradlew build
```

Profiler tests check sample counts, exact small min/max/mean fixtures, finite
results, deterministic seed/coordinates, fixed warm-up consumption and
repeatable aggregates from all four kernels. They do not assert elapsed time,
throughput or a guardrail ratio. Existing bit-exact noise golden tests remain
unchanged.

This issue adds only development profiling/regression tooling. No DensityFunction,
dimension/worldgen JSON, `/wp` command or worldgen integration is implemented.
The WPH-005 [density adapter](PHASE_DENSITY.md) is documented separately;
profiler scope and kernel behavior remain unchanged.
