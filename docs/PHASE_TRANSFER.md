# Phase transfer and safe landing (WPH-009 / WPH-010 / WPH-011)

```java
var transfers = new PhaseTransferService(server);
transfers.transfer(player, PhaseId.RIDGE, TransferPolicy.BOUNDED_LANDING);
```

The service belongs to one MinecraftServer lifetime. Calls must run on its main
thread; an off-thread call throws before reading the player. Source Level,
position, yaw/pitch and complete AABB come from ServerPlayer. Only live players
in this server's loaded MVP Phases are accepted. Removed, dead, sleeping, riding,
passenger-carrying and unknown/Vanilla source states remain INVALID_SOURCE.
SAME_PHASE remains an early no-op for EXACT_POSITION and BOUNDED_LANDING.
Explicit DEV_SURFACE can reposition within the same Phase. No client coordinates or terrain estimates
are used. PhaseWorldResolver supplies the target key; a missing Level returns
TARGET_LEVEL_MISSING.

## Policies and search order

- EXACT_POSITION preserves the original x/y/z. As in WPH-009, it checks only
  full body bounds, Vanilla block/entity collision and liquid occupancy. It can
  accept unsupported air; it does not promise a safe standing surface.
- BOUNDED_LANDING checks exactly this fixed order:
  `0, +1, -1, +2, -2, +3, -3, +4, -4`. Upward wins equal-distance ties. It stops
  at the first safe position; at most nine candidates receive safety checks.
  Each position copies the original x/z doubles directly (including signed zero)
  and adds an integer to the original y. There is no rounding, horizontal move,
  dimension scaling, fractional y snapping, surface scan or +/-5 fallback.
- DEV_SURFACE is an explicit OP development policy. After preparing FULL chunks,
  it examines at most 64 descending rows from the real
  MOTION_BLOCKING_NO_LEAVES first-free height plus four (clamped to build height).
  The center-column collision shape proposes a top face, including fractional
  slab/stair heights; the same full-body LandingSafety checks remain authoritative.
  It preserves exact x/z but may replace y and its fractional part. It is never
  a fallback for gameplay. See [developer commands](DEVELOPER_COMMANDS.md).

Airborne positions may have no supported candidate with that fractional y and
will be refused. Integer offsets deliberately cannot turn arbitrary fractional
feet height into an integer or half-block surface height.

SafeLandingResolver.gameplay() supports these policies. The original
exactPosition() factory remains available. Resolvers must not mutate the player.
TransferPolicy.permits verifies that a returned Vec3 equals one of the actual
policy candidates. A custom resolver returning another x/z, fractional adjustment
or an out-of-range y causes a programming error before teleport. Custom resolver
results also pass the same real safety check in the service. The built-in already
checks that final AABB during resolution, so the service does not repeat its
safety evaluation; callers cannot enable that internal trusted path.

## Actual 26.1 collision and support APIs

