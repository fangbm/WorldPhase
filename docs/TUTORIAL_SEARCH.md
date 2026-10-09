# Seed-aware tutorial search prototype (WPH-013)

OP development diagnostics for FRACTURE/SMOOTH. They search and explain base
terrain candidates; they select/persist no tutorial location, change no spawn,
teleport nobody and place no Anchor. Keep the [original frozen 2/10 Top1 record](TUTORIAL_SEARCH_VALIDATION.md)
separate from [failure attribution](TUTORIAL_SEARCH_ATTRIBUTION.md), the [frozen v4 plan](TUTORIAL_SEARCH_REFINEMENT_PLAN.md)
and its [actual results](TUTORIAL_SEARCH_REFINEMENT_VALIDATION.md).

## API, seed and determinism

`TutorialCandidateFinder.beginRefined(centerX,centerZ,config)` returns an ephemeral
`RefinedTutorialSearch`; `scoreRefined(x,z,centerX,centerZ,config)` returns a
single-location corridor job. `advance(1..4)` limits new height columns per call;
`result()` requires completion. All operations use the server thread and unchanged
`TerrainSampleService` OCEAN_FLOOR_WG base-height API. Missing Levels, mismatched
raw seeds, mid-search seed changes and invalid configuration fail explicitly.
Normal ranking uses no FULL chunk request or live heightmap.

Legacy `begin` / `score`, original nine-point `TutorialScore`, its thresholds and
weights remain for baseline reproduction. `Search.diagnostics()` exposes cached
stencils after completion without extra sampling. `TutorialCorridor` stores
separate directional evidence; legacy usefulHeuristic is not gameplay safety.

Same raw seed, integer center, config and presets produce the same candidates,
ranking and tie-breaks across batch sizes. Timing never affects ranking. Results
are immutable coordinates/numeric evidence/risk/status, without player/time/Level
references. Sampling caches and service references are ephemeral job state.

Candidate sequence remains version3: stateless uniform-disk rejection, at most16*N
attempts, stable64-point prefix of256, signed floorMod, duplicate center rejection:

```text
packedCenter = ((long)centerX << 32) XOR (centerZ & 0xffffffffL)
base = mix(rawSeed XOR 0x575048313343414eL XOR packedCenter)
channel_i = base + 0x9e3779b97f4a7c15L * i
dx = floorMod(mix(channel_i), 2*radius+1) - radius
dz = floorMod(mix(channel_i XOR 0xd1b54a32d192ed03L), 2*radius+1) - radius
```

