package dev.fangbm.worldphase.transfer;

import com.mojang.logging.LogUtils;
import dev.fangbm.worldphase.phase.PhaseId;
import dev.fangbm.worldphase.phase.PhaseWorldResolver;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Server-owned Phase transfer. Preflight/normal rejection never mutates the player.
 * Native teleport is the commit boundary: exceptions or inconsistent postconditions propagate
 * because Minecraft's packet/event/entity pipeline has no transactional rollback API.
 */
public final class PhaseTransferService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final SafeLandingResolver landing;
    private final boolean builtInResolver;

    public PhaseTransferService(MinecraftServer server) {
        this(server, SafeLandingResolver.gameplay(), true);
    }

    public PhaseTransferService(MinecraftServer server, SafeLandingResolver landing) {
        this(server, landing, false);
    }

    private PhaseTransferService(MinecraftServer server, SafeLandingResolver landing, boolean builtInResolver) {
        this.server = Objects.requireNonNull(server, "server");
        this.landing = Objects.requireNonNull(landing, "landing");
        this.builtInResolver = builtInResolver;
    }

    public TransferResult transfer(ServerPlayer player, PhaseId targetPhase, TransferPolicy policy) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(targetPhase, "targetPhase");
        Objects.requireNonNull(policy, "policy");
        if (!server.isSameThread()) throw new IllegalStateException("Phase transfer must run on the server thread");

        var source = player.level();
        if (!validSource(player, source)) return TransferResult.INVALID_SOURCE;
        if (PhaseWorldResolver.phaseId(source).orElseThrow() == targetPhase && policy != TransferPolicy.DEV_SURFACE) {
            return TransferResult.SAME_PHASE;
        }
        var target = server.getLevel(PhaseWorldResolver.levelKey(targetPhase));
        if (target == null) return TransferResult.TARGET_LEVEL_MISSING;

        var position = player.position();
        float yaw = player.getYRot(), pitch = player.getXRot();
        var box = player.getBoundingBox();
        if (!finite(position) || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !finite(box)) {
            return TransferResult.INVALID_SOURCE;
        }
        var inspection = resolveLanding(player, target, policy, new Snapshot(source, position, yaw, pitch, box));
        if (inspection.result() != TransferResult.SUCCESS) return inspection.result();
        var finalPosition = inspection.destination().orElseThrow();

        // Unlike teleportTo(...), this does not wake sleepers/reset camera before a travel veto.
        // Empty relative flags mean absolute, unscaled doubles. Vanilla reuses this ServerPlayer.
        ServerPlayer moved;
        try {
            moved = player.teleport(new TeleportTransition(target, finalPosition, Vec3.ZERO, yaw, pitch,
                    TeleportTransition.PLACE_PORTAL_TICKET));
        } catch (RuntimeException error) {
            throw new IllegalStateException("Native Phase teleport failed at commit boundary; player state may have changed", error);
        }
        if (moved == null && validSource(player, source) && unchanged(player, source, position, yaw, pitch)
                && player.getBoundingBox().equals(box)) return TransferResult.ERROR;
        if (moved != player || !unchanged(player, target, finalPosition, yaw, pitch) || player.isRemoved()) {
            throw new IllegalStateException("Native Phase teleport returned inconsistent state; no safe automatic rollback");
        }
        return TransferResult.SUCCESS;
    }

    /** Diagnostic only; accepts a Vanilla source, but never relaxes transfer's Phase-source rule. */
    public LandingInspection inspectLanding(ServerPlayer player, PhaseId targetPhase, TransferPolicy policy) {
        Objects.requireNonNull(player); Objects.requireNonNull(targetPhase); Objects.requireNonNull(policy);
        if (!server.isSameThread()) throw new IllegalStateException("Phase landing inspection must run on the server thread");
        var state = new Snapshot(player.level(), player.position(), player.getYRot(), player.getXRot(), player.getBoundingBox());
        if (!validPlayer(player, state.source()) || !finite(state.position()) || !finite(state.box())
                || !Float.isFinite(state.yaw()) || !Float.isFinite(state.pitch())) return failed(TransferResult.INVALID_SOURCE, state);
        var target = server.getLevel(PhaseWorldResolver.levelKey(targetPhase));
        if (target == null) return failed(TransferResult.TARGET_LEVEL_MISSING, state);
        return resolveLanding(player, target, policy, state);
    }

    private record Snapshot(ServerLevel source, Vec3 position, float yaw, float pitch, AABB box) {}

    private LandingInspection resolveLanding(ServerPlayer player, ServerLevel target, TransferPolicy policy, Snapshot state) {
        var position = state.position(); var box = state.box();
        // Surface height must not be queried before FULL chunk preparation.
        if (!target.getWorldBorder().isWithinBounds(box) || (policy == TransferPolicy.DEV_SURFACE
                ? box.getYsize() > (double) target.getMaxY() + 1 - target.getMinY()
                : policy.candidates(position).stream().noneMatch(p -> withinWorld(target, box.move(p.subtract(position)))))) {
            return failed(TransferResult.OUT_OF_WORLD, state);
        }
        var ready = prepareChunks(target, box);
        if (ready != TransferResult.SUCCESS) return failed(ready, state);
        if (!snapshotUnchanged(player, state)) return failed(TransferResult.INVALID_SOURCE, state);
        if (policy.candidates(position, target).stream().noneMatch(p -> withinWorld(target, box.move(p.subtract(position))))) {
            return failed(TransferResult.OUT_OF_WORLD, state);
        }
        var destination = landing.resolve(player, target, position, policy);
        if (destination.isEmpty()) return failed(TransferResult.NO_SAFE_POSITION, state);
        var finalPosition = destination.get();
        if (!policy.permits(position, finalPosition, target)) {
            throw new IllegalStateException(policy + " resolver returned a position outside its candidates");
        }
        if (!snapshotUnchanged(player, state)) return failed(TransferResult.INVALID_SOURCE, state);
        if (!builtInResolver && !LandingSafety.isSafe(player, target, finalPosition, policy)) {
            return failed(TransferResult.NO_SAFE_POSITION, state);
        }
        return new LandingInspection(TransferResult.SUCCESS, position, destination);
    }

    private static LandingInspection failed(TransferResult result, Snapshot state) {
        return new LandingInspection(result, state.position(), Optional.empty());
    }

    private boolean snapshotUnchanged(ServerPlayer player, Snapshot state) {
        return validPlayer(player, state.source()) && unchanged(player, state.source(), state.position(), state.yaw(), state.pitch())
                && player.getBoundingBox().equals(state.box());
    }

    private boolean validSource(ServerPlayer player, ServerLevel source) {
        return validPlayer(player, source) && PhaseWorldResolver.phaseId(source).isPresent();
    }

    private boolean validPlayer(ServerPlayer player, ServerLevel source) {
        return source != null && source.getServer() == server && server.getLevel(source.dimension()) == source
                && player.isAlive() && !player.isRemoved()
                && !player.isSleeping() && !player.isPassenger() && !player.isVehicle();
    }

    private static boolean unchanged(ServerPlayer player, ServerLevel level, Vec3 position, float yaw, float pitch) {
        return player.level() == level && player.position().equals(position)
                && Float.compare(player.getYRot(), yaw) == 0 && Float.compare(player.getXRot(), pitch) == 0;
    }

    private static boolean withinWorld(ServerLevel target, AABB box) {
        return LandingSafety.withinWorld(target, box);
    }

    private static TransferResult prepareChunks(ServerLevel target, AABB box) {
        // Vanilla BlockCollisions also inspects neighboring blocks whose shapes can extend into
        // the body. Load that one-block halo, including across negative/boundary chunk edges.
        // Match BlockCollisions' epsilon as well, including an edge just inside a block boundary.
        int minX = (Mth.floor(box.minX - 1.0E-7) - 1) >> 4;
        int maxX = (Mth.floor(box.maxX + 1.0E-7) + 1) >> 4;
        int minZ = (Mth.floor(box.minZ - 1.0E-7) - 1) >> 4;
        int maxZ = (Mth.floor(box.maxZ + 1.0E-7) + 1) >> 4;
        try {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                if (!(target.getChunkSource().getChunk(x, z, ChunkStatus.FULL, true) instanceof LevelChunk)) {
                    return TransferResult.CHUNK_NOT_READY;
                }
            }
        } catch (CompletionException | IllegalStateException error) {
            LOGGER.warn("Phase destination chunk acquisition failed for {}", target.dimension().identifier(), error);
            return TransferResult.CHUNK_FAILURE;
        }
        return TransferResult.SUCCESS;
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static boolean finite(AABB b) {
        return Double.isFinite(b.minX) && Double.isFinite(b.minY) && Double.isFinite(b.minZ)
                && Double.isFinite(b.maxX) && Double.isFinite(b.maxY) && Double.isFinite(b.maxZ);
    }
}
