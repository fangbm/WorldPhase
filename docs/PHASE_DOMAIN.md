# Phase domain model (WPH-002)

All four types live in `dev.fangbm.worldphase.phase` and use common/server-safe
Minecraft classes. They do not register dimensions or load worlds.

## Identity and metadata

`PhaseId` contains exactly `FRACTURE`, `SMOOTH`, `RIDGE` and `CELL`.
These enum constants are stable internal identities. Display text and enum
ordinals are not identity contracts. Dimension identifiers are explicit in the
catalog and are not derived from display labels or enum spelling.

`PhaseDefinition` is an immutable record with only:

- `PhaseId id`
- `ResourceKey<Level> levelKey`

Both fields are required. The conceptual terrain/algorithm parameters listed
in Technical Design v0.1 section 5 are deliberately deferred to the issues that
will validate them, per WPH-002's scope. No custom registry is introduced.

## Single source and mapping

`PhaseCatalog.all()` returns an immutable list in the explicit MVP order shown
below. `PhaseCatalog.get(PhaseId)` returns a definition. The resolver derives
its reverse map from this catalog rather than maintaining another set of
hardcoded identifiers.

| Phase identity | Level key |
| --- | --- |
| `FRACTURE` | `worldphase:fracture` |
| `SMOOTH` | `worldphase:smooth` |
| `RIDGE` | `worldphase:ridge` |
| `CELL` | `worldphase:cell` |

`PhaseWorldResolver` provides:

```java
ResourceKey<Level> levelKey(PhaseId id)
Optional<PhaseId> phaseId(ResourceKey<Level> levelKey)
Optional<PhaseId> phaseId(ServerLevel level)
```

Reverse resolution uses the exact catalog key. Overworld, Nether, End,
another mod's `smooth`, unknown WorldPhase keys, and future `warp` / `stable`
keys return `Optional.empty()`. There is no default Phase. Null arguments
are programming errors and fail with `NullPointerException`.

A successful mapping describes identity only: it does not guarantee that the
corresponding `ServerLevel` exists in a running server. Phase dimension data
and world generation remain future work.

## Actual Minecraft 26.1 API

Verified against the pinned NeoForge 26.1.0.19-beta generated sources:

- `Identifier.fromNamespaceAndPath(namespace, path)` constructs namespaced IDs.
- `ResourceKey.create(Registries.DIMENSION, identifier)` creates Level keys.
- `ResourceKey.identifier()` exposes the identifier; `isFor(Registries.DIMENSION)`
  checks its registry.
- `ServerLevel.dimension()` inherits `ResourceKey<Level>` from `Level`.

This baseline uses `Identifier`, rather than older examples using
`ResourceLocation`, and `identifier()`, rather than `location()`.
No compatibility layer or version change is needed.

## Test boundary

`PhaseDomainTest` checks exact forward identifiers, independent-key reverse
resolution, round trips, the four-entry immutable catalog, unique keys,
required metadata, and Vanilla/unknown key behavior. Both known and unknown
`ServerLevel` overload cases use mocked levels with real Minecraft keys.
This is unit evidence for the mapping adapter, not live Phase-world acceptance
or the later WPH-012 gameplay regression suite.
