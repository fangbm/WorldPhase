package dev.fangbm.worldphase.transfer;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Explicit landing contracts. Only DEV_SURFACE performs a bounded surface search. */
public enum TransferPolicy {
    EXACT_POSITION,
    /** Supported gameplay landing: original y, then +1/-1 through +4/-4; never changes x/z. */
    BOUNDED_LANDING,
    /** Explicit OP developer command only; never a gameplay fallback. */
    DEV_SURFACE;

    /** Fixed order also defines upward preference for ties. Integer addition preserves fractional y. */
    public List<Vec3> candidates(Vec3 original) {
        if (this == DEV_SURFACE) throw new IllegalStateException("DEV_SURFACE requires prepared target chunks");
        if (this == EXACT_POSITION) return List.of(original);
        return List.of(original, at(original,1), at(original,-1), at(original,2), at(original,-2),
                at(original,3), at(original,-3), at(original,4), at(original,-4));
    }

    public boolean permits(Vec3 original, Vec3 destination) {
        return candidates(original).contains(destination);
    }

    /** Call after FULL collision-neighborhood chunks are prepared. Height is real, not base terrain. */
    public List<Vec3> candidates(Vec3 original, ServerLevel target) {
        if (this != DEV_SURFACE) return candidates(original);
        int top = Math.min(target.getMaxY() + 1,
                target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(original.x), Mth.floor(original.z)) + 4);
        var positions = new java.util.ArrayList<Vec3>();
        for (int i = 0; i < 64 && top - i >= target.getMinY(); i++) {
            int row = top - i;
            var below = new BlockPos(Mth.floor(original.x), row - 1, Mth.floor(original.z));
            var shape = target.getBlockState(below).getCollisionShape(target, below, CollisionContext.empty());
            // Propose the actual top face, including slabs/stairs. This is only a candidate:
            // LandingSafety still checks the full footprint and player-specific collision context.
            double y = shape.isEmpty() ? row : below.getY() + shape.max(Direction.Axis.Y);
            positions.add(new Vec3(original.x, y, original.z));
        }
        return List.copyOf(positions);
    }

    public boolean permits(Vec3 original, Vec3 destination, ServerLevel target) {
        return candidates(original, target).contains(destination);
    }

    private static Vec3 at(Vec3 original, int dy) {
        return new Vec3(original.x, original.y + dy, original.z);
    }
}
