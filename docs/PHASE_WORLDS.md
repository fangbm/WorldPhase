# WPH-007 four Phase worlds

Smooth, Ridge, Cell and Fracture are actual dimensions driven by the same raw
world seed. This adds the three remaining MVP presets and generalizes the
isolated WPH-006 verification tools. No production Java, PhaseDefinition,
kernel, golden output, seed hook or gameplay service changes.

## Resources and actual 26.1 format

All paths below are relative to `src/main/resources/data/worldphase/`.

| Resources | Role |
| --- | --- |
| `dimension/{ridge,cell,fracture}.json` | Existing `minecraft:overworld` type, `minecraft:noise` generator, Overworld multi-noise biome preset, corresponding Phase settings holder |
| `worldgen/noise_settings/{ridge,cell,fracture}.json` | Pinned Vanilla Overworld settings, changing only terrain references in `final_density` and offset references in `preliminary_surface_level` |
| `worldgen/density_function/{ridge,cell,fracture}_noise.json` | Existing seeded `worldphase:phase_noise` kernels |
| `worldgen/density_function/ridge_profile.json` | Centered/bipolar ridge field |
| `worldgen/density_function/cell_{radius,profile}.json` | Clamped radial distance and cubic basin/rim profile |
| `worldgen/density_function/{ridge,cell,fracture}_offset.json` | Cached macro offset, retaining a portion of Vanilla variation |
| `worldgen/density_function/{ridge,cell,fracture}_{gradient,modifier,terrain}.json` | Readable gradient correction and additive Vanilla terrain composition |
| `worldgen/density_function/vanilla_terrain_gradient.json` | Shared original Vanilla gradient subexpression |

Existing Smooth resources remain unchanged. Minecraft 26.1 / NeoForge
26.1.0.19-beta source/data were rechecked: LevelStem reads a generator/settings
holder; multi-noise accepts the Overworld `preset` holder; density resources use
DIRECT_CODEC at their root and holder references inside expressions. A root
string alias is not a legal density registry entry. No `neoforge:seed_override`,
new registry, new Mixin or generator is used. Server datapack format is 101.1;
NeoForge supplies the mod pack metadata, as in [Smooth](SMOOTH_WORLD.md).

The copied settings/surface data comes from the pinned Minecraft JAR. The
format has no noise-settings inheritance; three data snapshots are preferable
to inventing runtime inheritance. All non-router settings and all router
fields except the two mentioned above remain equal JSON values to
Vanilla. The snapshots should be rechecked on a game upgrade.

## Composition and units

Let `O` be Vanilla Overworld offset and `J` its jaggedness signal:

```text
Y = y_clamped_gradient(-64,320,1.5,-1.5)
G(depth,J) = 4 * quarter_negative((depth + J) * vanilla_factor)
phase_offset = O + (retained - 1) * (O + 0.55) + profile
phase_modifier = G(Y + phase_offset, jaggedness_scale * J) - G(vanilla_depth,J)
phase_terrain = vanilla_sloped_cheese + phase_modifier
```

Only sloped-cheese references inside Vanilla `final_density` are substituted.
The cave entrance/range choices, underground spaghetti/pillar/noodle functions,
3D base noise, slides, blending, interpolation and squeeze remain in their
original positions. The correction precedes caves. Preliminary surface
estimation uses the matching Phase offset, so aquifer planning follows terrain.
The kernel alone never decides the complete stone/air density.

Offset/depth units change the simple gradient zero by **128 blocks per unit**.
This scale applies before Vanilla factor, 3D noise, caves and slides; it is not
a promise of an exact block-height offset. Frequencies are inverse input
blocks, warp strength is in input blocks, ridge/cell remaps are dimensionless.

| Phase | Kernel configuration | Profile | Retained O deviations / J scale |
| --- | --- | --- | --- |
| Smooth (unchanged) | smooth; f=0.001; amplitude=0.10 | kernel output | 0.35 / 0.35 |
| Ridge | ridge; f=0.002; amplitude=1; octaves=2 | `2.4 * (kernel - 0.82)` | 0.50 / 0.60 |
| Cell | cell; f=0.003; amplitude=1 | `t=clamp(2*(F1-0.18),0,1)`; `-0.16 + 0.95*t³` | 0.20 / 0.15 |
| Fracture | fracture; f=0.002; amplitude=1.80; warp_frequency=0.0015; warp_strength=1400 | kernel output | 0.35 / 0.20 |

