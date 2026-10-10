# Frozen 3f510d0 baseline: failure attribution

Original seeds/radius512/count64, generation3 and all original score thresholds/weights are unchanged.
The original **2/10 Top1** protocol and its complete report remain in
[baseline validation](TUTORIAL_SEARCH_VALIDATION.md). This analysis precedes new ranking code.

Every raw64-point stencil is captured. FULL audits inspect old Top3 and at most
one additional shape-qualified sea/coast candidate; at most4 per seed. Uninspected
FULL candidates are **unknown**, never classified as nonexistent. Sea false negatives
below mean numeric shape-qualified hints whose two center bodies stand safely;
the stricter all-nine-footprint safety audit is shown separately. Neither proves
a usable route or feasible +/-4 shift.

| Seed | Pool old-qualified | Sea-only shape rejects | Directional-proxy misses | Audited FULL | FULL old-qualified | Sea rejects: both centers safe / all9 safe | Base-positive -> FULL-negative | Classification |
| --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | --- |
| 0 | 0 | 18 | 12 | 4 | 0 | 1/0 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |
| 1 | 0 | 23 | 12 | 4 | 0 | 1/1 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |
| -1 | 0 | 9 | 18 | 4 | 0 | 1/1 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |
| 42 | 0 | 11 | 23 | 4 | 0 | 3/2 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |
| 8675309 | 2 | 21 | 12 | 4 | 1 | 1/0 | 0 | OLD_BASE_AND_FULL_TOP1_PASS; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |
| 20261009 | 0 | 18 | 13 | 4 | 0 | 1/0 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |
| 123456789 | 0 | 12 | 11 | 4 | 0 | 0/0 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; DIRECTIONAL_PROXY_DISAGREEMENT |
| 987654321 | 0 | 18 | 14 | 4 | 0 | 2/0 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |
| -7046029254386353131 | 0 | 23 | 10 | 4 | 0 | 0/0 | 0 | NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown; DIRECTIONAL_PROXY_DISAGREEMENT |
| 9223372036854775807 | 1 | 10 | 27 | 4 | 1 | 1/0 | 0 | OLD_BASE_AND_FULL_TOP1_PASS; COAST_CENTER_FALSE_NEGATIVE; DIRECTIONAL_PROXY_DISAGREEMENT |

## Aggregate and causal limits

Raw old-qualified candidates: 3; sea-only shape rejections: 163;
directional proxy disagreements: 152. Audited 40 FULL candidates.
Coast hints with both centers actually safe: 11; all-nine safe: 4.
Observed base-positive/FULL-negative cases: 0; seeds with audited lower-rank old success: 0.

Base/FULL center heights differ in 2/40 audits; local slopes differ in 8/40.
These differences affect already-rejected locations, but none turns an old positive into a FULL negative in this audited subset. They are not evidence that base columns certify gameplay safety.

Qualified-first legacy ranking cannot hide an old-base-qualified candidate behind an
unqualified Top1: eight old Top1 failures therefore have no qualifying point in their
64-point **base** pool. This does not prove natural qualified locations do not exist.
The FULL audit additionally checks whether lower-ranked candidates pass the old standard.
No whole-pool FULL scan is performed, so any unobserved real ranking miss remains unknown.

Maximum center-to-neighbor slope is a directionless mean-gradient proxy. The actual
33-cell line measurements above show adjacent-block steps and the minimum same-x/z
ground-height gap for four axes. These do not inspect all body hazards/paths and do
not count as verified shifts. The maximum-long old success has no <=4 ground gap
on these lines despite its strong visual contrast; the8675309 old success has
height-compatible positions. Shape improvement and shift feasibility must be separate.

New experiments must retain the original report and report old-protocol Top1, new
Top1/Top3 and actual bounded-shift rates separately. No weight/threshold change was
made to the old finder in this attribution pass.

