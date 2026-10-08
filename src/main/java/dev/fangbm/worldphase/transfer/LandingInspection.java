package dev.fangbm.worldphase.transfer;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Preflight result, not a reservation or a completed teleport. SUCCESS means a safe candidate exists. */
public record LandingInspection(TransferResult result, Vec3 original, Optional<Vec3> destination) {
    /** BOUNDED_LANDING searches exact first; an adjusted/no destination means exact was unsafe. */
    public boolean exactSafe() { return destination.filter(original::equals).isPresent(); }
}
