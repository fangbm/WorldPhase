package dev.fangbm.worldphase.worldgen.sample;

import java.util.Objects;

/** Same-coordinate samples. Both deltas are second minus first, never absolute values. */
public record TerrainComparison(TerrainSample firstSample, TerrainSample secondSample) {
    public TerrainComparison {
        Objects.requireNonNull(firstSample, "firstSample");
        Objects.requireNonNull(secondSample, "secondSample");
        if (firstSample.x() != secondSample.x() || firstSample.z() != secondSample.z()) {
            throw new IllegalArgumentException("Terrain comparison requires identical x/z coordinates");
        }
    }

    public int heightDelta() { return secondSample.baseHeight() - firstSample.baseHeight(); }

    public double noiseDelta() { return secondSample.rawNoise() - firstSample.rawNoise(); }
}
