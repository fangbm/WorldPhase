package dev.fangbm.worldphase.transfer;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Landing strategy boundary. Called on the server thread after collision-neighborhood chunks are ready.
 * Implementations must not mutate the player; returned positions must satisfy the supplied policy.
 */
@FunctionalInterface
public interface SafeLandingResolver {
    Optional<Vec3> resolve(ServerPlayer player, ServerLevel target, Vec3 originalPosition, TransferPolicy policy);

    /** Exact body-space check only: no y search, support-floor search or estimated terrain height. */
    static SafeLandingResolver exactPosition() {
        return (player, target, position, policy) -> {
            var box = player.getBoundingBox().move(position.subtract(player.position()));
            return target.noCollision(player, box) && !target.containsAnyLiquid(box)
                    ? Optional.of(position) : Optional.empty();
        };
    }

    /** Gameplay: at most nine positions. Explicit DEV_SURFACE: at most64. Reuses prepared chunks. */
    static SafeLandingResolver gameplay() {
        return (player, target, position, policy) -> policy.candidates(position, target).stream()
                .filter(candidate -> LandingSafety.isSafe(player, target, candidate, policy)).findFirst();
    }
}
