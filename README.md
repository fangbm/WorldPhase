# WorldPhase

> Same seed. Same coordinates. Different worlds.

**WorldPhase** is a Minecraft NeoForge mod concept built around one rule:

> The same Minecraft seed can resolve into multiple valid terrain phases, and the player must move between them to survive, explore, and eventually return home.

The player does **not** begin in the Vanilla Overworld. A `Seed Collapse` has left the world with multiple valid terrain solutions. Each Phase shares the same seed and coordinate system, but interprets terrain differently.

The long-term goal is to reconstruct the player's compatibility with the **Stable** phase — the normal Vanilla Overworld.

## Core pillars

- **Shared coordinates** — shifting Phase preserves `x/z`.
- **Terrain as gameplay** — another Phase may turn a wall into a cave, a ravine into a road, or buried resources into exposed geology.
- **Incomplete worlds** — no single Phase is optimal for everything.
- **Return home** — the Vanilla Overworld is an endgame destination, not the starting dimension.

## Main phases

| Phase | Terrain language | Gameplay role |
| --- | --- | --- |
| Fracture | Domain-warped terrain | Starting phase, unstable but survivable |
| Smooth | Low-frequency smooth noise | Building, farming, transport |
| Ridge | Ridged multifractal | Mining, vertical exploration |
| Cell | Worley / cellular terrain | Cavities, underground routing |
| Warp | Interference between phases | Late-game expeditions and anomaly resources |
| Stable | Vanilla Overworld | Home |

## Project status

**Week 1 technical PoC / WPH-007 four Phase worlds.**

The project contains the NeoForge bootstrap and a minimal Phase domain model
with tested, bidirectional Level-key mappings and pure Java 2D Smooth, Ridged,
Worley F1 and DomainWarp noise kernels, a deterministic development profiler,
and the composable `worldphase:phase_noise` density-function type. Smooth,
Ridge, Cell and Fracture now reuse Vanilla Overworld generation with distinct
macro-terrain interpretations of the same seed. Transfer, developer gameplay
commands and tutorial behavior remain deferred.

The first milestone is intentionally small:

1. Generate Fracture, Smooth, Ridge, and Cell from the same base seed.
2. Preserve `x/z` while shifting between them.
3. Resolve safe destination positions.
4. Provide developer commands for rapid comparison.
5. Prototype seed-aware tutorial-location scoring.

If that loop is not fun, the project should not expand into content, bosses, machines, or rendering polish.

## Documentation

- [Game Design v0.1](docs/GAME_DESIGN_v0.1.md)
- [Opening Flow](docs/OPENING_FLOW.md)
- [Technical Design v0.1](docs/TECHNICAL_DESIGN_v0.1.md)
- [MVP Issue List](docs/MVP_ISSUE_LIST.md)
- [Roadmap](docs/ROADMAP.md)
- [Phase domain API](docs/PHASE_DOMAIN.md)
- [Noise kernel API, algorithms and provenance](docs/NOISE_KERNELS.md)
- [Noise profiler usage and local comparison](docs/NOISE_PROFILING.md)
- [Phase noise density API, JSON and seed binding](docs/PHASE_DENSITY.md)
- [Smooth world preset, entry and fixed-seed validation](docs/SMOOTH_WORLD.md)
- [Four Phase presets, diagnostics and runtime validation](docs/PHASE_WORLDS.md)

## Target stack

Pinned development baseline:

- Minecraft 26.1
- NeoForge 26.1.0.19-beta
- Java 25 (JDK)
- ModDevGradle 2.0.148 / Gradle wrapper 9.2.1

See [Development setup](docs/DEVELOPMENT.md) for build and launch instructions,
toolchain provenance, and the WPH-001 validation record.

## Design rule

Every new feature should answer:

> **Does this make “the same coordinate in different worlds” more interesting?**

If not, it probably does not belong in WorldPhase.
