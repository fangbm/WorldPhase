# WPH-015 supplemental coverage protocol

Frozen 2026-10-14 before supplemental client trials; source baseline `bbfd588`.
Same PR32, Minecraft26.1 / NeoForge26.1.0.19-beta / Temurin25.0.3+9.
Linux replaces Windows as the available host; this is not a GLFW/JDK fix or a
controlled reproduction of Windows Issue33. No production worldgen/transfer changes.

## Immutable primary and missing trial

Preserve seed8675309 primary FAIL (35 completed, fatal final baseline) and the
four-case settled replay. Original primary JSON SHA256:
`f9e6b305694ead82f9af84be75131d4a2e9b1f8e9f642be35e890f31e99084ba`.
Replay **only index35**, Cell->Fracture, west, entrance(-383.5,896.5), source
baseline160 ticks /24 blocks, the same jump timing, >=24 alternate progress,
source<12 useful-obstruction criterion and normal +/-4 entry/return. Independent
same-seed save under a new run ID. Do not resurrect or rescue a dying trial.
Record a server-confirmed death with source/cause/position/stage and native image.
Keep trial FAIL/DEATH even if its observation coverage is complete.

## Cave exploration

Seed123456789, Fracture and Smooth separately. Bounded setup inspection in the
previous forest area x[-448,-320],z[832,960], at most81 loaded FULL chunks/Phase.
Inspect actual blocks/collision only, not baseHeight. A dev-only local connected
walking corridor may be discovered with <=12000 visited floor nodes/Phase and
reversible steps at most1 block vertically. Select an outdoor mouth connected to
a covered passage at least6 blocks below its local surface, <=96 waypoints.
If none is found, report discovery failure; do not dig or fabricate a cave.

Bootstrap at the outdoor mouth only; disclose any food/tool/light assistance.
Then Survival native client input walks inside and back, no spectator/teleports
on the measured passage. Record mouth/interior/return, sampled hazards/entities/
ores, actual movement/collision, chunk settling, MSPT, heap/GC and screenshots.
Stop on death, impassable motion or bounded timeout; record failed exploration
honestly. This local dev fixture is not a shipped navigation or tutorial system.

## Sustained ground travel and adaptive gameplay attempt

Seed123456789, Smooth. Use the forest region mouth/nearby actual dry terrain as
the natural start. Declare destination north of the start (up to384 blocks) before
movement, corridor width<=48 blocks, <=18000 floor-node inspections and <=256
waypoints. Inspect/stream actual chunks; use native Survival movement, no flying,
surface jumps or teleport on the route. Log attempted and achieved displacement,
blocked sections, visible loading/joins, periodic MSPT/FPS/heap/GC and images.
Distance alone never determines PASS. If a continuous walking corridor cannot be
found or followed, document this as a bounded negative result.

Adaptive observation is **scripted native exploration**, not human-playtested:
choose a destination from real terrain, inspect a local obstruction, try normal
PhaseTransferService BOUNDED_LANDING to Fracture, attempt a bounded alternative
and legal exit/return. Keep OBSERVED_TERRAIN_DIFFERENCE, SHIFT_POSITION_FEASIBLE
and ROUTE_COMPLETED separate. No useful-route claim from height data or setup.
No fixed repeating key cycle is used for this supplemental waypoint attempt.

## Proposed negative-evidence completion contract

An immutable failed primary need not become a primary PASS. Its one missing
observation is accounted for by a separately linked replay with exact trial
identity and a terminal observed outcome, including authenticated native death.
Fatal fall stays a gameplay failure. A missing report, bad identity, native crash,
stale/incomplete stage, absent screenshots/metrics or unsupported narrative does
not satisfy coverage. Cover both real cave ingress/interior/return (or explicitly
failed attempts), declared sustained travel and the adaptive attempt.

Report independent fields: PLAYTEST_EVIDENCE_COMPLETE, CORE_TECHNICAL_REGRESSION,
GAMEPLAY_RECOMMENDATION, PHASE_2_GATE. Coverage PASS can coexist with RETUNE and
zero useful routes; Phase2 remains NOT_APPROVED. Partial scope or unresolved
runtime interruption remains INCOMPLETE. This amendment was accepted in final independent review on 2026-10-14;
it never retroactively changes the original protocol/results or the Phase 2 gate.

## Setup correction before second exploration run

