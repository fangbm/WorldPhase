package dev.fangbm.worldphase.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persistent facts, not a playable route, live safety certificate or player state. */
public record TutorialOpeningState(int schemaVersion, boolean searchComplete, OpeningChoice openingChoice,
        Optional<Provenance> provenance, Optional<CandidateMetadata> candidate) {
    public static final int SCHEMA_VERSION = 1;

    public enum OpeningChoice { UNDECIDED, CONTROLLED_OPENING_REQUIRED, NATURAL_CANDIDATE_SELECTED }
    public enum Evidence { UNVERIFIED, BOUNDED_SHIFT_SNAPSHOT }
    // No trustworthy loaded-preset fingerprint is computed yet. Never treat this as a match.
    public enum PresetIdentity { UNVERIFIED }

    private static final class MetadataCodecs {
    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try { return DataResult.success(Enum.valueOf(type, name)); }
            catch (IllegalArgumentException e) { return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + name); }
        }, Enum::name);
    }
    }

    public record Provenance(long rawSeed, List<String> phasePair, int centerX, int centerZ,
            TutorialSearchConfig config, int generationVersion, int scoringProtocolVersion,
            String minecraftVersion, String neoForgeVersion, PresetIdentity presetIdentity) {
        public Provenance {
            phasePair = List.copyOf(phasePair);
            Objects.requireNonNull(config);
            Objects.requireNonNull(minecraftVersion);
            Objects.requireNonNull(neoForgeVersion);
            Objects.requireNonNull(presetIdentity);
        }

        private static final Codec<List<String>> PAIR_CODEC = Codec.STRING.listOf().validate(pair ->
                pair.equals(List.of("fracture", "smooth")) ? DataResult.success(pair)
                        : DataResult.error(() -> "Only the ordered fracture/smooth tutorial pair is supported"));
        private static final Codec<String> VERSION_CODEC = Codec.STRING.validate(s -> !s.isBlank()
                ? DataResult.success(s) : DataResult.error(() -> "Toolchain version must not be blank"));
        private static final Codec<TutorialSearchConfig> CONFIG_CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(64, 1024).fieldOf("radius").forGetter(TutorialSearchConfig::radius),
                Codec.intRange(8, 256).fieldOf("candidate_count").forGetter(TutorialSearchConfig::candidateCount)
        ).apply(i, TutorialSearchConfig::new));
        public static final Codec<Provenance> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("raw_seed").forGetter(Provenance::rawSeed),
                PAIR_CODEC.fieldOf("phase_pair").forGetter(Provenance::phasePair),
                Codec.INT.fieldOf("center_x").forGetter(Provenance::centerX),
                Codec.INT.fieldOf("center_z").forGetter(Provenance::centerZ),
                CONFIG_CODEC.fieldOf("search_config").forGetter(Provenance::config),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("generation_version").forGetter(Provenance::generationVersion),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("scoring_protocol_version").forGetter(Provenance::scoringProtocolVersion),
                VERSION_CODEC.fieldOf("minecraft_version").forGetter(Provenance::minecraftVersion),
                VERSION_CODEC.fieldOf("neoforge_version").forGetter(Provenance::neoForgeVersion),
                MetadataCodecs.enumCodec(PresetIdentity.class).fieldOf("preset_identity").forGetter(Provenance::presetIdentity)
        ).apply(i, Provenance::new));
    }

    public record CandidateMetadata(int x, int z, int fractureBaseHeight, int smoothBaseHeight, Evidence evidence) {
        public CandidateMetadata { Objects.requireNonNull(evidence); }
        public static final Codec<CandidateMetadata> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(CandidateMetadata::x),
                Codec.INT.fieldOf("z").forGetter(CandidateMetadata::z),
                Codec.INT.fieldOf("fracture_base_height").forGetter(CandidateMetadata::fractureBaseHeight),
                Codec.INT.fieldOf("smooth_base_height").forGetter(CandidateMetadata::smoothBaseHeight),
                MetadataCodecs.enumCodec(Evidence.class).fieldOf("evidence").forGetter(CandidateMetadata::evidence)
        ).apply(i, CandidateMetadata::new));
    }

    public TutorialOpeningState {
        Objects.requireNonNull(openingChoice);
        Objects.requireNonNull(provenance);
        Objects.requireNonNull(candidate);
    }

    private static final Codec<Integer> SCHEMA_CODEC = Codec.INT.validate(v -> v == SCHEMA_VERSION
            ? DataResult.success(v) : DataResult.error(() -> "Unsupported WorldPhase tutorial schema: " + v));
    public static final Codec<TutorialOpeningState> CODEC = RecordCodecBuilder.<TutorialOpeningState>create(i -> i.group(
            SCHEMA_CODEC.fieldOf("schema_version").forGetter(TutorialOpeningState::schemaVersion),
            Codec.BOOL.fieldOf("tutorial_search_complete").forGetter(TutorialOpeningState::searchComplete),
            MetadataCodecs.enumCodec(OpeningChoice.class).fieldOf("opening_choice").forGetter(TutorialOpeningState::openingChoice),
            Provenance.CODEC.optionalFieldOf("provenance").forGetter(TutorialOpeningState::provenance),
            CandidateMetadata.CODEC.optionalFieldOf("natural_candidate").forGetter(TutorialOpeningState::candidate)
    ).apply(i, TutorialOpeningState::new)).validate(TutorialOpeningState::validate);

    private static DataResult<TutorialOpeningState> validate(TutorialOpeningState state) {
        if (state.searchComplete != state.provenance.isPresent())
            return DataResult.error(() -> "Completed search must have provenance; unfinished search must not");
        if (state.candidate.isPresent() && !state.searchComplete)
            return DataResult.error(() -> "Candidate evidence requires a completed search and provenance");
        if (state.openingChoice == OpeningChoice.NATURAL_CANDIDATE_SELECTED && state.candidate.isEmpty())
            return DataResult.error(() -> "Explicit natural choice requires candidate metadata");
        if (state.candidate.isPresent()) {
            var p = state.provenance.orElseThrow();
            var c = state.candidate.get();
            if (StrictMath.hypot((double)c.x - p.centerX, (double)c.z - p.centerZ) > p.config.radius())
                return DataResult.error(() -> "Candidate lies outside the recorded search radius");
        }
        return DataResult.success(state);
    }

    public static TutorialOpeningState initial() {
        return new TutorialOpeningState(SCHEMA_VERSION, false, OpeningChoice.UNDECIDED, Optional.empty(), Optional.empty());
    }
}
