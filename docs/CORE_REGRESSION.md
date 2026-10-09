# Core regression acceptance (WPH-012)

This is development tooling, not a gameplay feature. Production kernels, golden
values, PhaseDefinition, presets, sampling semantics, transfer policies and `/wp`
behavior are unchanged. There is no new test framework or GUI requirement.

## Run

Use the pinned Java 25 setup in [Development](DEVELOPMENT.md). After reading and
accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA), put `eula=true` in
`run/core-regression-server/eula.txt`, or use the explicit Gradle property below
on the first run:

```sh
./gradlew coreRegressionAll -PcoreRegressionAcceptEula=true --console=plain
```

Subsequent complete runs need only:

```sh
./gradlew coreRegressionAll --console=plain
```

| Task | Coverage |
| --- | --- |
| `coreRegression` | All existing unit suites, profiler tests and new report-accounting tests |
| `coreRegressionServer` | Fresh dedicated server, 50 complete cycles, save/stop, second JVM reload and another 50 cycles |
| `coreRegressionAll` | Both tasks, with unit tests before the cold server probe |
| `build` | Existing build/check tasks, including the same unit/report tests |

Windows can use `gradlew.bat` with the same arguments. Gradle retains normal
incremental unit-test behavior; append `--rerun-tasks` to force their execution.
The server tasks always execute. They use loopback port **25574**, offline mode,
seed **123456789**, view/simulation distance 2 and an isolated game directory.
Each invocation creates a new `world-wph012-<UUID>`; run identity is variable,
but seed, coordinates, inventory and workload are fixed. Earlier worlds are
preserved rather than recursively deleted. The reload JVM uses the same world.
Do not run two copies against that directory/port concurrently.

## Invariants and reuse

| Invariant | Existing coverage and integration evidence |
| --- | --- |
| Exact deterministic noise | Unchanged 34 golden tests; actual configured samples repeated and compared across cold/reload JVMs, including raw double bits |
| Four raw seeds equal | Reads `ServerLevel.getSeed()` for all four loaded Phase Levels |
| Same-coordinate terrain differences | Existing preset/sampling tests; real base columns at three distributed coordinates must not all have the same height |
| Exact double x/z and yaw/pitch | Existing transfer tests; every successful server move compares raw double/float bits and records maximum deviation |
| Bounded vertical candidates | Existing landing/transfer tests; real cycle requires exact, +1 and -1 landings; safe +5 fixture must be refused |
| Failure preserves source state | Existing missing-Level/native exception tests; real refusals verify Level, coordinates, rotation, UUID, inventory object and all slots |
| UUID/object/entity uniqueness | Existing transfer helper reused; additionally enumerates entities across every Level after each attempt, requiring exactly one matching UUID and the same player object |
| Inventory contents and dropped items | Every slot checked with `ItemStack.matches`; scans all Levels for dropped entities of any test-inventory item type |
| Actual safe destination | Existing `LandingSafety`, Vanilla collision/fluid/support APIs and the WPH-010 real landing probe, without copied collision mathematics |
| Save/stop/reload | Actual FULL chunk fingerprints, persisted fixture and native player `.dat` restored before any fixture reset |
| Cycle uses TransferService | Existing command tests; all 200 loop shifts per stage execute the registered `wp phase cycle` command; native veto and safe +5 rejection also exercise that path |

The new pure-Java `CoreInvariantLedgerTest` adds seven focused tests for counters,
bit-exact coordinates (including signed zero), rotation, bounded y, finite
statistics and diagnostic context/first-failure retention. It runs in the
existing plain-JUnit dev-tooling task, without loading Minecraft classes. The
existing missing-Level test also checks that a subsequent restored target works.

## Real server scenarios

A real `ServerPlayer` is registered in real `ServerLevel`s. Outbound packet sends
are simulated; native teleport, entity registration, chunk generation and actual
collision checks execute on the server thread. This is headless server evidence,
not another connected-client/login acceptance claim.

The loop starts in Fracture at **(-16.125, 220.5, 15.9375)**, with yaw **123.25**
and pitch **-37.5**. Its AABB crosses negative x and positive z chunk boundaries.
Isolated slab platforms support Fracture/Smooth/Cell at y=220.5 and Ridge at
y=221.5. Each of 50 iterations runs:

```text
FRACTURE -> SMOOTH (exact) -> RIDGE (+1) -> CELL (-1) -> FRACTURE (exact)
```

Every step checks the full player state and landing, not just the command return
value. The test inventory includes diamonds, iron, a clock, boots and an offhand
shield; empty slots are checked too. Platforms are deliberately high and local;
they do not tune or replace the worldgen presets.

Before and after the loops, seven scenarios run: fully blocked body space, no
support, powder-snow hazard, outside WorldBorder, native travel veto, genuinely
safe +5 landing outside the gameplay policy, and SAME_PHASE no-op. Each is
followed by two successful service transfers (Smooth and Fracture), proving
recovery without a fallback surface policy. Veto and +5 also refuse command
cycle. Missing Level and native exception/commit-boundary cases reuse existing
unit tests; no new rollback or arbitrary-exception atomicity guarantee is made.

