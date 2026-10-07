# WorldPhase — Roadmap v0.1

## Product target

WorldPhase is planned around:

**10–15h first completion + 25–40h+ optional/postgame play**

The roadmap is intentionally gated by gameplay validation.

---

# Phase 0 — Documentation / concept lock

Status: **current**

Deliverables:

- Game Design v0.1;
- Opening Flow;
- Technical Design v0.1;
- MVP Issue List;
- first-week implementation plan.

Exit condition:

The project has one clear gameplay sentence:

> Same coordinate, different terrain solutions must create useful spatial choices.

---

# Phase 1 — Technical PoC

Goal:

Prove core worldgen and transfer invariants.

Scope:

- Fracture;
- Smooth;
- Ridge;
- Cell;
- shared seed;
- same-x/z shift;
- safe landing;
- debug commands;
- seed-aware tutorial candidate prototype.

Not included:

- custom Anchor blocks;
- Echo;
- visual Shift;
- Warp;
- progression;
- bosses.

Exit condition:

Phase cycling is stable and already produces useful movement/access decisions.

---

# Phase 2 — MVP Gameplay

Goal:

Turn the technical mechanic into an actual first-play experience.

Scope:

- opening Anchor Station;
- Phase Anchor Mk.I;
- first controlled Fracture → Smooth Shift;
- seed-aware first obstacle;
- Resonance Scanner Mk.I;
- basic progression state;
- early Phase Residue concept;
- simple but polished Shift presentation.

Target playtime:

~30–60 minutes.

Exit condition:

A new player understands:

1. coordinates correspond;
2. terrain differs;
3. shifting can solve spatial problems.

without reading a manual.

---

# Phase 3 — Early-game Alpha

Goal:

Deliver the first ~2 hours of the planned game.

Scope:

- Ridge;
- Cell;
- Resonant Crystal;
- Cellstone;
- Phase Anchor Mk.II;
- Phase Hub behavior;
- Scanner Mk.II;
- first temporary Stable interaction;
- early Stable Echo prototype.

Exit condition:

Ridge and Cell form a meaningful “resource + route” relationship.

---

# Phase 4 — Mid-game Alpha

Goal:

Introduce Warp and the mystery of world-state ambiguity.

Scope:

- Deep Resonance Zone;
- Resonance Array multi-phase structure;
- Warp;
- Desynchronization;
- Unbound Matter;
- Observation Nodes;
- Signature Reconstructor;
- first Convergence diagnostics.

Target cumulative playtime:

~4–5 hours.

---

# Phase 5 — Beta progression

Goal:

Complete the main progression spine.

Scope:

- Ridge Trial: The Peak;
- Cell Trial: The Boundary;
- Fracture Trial: Origin Scar;
- Stable temporary visit;
- Stable Compatibility;
- Valid Solution reveal;
- Overlap Events;
- Convergence Array.

Target cumulative playtime:

~8–10 hours.

---

# Phase 6 — Final encounter / Home

Goal:

Ship the narrative and mechanical conclusion.

Scope:

- The Convergence encounter;
- multi-phase boss mechanics;
- Stabilization Core;
- disconnect outcome;
- Stable compatibility 100%;
- permanent Stable Home;
- `Home.` advancement.

Target first completion:

**10–15 hours.**

---

# Phase 7 — Presentation pass

This can begin earlier in small pieces, but major rendering work should wait until mechanics are stable.

## Phase Shift

Target:

- old-scene capture;
- screen-space domain distortion;
- target preload;
- real transfer hidden inside transition;
- noise/domain dissolve into target scene.

Avoid per-block geometry morphing.

## Stable Echo

Target:

- irregular spatial rift;
- target-phase local snapshot;
- basic perspective;
- edge refraction;
- limited entity silhouettes / lightweight representations;
- cross-phase audio bleed.

High-fidelity Echo is expected to be one of the more expensive visual systems.

---

# Phase 8 — 1.0 postgame

Goal:

Turn Phase manipulation into a sandbox after “Home.”

Scope:

- Portable Phase Shifter;
- Resonance Scanner Mk.III;
- Phase Laboratory;
- explicit noise-family terminology;
- Experimental Phases;
- shareable phase presets.

Possible preset concept:

```text
WP-7C4F-A912
```

A preset reproduces the terrain style on another player's own seed, not the exact same map.

---

# Estimated player pacing

| Segment | First-play target |
| --- | ---: |
| Fracture / first Shift | 0–0.5h |
| Smooth / early tools | 0.5–1h |
| Ridge + Cell | 1–2h |
| Deep Resonance / first large structure | 2–3h |
| Warp expedition | 3–4.5h |
| Phase Trials | 4.5–6.5h |
| Stable visit / truth reveal | 6.5–7h |
| Convergence escalation | 7–8h |
| Convergence Array | 8–10h |
| Final encounter + Home | 10–15h typical |

Player types:

- experienced / rushing: ~7–9h;
- typical first playthrough: ~10–15h;
- building/exploration focused: ~15–25h;
- postgame / sandbox: ~25–40h+.

---

# Scope guardrails

Do not increase playtime by adding:

- arbitrary material tiers;
- dozens of machines;
- repetitive minibosses;
- mandatory grind;
- unrelated dimensions.

Add depth primarily through:

- phase relationships;
- terrain interpretation;
- cross-phase structures;
- new ways to inspect or manipulate corresponding coordinates.
