# WPH-003 deterministic 2D noise

All kernels live in `dev.fangbm.worldphase.worldgen.noise` and implement
`PhaseNoise.sample(long seed, double x, double z)`. They use only the Java standard
library. Instances contain immutable configuration; sampling does not change
state, allocate point tables or depend on call order. A shared base seed can be
passed to every kernel directly.

## API and ranges

| Kernel / constructor | Algorithm | Conservative output range |
| --- | --- | --- |
| `GradientNoise(double frequency)` | Smooth field: hashed square lattice, eight unit gradients, quintic interpolation | `[-1, 1]` |
| `RidgedNoise(double frequency, int octaves)` | `1 - abs(GradientNoise)` accumulated with lacunarity 2, persistence 0.5; divide by total weight | `[0, 1]` |
| `WorleyNoise(double frequency)` | One feature per cell; Euclidean nearest-point distance (F1) | `[0, sqrt(2)]` in scaled cell units |
| `DomainWarpNoise(double baseFrequency, double warpFrequency, double strength)` | Two independent gradient fields displace x/z once, then sample the original-seed base gradient field | `[-1, 1]` |

Frequency is positive and finite; it multiplies each input coordinate. Ridged
allows 1–8 octaves, with the same seed at each doubled spatial frequency. Warp
strength is finite and nonnegative, in **input coordinate units**; zero strength
gives the base field. No phase-specific frequency or terrain preset is assigned
in this issue. `PhaseDefinition` and the Phase catalog are unchanged.

The Smooth range follows from a convex blend of unit-gradient dot products:
each corner offset has length at most sqrt(2), then the blend is scaled by a
fixed binary approximation of 1/sqrt(2). This conservative range does not imply
the field reaches its endpoints. Interpolation uses `6t^5 - 15t^4 + 10t^3`,
giving smooth transitions at lattice boundaries. Ridge peaks at base zeros;
positive octave weights preserve its range after normalization. Warping changes
where the bounded base field is evaluated, not its range.

## Exact Worley search

Feature offsets lie in `[0, 1)` on each axis. The point in the query's own cell
provides a distance bound of at most sqrt(2). A cell outside a Chebyshev radius
of 2 is at least 2 units away, so it cannot win. The implementation visits the
two neighboring rings and skips a cell before hashing if its axis-aligned box
distance cannot improve the current best. Thus the maximum envelope is 5×5,
with only potentially closer feature cells evaluated. A fixed 3×3 search is
insufficient for full-cell jitter; the regression fixture `seed=345228`,
`(x,z)=(0.0001,0.5)` captures a nearest point in the second ring.

Distances are computed from relative cell offsets to avoid subtracting large
absolute feature coordinates. There is no F2, alternative metric or stored
feature-point table.

## Seed and hash contract

`NoiseHash` uses the stateless SplitMix64 finalizer `mix`, with unsigned shifts
and intentional Java long overflow. Lattice/cell hashing is:

```text
mix(seed XOR channel XOR mix(cellX XOR X_SALT)
    XOR rotateLeft(mix(cellZ XOR Z_SALT), 32))
X_SALT = 0x9e3779b97f4a7c15
Z_SALT = 0xd1b54a32d192ed03
```

| Channel | Stable salt |
| --- | --- |
| Gradient direction | `0x4752414449454e54` (`GRADIENT`) |
| Worley feature x | `0x574f524c45595f58` (`WORLEY_X`) |
| Worley feature z | `0x574f524c45595f5a` (`WORLEY_Z`) |
| Warp x seed | `0x574152505f585f31` (`WARP_X_1`) |
| Warp z seed | `0x574152505f5a5f31` (`WARP_Z_1`) |

Gradient selection uses the low three hash bits; feature offsets use the high
53 bits multiplied by `2^-53`. Warp seeds are `mix(baseSeed XOR channelSalt)`;
the displaced base sample retains `baseSeed`. There are no `seed + phaseIndex`
offsets or mutable random streams. These salts and arithmetic are part of the
kernel contract; changing them changes terrain and requires deliberate golden
fixture review.

Java 25 uses strict floating-point evaluation. `StrictMath.floor` and
`StrictMath.sqrt` provide reproducible lattice/distance operations. Golden tests
compare double bits, with exact hexadecimal literals. Two ordinary, non-lattice
fixtures per kernel pin interpolation, negative-cell floor behavior and channel
hashing; the additional Worley fixture protects search correctness.

## Input limits

Non-finite coordinates/configuration are rejected with `IllegalArgumentException`.
Each scaled coordinate must be within **±2^50**, including every Ridged octave
and the warped base coordinates. This keeps lattice and neighboring cell integers
exactly representable and prevents silent long saturation or precision collapse.
Normal Minecraft coordinates (±30 million) are well within this domain at the
tested frequencies. Unsupported extreme inputs fail explicitly rather than
producing NaN/Infinity or silently wrapping. There is no promise of meaningful
spatial variation below double precision or at underflowing frequency scales.

## Sources and licensing

Gradient, Ridged, Worley and DomainWarp kernels are implementations written for
WorldPhase. The Smooth algorithm uses the standard gradient-dot/interpolation
construction and quintic fade described by
[Ken Perlin's Improved Noise reference](https://cs.nyu.edu/~perlin/noise/).
That reference is copyrighted by Ken Perlin (2002); its 3D implementation and
permutation table are **not copied or bundled**. This implementation uses a
2D hashed lattice and eight unit gradients instead.

Only the three-step integer avalanche finalizer is adapted from
[Sebastiano Vigna's SplitMix64 (2015)](https://prng.di.unimi.it/splitmix64.c).
The source dedicates the software to the public domain worldwide and grants
permission to use, copy, modify and distribute it for any purpose, with or
without fee. Its attribution and full permission/warranty notice are retained
in `NoiseHash.java`. Its mutable generator state and increment sequence are not
used. No external noise library or new dependency is added. WorldPhase's own
code retains the repository's current All Rights Reserved status.

## Design relationship and next issue

Technical Design v0.1 lists `OpenSimplexNoise.java` as an example. This issue uses
`GradientNoise` instead: the requested coherent gradient/simplex-style field is
implemented with a simpler square lattice. It is not an OpenSimplex implementation.
Ridged uses the required ridge transform and normalized octave accumulation;
the design's exponent `sharpness` is fixed to 1 for this first implementation,
so there is no extra shaping parameter. It does not claim a weighted
ridged-multifractal feedback model. The minimal
parameters are frequency, octave count and warp strength where needed; extra
worldgen configuration belongs to later issues.

The kernels are not wired into Minecraft DensityFunction, Phase dimensions,
worldgen JSON, transfer, commands or tutorial behavior. Next is **#4 / WPH-004:
Add noise profiling helper**, after review; density-function integration remains
**#5 / WPH-005**. This change stops at #3.
