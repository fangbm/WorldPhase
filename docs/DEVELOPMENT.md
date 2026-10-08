# WorldPhase development

## WPH-001 scope

This bootstrap implements only Issue #1, under the Week 1 tracking Issue #16.
It adds a common, dedicated-server-safe `worldphase` entry point and a startup
log message. Packages under `dev.fangbm.worldphase` follow Technical Design
v0.1 section 4; `package-info.java` files reserve their responsibilities without
implementing PhaseId, registries, terrain, transfer, commands or tutorials.

## Toolchain

| Component | Pinned version |
| --- | --- |
| Minecraft | 26.1 |
| NeoForge | 26.1.0.19-beta |
| Java toolchain | 25 |
| ModDevGradle | 2.0.148 |
| Gradle wrapper | 9.2.1 |
| Foojay toolchain resolver | 1.0.0 |

The baseline follows the [official Minecraft 26.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/tree/15b9ce62ebd2ea80871cea2ebb1b6551ef972280).
The wrapper scripts/JAR and template license are copied from that revision.
The distribution SHA-256 comes from Gradle's official distribution endpoint.
[NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
confirms the pinned release for Minecraft 26.1 (hotfix 0).

[NeoForge's 26.1 release notes](https://neoforged.net/news/26.1release/)
explain the four-component version format and the Java 25 / Gradle 9.1+
requirements. `26.1.0.19-beta` concretizes the design's `26.1.x`; this is not
a switch to Minecraft 26.1.1 or 26.1.2. No compatibility hacks, Parchment,
mixins or access transformers are needed for this bootstrap.

Metadata follows the current MDK's `neoforge.mods.toml` format. Minecraft is
restricted to `[26.1]`, and NeoForge to `[26.1.0.19-beta,26.1.1)` so a different
Minecraft hotfix cannot silently satisfy the loader dependency. The project
uses `All Rights Reserved` because the repository has not chosen a project
license; `TEMPLATE_LICENSE.txt` applies to the upstream template only.

## Build

Install a JDK 25 and point `JAVA_HOME` at it. The wrapper uses `JAVA_HOME` (or
`java` on PATH) to launch Gradle; the Java 25 compile toolchain is configured
separately. Foojay can provision a missing compile toolchain once Gradle runs.
First builds require network access to Gradle, Maven and Minecraft downloads.

```sh
./gradlew build
```

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat build
```

The mod JAR is written to `build/libs/worldphase-0.1.0-dev.jar`.

## Client

```sh
./gradlew runClient
```

Uses `run/client`. On first launch, complete Minecraft's initial accessibility
screen and check the main menu. The Mods screen should list WorldPhase.
Quit through the game menu to end the Gradle task normally.

## Dedicated server

```sh
./gradlew runServer
```

Uses `run/server` and `--nogui`. NeoForge 26.1's development runtime automatically
agrees to the EULA and uses offline mode; this Gradle run configuration is for
local development. A standalone installed server still requires the normal
Minecraft EULA acceptance and appropriate server settings. Wait for
`Done (...)! For help, type "help"`, then enter `stop` for a clean shutdown.
Server console input is forwarded by the Gradle run task. Any generated
`server.properties`, worlds and logs stay under the ignored game directory.

PowerShell equivalents are `.\gradlew.bat runClient` and
`.\gradlew.bat runServer`. IDEs can import the Gradle project; ModDevGradle
generates the launch configurations on sync. IDE configuration files and all
game directories remain local and are ignored by Git.

## WPH-001 validation record

Validated on 2026-10-07 (Asia/Shanghai), Windows x64, Temurin
25.0.3+9 and an NVIDIA RTX 5070 Ti Laptop GPU.

| Check | Command / action | Observed result |
| --- | --- | --- |
| Compile/package | `gradlew.bat build` | `BUILD SUCCESSFUL`; JAR created |
| Unix wrapper | `./gradlew build` in Git Bash | `BUILD SUCCESSFUL` (cached tasks); wrapper works with LF and executable bit |
| Client | `gradlew.bat runClient` | Welcome screen completed; actual main menu visually inspected, showing Minecraft 26.1 / NeoForge 26.1.0.19-beta |
| Mod discovery | Select WorldPhase on the Mods screen | `ModID: worldphase`, `Version: 0.1.0-dev`, `State: loaded`; common bootstrap log also present |
| Client shutdown | Click Quit Game | `Stopping!`, Gradle `BUILD SUCCESSFUL`, exit 0 |
| Dedicated server | `gradlew.bat runServer` | Initial new-world boot: `Done (1.870s)!`; second boot: `Done (0.274s)!`; bootstrap log on both |
| Server shutdown | Enter `stop` in the terminal | All dimensions saved, Gradle `BUILD SUCCESSFUL`, exit 0 on both runs |
| Packaging / hygiene | `jar tf build/libs/worldphase-0.1.0-dev.jar`, `git diff --cached --check`, `git check-ignore` | Only entry class and mod metadata in JAR; diff clean; build, IDE and game output ignored |

The first dependency download failed with a TLS handshake interruption for
`lwjgl-3.4.1-natives-windows-arm64.jar`. The successful initial build and first
client/server launches used this machine's existing HTTP proxy via command-line
JVM properties (`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890
-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890`). No proxy settings are
committed. The subsequent Git Bash build and second server launch succeeded
without those properties, using the downloaded cache.

On the first server boot Minecraft logged a missing `server.properties` before
creating it and successfully starting. The second boot had no ERROR entries.
The second server smoke run used `server-ip=127.0.0.1` in the ignored local
properties file. Upstream command-ambiguity, beta rendering and Windows
performance-counter warnings did not prevent startup; the development server's
offline-mode warning is expected. No gameplay regression tests are added in
this bootstrap (`test NO-SOURCE`); those belong to WPH-012. This validates
development launch configurations, not a production server installation or any
future Phase gameplay.

No scope or target-stack deviation from the design documents was necessary.
Current official APIs/configuration worked directly. Exact versions and the
development EULA/offline behavior are documented above.

## Phase domain unit tests (WPH-002)

```sh
./gradlew test --tests dev.fangbm.worldphase.phase.PhaseDomainTest
./gradlew build
```

JUnit Jupiter 5.13.4 and Mockito 5.21.0 are test-only dependencies. The minimal
[ModDevGradle JUnit configuration](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#unit-testing-with-junit)
enables Minecraft/NeoForge classes in tests, with WorldPhase as the tested mod.
Mockito uses its subclass mock maker to stub `ServerLevel.dimension()` without
an inline-mocking agent; NeoForge bootstraps its own test environment. The
`test` task is included in `build`; its HTML report is
`build/reports/tests/test/index.html`.

These tests validate the domain model and Level-key resolution. They do not
generate Phase worlds or test worldgen, transfer, commands or tutorials.
See [Phase domain API](PHASE_DOMAIN.md) for the contract and scope.

Validated on 2026-10-07 (Asia/Shanghai), Windows x64 / Temurin 25.0.3+9:

- Focused `PhaseDomainTest` run: **29 tests, 0 failures/errors/skips**.
- `./gradlew build` in Git Bash: **BUILD SUCCESSFUL**, including a fresh test
  execution with the same 29 passing cases and the packaged domain classes.
- JAR inspection confirms no test classes, mocking resources or test libraries
  are packaged. `git diff --check` passes; outputs remain ignored.

The initial test worker failed before test execution with
`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`
under this machine's non-ASCII Gradle home. A separate ASCII cache directory
resolved worker startup. An ASCII Java temporary directory also avoided a
NeoForge self-attach error under the user temporary directory. The successful
build used these local environment values, outside the repository:

```powershell
$env:GRADLE_USER_HOME = 'D:\codex-temp\worldphase-gradle-ascii'
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir=D:\codex-temp\worldphase-java-tmp'
```

Set `JAVA_HOME` to JDK 25 as above. These paths are machine-local validation
settings, not project requirements or committed Gradle configuration. Use your
own ASCII directories if encountering the same Windows worker/temporary-path
issue. The successful full build required no proxy command-line properties.

## Noise kernel tests (WPH-003)

```sh
./gradlew test --tests dev.fangbm.worldphase.worldgen.noise.NoiseKernelsTest
./gradlew cleanTest test --tests dev.fangbm.worldphase.worldgen.noise.NoiseKernelsTest
./gradlew build
```

Validated on 2026-10-07 (Asia/Shanghai), Windows x64 / Temurin 25.0.3+9,
using the same local ASCII Gradle cache and Java temporary directory described
above. Commands used the Unix wrapper in Git Bash:

- Both focused runs: **34 tests, 0 failures/errors/skips**; `cleanTest` ensures
  golden values are checked again in a fresh test worker.
- Full build: **BUILD SUCCESSFUL**, **63 tests, 0 failures/errors/skips**
  (34 noise tests plus 29 existing Phase domain tests).
- All production noise sources also compile with JDK 25 `javac` alone, without
  a Minecraft/NeoForge classpath. This verifies the pure Java dependency boundary.
- JAR inspection and `git diff --check` verify production packaging and source
  hygiene; build output, standalone probes and game directories remain ignored.

Tests cover bit-exact golden results, repeated calls/fresh instances, sampling
order, different seeds, finite output/ranges, Smooth continuity, normalized
ridge behavior, deterministic Worley features and exact search across cell
boundaries, and independent warp channels. A wider unpruned Worley reference
search checks the bounded/pruned search; one pinned fixture would fail a 3×3
implementation. These tests validate kernels only, not terrain generation.

See [Noise kernels](NOISE_KERNELS.md) for algorithms, ranges, seed/channel
contract, numerical limits, source/license attribution and the intentional
`GradientNoise` / fixed ridge sharpness choices relative to Technical Design v0.1.

## Noise profiling helper (WPH-004)

```sh
./gradlew noiseProfile
./gradlew noiseProfile -PnoiseProfileSamples=5000000
./gradlew noiseProfile -PnoiseProfileKernel=WorleyNoise -PnoiseProfileSamples=100000
./gradlew testNoiseProfiler
./gradlew cleanTest test --tests dev.fangbm.worldphase.worldgen.noise.NoiseKernelsTest --no-build-cache
./gradlew build --no-build-cache
```

`noiseProfile` runs development classes from `src/profiling/java`, with no
Minecraft runtime. The ordinary JUnit task `testNoiseProfiler` checks the
profiler from `src/profilingTest/java` and is included in `check`/`build`.
This keeps tooling classes outside NeoForge's mod test loader and the final
mod JAR; no gameplay API or benchmark library is added.

Validated on 2026-10-07 (Asia/Shanghai), Windows x64 / AMD Ryzen 9 8945HX /
Temurin 25.0.3+9, using the same local ASCII cache/temp settings as above:

- Profiler tests: **8 tests, 0 failures/errors/skips**.
- Fresh noise golden/regression run (build-cache disabled): **34 tests,
  0 failures/errors/skips**. Kernels and golden fixtures are unchanged.
- Full build: **BUILD SUCCESSFUL**, with **71 passing tests** across both test
  tasks (29 Phase + 34 noise + 8 profiler), no failures/errors/skips.
- Repeated 5,000,000-sample runs in separate JVMs: Worley **1.62–1.64×** Smooth,
  Ridge **2.18–2.21×** and DomainWarp **3.22–3.31×**. The latter two are slightly
  above the approximate guardrails; no clear kernel bug was found and no
  kernel optimization or mathematical change is made.
- JAR inspection excludes dev/test classes. Diff checks pass; logs and all
  build/game output remain ignored.

See [Noise profiling](NOISE_PROFILING.md) for exact fixture settings, workload,
warm-up/consumption design, full result tables and measurement limits. Timings
are local comparison evidence, not performance assertions or worldgen validation.

## Phase noise density function (WPH-005)

```sh
./gradlew test --tests dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunctionTest --no-build-cache
./gradlew test --tests dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunctionTest \
  --tests dev.fangbm.worldphase.worldgen.noise.NoiseKernelsTest \
  --tests dev.fangbm.worldphase.phase.PhaseDomainTest testNoiseProfiler --no-build-cache
./gradlew build --no-build-cache
./gradlew runDensitySmokeServer --console=plain
```

The density adapter registers `worldphase:phase_noise` with a MapCodec in
Vanilla's existing density-function type registry. It uses the pinned 26.1
`DensityFunction.SimpleFunction` / `KeyDispatchDataCodec` APIs. `FunctionContext`
has coordinates but no world seed: one required MixinExtras wrapper binds
WorldPhase leaves during RandomState's existing router wiring pass, retaining
the original Vanilla visitor. See [Phase density API](PHASE_DENSITY.md) for the
actual API/schema, seed path, defaults/ranges and the necessary seed-hook detail
absent from Technical Design v0.1.

Validated on 2026-10-07 (Asia/Shanghai), Windows x64 / Temurin 25.0.3+9,
using the previously documented local cache/temp environment:

- Focused density tests: **51 tests, 0 failures/errors/skips**.
- Requested regression suites: **51 density + 34 noise + 29 Phase + 8 profiler**,
  all passing. Noise math, golden fixtures and PhaseDefinition are unchanged.
- Full build: **BUILD SUCCESSFUL**, **122 tests**, zero failures/errors/skips.
- In-memory Vanilla `NoiseBasedChunkGenerator.getBaseColumn` test proves that
  adding the modifier changes stone/air decisions while retaining Vanilla
  generator/settings; no Phase dimension or world preset is created.
- Isolated server loads the test-only datapack and reaches `Done (1.588s)` on
  first boot and `Done (0.354s)` on final-adapter restart. Enabled pack listing
  includes `file/phase-noise-fixture`. Both shut down with `stop` and exit 0.
- No startup ERROR, density registry/codec failure or Mixin failure. Known
  upstream command-ambiguity/Windows-counter/offline-dev warnings persist.
- JAR contains density classes, the seed Mixin and its configuration; no test
  classes, profiler, datapack fixture, dimensions or noise-settings JSON.
  Generated sources, debug/game files and output remain ignored.

## WPH-006 Smooth world

Historical WPH-006 record: its Smooth-only source set/run names below were
replaced by the general WPH-007 tooling. Use the current commands in the next
section when running this checkout.

The mod now supplies `worldphase:smooth` through bundled dimension and worldgen
JSON. It uses Vanilla's Overworld dimension type, multi-noise biome preset,
NoiseBasedChunkGenerator, caves, aquifers, surface rules and structure/feature
pipeline. Only terrain density and preliminary surface estimation are modified;
the biome climate router remains identical. See [Smooth world](SMOOTH_WORLD.md)
for resource paths, parameter units, native entry commands and measured results.

```sh
./gradlew test --tests dev.fangbm.worldphase.worldgen.SmoothWorldTest --no-build-cache
./gradlew build --no-build-cache
./gradlew runSmoothVerificationServer --console=plain
# Repeat the same run to verify the saved world's fingerprints.
./gradlew runSmoothVerificationServer --console=plain
```

The isolated server run loads `src/smoothVerification` as a separate dev mod,
never packages it into WorldPhase, asserts seed 123456789, samples generated
chunks in both worlds, checks a simulated player's Vanilla teleport and three
Vanilla structure tags, flushes saves, and exits normally. On subsequent runs
it compares all recorded fingerprints/structure positions. A missing/failed
probe result fails the Gradle task. The ordinary client/server runs load only
the gameplay mod.

Before the first isolated run, accept Minecraft's EULA in
`run/smooth-verification-server/eula.txt` and create `server.properties` there
with `level-seed=123456789`, `server-ip=127.0.0.1`, an available `server-port`,
`view-distance=2`, and `simulation-distance=2`. The measured local port was
25568. Use a fresh verification directory when changing worldgen presets:
saved chunks must not be mistaken for newly generated terrain.

The run writes ignored `smooth-baseline.json`, `smooth-reload.json` and status
files in its own directory. These are local acceptance artifacts, not fixtures
or persistent mod state. It does not touch the ordinary client/server worlds.

`runSmoothVerificationClient` can load a copy of this stopped world via native
Quick Play and record six matching Overworld/Smooth views. See
[native client setup/results](SMOOTH_WORLD.md#native-client-check). Both
verification runs are isolated, load the extra dev mod only there, and fail if
their completion marker is absent. The graphical run was inspected on the
actual GPU; its screenshots stay ignored under its run directory.

Final validation: **125 tests**, zero failures/errors/skips; 200 macro-height
comparison edges average 13.095 blocks in Vanilla versus 6.590 in Smooth.
Twenty generated-region fingerprints and three structure coordinates matched
across disk save/stop/reload. A real client entered both worlds and rendered
forest, shores and water normally. Production packaging excludes all dev
verification code and game artifacts.

## WPH-007 four Phase worlds

Ridge, Cell and Fracture are now bundled dimensions alongside unchanged
Smooth. See [Phase worlds](PHASE_WORLDS.md) for the exact compositions,
parameter units, fixed-seed comparisons and acceptance observations.

```sh
./gradlew test --tests dev.fangbm.worldphase.worldgen.PhaseWorldTest \
  --tests dev.fangbm.worldphase.worldgen.noise.FracturePresetTest --no-build-cache
./gradlew build --no-build-cache
./gradlew runPhaseVerificationServer --console=plain
# Save/stop is automatic. Repeat to compare persisted FULL chunks.
./gradlew runPhaseVerificationServer --console=plain
./gradlew runPhaseVerificationClient --console=plain
```

`src/phaseVerification` replaces `src/smoothVerification`: one development
mod, Dist-gated server/client entry points, excluded from the production JAR.
Ordinary client/server/density-smoke runs still load only WorldPhase. Verification
is isolated under `run/phase-verification-{server,client}`. The dedicated probe
requires seed 123456789, creates FULL chunks, checks Vanilla teleport and
structures, writes its baseline/reload JSON, flushes saves and exits; a failed
or missing completion marker fails the Gradle task.

Before the first server run, create `eula.txt` with `eula=true` and
`server.properties` with `level-seed=123456789`, `server-ip=127.0.0.1`, an
unused port (local validation used 25569), `view-distance=2`, and
`simulation-distance=2` in its game directory. Use a fresh isolated directory
when modifying presets; do not reuse stale chunks as tuning evidence. Keep
the same directory and baseline for the unchanged preset's restart check.

For the graphical probe, copy the **contents** of the stopped server's `world`
into `run/phase-verification-client/saves/wph007` (`level.dat` directly inside
wph007). Complete Vanilla's initial accessibility onboarding if necessary,
then set `onboardAccessibility:false`, `pauseOnLostFocus:false` and
`tutorialStep:none` in this isolated client's options. The dev helper accepts
the copied experimental world's Backup and Join prompt. Quick Play uses this
copy, never an ordinary player's world. The helper selects spectator mode,
daytime, render distance 16, client simulation distance 5, and disables clouds for
unobstructed comparison. Window size is 1280x720.

Three same-coordinate cameras capture all five worlds (15 images, overwritten
on repeat). After a minimum 18 seconds, capture waits for the inner 14-chunk
view circle to load and the native renderer's section compile queue to finish
for one stable second. Missing readiness times out instead of capturing an
incomplete loading frame. Screenshots and game data remain ignored in the run directory.
The client exits once native capture completes and the Gradle task checks its
completion marker; inspect the images before claiming visual acceptance.

## Next issue

After WPH-007 review, the next issue is **#8 / WPH-008: Implement
TerrainSampleService**. This change stops at #7.
