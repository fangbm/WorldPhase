package dev.fangbm.worldphase.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class PhaseDomainTest {
    @ParameterizedTest
    @CsvSource({
        "FRACTURE, worldphase:fracture",
        "SMOOTH, worldphase:smooth",
        "RIDGE, worldphase:ridge",
        "CELL, worldphase:cell"
    })
    void phaseResolvesToItsExactDimensionKey(PhaseId phase, String identifier) {
        ResourceKey<Level> key = PhaseWorldResolver.levelKey(phase);

        assertTrue(key.isFor(Registries.DIMENSION));
        assertEquals(identifier, key.identifier().toString());
        assertEquals(key, PhaseCatalog.get(phase).levelKey());
    }

    @ParameterizedTest
    @CsvSource({
        "worldphase:fracture, FRACTURE",
        "worldphase:smooth, SMOOTH",
        "worldphase:ridge, RIDGE",
        "worldphase:cell, CELL"
    })
    void independentlyCreatedKeyResolvesBackToItsPhase(String identifier, PhaseId phase) {
        assertEquals(Optional.of(phase), PhaseWorldResolver.phaseId(key(identifier)));
        assertEquals(phase, PhaseCatalog.get(phase).id());
    }

    @ParameterizedTest
    @MethodSource("unknownLevels")
    void vanillaAndUnknownKeysHaveNoPhase(ResourceKey<Level> levelKey) {
        assertEquals(Optional.empty(), PhaseWorldResolver.phaseId(levelKey));
    }

    @ParameterizedTest
    @CsvSource({
        "worldphase:fracture, FRACTURE",
        "worldphase:smooth, SMOOTH",
        "worldphase:ridge, RIDGE",
        "worldphase:cell, CELL"
    })
    void serverLevelResolvesByItsDimensionKey(String identifier, PhaseId phase) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(key(identifier));

        assertEquals(Optional.of(phase), PhaseWorldResolver.phaseId(level));
    }

    @ParameterizedTest
    @MethodSource("unknownLevels")
    void vanillaAndUnknownServerLevelsHaveNoPhase(ResourceKey<Level> levelKey) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(levelKey);

        assertEquals(Optional.empty(), PhaseWorldResolver.phaseId(level));
    }

    @Test
    void catalogContainsExactlyTheFourMvpPhasesWithUniqueKeys() {
        List<PhaseDefinition> definitions = PhaseCatalog.all();
        List<PhaseId> expected = List.of(PhaseId.FRACTURE, PhaseId.SMOOTH, PhaseId.RIDGE, PhaseId.CELL);

        assertEquals(expected, List.of(PhaseId.values()));
        assertEquals(expected, definitions.stream().map(PhaseDefinition::id).toList());
        assertEquals(4, definitions.stream().map(PhaseDefinition::levelKey).distinct().count());
        for (PhaseDefinition definition : definitions) {
            assertEquals(Optional.of(definition.id()), PhaseWorldResolver.phaseId(definition.levelKey()));
        }
    }

    @Test
    void callersCannotMutateTheCatalog() {
        assertThrows(UnsupportedOperationException.class, () -> PhaseCatalog.all().clear());
        assertEquals(4, PhaseCatalog.all().size());
    }

    @Test
    void definitionRequiresBothStableMetadataFields() {
        assertThrows(NullPointerException.class, () -> new PhaseDefinition(null, key("worldphase:smooth")));
        assertThrows(NullPointerException.class, () -> new PhaseDefinition(PhaseId.SMOOTH, null));
    }

    private static Stream<Arguments> unknownLevels() {
        return Stream.of(
            Arguments.of(Level.OVERWORLD),
            Arguments.of(Level.NETHER),
            Arguments.of(Level.END),
            Arguments.of(key("othermod:smooth")),
            Arguments.of(key("worldphase:unknown")),
            Arguments.of(key("worldphase:warp")),
            Arguments.of(key("worldphase:stable"))
        );
    }

    private static ResourceKey<Level> key(String identifier) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(identifier));
    }
}
