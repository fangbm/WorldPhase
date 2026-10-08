package dev.fangbm.worldphase.worldgen.sample;

import dev.fangbm.worldphase.phase.PhaseId;
import java.util.Objects;

/** A single base-terrain column and its configured, pre-profile kernel signal. */
public record TerrainSample(PhaseId phase, int x, int z, int baseHeight, double rawNoise) {
    public TerrainSample {
        Objects.requireNonNull(phase, "phase");
    }
}
