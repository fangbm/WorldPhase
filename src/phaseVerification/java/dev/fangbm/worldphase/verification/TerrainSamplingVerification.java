package dev.fangbm.worldphase.verification;

import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunction;
import dev.fangbm.worldphase.worldgen.sample.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.*;

/** Small real-registry/server-shell check, without FULL chunks, structures or visual tooling. */
final class TerrainSamplingVerification {
    private static final int[][] POINTS = {{0,0},{256,256},{-512,768},{1024,-1024},{2048,2048},
            {-2048,-2048},{4096,0},{0,4096},{-4096,2048},{8192,-4096},{-1000000,2000000}};

    static void verify(MinecraftServer server) {
        try {
            var service = new TerrainSampleService(server);
            var levels = new EnumMap<PhaseId, ServerLevel>(PhaseId.class);
            var counts = new EnumMap<PhaseId, Integer>(PhaseId.class);
            var samples = new EnumMap<PhaseId, List<TerrainSample>>(PhaseId.class);
            var report = new StringBuilder("WPH008 raw seed=123456789; highest solid base y; no FULL chunk requests\n");
            for (PhaseId phase : PhaseId.values()) {
                var level = server.getLevel(PhaseWorldResolver.levelKey(phase));
                require(level != null && level.getSeed() == 123456789L, "Missing Phase or wrong raw seed: " + phase);
                levels.put(phase, level);
                counts.put(phase, level.getChunkSource().getLoadedChunksCount());
                samples.put(phase, new ArrayList<>());
            }
            for (int[] p : POINTS) for (PhaseId phase : PhaseId.values()) {
                var level = levels.get(phase);
                var cache = level.getChunkSource();
                var generator = (NoiseBasedChunkGenerator) cache.getGenerator();
                var key = ResourceKey.create(Registries.DENSITY_FUNCTION,
                        Identifier.fromNamespaceAndPath("worldphase", phase.name().toLowerCase(Locale.ROOT) + "_noise"));
                var template = (PhaseNoiseDensityFunction) level.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION).getOrThrow(key).value();
                var expected = new TerrainSample(phase, p[0], p[1],
                        generator.getBaseHeight(p[0], p[1], Heightmap.Types.OCEAN_FLOOR_WG, level, cache.randomState()) - 1,
                        template.withSeed(level.getSeed()).compute(new DensityFunction.SinglePointContext(p[0],0,p[1])));
                require(expected.equals(service.sample(phase,p[0],p[1])), "Generator/registry cross-check: " + expected);
                require(service.sampleHeight(phase,p[0],p[1]) == expected.baseHeight(), "Height-only entry point");
                require(service.sampleNoise(phase,p[0],p[1]) == expected.rawNoise(), "Noise-only entry point");
                require(Double.isFinite(expected.rawNoise()), "Finite signal");
                samples.get(phase).add(expected);
                report.append(expected).append('\n');
            }
            for (int i = POINTS.length - 1; i >= 0; i--) for (PhaseId phase : PhaseId.values()) {
                var p = POINTS[i];
                require(samples.get(phase).get(i).equals(service.sample(phase,p[0],p[1])), "Reverse-order determinism");
            }
            PhaseId[][] pairs = {{PhaseId.SMOOTH,PhaseId.RIDGE},{PhaseId.SMOOTH,PhaseId.CELL},
                    {PhaseId.SMOOTH,PhaseId.FRACTURE},{PhaseId.RIDGE,PhaseId.CELL}};
            for (var pair : pairs) {
                int differences = 0;
                for (int i = 0; i < POINTS.length; i++) {
                    var p = POINTS[i];
                    var a = samples.get(pair[0]).get(i); var b = samples.get(pair[1]).get(i);
                    var comparison = service.compare(pair[0],pair[1],p[0],p[1]);
                    require(comparison.firstSample().equals(a) && comparison.secondSample().equals(b), "Compare samples");
                    require(comparison.heightDelta() == b.baseHeight() - a.baseHeight()
                            && comparison.noiseDelta() == b.rawNoise() - a.rawNoise(), "Signed compare arithmetic");
                    if (comparison.heightDelta() != 0) differences++;
                }
                require(differences >= 7, "Observable terrain differences");
                report.append(pair[0]).append('/').append(pair[1]).append(" differing heights=").append(differences).append("/11\n");
            }
            for (PhaseId phase : PhaseId.values()) {
                int after = levels.get(phase).getChunkSource().getLoadedChunksCount();
                require(after == counts.get(phase), "Sampling must not load chunks: " + phase);
                report.append(phase).append(" loaded chunks before/after=").append(counts.get(phase)).append('/').append(after).append('\n');
            }
            Files.writeString(Path.of("terrain-sampling-report.txt"), report);
            System.out.print(report);
            Files.writeString(Path.of("terrain-sampling-status.txt"), "PASS\n");
            System.out.println("WPH008 TERRAIN SAMPLING PASS: 44 real-Level cross-checks; repeat/order/compare; loaded chunk counts unchanged");
            server.halt(false);
        } catch (Exception error) {
            try { Files.writeString(Path.of("terrain-sampling-status.txt"), "FAIL " + error); } catch (Exception ignored) {}
            server.halt(false);
            throw new IllegalStateException("WPH008 sampling verification failed", error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
