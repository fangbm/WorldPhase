# WPH-016-02 / Milestone B1 — wiring and numerical preflight

Status: **BLOCKED; no V2 terrain generation authorized**. Refs #36 / #34; neither issue is closed.
Milestone A merge: `75699957822322703a756019532eebbf97590a9c`.
The frozen r1 manifest remains byte-for-byte unchanged (LF-normalized SHA256
`521fc8d6e854570ae79f851129109d5948943f9ec372aee50643939d840ad2eb`).
Its historical proposal/status fields are not rewritten. Phase 2 remains NOT_APPROVED.

## Scope and implementation

All new Java implementation is in `src/phaseVerification`, enabled only by the verification source set.
Production phase mappings/resources, PhaseNoise, goldens, transfer semantics, SavedData and presets are unchanged.
No V2 dimension, datapack override, terrain world, screenshot or morphology experiment was created.
The three holdouts remain unsampled. Native integration uses bounded in-memory density/base-column probes;
server preparation uses newly named **legacy-only** worlds with the unchanged seven loaded dimensions.
Legacy spawn chunks do generate. “No FULL requests” below refers to the preparation code path, not to server startup.

### Actual 26.1 wiring

- `DensityFunction`, `KeyDispatchDataCodec`, `MapCodec` and
  `DeferredRegister<MapCodec<? extends DensityFunction>>` on `Registries.DENSITY_FUNCTION_TYPE`.
  Dev-only ID `worldphase:prototype_v2/analytic_r1`; no production registration entry is changed.
- Strict schema: required `profile=worldphase:prototype_v2/shared_r1`,
  `field=common_density|smooth_core|ridge_core|node_weight|protection`,
  named `reference=minecraft:overworld/continents`. Core fields additionally require the corresponding
  named `worldphase:prototype_v2/{smooth|ridge}/detail_r1` NoiseHolder, parameters 0/[1].
  Unknown keys/profile/field, wrong/missing holders or detail on non-core fields fail codec parsing.
  Raw seed and runtime context are not serialized. Runtime-bound round-trip restores an unbound leaf.
- Dev-only constructor `RandomState.<init>` / `NoiseRouter.mapAll` wrapper (`require=1`), using
  MixinExtras `WrapOperation`. Existing production seed visitor is unchanged. Both wrapper nesting orders
  visit every child/NoiseHolder once, delegate native wiring, and bind the constructor's raw long seed.
  No global/current-world seed or mutable Random is used. Context is shared only within that constructor.
- `mapAll` visits the reference child, optional NoiseHolder and the leaf. The immutable named serialization
  reference survives runtime holder expansion. RegistryOps retains each RegistrySetBuilder provider's real
  holder owner; rebuilding a generic lookup loses its UniversalOwner and correctly failed the initial test.
- The existing GradientNoise is called directly with frozen role mixing. NormalNoise output is divided by
  the **actual wired** `NoiseHolder.maxValue()` (observed 3.3333333333333335), finite and positive required.
  Unbound seed/noise, unprepared/failed/closed context, unsupported/ROI-exterior coordinates and nonfinite values fail.
- Analytic density bounds `[-134217731,134217741]` cover all int context y, not just build height.
  Frozen Hc bound [-7,133], Smooth [-22,140], Ridge [-21,185] and normalized detail <=.12 give margin
  for binary64 arithmetic. m/P bounds [0,1]. Tests include Integer.MIN/MAX y; runtime bounds are also checked.

### Native composition, surface and probe boundary

`NativeV2Fixture` checks pinned Vanilla Overworld settings bytes, replaces exactly the three
`sloped_cheese` references in the actual final AST, keeps native cave/noodle branches, and applies
`(1-P)*Fnative+P*Fcommon` **after** the final noodle cap. A forced P=1/negative-noodle operator test
would fail with the opposite ordering. It is a structure test, not proof of a real enabled safe node.

The settings change only final_density and preliminary_surface_level in memory. Surface uses the frozen
protected cave-free reference with native `find_top_surface`, lower -64, cell 8, upper 152/200.
Tests check its first positive sample and all higher rejected samples. Biome, climate, aquifer signals,
surface rules and other router slots retain Vanilla values; no FULL terrain or safe landing claim follows.

The explicit slide expression avoids Minecraft `lerp`'s constant-branch arithmetic reassociation; a bitwise test fixes the r1 operation order.

