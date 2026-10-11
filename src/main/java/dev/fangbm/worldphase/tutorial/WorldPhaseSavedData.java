package dev.fangbm.worldphase.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import dev.fangbm.worldphase.tutorial.TutorialOpeningState.*;

/** Global Overworld-owned state. Loading/updating never searches, generates chunks or changes players. */
public final class WorldPhaseSavedData extends SavedData {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("worldphase", "tutorial_opening");
    private static final SavedDataType<WorldPhaseSavedData> TYPE = new SavedDataType<>(ID,
            level -> new WorldPhaseSavedData(Objects.requireNonNull(level).getServer(), TutorialOpeningState.initial()),
            level -> codec(Objects.requireNonNull(level).getServer()));

    // Runtime thread context only; excluded from the codec. No Level/player/search cache is retained.
    private final MinecraftServer server;
    private TutorialOpeningState state;

    private WorldPhaseSavedData(MinecraftServer server, TutorialOpeningState state) {
        this.server = server;
        this.state = state;
    }

    static Codec<WorldPhaseSavedData> codec(MinecraftServer server) {
        var delegate = TutorialOpeningState.CODEC.xmap(state -> new WorldPhaseSavedData(server, state), data -> data.state);
        // SavedDataStorage uses resultOrPartial. Reject error-bearing partial snapshots too,
        // otherwise a corrupt optional candidate can disappear while the load seems successful.
        return new Codec<>() {
            @Override public <T> DataResult<Pair<WorldPhaseSavedData, T>> decode(DynamicOps<T> ops, T input) {
                var result = delegate.decode(ops, input);
                return result.error().isPresent() ? DataResult.error(result.error().get()::message) : result;
            }
            @Override public <T> DataResult<T> encode(WorldPhaseSavedData data, DynamicOps<T> ops, T prefix) {
                return delegate.encode(data, ops, prefix);
            }
        };
    }

    public static WorldPhaseSavedData get(ServerLevel anyLevel) { return get(anyLevel.getServer()); }

    public static WorldPhaseSavedData get(MinecraftServer server) {
        checkThread(server);
        var storage = server.overworld().getDataStorage();
        var loaded = storage.get(TYPE);
        if (loaded != null) return loaded;
        Path file = dataFile(server);
        // Vanilla returns null for BOTH missing and unreadable data. Do not replace a failed load.
        if (!Files.notExists(file))
            throw new IllegalStateException("WorldPhase tutorial data failed to load; preserving " + file
                    + ". Inspect SavedDataStorage errors/schema; no automatic reset or re-search.");
        var fresh = new WorldPhaseSavedData(server, TutorialOpeningState.initial());
        storage.set(TYPE, fresh); // Initial creation is a real persistent change; set() marks dirty.
        return fresh;
    }

    public static Path dataFile(MinecraftServer server) {
        return ID.withSuffix(".dat").resolveAgainst(DimensionType.getStorageFolder(Level.OVERWORLD,
                server.getWorldPath(LevelResource.ROOT)).resolve("data"));
    }

    public TutorialOpeningState state() { checkThread(server); return state; }

    public static Provenance currentProvenance(MinecraftServer server, int centerX, int centerZ, TutorialSearchConfig config) {
        checkThread(server);
        return new Provenance(server.overworld().getSeed(), List.of("fracture", "smooth"), centerX, centerZ, config,
                TutorialCandidateFinder.GENERATION_VERSION, RefinedTutorialSearch.VERSION,
                SharedConstants.getCurrentVersion().name(),
                ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString(),
                PresetIdentity.UNVERIFIED);
    }

