# WorldPhase — Technical Design v0.1

## 1. Target stack

Planning target:

- Minecraft 26.1
- NeoForge 26.1.x
- Java 25
- Mod ID: `worldphase`

Implementation may revise the exact baseline if worldgen compatibility testing demands it.

---

## 2. First technical milestone

The first milestone validates only:

```text
same base seed
+ different terrain algorithms
+ shared x/z
+ safe phase transfer
```

It does **not** include:

- Stable Echo renderer;
- advanced shift shader;
- custom Anchor block;
- Warp;
- bosses;
- resource progression;
- GUI;
- lore;
- custom particles.

---

## 3. Architecture principles

### Server authoritative

The server decides:

- current target Phase;
- transfer validity;
- destination coordinate;
- safe landing;
- tutorial candidate state.

### Reuse Vanilla worldgen

Do not replace the full chunk generator unless absolutely necessary.

Prefer:

```text
Vanilla worldgen
  + phase-specific macro terrain signal
```

Preserve where possible:

- biome pipeline;
- caves;
- aquifers;
- structures;
- surface rules.

### Data-driven world definitions

Keep dimension / worldgen configuration data-driven where practical.

Java should own:

- algorithms;
- service logic;
- codecs / registered function types;
- transfer rules;
- debug tooling.

---

## 4. Proposed package structure

```text
dev.fangbm.worldphase
├── WorldPhase.java
├── registry
│   ├── ModDensityFunctions.java
│   └── ModWorldgenKeys.java
├── phase
│   ├── PhaseId.java
│   ├── PhaseDefinition.java
│   ├── PhaseCatalog.java
│   └── PhaseWorldResolver.java
├── worldgen
│   ├── density
│   │   └── PhaseNoiseDensityFunction.java
│   ├── noise
│   │   ├── PhaseNoise.java
│   │   ├── OpenSimplexNoise.java
│   │   ├── RidgedNoise.java
│   │   ├── WorleyNoise.java
│   │   └── DomainWarpNoise.java
│   └── sample
│       └── TerrainSampleService.java
├── transfer
│   ├── PhaseTransferService.java
│   ├── SafeLandingResolver.java
│   ├── TransferResult.java
│   └── TransferPolicy.java
├── tutorial
│   ├── TutorialCandidateFinder.java
│   ├── TutorialCandidate.java
│   └── TutorialScore.java
├── command
│   └── WorldPhaseCommand.java
├── data
│   └── WorldPhaseSavedData.java
└── test
    └── WorldPhaseGameTests.java
```

---

## 5. Phase domain model

MVP phases:

```text
FRACTURE
SMOOTH
RIDGE
CELL
```

Suggested conceptual record:

```text
PhaseDefinition
- id
- levelKey
- algorithm
- frequency
- amplitude
- octaves
- lacunarity
- persistence
- warpStrength
```

Do not create a custom registry for this until the data model proves it is needed.

---

## 6. Seed invariant

All phases use the same base seed.

Never implement:

```text
Ridge = seed + 1
Cell = seed + 2
```

Instead:

```text
SmoothAlgorithm(baseSeed)
RidgeAlgorithm(baseSeed)
CellAlgorithm(baseSeed)
FractureAlgorithm(baseSeed)
```

Independent random channels may derive deterministic salts from the base seed.

---

## 7. Noise abstraction

MVP interface should remain small.

Conceptually:

```java
interface PhaseNoise {
    double sample(long seed, double x, double z);
}
```

Start with 2D macro-terrain modulation.

Move into fully 3D density only if needed after the gameplay PoC.

### Smooth

Low-frequency coherent gradient/simplex-style noise.

### Ridge

Derived from a coherent base:

```text
r = (1 - abs(noise)) ^ sharpness
```

with octave accumulation.

### Cell

Start with Worley F1 distance from deterministic feature points.

### Fracture

Domain-warp a coherent base:

```text
x' = x + warpX(x,z) * strength
z' = z + warpZ(x,z) * strength
terrain = base(x', z')
```

---

## 8. Density integration

Register one configurable custom density-function type:

```text
worldphase:phase_noise
```

Do not create a Java type per Phase.

Conceptual configuration:

```json
{
  "type": "worldphase:phase_noise",
  "algorithm": "ridge",
  "frequency": 0.004,
  "amplitude": 1.0
}
```

MVP preference:

```text
finalDensity = vanillaDensity + phaseModifier(x,z,y)
```

rather than using PhaseNoise directly as the complete stone/air decision.

This keeps the Vanilla pipeline involved.

---

## 9. Resource layout

Expected worldgen data layout:

```text
data/worldphase/
├── dimension/
│   ├── fracture.json
│   ├── smooth.json
│   ├── ridge.json
│   └── cell.json
├── dimension_type/
│   └── phase.json
└── worldgen/
    ├── density_function/
    │   ├── fracture_terrain.json
    │   ├── smooth_terrain.json
    │   ├── ridge_terrain.json
    │   └── cell_terrain.json
    └── noise_settings/
        ├── fracture.json
        ├── smooth.json
        ├── ridge.json
        └── cell.json
```

MVP should reuse Vanilla biome logic wherever possible.

---

## 10. PhaseWorldResolver

Centralize:

```text
PhaseId ↔ ResourceKey<Level>
ServerLevel → Optional<PhaseId>
```

