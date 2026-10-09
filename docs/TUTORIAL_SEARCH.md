# Seed-aware tutorial search prototype (WPH-013)

This is an OP development diagnostic for **Fracture versus Smooth**. It finds and
scores candidates; it neither selects/persists a tutorial location nor changes
spawn, teleports a player, places an Anchor or proves a traversable path. See the
[predeclared acceptance plan](TUTORIAL_SEARCH_VALIDATION_PLAN.md) and
[actual ten-seed results](TUTORIAL_SEARCH_VALIDATION.md).

## API and seed semantics

`dev.fangbm.worldphase.tutorial` contains `TutorialCandidateFinder`, immutable
`TutorialCandidate` / `TutorialScore`, and the small `TutorialSearchConfig`.
The Finder owns a server-scoped `TerrainSampleService`; all terrain columns use
that service's unchanged `OCEAN_FLOOR_WG` base-height semantics. It never copies
generator, density or noise mathematics. A candidate stores only integer x/z,
the two base heights and numeric score components, without a Level/player/time.

`begin(centerX, centerZ, config)` reads the actual loaded Phase Levels' raw seed,
requires all four equal and Fracture/Smooth height bounds and sea levels equal,
and returns an ephemeral incremental `Search`. `advance(1..4)` budgets **height
columns**, not whole candidates, on the server thread. `result()` requires
completion. The convenience `score(...)` evaluates one point synchronously
(18 columns maximum). Missing Levels, mismatched seeds, off-thread use, invalid
coordinates/configuration and incomplete results fail explicitly.

Fixed pair: FRACTURE/SMOOTH. No arbitrary pair framework or seed override is
introduced. Given the same raw seed, integer center, config and presets, the
point sequence, scores and ranked candidates are identical. Timing is diagnostic
metadata in the search Result and never participates in ranking.

### Candidate sequence, version 3

The sequence uses stateless hash-indexed rejection sampling in a disk:

```text
packedCenter = ((long)centerX << 32) XOR (centerZ & 0xffffffffL)
base = mix(rawSeed XOR 0x575048313343414eL XOR packedCenter)
channel_i = base + 0x9e3779b97f4a7c15L * i
dx = floorMod(mix(channel_i), 2*radius+1) - radius
dz = floorMod(mix(channel_i XOR 0xd1b54a32d192ed03L), 2*radius+1) - radius
```

Reject points outside the disk and repeated centers. Java long overflow is
intentional modular arithmetic. Try at most `16*candidateCount` hash attempts;
fail explicitly if insufficient unique points. This makes the first64 points
a stable prefix of256 for identical seed/center/radius. There is no mutable
Random, time seed, Phase-index seed offset or dependency on player movement.
Coordinates plus the local stencil must remain inside +/-30,000,000 blocks.

