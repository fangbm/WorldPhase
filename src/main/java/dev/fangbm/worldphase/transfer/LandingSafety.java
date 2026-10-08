package dev.fangbm.worldphase.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Actual target collision geometry; no terrain estimates and no player mutation. */
public final class LandingSafety {
    private static final double CONTACT_PROBE = 1.0E-4;
    private LandingSafety() {}

    public static AABB body(ServerPlayer player, Vec3 destination) {
        return player.getBoundingBox().move(destination.subtract(player.position()));
    }

    public static boolean withinWorld(ServerLevel target, AABB box) {
        return box.minY >= target.getMinY() && box.maxY <= (double) target.getMaxY() + 1
                && target.getWorldBorder().isWithinBounds(box);
    }

    public static boolean isSafe(ServerPlayer player, ServerLevel target, Vec3 destination, TransferPolicy policy) {
        var box = body(player, destination);
        if (!withinWorld(target, box)) return false;
        if (policy == TransferPolicy.EXACT_POSITION) {
            return target.noCollision(player, box) && !target.containsAnyLiquid(box);
        }
        // Vanilla's candidate-position context avoids testing scaffolding against the source y.
        var context = CollisionContext.withPosition(player, destination.y);
        if (blockShapes(target, context, box).iterator().hasNext() || !target.noEntityCollision(player, box)
                || target.containsAnyLiquid(box)) return false;
        // Require contact beneath the central half of the footprint. Vanilla shape collision
        // performs the geometry; a ledge touching only the outer edge is conservatively rejected.
        var feet = new AABB(box.minX + box.getXsize() * .25, box.minY, box.minZ + box.getZsize() * .25,
                box.maxX - box.getXsize() * .25, box.minY + CONTACT_PROBE, box.maxZ - box.getZsize() * .25);
        double fall = Shapes.collide(Direction.Axis.Y, feet,
                blockShapes(target, context, feet.expandTowards(0, -CONTACT_PROBE, 0)), -CONTACT_PROBE);
        if (Math.abs(fall) > 1.0E-7) return false;
        // Include the supporting surface and the body footprint, with a tiny contact margin.
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(box.minX - CONTACT_PROBE, box.minY - CONTACT_PROBE, box.minZ - CONTACT_PROBE),
                BlockPos.containing(box.maxX + CONTACT_PROBE, box.maxY, box.maxZ + CONTACT_PROBE))) {
            if (hazardous(target.getBlockState(pos))) return false;
        }
        return true;
    }

    private static Iterable<VoxelShape> blockShapes(ServerLevel target, CollisionContext context, AABB box) {
        return () -> new BlockCollisions<>(target, context, box, false, (pos, shape) -> shape);
    }

    private static boolean hazardous(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(BlockTags.FIRE)
                || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)
                || (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT));
    }
}
