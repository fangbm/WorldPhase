# WPH-015 MVP core playtest

## 1. Executive Summary

**Recommendation: RETUNE. Delivery remains Draft while critical native acceptance
is incomplete.** Technical transfer regression passes and the four interpretations
look different. The bounded natural-route trials have not demonstrated multiple
independent useful routes. A feasible entry shift is insufficient: the return
position can be incompatible, the alternative can also be blocked, or ordinary
walking already works. This is negative gameplay evidence, not a reason to add
content or relax safety.

Base main: `5d359a9d60b75f5552c2fdbac36cc0a8ddee4b4a`. Dedicated observations began
2026-10-11; native client work continued2026-10-12. Kernels, goldens, presets,
PhaseDefinition, scoring and production transfer/commands are unchanged.
The [frozen protocol](MVP_PLAYTEST_PLAN.md) predates world observation.

## 2. Environment and versions

Minecraft26.1, NeoForge26.1.0.19-beta, Temurin25.0.3+9-LTS, Gradle9.2.1,
ModDevGradle2.0.148, LWJGL3.4.1+2. Windows11 build28000.3181; Ryzen9 8945HX
(16 cores/32 threads), about31.2GiB RAM, NVIDIA RTX5070Ti Laptop, driver610.74.
Native screenshots are actual game render targets; no reconstructed screenshots.
Archived PNGs only recompress IDAT losslessly, retaining the exact decompressed
image stream and all other PNG chunks. Full recordings/game directories/logs stay ignored.

Dedicated worlds: view8/simulation5, loopback25577, real FULL columns. Native
copied worlds: view16/simulation5, clouds off, camera yaw135/pitch45. A first view8
attempt obscured terrain with distance fog at y220 and was discarded. The corrected
camera waits at least18 seconds, inner6 loaded chunks and the native compile queue.
Outer unloaded edges remain possible; rectangular far-water edges are not claimed
as density seams. Several clients ran concurrently during observation. Background/
AFK frame limits explain30/10FPS samples; they are not an isolated GPU benchmark.

Native route/input and Survival workloads use actual client KeyMappings, physics,
ServerPlayer, ServerLevel and PhaseTransferService. This is explicitly scripted
native play, not an unrestricted human playtest or a simulated packet transcript.
All Level operations execute on the server thread. The dev source set is excluded
from the production JAR.

## 3. Tested Seeds / Coordinates

| Frozen seed | Actual Overworld shared spawn | Columns | Native Phase views |
| --- | --- | ---: | ---: |
| 123456789 | (64,68,-48) | 10 ×4 | 40 |
| 8675309 | (16,64,80) | 10 ×4 | 40 |
| 20261011 | (-64,88,-192) | 10 ×4 | 40 |

Eight predeclared random columns plus two previous reference cameras:
(464,-128), (480,-160), (-400,-1312), (-1376,-80), (416,-1392),
(1280,-1488), (1344,1200), (-128,-656), (-384,896), (128,128).
No seed was replaced. See the [120-column coordinate index](verification/mvp-playtest/coordinate-index.md)
for base/FULL heights and screenshot naming. FULL height includes water/structures;
baseHeight is not a safe landing or a final surface snapshot.
All120 views were captured in the native client; the curated set was visually
reviewed. This is not a completed per-column walking/hazard survey. Unreviewed
archive views cannot be used to claim the absence of generation defects.

## 4. Four-Phase visual evaluation

| Phase | Actual observation | Spatial role demonstrated? |
| --- | --- | --- |
| Fracture | Asymmetric shores, steep hills, abrupt elevation changes; seed20261011 village has ravine/water adjacency | Distinct shape; meaningful warp-assisted access not demonstrated |
| Smooth | Lower, gentler forested shores and more shallow water; not uniformly flat | Visually gentler; does not guarantee legal entry/exit from taller Phases |
| Ridge | Elevated connected ridges/terraces, steep sand/grass slopes | Vertical obstacles visible; a useful bounded ridge bypass was not completed |
| Cell | Basin/rim and high relief; several observation columns under water | Cellular relief visible; useful enclosed-region ingress/egress not demonstrated |

Examples, all unmodified native images:
[123456789 Fracture](verification/mvp-playtest/wph015-123456789-point-0-fracture.png),
[Smooth](verification/mvp-playtest/wph015-123456789-point-0-smooth.png),
[Ridge](verification/mvp-playtest/wph015-123456789-point-0-ridge.png),
[Cell](verification/mvp-playtest/wph015-123456789-point-0-cell.png).
The archive also contains four corresponding point9 views for8675309 and point6
views for20261011, rather than selecting only attractive terrain.

