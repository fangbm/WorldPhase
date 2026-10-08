# WPH-005 configurable Phase noise density function

`worldphase:phase_noise` is one composable Minecraft density-function type.
`PhaseNoiseDensityFunction` delegates directly to the existing pure Java kernels:

```text
output = kernel.sample(worldSeed, context.blockX(), context.blockZ()) * amplitude
```

It ignores y because the MVP kernels are 2D. It adds no chunk generator, Phase
dimension or terrain preset. Later settings can compose
`finalDensity = vanillaDensity + phaseModifier` using Vanilla `minecraft:add`.

## Verified 26.1 API

Inspected the project's generated
`build/moddev/artifacts/minecraft-patched-26.1.0.19-beta-sources.jar`, the
NeoForge 26.1.0.19-beta sources, and the
[official 26.1 MDK](https://github.com/NeoForgeMDKs/MDK-26.1-ModDevGradle) and
[26.1 registry documentation](https://docs.neoforged.net/docs/concepts/registries/).
The pinned Minecraft/NeoForge/Java baseline is unchanged.

| Responsibility | Actual API used |
| --- | --- |
| Leaf interface | `DensityFunction.SimpleFunction` |
| Sampling | `compute(DensityFunction.FunctionContext)`; `blockX()` / `blockZ()` are ints |
| Bulk sampling / traversal | SimpleFunction defaults: `ContextProvider.fillAllDirectly`, `Visitor.apply(this)` |
| Function codec return | `KeyDispatchDataCodec<PhaseNoiseDensityFunction>` |
| Registered type value | `MapCodec<? extends DensityFunction>` (`CODEC.codec()`) |
| Static type registry | `BuiltInRegistries.DENSITY_FUNCTION_TYPE` / `Registries.DENSITY_FUNCTION_TYPE` |
| Datapack instance registry | `Registries.DENSITY_FUNCTION`; entries are density-function templates |
| Inline type dispatch | `DensityFunction.DIRECT_CODEC` dispatches by registered MapCodec |
| Holder/reference codec | `DensityFunction.CODEC` is `Codec<Holder<DensityFunction>>` via `RegistryFileCodec`; `HOLDER_HELPER_CODEC` uses `DensityFunctions.HolderHolder` |
| Registration | `DeferredRegister<MapCodec<? extends DensityFunction>>`, `DeferredHolder<..., MapCodec<PhaseNoiseDensityFunction>>`; attach to constructor-injected `IEventBus` |

`ModDensityFunctions.TYPES` registers `phase_noise` in Vanilla's existing
registry through NeoForge's mod-bus `RegisterEvent`. This creates no custom
registry. The identifier is `net.minecraft.resources.Identifier` in 26.1.

## JSON schema

Minimal inline value or `data/<namespace>/worldgen/density_function/<name>.json`:

```json
{
  "type": "worldphase:phase_noise",
  "algorithm": "smooth",
  "frequency": 0.004,
  "amplitude": 1.0
}
```

| Field | Required/default | Validation / meaning |
| --- | --- | --- |
| `algorithm` | Required | Exactly `smooth`, `ridge`, `cell`, `fracture` |
| `frequency` | Required | Finite and positive; base kernel frequency in block-coordinate units |
| `amplitude` | Required | Finite in `[-1000000,1000000]`; final scalar multiplier only. Zero disables the signal; negatives invert it |
| `octaves` | Default `2` | Integral `1..8`; used by Ridge. Fractional values are rejected, not truncated |
| `warp_frequency` | Default `0.008` | Finite and positive; used by Fracture |
| `warp_strength` | Default `32` | Finite and nonnegative, in block-coordinate units; used by Fracture |

These are adapter defaults, not four Phase terrain presets. All supplied fields
are validated even when not used by the selected algorithm. Missing/malformed
optional values are distinct: missing uses the default, malformed fails.
Unknown algorithm and invalid numeric configuration return codec `DataResult`
errors. No seed field is exposed or serialized.

Frequency/warp combinations are also checked against the kernels' scaled
coordinate limit ±2^50 for every possible int-valued context (maximum absolute
coordinate 2^31), including each Ridge octave and the maximum warp displacement.
Unsupported extreme settings fail during decoding rather than later chunk
generation. The amplitude limit follows Vanilla DensityFunctions' reasonable
coefficient scale and keeps output/range metadata finite.

| Algorithm | Existing kernel | Conservative unscaled bounds |
| --- | --- | --- |
| smooth | `GradientNoise(frequency)` | `[-1,1]` |
| ridge | `RidgedNoise(frequency, octaves)` | `[0,1]` |
| cell | `WorleyNoise(frequency)` | `[0,sqrt(2)]` |
| fracture | `DomainWarpNoise(frequency, warp_frequency, warp_strength)` | `[-1,1]` |

`minValue()` / `maxValue()` scale these bounds and swap endpoints for negative
amplitude. No kernel math, salt, frequency or seed is changed by amplitude.

## World seed binding

26.1's `FunctionContext` supplies coordinates and Blender, **not a world seed**.
`RandomState` receives the seed when wiring its router, but its public
`DensityFunction.Visitor` has no raw-seed callback for custom leaves. NormalNoise
holders are initialized through Vanilla's named noise registry; deriving a
different seed from them would break the shared raw-base-seed contract.

The verified server path is:

```text
ChunkMap: level.getSeed()
  -> RandomState.create(settings, registryAccess.lookupOrThrow(Registries.NOISE), levelSeed)
  -> RandomState constructor(settings, noises, seed)
  -> NoiseRouter.mapAll(Vanilla NoiseWiringHelper)
```

`RandomStateMixin` wraps **only that constructor's `NoiseRouter.mapAll` call**
using NeoForge's already bundled MixinExtras 0.5.3 `@WrapOperation`.
`PhaseNoiseSeedVisitor` delegates both `apply` and `visitNoise` to the original
visitor; after Vanilla wiring it replaces only a WorldPhase leaf with an
immutable `withSeed(originalWorldSeed)` instance. The original operation is
called, so wrapper chaining and Vanilla noise/End/legacy/surface behavior are
preserved. Router leaves are bound before `RandomState` builds its climate
sampler, including holder-backed and nested expressions.

Shared datapack templates contain an **empty OptionalLong**. `compute` on an
unbound template throws a clear exception; there is no seed=0 fallback. Binding
never mutates a template or another world's instance. Encode/decode preserves
configuration only, so a decoded value must be bound again. Direct isolated
evaluation can explicitly call `withSeed(seed)`; actual worldgen gets binding
automatically from the constructor hook, not caller-provided JSON seeds.

This is the necessary implementation detail missing from Technical Design v0.1's
conceptual API. There is no public 26.1 raw-seed visitor/event to use instead;
the single hook is preferable to global server lookups, coordinate-derived
seeds or copying RandomState. No access transformer, reflection, overwrite or
mutable random sampler is used. See the
[MixinExtras WrapOperation API](https://github.com/LlamaLad7/MixinExtras/wiki/WrapOperation).
No new Mixin dependency/version is added. The required injection fails visibly
if a future Minecraft version removes the targeted wiring call; this integration
must be rechecked when upgrading the pinned game version.

## Fixture and runtime check

The test-only datapack is in `src/test/resources/datapacks/phase-noise-fixture`.
Its sole density entry, `worldphase:wph005_fixture`, contains the minimal JSON
above. `pack.mcmeta` uses the actual 26.1 server data format **101.1** from the
server JAR's `version.json`. It contains no dimensions or noise-settings presets
and is not included in the production mod JAR.

```sh
./gradlew runDensitySmokeServer --console=plain
```

This isolated dev run uses `run/density-smoke-server`; its prerequisite copy
task installs the fixture into `world/datapacks/phase-noise-fixture` before
startup. The resulting world remains Vanilla. After `Done`, enter the Vanilla
console command `datapack list enabled`, then `stop` for a clean shutdown.
It does not modify the existing `run/server` world.

Validated 2026-10-07 (Asia/Shanghai), Windows x64 / Temurin 25.0.3+9:

- New-world boot: fixture automatically loaded; `Done (1.588s)`.
- Final-adapter restart: `Done (0.354s)`; enabled pack list includes
  `file/phase-noise-fixture` alongside `vanilla` and `mod_data`.
- Both boots load Minecraft 26.1 / NeoForge 26.1.0.19-beta / MixinExtras 0.5.3;
  both save and exit with Gradle `BUILD SUCCESSFUL`, exit 0.
- No ERROR entries, density registry/codec failure or Mixin failure. Upstream
  command-ambiguity, Windows performance-counter and dev offline-mode warnings
  are unrelated to density registration.
- This machine used ignored local properties (loopback port 25567, seed
  42001729, view/simulation distance 2) and the previously documented ASCII
  Gradle cache/temp settings. None are committed.

## Tests and evidence boundary

```sh
./gradlew test --tests dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunctionTest --no-build-cache
./gradlew test --tests dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunctionTest \
  --tests dev.fangbm.worldphase.worldgen.noise.NoiseKernelsTest \
  --tests dev.fangbm.worldphase.phase.PhaseDomainTest testNoiseProfiler --no-build-cache
./gradlew build --no-build-cache
```

51 density tests cover registered dispatch, all algorithms/defaults, invalid and
non-finite configuration, fractional octaves, round-trip without serialized
seed, amplitude/direct-kernel bit equality, y independence, fillArray/mapAll,
real RandomState auto-binding under several seeds and Vanilla composition.
An isolated `NoiseBasedChunkGenerator.getBaseColumn` test adds a decoded Smooth
modifier to in-memory Vanilla Overworld final density and observes changed
stone/air decisions. It retains Vanilla generator, noise settings, default
blocks/fluids and surface-rule source; the runtime smoke world retains Vanilla
biome/surface generation. No Phase dimension or world preset is created.

Full build passes **122 tests**: 51 density + 34 unchanged noise golden/regression
+ 29 Phase domain + 8 profiler; zero failures/errors/skips. The startup fixture
proves actual worldgen-loader parsing/registration. The isolated noise-column
test proves additive terrain influence; it is not a four-Phase survival test
or visual terrain tuning. WPH-003 mathematical outputs and `PhaseDefinition`
are unchanged. Next is **#6 / WPH-006**, after review; this change stops at #5.

WPH-006 subsequently supplies the first [Smooth world preset](SMOOTH_WORLD.md)
as data using this adapter, without changing its codec, kernels or seed hook.