Native RandomState, SinglePointContext, NoiseChunk (`getInterpolatedNoiseValue`) and
`NoiseBasedChunkGenerator.getBaseColumn` are exercised. The six probes are in [preflight-evidence.json](preflight-evidence.json).
Raw density and native interpolated density differ away from interpolation vertices; the base column is an
actual native stone/air/fluid result, not a height estimate or FULL chunk. These differences are reported,
not forced to zero. Native preliminary minus highest base-column default-block y is Smooth -1/-2/-7 and Ridge -2/-2/0
for the three listed columns; Smooth non-air top is 62 (water). No surface-rule/feature pass or FULL height is inferred. With all certificates rejected, actual P=0 and no eligible valley edges exist.
Thus enabled-node interpolation/clearance, FULL caves/carvers/aquifers, collision and routes remain **BLOCKED/NOT_RUN**.

## Continuous certificate: result and blocker

See [NUMERICAL_CERTIFICATE.md](NUMERICAL_CERTIFICATE.md) for the continuous proof, outward binary64 rules,
model/program distinction and the non-sampling basis of the interval proof.

1. Native integer continents outputs define a fixed bilinear reference. Every vertex of every covered
   cell is included; corner extrema plus derivative/GradientNoise bounds enclose the entire region.
2. Height interval and model Lipschitz are conservative, with uniform program error E.
   The actual increment certificate is `L*d+2E`. r1 has no accepted minimum-distance/error-slack slope semantics.
   Model L alone must not authorize a rounded-program slope certificate.
3. A synthetic constant-continents/unchanged-GradientNoise test, seed 19, z=0, exhibits adjacent x values
   `0x1.2c00000000001p-39` and `0x1.2c00000000002p-39` with height-jump/distance about `3.51844e13`.
   This is not a sampled development/holdout terrain result and does not imply an unsafe block-scale slope;
   it demonstrates why treating ideal derivative as actual binary64 homogeneous Lipschitz is invalid.
4. Independently, all three actual development seeds have **0/477 model dry+slope candidates**.
   Rejection counts are reported below; this conservative proof failure does not prove every real location unsafe.
   No fallback or failed certificate enables a node. The shared graph and playable node availability are unproved.

Proposed r2 review topics: approve explicit model-plus-error/physical-distance semantics; assess whether raw
continents' high-frequency tail, conservative whole-region envelopes and .125 slope target are compatible.
No r2 change, epsilon, gate relaxation, coefficient tuning or additional seed search was implemented.

## Preparation measurements

Environment: Windows, AMD Ryzen 9 8945HX (16 cores / 32 logical), MC26.1,
NeoForge26.1.0.19-beta, Java25.0.3+9-LTS; Gradle9.2.1. No CPU affinity/OS disk-cache control.
The dedicated server run overlapped documentation work and one focused build; wall times include scheduling/GC.
This is a bounded preflight, not a CPU benchmark or a claim about production server latency.

### Cold test-worker observation

A fresh NeoForge test worker, cold certificate/column caches; registry/bootstrap wall 247.116ms,
CPU 234.375ms; first RandomState wall 39.031ms. The remaining seeds share the JVM/JIT.
“steps” are executed consecutively here and **are not real server ticks**.

| seed | nodes / raw calls | preparation wall / CPU | slowest step | wet/band reject | model-slope reject | enabled |
|---|---|---|---|---|---|---|
|123456789|477 / 2,077,812|2.214s / 2.203s|14.155ms|353|124|0|
|8675309|477 / 2,077,812|2.004s / 2.016s|0.761ms|365|112|0|
|20261011|477 / 2,077,812|1.993s / 2.000s|4.973ms|391|86|0|

Observed heap before→after: 176.08→141.50MB, 141.50→171.91MB, 171.91→126.84MB.
These are whole JVM heap snapshots, not retained-context allocation sizes or leak proofs.
All caches clear on close. No preparation call requests a Chunk/Level; raw noise evaluation only.

A fresh-worker repeat after the current registry-owner/manifest-source wiring checks also completed:
bootstrap wall/CPU 332.427/296.875ms; preparation wall/CPU by seed respectively
2.082/2.031s, 1.920/1.922s, 1.922/1.922s; slowest steps 14.645/.657/.630ms.
The initial observation and this repeat are separately preserved in the curated evidence, not averaged or selected
for a preferred outcome. Both have identical counts/rejections and zero enabled nodes.

### Actual dedicated-server ticks

Legacy-only dedicated servers, view-distance=2, simulation-distance=2; Post-tick preparation:

| seed | tick-gap wall | step wall sum | step CPU sum (coarse) | whole server-thread CPU | max step wall | rolling MSPT | heap before→after |
|---|---|---|---|---|---|---|---|
|123456789|202.909s|2.933s|0.609s|1.688s|5.991ms|0.054ms|191.22→192.37MB|
|8675309|202.909s|2.991s|1.797s|7.609s|5.561ms|0.055ms|349.51→482.08MB|
|20261011|202.911s|2.928s|0.984s|3.234s|8.118ms|0.063ms|284.57→313.93MB|

