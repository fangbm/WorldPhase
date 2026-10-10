# Frozen review experiment v4 — after attribution, before implementation/runs

Attribution of unchanged3f510d0:640 old pool centers contain only3 old-qualified
points across2 seeds.40 FULL audits expose163 sea-only base rejections,11 audited
coast hints with safe centers (4 with all9 positions safe),152 directional proxy
disagreements,0 observed base-positive/FULL-negative cases and0 audited lower-rank
old successes. Original2/10 protocol/report/thresholds remain unchanged.
See TUTORIAL_SEARCH_ATTRIBUTION.md; unaudited FULL pool members remain unknown.

This is **one** new, fixed experiment. No endless weight/seed selection. Retain
original10 seeds; independent holdout:314159265,271828182,20260711,-20260930,
-9223372036854775808. All use their actual Overworld shared spawn, radius512,
64 deterministic uniform centers. Original10 and holdout5 are reported separately.

## Two stages and hard work caps

Stage1: sample both center heights for N centers (8..256). Select at most24,
spacing32, then sample their four cardinal neighbors at16 blocks (8 columns).
Center ordering favors moderate/small inter-Phase height gaps, distance and dry
hints, not the largest height difference. It does not discard coast hints.
After cardinal screening retain at most8 centers, spacing64, with potential
directional improvement and height-compatible positions ranked first.

Stage2: for each of at most8, sample four fixed straight corridors through center
(x,z,both diagonals), offsets -16,-12,-8,-4,0,4,8,12,16.33 unique x/z columns per
Phase. Cache reused centers/endpoints/overlapping coordinates. Select one best
axis per candidate by deterministic score, ties in fixed axis order.

Maximum unique heights:2*N + 8*min(N,24) + 56*min(N,8): default768, maximum1152;
single score66. At most2 new heights/tick, one job/server, same server thread.
No FULL requests, background Level access or startup auto-search. Tick/CPU/wall
and maximum batch times are captured. Cache/ranking are independent of time.

Center score:6*min(H/32,1)+8/(1+H/4)-12*D/R-8*coastHint. Invalid vertical
sentinels are excluded. Cardinal score uses same slope-improvement weights as
legacy but per axis; add8 when one of center/endpoints has height gap<=4.
Choose the best axis, then total descending and x/z ascending.

Fine corridor: F/S are maximum absolute consecutive **four-block** height steps
divided by4 (diagonals additionally divide sqrt(2)); H is maximum same-x/z height
contrast along that corridor. Score uses legacy capped height/slope/distance
weights, but directional signals; uncertain coast/extreme hints subtract8 rather
than acting as a safety verdict. Add4*min(height-compatible points,3)/3.
Require F>=.375, S<=.25, F-S>=.25, H>=4 for base directional interest. Rank
interesting axes with a <=4 height-gap hint first, then interesting without hint,
then other diagnostics, by score and stable coordinates. No hint proves safety.

Risk categories:INVALID (empty/vertical-bound data), REQUIRES_VALIDATION (sea/coast
or extreme slope), LOW_RISK (no such hints). All categories still require actual
validation for any playable claim. Water safety is not cancelled.

## Actual acceptance: explicitly a NEW protocol

For actual Top3 only, load each37x37 collision-halo footprint, at most16 explicit
FULL requests/Phase/candidate (96 per seed), with per-server chunk request cache.
Generate no platform or modified terrain. Recompute the chosen axis on **all33
adjacent FULL ground columns**, skipping canopy/logs as in the baseline.

A new useful candidate must have:

- A Fracture adjacent-column step >=2 blocks, Smooth maximum adjacent step<=1,
  improvement>=1; either travel orientation may expose the obstacle.
- Maximum same-x/z height contrast along corridor>=4, distance<=512.
- Actual supported collision/fluid/known-hazard checks at all33 sampled standing
  positions in both Phases, using unchanged LandingSafety.
- At least one of9 fixed four-block corridor points where a real ServerPlayer
  body stands safely in Fracture, unchanged SafeLandingResolver resolves Smooth
  with BOUNDED_LANDING, and a reverse BOUNDED_LANDING resolves Fracture. Check
  unchanged x/z, policy permits, actual destination safety and player nonmutation.

At most9 potential points/candidate, two resolvers/point, nine y candidates per
resolver; max486 resolver safety candidates/seed. Do not use DEV_SURFACE, alter
TransferPolicy, perform native teleport, or inspect an arbitrary map/path. This
is a local sampled corridor and shift-position proof, **not a continuous route**
or proof of reachable jump trajectories. Partial ground shapes can be rejected
conservatively by the ground+1 source-position convention.

Normal command rankings remain INTERESTING_BUT_SHIFT_UNVERIFIED. The isolated
real validator reports NO_FEASIBLE_SHIFT when all bounded tested points fail,
and specific obstacle/Smooth/standing-safety failures separately. Unverified
strong contrasts never become proved playable candidates.

Report old protocol Top1 alongside new Top1 useful, new Top3-any useful, Top1 and
Top3-any bounded-shift rates, causes, average actual quality/steps and mean CPU/
ticks/wall. Top3 is never substituted for the old Top1 denominator. Target new
Top1>=7/10 on original seeds; holdout is independent evidence. If it fails,
retain Draft and assess natural terrain / radius512 / +/-4 constraints; propose
controlled Anchor Station plus nearby natural obstacle without implementing it.

Repeat searches and compare ranked candidates exactly, independently check fine
base columns against real generators; test maximum256 workload on one seed.
Run focused tests and coreRegressionAll build; preserve kernels/presets/goldens,
PhaseDefinition/TransferPolicy, existing transfer behavior, baseline documentation.
No #14 persistence, spawn redirection or Anchor implementation.
