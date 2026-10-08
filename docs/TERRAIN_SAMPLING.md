# WPH-008 TerrainSampleService

Production API in `dev.fangbm.worldphase.worldgen.sample`:

```java
var sampling = new TerrainSampleService(server);
double signal = sampling.sampleNoise(PhaseId.CELL, x, z);
int solidY = sampling.sampleHeight(PhaseId.CELL, x, z);
TerrainSample sample = sampling.sample(PhaseId.CELL, x, z);
TerrainComparison comparison = sampling.compare(PhaseId.SMOOTH, PhaseId.CELL, x, z);
```

Coordinates are integer block x/z. Construct the service for one MinecraftServer
lifetime and call it synchronously on that server's thread. Off-thread calls fail
before accessing worlds. The service does not schedule work or cache Levels,
RandomStates, samples or density instances. Every invocation resolves the current
four Levels via PhaseWorldResolver and requires all four to be loaded with the
same raw seed. This deliberately rejects partial Phase setups, even when sampling
only one Phase. Missing dimensions and mismatched seeds throw IllegalStateException
with the offending key/seed; there is no Overworld or other Phase fallback.

TerrainSample is an immutable record `(phase, x, z, baseHeight, rawNoise)`.
TerrainComparison stores only `firstSample` and `secondSample`; both samples must
have identical x/z. `heightDelta()` and `noiseDelta()` derive **second minus first**
from them, so redundant stored delta fields cannot become inconsistent. Same-Phase
comparisons are allowed and have zero deltas.

## Height contract

`baseHeight` is the highest motion-blocking **base terrain block y**, computed as:

```java
generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
        targetLevel, targetLevel.getChunkSource().randomState()) - 1
```

26.1's getBaseHeight returns the first-free height above the matching block. The
minus one matches WPH-006/WPH-007 base-column diagnostics. With no matching block,
Vanilla returns the Level's minimum y; the service returns `minY - 1` as an empty
column sentinel, not a valid landing position.

OCEAN_FLOOR_WG uses Vanilla's motion-blocking-material predicate and excludes
water/lava as height candidates. The generator uses the **actual loaded preset
and seeded RandomState**, with its interpolated density/cave/aquifer composition.
The accessor is the target Level, so its vertical bounds participate in sampling.
This is not a live chunk heightmap: surface-rule transformations, carvers run in
later generation stages, structures/terrain adaptation, features/trees, entities
and player edits are not evaluated. It does not assert that a FULL chunk's surface
or a safe standing position equals this y.

No `getChunk`, live `Level.getHeight(type,x,z)`, chunk tickets, FULL chunk generation,
features or entities are requested. Vanilla evaluates a temporary noise column;
sampling still has a CPU cost, but does not create persistent chunks. There is no
new ChunkGenerator or copied terrain composition.

## Noise contract and seed

The only static metadata is four density-function ResourceKeys:

| Phase | Entry in Registries.DENSITY_FUNCTION |
| --- | --- |
| SMOOTH | worldphase:smooth_noise |
| RIDGE | worldphase:ridge_noise |
| CELL | worldphase:cell_noise |
| FRACTURE | worldphase:fracture_noise |

Each call reads the target Level's dynamic registry, obtains the holder's value,
requires an existing PhaseNoiseDensityFunction template and calls:

```java
template.withSeed(targetLevel.getSeed())
        .compute(new DensityFunction.SinglePointContext(x, 0, z))
```

Missing entries or entries of another density type fail explicitly. No frequency,
amplitude, octave, warp or profile constants are copied into Java. The raw signal
is the **configured adapter output, including its JSON amplitude**, before Ridge
or Cell remapping and before Vanilla + Phase terrain composition. It is not
unit-amplitude noise, final density or a height estimate. Y=0 is intentional: the
existing adapter is 2D and ignores y. The shared template remains unbound and
unmodified; withSeed makes an immutable bound instance using the actual raw Level
seed. Height sampling uses the live RandomState already bound by the unchanged
WPH-005 hook. There is no arbitrary caller seed, phase-index seed offset, mutable
Random or new seed-binding Mixin.

## Verified Minecraft 26.1 API

Checked the pinned NeoForge 26.1.0.19-beta generated Minecraft sources, then
exercised them in tests and on a dedicated server:

