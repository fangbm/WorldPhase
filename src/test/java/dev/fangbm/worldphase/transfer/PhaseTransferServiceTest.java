package dev.fangbm.worldphase.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.fangbm.worldphase.phase.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests core sequencing/failure contracts; actual teleport/entity/inventory behavior has a server probe. */
class PhaseTransferServiceTest {
    private MinecraftServer server;
    private ServerPlayer player;
    private PhaseTransferService service;
    private final Map<PhaseId, ServerLevel> levels = new EnumMap<>(PhaseId.class);
    private final Map<PhaseId, ServerChunkCache> chunks = new EnumMap<>(PhaseId.class);
    private final AtomicReference<ServerLevel> currentLevel = new AtomicReference<>();
    private final AtomicReference<Vec3> currentPosition = new AtomicReference<>();
    private final AtomicReference<Float> currentYaw = new AtomicReference<>(), currentPitch = new AtomicReference<>();
    private final List<TeleportTransition> transitions = new ArrayList<>();
    private Vec3 original;
    private AABB body;

    @BeforeEach void setup() {
        server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        for (PhaseId phase : PhaseId.values()) {
            var level = mock(ServerLevel.class);
            var cache = mock(ServerChunkCache.class);
            when(level.dimension()).thenReturn(PhaseWorldResolver.levelKey(phase));
            when(level.getServer()).thenReturn(server);
            when(level.getMinY()).thenReturn(-64);
            when(level.getMaxY()).thenReturn(319);
            when(level.getWorldBorder()).thenReturn(new WorldBorder());
            when(level.getChunkSource()).thenReturn(cache);
            when(level.noCollision(any(), any(AABB.class))).thenReturn(true);
            when(cache.getChunk(anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(true))).thenReturn(mock(LevelChunk.class));
            when(server.getLevel(PhaseWorldResolver.levelKey(phase))).thenReturn(level);
            levels.put(phase, level); chunks.put(phase, cache);
        }
        player = mock(ServerPlayer.class);
        currentLevel.set(levels.get(PhaseId.SMOOTH));
        original = new Vec3(-16.125, 80.375, 15.9375);
        currentPosition.set(original); currentYaw.set(123.25f); currentPitch.set(-37.5f);
        body = new AABB(original.x - .3, original.y, original.z - .3, original.x + .3, original.y + 1.8, original.z + .3);
        when(player.level()).thenAnswer(call -> currentLevel.get());
        when(player.position()).thenAnswer(call -> currentPosition.get());
        // Entity's final accessor cannot be stubbed with the project's subclass mock maker.
        player.setBoundingBox(body);
        when(player.getYRot()).thenAnswer(call -> currentYaw.get());
        when(player.getXRot()).thenAnswer(call -> currentPitch.get());
        when(player.isAlive()).thenReturn(true);
        when(player.teleport(any(TeleportTransition.class))).thenAnswer(call -> {
            TeleportTransition transition = call.getArgument(0);
            transitions.add(transition);
            currentLevel.set(transition.newLevel()); currentPosition.set(transition.position());
            currentYaw.set(transition.yRot()); currentPitch.set(transition.xRot());
            return player;
        });
        service = new PhaseTransferService(server);
    }

    private TransferResult transfer(PhaseId target) { return service.transfer(player, target, TransferPolicy.EXACT_POSITION); }

    private void unmoved() {
        assertSame(levels.get(PhaseId.SMOOTH), player.level());
        assertEquals(original, player.position());
        assertEquals(123.25f, player.getYRot()); assertEquals(-37.5f, player.getXRot());
        verify(player, never()).teleport(any(TeleportTransition.class));
    }

    @Test void smoothToRidgeThenCellPreservesExactCoordinatesAndRotation() {
        assertEquals(TransferResult.SUCCESS, transfer(PhaseId.RIDGE));
        assertSame(levels.get(PhaseId.RIDGE), player.level());
        assertEquals(TransferResult.SUCCESS, transfer(PhaseId.CELL));
        assertSame(levels.get(PhaseId.CELL), player.level());
        assertEquals(original, player.position());
        for (var transition : transitions) {
            assertEquals(original, transition.position());
            assertEquals(123.25f, transition.yRot()); assertEquals(-37.5f, transition.xRot());
            assertTrue(transition.relatives().isEmpty());
            assertEquals(Vec3.ZERO, transition.deltaMovement());
            assertSame(TeleportTransition.PLACE_PORTAL_TICKET, transition.postTeleportTransition());
        }
        var order = inOrder(chunks.get(PhaseId.RIDGE), levels.get(PhaseId.RIDGE), player);
        order.verify(chunks.get(PhaseId.RIDGE), atLeastOnce()).getChunk(anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(true));
        order.verify(levels.get(PhaseId.RIDGE)).noCollision(eq(player), eq(body));
        order.verify(player).teleport(argThat(t -> t.newLevel() == levels.get(PhaseId.RIDGE)));
    }

