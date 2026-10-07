package dev.fangbm.worldphase.phase;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Resolves Phase identities by exact Level key, without loading or creating worlds. */
public final class PhaseWorldResolver {
    private static final Map<ResourceKey<Level>, PhaseId> BY_LEVEL = PhaseCatalog.all().stream()
        .collect(Collectors.toUnmodifiableMap(PhaseDefinition::levelKey, PhaseDefinition::id));

    private PhaseWorldResolver() {}

    public static ResourceKey<Level> levelKey(PhaseId id) {
        return PhaseCatalog.get(id).levelKey();
    }

    /** Unknown keys, including all Vanilla dimensions, have no MVP Phase. */
    public static Optional<PhaseId> phaseId(ResourceKey<Level> levelKey) {
        return Optional.ofNullable(BY_LEVEL.get(Objects.requireNonNull(levelKey, "levelKey")));
    }

    public static Optional<PhaseId> phaseId(ServerLevel level) {
        return phaseId(Objects.requireNonNull(level, "level").dimension());
    }
}
