package dev.fangbm.worldphase.tutorial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.phase.PhaseId;
import dev.fangbm.worldphase.tutorial.TutorialOpeningState.*;
import dev.fangbm.worldphase.tutorial.WorldPhaseSavedData.ValidationIssue;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real 26.1 codecs, compressed NBT and SavedDataStorage; only the server/Level shells are mocked. */
class WorldPhaseSavedDataTest {
    @TempDir Path root;
    MinecraftServer server;
    ServerLevel overworld;
    SavedDataStorage storage;
    Provenance provenance;
    final CandidateMetadata candidate = new CandidateMetadata(-16, 24, 110, 72, Evidence.UNVERIFIED);

    @BeforeEach void setup() throws Exception {
        server = mock(MinecraftServer.class);
        overworld = mock(ServerLevel.class);
        when(server.isSameThread()).thenReturn(true);
        // overworld() is final in 26.1; let that real accessor read the server shell's map.
        var levels = MinecraftServer.class.getDeclaredField("levels"); levels.setAccessible(true);
        levels.set(server, Map.of(Level.OVERWORLD, overworld));
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(root);
        when(overworld.getServer()).thenReturn(server);
        when(overworld.getSeed()).thenReturn(123456789L);
        reopenStorage();
        provenance = WorldPhaseSavedData.currentProvenance(server, 0, 0, TutorialSearchConfig.defaults());
    }

    void reopenStorage() throws Exception {
        Path folder = WorldPhaseSavedData.dataFile(server).getParent().getParent();
        Files.createDirectories(folder);
        storage = new SavedDataStorage(overworld, folder, mock(DataFixer.class), RegistryAccess.EMPTY);
        when(overworld.getDataStorage()).thenReturn(storage);
    }

    @AfterEach void close() { storage.close(); }

    JsonObject json(TutorialOpeningState state) {
        return TutorialOpeningState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
    }

