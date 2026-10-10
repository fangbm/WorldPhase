# WPH-013 predeclared validation plan

Freeze before running multi-seed worldgen acceptance. No post-run threshold or seed
replacement to make the majority look better. Finder/scoring only; no gameplay
spawn changes, selected-location persistence, Anchor or automatic teleport.

Seeds, in order: **0, 1, -1, 42, 8675309, 20261009, 123456789, 987654321,
-7046029254386353131, 9223372036854775807**. All use radius512, 64 candidate
centers, local offsets center plus eight directions at16 blocks, minimum ranked
spacing64. Development center is the actual Overworld shared respawn x/z, not the
player's position or an invented Fracture spawn. The same parameters apply to all.

For each seed: repeat the complete search and compare candidates/ranking exactly,
record Top5 with every score component, independently cross-check their base
heights against actual generators, and observe the actual FULL terrain of Top1.
If Top1 fails, that seed is counted as a failure (no post-hoc lower-rank rescue).
Also observe at least two low-scoring candidates to expose failure examples.

Useful minimum, applied to both base heuristic and actual ground observation:

- Absolute Fracture/Smooth center height contrast >=4 blocks.
- Fracture maximum center-to-neighbor rise/run >=0.375 (e.g.6 blocks/16).
- Smooth maximum rise/run <=0.25 (e.g.4 blocks/16).
- Signed slope improvement Fracture minus Smooth >=0.25.
- All nine ground columns above seaLevel+1 and away from vertical bounds;
  neither Phase's local slope >2.
- Candidate center within512 of the declared spawn center.
- On real FULL terrain, both center standing positions must pass existing
  LandingSafety's supported collision/fluid/known-hazard checks.

Actual ground observation skips tree logs/leaves above the ground, records the
resulting surface blocks and fluids, and retains the difference from base height.
This is an explicit expensive dev observation, never part of normal ranking.
Actual shape must pass the same numeric criteria; sampling and FULL terrain
disagreement is reported rather than "corrected" by changing presets or data.
This does not prove a continuous path, safe shifting between endpoints or every
intermediate foothold. Majority target: >=7/10; fewer is honestly reported.

Measure 64- and256-candidate workloads, sample calls, total compute/wall time and
maximum batch time. Commands evaluate at most two height columns per
server tick, one active search per server. No background Level access or FULL
chunk request in ordinary scoring. Timing is evidence, not a portable assertion.

## Predeclared second pass after uniform baseline

The first complete uniform-disk experiment measured **2/10** useful actual Top1
candidates under the criteria above. It is archived in ignored
`build/tutorial-verification/uniform-baseline.json`. Coast/water coverage and
candidate sparsity near spawn motivate one generation-only refinement: alternate
hash attempts between radius/2 and the full radius disk (generation version2).
No scoring threshold, weight, seed, radius, candidate count or preset is changed.
The same ten-seed workload will be rerun; original results remain documented.
The final Long.MAX_VALUE seed additionally exercises the actual 256-candidate
command through real ticks to measure its production two-height-column batches.
The dev-only measurement probe runs small4-column batches back-to-back separately;
its bulk tick warnings are not claimed as normal command latency.

## Final pass after inner-disk experiment

Generation version2 measured **0/10** useful actual Top1 candidates and is archived
in ignored `build/tutorial-verification/inner-bias-experiment.json`. Retain the
better uniform disk sequence. Ranking version3 first partitions by the **same
predeclared base usefulness predicate**, then sorts by total score and x/z.
This prevents a known hazardous or unsuitable high-score candidate from
displacing a base-qualified one. Unqualified rows remain visible diagnostics.
Weights, thresholds, seeds, radius, count and presets remain unchanged. Rerun all
ten seeds at this final revision, including the actual256-candidate command.
