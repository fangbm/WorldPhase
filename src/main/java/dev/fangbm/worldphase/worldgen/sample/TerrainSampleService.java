package dev.fangbm.worldphase.worldgen.sample;

import dev.fangbm.worldphase.phase.PhaseId;
import dev.fangbm.worldphase.phase.PhaseWorldResolver;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunction;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * Synchronous, server-thread-only sampling of the currently loaded Phase worlds.
 * Create one service per server lifetime. No Level, RandomState or density instance is cached.
 * Every call requires all MVP worlds to be loaded with the same raw seed.
 */
public final class TerrainSampleService {
    // Resource identities only; algorithms, parameters and composition belong to the datapack.
    private static final Map<PhaseId, ResourceKey<DensityFunction>> NOISE_KEYS = Map.of(
            PhaseId.SMOOTH, noiseKey("smooth_noise"),
            PhaseId.RIDGE, noiseKey("ridge_noise"),
            PhaseId.CELL, noiseKey("cell_noise"),
            PhaseId.FRACTURE, noiseKey("fracture_noise"));

    private final MinecraftServer server;

    public TerrainSampleService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /** Configured kernel output (including amplitude), before terrain remapping/composition. */
    public double sampleNoise(PhaseId phase, int x, int z) {
        Objects.requireNonNull(phase, "phase");
        return noise(levels().get(phase), phase, x, z);
    }

    /**
     * Highest motion-blocking base-terrain block y: OCEAN_FLOOR_WG first-free height minus one.
     * Ignores fluid, surface rules, features/trees, structures, entities and player edits.
     * An empty column returns level.getMinY() - 1. Does not request/generate FULL chunks.
     */
    public int sampleHeight(PhaseId phase, int x, int z) {
        Objects.requireNonNull(phase, "phase");
        return height(levels().get(phase), x, z);
    }

    public TerrainSample sample(PhaseId phase, int x, int z) {
        Objects.requireNonNull(phase, "phase");
        return sample(levels().get(phase), phase, x, z);
    }

    public TerrainComparison compare(PhaseId first, PhaseId second, int x, int z) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        var levels = levels();
        var a = sample(levels.get(first), first, x, z);
        var b = first == second ? a : sample(levels.get(second), second, x, z);
        return new TerrainComparison(a, b);
    }

    private EnumMap<PhaseId, ServerLevel> levels() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Terrain sampling must run on the server thread");
        }
        var levels = new EnumMap<PhaseId, ServerLevel>(PhaseId.class);
        Long seed = null;
        for (PhaseId phase : PhaseId.values()) {
            var key = PhaseWorldResolver.levelKey(phase);
            var level = server.getLevel(key);
            if (level == null) throw new IllegalStateException("Phase dimension is not loaded: " + key.identifier());
            if (seed != null && level.getSeed() != seed) {
                throw new IllegalStateException("Phase seeds differ: " + key.identifier() + " has "
                        + level.getSeed() + "; expected shared raw seed " + seed);
            }
            seed = level.getSeed();
            levels.put(phase, level);
        }
        return levels;
    }

    private static TerrainSample sample(ServerLevel level, PhaseId phase, int x, int z) {
        return new TerrainSample(phase, x, z, height(level, x, z), noise(level, phase, x, z));
    }

    private static int height(ServerLevel level, int x, int z) {
        var chunks = level.getChunkSource();
        if (!(chunks.getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("Phase requires a NoiseBasedChunkGenerator: " + level.dimension().identifier());
        }
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, chunks.randomState()) - 1;
    }

    private static double noise(ServerLevel level, PhaseId phase, int x, int z) {
        var key = NOISE_KEYS.get(phase);
        var function = level.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION).get(key)
                .orElseThrow(() -> new IllegalStateException("Missing Phase noise density function: " + key.identifier())).value();
        if (!(function instanceof PhaseNoiseDensityFunction template)) {
            throw new IllegalStateException("Expected worldphase:phase_noise at " + key.identifier());
        }
        return template.withSeed(level.getSeed()).compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    private static ResourceKey<DensityFunction> noiseKey(String path) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath("worldphase", path));
    }
}