    void disk(JsonObject data) throws Exception {
        var tag = new CompoundTag();
        tag.put("data", JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, data));
        Path file = WorldPhaseSavedData.dataFile(server);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(tag, file);
    }

    TutorialOpeningState searched(OpeningChoice choice) {
        return new TutorialOpeningState(1, true, choice, Optional.of(provenance), Optional.of(candidate));
    }

    @Test void newWorldIsUndecidedAndCreationIsDirty() {
        var data = WorldPhaseSavedData.get(server);
        assertEquals(TutorialOpeningState.initial(), data.state());
        assertTrue(data.isDirty());
        assertFalse(data.canUseNaturalOpening());
    }

    @Test void searchWithTopCandidateDoesNotSelectIt() {
        var data = WorldPhaseSavedData.get(server);
        data.setDirty(false);
        assertTrue(data.completeSearch(provenance, Optional.of(candidate)));
        assertTrue(data.isDirty());
        assertTrue(data.state().searchComplete());
        assertEquals(OpeningChoice.UNDECIDED, data.state().openingChoice());
        assertEquals(candidate, data.state().candidate().orElseThrow());
        assertFalse(data.canUseNaturalOpening());
    }

    @Test void completedSearchWithoutCandidateIsSeparateFromFallback() {
        var data = WorldPhaseSavedData.get(server);
        data.completeSearch(provenance, Optional.empty());
        assertEquals(OpeningChoice.UNDECIDED, data.state().openingChoice());
        assertTrue(data.requireControlledOpening());
        assertEquals(OpeningChoice.CONTROLLED_OPENING_REQUIRED, data.state().openingChoice());
        assertTrue(data.state().candidate().isEmpty());
    }

    @Test void fallbackBeforeSearchRemainsExplicitAfterSearch() {
        var data = WorldPhaseSavedData.get(server);
        data.requireControlledOpening();
        assertFalse(data.state().searchComplete());
        data.completeSearch(provenance, Optional.of(candidate));
        assertEquals(OpeningChoice.CONTROLLED_OPENING_REQUIRED, data.state().openingChoice());
    }

    @Test void identicalUpdatesDoNotDirtyOrWriteAgain() throws Exception {
        var data = WorldPhaseSavedData.get(server);
        data.completeSearch(provenance, Optional.of(candidate));
        data.requireControlledOpening();
        storage.saveAndJoin();
        Path file = WorldPhaseSavedData.dataFile(server);
        var modified = Files.getLastModifiedTime(file);
        assertFalse(data.isDirty());
        assertFalse(data.completeSearch(provenance, Optional.of(candidate)));
        assertFalse(data.requireControlledOpening());
        assertFalse(data.isDirty());
        storage.saveAndJoin();
        assertEquals(modified, Files.getLastModifiedTime(file));
    }

    @Test void compressedNbtStorageReloadKeepsExactCandidateChoiceAndProvenance() throws Exception {
        var before = WorldPhaseSavedData.get(server);
        before.completeSearch(provenance, Optional.of(candidate));
        before.requireControlledOpening();
        var expected = before.state();
        storage.close();
        reopenStorage();
        var after = WorldPhaseSavedData.get(server);
        assertNotSame(before, after);
        assertEquals(expected, after.state());
        assertFalse(after.isDirty());
        assertFalse(after.canUseNaturalOpening());
        assertEquals(List.of(ValidationIssue.PRESET_IDENTITY_UNVERIFIABLE, ValidationIssue.ROUTE_VALIDATION_UNAVAILABLE),
                after.validationIssues(provenance));
    }

    @Test void diagnosticBoundedShiftSnapshotIsNotRouteValidation() throws Exception {
        var snapshot = new CandidateMetadata(-16, 24, 110, 72, Evidence.BOUNDED_SHIFT_SNAPSHOT);
        var data = WorldPhaseSavedData.get(server);
        data.completeSearch(provenance, Optional.of(snapshot));
        storage.close(); reopenStorage();
        assertEquals(snapshot, WorldPhaseSavedData.get(server).state().candidate().orElseThrow());
        assertFalse(WorldPhaseSavedData.get(server).canUseNaturalOpening());
    }

    @Test void differentSecondSearchCannotSilentlyReplaceEvidence() {
        var data = WorldPhaseSavedData.get(server);
        data.completeSearch(provenance, Optional.of(candidate));
        data.requireControlledOpening(); data.setDirty(false);
        var before = data.state();
        var replacement = new CandidateMetadata(32, 48, 120, 80, Evidence.UNVERIFIED);
        assertThrows(IllegalStateException.class, () -> data.completeSearch(provenance, Optional.of(replacement)));
        assertEquals(before, data.state());
        assertFalse(data.isDirty());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 2, 999})
    void unknownSchemaFailsAndOriginalFileIsNotOverwritten(int schema) throws Exception {
        var malformed = json(searched(OpeningChoice.CONTROLLED_OPENING_REQUIRED));
        malformed.addProperty("schema_version", schema);
        assertTrue(TutorialOpeningState.CODEC.parse(JsonOps.INSTANCE, malformed).error().orElseThrow().message().contains("schema"));
        disk(malformed);
        byte[] original = Files.readAllBytes(WorldPhaseSavedData.dataFile(server));
        assertThrows(IllegalStateException.class, () -> WorldPhaseSavedData.get(server));
        storage.saveAndJoin();
        assertArrayEquals(original, Files.readAllBytes(WorldPhaseSavedData.dataFile(server)));
    }

    @ParameterizedTest @ValueSource(strings = {"schema_version", "tutorial_search_complete", "opening_choice", "provenance"})
    void incompleteCompletedRecordFailsClosed(String missing) throws Exception {
        var malformed = json(searched(OpeningChoice.UNDECIDED)); malformed.remove(missing);
        assertTrue(TutorialOpeningState.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent());
        disk(malformed);
        assertThrows(IllegalStateException.class, () -> WorldPhaseSavedData.get(server));
    }

    @ParameterizedTest @ValueSource(strings = {"provenance", "natural_candidate"})
    void corruptOptionalMetadataIsNotSilentlyDropped(String field) throws Exception {
        var malformed = json(searched(OpeningChoice.UNDECIDED)); malformed.addProperty(field, "broken");
        assertTrue(TutorialOpeningState.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent());
        disk(malformed);
        assertThrows(IllegalStateException.class, () -> WorldPhaseSavedData.get(server));
    }

    @Test void brokenCompressedFileIsPreserved() throws Exception {
        Path file = WorldPhaseSavedData.dataFile(server);
        Files.createDirectories(file.getParent());
        byte[] broken = {31, -117, 0, 4, 5}; Files.write(file, broken);
        assertThrows(IllegalStateException.class, () -> WorldPhaseSavedData.get(server));
        storage.saveAndJoin();
        assertArrayEquals(broken, Files.readAllBytes(file));
    }

    @Test void missingDataPayloadDoesNotBecomeFreshState() throws Exception {
        Path file = WorldPhaseSavedData.dataFile(server); Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(new CompoundTag(), file);
        assertThrows(IllegalStateException.class, () -> WorldPhaseSavedData.get(server));
    }

    @Test void rawSeedMismatchPreservesRecordedStateAndRefusesMutation() throws Exception {
        disk(json(searched(OpeningChoice.CONTROLLED_OPENING_REQUIRED)));
        when(overworld.getSeed()).thenReturn(42L);
        var data = WorldPhaseSavedData.get(server);
        var before = data.state();
        var expected = WorldPhaseSavedData.currentProvenance(server, 0, 0, TutorialSearchConfig.defaults());
        assertTrue(data.validationIssues(expected).contains(ValidationIssue.SEED_MISMATCH));
        assertThrows(IllegalArgumentException.class, data::requireControlledOpening);
        assertThrows(IllegalStateException.class, () -> data.completeSearch(expected, Optional.empty()));
        assertEquals(before, data.state()); assertFalse(data.isDirty());
    }

    @ParameterizedTest @ValueSource(strings = {"generation_version", "scoring_protocol_version", "minecraft_version", "neoforge_version"})
    void oldVersionsRemainReadableButUnusableAndCannotBeOverwritten(String field) throws Exception {
        var saved = json(searched(OpeningChoice.CONTROLLED_OPENING_REQUIRED));
        var p = saved.getAsJsonObject("provenance");
        if (field.endsWith("_version") && (field.startsWith("generation") || field.startsWith("scoring"))) p.addProperty(field, 99);
        else p.addProperty(field, "old-toolchain");
        disk(saved);
        var data = WorldPhaseSavedData.get(server);
        assertTrue(data.validationIssues(provenance).size() > 2);
        var before = data.state();
        assertThrows(IllegalArgumentException.class, data::requireControlledOpening);
        assertFalse(data.canUseNaturalOpening());
        assertEquals(before, data.state()); assertFalse(data.isDirty());
    }

    @Test void searchCenterAndConfigMismatchAreReportedWithoutMutation() {
        var data = WorldPhaseSavedData.get(server); data.completeSearch(provenance, Optional.of(candidate));
        data.setDirty(false);
        var different = WorldPhaseSavedData.currentProvenance(server, 10, -20, new TutorialSearchConfig(256, 32));
        assertTrue(data.validationIssues(different).contains(ValidationIssue.SEARCH_CONFIG_MISMATCH));
        assertFalse(data.isDirty());
        assertThrows(IllegalStateException.class, () -> data.completeSearch(different, Optional.of(candidate)));
    }

    @Test void noManualStringCanCertifyPresetOrRoute() {
        var value = json(searched(OpeningChoice.UNDECIDED));
        value.getAsJsonObject("provenance").addProperty("preset_identity", "worldphase-v1");
        assertTrue(TutorialOpeningState.CODEC.parse(JsonOps.INSTANCE, value).error().isPresent());
        value = json(searched(OpeningChoice.UNDECIDED));
        value.getAsJsonObject("natural_candidate").addProperty("evidence", "ROUTE_VALIDATED");
        assertTrue(TutorialOpeningState.CODEC.parse(JsonOps.INSTANCE, value).error().isPresent());
    }

    @Test void historicalExplicitNaturalChoiceIsPreservedButNotUsedOrAutoReplaced() throws Exception {
        disk(json(searched(OpeningChoice.NATURAL_CANDIDATE_SELECTED)));
        var data = WorldPhaseSavedData.get(server);
        assertEquals(OpeningChoice.NATURAL_CANDIDATE_SELECTED, data.state().openingChoice());
        assertEquals(candidate, data.state().candidate().orElseThrow());
        assertFalse(data.canUseNaturalOpening());
        assertThrows(IllegalStateException.class, data::requireControlledOpening);
        assertFalse(data.isDirty());
    }

    @Test void invalidRadiusCandidateUpdateIsAtomic() {
        var data = WorldPhaseSavedData.get(server); data.setDirty(false);
        assertThrows(IllegalStateException.class, () -> data.completeSearch(provenance,
                Optional.of(new CandidateMetadata(513, 0, 70, 80, Evidence.UNVERIFIED))));
        assertEquals(TutorialOpeningState.initial(), data.state()); assertFalse(data.isDirty());
    }

    @Test void everyPhaseUsesSingleOverworldStateWithoutChunkOrPlayerAccess() {
        var data = WorldPhaseSavedData.get(server);
        for (PhaseId phase : PhaseId.values()) {
            var level = mock(ServerLevel.class); when(level.getServer()).thenReturn(server);
            assertSame(data, WorldPhaseSavedData.get(level));
            verify(level, never()).getDataStorage();
            verify(level, never()).getChunkSource();
        }
        data.completeSearch(provenance, Optional.of(candidate)); data.requireControlledOpening();
        verify(overworld, never()).getChunkSource();
        verify(server, never()).getPlayerList();
        var serialized = json(data.state()).toString();
        for (String forbidden : List.of("player", "uuid", "current_phase", "yaw", "pitch", "inventory", "Level"))
            assertFalse(serialized.contains(forbidden), forbidden);
        assertEquals(root.resolve("dimensions/minecraft/overworld/data/worldphase/tutorial_opening.dat"), WorldPhaseSavedData.dataFile(server));
    }

    @Test void offServerThreadReadAndWriteAreRejectedBeforeChanges() {
        var data = WorldPhaseSavedData.get(server); data.setDirty(false);
        when(server.isSameThread()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> WorldPhaseSavedData.get(server));
        assertThrows(IllegalStateException.class, data::state);
        assertThrows(IllegalStateException.class, data::requireControlledOpening);
        assertThrows(IllegalStateException.class, () -> data.completeSearch(provenance, Optional.of(candidate)));
        assertFalse(data.isDirty());
    }
}
