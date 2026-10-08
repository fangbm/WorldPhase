package dev.fangbm.worldphase.transfer;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Explicit landing contracts; neither policy searches horizontally or scans to the surface. */
public enum TransferPolicy {
    EXACT_POSITION,
    /** Supported gameplay landing: original y, then +1/-1 through +4/-4; never changes x/z. */
    BOUNDED_LANDING;

    /** Fixed order also defines upward preference for ties. Integer addition preserves fractional y. */
    public List<Vec3> candidates(Vec3 original) {
        if (this == EXACT_POSITION) return List.of(original);
        return List.of(original, at(original,1), at(original,-1), at(original,2), at(original,-2),
                at(original,3), at(original,-3), at(original,4), at(original,-4));
    }

    public boolean permits(Vec3 original, Vec3 destination) {
        return candidates(original).contains(destination);
    }

    private static Vec3 at(Vec3 original, int dy) {
        return new Vec3(original.x, original.y + dy, original.z);
    }
}
