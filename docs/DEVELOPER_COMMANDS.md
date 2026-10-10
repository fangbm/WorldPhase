# OP developer commands (WPH-011)

These commands execute on the server. They expose existing Phase, terrain-sampling
and transfer services; no new worldgen preset, client protocol or gameplay item is added.
Dedicated-server and connected native-client acceptance pass, including ordinary
bounded transfers, explicit surface mode, rejection and save/stop/reload.

## Command contract

```text
/wp phase list
/wp phase current
/wp phase goto <phase>
/wp phase goto <phase> --surface
/wp phase cycle
/wp sample <phase>
/wp compare <phaseA> <phaseB>
/wp compare all
/wp safepos <phase>
/wp seedcheck
/wp tutorial score
/wp tutorial find [candidateCount]
```

Phase tokens are `fracture`, `smooth`, `ridge`, `cell`, derived from the domain's
Level keys. Literal Brigadier nodes provide completion and reject unknown tokens;
`compare all` does not overlap a generic argument. Cycle order is fixed:
FRACTURE -> SMOOTH -> RIDGE -> CELL -> FRACTURE.

The whole tree requires OP level 2 / GAMEMASTERS permission. Authorized console or
command-block sources may use list, current and seedcheck. All other commands call
`getPlayerOrException()` and return Vanilla's typed player-required error for console,
except the WPH-013 `tutorial find` diagnostic, which also accepts an authorized
console source. `tutorial score` remains player-only.
Current describes the command source Level; Vanilla dimensions are NON_PHASE.
List reports each dimension key and whether its ServerLevel is loaded.

Goto and cycle delegate to PhaseTransferService, with BOUNDED_LANDING by default.
They retain exact x/z and yaw/pitch, allow only y offsets
`0,+1,-1,+2,-2,+3,-3,+4,-4`, and report the specific TransferResult on rejection.
Cycle has no surface fallback. Same-Phase gameplay calls remain a no-op.

Sample/compare use TerrainSampleService at `floor(player.x/z)`, including negative
coordinates. Output explicitly labels block x/z, baseHeight, and configured raw
kernel signal (not final density). Compare delta is second minus first. Compare all
gathers four results before printing. These samples do not request generated chunks.
The client font is proportional, so padding provides compact columns rather than
guaranteed pixel alignment.

Safepos invokes the service's shared landing inspection with BOUNDED_LANDING.
It reports original position, exact-y safety, first permitted safe y and delta, or
the rejection. It never teleports or reserves the result. This is real block/entity
safety and can synchronously load/generate FULL chunks; it is not a cheap height
estimate. Later world changes may invalidate a diagnostic result. Missing/unready
Levels report exact safety as unknown.

Seedcheck reads every actual loaded ServerLevel.getSeed(). PASS requires all four
Levels present and identical raw seeds. Missing keys and mismatching Phase seeds
are printed; no configuration constant is substituted for a Level seed.

## Explicit developer surface policy

Only the explicit `goto <phase> --surface` literal selects DEV_SURFACE. It keeps
the same OP permission, server thread, source snapshot, chunk readiness, native
teleport and commit-boundary failure handling as ordinary transfers.

After the original x/z AABB plus Vanilla collision halo is prepared once as FULL
chunks, candidates start at the real MOTION_BLOCKING_NO_LEAVES first-free height
plus four, clamped to the inclusive build-height upper edge. At most **64 descending
rows** are inspected, stopping at minimum build height. Each row proposes the top
of the center-column block's actual collision shape (or the row height for an
empty shape). Slab/stair tops can therefore have fractional y; only this policy
can replace the original fractional y. Original double x/z are copied directly.

Every candidate passes the existing full AABB border/height, block/entity collision,
liquid, central-footprint support and listed Vanilla hazard checks. Shape top alone
never proves safety. The policy permits only these prepared-world candidates, so
custom resolvers cannot bypass the contract. It may reject water, thin edges,
unsupported terrain or a column without safe support in its finite window. Player-
specific/modded shapes can cause conservative false negatives; arbitrary modded
damage callbacks and future environmental changes are not universally covered.

Explicit DEV_SURFACE can reposition a player already in the target Phase. Actual
transfer still rejects Vanilla source dimensions as INVALID_SOURCE. For initial
development entry use Vanilla, then request the safe surface policy:

```text
/execute in worldphase:smooth run tp @s -511.875 220 768.25 123.25 35.5
/wp phase goto smooth --surface
```

The Vanilla command is an OP development bootstrap and does not apply landing safety.
In the isolated creative/flying test setup, remain flying until the wp surface
command succeeds. Neither ordinary goto nor cycle silently expands its search.

## WPH-013 tutorial diagnostics

