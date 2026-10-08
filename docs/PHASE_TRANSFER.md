# WPH-009 PhaseTransferService

Production types live in `dev.fangbm.worldphase.transfer`:

```java
var transfers = new PhaseTransferService(server);
TransferResult result = transfers.transfer(player, PhaseId.RIDGE, TransferPolicy.EXACT_POSITION);
```

The service is scoped to one MinecraftServer lifetime. Calls must be made on its
main thread; otherwise IllegalStateException is thrown before reading the player.
The service reads the source Level, full double position, rotation and body AABB
from ServerPlayer. There are no client-supplied coordinates, coordinate rounding,
dimension-scale multiplication, new player objects or inventory copies.

Only a live, nonremoved player in a currently loaded MVP Phase of this server is
accepted. Sleeping, riding and carrying passengers are rejected as INVALID_SOURCE:
this slice does not define bed/camera/vehicle/passenger-transfer policy. Vanilla and
unknown source Levels are also INVALID_SOURCE; use Vanilla development teleport
to enter the first Phase. SAME_PHASE is an early no-op. Null arguments are caller
programming errors. Nonfinite position/rotation/AABB is rejected before generation.

## Responsibilities and exact policy

PhaseWorldResolver maps the requested Phase to its catalog Level key. A missing
target returns TARGET_LEVEL_MISSING, without falling back to another world.

The service checks the entire body AABB against the destination's build height
and WorldBorder **before** chunk generation. In 26.1 getMaxY is inclusive, so
the body's max edge may reach `getMaxY()+1`. It then acquires FULL LevelChunks
synchronously via `ServerChunkCache.getChunk(cx,cz,ChunkStatus.FULL,true)` covering
the body's x/z footprint **plus one block**. Vanilla BlockCollisions inspects this
halo for shapes extending from neighboring blocks; chunk edges and negative
coordinates are included. This can generate actual features and structures, as
required for real safety, unlike TerrainSampleService's temporary base columns.

getChunk's main-thread path adds Vanilla UNKNOWN loading tickets (timeout one
tick) and pumps its processor until generation completes. A null/non-LevelChunk
result is CHUNK_NOT_READY; known IllegalStateException/CompletionException loading
failures are logged and return CHUNK_FAILURE. Other unexpected failures propagate.
No custom ticket registry, permanent force load, scheduler or timeout framework is
added. Because the wait pumps tasks, source Level/position/rotation/body/player
validity and destination bounds are checked again before landing evaluation.

SafeLandingResolver is a functional boundary:

```java
Optional<Vec3> resolve(ServerPlayer player, ServerLevel target,
        Vec3 originalPosition, TransferPolicy policy);
```

Resolvers must not mutate players. The default `exactPosition()` translates the
current body to the original position and asks the **target** Level:

```java
target.noCollision(player, body) && !target.containsAnyLiquid(body)
```

Vanilla noCollision checks block shapes, entities that participate in collision
and world-border collision; not every visible entity blocks movement. For example,
an armor stand is not an appropriate blocking-entity fixture; the smoke uses a boat.
Liquid-filled body space also rejects. No estimated height or noise is consulted.
The resolver returns the exact original Vec3 or empty, with no y adjustment.
Returning adjusted coordinates under EXACT_POSITION is a programming error.

This policy guarantees collision-free, non-liquid **body space**, not support
under the feet or freedom from fall damage, magma, fire or nearby hazards. It can
accept open air. No ground/surface search, y +/-1..4 search or safe-route/scoring
system is implemented in #9.

## Verified 26.1 teleport pipeline

Inspected the generated Minecraft 26.1 sources pinned to NeoForge 26.1.0.19-beta
and NeoForge's ServerPlayer travel hook. The service calls:

```java
ServerPlayer moved = player.teleport(new TeleportTransition(
        target, originalPosition, Vec3.ZERO, originalYaw, originalPitch,
        TeleportTransition.PLACE_PORTAL_TICKET));
```

This constructor uses an empty Set of Relative flags: position/rotation are
absolute; original x/y/z doubles are passed unchanged. Velocity is reset to zero,
matching Vanilla's absolute teleportTo helper. Vanilla's ServerPlayer implementation
returns **the same ServerPlayer**, removes it from the old Level and registers it
in the destination; inventory and UUID stay attached to that object. The service
checks returned identity, actual Level/position/yaw/pitch and removal state before
reporting SUCCESS. The post-transition uses Vanilla's portal ticket: radius 3,
300-tick timeout, preserving destination loading while normal player tracking takes
over. Preflight itself uses only temporary UNKNOWN tickets.

NeoForge's EntityTravelToDimensionEvent can veto before native player mutation and
the method then returns null. The service returns ERROR only if the player is still
valid in the source with the original position/rotation/body. This lower-level API
is intentional: ServerPlayer.teleportTo(...) wakes a sleeper and can reset camera
**before** delegating, even if the travel event later vetoes.

## Failure atomicity boundary

Every normal preflight rejection is before player mutation. Chunk generation may
change the destination world, but does not move the player or copy inventory. A
normal NeoForge travel veto is verified to leave source state/items unchanged.
Externally changed state while chunk tasks execute prevents a stale transfer.