## Seed 0: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 0/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | -47,-227 | False/False | +0/+0 | 9/7 | 1,0:1/1/2; 0,1:1/1/0; 1,1:1/1/0; 1,-1:1/1/0 |
| old-top2 | 80,238 | False/False | +0/+0 | 9/9 | 1,0:3/1/10; 0,1:6/1/1; 1,1:9/1/0; 1,-1:11/1/0 |
| old-top3 | 238,218 | False/False | +0/+0 | 8/7 | 1,0:1/1/20; 0,1:4/1/18; 1,1:13/14/8; 1,-1:4/1/16 |
| coastal-audit | -44,-499 | False/False | +0/+0 | 0/0 | 1,0:20/1/15; 0,1:1/1/21; 1,1:2/1/13; 1,-1:6/1/17 |

## Seed 1: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 0/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | 261,598 | False/False | +0/+0 | 5/0 | 1,0:2/0/18; 0,1:1/0/24; 1,1:2/1/15; 1,-1:2/0/23 |
| old-top2 | 299,545 | False/False | +0/+0 | 8/0 | 1,0:2/1/29; 0,1:1/1/35; 1,1:3/1/22; 1,-1:15/1/24 |
| old-top3 | 579,409 | False/False | +0/+0 | 9/9 | 1,0:10/1/21; 0,1:1/1/28; 1,1:1/1/23; 1,-1:1/1/31 |
| coastal-audit | 274,600 | False/False | +0/+0 | 8/0 | 1,0:1/1/29; 0,1:1/1/32; 1,1:3/1/23; 1,-1:2/1/30 |

## Seed -1: actual audit

Base/FULL center mismatch: 1/4; local-slope mismatch: 2/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | 905,338 | False/False | +0/+4 | 9/8 | 1,0:1/5/23; 0,1:1/5/21; 1,1:2/4/20; 1,-1:3/6/23 |
| old-top2 | 966,270 | False/False | +0/+0 | 9/8 | 1,0:1/1/36; 0,1:1/1/35; 1,1:1/6/33; 1,-1:1/1/36 |
| old-top3 | 748,441 | False/False | +0/+0 | 9/9 | 1,0:1/0/11; 0,1:1/1/8; 1,1:2/1/2; 1,-1:1/1/13 |
| coastal-audit | 342,347 | False/False | +0/+0 | 9/0 | 1,0:1/1/29; 0,1:1/1/30; 1,1:4/3/31; 1,-1:2/1/23 |

## Seed 42: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 3/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | 211,-158 | False/False | +0/+0 | 9/9 | 1,0:3/1/18; 0,1:1/1/14; 1,1:2/2/18; 1,-1:2/24/7 |
| old-top2 | 40,-245 | False/False | +0/+0 | 8/9 | 1,0:6/6/19; 0,1:1/1/13; 1,1:1/1/19; 1,-1:6/4/10 |
| old-top3 | -325,-292 | False/False | +0/+0 | 0/3 | 1,0:3/1/45; 0,1:1/3/57; 1,1:2/1/56; 1,-1:3/1/31 |
| coastal-audit | -24,-274 | False/False | +0/+0 | 9/9 | 1,0:1/1/20; 0,1:9/1/13; 1,1:7/8/17; 1,-1:6/1/11 |

## Seed 8675309: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 0/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | -175,-107 | True/True | +0/+0 | 9/9 | 1,0:3/1/1; 0,1:1/1/10; 1,1:2/1/0; 1,-1:2/2/4 |
| old-top2 | 312,14 | False/False | +0/+0 | 9/4 | 1,0:2/1/27; 0,1:1/1/32; 1,1:2/1/18; 1,-1:1/1/37 |
| old-top3 | 220,225 | False/False | +0/+0 | 9/0 | 1,0:10/1/34; 0,1:4/1/45; 1,1:12/2/30; 1,-1:8/1/36 |
| coastal-audit | -58,214 | False/False | +0/+0 | 6/8 | 1,0:2/1/0; 0,1:1/1/7; 1,1:2/1/0; 1,-1:2/1/0 |