`tutorial score` evaluates the player's current block-column x/z; `tutorial find`
searches around the actual Overworld shared spawn x/z, with radius512 and default
64 candidates (explicit count8..256). Both require the existing OP permission.
They report Fracture/Smooth heights, local slopes, height/slope contributions,
spawn-distance and conservative hazard penalties, total and base usefulness.
There is no teleport, location selection, spawn change or persistence.

Score and find now run incrementally on server ticks, at most two new base height
columns per tick and one active job per server. V4 coarse screening retains at
most24 spatially separated centers and refines at most8 directional corridors.
Default64 uses at most768 columns/384 ticks (~19.2s); maximum256 uses1152/576
(~28.8s); score uses66/33 (~1.65s at20TPS). These are workload bounds, not CPU
timing guarantees. Output distinguishes legacy score components, directional
rank/risk/height-compatible hints and explicit shift UNVERIFIED. Neither requests FULL chunks or
certifies a safe landing/path. See [Tutorial search](TUTORIAL_SEARCH.md) for
actual26.1 spawn APIs, formulas, failure handling, workload and quality limitations,
and [ten-seed observations](TUTORIAL_SEARCH_VALIDATION.md) for empirical results.
The native-client record below concerns WPH-011; WPH-013 adds headless server
command/worldgen acceptance, not a new connected-client screenshot claim.

## Minecraft 26.1 APIs