All three traversals completed; mechanical budgets observed PASS, cache count after close 0.
The cold-start worlds and empty contexts are new per seed, but the Vanilla noise JVM is already warm from legacy spawn.
This is **not** an interactive-ready approval: about 203 seconds wall per prepared seed/ROI union is substantial,
and yielded zero usable nodes. Automatic startup preparation and unrestricted-world V2 use remain unapproved.
Native generation throughput/1.5× target is NOT_RUN.

Each completed seed consumes 4,059 Post ticks, at most 512 raw calls/tick, total 2,077,812;
last step has 116 calls. Tick gaps make wall time about 203s despite a few seconds of aggregate work.
A single raw call cannot be preempted: if a real step exceeds 25ms, record BLOCKED, stop preparation,
and skip later seeds, preserving this result. Do not equate the configured calls limit to measured time.

ThreadMXBean on this Windows JVM has coarse CPU resolution; sums of tiny per-step CPU measurements
under-count work. Whole-pass worker CPU and whole server-thread CPU (which includes unrelated legacy ticks)
are reported separately; the latter is not pure preparation CPU. MSPT is Minecraft's current rolling estimate,
not a 4,059-tick global average. Preparation step wall time includes overhead and pauses.

## Validation and reproducibility

PASS: full `coreRegressionAll build` (379 JUnit cases: 378 passed, 1 opt-in measurement skipped),
17 focused numerical/native tests, opt-in fresh-worker preparation test, three Python evidence tests,
three real legacy-only server traversals, and evidence validation.
The optional measurement was separately executed and passed; no time threshold is a unit-test speed assertion.
The explicit generation checker returns **1 as expected**, not a generation approval.
Production JAR audit contains no verification/v2 classes or preflight mixin; `src/main`, frozen manifest/protocol,
V1 evidence, kernels/goldens/presets/transfer/SavedData have no diff.

Current production server regression: 100 complete cycles / 400 loop shifts, 456 successes including recovery;
zero x/z or yaw/pitch deviation, max y correction 1, 495 inventory checks, zero duplicate players/dropped test items,
save/reload PASS. These are legacy-world regressions and do not prove V2 FULL behavior.

The process-local Windows invocation uses the pinned Java25 SDK, GBK Gradle daemon encoding, ASCII temp path
and a preloaded cached ByteBuddy agent; this avoids Chinese-path worker/attach issues without changing the mod
or frozen JVM/toolchain. Standard Gradle tasks remain the entry points.

- `test --tests '*HcCertificateTest' --tests '*GradientDecimalOracleTest' --tests '*NativeV2IntegrationTest'`
- `test --tests '*V2PreparationTest' -Pv2MeasurePreparation=true` (fresh worker, optional measurement)
- `v2NumericalPreflightServer` (legacy-only real server tick observations)
- `testV2PreflightTools` and `v2PreflightEvidence` (evidence completeness, not generation approval)
- `v2PreflightCheck` (**expected nonzero** while numeric gate blocked)
- `coreRegressionAll build` (production regression, independent of V2 approval)

Missing/corrupt reports, wrong manifest/seed/scope, incomplete execution, false completion, invalid call/count
budgets, wrong 477-target ROI/counts or missing certificate outcomes, uncleared caches and unjustified skipped seeds fail evidence validation. Recorded budget failures can
be complete negative evidence; `--require-pass` still fails. Unit tests exercise CLI nonzero paths.
The 800-node/3,484,800-call/1024-certificate limits are also bounded structurally by the frozen ROI and
per-certificate vertex count; no alternate oversized ROI or lowered runtime cap is used to reach defensive branches.
The actual injected time-budget failure uses a deterministic test clock; invalid/nonfinite references,
incomplete certificates, wrong-thread, readiness, ROI and cache eviction/invalidation paths are tested.
Reports/logs stay ignored under build/run; only curated numerical evidence is committed here.

## Gate decision

| gate | outcome |
|---|---|
|r1 immutable inputs and development-only scope|PASS|
|codec/registry/raw-seed/children/two visitor orders/actual normalization|PASS|
|analytic leaves, bounds, native cave composition/preliminary surface/base columns|PASS, bounded in-memory probes only|
|continuous model height/error enclosure|PASS with explicit model/program boundary|
|r1 actual rounded-Hc slope authorization|BLOCKED|
|certified shared nodes/eligible valley graph in frozen ROI|BLOCKED (0 for all seeds)|
|preparation wall/call/cache budget|See measured rows; machine-specific observation only|
|real enabled-node interpolation, FULL terrain, safety/routes|NOT_RUN / blocked; no V2 world allowed|
|V2 terrain data generation / holdout sampling / Phase 2|NOT_APPROVED|

Review the numerical blocker and r2 proposals before progressing. Passing production regression cannot override this decision.
