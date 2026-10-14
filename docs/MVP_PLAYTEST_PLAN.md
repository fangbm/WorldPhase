# WPH-015 frozen playtest protocol

Frozen before opening these worlds on 2026-10-11, base main
`5d359a9d60b75f5552c2fdbac36cc0a8ddee4b4a`. No preset/kernel/transfer tuning.

Seeds, in order: **123456789, 8675309, 20261011**. Never replace a failed seed.
Record actual Overworld shared spawn and pinned runtime versions for each.

Eight deterministic random observation columns were drawn once with Python
`random.Random(20261011)`, two `randrange(-96,97)*16` draws per point:

| index | x | z |
| --- | ---: | ---: |
| 0 | 464 | -128 |
| 1 | 480 | -160 |
| 2 | -400 | -1312 |
| 3 | -1376 | -80 |
| 4 | 416 | -1392 |
| 5 | 1280 | -1488 |
| 6 | 1344 | 1200 |
| 7 | -128 | -656 |
| 8 (prior forest camera) | -384 | 896 |
| 9 (prior plain camera) | 128 | 128 |

All ten columns in all four Phases, all three seeds. Spectator cameras at
x/z+0.5,y220,yaw135,pitch45 are observations only, never gameplay-route evidence.
Minimum client render distance8/simulation5, settled inner6-chunk circle and
native section compile queue before capture. Native screenshots + coordinate
index; keep full run outputs ignored and publish representative original images.
Also inspect located Vanilla villages in Ridge and Fracture; locator success
alone is not placement/entity safety. Do not repair Issue25 in this issue.

Bounded natural-route trials: starting columns (128,128), (464,-128), (-384,896),
pairs Fracture->Smooth, Ridge->Smooth, Cell->Fracture; directions N/E/S/W.
At most36 attempts/seed. Safe developer surface bootstrap is allowed before
each trial, separately labelled. In Survival, first attempt a straight24-block
walk/jump baseline in the source, then reset to the source entrance as a new
trial setup. Alternate trial uses normal BOUNDED_LANDING only, actual native
client walking/jumping, and a normal return shift at the far end. No flying,
surface fallback, block platforms or teleport along the route.

Classes: OBSERVED_TERRAIN_DIFFERENCE, SHIFT_POSITION_FEASIBLE, ROUTE_COMPLETED.
Count route completion only if source baseline makes<12 blocks forward progress,
alternate reaches>=24 blocks within bounded walking time, both normal shifts
succeed, and actual arrival is safe. This is a short movement/access experiment,
not full pathfinding or a claim that source passage is impossible by other means.
Record all refused/incomplete attempts. Failed/aborted trials never become GO.

Survival smoke: real native client, at least15 wall-clock minutes each in
Fracture and Smooth, seed123456789. OP bootstrap is excluded from the timer.
Automated native key-mapping workloads are permitted but labelled explicitly;
they must run real movement, jumping, mining/placing and entity/world ticks.
No creative mode, flying, surface teleport, item grants or healing during timed
intervals. Death/blocking screen interrupts the interval and is reported, never
counted as successful survival. No idle menu time or simulated logs count.

Rerun `./gradlew coreRegressionAll build` on this branch: real player/service,
400 loop shifts with refusal/recovery and independent save/reload. This proves
correctness, not route utility. Record JVM heap/GC and server tick time while
observing native chunk settling/movement and shifts; metrics are finite local
samples, not universal performance promises.

Final decision: GO requires multiple independent ROUTE_COMPLETED examples and
completed critical native/survival acceptance. RETUNE if correct/visible but
route relationships do not yield enough useful play. STOP/REDESIGN for fundamental
mechanic/safety/performance failure. Unfinished critical client acceptance means
Draft PR even when all automated correctness checks pass. No Phase2 work.

Tooling correction during first launch (2026-10-12): view8 fog obscured terrain
from the frozen y220 camera. Discarded that interrupted observation run; use
view16 (still above the declared minimum), simulation5 and at least18 seconds
settling. Coordinates, seeds, route criteria and presets remain frozen. Three
native clients may run concurrently; label their performance samples accordingly.
