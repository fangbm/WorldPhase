# WPH-015 supplemental native evidence index

Baseline head `bbfd588`; source protocols: [original](../../MVP_PLAYTEST_PLAN.md),
[supplement](../../MVP_PLAYTEST_SUPPLEMENT_PLAN.md). Original `findings.json` and
primary120-column index remain unchanged. [Supplemental findings](supplemental-findings.json)
contains source-report/image SHA256, exact coordinates, statuses and every attempted
run, including setup/probe failures. [Contract](supplemental-contract.json) identifies
which independent runs account for which missing coverage. Runtime reports/worlds
stay ignored. Images below are exact copies of native Minecraft render targets.

| Coverage | Seed / Phase | x/y/z or interval | Archived images |
| --- | --- | --- | --- |
|Missing primary trial35 supplemental replay|8675309 Cell->Fracture|entrance(-383.5,171,896.5), west;17.2 source progress, entry NO_SAFE_POSITION|[damaged baseline](supplemental/route35-baseline.png)|
|Cave ingress/survey/return|123456789 Fracture|mouth(1295.5,66,1200.5), covered(1299.555,65,1201.500), back(1295.295,66,1200.539)|[mouth](supplemental/cave-fracture-mouth.png), [passage](supplemental/cave-fracture-survey-3.png), [return](supplemental/cave-fracture-return.png)|
|Cave ingress/survey/return|123456789 Smooth|mouth(1350.5,80,1237.5), covered(1348.476,79,1234.444), back(1350.484,80,1237.592)|[mouth](supplemental/cave-smooth-mouth.png), [interior](supplemental/cave-smooth-survey-3.png), [return](supplemental/cave-smooth-return.png)|
|Water-bounded ground attempt|123456789 Smooth|(-383.5,64,896.5)->(-407.576,63,820.691), intended north384|[start](supplemental/smooth-travel-start.png), [stop](supplemental/smooth-travel-end.png)|
|Adaptive normal shift and refused exit|123456789 Smooth->Fracture|entry(-407.529732,63->67,820.472924); exit(-407.499996,66,809.471726)NO_SAFE_POSITION|[entry](supplemental/smooth-adaptive-choice.png), [exit](supplemental/smooth-adaptive-exit.png)|
|Ground streaming / bounded blocked-waypoint avoidance|123456789 Ridge|(-383.5,89,896.5)->(-368.470,97,750.508), intended z512.5; actual north145.992|[start](supplemental/ridge-travel-start.png), [mid-route](supplemental/ridge-travel-segment-4.png), [stop](supplemental/ridge-travel-end.png)|

These short cave passages and interrupted ground goals are limited native scripted
observations, not human play, deep-cave mining or long-distance endurance PASS.
Original seed8675309 fatal fall remains a failure. Neither the supplemental replay
nor the partial adaptive movement is ROUTE_COMPLETED. View16/simulation5;
Linux/driver differences and all assistance are documented in MVP_PLAYTEST.md.
