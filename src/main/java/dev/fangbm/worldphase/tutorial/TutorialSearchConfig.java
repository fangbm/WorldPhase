package dev.fangbm.worldphase.tutorial;

/** Small prototype bounds, not a general terrain-search configuration. */
public record TutorialSearchConfig(int radius, int candidateCount) {
    public static final int DEFAULT_RADIUS = 512;
    public static final int DEFAULT_COUNT = 64;
    public static final int MAX_COUNT = 256;
    public static final int MIN_COUNT = 8;
    public static final int MIN_SPACING = 64;

    public TutorialSearchConfig {
        if (radius < 64 || radius > 1024) throw new IllegalArgumentException("Tutorial radius must be 64..1024 blocks");
        if (candidateCount < MIN_COUNT || candidateCount > MAX_COUNT)
            throw new IllegalArgumentException("Tutorial candidateCount must be 8..256");
    }

    public static TutorialSearchConfig defaults() { return new TutorialSearchConfig(DEFAULT_RADIUS, DEFAULT_COUNT); }
}
