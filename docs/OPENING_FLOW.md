# WorldPhase — Opening Flow v0.1

This document defines the intended first ~2 hours of a first playthrough. It is a pacing target, not a hard scripted sequence.

WPH-013 [negative feasibility closeout](TUTORIAL_FEASIBILITY_DECISION.md) revises
the first lesson to Hybrid Opening. Station/spawn/lesson content below is planned,
not implemented by the bounded search prototype.

## 0:00 — World generation

The player creates a world through the normal Vanilla UI.

The loading screen initially behaves normally.

Later, short anomalies appear:

```text
Preparing spawn area...
Resolving terrain...
Preparing spawn area...
```

Near completion:

```text
Validating seed...

Expected solutions: 1
Resolved solutions: 5

Selecting compatible solution...
```

Then everything returns to normal:

```text
Joining world...
```

The player spawns in Fracture.

The opening must not explain what “solutions” means.

---

## 0:00–0:05 — Fracture

The world is clearly Minecraft, but terrain relationships feel wrong:

- warped ridges;
- stretched valleys;
- abruptly redirected rivers;
- unusual transitions.

Normal survival remains possible.

A subtle one-time HUD message may identify:

```text
PHASE // FRACTURE
```

No tutorial wall of text.

---

## ~0:03 — Anchor Station

A damaged Anchor Station exists near spawn.

It contains a weak Phase Anchor.

Initial state may resemble:

```text
CURRENT: FRACTURE
AVAILABLE: UNKNOWN
SIGNAL DETECTED
```

The Station provides enough early resources to avoid turning the first interaction into a mining grind.

---

## ~0:08 — Controlled first terrain interaction

The planned Station provides a controlled, safe and predictable local lesson where:

- Fracture traversal is significantly harder;
- Smooth traversal is significantly easier.

It requires safe entry, a Fracture obstacle, a traversable Smooth bypass and safe
exit/return with normal bounded Shift positions. A found natural candidate is
not automatically selected during world initialization and is not required to
guarantee this first interaction.

Nearby natural differences can supply subsequent optional lessons, for example:

- a ravine;
- a steep ridge;
- a blocked route;
- dangerous terrain;
- a large local height discontinuity.

Those optional targets are seed-dependent and need separate route validation.
If none qualifies, use the controlled opening rather than weakening safety or
selecting an unverified candidate. Station layout and fallback realization remain
future design/implementation work.

---

## ~0:10 — First Shift

The player activates the Anchor.

There is no conventional portal.

The same coordinate resolves into Smooth.

Expected realization:

> I did not move. The world changed.

The player crosses the previously difficult area in Smooth.

Returning to Fracture at a corresponding Anchor position places them on the far side.

This is the first lesson:

> Another Phase can provide a different route through the same space.

No “Congratulations, you learned Phase Shift” popup is required.

---

## ~0:15 — First Stable Echo

A Stable Echo appears.

Through it the player sees a completely normal Vanilla scene at the same coordinate.

Approaching may produce:

```text
UNKNOWN PHASE
COMPATIBILITY: REJECTED
```

The Echo collapses.

The player now knows that a normal world exists.

---

## ~0:18 — First explicit objective

The Anchor Station identifies:

```text
Stable signal detected.
Synchronization unavailable.
Additional phase signatures required.
```

The motivation is established:

> Find a way back.

---

## ~0:20–0:30 — Second spatial lesson

A second seed-aware target teaches solid/air correspondence.

Example:

```text
FRACTURE: SOLID
CELL: VOID
```

The player enters a cavity through the alternate Phase, moves to the target coordinate, then returns to the original Phase.

Second lesson:

> Phase travel can alter access to enclosed space, not just surface routes.

After this point, strong tutorial control should end.

---

# 0:30–1:00 — Establishing a base

Smooth naturally becomes the attractive base Phase.

Reasons should emerge from the world itself:

- flat terrain;
- better agriculture;
- easier transport;
- coherent waterways.

The player repairs the Anchor Station using Fracture and Smooth materials.

Early anomaly material:

**Phase Residue**

It appears preferentially where phase terrain differs strongly.

This begins teaching the player to seek differences rather than simply visit dimensions.

---

## Resonance Scanner Mk.I

The first core tool answers:

```text
FRACTURE: SOLID
SMOOTH: AIR
```

or:

```text
FRACTURE SURFACE: 121
SMOOTH SURFACE: 76
```

The Scanner reduces repetitive blind shifting and turns phase comparison into intentional gameplay.

---

## Unknown signatures

The repaired Station detects two unknown signatures.

They lead to Ridge and Cell.

The player may choose either order.

---

# 1:00–2:00 — Ridge and Cell

## First Ridge visit

Ridge should deliver the first major terrain spectacle.

Expected observations:

- huge ridges;
- exposed geology;
- extreme height;
- difficult traversal.

The player should infer that Ridge is good for mining without being explicitly told.

Key material:

**Resonant Crystal**

Its generation should correlate with strong geological gradients.

---

## First Cell visit

Cell should contrast Ridge by emphasizing space rather than height.

Expected features:

- basins;
- enclosed regions;
- large cavities;
- strong boundaries.

The player should infer:

> Cell is good for finding paths through the underground.

Key material:

**Cellstone**

It should correlate with important cellular boundaries.

---

## First Ridge / Cell combination

A desirable early emergent sequence:

```text
RIDGE: solid mountain with valuable geology
CELL: cavity through the same coordinate
```

The player enters through Cell, moves underground, then returns to Ridge inside the mountain.

This establishes the early relationship:

> Ridge is resource-rich. Cell is route-rich.

---

## Phase Anchor Mk.II

Crafted from materials obtained through both branches.

Unlocks:

- Fracture;
- Smooth;
- Ridge;
- Cell;
- expanded range;
- Home Anchor;
- Phase Recall.

At this point, players may naturally build a Phase Hub at one shared coordinate.

---

## First attempted Stable entry

The player briefly enters Stable.

For a few seconds it is genuinely playable.

Then visual/audio desynchronization increases and the player is rejected.

Important lesson:

> The doorway works. The player does not yet belong there.

---

## End of the opening chapter

The Anchor detects:

```text
PHASE // UNKNOWN
STATUS // UNSTABLE
```

This points toward Warp.

By ~2 hours the player should independently think:

- “Would Smooth be easier to cross?”
- “Is this coordinate empty in Cell?”
- “Would Ridge expose this resource?”
- “Can I build all four phase stations at the same x/z?”

If those thoughts occur naturally, the opening has succeeded.