Long overflow is intentional. Reject outside-radius/repeated points; fail if the
finite attempt cap is exhausted. Coordinates/stencil stay inside +/-30,000,000.
No mutable Random/time/Phase-index seed offsets. `mix` is
[Vigna's public-domain SplitMix64 finalizer](https://prng.di.unimi.it/splitmix64.c),
xor-shifts30/27/31, multipliers0xbf58476d1ce4e5b9/0x94d049bb133111eb.
Search/score code is original WorldPhase; mod license remains All Rights Reserved.
Existing kernels/provenance remain unchanged.

## Two-stage v4 ranking

Defaults radius512/count64; API radius64..1024/count8..256.

1. Sample both Phase centers for N uniform points. Exclude invalid height
   sentinels; retain at most24 spaced32 apart, preferring moderate contrast,
   possible height compatibility, distance and lower uncertain-coast penalty.
   Sample four cardinal neighbors at16 blocks. Retain at most8 spaced64 apart,
   prioritizing directional difficulty/ease and a <=4 height-gap hint.
2. At those8 analyze x,z and both diagonal corridors, offsets
   -16,-12,-8,-4,0,4,8,12,16:33 unique x/z per Phase. Cache center/endpoints and
   overlaps. Axis ties use fixed axis order; candidate ties use x then z.

F/S are maximum consecutive four-block rise/run along one axis; diagonal distance
is4*sqrt(2). H is maximum same-x/z height contrast along it. D is distance from
explicit center, R radius, C number of sampled height gaps<=4. Fine score:

```text
height = 6 * min(H/32,1)
slope = 64*clamp(F-S,-2,2) -128*clamp(S-.25,0,2)
        -16*clamp((.375-F)/.375,0,1)
distancePenalty = 12*D/R
riskPenalty = 8 when REQUIRES_VALIDATION, otherwise0
shiftHint = 4*min(C,3)/3
rankScore = height + slope -distancePenalty -riskPenalty +shiftHint
```

Exact coarse formulas are in the frozen plan. Interesting requires F>=.375,
S<=.25,F-S>=.25,H>=4,D<=R and valid data. Rank interesting with height-gap hint
first, interesting without hint next, other diagnostics next, invalid last;
then descending score, x/z. Large contrast alone is insufficient. Height
compatibility is a hint, never a permission to transfer. This is local analysis,
not A* or pathfinding.

Risk: INVALID (empty/vertical-edge data), REQUIRES_VALIDATION (sea/coast or extreme
slope), LOW_RISK (no such base hint). Coast is not discarded as definitely unsafe;
LOW_RISK is not safety certification. Actual water/lava, support, body/entity
collision and known hazards remain mandatory in separate real validation.
Normal results are INTERESTING_BUT_SHIFT_UNVERIFIED or INSUFFICIENT_DIRECTIONAL_CONTRAST.

## Scheduling, bounds and commands

Maximum unique height calls:2*N+8*min(N,24)+56*min(N,8): **768 default /1152 maximum**;
single score66. One score/find per server. Both advance at most2 new heights per
`ServerTickEvent.Post` on the server thread; no async Level access. Default384
ticks/about19.2s, maximum576/about28.8s, single score33/about1.65s at20TPS.
Work caps are not hard millisecond guarantees; cold generation can exceed a frame
budget. No automatic startup search.

```text
/wp tutorial score
/wp tutorial find
/wp tutorial find 256
```

Root permission remains typed GAMEMASTER/OP2. Score is player-only and captures
floor(player.x/z) when submitted. Find also accepts authorized console; its center
is actual Overworld shared spawn. Both use that spawn for distance via
`server.getLevel(Level.OVERWORLD).getRespawnData().pos()`. Missing Overworld or
shared respawn outside Overworld fails explicitly. No Fracture spawn redirection.
Brigadier enforces8..256. Second jobs are refused; stop clears pending state;
failure reports reason and releases the slot.

Output: seed/center/radius, coarse/fine counts, columns/ticks, active elapsed
computeMs, current-thread cpuMs, wallMs, maxBatchMs and Top5. ThreadMXBean CPU
timing prints-1 when unsupported/disabled. Legacy components are labelled
separately from corridor axis/F/S step/contrast/rankScore/risk/height-compatible
count. The formula above explains new contributions. All ordinary output says
shift UNVERIFIED. Existing phase goto/cycle/sampling/TransferPolicy are unchanged.

Registration reuses actual26.1 `RegisterCommandsEvent`, `Commands.hasPermission`,
`ServerTickEvent.Post`, `ServerStoppedEvent`; LevelData.RespawnData has pos()/dimension().
No registry, packet, permission system, Mixin or compatibility hack. See official
[26.1 primer](https://docs.neoforged.net/primer/docs/26.1/). Empty-server tick
pausing pauses jobs; use pause-when-empty-seconds=0 for development.

## Separate actual validation

Only the explicit dev probe audits Top3 with FULL chunks: max16 requests/Phase/
candidate,96/seed in37x37 collision-halo footprints, cached across candidates.
It analyzes all33 adjacent actual ground columns on the selected axis, skipping
logs/leaves, and checks supported standing in both worlds with unchanged
LandingSafety. At9 fixed points real ServerPlayer bodies/Levels and
SafeLandingResolver.gameplay must resolve forward and reverse under normal
BOUNDED_LANDING, preserve exact double x/z and satisfy policy/safety. No
DEV_SURFACE or teleport. Strong contrast without a proven shift is NO_FEASIBLE_SHIFT.

New acceptance requires actual F adjacent step>=2,S<=1,improvement>=1,contrast>=4,
distance<=512,all33 standing positions safe in both worlds and one bidirectional
bounded shift. Old2/10 is not recomputed with this new criterion. Partial support
shapes at ground+1 can be conservatively rejected. These are local samples/shift
positions, not a continuous playable route, jump-trajectory proof, future safety
or coverage of arbitrary modded damage. Entity/environment changes can invalidate
an otherwise deterministic base prediction.

## Reproduce

```sh
./gradlew test --tests '*tutorial*' --tests '*WorldPhaseCommandTest' --tests '*TerrainSampleServiceTest'
./gradlew coreRegressionAll build --console=plain
./gradlew tutorialVerification -PtutorialProtocol=attribution --console=plain
./gradlew tutorialVerification -PtutorialProtocol=refined --console=plain
```

After accepting Minecraft EULA, retain eula=true in
run/tutorial-verification-server/eula.txt or pass -PtutorialAcceptEula=true.
Use pinned Java25; isolated loopback25575/fresh worlds preserve earlier worlds.
Do not run parallel copies on that port. Historical default protocol is legacy.
Attribution captures entire old pools/up to4 FULL audits/seed without changing
old scoring. Refined runs original10 plus independent5. Ignored reports use
attribution-report.json /refined-report.json, build/tutorial-attribution or
tutorial-refined/summary.json; old tutorial-report.json /build/tutorial-verification
stay separate. Probes/plot dependencies never enter the gameplay JAR.

Correctness failures return nonzero with specific invariant/failure details.
Empirical QUALITY_SHORTFALL is separate from correctness PASS; Draft review is
not silently passed. Curated tools render actual FULL numeric observations to
documentation figures; plots are not native-client screenshots.
