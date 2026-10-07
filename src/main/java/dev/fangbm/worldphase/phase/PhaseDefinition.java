package dev.fangbm.worldphase.phase;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Stable Phase metadata; terrain parameters belong to later issues. */
public record PhaseDefinition(PhaseId id, ResourceKey<Level> levelKey) {
    public PhaseDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(levelKey, "levelKey");
    }
}
