# WPH-015 MVP core playtest

## 1. Executive Summary

**Recommendation: RETUNE. Supplemental documented-negative observation coverage was accepted in final independent review. Phase 2 remains NOT APPROVED.** Technical transfer regression passes and the four interpretations
look different. The bounded natural-route trials have not demonstrated multiple
independent useful routes. A feasible entry shift is insufficient: the return
position can be incompatible, the alternative can also be blocked, or ordinary
walking already works. This is negative gameplay evidence, not a reason to add
content or relax safety.

Base main: `5d359a9d60b75f5552c2fdbac36cc0a8ddee4b4a`. Dedicated observations began
2026-10-11; native client work continued2026-10-12. Kernels, goldens, presets,
PhaseDefinition, scoring and production transfer/commands are unchanged.
The [frozen protocol](MVP_PLAYTEST_PLAN.md) predates world observation. The first-review
primary FAIL is immutable; [supplemental protocol](MVP_PLAYTEST_SUPPLEMENT_PLAN.md)
and section13 distinguish later evidence from the original acceptance result.

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

Supplemental native runs on2026-10-14 use Linux7.0.0-38-generic/GNOME Wayland,
the same Ryzen9 8945HX/30GiB host and RTX5070Ti Laptop, driver595.99.02.
Minecraft/NeoForge/Temurin versions remain identical; a separate Linux Gradle
cache avoids reusing OS-specific Windows transforms. This OS/driver change is
not a controlled GLFW reproduction or an Issue33 fix. View16/simulation5 and
production generation/transfer configuration remain unchanged.

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
walking coverage were incomplete at the first review. Section13 records the
additional native attempts; neither stage is human-playtested.

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
  deep-cave/resource-balance/crafting coverage or long-term memory testing. The
  supplemental northward384-block intention stops after146 blocks; that bounded
  negative attempt does not certify long-distance endurance or route completion.

## 11. Go / Retune / Stop Decision

**RETUNE**, not GO. Correct transfer and visible worlds have not yet produced
multiple meaningful playable spatial solutions. Limited trials cannot prove a
fundamental impossibility warranting STOP/REDESIGN. The original primary protocol remains FAIL. Completed negative-evidence coverage
does not resolve the native crash/village risks or approve a release/Phase2 gate.

Suggested future design targets, **not changes made here**: compatible approach
and return contours around a genuine Fracture/Smooth obstacle; meaningful Ridge
barriers with reachable alternate exits; Cell regions with observable ingress/
egress benefit; actual warp-related access rather than generic roughness. Review
these relationships under the existing +/-4 safety rule before choosing any tuning.
Use the approved controlled Hybrid Opening concept for a guaranteed first lesson;
do not silently select an unreliable natural candidate.

## 12. Next Development Priorities / Reproduction

Review the supplemental negative-coverage amendment and RETUNE decision before
expansion. Broader human gameplay and endurance/deep-cave testing remain future
validation, not claims made by these scripted native observations. Investigate Issue25 separately with Vanilla
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

## 13. First-review supplemental evidence (2026-10-14)

```yaml
PLAYTEST_EVIDENCE_COMPLETE: PASS  # accepted documented-negative coverage contract
CORE_TECHNICAL_REGRESSION: PASS
GAMEPLAY_RECOMMENDATION: RETUNE
PHASE_2_GATE: NOT_APPROVED
ROUTE_COMPLETED: 0
ORIGINAL_PRIMARY_PROTOCOL: FAIL  # seed8675309 remains35/36 + fatal fall
```

The [contract](verification/mvp-playtest/supplemental-contract.json) and
[compact supplemental findings](verification/mvp-playtest/supplemental-findings.json)
and [supplemental coordinate/image index](verification/mvp-playtest/supplemental-index.md)
were accepted as documented-negative evidence in final independent review. Coverage PASS means the missing trial now has a
terminal native observation, both Phases have actual short cave ingress/return,
and declared sustained-ground/adaptive attempts have observed terminal outcomes,
including failure. It does **not** mean that all intended distances/routes passed,
that a human played, or that the old protocol passed. Independent review accepted
this transparent amendment; the validator itself cannot approve gameplay or GO.

### Primary/replay relationship

The original `findings.json`, seed8675309 primary JSON and four-case settled
replay are unchanged. Primary SHA256 remains
`f9e6b305694ead82f9af84be75131d4a2e9b1f8e9f642be35e890f31e99084ba`.
The manifest pins all three primary reports, the original settled replay and
native reload. No failed seed/trial is removed from the totals.

Independent clone `route35-20261014` replays only index35: Cell->Fracture,
west from(-383.5,171,896.5), same160-tick/24-block source walk, source<12
obstruction threshold, unchanged +/-4 entry/return policy. It advances17.2
source blocks and survives with severe fall damage visible in the
[baseline image](verification/mvp-playtest/supplemental/route35-baseline.png).
After the existing pre-entry reset, entry is refused with NO_SAFE_POSITION;
exact source position/rotation, UUID and inventory remain unchanged, one player
entity. Terminal classification OBSERVED_TERRAIN_DIFFERENCE, ENTRY_SHIFT_REFUSED.
This is a separately completed **negative replay**, not a repaired primary PASS,
a death retry success, or a useful route. The earlier fatal fall stays recorded.