The first `exploration-20261014` setup failed to discover a connected cave in
both Phases; its FAIL JSON/log remain in the isolated run. No cave walk occurred.
`exploration-r2-20261014` retains the same seed, box, budgets and cave depth.
Outdoor endpoints use MOTION_BLOCKING_NO_LEAVES surface height without an
additional canSeeSky requirement: leaves can obscure the sky above genuine
outdoor ground. Covered interiors still require an actual stone roof/depth.
Travel setup is independent of cave discovery: if no mouth is found, explicit
pre-walk DEV_SURFACE at(-383.5,896.5) may establish its natural Smooth start.
No rescue occurs after measured Survival movement begins.

## Cave-only setup follow-up

The second run again found only sealed/deep air pockets without a connected
walkable exit; no cave coverage is claimed. It continues the independently
declared Smooth ground/adaptive trial. A cave-only third run uses the same
box/depth/budget but starts its bounded local walk inspection at actual
outdoor-to-covered boundaries, before testing isolated covered air pockets.
This is a setup locator correction, not altered collision or route acceptance.
The first two discovery failures remain archived.

## Additional cave locations declared before inspection

Three setup attempts in the original coastal forest box did not establish an
open connected passage; all are failures of cave coverage, not terrain defects.
The cave-only follow-up may inspect three additional existing observation boxes
in fixed order: (1344,1200), (1280,-1488), (464,-128), seed123456789. Each is
124x124 blocks, <=81 explicitly requested FULL chunks and <=12000 inspected
floor positions per Phase, unchanged >=6 covered depth and <=96 waypoints.
Stop after these boxes, retaining every failed discovery. No random seed/preset
search or terrain edits. A Phase with a completed ingress/return need not repeat.
These are supplemental locations, never substituted for original route trials.

## Extended streaming attempt declared before movement

Smooth's first northward attempt stopped after75.8 northward blocks at water,
with a legal +4 Fracture entry and a refused later return. It is not relabelled
as long-distance completion. One additional ground-streaming attempt will use
seed123456789, **Ridge**, start(-383.5,896.5), northward goal z512.5, x corridor
[-408,-360], the same384-block intention/18000-inspection/256-waypoint caps.
Ridge is an already recorded Phase; no generation parameters change. Both full
and failed segments remain evidence. New FULL-chunk and client-receive events
are counted during native movement; pending entry generation can confound them.
View16/simulation5 remains unchanged. These counters are observation only.

The successful short natural cave passages at(1344,1200) will receive a separate
repeat with four interior view directions and a10-second stationary survey;
short covered passages must not be described as a deep-cave mining survey.
No additional cave boxes are needed if both ingress/return checks finish.

## Bounded native walking follow-up

The first Ridge attempt stopped97.2 blocks north at a static-safe floor step
which native walking could not cross beside trees. That is a test-walker
limitation, not a demonstrated impassable Minecraft obstacle. A separate
`streaming-adaptive-20261014` replay keeps the same Ridge start/384 north goal,
48-wide corridor,18000 floor checks and256 waypoint cap. North-first bounded
local inspection avoids breadth-first surface flooding, collinear same-height
waypoints are compressed, and at most8 physically blocked waypoint cells may
be excluded before replanning. No terrain edits, teleport, flight or healing
during movement. This is dev-only scripted exploration, not production route
validation; every blocked segment remains in the new report. No further search
budget growth is planned. Success at ordinary walking is not a useful shift route.

## Survey replay bookkeeping correction

The separate four-direction survey reached both interiors but immediately marked
return blocked: its200-tick survey wait counter was mistakenly reused as the
120-tick per-waypoint walking timeout. This is a dev-only probe bug, not a
terrain failure. Retain `caves-survey-20261014` with RETURN_BLOCKED. Reset the
per-waypoint counter at return start and repeat as `caves-survey-r2-20261014`;
same mouths, geometry, phases, assistance and collision rules. The earlier r4
ingress/return remains independently valid.

## Settled checkpoint correction

The r2 survey physically returned, but its Fracture interior server snapshot
was requested immediately after the client reached the final waypoint, before
its last movement packet settled. That snapshot lies in the preceding column
(height70, feet65), so the strict actual >=6-depth validator correctly rejects
it. Do not edit the measured height or lower the depth threshold. The r3 survey
requests the authoritative snapshot **after** the observation/settling interval,
and uses a tighter0.2-block waypoint arrival radius. Same endpoints and geometry.
The r2 raw report remains preserved; only settled r3 evidence may satisfy this
coverage check. These are probe bookkeeping fixes, not production movement fixes.