Ridge's two octaves preserve long connected crests at roughly 500/250-block
lattice scales. Bias 0.82 reflects the existing `1-abs(gradient)` kernel's
high-valued distribution, not an assumed zero mean or midpoint 0.5. Its
negative side creates valleys; the scale produces substantial relief without
changing the kernel or adding higher octaves.

Cell uses about 333-block feature spacing. Low F1 values form basin floors;
the cubic transition raises surrounding interiors gradually then strongly;
far-feature distances form high rims/shared uplands. Saturation bounds the
rim height and gives broad traversable boundary regions. Jittered features
remain irregular, never a regular hexagonal layout. **F1 alone does not identify
exact Voronoi boundaries**: these are high-distance rim regions, with gaps and
connected low routes where nearby features overlap. No F2-F1 or cave changes.

Fracture uses a 500-block base lattice warped by independent existing stable
channels at about 667-block spacing. Strength 1400 gives measured displacements
around 279 blocks while adjacent vectors remain continuous. This bends and
stretches the modifier's landforms rather than adding high-frequency jaggedness.
It does **not** warp every Vanilla climate/cave coordinate: those stay original.

All custom leaves bind the same raw seed through the unchanged WPH-005
RandomState visitor. Only DomainWarp's existing stable channel mixing is used;
no per-Phase seed offsets, mutable randomness or additional salts.

## Fixed-seed diagnostic observations

2026-10-08, Windows x64, Temurin 25.0.3+9, seed **123456789**. Highest solid base
terrain y from actual NoiseBasedChunkGenerator (no fluid/decoration), not
long-term height snapshots:

| x | z | Overworld | Smooth | Ridge | Cell | Fracture |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 0 | 54 | 55 | 115 | 60 | 55 |
| 256 | 256 | 63 | 64 | 90 | 76 | 93 |
| -512 | 768 | 100 | 73 | 124 | 47 | 78 |
| 1024 | -1024 | 40 | 56 | 101 | 58 | 51 |
| 2048 | 2048 | 96 | 71 | 104 | 55 | 102 |
| -2048 | -2048 | 70 | 65 | 94 | 51 | 46 |
| 4096 | 0 | 37 | 53 | 71 | 65 | 96 |
| 0 | 4096 | 54 | 59 | 72 | 42 | 128 |
| -4096 | 2048 | 89 | 69 | 86 | 47 | 120 |
| 8192 | -4096 | 65 | 65 | 71 | 167 | 37 |

Five 5x5 neighborhoods centered at (-512,768), (2048,2048), (-2048,-2048),
(-4096,2048), (8192,-4096) give 125 samples and 200 edges at 64-block spacing:

| World | min y | max y | relief | Mean absolute 64-block height change |
| --- | ---: | ---: | ---: | ---: |
| Overworld | 35 | 118 | 83 | 13.095 |
| Smooth | 45 | 98 | 53 | 6.590 |
| Ridge | 23 | 143 | 120 | 16.405 |
| Cell | 23 | 172 | 149 | 18.280 |
| Fracture | 10 | 164 | 154 | 18.115 |

Same-coordinate climate target points match exactly across all five worlds.
Ridge's slope is 2.49x Smooth, and relief 2.26x. It retains low valleys rather
than globally raising all columns: 23/125 samples are below sea level;
34/36 samples above y=100 connect to neighboring high ground above y=90
at 64-block spacing. Smooth retains land/water/hills.

Mean absolute pairwise height differences across the ten dispersed coordinates:

| Pair | Mean delta (blocks) | Differing coordinates |
| --- | ---: | ---: |
| Smooth / Ridge | 29.80 | 10/10 |
| Smooth / Cell | 22.80 | 10/10 |
| Smooth / Fracture | 28.00 | 9/10 |
| Smooth / Overworld | 11.60 | 9/10 |
| Ridge / Cell | 45.20 | 10/10 |
| Ridge / Fracture | 35.80 | 10/10 |
| Ridge / Overworld | 26.60 | 10/10 |
| Cell / Fracture | 43.20 | 10/10 |
| Cell / Overworld | 33.40 | 10/10 |
| Fracture / Overworld | 28.60 | 10/10 |