### Actual native cave entry and return

Seed123456789; fixed additional observation box center(1344,1200), after the
original coastal forest box failed discovery. Actual block/shape inspection,
not estimated baseHeight, identifies a short natural outdoor-to-covered passage.
Local setup explicitly requests64 FULL chunks per Phase and inspects2178/3292
floor positions, below the81-chunk/12000-position bounds. No blocks are changed.

| Phase | Outdoor mouth x/y/z | Settled covered checkpoint x/y/z | Actual returned x/y/z | In / out client ticks |
| --- | --- | --- | --- | ---: |
| Fracture |1295.5/66/1200.5|1299.555/65/1201.500|1295.295/66/1200.539|28/32|
| Smooth |1350.5/80/1237.5|1348.476/79/1234.444|1350.484/80/1237.592|28/28|

These are **brief shallow cave-mouth traversals**, not deep-network exploration.
Both use native Survival movement, step down and back up, full player collision,
actual support and health20 throughout. Setup alone uses OP positioning at the
outdoor mouth plus health/food reset and night vision. No in-walk teleport,
spectator flight, mining, rescue or healing occurs. Four interior headings and
10 seconds of stationary observation are captured before the authoritative
checkpoint; return is also sampled after settling.

The Fracture passage is narrow with stone walls/ceiling and a visible natural
opening. Smooth has roughly two-block headroom and a small downward floor recess;
we do not enter the deeper recess. Interior footprints are6/7 blocks below the
actual local heightmap. Nearby11x7x11 block surveys find6 copper ore blocks in
Fracture and26 in Smooth; Smooth's mouth also has2 coal ore blocks nearby. This
limited volume does not establish ore balance. Nearby water2 blocks in Smooth's
interior survey is outside the tested body; no lava/fire or living entities within
12 blocks were observed at the checkpoints. Their absence here is not global safety.
Both return to the mouth naturally without collision/death/load failure.

Images: [Fracture mouth](verification/mvp-playtest/supplemental/cave-fracture-mouth.png),
[covered passage](verification/mvp-playtest/supplemental/cave-fracture-survey-3.png),
[return](verification/mvp-playtest/supplemental/cave-fracture-return.png);
[Smooth mouth](verification/mvp-playtest/supplemental/cave-smooth-mouth.png),
[interior](verification/mvp-playtest/supplemental/cave-smooth-survey-3.png),
[return](verification/mvp-playtest/supplemental/cave-smooth-return.png).

Preserved attempts: `exploration`, `exploration-r2` and `caves-r3` fail discovery
in the original box; `caves-r4` first completes both short walks. The first
four-direction survey incorrectly reused its200-tick wait as the120-tick walking
timeout, reporting RETURN_BLOCKED. Survey r2 returns but captures a server
position before the last movement packet settles; its Fracture snapshot is only
5 blocks below its preceding column, so strict validation rejects it. Final
`caves-survey-r3-20261014` resets the counter and samples after settling, preserving
all failed outputs. These are isolated probe fixes, not production movement fixes;
no measured heights or >=6-depth requirement were rewritten.

### Ground streaming and adaptive choice: failures retained

All northward goals are declared **before movement**, seed123456789,
start x/z(-383.5,896.5), intended destination z512.5, corridor half-width24,
<=18000 inspected floors and<=256 waypoints. Keys follow actual waypoints rather
than a repeating shoreline macro. Same-height collinear segments can be compressed;
at most8 physically blocked waypoint cells may be excluded in the final attempt.
No flight/teleport/healing occurs during the measured walk. No global path proof.

| Isolated attempt | Phase | Northward displacement | Terminal observation |
| --- | --- | ---: | --- |
|exploration-r2|Smooth|75.809|Dry corridor stops near water at(-407.576,63,820.691)|
|travel-ridge|Ridge|97.200|Native walking cannot follow a static-safe step near trees; probe limitation|
|streaming-adaptive|Ridge|145.992|One blocked waypoint avoided; no further corridor found within bounds at(-368.470,97,750.508)|

The final attempt lasts58.443 wall seconds from declared start to stop. It does
**not** reach384 blocks and does not certify long-distance endurance. Its multiple
walked segments and concurrent stream events provide a bounded negative loading
observation, not a distance-triggered gameplay PASS. The fixed corridor and local
walker can reject routes a human could take; do not label these barriers impassable.
Surface y changes89->97 through forest/hills; coal ore6 blocks and a healthy
chicken are observed near the final stop. No extreme-terrain/hazard immunity claim.