At(128,128), base heights F/S/R/C are70/66/67/41 for123456789,
26/62/79/85 for8675309, and11/55/109/38 for20261011. The same raw seed within
each world does not imply identical terrain or a usable +/-4 crossing. Climate
and feature pipeline reuse also does not guarantee a sensible biome/shape pairing.

## 5. Useful-route attempts and failures

Compact machine-readable [findings](verification/mvp-playtest/findings.json)
retain primary failures and the separately labelled replay. Selected route images:
[east source obstruction](verification/mvp-playtest/wph015-8675309-settled-route-25-baseline.png),
[Smooth progress](verification/mvp-playtest/wph015-8675309-settled-route-25-alternate.png),
[refused return](verification/mvp-playtest/wph015-8675309-settled-route-25-return.png).

Each source entrance is OP-bootstrapped **before** the trial. The timed walk uses
Survival input, no flight/platforms/surface teleport. A straight24-block goal is
compared with a source baseline; all in-route shifts use BOUNDED_LANDING. This
small directional probe cannot prove that ordinary walking around a tree or another
route is impossible. No complete tutorial route/pathfinding is claimed.

| Seed | Completed primary trials | Entry-feasible classifications | ROUTE_COMPLETED |
| --- | ---: | ---: | ---: |
| 123456789 | 36 | 12 | 0 |
| 8675309 | 35 + one aborted on death | 4 | 0 |
| 20261011 | 36 | 0 | 0 |

**OBSERVED_TERRAIN_DIFFERENCE**, **SHIFT_POSITION_FEASIBLE** and
**ROUTE_COMPLETED** remain separate. The four entry-feasible trials for8675309
were replayed on a separate same-seed clone after fixing a probe issue: release
movement and wait20..100 ticks for actual onGround before the return shift.
The initial immediate-return attempts sometimes sampled a jumping/falling player.
Those failures alone are not evidence of incompatible terrain. The replay kept
the same coordinates, directions, +/-4 policy and route-success threshold.

Concrete cases:

- **8675309, Fracture->Smooth,(-383.5,81,896.5),east:** source stops around9.2
  blocks; Smooth reaches24.07. Entry succeeds at y80. After settling, return at
  (-358.82097608499123,79,896.5) still returns NO_SAFE_POSITION. Player/inventory
  snapshot is unchanged. This is an actual partial movement benefit, not a completed
  source-to-source bypass. Classification: SHIFT_POSITION_FEASIBLE.
- **Same entrance,south:** source and alternative both reach24 blocks. Settled
  return succeeds from(-383.5,81,920.9699144897558) to Fracture y78, preserving
  exact x/z/rotation and player/inventory. Ordinary source movement already works;
  classification remains SHIFT_POSITION_FEASIBLE, not a useful-route success.
- **Same entrance,north/west:** entry succeeds, alternative stops at13.2/11.2
  blocks. No completed bypass.
- **8675309,Ridge,(128.5,80,128.5):** source can walk; Smooth entry is rejected.
  At the initial column the ground heights differ substantially more than4 blocks.
- **20261011:** the frozen entrances frequently begin in water or differ greatly
  in elevation. Surface bootstrap can itself be refused, and every tested normal
  entry was refused. These are not successful gameplay routes.
- **8675309,Cell,(-383.5,171,896.5),west:** the last primary baseline aborted
  after a fatal fall. This is a natural hazard and a limitation of blind straight
  input; it is not evidence that a successful transfer created an unsafe landing.
  No respawn/heal is counted as route completion. A replay against the dead saved
  player also failed explicitly; the successful four-case replay used a separate
  clone, preserving this failure.

Overall: **0 completed useful routes**, not proof that none exist elsewhere.
DEV_SURFACE bootstrap and spectator screenshots are never counted as route help.

## 6. Phase Transfer Stress Results

Fresh current-branch `cleanTest cleanTestNoiseProfiler coreRegressionAll build`:
**PASS**,361 Minecraft tests +15 plain dev tests, zero failures/errors/skips.
Cold startup and separate-JVM reload each execute50 complete four-Phase cycles:
400 loop shifts,456 total successes including recovery,495 inventory checks.
Maximum x/z and yaw/pitch deviation0; maximum tested y correction1; zero duplicate
player entities or dropped test items. Actual terrain/marker/Vanilla player data
restore before fixture initialization. Real native teleport/ServerPlayer/Levels;
headless outbound packet sends are discarded, so this is not native-login evidence.

Refusals/recovery include blocked, unsupported, powder-snow hazard, WorldBorder,
native travel veto, forbidden+5 and same-Phase no-op. Missing-Level and commit-boundary
semantics remain covered in units. Native route probes separately use actual
connected player inventory/identity and real LandingSafety on successful arrivals.