Cell diagnostic: a 32-block grid over [-768,768]² finds 76 near-feature samples
(F1<0.10), mean y=42.39, and 471 far-feature rim samples (F1>0.62), mean y=156.95.
Of 20 near-feature candidates aligned to the coarser grid, **11 have at least
6/8 surrounding directions rise >12 blocks at radius 160**. This measures
enclosing basins in actual generated columns, not merely the kernel's name.
Incomplete enclosure is intentional evidence of passages/merging basins, not
a claim that every feature gets a sealed bowl.

Fracture diagnostic: 1,089 sampled warp vectors have mean length **279.47**
blocks, maximum **653.85**, and maximum adjacent one-block vector change
**3.032**. The seeded adapter exactly equals sampling the original GradientNoise
at those displaced coordinates. Warp on/off at identical amplitude/frequency/
seed changes base terrain by **24.71 blocks on average** across 125 samples;
98/125 differ by >8 blocks. The unwarped comparison is test-only, not a fifth
Phase or alternate production preset.

## Runtime verification

`phaseVerification` replaces the Smooth-only source set. One dev mod covers
all four Phases and Overworld. Dedicated-server code is Dist-gated; client
code uses native player teleport, rendering and Screenshot APIs. It is not a
TerrainSampleService or PhaseTransferService. Ordinary runs load only
WorldPhase, and no verification class/resource goes into its production JAR.

The dedicated probe generates **10 FULL chunks per world (50 total)**. It
hashes all 16x16x384 block states and biomes separately, hashes both heightmaps
for all columns, records surface block histograms, fluid/cave/ore/vegetation
counts, checks raw Level seeds and Vanilla simulated-player entry, and locates
three Vanilla structure tags in every world. It saves, flushes and stops.
On the second run the entire report must exactly equal its saved baseline.

Fresh server and persisted-world restart both reached Done. **All 50 complete
block/biome/height fingerprints, fluid and pipeline counts, surfaces and the
15 located structure positions matched across save/stop/reload.** Both runs
exited successfully without registry, density codec or Mixin errors.

Counts summed over the ten sampled FULL chunks in each world:

| World | Underground air | Water | Lava | Ore blocks | Logs/leaves |
| --- | ---: | ---: | ---: | ---: | ---: |
| Overworld | 26,635 | 18,488 | 214 | 4,256 | 1,829 |
| Smooth | 26,823 | 9,091 | 214 | 4,154 | 2,165 |
| Ridge | 36,111 | 285 | 214 | 5,209 | 2,300 |
| Cell | 20,496 | 28,787 | 214 | 4,440 | 621 |
| Fracture | 27,892 | 15,431 | 232 | 5,277 | 1,925 |

Grass/dirt/stone surfaces and desert sand/sandstone remain; river/ocean/basin
floors retain gravel, sand, clay and water. Forest/jungle tree features and
coal/copper/iron/etc. ore generation remain. Cave counts vary with terrain,
but no sampled wholesale cave fill, surface lava flood or missing pipeline.
FULL heightmaps include canopy/decorations and must not be confused with the
base terrain table above. These finite samples are not exhaustive topology
or playability guarantees.

Every world locates village (-272,0,160), mineshaft (-144,0,-128) and ruined
portal (384,0,336). This confirms the retained structure-placement pipeline;
locator y is not a landing height or a guarantee of ideal adaptation to slopes.

## Native development entry and reproduction

```mcfunction
/gamemode spectator
/execute in worldphase:ridge run tp @s -512 220 768
/execute in worldphase:cell run tp @s -512 220 768
/execute in worldphase:fracture run tp @s -512 220 768
```

Only Vanilla commands; no `/wp` command or safe-landing behavior.

```sh
./gradlew test --tests dev.fangbm.worldphase.worldgen.PhaseWorldTest \
  --tests dev.fangbm.worldphase.worldgen.noise.FracturePresetTest --no-build-cache
./gradlew build --no-build-cache
./gradlew runPhaseVerificationServer --console=plain
./gradlew runPhaseVerificationServer --console=plain
./gradlew runPhaseVerificationClient --console=plain
```