The native call is the commit boundary. Vanilla sends packets, removes/registers
entities and invokes events during it; it exposes no transactional undo. If it
throws, the service rethrows with the cause and an explicit commit-boundary warning.
If it returns null after changing source state, returns another player, or reports
a result with wrong Level/position/rotation/removal state, the service throws an
inconsistency exception. These are **not** normal TransferResult failures or safe
automatic retry cases. There is no second-teleport rollback that could itself fail
or duplicate packets/events. Atomic rollback of arbitrary third-party event/native
exceptions is not promised. No teleport pipeline Mixin or patch is introduced.

| TransferResult | Meaning |
| --- | --- |
| SUCCESS | Same player verified at the requested Phase and exact position/rotation |
| SAME_PHASE | Source already is the requested Phase; no mutation or chunk load |
| TARGET_LEVEL_MISSING | Target Phase Level is not loaded |
| NO_SAFE_POSITION | Exact body space is blocked by real collision or contains liquid |
| CHUNK_NOT_READY | FULL acquisition did not return a LevelChunk |
| CHUNK_FAILURE | Known synchronous chunk loading/generation failure |
| OUT_OF_WORLD | Body leaves destination build bounds or WorldBorder |
| INVALID_SOURCE | Invalid/unsupported/stale source player/world/state |
| ERROR | Native travel returned null with verified unchanged source state |

## Tests and dedicated-server probe

```sh
./gradlew test --tests dev.fangbm.worldphase.transfer.PhaseTransferServiceTest --no-build-cache
./gradlew test --tests dev.fangbm.worldphase.transfer.PhaseTransferServiceTest --tests 'dev.fangbm.worldphase.worldgen.*' --no-build-cache
./gradlew build --no-build-cache
./gradlew runPhaseVerificationTransferServer --console=plain --no-build-cache
```

The dedicated run uses the existing phaseVerification dev source set in transfer
mode, with its own `run/phase-transfer-verification-server` directory, never the
WPH-007 full-chunk/visual probe. Prepare accepted eula.txt and server.properties
with seed 123456789, loopback server-ip, unused port (validated: 25571), offline
mode, view-distance=2, simulation-distance=2. The final validation used a fresh
`level-name=world-wph009-final`. A fresh level-name exercises cold generation;
the probe can also run again on its saved world. The task clears old status, requires PASS and stops
automatically. Reports/worlds/logs remain ignored; the probe is excluded from the
production JAR.

Unit tests cover sequencing, Smooth->Ridge->Cell, exact fractional position and
rotation, same Phase, missing target, body/fluid/entity/border/build-bound rejection,
chunk failure/readiness/halo, invalid source states, off-thread rejection, native
null/exception/inconsistent results, stale source during loading, exact-policy
enforcement and repeated delegation. They use mocked shells; actual entity identity,
all inventory slots and Vanilla teleport behavior are checked by the real probe.

Full build: **188 tests, zero failures/errors/skips**: 26 transfer, 30 sampling,
6 Phase world, 3 Smooth world, 1 Fracture preset, 34 unchanged noise golden/regression,
51 density/codec, 29 Phase domain and 8 profiler. Focused transfer + worldgen run:
151 tests, all passing. Production JAR contains the four transfer types and no
verification or test classes.

Dedicated validation on 2026-10-08, Windows x64 / Temurin 25.0.3+9:

- All four actual same-seed Phase Levels loaded. Fresh cold-target run reached
  Done (4.625s), PASS, save/stop,
  Gradle exit 0; no registry, density codec, Mixin or final-run runtime errors.
- Real ServerPlayer with a Vanilla ServerGamePacketListenerImpl; **only outbound
  packet send is discarded**. Vanilla teleport/position updates are not overridden.
  NeoForge FakePlayer's handler skips teleport position updates, so it would not
  prove this requirement. This is simulated transport, not a connected-client test.
- Controlled body-space fixture at (-16.125,220.375,15.9375), yaw=123.25,
  pitch=-37.5. Smooth->Ridge->Cell and 12 further Ridge/Cell transfers all retain
  exact coordinates, rotation, UUID, same player/inventory objects and every slot
  (including 7 diamonds and 23 iron ingots). Exactly one matching entity across all
  Levels after every transfer.
- Initial Ridge and Cell visible chunk-holder counts were 0; each became 576
  after its first transfer. These counts include Vanilla's lower-status generation
  dependencies, **not 576 FULL chunks**. At this body/chunk boundary the service
  requests four FULL chunks, with Vanilla managing their dependencies and the
  ordinary post-teleport ticket. Cold generation blocks the server until ready.
- Saved-world restart also passed the same probe and stopped cleanly: Done
  (5.281s), Ridge/Cell holder counts 841->841. Expiring native portal tickets can
  be persisted across restart; this is why the probe accepts warm and cold targets.
- Same-Phase no-op, blocked exact y despite empty higher space, overlapping boat,
  border violation, **actual NeoForge travel veto** and removed-player rejection
  pass. Normal failures retain source Level/position/rotation/identity/items.

## Scope and next issue

Technical Design v0.1's core service/result/resolver responsibilities are retained;
26.1's nullable native return and nontransactional commit boundary are made explicit.
No changes to PhaseDefinition, kernels/golden fixtures, density adapter, sampling
service or worldgen presets. No commands, items, Anchor/Shifter, tutorial/Scanner,
rendering, worldgen tuning or village/entity adaptation (#25).

After review, **#10** should implement bounded exact-y then +/-1..4 candidate
search in SafeLandingResolver, define/validate its standing/support/hazard policy,
retain original x/z, and add correction/all-blocked acceptance tests. #9 provides
the resolver seam; it intentionally does not enable any correction or surface mode.
