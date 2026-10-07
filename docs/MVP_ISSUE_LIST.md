# WorldPhase — MVP Issue List

The first week has one purpose:

> Prove that same-seed, same-coordinate Phase shifting is technically stable and intrinsically fun.

Do not add custom blocks, lore, Echo rendering, bosses or progression until this passes.

---

## WPH-001 — Bootstrap NeoForge project

**Priority:** P0  
**Estimate:** 2–3h

Create:

- NeoForge 26.1.x project;
- Java 25 toolchain;
- mod ID `worldphase`;
- package skeleton;
- logging;
- client and dedicated-server run configs.

### Acceptance

- `./gradlew build` passes.
- Client reaches main menu.
- Dedicated server starts.

---

## WPH-002 — Define Phase domain model

**Priority:** P0  
**Estimate:** ~2h

Implement:

- `PhaseId`;
- `PhaseDefinition`;
- `PhaseCatalog`;
- `PhaseWorldResolver`.

MVP phases:

- FRACTURE;
- SMOOTH;
- RIDGE;
- CELL.

### Acceptance

Phase ↔ Level key mappings are covered by tests.

---

## WPH-003 — Deterministic noise kernels

**Priority:** P0  
**Estimate:** 6–10h

Implement:

- `PhaseNoise`;
- coherent Smooth base noise;
- `RidgedNoise`;
- `WorleyNoise`;
- `DomainWarpNoise`.

### Acceptance

Fixed seed + coordinate values pass golden tests across repeated runs.

---

## WPH-004 — Noise profiling helper

**Priority:** P1  
**Estimate:** ~2h

Add lightweight sampling benchmark output:

- samples;
- total time;
- ns/sample;
- min;
- max;
- mean.

### Acceptance

Obvious pathological implementations are easy to detect.

---

## WPH-005 — Register custom Phase density function

**Priority:** P0  
**Estimate:** 5–8h

Implement:

- `PhaseNoiseDensityFunction`;
- codec/configuration;
- `ModDensityFunctions`.

Target type:

`worldphase:phase_noise`

### Acceptance

A datapack density function using the custom type loads without registry or codec errors.

---

## WPH-006 — Smooth world

**Priority:** P0  
**Estimate:** 3–5h

Create the first custom Phase dimension and use it to validate the whole pipeline.

### Acceptance

- `worldphase:smooth` generates successfully.
- Terrain is visibly smoother than baseline.
- Vanilla biome/surface behavior remains broadly functional.

---

## WPH-007 — Ridge, Cell and Fracture worlds

**Priority:** P0  
**Estimate:** 5–8h

Add the remaining three MVP worlds.

### Acceptance

- all four levels generate;
- all use the same base seed;
- terrain differences are obvious at the same x/z.

---

## WPH-008 — TerrainSampleService

**Priority:** P0  
**Estimate:** ~3h

Implement central sampling API.

### Acceptance

Commands and tutorial scoring can reuse the same sampling logic.

---

## WPH-009 — PhaseTransferService

**Priority:** P0  
**Estimate:** 4–6h

Implement server-authoritative cross-level transfer.

### Acceptance

Player can move Smooth → Ridge while preserving x/z and rotation.

---

## WPH-010 — SafeLandingResolver

**Priority:** P0  
**Estimate:** 3–5h

Implement exact/near-y safety search.

### Acceptance

- valid corresponding positions succeed;
- blocked positions reject;
- gameplay transfer does not silently surface-teleport.

---

## WPH-011 — /wp debug commands

**Priority:** P0  
**Estimate:** 4–6h

Implement:

```text
/wp phase list
/wp phase current
/wp phase goto <phase>
/wp phase goto <phase> --surface
/wp phase cycle
/wp sample <phase>
/wp compare <phaseA> <phaseB>
/wp compare all
/wp safepos <phase>
/wp seedcheck
```

### Acceptance

All are usable from an OP development session.

---

## WPH-012 — Automated regression tests

**Priority:** P1  
**Estimate:** 4–6h

Cover:

- deterministic noise;
- phase differences;
- transfer x/z preservation;
- rotation preservation;
- solid rejection;
- repeated phase cycling.

### Acceptance

One test run gives a clear pass/fail signal for core invariants.

---

## WPH-013 — Tutorial candidate scorer prototype

**Priority:** P1  
**Estimate:** 5–8h

Implement:

- `TutorialCandidate`;
- `TutorialScore`;
- `TutorialCandidateFinder`.

MVP compares Fracture and Smooth only.

### Acceptance

`/wp tutorial find` returns ranked natural terrain candidates near spawn.

---

## WPH-014 — Persist tutorial candidate

**Priority:** P2  
**Estimate:** 2–3h

Persist the selected candidate with `WorldPhaseSavedData`.

### Acceptance

Save/reload does not silently choose a new tutorial location.

---

## WPH-015 — MVP playtest pass

**Priority:** P0 — Release blocker  
**Estimate:** 3–4h

Test at least three seeds.

At multiple random coordinates, cycle all phases and record:

- terrain quality;
- transfer failures;
- bad safety corrections;
- chunk issues;
- performance;
- whether phase differences naturally suggest routes or strategies.

Create `MVP_PLAYTEST.md`.

### Acceptance

The team can answer:

> “Would I actually use another Phase to solve a spatial problem here?”

If the answer is mostly no, return to terrain tuning before building progression systems.

---

# First-week schedule

## Day 1

- WPH-001
- WPH-002
- start WPH-003

## Day 2

- finish WPH-003
- WPH-004
- WPH-005

## Day 3

- WPH-006
- WPH-007

**Milestone:** stand in four working Phase worlds.

## Day 4

- WPH-008
- WPH-009
- WPH-010

**Milestone:** stable same-coordinate Shift.

## Day 5

- WPH-011
- WPH-012

**Milestone:** fast development/debug loop.

## Day 6

Do not add features.

Tune:

- frequency;
- amplitude;
- ridge sharpness;
- octave count;
- Worley scale;
- warp strength.

## Day 7

- WPH-013
- WPH-015
- WPH-014 only if time remains.

---

# MVP Definition of Done

The core PoC is complete when all of the following hold:

1. Four phases generate from one base seed.
2. Same phase + seed + coordinate is deterministic across reloads.
3. Terrain differences are visually substantial.
4. `/wp phase cycle` preserves x/z.
5. Safe transfer never knowingly places the player inside solid terrain.
6. Save/reload preserves dimension state correctly.
7. Fracture and Smooth survive 15-minute Vanilla-style smoke tests.
8. 100 repeated shifts do not crash, duplicate inventory or create invalid coordinates.
9. Tutorial candidate search works across a useful majority of tested random seeds.
10. A developer naturally starts using Phase differences to solve movement/access problems during playtesting.

Only after this should development move to Anchor gameplay and visual presentation.
