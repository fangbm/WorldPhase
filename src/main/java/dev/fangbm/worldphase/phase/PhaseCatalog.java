package dev.fangbm.worldphase.phase;

import dev.fangbm.worldphase.WorldPhase;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Immutable single source of MVP Phase definitions. Creating keys does not
 * register or create dimensions; datapack/worldgen setup belongs to later issues.
 */
public final class PhaseCatalog {
    private static final List<PhaseDefinition> DEFINITIONS = List.of(
        new PhaseDefinition(PhaseId.FRACTURE, levelKey("fracture")),
        new PhaseDefinition(PhaseId.SMOOTH, levelKey("smooth")),
        new PhaseDefinition(PhaseId.RIDGE, levelKey("ridge")),
        new PhaseDefinition(PhaseId.CELL, levelKey("cell"))
    );

    private static final Map<PhaseId, PhaseDefinition> BY_ID = DEFINITIONS.stream()
        .collect(Collectors.toUnmodifiableMap(PhaseDefinition::id, Function.identity()));

    private PhaseCatalog() {}

    /** Returns the four immutable definitions in their explicit MVP order. */
    public static List<PhaseDefinition> all() {
        return DEFINITIONS;
    }

    /** Returns the definition for a known Phase; null is a programming error. */
    public static PhaseDefinition get(PhaseId id) {
        return Objects.requireNonNull(BY_ID.get(Objects.requireNonNull(id, "id")), "Undefined Phase");
    }

    private static ResourceKey<Level> levelKey(String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(WorldPhase.MOD_ID, path));
    }
}
