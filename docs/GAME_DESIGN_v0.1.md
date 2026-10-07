# WorldPhase — Game Design Document v0.1

## 1. Product definition

WorldPhase is a Minecraft adventure / exploration mod built around a single systemic idea:

> One seed can resolve into several valid terrain solutions. All phases share the same coordinates, but the terrain at those coordinates is different.

The player does not spawn in the Vanilla Overworld. They begin in an abnormal phase, learn to exploit phase differences, reconstruct their own phase compatibility, stop an automatic convergence process, and finally return to the normal Overworld.

The intended structure is:

**10–15 hours of focused progression + long-term sandbox play.**

WorldPhase must not become a generic dimension pack, a 50-machine tech mod, or a boss-rush mod.

---

## 2. Core design pillars

### Shared Coordinates

All gameplay depends on this invariant:

```text
Phase A: x=1000, z=-500
Phase B: x=1000, z=-500
```

Switching Phase must preserve `x/z`. Vertical placement may be adjusted only when necessary for safety.

### Terrain as Gameplay

Terrain is not decoration. It must solve problems.

Examples:

- A ravine in Fracture may be flat ground in Smooth.
- Solid rock in Ridge may correspond to a cave in Cell.
- A mountain in Ridge may expose resources inaccessible in Smooth.
- A buried structure may become reachable through another Phase.

The desired player thought is:

> “What does this coordinate look like in another Phase?”

### Incomplete Worlds

Each Phase is good at some activities and poor at others.

The player should move between worlds because it is strategically useful, not because a quest marker tells them to.

### Return Home

The Vanilla Overworld is visible early but unavailable.

The emotional arc is:

```text
survive
→ understand
→ manipulate
→ reconstruct
→ return home
→ explore by choice
```

---

## 3. World model

Originally:

```text
W = F(seed)
```

After the Seed Collapse:

```text
W_i = F_i(seed)
```

Several terrain solutions become valid at once.

These solutions are called **Phases**.

They share:

- base seed;
- coordinate system;
- world-time basis;
- selected structural anchors;
- selected progression structures.

They may differ in:

- macro terrain;
- cave accessibility;
- resource tendencies;
- environmental stability;
- phase-specific structures and materials.

The exact cause of the Seed Collapse is intentionally not explained in v0.1.

---

## 4. Phase roster

### Fracture

**Role:** starting phase.

**Terrain language:** domain-warped, stretched and locally broken terrain.

**Player impression:** “This is recognizably Minecraft, but something is wrong.”

Fracture must remain survivable. It is not a punishment dimension.

Primary uses:

- opening survival;
- anomaly materials;
- early story clues;
- Origin Scar trial.

### Smooth

**Role:** early home base and transportation.

**Terrain language:** low-frequency smooth terrain, large plains, gentle slopes, coherent waterways.

Primary uses:

- building;
- agriculture;
- animals;
- long-distance travel.

Smooth should be comfortable enough that players naturally choose to build there.

### Ridge

**Role:** geology and mining.

**Terrain language:** ridged multifractal terrain, large mountain chains, exposed rock and extreme verticality.

Primary uses:

- exposed ore;
- geological materials;
- vertical traversal;
- long-distance observation.

Signature material: **Resonant Crystal**.

### Cell

**Role:** underground access and spatial routing.

**Terrain language:** Worley / cellular macro structure, basins, enclosed regions, large cavities and region boundaries.

Primary uses:

- cave access;
- buried structures;
- bypassing solid terrain in other phases.

Signature material: **Cellstone**.

### Warp

**Role:** late-game expedition phase.

Warp is not “Fracture but stronger.” It represents interference between multiple valid world solutions.

Terrain profiles may locally resemble Ridge, Cell, Fracture or combinations of them.

Primary uses:

- Unbound Matter;
- late-game structures;
- Convergence diagnostics;
- high-risk expeditions.

Long stays produce **Desynchronization** and eventually Phase Rejection.

### Stable

**Role:** home.

Stable is the normal Vanilla Overworld.

It should remain visually and mechanically close to Vanilla. The normality is the reward.

---

## 5. Phase Shift

Phase Shift is not framed as conventional teleportation.

The player stays at the same world coordinate and changes which world solution they occupy.

Core rule:

- preserve `x/z`;
- attempt to preserve `y`;
- allow only small safety correction;
- reject the shift if no corresponding safe space exists.

This intentionally preserves spatial puzzles. Phase Shift must not silently teleport the player dozens of blocks to a convenient surface.

---

## 6. Player-built blocks

Ordinary placed blocks are phase-local.

A house built in Smooth does not automatically appear in Ridge.

Cross-phase objects are exceptional:

- Phase Anchors;
- selected progression structures;
- selected endgame constructs.

This allows players to build a **Phase Hub** at one coordinate, with different infrastructure in each Phase.

---

## 7. Core tools

### Phase Anchor Mk.I

Early fixed-area device.

Supports:

- Fracture;
- Smooth.

The player must be close to the Anchor to Shift.

### Phase Anchor Mk.II

Mid-game Anchor.

Supports:

- Fracture;
- Smooth;
- Ridge;
- Cell.

Adds:

- larger synchronization radius;
- Home Anchor;
- Phase Recall.

### Portable Phase Shifter

Postgame tool.

Allows freer shifting and is intentionally unavailable during most of the story.

### Resonance Scanner Mk.I

Answers simple questions about the same coordinate in another Phase:

```text
FRACTURE: SOLID
CELL: AIR
```

or:

```text
Surface difference: +23
```

### Resonance Scanner Mk.II

Shows small local cross-sections for underground routing.

### Resonance Scanner Mk.III