Pinned Minecraft 26.1 / NeoForge 26.1.0.19-beta generated sources were inspected.
The actual registration event is **RegisterCommandsEvent**, on NeoForge.EVENT_BUS,
not `CommandRegistrationEvent`. Registration is repeated when Commands is rebuilt
for datapack reload. Permission checks use
`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`, the current typed permission
API; no new permission or custom network ArgumentType registry is introduced.
See the official [26.1 primer](https://docs.neoforged.net/primer/docs/26.1/) and
[typed-permission migration](https://docs.neoforged.net/primer/docs/1.21.11/).

Native transfer remains ServerPlayer.teleport(TeleportTransition), with its nullable
result and existing portal ticket. FULL acquisition remains ServerChunkCache.getChunk.
Surface height uses ServerLevel.getHeight and Vanilla collision shapes. The design's
gameplay +/-4 semantics remain unchanged; DEV_SURFACE is a separate development policy.

## Automated and dedicated-server validation

Validated 2026-10-08 and resumed 2026-10-09, Windows x64, Temurin 25.0.3+9:

```sh
./gradlew test --tests '*WorldPhaseCommandTest' --tests '*transfer*' --console=plain --no-build-cache
./gradlew build --console=plain --no-build-cache
./gradlew runPhaseVerificationCommandsServer --console=plain --no-build-cache
# Repeat against the same saved world for reload verification.
./gradlew runPhaseVerificationCommandsServer runPhaseVerificationTransferServer --console=plain --no-build-cache
```

Focused command/landing/transfer tests: **118 passed** (38 command, 35 landing,
45 transfer). Complete build: **280 passed**, zero failures/errors/skips, including
34 noise golden, 51 density/codec, 29 Phase domain, 30 sampling, 10 world preset and
8 profiler tests. No kernels, golden fixtures, PhaseDefinition or presets changed.

The command probe uses real four-Phase ServerLevels and a real ServerPlayer with
Vanilla teleport handling; only outbound transport is discarded. Registered-tree
ambiguity/completion, permission denial and console behavior pass. Smooth -> Ridge
at (-16.125,220.5,15.9375), Ridge -> Cell at y221.5, a complete cycle, blocked-cycle
rejection and DEV_SURFACE to a full-block floor at y220 pass. Actual arrival safety,
UUID/entity uniqueness, every inventory slot and exact x/z/yaw/pitch are checked.
Safepos reports +1 without moving the player. Seedcheck reads all four 123456789
seeds. Save/stop/reload retains the GOLD_BLOCK fixture and reruns commands successfully.
The original WPH-010 dedicated transfer regression also passes and stops cleanly.
This simulated transport does not prove connected-client transfer acceptance.

Probe outputs are ignored runtime files under run/command-verification-server
(command-report.txt, command-status.txt). The production JAR excludes verification,
test and profiling classes. Native runs use the separate command-native-server
and command-client directories; these game directories are never committed.

## Native-client acceptance record

The connected NeoForge client joined the real loopback dedicated server as OP
WPH011Review, seed123456789. Chat successfully executed list, current, seedcheck,
compare all and sample ridge. Current reported NON_PHASE in Overworld; seedcheck
printed four actual matching seeds and PASS. At block (-512,768), output was:

| Phase | baseHeight | configured rawNoise |
| --- | ---: | ---: |
| fracture | 78 | +0.042475 |
| smooth | 73 | +0.008676 |
| ridge | 124 | +0.969948 |
| cell | 47 | +0.286435 |

Sample ridge agreed. Goto smooth --surface from Overworld correctly returned
INVALID_SOURCE without changing the source. Vanilla /execute then entered Smooth.
The session was interrupted on October 8 and completed on October 9. The isolated
dev server logs each wp command's before/after position and rotation, UUID, inventory
object/every slot and unique player entity checks in native-command-audit.txt.

The resumed connected-client checks passed:

| Scenario | Actual result |
| --- | --- |
| Smooth explicit surface at x=-511.875,z=768.25 | SUCCESS, actual commit source y206.49945590426327 -> y75; forest grass surface visible |
| Ordinary goto ridge at Smooth y75 | NO_SAFE_POSITION; source Level/position/rotation/inventory unchanged |
| Cycle at the same natural position | Same rejection; no surface fallback |
| Safepos cell at the same position | exactSafe=false, NO_SAFE_POSITION; no movement |
| Smooth -> Ridge at (-16.125,220.5,15.9375) | BOUNDED_LANDING SUCCESS at original y |
| Safepos cell from Ridge | exactSafe=false, candidate y221.5, deltaY=+1; player stays y220.5 |
| Ridge -> Cell | BOUNDED_LANDING SUCCESS at y221.5, matching diagnostic |
| Four consecutive cycles | Cell -> Fracture -> Smooth -> Ridge -> Cell, y221.5 ->220.5 ->220.5 ->220.5 ->221.5 |
| Save/stop/restart/reconnect | Restored Cell, exact position, rotation, UUID and inventory before test initialization |
| Commands after restart | Seedcheck PASS, compare-all same values above, goto ridge SUCCESS, current=ridge |

The platform sequence retained x=-16.125,z=15.9375, yaw123.25,pitch-37.5 and UUID
713feee0-75bf-3a00-8414-a1e79a9cb011. The explicit surface request preserved its
own actual source rotation (-128.9001,26.09999). After reconnect the dev fixture
uses Vanilla to position the player again; the final platform entry deliberately
used pitch35.5 to show the supporting slab in the client, which goto preserved.
No mathematical/kernel/preset or production transfer behavior changed on resume.

At 09:26:47, before login fixture positioning or inventory initialization, the
new dev-only RESTORED log reported Cell (-16.125,221.5,15.9375), yaw123.25,
pitch-37.5, the same UUID, seven diamonds in slot0 and23 iron ingots in slot10;
all other slots were empty. This observes actual player save data before the
test entry can hide a persistence problem. Save-all flush and stop both saved
every Level and exited cleanly; the restarted native server reached Done in0.857s.
Native commands were entered through the connected client's chat, not substituted
by simulated packet transport. A dev-only post-command check after restart also
evaluates LandingSafety in the real destination and logged landing=SAFE for goto.

Curated F2 screenshots from this native session (only these evidence images, not
game directories or runtime logs, are committed):

- [Surface success, bounded/cycle rejection and safepos](verification/wph011/surface-and-rejection.png)
- [Bounded goto, +1 diagnostic and full cycle](verification/wph011/bounded-cycle.png)
- [After reconnect: seedcheck, identical samples, goto/current and actual support](verification/wph011/reload-commands.png)

The build was rerun after adding the dev-only restored-state log and arrival
safety assertion: `./gradlew build compilePhaseVerificationJava --console=plain
--no-build-cache`, exit0 with280 passing tests. No production changes were required.

Some cold high-altitude fixture transitions displayed Vanilla's Loading terrain
screen for roughly30 seconds; later warm transitions completed promptly. Initial
chunk generation also produced a brief Can't keep up warning. Startup has existing
Vanilla command ambiguities outside the wp subtree and a Windows PerfOS warning;
no wp tree ambiguity, density/registry/Mixin error or failed native audit was seen.
These checks establish functional acceptance, not a transfer-latency benchmark.

To reproduce, prepare accepted eula.txt and server.properties in
run/command-native-server: level-seed=123456789, loopback IP, port25572,
online-mode=false, gamemode=creative, spawn-protection=0, view-distance=4,
simulation-distance=2, max-tick-time=-1. Run these in separate terminals:

```sh
./gradlew runPhaseVerificationCommandsNativeServer --console=plain
./gradlew runCommandClient --console=plain
```

The dev-only server fixture OPs WPH011Review, gives test inventory and enables
creative flight; the native client contains only production mod code. Repeat the
commands above, bootstrap with Vanilla /execute, then exercise successful surface,
default goto ridge, cycle, safepos cell, rejection with unchanged state and repeated
shifts. Real supported slab platforms also exist around (-16.125,220.5,15.9375);
Cell's platform is one block higher. Use Vanilla OP positioning to visit this
fixture for reproducible bounded successes; natural terrain may legitimately reject
a +/-4 transfer. Verify inventory/UUID/rotation, save/stop, restart the same world
and reconnect to repeat commands. The login fixture intentionally repositions the
player after logging the restored state; this is isolated dev setup, not gameplay
spawn/progression behavior. Native acceptance is complete and the delivery PR can
be reviewed. No #12 work is included.