During final native movement,1299 new server FULL-chunk events and1042 client
chunk-receive events are counted. Load callbacks only count events, never read or
mutate the Level. Pending entry generation is included, so these totals are not
independent chunks caused solely by travel. Near-terrain views are coherent;
[streaming start](verification/mvp-playtest/supplemental/ridge-travel-start.png),
[mid-route](verification/mvp-playtest/supplemental/ridge-travel-segment-4.png),
[stopped view](verification/mvp-playtest/supplemental/ridge-travel-end.png).
Distant/in-flight section edges and temporarily incomplete foliage are observed;
no persistent density seam or client crash is established by them.

Adaptive example in Smooth uses the actual water-blocked position
(-407.5297320351578,63,820.472923795025): normal BOUNDED_LANDING succeeds to
Fracture y67, exact x/z retained. Native walking proceeds north to
(-407.49999593689364,66,809.471725964118), but the normal return to Smooth is
NO_SAFE_POSITION. It remains SHIFT_POSITION_FEASIBLE, never ROUTE_COMPLETED.
[entry](verification/mvp-playtest/supplemental/smooth-adaptive-choice.png),
[exit refusal](verification/mvp-playtest/supplemental/smooth-adaptive-exit.png).
The Ridge attempts' normal Fracture entries are refused; their source snapshots
remain equal. No DEV_SURFACE is used after the declared start.
This is **scripted native exploration with terrain-dependent decisions**;
there was no human gameplay participant. It does not change the0 useful-route total.

### Performance, failures and current validation

Final ground attempt sampled FPS26/37/60 and average MSPT5.00/8.07/59.59
min/median/max; used heap729..1646MiB,65 GC collections/765ms collection-time delta.
Maximum client-tick gap3.575 seconds includes entry/planning/streaming, not a pure
steady-state frame-time measurement. Smooth's earlier backgrounded run reported
FPS0..1 and visibly incomplete distant foliage: it cannot support a smooth-client
performance claim. Final cave survey FPS29/47/59, average MSPT6.99/8.13/47.87,
heap1040..2171MiB,63 collections/1163ms. Cave **setup** requests cause9–11-second
Can't keep up warnings: synchronous dev discovery is expensive and unsuitable
for automatic startup/gameplay invocation. Walk observations must not hide that cost.
No OOM/persistent loading failure or additional native crash was seen in these finite
Linux sessions; no long-term leak-free, TPS or Windows stability guarantee follows.
Issue25 villager in-wall evidence and Issue33 GLFW fault remain open and unmodified.

Current `coreRegressionAll build testMvpEvidence` passes:361 MC +15 plain dev tests,
38 Python evidence-validator tests, zero failures; cold/reload each50 cycles,
400 loop transfers/456 total successes,495 inventory checks,0 x/z/rotation deviation,
max y correction1, no duplicate player/item entities, actual save/reload PASS.
The final core/build run takes56 seconds. Existing units are up-to-date after the
initial fresh Linux execution; both actual dedicated-server stages rerun.

Validator tests execute the CLI and assert nonzero exits for missing/corrupt/stale
reports, wrong seed/run/trial/direction, incomplete cave/return, absent images,
changed source state, unsupported utility claims, budget overruns and failed core/
reload. An actual pending-evidence `validateMvpEvidence` Gradle run returned nonzero,
then the fully populated contract passes. Primary FAIL cannot be silenced by a
supplemental success string; immutable hashes and trial identity must match.
The review gate remains separate and never becomes GO automatically.

```sh
./gradlew runPhaseVerificationMvpSeed1Client -PmvpSupplement=route35-20261014 -PmvpRoutesOnly=true -PmvpRouteFrom=35 -PmvpRouteTo=36
./gradlew runPhaseVerificationMvpSeed0Client -PmvpSupplement=exploration-r2-20261014 -PmvpExploration=true
./gradlew runPhaseVerificationMvpSeed0Client -PmvpSupplement=streaming-adaptive-20261014 -PmvpExploration=true -PmvpTravelOnly=true -PmvpTravelPhase=RIDGE -PmvpAdaptiveWalk=true
./gradlew runPhaseVerificationMvpSeed0Client -PmvpSupplement=caves-survey-r3-20261014 -PmvpExploration=true -PmvpCavesOnly=true -PmvpCaveX=1344 -PmvpCaveZ=1200
./gradlew coreRegressionAll build testMvpEvidence
./gradlew validateMvpEvidence
```

On this Linux host set JAVA_HOME to the installed Temurin25.0.3+9 and use a
Linux-specific GRADLE_USER_HOME. Existing isolated worlds are preserved on rerun;
use a new run ID and explicitly reviewed manifest for a new independent clone.
Full native reports/logs/worlds stay ignored;14 exact native PNG copies (9.1MiB),
compact results, image/source SHA256 values and coordinate index are committed.
No production classes, kernels/goldens/presets/PhaseDefinition/TransferPolicy/
PhaseTransferService/SavedData/selection behavior changes. No tuning or Phase2.
