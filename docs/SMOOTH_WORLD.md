# WPH-006 Smooth world

`worldphase:smooth` is the first enterable Phase dimension. It uses the same
base world seed as Overworld, with a different terrain interpretation. This
change adds only Smooth; kernels, seed binding, PhaseDefinition and the other
three Phase definitions are unchanged.

## Actual 26.1 resources

All production data is under `src/main/resources/data/worldphase/`:

| Path | Responsibility |
| --- | --- |
| `dimension/smooth.json` | `minecraft:overworld` dimension type; `minecraft:noise` generator; `minecraft:multi_noise` biome source with `minecraft:overworld` preset; `worldphase:smooth` settings |
| `worldgen/noise_settings/smooth.json` | Pinned Vanilla Overworld settings, with only `final_density` terrain references and `preliminary_surface_level` offset references changed |
| `worldgen/density_function/smooth_noise.json` | Existing `worldphase:phase_noise`, algorithm `smooth`, frequency `0.001`, amplitude `0.10` |
| `worldgen/density_function/smooth_offset.json` | Cached additive macro offset correction |
| `worldgen/density_function/smooth_terrain.json` | Vanilla sloped-cheese density plus the Smooth gradient correction |

Verified against Minecraft 26.1 / NeoForge 26.1.0.19-beta generated sources and
JAR data. The server's `version.json` reports data format **101.1**, also stated
in the [official 26.1 pre-release notes](https://www.minecraft.net/ja-jp/article/minecraft-26-1-pre-release-3).
The mod data is supplied through NeoForge's built-in mod data pack; no standalone
pack metadata is necessary. [NeoForge resource documentation](https://docs.neoforged.net/docs/resources/)
describes these dynamic-registry resource folders.

26.1 `LevelStem.CODEC` reads `type` and `generator`; NeoForge adds an optional
`neoforge:seed_override`. It is deliberately omitted. MultiNoiseBiomeSource
accepts the `preset` holder form; NoiseBasedChunkGenerator resolves a
noise-settings holder. Density references use the existing holder/MapCodec
dispatch described in [Phase density](PHASE_DENSITY.md).

No dimension type, biome, structure set, chunk generator or Java worldgen
registry is invented. Vanilla's existing Overworld dimension type is reused
instead of the design's optional generic `dimension_type/phase.json`.
The noise-settings JSON includes Vanilla surface rules because this format has
no settings-inheritance field. It is derived from the pinned game's
`data/minecraft/worldgen/noise_settings/overworld.json`; it remains Minecraft
data, not an independently authored/relicensed surface implementation. Recheck
this snapshot and codec layout when upgrading the game.

## Density scale and integration

The inspected `NoiseRouterData` builds the terrain signal as:

```text
depth = y_clamped_gradient(-64,320,1.5,-1.5) + offset
gradient(depth,jaggedness) = 4 * quarter_negative((depth + jaggedness_signal) * factor)
vanilla_sloped_cheese = gradient + vanilla_base_3d_noise
```

The vertical gradient changes by **1/128 per block**. The preset therefore
uses amplitude `0.10` in *offset/depth units*, with at most 12.8 blocks of
zero-crossing excursion from the kernel's conservative [-1,1] bound. Actual
gradient samples are smaller. This is not amplitude 64 from the prior issue's
deliberately exaggerated isolated test, nor a scalar added to squeezed final
density. Frequency `0.001` gives a 1000-block lattice spacing.

The selected data composition is:

```text
smooth_offset = vanilla_offset
              - 0.65 * (vanilla_offset + 0.55)
              + phase_noise(baseSeed, frequency=0.001, amplitude=0.10)
smooth_depth = vanilla_y_gradient + smooth_offset
smooth_jaggedness = 0.35 * vanilla_jaggedness_signal
smooth_terrain = vanilla_sloped_cheese
               + (gradient(smooth_depth,smooth_jaggedness)
                  - gradient(vanilla_depth,vanilla_jaggedness))
```

This retains 35% of Vanilla macro offset deviations around -0.55 (the simple
gradient's zero at y=57.6), retaining continental/ocean/river distinctions while
reducing mountain excursions. Jaggedness is similarly reduced, not removed.
The low-frequency coherent field supplies gentle additional hills. A standalone
low-frequency additive signal cannot by itself reduce Vanilla mountains;
the explicit Vanilla-derived correction makes the Smooth interpretation milder.

Only occurrences of `minecraft:overworld/sloped_cheese` inside `final_density`
are replaced with `worldphase:smooth_terrain`. The original entrance/range-choice,
underground cave, spaghetti/pillar/noodle, top/bottom slide, blending,
interpolation and squeeze operations remain. The modifier is applied **before**
these cave operations, avoiding a final-density offset that would fill caves.
It is never used as complete stone/air density.

Preliminary surface estimation uses the same Smooth offset so aquifer/surface
planning follows the changed terrain. The router's `depth`, `continents`,
`erosion`, `ridges`, `temperature` and `vegetation` climate fields are unchanged,
as are aquifer noise fields and ore-vein fields. All other settings, sea level
63, aquifers, ore veins, biomes/features, spawn targets and surface rules remain
the pinned Vanilla values. Seeds still bind through the existing WPH-005 hook;
no seed overrides, new salts or new Mixins are added.

## Native development entry

Create a fresh world with seed `123456789`, cheats enabled, using the regular
client or server run. In player chat:

```mcfunction
/gamemode spectator
/execute in worldphase:smooth run tp @s -512 140 768
/execute in minecraft:overworld run tp @s -512 140 768
```

For a server console, replace `@s` with the connected player's name. This uses
Vanilla commands only. It is a development teleport, not a safe-landing policy
or a `/wp` gameplay command. New Smooth chunks save under the Vanilla custom
dimension directory `world/dimensions/worldphase/smooth`.

## Fixed-seed comparison (2026-10-08)

Windows x64 / Temurin 25.0.3+9; both worlds use seed **123456789**. The following
are **base-column highest solid terrain y**, excluding fluids and decoration,
measured through the actual Vanilla NoiseBasedChunkGenerator with each decoded
preset. They are observations, not golden height snapshots:

| x | z | Overworld | Smooth |
| ---: | ---: | ---: | ---: |
| 0 | 0 | 54 | 55 |
| 256 | 256 | 63 | 64 |
| -512 | 768 | 100 | 73 |
| 1024 | -1024 | 40 | 56 |
| 2048 | 2048 | 96 | 71 |
| -2048 | -2048 | 70 | 65 |
| 4096 | 0 | 37 | 53 |
| 0 | 4096 | 54 | 59 |
| -4096 | 2048 | 89 | 69 |
| 8192 | -4096 | 65 | 65 |

Five 5x5 neighborhoods around the land coordinates (-512,768), (2048,2048),
(-2048,-2048), (-4096,2048), (8192,-4096), sampled at 64-block spacing, provide
200 adjacent edges. Mean absolute height change: **13.095 Vanilla vs 6.590
Smooth**, about **49.7% lower**. Smooth still ranges 53..73 in the dispersed
base-column observations, crossing sea level; it is not one flat plane.
125 same-coordinate climate samples match exactly, demonstrating that the
biome routing has not been randomized or altered to create the difference.

## Actual server generation and reload

`runSmoothVerificationServer` loads a separate dev-only acceptance mod and the
production JSON through the real worldgen loader. It generates FULL chunks at
all ten coordinates in both dimensions, records surface/biome/fluid/cave data,
and hashes 25 complete 384-block columns per coordinate (block state plus biome).
See [Development setup](DEVELOPMENT.md#wph-006-smooth-world) for setup/commands.

Observed generated data includes:

- `(-512,768)`: forest, grass_block over dirt in both worlds; Smooth floor 73
  versus Vanilla 100. Smooth's sampled region contains 186 underground air blocks.
- `(-2048,-2048)`: desert, sand/sand, Smooth floor 65 versus Vanilla 70.
- `(0,0)` and `(256,256)`: river biomes and gravel/sand shores; retained water.
- `(1024,-1024)`: deep_ocean, water surface y=62, Smooth floor 56; 297 water
  and 710 underground air blocks in the sampled Smooth region.
- `(4096,0)`: deep_cold_ocean, gravel over stone, Smooth floor 53; water surface
  y=62, 191 water blocks and 77 underground air blocks.
- `(0,4096)`: cold_ocean; 747 underground air blocks and 102 water blocks.
- `(8192,-4096)`: forest, with sampled underground water and lava (77 / 12
  blocks in both dimensions). Jungle trees/features also generated at (2048,2048).

Full-chunk heightmaps include trees/features: e.g. (2048,2048) reports 122/72,
while undecorated base columns are 96/71. Do not confuse canopy height with
ground height or use height alone to infer missing surface blocks.

Vanilla structure tags locate in Smooth: village **(-272,0,160)**, mineshaft
**(-144,0,-128)**, ruined_portal **(384,0,336)**. Locator y=0 is its returned
search position, not a verified build/landing height. This confirms structure
placement/biome eligibility works; full structure correspondence is deferred.

A server-side NeoForge FakePlayer exercises Vanilla ServerPlayer's actual
cross-dimension teleport and asserts its Level becomes `worldphase:smooth`.
This is separate from the connected graphical-client check below.

The initial server reached `Done (2.555s)`, generated the regions, flushed saves
and stopped normally. Restart reached `Done (0.444s)` and re-read the persisted
chunks. **All 20 block-state/biome fingerprints, sampled heights/blocks/fluid
counts, and the three structure positions matched exactly.** The acceptance
probe now flushes and halts the isolated server automatically after checking.
Both runs exited successfully; no density codec, registry or Mixin error.

## Native client check

An isolated graphical client loads a copy of the verified server world through
Vanilla Quick Play. A client-only class in the same dev source set moves the
actual integrated-server player through Vanilla teleport, waits twelve seconds
for rendering at each view, and calls Minecraft's native Screenshot API. No
renderer, shader, GUI, gameplay transfer code or screenshot assets are packaged
in the mod. Dist annotations keep this client tooling out of dedicated-server
loading; ordinary client/server runs do not load the verification mod.

```sh
./gradlew runSmoothVerificationClient --console=plain
```

Prepare `run/smooth-verification-client/saves/wph006` with the **contents** of
the stopped verification server's `world` directory (level.dat directly under
wph006). Set initial accessibility onboarding off after completing it normally,
and disable pause-on-lost-focus in this isolated client's options. The helper
accepts Vanilla's experimental-world backup prompt only for this copied test
world. It exits after all six native screenshots exist, and the Gradle task
checks the completion marker. Missing completion fails rather than treating a
main-menu launch as visual acceptance. Existing screenshots from this run are
cleared before capture; source worlds and other run directories are unaffected.

Camera positions (x/z, y=140, yaw=135, pitch=25) are (-383.5,896.5),
(128.5,128.5), and (1152.5,-895.5); daytime is set in each target dimension,
with matching render distance 6. Files are
`run/smooth-verification-client/screenshots/wph006-{0,1,2}-{overworld,smooth}.png`.
These views correspond to the forest, river/shore and ocean comparison regions
respectively.

Actual client `Dev` logged in, entered Smooth, rendered both worlds and saved
six 854x480 images on the NVIDIA RTX 5070 Ti Laptop GPU. The native images were
inspected: grass/stone/dirt textures, trees, river shores and ocean water render
normally; forest macro relief is lower, and the river-side plateau becomes a
lower bank with more visible water. Ocean views retain the water surface. The
client then shut down and saved its copied world normally. Screenshots and game
files remain ignored local evidence, not repository assets.

## Tests, limits and next step

Three focused tests cover key/preset decoding, unchanged Vanilla fields and
surface rules, no seed override, fixed-seed column determinism, terrain
differences, remaining relief, macro slope reduction and identical climate.
The server run provides actual registry loading, FULL chunk surface/features,
simulated entry, structure lookup and disk save/stop/reload evidence.

Full build: **125 tests, zero failures/errors/skips**: 3 Smooth + 51 density/
codec + 34 unchanged noise golden/regression + 29 Phase domain + 8 profiler.
The production JAR contains only Smooth world data alongside existing mod
code; no verification classes/metadata, screenshots or game outputs.

The comparison proves milder macro terrain through measurements and native
client views. This is a limited visual smoke check, not a survival playtest. Known
preset tradeoffs: oceans become shallower, mountain biomes retain their Vanilla
climate labels on lower hills, and Vanilla 3D noise/caves can still make local
overhangs or abrupt cave entrances. This is initial PoC tuning, not final art.
No sampled water/cave disaster was detected, but this finite set is not an
exhaustive aquifer/topology guarantee across seeds.

Stop at #6 and review. **#7** adds Ridge/Cell/Fracture worlds later; transfer,
commands, tutorial, content and visuals remain outside this change.