| Responsibility | API |
| --- | --- |
| Current Level | MinecraftServer.getLevel(PhaseWorldResolver.levelKey(phase)) |
| Raw seed / thread | ServerLevel.getSeed() / MinecraftServer.isSameThread() |
| Generator / seeded state | ServerChunkCache.getGenerator() / randomState() |
| Base height | NoiseBasedChunkGenerator.getBaseHeight(int,int,Heightmap.Types,LevelHeightAccessor,RandomState) |
| Data-driven template | level.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION).get(key).value() |
| Coordinates | DensityFunction.SinglePointContext(int,int,int) |

Technical Design v0.1's conceptual sampling capabilities are retained. The server
instance, thread contract, height minus-one convention, configured signal semantics
and two-sample comparison record make the previously unspecified details explicit.
No toolchain, worldgen preset, PhaseDefinition, kernel or golden value changes.

## Verification and reproduction

```sh
./gradlew test --tests dev.fangbm.worldphase.worldgen.sample.TerrainSampleServiceTest --no-build-cache
./gradlew test --tests 'dev.fangbm.worldphase.worldgen.*' --no-build-cache
./gradlew build --no-build-cache
./gradlew runPhaseVerificationSamplingServer --console=plain --no-build-cache
```

The last command loads the existing isolated verification mod in **sampling mode**;
it does not run the WPH-007 FULL-chunk/structure/client probe. Its directory is
`run/terrain-sampling-verification-server`. Prepare that directory's accepted
`eula.txt` and `server.properties` with `level-seed=123456789`,
`server-ip=127.0.0.1`, an unused `server-port` (validated: 25570), `online-mode=false`,
`view-distance=2`, `simulation-distance=2`. The probe writes status/report there,
halts automatically, and the Gradle task fails unless status is PASS. Ordinary
client/server runs still load only the production mod. No verification classes,
reports or game directories are packaged in its JAR.

Unit tests use real production JSON codecs, registry holders, generators and
seeded RandomStates, with mocked server/Level/cache shells. They cross-check height
and noise independently, test all four Phases, order/repeat determinism, finite
signals, compare arithmetic/differences, missing Levels, inconsistent seeds,
missing/wrong density entries, wrong generators, off-thread rejection, current
seed/registry/RandomState reads, amplitude overrides, water exclusion and the empty
column sentinel. They also inspect Level/cache calls to reject chunk requests and
live heightmap reads. The test-only reference expansion does not substitute for
real datapack loading; the dedicated smoke exercises that loading separately.

Full build: **162 tests, zero failures/errors/skips**: 30 sampling, 6 Phase world,
3 Smooth world, 1 Fracture preset, 34 unchanged kernel/golden regression,
51 density/codec, 29 Phase domain, 8 profiler. Focused sampling + worldgen + noise
run: 74 tests, all passing. Production JAR contains the three sampling classes;
no test or verification classes/resources. All production presets, PhaseDefinition
and kernel source files are byte-for-byte unchanged from main.

Dedicated smoke on 2026-10-08, Windows x64 / Temurin 25.0.3+9, seed 123456789:

- Reached Done (2.731s), stopped/saved cleanly, Gradle exit 0; no registry, density
  codec or Mixin errors.
- 44 samples: four Phases at the ten WPH-007 coordinates plus (-1000000,2000000).
  Each matches the real Level generator/base-height call and registry-template
  evaluation exactly. Reverse order/repeat and compare arithmetic also match.
- Smooth/Ridge: 11/11 differing heights; Smooth/Cell: 11/11; Smooth/Fracture: 9/11;
  Ridge/Cell: 11/11. Broad differences, not a height golden snapshot.
- Loaded Phase chunks before/after sampling: **0/0 for every Phase**. Server startup
  may generate ordinary Overworld spawn chunks; the service probe loads none.

Example at (-512,768): Smooth y=73, Ridge=124, Cell=47, Fracture=78. These are
observations, not long-term assertions. The ignored report records all coordinates
and configured raw signals. No new visual comparison is needed for this service.

Next after review: **#9 / WPH-009 PhaseTransferService**. No transfer, commands,
tutorial, Scanner, preview or structure/entity repair is implemented here. The
WPH-007 villager suffocation risk remains outside this issue.