See [development setup](DEVELOPMENT.md#wph-007-four-phase-worlds) for isolated
seed, EULA, copied-client-world and options setup. Use a fresh server directory
after changing presets; saved chunks cannot validate newly tuned terrain.

## Actual native client observations

The graphical client entered all five worlds and saved **15 native 1280x720
images**, all visually inspected, on the RTX 5070 Ti Laptop GPU. Three matching
cameras use x/z (-383.5,896.5), (128.5,128.5), (-2047.5,-2047.5); y=220,
yaw=135, pitch=45. Daytime, spectator, render distance 16, client simulation
distance 5 (the normal 26.1 client option minimum), clouds disabled. Each
capture waited at least 18 seconds, then for the inner 14-chunk circle and the
native section compile queue to settle for one second. All captures reported
**1,057 loaded chunks and pC=000**. The final run exited normally and saved the
isolated copied world. No density codec, registry or Mixin error.

| Camera area | Smooth | Ridge | Cell | Fracture |
| --- | --- | --- | --- | --- |
| Forest (-384,896) | Lower central hills and broad water margins | Connected high forest crests, steep grassy sides, contracted valleys | Tall curved walls around a large water basin, snowy higher rim, visible inward slopes | Forested landform/shoreline shifted into an asymmetric peninsula with an enlarged cove |
| River/plain (128,128) | Broad low plateau, small lakes and low river banks | High arcing ridge and continuous slopes around the lower water pocket | Large adjacent basins separated by a narrow raised saddle/boundary | Long water channel cuts the former broad plain, leaving an elongated raised land tongue and shifted forest shore |
| Desert (-2048,-2048) | Low dunes and shallow coastal terrain | Continuous high sand/stone wall, exposed cave entrances | Narrow high boundary spines between wide basins, distinct from ordinary dunes | Much of the former coast becomes submerged; fragmented islands and bent seabed/coastal relationships |

Normal grass/dirt/stone/sand textures, trees, coral/kelp, water surfaces and
cave openings render; no widespread surface lava or missing-surface disaster
was observed. These views make the Phase differences visible, though Fracture
is easiest to distinguish at the plain's displaced channel rather than in a
uniform ocean area. Screenshots stay ignored under
`run/phase-verification-client/screenshots/wph007-{0,1,2}-{world}.png`.

## Verification coverage

Six PhaseWorldTest cases and one FracturePresetTest case cover the four real
dimension/settings codecs and Level keys, original biome source, unchanged
Vanilla settings/climate/aquifer/ore/surface/cave trees, same-seed column
determinism, pairwise terrain differences, macro slopes/relief, connected Ridge
crests/low valleys, Cell enclosing-basin/rim statistics, Fracture warp vectors
and actual warp-on/off terrain. They assert broad properties, never a final
height snapshot or elapsed-time threshold.

Full build: **132 tests, zero failures/errors/skips**: 7 new preset diagnostics,
3 existing Smooth, 51 density/codec, 34 unchanged noise golden/regression,
29 Phase domain, 8 profiler. Production packaging contains four dimension
presets and existing main code; no dev classes, verification metadata, tests,
screenshots, game directories or generated source. Kernel Java and all Smooth
production resources are unchanged from main.

## Limits and next step

Initial PoC presets, not final art. Climate remains Vanilla: elevated ocean
biomes can become bare grassy land, lowered forest/desert regions can flood,
and biome labels are not recalculated to match a Phase's new altitude. Cell
often has water-filled basins and saturated broad uplands; its F1 rim proxies
are not exact Voronoi boundaries. Fracture bends only the modifier, retaining
Vanilla climate and cave relationships. Vanilla caves/3D noise can still create
local abrupt edges/overhangs. Structure slope adaptation and survival routes
remain unproven by this smoke check.
The desert client comparison also logged a villager suffocating in a wall;
its dimension is not identified in that Vanilla log. Structure lookup remains
valid, but complete village-piece/entity adaptation to steep Phase terrain
has not been established and is not claimed here.

Next after review: **#8 / WPH-008 Implement TerrainSampleService**. This change
stops at #7 without transfer, commands, content, shaders or tutorial work.