## 7. Survival Smoke Test

Seed123456789, separate Fracture and Smooth intervals. OP surface bootstrap at
(128.5,128.5) and health/food setup happen **before** timing. No healing, grants,
creative flight or teleport occurs inside the timed interval. Time only advances
with a live unpaused native world, requiring900 active seconds and18000 client ticks.
Death/menu interruptions cannot count as success.

| Phase | Active seconds | Client ticks | Broken / placed | Health |
| --- | ---: | ---: | ---: | ---: |
| Fracture | 900.061 | 18000 | 152 /142 | 20 |
| Smooth | 900.055 | 18001 | 47 /42 | 19 |

Fracture ended(121.40030067116776,62,139.30000001192093); Smooth ended
(118.31960001186431,62.03499999910593,146.30000001192093). Inventory GUI
opening/closing was exercised through actual native UI, with that interval excluded
from timing. Native images showed dirt, wood and sand pickups and placement.
These are completed timed **basic** smoke intervals. The input macro settled into a shoreline loop;
this is limited coverage, not long-distance or cave traversal, crafting progression,
an unassisted opening or a broad human Survival pass. Cave and long-distance
walking coverage remain incomplete. Delivery stays Draft.

[Fracture collecting/placing](verification/mvp-playtest/wph015-123456789-survival-FRACTURE-1.png),
[Smooth collected materials](verification/mvp-playtest/wph015-123456789-survival-SMOOTH-5.png),
[native inventory UI](verification/mvp-playtest/survival-inventory.png),
[after native reconnect](verification/mvp-playtest/wph015-123456789-reload.png).

After save/stop, a new native JVM reopened the same seed123456789 world and
checked the player **before any bootstrap/shift**: RELOAD_PASS, exact equality
for UUID01461149-4349-368b-8856-192924919853, worldphase:smooth,
(118.38484667762509,62,146.30000001192093), yaw-90/pitch0 and all inventory slots.
The native inventory was empty by then because the macro placed its gathered
blocks; nonempty inventory persistence is independently verified by the core
server fixture. Native terrain is visually present after reconnect, but no native
Survival block-by-block terrain fingerprint was taken. The core save/reload
fixture separately verifies actual FULL terrain fingerprints and a persisted marker.

## 8. Performance Observations

Method: real FULL-chunk request wall time, settled native captures, service call
elapsed time, sampled client FPS, JVM heap/GC MXBeans, actual server average-tick
nanoseconds. No absolute timing test assertion or new benchmark framework.

Dedicated village probes force-load two bounded64-chunk regions per seed
and observe600 real server ticks, approximately30 seconds from log timestamps.
Each uses actual generated villagers, with no player/network load; this is not
a multiplayer TPS stress benchmark. Sampled average-MSPT min/median/max:

| Seed | ms min / median / max |
| --- | --- |
| 123456789 | 3.23 /5.29 /39.56 |
| 8675309 | 2.08 /3.50 /30.01 |
| 20261011 | 2.80 /4.44 /30.49 |

Core's200-loop startup burst allocates roughly32MiB per JVM, without GC during
that short burst; this is allocation, not proof of a leak or of long-term release.
Native observation produces several Can't keep up warnings (~2–2.8 seconds behind).
Cold entry replay measured624ms; warm successful entry examples2.7–30ms. These
are synchronous service costs, not complete packet/render latency. The background
FPS cap, simultaneous clients and deliberate distant camera jumps confound them.

One native crash at13:25:31 occurred in `glfw.dll+0x10fa1`,
EXCEPTION_ACCESS_VIOLATION in RenderSystem.pollEvents, outside the Java VM.
Cause is **unconfirmed**; do not attribute it to noise or claim it was fixed by
reducing parallel clients. The34 settled seed123456789 views were retained,
remaining observations resumed, and route/Survival work started fresh. No renderer
or JDK workaround was added.

The two timed intervals (one native client, with a final headless build near the
end) sampled average MSPT1.91/2.57/9.37ms min/median/max. Used heap ranged
762..2924MiB with GC collections74 and collection-time delta756ms. No persistent
runaway heap or OOM was seen in this finite session; this is not a leak-free
guarantee. FULL column chunk-request min/median/max across120 measurements was
109/631/2553ms, including generation/dependencies and cache effects. The final
`coreRegressionAll build` rerun passed in1m14s; units were up-to-date from the
earlier forced361+15 execution and both actual server stages reran.

## 9. Worldgen / Structure / Entity Issues

Actual located/generated villages, both Ridge and Fracture:
123456789(-272,160),8675309(-304,-320),20261011(-1408,368).
Native village screenshots are in the archive. Water-adjacent houses/paths in
8675309 and heavily obscured/buried parts in20261011 deserve explicit structure
adaptation review. Locator success is not placement safety.