## Persistence and reports

Reports and game data are ignored development output:

- `build/core-regression/summary.json`: complete acceptance summary and both stages.
- `run/core-regression-server/core-cold.json` and `core-reload.json`: stage results,
  versions, raw seeds, sample data, transfer counters, rejection scenarios,
  deviations, inventory checks and persistence results.
- `run/core-regression-server/core-run-world.txt`: current isolated world name.
- That world's `core-baseline.json`: comparison oracle, **not** player persistence.
  Player state is written to the actual `playerdata/<UUID>.dat` by Vanilla.
- Existing JUnit reports remain in `build/reports/tests/` and `build/test-results/`.

The pinned 26.1 implementation uses `PlayerList.getPlayerIo().save(player)`,
checks that the native `.dat` exists, then `saveEverything(false, true, true)`
and clean shutdown. A separate JVM uses `PlayerList.loadPlayerData(NameAndId)`;
the stored `ServerPlayer.TAG_DIMENSION` determines the restored Level.
`Entity.load(TagValueInput)` restores position, rotation, UUID and inventory.
Registry-aware `ItemStack.OPTIONAL_CODEC` records every slot. These comparisons
happen before any teleport, inventory replacement or platform reconstruction.
The original persisted supporting platform must already be safe on reload.

Terrain verification reuses WPH-007's FULL chunk fingerprint helper at
(-512,768) in every Phase, away from modified platforms. It compares block,
biome, height and pipeline diagnostics across JVMs; it is a selected-chunk
regression, not a proof for every coordinate or every hazard.

Task preparation resets aggregate/stage status to `RUNNING`, so an interrupted
run cannot reuse an old PASS. A completed failure records `status=FAIL`,
`failedInvariant`, `scenario` and `failure`; Gradle validates completion/counts
and returns nonzero even when Minecraft's own failure shutdown returns zero.
An abrupt process failure leaves `server.did_not_complete` and fails the task.
Duplicate counts on failed stage reports are -1 (not certified), rather than a
misleading zero. Cold `CHECKPOINT_SAVED` alone is not full acceptance; reload
must separately report PASS.

Failure-path self-test (intentionally exits nonzero):

```sh
./gradlew runPhaseVerificationCoreColdServer -PcoreRegressionFailInvariant=report.self_test --console=plain
```

Inspect `core-cold.json` for `failedInvariant=report.self_test`, then rerun
`coreRegressionAll` without that property. Ordinary failures identify scenario,
cycle/step/target and the failed invariant; inventory failures include the slot.

## Local acceptance — 2026-10-09

Minecraft **26.1**, NeoForge **26.1.0.19-beta**, Temurin **25.0.3+9-LTS**:

- `./gradlew coreRegression build --console=plain --no-build-cache`: PASS,
  **287 tests**, zero failures/errors/skips. Existing 280 tests are retained.
- Deliberate failure self-test above: expected **exit 1**, with
  `report.self_test` in both structured report and Gradle failure.
- `./gradlew coreRegressionAll --console=plain --no-build-cache`: PASS;
  **50 cold + 50 reloaded complete cycles**, **400 loop shifts**, **456 total
  successful transfers** including 56 recovery shifts, **495 inventory/state
  checks**, 28 recorded rejection/no-op scenarios plus command veto/+5 checks.
- Final `./gradlew coreRegressionAll build --console=plain --no-build-cache`:
  PASS again after strengthening entity enumeration and Phase-pair checks;
  the same counters and 287 passing tests were confirmed.
- Maximum x/z and yaw/pitch deviation **0**; maximum y correction **1**.
  Duplicate player entities and dropped test-item entities **0**.
- Actual player data, all four chunk fingerprints, base samples/raw signal bits
  and saved gold-block marker matched after save/stop/reload.
- `./gradlew runPhaseVerificationTransferServer --console=plain --no-build-cache`:
  PASS for the existing real exact/bounded landing scenarios, including hazards,
  water/lava, entities, headroom, slabs/stairs and +/-4 boundary cases.

Observed base heights (diagnostics, not new golden assertions):

| x,z | Fracture | Smooth | Ridge | Cell |
| --- | ---: | ---: | ---: | ---: |
| -512,768 | 78 | 73 | 124 | 47 |
| 256,256 | 93 | 64 | 90 | 76 |
| 1024,-1024 | 51 | 56 | 101 | 58 |

No production bug or design/API deviation was found. Existing Vanilla command
ambiguity and Windows PerfOS warnings are outside the `/wp` tree; no new
density/registry/Mixin error occurred. WPH-013 and village adaptation #25 remain
separate work. The known safety boundaries in [Phase transfer](PHASE_TRANSFER.md)
still apply.