## Seed 20261009: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 0/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | 125,311 | False/False | +0/+0 | 0/9 | 1,0:9/1/33; 0,1:1/1/45; 1,1:14/1/36; 1,-1:3/3/27 |
| old-top2 | 395,-259 | False/False | +0/+0 | 9/8 | 1,0:5/1/29; 0,1:1/1/30; 1,1:1/1/32; 1,-1:1/1/27 |
| old-top3 | 168,40 | False/False | +0/+0 | 7/5 | 1,0:2/1/28; 0,1:4/1/27; 1,1:2/2/16; 1,-1:1/1/35 |
| coastal-audit | 210,98 | False/False | +0/+0 | 8/6 | 1,0:1/1/34; 0,1:2/1/26; 1,1:2/1/21; 1,-1:2/1/33 |

## Seed 123456789: actual audit

Base/FULL center mismatch: 1/4; local-slope mismatch: 1/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | -213,-356 | False/False | +0/-7 | 0/8 | 1,0:40/9/15; 0,1:23/7/17; 1,1:24/20/10; 1,-1:32/8/14 |
| old-top2 | -192,0 | False/False | +0/+0 | 9/9 | 1,0:1/1/8; 0,1:1/3/9; 1,1:2/3/9; 1,-1:1/2/7 |
| old-top3 | 255,304 | False/False | +0/+0 | 8/6 | 1,0:2/2/33; 0,1:1/1/38; 1,1:3/6/31; 1,-1:2/2/33 |
| coastal-audit | 51,-474 | False/False | +0/+0 | 0/8 | 1,0:1/1/30; 0,1:1/1/29; 1,1:1/1/28; 1,-1:5/1/30 |

## Seed 987654321: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 2/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | -140,46 | False/False | +0/+0 | 8/7 | 1,0:7/1/19; 0,1:2/4/9; 1,1:6/7/8; 1,-1:8/4/13 |
| old-top2 | 122,108 | False/False | +0/+0 | 8/7 | 1,0:1/1/15; 0,1:1/1/10; 1,1:1/1/10; 1,-1:1/1/8 |
| old-top3 | 221,-18 | False/False | +0/+0 | 8/6 | 1,0:1/1/25; 0,1:1/1/23; 1,1:2/1/17; 1,-1:1/1/24 |
| coastal-audit | -366,61 | False/False | +0/+0 | 9/7 | 1,0:1/1/30; 0,1:1/1/29; 1,1:3/6/29; 1,-1:1/5/24 |

## Seed -7046029254386353131: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 0/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | -287,235 | False/False | +0/+0 | 8/3 | 1,0:1/1/20; 0,1:2/1/9; 1,1:2/1/9; 1,-1:2/1/10 |
| old-top2 | 285,-296 | False/False | +0/+0 | 8/0 | 1,0:1/1/20; 0,1:1/1/19; 1,1:1/1/24; 1,-1:2/1/13 |
| old-top3 | 396,-270 | False/False | +0/+0 | 3/0 | 1,0:1/1/21; 0,1:1/0/14; 1,1:2/1/17; 1,-1:2/1/15 |
| coastal-audit | -47,272 | False/False | +0/+0 | 9/1 | 1,0:1/1/19; 0,1:1/1/18; 1,1:1/5/21; 1,-1:1/1/16 |

## Seed 9223372036854775807: actual audit

Base/FULL center mismatch: 0/4; local-slope mismatch: 0/4. Positive-to-negative classification: 0.

| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |
| --- | --- | --- | --- | --- | --- |
| old-top1 | 43,-50 | True/True | +0/+0 | 9/7 | 1,0:1/1/46; 0,1:7/1/32; 1,1:4/0/27; 1,-1:7/2/32 |
| old-top2 | -259,96 | False/False | +0/+0 | 9/9 | 1,0:2/1/3; 0,1:16/6/0; 1,1:4/1/0; 1,-1:1/1/9 |
| old-top3 | 120,-78 | False/False | +0/+0 | 8/6 | 1,0:2/3/34; 0,1:4/2/26; 1,1:3/1/15; 1,-1:2/1/35 |
| coastal-audit | -199,-246 | False/False | +0/+0 | 7/7 | 1,0:2/3/0; 0,1:1/1/0; 1,1:5/3/0; 1,-1:2/1/0 |