    @Test void samePhaseIsANoOpBeforeChunkOrLandingChecks() {
        assertEquals(TransferResult.SAME_PHASE, transfer(PhaseId.SMOOTH)); unmoved();
        for (var cache : chunks.values()) verifyNoInteractions(cache);
    }

    @Test void missingTargetIsExplicitAndDoesNotMove() {
        when(server.getLevel(PhaseWorldResolver.levelKey(PhaseId.RIDGE))).thenReturn(null);
        assertEquals(TransferResult.TARGET_LEVEL_MISSING, transfer(PhaseId.RIDGE)); unmoved();
    }

    @Test void blockedExactBodySpaceRejectsWithoutTryingAnotherY() {
        when(levels.get(PhaseId.RIDGE).noCollision(player, body)).thenReturn(false);
        assertEquals(TransferResult.NO_SAFE_POSITION, transfer(PhaseId.RIDGE)); unmoved();
        verify(levels.get(PhaseId.RIDGE)).noCollision(player, body);
    }

    @Test void occupiedEntitySpaceUsesTargetCollisionCheck() {
        when(levels.get(PhaseId.CELL).noCollision(player, body)).thenReturn(false);
        assertEquals(TransferResult.NO_SAFE_POSITION, transfer(PhaseId.CELL)); unmoved();
    }

    @Test void liquidFilledSpaceRejects() {
        when(levels.get(PhaseId.RIDGE).containsAnyLiquid(body)).thenReturn(true);
        assertEquals(TransferResult.NO_SAFE_POSITION, transfer(PhaseId.RIDGE)); unmoved();
    }

    @Test void wholeBodyOutsideWorldBorderRejectsBeforeChunkLoads() {
        var border = new WorldBorder(); border.setCenter(original.x, original.z); border.setSize(.4);
        when(levels.get(PhaseId.RIDGE).getWorldBorder()).thenReturn(border);
        assertEquals(TransferResult.OUT_OF_WORLD, transfer(PhaseId.RIDGE)); unmoved();
        verifyNoInteractions(chunks.get(PhaseId.RIDGE));
    }

    @ParameterizedTest @CsvSource({"82,319", "-64,79"})
    void verticalBodyBoundsRejectBeforeLoads(int minY, int maxY) {
        when(levels.get(PhaseId.RIDGE).getMinY()).thenReturn(minY);
        when(levels.get(PhaseId.RIDGE).getMaxY()).thenReturn(maxY);
        assertEquals(TransferResult.OUT_OF_WORLD, transfer(PhaseId.RIDGE)); unmoved();
        verifyNoInteractions(chunks.get(PhaseId.RIDGE));
    }