[20261011 Ridge burial](verification/mvp-playtest/wph015-20261011-village-ridge.png),
[Fracture ravine/structures](verification/mvp-playtest/wph015-20261011-village-fracture.png).

Thirty-second dedicated observations saw19/16/30 distinct villagers respectively.
In20261011 Fracture, UUID753a7eb5-8e65-4579-bcd9-ab7ca8d6797c was in-wall at
(-1415.6163975727975,64,343.5806821123198), health18 at tick20; minimum17 and
later escaped to(-1425.5945633371055,64,346.3427657532413). Damage source was not
instrumented, so every health decrease cannot be conclusively labelled suffocation.
This is an in-wall/health-loss reproduction relevant to [Issue25](https://github.com/fangbm/WorldPhase/issues/25),
not a frequency estimate or root-cause fix. Other short probes observed no such
health decrease; absence in30 seconds does not certify safety after reload/night.

Native logs also include Received passengers for unknown entity and an empty
tropical-fish sound warning. No density-codec/registry/Mixin failure was observed.
Far-camera streaming edges and the GLFW crash remain separate from terrain defects.

## 10. Known Limitations

- Smooth's shallow oceans and biome/shape mismatch remain.
- Cell's useful enclosed-region role and Fracture's meaningful warp gameplay
  remain unproven. Irregular terrain alone does not establish either role.
- Normal same-x/z +/-4 needs compatible entry **and exit** contours; widening
  this policy to force a route would bypass the experiment and safety contract.
- Hybrid Opening is a design decision only. No Station, Anchor or spawn redirect exists.
- Tutorial original useful-majority2/10 and refined1/10, holdout0/5 remain FAIL.
  Local shift feasibility is not route validation.
- SavedData has no trusted preset fingerprint/full route validator and cannot
  enable a usable natural opening. Searched does not mean selected or safe.
- Scripted Survival/short corridors do not replace full human route exploration,
  long-distance loading, cave/resource/crafting coverage or long-term memory testing.

## 11. Go / Retune / Stop Decision

**RETUNE**, not GO. Correct transfer and visible worlds have not yet produced
multiple meaningful playable spatial solutions. Limited trials cannot prove a
fundamental impossibility warranting STOP/REDESIGN. Current acceptance gaps,
native crash and village risks prevent claiming a final release gate PASS.

Suggested future design targets, **not changes made here**: compatible approach
and return contours around a genuine Fracture/Smooth obstacle; meaningful Ridge
barriers with reachable alternate exits; Cell regions with observable ingress/
egress benefit; actual warp-related access rather than generic roughness. Review
these relationships under the existing +/-4 safety rule before choosing any tuning.
Use the approved controlled Hybrid Opening concept for a guaranteed first lesson;
do not silently select an unreliable natural candidate.

## 12. Next Development Priorities / Reproduction

Review gameplay evidence/criteria and finish uncovered native Survival/reload/
human exploration before expansion. Investigate Issue25 separately with Vanilla
comparison, actual damage causes and persisted village state. Address/reproduce
the native GLFW fault separately before claiming stable client sessions. No Phase2
implementation, new policy, tuning or natural-selection unlock is included.

```sh
./gradlew mvpPlaytestWorlds
./gradlew runPhaseVerificationMvpSeed0Client
./gradlew runPhaseVerificationMvpSeed1Client
./gradlew runPhaseVerificationMvpSeed2Client
./gradlew mvpPlaytestWorlds -PmvpServerMode=mvp-villages
./gradlew runPhaseVerificationMvpSeed1Client -PmvpRoutesOnly=true -PmvpRouteFrom=24 -PmvpRouteTo=28
./gradlew runPhaseVerificationMvpSeed0Client -PmvpReload=true
python tools/summarize_mvp_playtest.py --require-complete
./gradlew coreRegressionAll build
```

Accepted existing core-regression EULA is reused. Fresh seed worlds are prepared
under ignored `run/mvp-playtest`; existing client worlds/evidence are preserved.
For interrupted observation only, `-PmvpResumeViews=true` requires matching metadata,
VIEWS stage and no route/Survival work. It carries no Survival time. Primary,
settled-return replay and reload reports are separate. Missing/FAIL native reports
cause nonzero Gradle exit; the summary also rejects incomplete workloads. A completed
workload remains REVIEW_REQUIRED, never automatic GO. Full evidence resides in
`run/mvp-playtest`, `build/wph-015-*.log`, `build/mvp-playtest/summary.json` and
`build/core-regression/summary.json`. Only curated screenshots/coordinates/findings
are committed; no game save, crash dump or runtime log is committed.