No scattered string comparisons such as:

```text
if dimension == "worldphase:ridge"
```

throughout the codebase.

---

## 11. PhaseTransferService

Single API used later by:

- debug commands;
- Phase Anchors;
- Portable Phase Shifter;
- boss mechanics;
- tutorial events.

Conceptual call:

```text
transfer(player, targetPhase, policy)
```

Responsibilities:

1. resolve target ServerLevel;
2. preserve source x/z;
3. ask SafeLandingResolver for destination;
4. ensure target chunk is available;
5. perform transfer;
6. preserve rotation;
7. return structured TransferResult.

---

## 12. TransferResult

Use an enum/result object, not a boolean.

Suggested outcomes:

```text
SUCCESS
SAME_PHASE
TARGET_LEVEL_MISSING
NO_SAFE_POSITION
CHUNK_NOT_READY
OUT_OF_WORLD
ERROR
```

---

## 13. SafeLandingResolver

Gameplay policy:

1. test exact position;
2. test y ± 1;
3. test y ± 2;
4. test y ± 3;
5. test y ± 4;
6. reject if no safe corresponding space exists.

Do not search tens of blocks for a convenient surface during normal gameplay.

A developer-only `--surface` transfer mode may use a broader search.

---

## 14. TerrainSampleService

One reusable source of truth for:

```text
sampleNoise(phase,x,z)
sampleHeight(phase,x,z)
compare(phaseA,phaseB,x,z)
```

Future consumers:

- debug commands;
- tutorial scoring;
- Resonance Scanner;
- loading-screen terrain previews.

Avoid implementing independent noise calculations in each system.

---

## 15. Seed-aware tutorial prototype

`TutorialCandidateFinder` receives:

- base seed;
- spawn area;
- phase pair.

MVP scoring compares Fracture and Smooth using:

- surface height difference;
- local slope difference;
- distance from spawn;
- basic hazard penalty.

No full pathfinding is required for v0.1.

Candidate generation should be deterministic for the same seed.

WPH-013's [feasibility closeout](TUTORIAL_FEASIBILITY_DECISION.md) retains the
original useful-majority FAIL and records the accepted bounded experimental PoC deliverable.
The finder is a diagnostic/discovery tool; it neither selects a mandatory opening
nor establishes a complete route. Hybrid Opening guarantees the future first
lesson through a controlled Station, independently of natural candidate quality.

---

## 16. Saved data

Only persist information that cannot be inferred from Vanilla state.

Possible MVP fields:

```text
schemaVersion
tutorialSearchComplete
selectedTutorialCandidate
```

Do not duplicate player position or current dimension.

The fields above are conceptual, not a sufficient validation/selection contract.
`tutorialSearchComplete` must not imply `selectedTutorialCandidate`. Searched,
interesting, local shift-feasible, route-validated and explicitly selected are
distinct meanings; this prototype has no route-validated/selected output.
The [WPH-014 global data layer](TUTORIAL_SAVED_DATA.md) binds diagnostic evidence
to seed, search configuration and generation/scoring/toolchain versions. It
records preset identity as explicitly UNVERIFIED until a trustworthy resolved
fingerprint exists; no stored evidence can authorize a natural opening yet.
Version/context changes invalidate evidence without silently replacing the
recorded choice. Static geometry still requires transient safety rechecks.
Missing natural choices have an explicit controlled-opening state, not rank1.
Natural selection and full-route validation remain independent future contracts.

---

## 17. Debug command tree

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

/wp tutorial score
/wp tutorial find [candidateCount]

/wp profile noise <phase> <samples>
```

These commands are development infrastructure, not temporary throwaway code.

---

## 18. Initial automated tests

Required early regression coverage:

### deterministic_noise

Same seed/phase/x/z always returns the same value.

### phase_difference

At fixed test coordinates, at least selected phases produce meaningfully different values.

### transfer_preserves_xz

Phase shift preserves horizontal coordinates.

### transfer_preserves_rotation

Yaw/pitch remain stable.

### transfer_rejects_solid

Fully blocked destination returns `NO_SAFE_POSITION`.

### repeated_shift

Repeated cycling does not crash, duplicate the player, corrupt inventory, or produce invalid coordinates.

---

## 19. Performance guardrails

MVP targets are relative, not release-grade.

If Smooth is baseline:

```text
Ridge    < ~2x Smooth
Cell     < ~3x Smooth
Fracture < ~3x Smooth
```

If Cell is 10–15x slower, stop and fix it before continuing.

Worley should only inspect the minimum neighboring cells needed for F1.

Keep initial octave counts small.

---

## 20. Future rendering architecture

Do not implement in the first core PoC.

### Shift presentation

Preferred high-value approach:

1. capture old scene;
2. begin screen-space phase distortion;
3. preload target chunk;
4. execute real dimension transfer under distortion;
5. reveal target scene through a noise/domain-warp dissolve.

Do not attempt per-block mesh morphing.

### Stable Echo

Preferred later architecture:

- server provides a small target-phase snapshot around corresponding coordinates;
- client renders a cached EchoScene into an irregular rift surface;
- add basic perspective, edge refraction and audio bleed.

Avoid maintaining a complete second live ClientLevel unless later prototypes prove it worthwhile.