    @Test void chunkFailureIsReportedBeforeLandingOrMovement() {
        when(chunks.get(PhaseId.RIDGE).getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(true)))
                .thenThrow(new CompletionException(new IllegalStateException("generation failed")));
        assertEquals(TransferResult.CHUNK_FAILURE, transfer(PhaseId.RIDGE)); unmoved();
        verify(levels.get(PhaseId.RIDGE), never()).noCollision(any(), any(AABB.class));
    }

    @Test void incompleteChunkDoesNotCountAsReady() {
        when(chunks.get(PhaseId.RIDGE).getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(true))).thenReturn(null);
        assertEquals(TransferResult.CHUNK_NOT_READY, transfer(PhaseId.RIDGE)); unmoved();
    }

    @Test void collisionNeighborhoodCoversNegativeAndBoundaryChunkEdges() {
        assertEquals(TransferResult.SUCCESS, transfer(PhaseId.RIDGE));
        for (int x : new int[]{-2,-1}) for (int z : new int[]{0,1}) {
            verify(chunks.get(PhaseId.RIDGE)).getChunk(x,z,ChunkStatus.FULL,true);
        }
        verify(chunks.get(PhaseId.RIDGE), times(4)).getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(true));
    }

    @ParameterizedTest @ValueSource(strings={"dead","sleeping","passenger","vehicle"})
    void unsupportedPlayerStateRejectsWithoutSideEffects(String state) {
        switch (state) {
            case "dead" -> when(player.isAlive()).thenReturn(false);
            case "sleeping" -> when(player.isSleeping()).thenReturn(true);
            case "passenger" -> when(player.isPassenger()).thenReturn(true);
            case "vehicle" -> when(player.isVehicle()).thenReturn(true);
        }
        assertEquals(TransferResult.INVALID_SOURCE, transfer(PhaseId.RIDGE)); unmoved();
    }

    @Test void vanillaSourceIsNotSilentlyTreatedAsAPhase() {
        when(levels.get(PhaseId.SMOOTH).dimension()).thenReturn(Level.OVERWORLD);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(levels.get(PhaseId.SMOOTH));
        assertEquals(TransferResult.INVALID_SOURCE, transfer(PhaseId.RIDGE)); unmoved();
    }

    @Test void nonFiniteServerPositionRejectsBeforeLoading() {
        currentPosition.set(new Vec3(Double.NaN, 80, 15));
        assertEquals(TransferResult.INVALID_SOURCE, transfer(PhaseId.RIDGE));
        verify(player, never()).teleport(any(TeleportTransition.class));
        verifyNoInteractions(chunks.get(PhaseId.RIDGE));
    }

    @Test void offThreadFailsBeforeReadingPlayerState() {
        when(server.isSameThread()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> transfer(PhaseId.RIDGE));
        verifyNoInteractions(player);
    }

    @Test void cancelledNativeTeleportReturnsErrorAndKeepsSource() {
        when(player.teleport(any(TeleportTransition.class))).thenReturn(null);
        assertEquals(TransferResult.ERROR, transfer(PhaseId.RIDGE));
        assertSame(levels.get(PhaseId.SMOOTH), player.level()); assertEquals(original, player.position());
        assertEquals(123.25f, player.getYRot()); assertEquals(-37.5f, player.getXRot());
        verify(player, never()).stopSleepInBed(anyBoolean(),anyBoolean());
        verify(player, never()).setCamera(any());
    }

    @Test void nativeExceptionPropagatesRatherThanReportingSuccessOrRetryableFailure() {
        var error = new IllegalArgumentException("native failure");
        when(player.teleport(any(TeleportTransition.class))).thenThrow(error);
        assertSame(error, assertThrows(IllegalStateException.class, () -> transfer(PhaseId.RIDGE)).getCause());
    }

    @Test void nativePartialCommitIsAnExceptionNotANormalFailure() {
        when(player.teleport(any(TeleportTransition.class))).thenAnswer(call -> {
            currentLevel.set(levels.get(PhaseId.RIDGE)); return null;
        });
        assertThrows(IllegalStateException.class, () -> transfer(PhaseId.RIDGE));
    }

    @Test void nativeSuccessWithWrongCoordinatesIsNotReportedAsSuccess() {
        when(player.teleport(any(TeleportTransition.class))).thenAnswer(call -> {
            currentLevel.set(levels.get(PhaseId.RIDGE)); currentPosition.set(original.add(1,0,0)); return player;
        });
        assertThrows(IllegalStateException.class, () -> transfer(PhaseId.RIDGE));
    }

    @Test void exactPolicyRejectsResolverReturningAdjustedY() {
        service = new PhaseTransferService(server, (p,l,pos,policy) -> Optional.of(pos.add(0,1,0)));
        assertThrows(IllegalStateException.class, () -> transfer(PhaseId.RIDGE)); unmoved();
    }

    @Test void chunkTaskChangingSourceSnapshotPreventsAStaleTeleport() {
        when(chunks.get(PhaseId.RIDGE).getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(true))).thenAnswer(call -> {
            currentYaw.set(90f); return mock(LevelChunk.class);
        });
        assertEquals(TransferResult.INVALID_SOURCE, transfer(PhaseId.RIDGE));
        verify(player, never()).teleport(any(TeleportTransition.class));
    }

    @Test void repeatedTransfersAlwaysDelegateTheSamePlayerWithoutInventoryCopies() {
        for (int i = 0; i < 12; i++) {
            assertEquals(TransferResult.SUCCESS, transfer(i % 2 == 0 ? PhaseId.RIDGE : PhaseId.SMOOTH));
            assertEquals(original, player.position());
        }
        verify(player, times(12)).teleport(any(TeleportTransition.class));
        verify(player, never()).getInventory();
    }
}