Inspected generated sources pinned to Minecraft 26.1 / NeoForge 26.1.0.19-beta:
CollisionGetter, BlockCollisions, CollisionContext, EntityCollisionContext,
Shapes, VoxelShape, ServerPlayer, ServerChunkCache and TeleportTransition. Official
26.1 references: [migration primer](https://docs.neoforged.net/primer/docs/26.1/)
and [blockstates](https://docs.neoforged.net/docs/blocks/states/).

LandingSafety translates the source's actual AABB to each candidate, including
nondefault widths/poses. BOUNDED_LANDING requires:

1. The entire body is inside WorldBorder and build height. getMaxY is inclusive;
   the body's maximum edge may reach getMaxY()+1. Candidates outside these bounds
   are skipped. If none can fit, the service returns OUT_OF_WORLD before loading.
2. No target block collision shapes intersect the body. The public
   BlockCollisions constructor receives CollisionContext.withPosition(player,
   candidate.y), the API also used by Vanilla pre-move collision queries. This
   supplies candidate feet height, player descent state and held item without
   moving the player. CollisionGetter.noEntityCollision checks entities that
   actually block movement; visible entities such as armor stands need not block.
3. ServerLevel.containsAnyLiquid rejects body space containing water/lava/liquid.
4. A block collision face supports the central half of the footprint in both
   horizontal axes. A 0.0001-block downward probe is evaluated by Vanilla
   Shapes.collide(Y, feet, shapes, -0.0001); remaining fall must be <=1e-7, Vanilla's
   numerical tolerance. This is contact, not permission to hover or snap down.
   The support must come from block shapes, not a boat/entity. Half slabs, stairs,
   trapdoors and other shapes are handled by Vanilla geometry, never isSolid or
   copied collision math. Contact only at the outer edge is conservatively refused.
5. Blocks in the body/support footprint with a 0.0001 contact margin exclude:
   fire/soul fire (also the fire tag), lava, magma, cactus, sweet berry bush,
   wither rose, powder snow, pointed dripstone and lit normal/soul campfires.
   Unlit campfires can support a player. Hazard checks are conservative block-cell
   checks, independent of equipment or temporary immunity.

No TerrainSampleService, baseHeight, worldgen tuning, noise or PhaseDefinition
changes are involved.

## Chunks, destination integration and failure boundary

The service obtains FULL LevelChunks synchronously with
ServerChunkCache.getChunk(cx,cz,ChunkStatus.FULL,true). It prepares the original
body's x/z footprint and Vanilla BlockCollisions' one-block neighborhood once.
The integer endpoints match Vanilla's 1e-7 boundary epsilon as well, preventing
an unprepared neighbor immediately inside a chunk boundary. All vertical
candidates reuse that footprint; no repeated acquisition or distant search occurs.
Vanilla can generate dependency chunks while satisfying FULL requests. A null or
non-LevelChunk gives CHUNK_NOT_READY; known CompletionException/IllegalStateException
acquisition failures give logged CHUNK_FAILURE. Unexpected failures propagate.

Synchronous chunk loading pumps tasks, so the original Level/position/rotation,
body and source validity are rechecked afterward. Bounds are rechecked too, and
source consistency is checked again after resolution. The original snapshot is
used for preflight/veto checks; the resolved final destination is used in the
teleport call and success postconditions:

```java
ServerPlayer moved = player.teleport(new TeleportTransition(
    target, destination, Vec3.ZERO, originalYaw, originalPitch,
    TeleportTransition.PLACE_PORTAL_TICKET));
```

Empty relative flags mean absolute, unscaled coordinates. Vanilla reuses the same
ServerPlayer and inventory, and resets velocity as in WPH-009. Its normal portal
ticket has radius 3 and timeout 300 ticks; preflight uses temporary UNKNOWN tickets.
There is no new registry, queue, Mixin or teleport-pipeline modification.

All candidates failing safety gives NO_SAFE_POSITION before native teleport;
source Level/position/yaw/pitch/UUID/inventory remain unchanged. A normal NeoForge
travel veto returns null; ERROR is returned only if the original source state is
still valid and unchanged. The native call remains the nontransactional commit
boundary: exceptions, changed-state null returns, wrong identity or wrong final
position/rotation/removal state throw. Arbitrary third-party/native failures after
commit are not safely rollbackable; no second teleport or false SUCCESS is used.
Unexpected resolver failures also propagate. The service cannot undo mutations
made by a contract-violating custom resolver or third-party event listener.

## Validation

```sh
./gradlew test --tests '*transfer*' --console=plain --no-build-cache
./gradlew build --console=plain --no-build-cache
./gradlew runPhaseVerificationTransferServer --console=plain --no-build-cache
# Run the same server task again against its saved world.
```

The isolated run remains run/phase-transfer-verification-server with dev-only
phaseVerification classes, excluded from the production JAR. Prepare accepted
eula.txt, seed 123456789, loopback server-ip, unused port 25571, offline mode,
view-distance=2 and simulation-distance=2. Use a fresh level-name for a clean
world; repeating that name tests reload. The task clears old status, requires
PASS, saves and stops automatically. Runtime files stay ignored.

Unit tests use real Vanilla BlockCollisions/VoxelShapes over a small block map
with mocked world shells, plus transfer sequencing/native-result mocks. Dedicated
validation uses real ServerLevels, blocks, a boat and a real ServerPlayer with
Vanilla teleport handling; only outbound packet sends are discarded. This proves
server collision/teleport behavior with simulated transport, not a connected
client session or player login/save cycle.

The probe retains WPH-009 EXACT_POSITION regression (Smooth -> Ridge -> Cell,
solid block/entity/border rejection, actual NeoForge travel veto, 12 repeats and
removed-player rejection). It adds supported Smooth -> Ridge at
(-16.125,220.5,15.9375), then Ridge -> Cell corrected +1 to221.5; all-blocked,
unsupported-body-space, actually safe but forbidden +5, narrow cave, real
water/lava/fire/soul fire/magma/cactus/lit campfire/berry/rose/powder snow/dripstone,
boat occupancy, +4/-4/-1 and stairs. Postarrival it checks actual body/liquid space,
landing safety and Vanilla findSupportingBlock. Another 12 bounded transfers
alternate220.5/221.5 and verify exact x/z, yaw123.25, pitch-37.5, UUID, same player
and inventory objects, every inventory slot, and exactly one UUID entity across
all Levels. Bounded WorldBorder rejection and an actual veto of an adjusted -1
transfer retain source state. On restart a saved GOLD_BLOCK fixture is checked
before fixtures are reset, followed by the complete probe and another clean save.

Validated on 2026-10-08, Windows x64 / Temurin 25.0.3+9:

- Focused landing/transfer tests passed. The complete build passed **239 tests**:
  45 transfer, 32 landing, 29 Phase domain, 51 density/codec, 34 noise, 10 world
  preset tests, 30 sampling and 8 profiler; zero failures/errors/skips.
- Fresh `world-wph010-final` reached Done (3.278s), passed the complete real
  probe, saved every Level and stopped with Gradle exit0.
- Restarting that same world reached Done (3.414s), reported
  `WPH010 RELOAD PASS persisted GOLD_BLOCK target fixture`, passed every scenario
  again, saved and stopped with exit0. No registry/codec/Mixin/runtime errors in
  either final run. Initial fixture development exposed setup errors (slab height
  and soul-fire support); the fixtures were corrected before these final runs.
- The production JAR contains the transfer/landing types and excludes dev probe,
  test and profiler classes. No generated output or game directory is committed.

## Known safety boundaries and scope

This is a conservative immediate landing check. Central-footprint contact is not
a requirement that every square centimeter of the body footprint have ground.
It may refuse thin edge perches or hazards elsewhere in the touched block cells.
Only listed Vanilla hazards and fire-tag extensions are recognized; arbitrary
modded damage callbacks, traps/portals, future fluid flow, falling blocks, moving
entities, ongoing status/fall damage and subsequent world changes are not
promised safe. Special collision shapes depend on Vanilla's player context and
the block implementation. There is no blanket guarantee for every modded block.

Technical Design v0.1's bounded-y architecture is retained; actual 26.1 shape,
context and nullable teleport APIs implement it. EXACT_POSITION keeps its original
body-space contract. WPH-011 adds OP commands and explicit DEV_SURFACE without
changing those gameplay policies, presets, terrain/noise or the known hazard boundaries.

## WPH-011 diagnostic integration

`inspectLanding(player, phase, policy)` uses the same chunk preparation, candidate
contract and LandingSafety as transfer, and returns a LandingInspection without
teleporting. Its SUCCESS means a candidate was found, not a transfer or reservation.
Inspection accepts a valid Vanilla source for development diagnostics; actual
transfer still requires a Phase source. The original snapshot is checked after
chunk loading and resolution. DEV_SURFACE candidates and `permits` require the
prepared target Level; the no-Level candidate API refuses that policy. Returned
surface positions must match an actual candidate and pass the same safety check.

The original 239-test WPH-010 validation above is retained as its historical record.
WPH-011 adds command/surface tests, bringing the complete build to 280 passing tests,
and reruns the real transfer probe. See the separate command acceptance record for
dedicated-server and completed connected native-client evidence.