`mix` is the three-stage SplitMix64 finalizer with xor-shifts30/27/31 and
multipliers `0xbf58476d1ce4e5b9L` / `0x94d049bb133111ebL`. The finalizer is from
[Sebastiano Vigna's public-domain SplitMix64 source](https://prng.di.unimi.it/splitmix64.c).
Only this integer mixer is reused; candidate generation/scoring is original
WorldPhase prototype code; existing mod metadata retains its `All Rights Reserved`
declaration. Existing kernels and
their provenance remain unchanged.

## Signals, units and ranking

Sample the center plus eight neighbors at x/z offsets +/-16: four cardinal
directions and four diagonals. This uses18 base columns per candidate before
per-search coordinate caching. For each Phase, local slope is maximum absolute
center-to-neighbor height difference divided by the neighbor's actual horizontal
distance (16 or sqrt(512) blocks), i.e. dimensionless rise/run. It is a coarse
obstacle signal, not a path simulation or maximum adjacent-block step.

Let F/S be those slopes, H the absolute center height difference in blocks, D
the distance to the specified center and R the search radius:

```text
heightContribution = 6 * min(H/32, 1)
slopeContribution  = 64 * clamp(F-S, -2, 2)
                   - 128 * clamp(S-0.25, 0, 2)
                   - 16 * clamp((0.375-F)/0.375, 0, 1)
distancePenalty    = 12 * D/R
totalScore         = heightContribution + slopeContribution
                   - distancePenalty - hazardPenalty
```

Slope improvement dominates the capped height contribution. Smooth steepness
and an unchallenging Fracture are penalized, so a large height difference between
two flat plains does not imply a good lesson. Distance is a linear spawn-near
preference. Weights are initial explainable heuristics, not gameplay balance.

Hazard penalties are independent and conservative:

| Base-column hint | Penalty |
| --- | ---: |
| Any of nine heights <= seaLevel+1 | 60 per Phase |
| Any empty/invalid/vertical-edge height outside minY..maxY-2 | 120 per Phase |
| Either local slope >2 | 30 |

These hints do not inspect real water, lava, caves, entities, tree collision,
dangerous blocks or landing support. They cannot certify a safe Phase Shift.
Finite/overflow validation prevents extreme integer heights from wrapping and
rejects nonfinite scores. All individual contributions are exposed.

The fixed **base usefulness predicate** is H>=4, F>=0.375, S<=0.25,
F-S>=0.25, hazardPenalty=0 and D<=R. Rank predicate-qualified candidates first,
then each group by descending totalScore, then ascending x and z. Greedily retain
centers at least64 blocks from earlier retained candidates. Unqualified results
remain visible with `usefulHeuristic=false`, rather than being presented as
successful tutorial choices. The acceptance plan additionally checks actual FULL
terrain and supported safe standing at both centers; it does not rescue a failed
Top1 by looking for a post-hoc lower-ranked success.

## Bounds and server scheduling

Defaults: radius512,64 evaluated centers; API radius64..1024, count8..256.
The commands use radius512 and print Top5 after spatial de-duplication.
Maximum256 candidates require at most4608 height-service calls and4096 hash
attempts. Normal scoring requests no FULL chunks and never scans farther away.
Caching and partial stencils exist only for that search.

One `/wp tutorial find` may be active per server. NeoForge
`ServerTickEvent.Post` advances **at most two height columns per tick**; no
background thread accesses Levels. With20 ticks/s,64 candidates need576 ticks
(about28.8 seconds) and256 need2304 ticks (about115.2 seconds). Sampling cost is
spread across those ticks, not hidden by an unbounded async queue. Individual
generator calls can still exceed a frame budget on slow CPUs/first-use JIT; this
is a column budget, not a hard millisecond guarantee. `score` is a bounded
synchronous18-column request. Measured compute/max-batch times are in the result
record and command output; they are never portable timing assertions.

A second find is refused while the first runs. Server stop removes pending
state. Search failure reports a reason and releases the slot. No search runs at
normal startup. An empty dedicated server that pauses ticks will also pause its
console search; keep it ticking (e.g. `pause-when-empty-seconds=0`) during dev use.

## Commands and current 26.1 APIs

```text
/wp tutorial score
/wp tutorial find
/wp tutorial find 256
```

The existing `wp` root requires typed GAMEMASTER/OP level2 permission using
`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`. `score` is player-only
and floors the player's current double x/z to the block-column coordinates used
by TerrainSampleService. `find` accepts OP players and authorized console sources;
Brigadier's bounded IntegerArgumentType rejects counts outside8..256.

Both use the actual **Overworld shared respawn x/z** as the development center:
`server.getLevel(Level.OVERWORLD).getRespawnData().pos()`. Minecraft26.1 exposes
`LevelData.RespawnData` with `pos()` / `dimension()`; a missing Overworld or a
shared respawn currently pointing into another dimension is explicitly refused.
This is not an implementation of the designed future Fracture opening flow.

Registration reuses the existing current NeoForge `RegisterCommandsEvent` tree.
The new listeners are `ServerTickEvent.Post` and `ServerStoppedEvent` on
NeoForge.EVENT_BUS. There is no new argument registry, permission system, packet,
Mixin, teleport policy or API compatibility hack. The design's conceptual
spawn-area input maps to actual26.1 RespawnData; see the official
[26.1 primer](https://docs.neoforged.net/primer/docs/26.1/).

For manual in-game inspection, use the printed x/z and the **existing explicit
OP developer surface mode**, after a Vanilla creative/flying bootstrap into a
Phase if needed. For example, on seed8675309:

```text
/execute in worldphase:fracture run tp @s -174.5 220 -106.5
/wp phase goto fracture --surface
/wp phase goto smooth --surface
```

The first Vanilla command has no landing safety; remain flying until the existing
surface command succeeds. These are deliberate review actions, not automatic
tutorial transfer or proof that bounded gameplay shift succeeds at that center.

Output includes center, radius, raw seed, workload/compute/wall time and Top5
two-line rows: x/z, both heights/slopes, signed slope contrast, height contrast,
height/slope contributions, distance/hazard penalties, distance, total and the
explicit base-usefulness predicate. No command teleports or edits the world.
Existing `phase goto`, `cycle`, transfer policies and sampling semantics remain
unchanged. Results are descriptive rather than promises of safe travel.

## Reproduce dedicated-server acceptance

```sh
./gradlew test --tests '*tutorial*' --tests '*WorldPhaseCommandTest' --tests '*TerrainSampleServiceTest'
./gradlew coreRegression build
# After accepting the Minecraft EULA, set eula=true in
# run/tutorial-verification-server/eula.txt, or pass -PtutorialAcceptEula=true.
./gradlew tutorialVerification --console=plain
```

The last task runs ten sequential, isolated, fresh-world dedicated servers on
loopback25575, with the predeclared seeds. All versions, Top5 components, repeats,
generator cross-checks,64/256 workloads, command output and actual observations
are written to ignored `run/tutorial-verification-server/seed-*/tutorial-report.json`.
The aggregate is ignored `build/tutorial-verification/summary.json`. Existing
worlds are preserved. No probe or plotting tool is packaged in the gameplay JAR.

Automated correctness failures leave FAIL/RUNNING reports and return a nonzero
Gradle exit. Empirical quality is separately reported as PASS or
**QUALITY_SHORTFALL**, with `majorityMet=false` when fewer than7/10 Top1s meet
the frozen standard; it is not silently equated to correctness PASS. This is
deliberate because the issue permits reporting a negative prototype experiment.

The probe first asserts base searches/commands leave Phase loaded-chunk counts
unchanged and independently cross-checks Top5's9 columns/Phase against each real
generator. Afterwards only, expensive dev observations explicitly load the
33x33 footprint (at most9 FULL chunks per Phase per observed point), record real
ground heights/blocks, skip logs/leaves above ground, and reuse LandingSafety
at the two actual center standing positions. Top1 is observed in all ten seeds;
seeds0/42 also observe a low-ranked example. The isolated bulk compute measurement
runs4-column batches back-to-back and may warn about lag; production commands
are separately tested through real2-column ticks, including count256 on the
last seed. Such bulk warnings are not ordinary command latency measurements.

Optional data visualization (not native-client screenshots): install matplotlib
and numpy in a dev Python environment, then run
`python tools/render_tutorial_observations.py`. Images stay under ignored
`build/tutorial-verification/observations/`. They show actual FULL-ground surfaces
and cross-sections, without edits or smoothing. They do not show rendered art,
lighting or prove a continuous player route.

## Boundaries and future #14 interface

Coarse columns miss narrow cliffs, overhangs, final carvers/features and small
hazards. Height-based coast avoidance can reject valid shore lessons. A dry,
gentle surface in Smooth does not prove the original player's y is transferable
within +/-4. The FULL validation is intentionally separated from cheap ranking.
No paths, endpoints, current-player safety or arbitrary modded hazards are proven.

#14 could persist a primitive selected candidate together with a schema version,
raw seed, Phase keys, center/config, generation/scoring version and preset/data
fingerprint. It should explicitly distinguish searched/scored/validated/selected
states and invalidate stale preset results. The current immutable Candidate/Score
can be a DTO boundary; no SavedData codec, automatic selection or persistence is
implemented here. Candidate-quality work remains subject to review of the
negative and successful evidence in this issue.