    /** Explicitly record a completed search and optional DIAGNOSTIC candidate; never choose its opening. */
    public boolean completeSearch(Provenance provenance, Optional<CandidateMetadata> diagnostic) {
        checkThread(server);
        Objects.requireNonNull(provenance); Objects.requireNonNull(diagnostic);
        requireCurrent(provenance);
        var next = new TutorialOpeningState(TutorialOpeningState.SCHEMA_VERSION, true, state.openingChoice(),
                Optional.of(provenance), diagnostic);
        // Validate before committing anything, including bounds and cross-field invariants.
        TutorialOpeningState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, next).getOrThrow();
        if (state.searchComplete() && !state.equals(next))
            throw new IllegalStateException("Completed search/evidence is already recorded; explicit review/reset is required to replace it");
        return update(next);
    }

    /** Explicit fallback decision, independent of whether search completed or found any diagnostics. */
    public boolean requireControlledOpening() {
        checkThread(server);
        state.provenance().ifPresent(this::requireCurrent);
        if (state.openingChoice() == OpeningChoice.NATURAL_CANDIDATE_SELECTED)
            throw new IllegalStateException("Do not overwrite a recorded explicit natural choice; manual review is required");
        return update(new TutorialOpeningState(state.schemaVersion(), state.searchComplete(),
                OpeningChoice.CONTROLLED_OPENING_REQUIRED, state.provenance(), state.candidate()));
    }

    // Intentionally no natural-selection mutator: no independent route-validation issuer exists yet.
    // A persisted explicit natural choice may be inspected, but must not be treated as usable.
    public boolean canUseNaturalOpening() { checkThread(server); return false; }

    public enum ValidationIssue {
        SEARCH_NOT_COMPLETE, SEED_MISMATCH, PHASE_PAIR_MISMATCH, SEARCH_CONFIG_MISMATCH,
        GENERATION_VERSION_MISMATCH, SCORING_PROTOCOL_MISMATCH, TOOLCHAIN_MISMATCH,
        PRESET_IDENTITY_UNVERIFIABLE, ROUTE_VALIDATION_UNAVAILABLE
    }

    /** No repair/re-search/dirty marking; preserve the recorded choice even if its evidence is stale. */
    public List<ValidationIssue> validationIssues(Provenance expected) {
        checkThread(server);
        var issues = new ArrayList<ValidationIssue>();
        if (state.provenance().isEmpty()) issues.add(ValidationIssue.SEARCH_NOT_COMPLETE);
        else issues.addAll(compare(state.provenance().get(), expected));
        if (!issues.contains(ValidationIssue.PRESET_IDENTITY_UNVERIFIABLE))
            issues.add(ValidationIssue.PRESET_IDENTITY_UNVERIFIABLE);
        issues.add(ValidationIssue.ROUTE_VALIDATION_UNAVAILABLE);
        return List.copyOf(issues);
    }

    private void requireCurrent(Provenance recorded) {
        var expected = currentProvenance(server, recorded.centerX(), recorded.centerZ(), recorded.config());
        var issues = compare(recorded, expected).stream()
                .filter(issue -> issue != ValidationIssue.PRESET_IDENTITY_UNVERIFIABLE).toList();
        if (!issues.isEmpty()) throw new IllegalArgumentException("Tutorial provenance is stale or unsupported: " + issues);
    }

    private static List<ValidationIssue> compare(Provenance saved, Provenance expected) {
        var issues = new ArrayList<ValidationIssue>();
        if (saved.rawSeed() != expected.rawSeed()) issues.add(ValidationIssue.SEED_MISMATCH);
        if (!saved.phasePair().equals(expected.phasePair())) issues.add(ValidationIssue.PHASE_PAIR_MISMATCH);
        if (saved.centerX() != expected.centerX() || saved.centerZ() != expected.centerZ() || !saved.config().equals(expected.config()))
            issues.add(ValidationIssue.SEARCH_CONFIG_MISMATCH);
        if (saved.generationVersion() != expected.generationVersion()) issues.add(ValidationIssue.GENERATION_VERSION_MISMATCH);
        if (saved.scoringProtocolVersion() != expected.scoringProtocolVersion()) issues.add(ValidationIssue.SCORING_PROTOCOL_MISMATCH);
        if (!saved.minecraftVersion().equals(expected.minecraftVersion()) || !saved.neoForgeVersion().equals(expected.neoForgeVersion()))
            issues.add(ValidationIssue.TOOLCHAIN_MISMATCH);
        issues.add(ValidationIssue.PRESET_IDENTITY_UNVERIFIABLE);
        return issues;
    }

    private boolean update(TutorialOpeningState next) {
        if (state.equals(next)) return false;
        state = next; // One immutable snapshot swap after all checks; no partial mutation.
        setDirty();
        return true;
    }

    private static void checkThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("WorldPhase SavedData requires the server thread");
    }
}
