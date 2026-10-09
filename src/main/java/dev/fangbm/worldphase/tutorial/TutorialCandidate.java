package dev.fangbm.worldphase.tutorial;

import java.util.Objects;

/** Immutable primitive metadata only; no Level, player, time or persistence state. */
public record TutorialCandidate(int x, int z, int fractureHeight, int smoothHeight, TutorialScore score) {
    public TutorialCandidate { Objects.requireNonNull(score,"score"); }
}
