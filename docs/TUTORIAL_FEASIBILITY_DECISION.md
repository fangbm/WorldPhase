# WPH-013 closeout: bounded tutorial feasibility prototype

Date: **2026-10-10**. Experiment/source head: **f3069c6**. Design direction:
**Hybrid Opening**. Issue13 experimental-PoC acceptance amendment: **approved in final review**.
Original useful-majority remains FAIL; no natural-only tutorial reliability is claimed. This document records a
negative experiment and a design decision, not successful original acceptance.

## Evidence and verdict

| Question | Result | Meaning |
| --- | --- | --- |
| Bounded/deterministic search correctness | Local automated PASS; independent source review found no immediate blocker | Correct machinery, not tutorial usability; no independent CI result established |
| Original useful-majority gate | **FAIL** | Original frozen Top1 **2/10**; not a useful majority |
| Separately frozen v4 protocol | **FAIL** | Original-set Top1/Top3-any **1/10**; independent holdout Top1/Top3-any **0/5**; target7/10 not met |
| Bidirectional bounded-shift positions | Original-set Top1 **8/10**, Top3-any **9/10**; holdout **3/5** for both | Real Level/body/resolver safety snapshots under normal +/-4; candidate-position teleports were not committed |
| Complete tutorial route | **NOT VALIDATED** | No demonstrated safe entry -> Fracture obstacle -> Smooth bypass -> safe exit/return |
| Automatic natural-only mandatory onboarding | **Not reliable enough to adopt** | Current prototype cannot guarantee a first lesson for arbitrary seeds |

The original [protocol](TUTORIAL_SEARCH_VALIDATION_PLAN.md) and
[2/10 report](TUTORIAL_SEARCH_VALIDATION.md) are retained. Attribution precedes
the frozen [v4 plan](TUTORIAL_SEARCH_REFINEMENT_PLAN.md); its
[full results](TUTORIAL_SEARCH_REFINEMENT_VALIDATION.md) retain all original seeds,
holdouts, Top1 versus Top3, performance, failed cases and safety snapshot changes.
Neither 1/10 nor 2/10 meets the original acceptance criterion. Correctness PASS
must never be substituted for quality PASS.