Postgame tool for multi-phase comparison and 3D phase-difference visualization.

---

## 8. Desynchronization

Do not implement this as another hunger bar.

Desynchronization is staged audiovisual and mechanical instability.

Possible progression:

1. subtle audio lag and edge doubling;
2. player afterimages and scanner noise;
3. shift delay and mild phase damage;
4. Phase Rejection back to the Home Anchor.

Warp should feel unsuitable for permanent residence without reducing it to a visible countdown timer.

---

## 9. Stable Echo

Stable must be introduced early.

A Stable Echo is a short-lived visual opening showing the Vanilla world at the same coordinate.

Players may see:

- a normal forest;
- a river;
- animals;
- a village.

Early interaction ends with:

```text
COMPATIBILITY: REJECTED
```

The player should understand the motivation before understanding the terminology:

> There is a normal world, and I cannot stay there.

---

## 10. Loading-screen opening

The first sign of the Seed Collapse occurs before the player enters the world.

The normal Minecraft world-generation screen remains intact at first.

Later, brief diagnostic anomalies appear:

```text
Resolving terrain...

Expected solutions: 1
Resolved solutions: 5

Selecting compatible solution...
```

No red ERROR messages, horror effects or fake crashes.

The tone is **mathematical uncanny**: the system is operating normally, but its assumptions are no longer true.

Future polish target: overlay low-resolution real terrain previews sampled from multiple Phases using the active seed.

---

## 11. Seed-aware tutorial

A fixed linear tutorial conflicts with random seeds.

WorldPhase uses a hybrid model:

1. a controlled opening structure near spawn;
2. a guaranteed first Anchor interaction;
3. programmatic search for natural terrain differences near spawn.

Example target:

- Fracture is difficult to traverse;
- Smooth is easy to traverse.

The player learns the mechanic from naturally generated terrain rather than an artificial corridor.

Tutorial control should decrease over time:

```text
0–10 min: strong guidance
10–30 min: semi-controlled
30+ min: open play
```

---

## 12. Progression structure

```text
Fracture
   │
Smooth
 /    \
Ridge Cell
 \    /
  Warp
   │
Phase Trials
   │
Temporary Stable access
   │
Convergence
   │
Final encounter
   │
Stable / Home
```

Ridge and Cell should be available in either order.

---

## 13. Phase Trials

Mid-game trials test spatial understanding, not combat DPS.

### The Peak — Ridge

A strong ridged extreme contains a Structural Node.

Cell routing helps enter the Ridge mountain interior.

Reward: **Structural Signature**.

### The Boundary — Cell

A location where major cellular regions intersect.

Reward: **Boundary Signature**.

### Origin Scar — Fracture

A late-discovered anomaly beneath or near the opening Anchor Station.

Reward: **Origin Signature**.

---

## 14. Convergence

Convergence is initially presented as suspicious, then recontextualized.

Actual order:

```text
MULTIPLE SOLUTIONS DETECTED
→ WORLD STATE AMBIGUOUS
→ CONVERGENCE INITIATED
```

Convergence did not cause the original collapse. It is the world's automated repair process.

Its flaw: it cannot decide which valid solution should win, so it attempts to force several valid worlds into one.

The player stops forced convergence rather than destroying an evil entity.

---

## 15. Anchor Station reveal

Anchor Stations appear technological but have no conventional builder.

Late-game reveal:

> Anchor Stations are themselves world-generated structures created in response to phase instability.

Their logs are not human journals. They are diagnostics.

Preferred language:

```text
VALID
COMPATIBLE
REJECTED
RESOLVED
CONVERGENCE
```

---

## 16. Final encounter

The Convergence encounter must test the entire Phase system.

Potential sequence:

- Smooth: establish combat rules;
- Ridge: destroy Structural Shell nodes using vertical terrain;
- Cell: locate Boundary Anchors;
- Fracture: forced phase changes and spatial interpretation;
- Warp: overlapping phase behavior and final stabilization.

The final action is a **Disconnect**, not a conventional “kill the world” outcome.

Final diagnostic:

```text
CONVERGENCE REQUIRED?
NO

MULTIPLE VALID SOLUTIONS ACCEPTED
```

---

## 17. Ending

After the final encounter:

```text
STABLE COMPATIBILITY: 100%
SET HOME PHASE: STABLE
```

The player returns to the normal Overworld.

No giant visual spectacle is required.

Ordinary Minecraft should be the spectacle.

Previously placed blocks from temporary Stable visits remain.

Advancement:

**Home.**

---

## 18. Postgame

After returning home, Phase travel changes meaning.

Before:

> I have to travel through these worlds to get home.

After:

> I have a home, so I can explore these worlds deliberately.

Postgame systems may include:

- Portable Phase Shifter;
- Resonance Scanner Mk.III;
- Phase Laboratory;
- configurable Experimental Phases;
- shareable phase presets.

Only postgame should expose terms such as:

- OpenSimplex;
- Worley;
- Ridged;
- frequency;
- octaves;
- lacunarity;
- persistence;
- domain warp.

---

## 19. Scope constraints

Do not turn WorldPhase into:

- a large industrial tech mod;
- a collection of unrelated dimensions;
- a miniboss-per-dimension progression;
- a lore-book reading simulator.

Prefer:

- emergent spatial puzzles;
- terrain-driven resource logic;
- environmental storytelling;
- short system diagnostics;
- reusable systemic mechanics.

---

## 20. Experience target

The strongest success metric is not:

> “The terrain looks cool.”

It is:

> “I started thinking about what the same coordinate looks like in the other Phases.”

Any major feature should pass this test:

> **Does it make “same coordinate, different world” more interesting?**