The [second independent review](https://github.com/fangbm/WorldPhase/pull/30#issuecomment-6091269209)
also identifies a limitation of the v4 oracle: requiring all33 adjacent standing
positions in **each** Phase to be safe can reject the very Fracture obstacle that
a bypass should exploit. Conversely, one safe shift point proves no bypass.
The negative result applies to this frozen protocol and budget. It does not
prove that no natural lesson exists anywhere within512 blocks, or precisely
isolate the effects of radius, presets and +/-4 constraints. We do not relax
this oracle after seeing results or run another weight/seed/pool tuning cycle.

## Decision: Hybrid Opening

The future opening must provide a **controlled Anchor Station near the planned
Fracture spawn**, with a safe, predictable first interaction. A designed local
terrain interaction must demonstrate the same-coordinate Phase difference and
provide safe entry, an intelligible obstacle, a traversable Smooth bypass and
safe exit/return. Its intended Shift positions must work under the normal
BOUNDED_LANDING +/-4 contract, rather than depending on DEV_SURFACE or relaxed
transfer rules. These are future design/acceptance requirements, not shipped
Station geometry or a validated playable route.

Natural Phase differences remain valuable for subsequent exploration and optional
lessons. `TutorialCandidateFinder` remains a development diagnostic and candidate
discovery tool. It is **not** an automatic selector or the sole source of mandatory
onboarding. No requirement remains that every random seed naturally supplies a
perfect first lesson. If no qualified natural candidate exists, the future flow
must use the controlled opening; it must not promote a low-ranked/unverified
candidate, enlarge the pool indefinitely or silently relax safety.

This clarifies Game Design section11's existing controlled-structure/guaranteed
Anchor intention and changes Opening Flow's assumption that initialization can
always select a natural first obstacle. Authored layout, placement, spawn entry,
fallback realization and playable-route acceptance need separate future design
and implementation. This PR adds none of them: no Station/block/item, Anchor,
spawn redirection, worldgen tuning, progression, automatic teleport or SavedData.

## Issue13 accepted experimental-PoC scope

Keep the original useful-majority checklist item, its unchecked state and FAIL
evidence. The accepted deliverable is now a **validated bounded feasibility PoC
with a negative feasibility report and explicit design decision**, rather than
a reliable natural tutorial selector. Suggested completion criteria:

1. Deterministic Fracture/Smooth candidates/ranking, finite spawn-near radius and
   work caps, stable ties/diversity, server-thread/tick budget and ordinary search
   without FULL requests, selection, persistence or teleport.
2. Explainable score/risk/shift-hint output; explicitly separate heuristics from
   actual body/landing validation and leave complete routes unproven.
3. Preserve the original10-seed baseline and useful-majority failure, attribution,
   separately frozen refinement, independent5 holdouts, actual terrain/safety
   observations and CPU/tick/wall measurements without relabelling success.
4. Focused/build/regression evidence, plus actual dedicated-server validation,
   with simulated transport and historical versus newly rerun evidence identified.
5. Record Hybrid Opening and the semantic/invalidation boundaries below; no
   implicit natural selection or future-safety promise.
6. Final review explicitly accepted the experimental-PoC scope. This does **not**
   turn original useful-majority FAIL into PASS or authorize natural tutorial selection.

The implementation/evidence supports criteria1–5; criterion6 was accepted in final review.
This changes the deliverable's scope, not the experimental result. The issue
must retain a visible audit trail instead of replacing the failed criterion.

## Semantic boundary for a future #14

These are documented meanings, **not new runtime enums or a SavedData schema**.
Evidence and selection are separate axes, not an automatic promotion pipeline.

| Term | Meaning | Does not imply |
| --- | --- | --- |
| searched | A bounded search completed for an identified input/preset context | A candidate was selected, validated or usable |
| interesting | Base-column directional contrast/score merits inspection | Actual safe standing, shift feasibility or a route |
| shift-feasible | Specific natural points passed actual forward/reverse BOUNDED_LANDING checks for a stated body and world snapshot | Native teleport at those points, a reachable entry, continuous bypass, safe exit/return or future safety |
| route-validated | A separately specified route/entry/bypass/exit protocol has been demonstrated | Presently achieved by this prototype; permanent safety |
| selected | An explicit future opening workflow chose a location or controlled-opening mode with provenance | Searched=true, rank1 or shift-feasible automatically selected it |

Current `TutorialCandidate` contains coordinates/heights/score, without selection
or validation state. `TutorialCorridor.interesting` and
INTERESTING_BUT_SHIFT_UNVERIFIED remain heuristics. The isolated oracle's
`boundedShiftProved`/FEASIBLE_BOUNDED_SHIFT is only local snapshot evidence;
NO_FEASIBLE_SHIFT means none of its finite probes passed, not global impossibility.
Nothing currently exports route-validated or selected. LOW_RISK is not safe.

If #14 is later authorized, its design must distinguish search-complete,
candidate evidence and explicit opening choice. A stored optional candidate cannot
be auto-selected because search completed. Absence/rejection of natural candidates
must lead to an explicit **controlled-opening-required** state; the future opening
flow handles it. Do not encode a made-up natural coordinate or a fallback rank1.

Evidence must be associated with raw seed, Phase pair, search center/configuration,
candidate-generation version (currently3), scoring/protocol version (currently4),
and actual worldgen preset/configuration identity. A future implementation must
define the latter identity/fingerprint and relevant generator/toolchain version;
this prototype does not compute/persist such a fingerprint or validation record.
Any seed/config/scoring/generation/preset change requires renewed search/scoring
and applicable real validation. Unknown/missing version/provenance is unverified,
not backward-compatible safety. Do not silently keep stale evidence or substitute
a newly ranked location for an explicit prior choice.

Separate static block/shape suitability from transient entities, fluids,
environment and player body/context. A matching version tuple still cannot
guarantee future safety. Real safety must be rechecked at use; actual transfers
remain server-authoritative through PhaseTransferService and its existing chunk,
policy, collision/support/hazard checks. Full route validation remains a separate
requirement. No second teleport rollback or new policy is proposed here.

## Evidence applicability and this closeout

This closeout changes documentation only. No Java, test, Gradle configuration,
kernel/golden, domain, preset, sampling, command or TransferPolicy behavior changes
relative to f3069c6. Its real15-seed experiment, exact base-ranking replay and
coreRegressionAll cold/reload evidence therefore still describe the implementation;
they are **historical evidence**, not newly rerun experiments or future guarantees.
All original protocol/report/figure files are retained without rewriting results.

The implementation head's core run passed345 tests, cold/reload50 cycles each,
400 loop transfers/456 total successes, unchanged coordinates/rotation/inventory,
zero duplicate entities/items and restored terrain/player data. Candidate safety
snapshots varied between fresh runs even with identical base rankings. No new
GUI/client or candidate-position committed-teleport evidence is claimed.

Documentation closeout verification on2026-10-10:

```sh
./gradlew test --tests '*tutorial*' --tests '*WorldPhaseCommandTest' --tests '*TerrainSampleServiceTest' --console=plain --no-build-cache
./gradlew build --console=plain --no-build-cache
```

Focused126/126 passed (38s). Build passed (42s):330 transforming-loader tests
executed; the unchanged15 plain dev-tooling tests were UP-TO-DATE from the prior
passing run,345 total recorded tests with zero failures/errors. Logs remain ignored
under build/wph-013-closeout-*.log. No seed experiment or real-server probe was
rerun/refitted in this docs-only round. The scope amendment was accepted in final review; #14 and all gameplay
implementation remain deferred.
