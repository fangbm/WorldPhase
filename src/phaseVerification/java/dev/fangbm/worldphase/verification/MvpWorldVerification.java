package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

/** Frozen WPH015 measurement points. No preset change, player placement or gameplay feature. */
final class MvpWorldVerification {
    static final long[] SEEDS = {123456789L, 8675309L, 20261011L};
    static final int[][] POINTS = {{464,-128},{480,-160},{-400,-1312},{-1376,-80},{416,-1392},
            {1280,-1488},{1344,1200},{-128,-656},{-384,896},{128,128}};
    static final PhaseId[] PHASES = {PhaseId.FRACTURE,PhaseId.SMOOTH,PhaseId.RIDGE,PhaseId.CELL};
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static void verify(MinecraftServer server) {
        var report = new JsonObject();
        try {
            long start = System.nanoTime();
            report.addProperty("seed", server.overworld().getSeed());
            report.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
            report.addProperty("neoforge", ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());
            report.addProperty("java", System.getProperty("java.runtime.version"));
            report.addProperty("spawn", server.overworld().getRespawnData().pos().toShortString());
            var samples = new JsonArray(); var sampler = new TerrainSampleService(server);
            for (int i=0;i<POINTS.length;i++) for (var phase:PHASES) {
                var level = server.getLevel(PhaseWorldResolver.levelKey(phase));
                if (level == null || level.getSeed()!=server.overworld().getSeed()) throw new IllegalStateException("raw seed/level " + phase);
                int x=POINTS[i][0],z=POINTS[i][1]; var row=new JsonObject();
                var sample=sampler.sample(phase,x,z); row.addProperty("point",i); row.addProperty("phase",phase.name());
                row.addProperty("x",x); row.addProperty("z",z); row.addProperty("baseHeight",sample.baseHeight());
                row.addProperty("rawNoise",sample.rawNoise());
                long chunkStart=System.nanoTime(); level.getChunk(x>>4,z>>4);
                row.addProperty("fullChunkRequestMs",(System.nanoTime()-chunkStart)/1e6);
                int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                row.addProperty("actualHeight",height);
                var pos=new BlockPos(x,height-1,z); row.addProperty("surface",level.getBlockState(pos).toString());
                row.addProperty("biome",level.getBiome(pos).unwrapKey().orElseThrow().identifier().toString());
                samples.add(row);
            }
            report.add("samples",samples);
            var villages=new JsonArray();
            for(var phase:new PhaseId[]{PhaseId.RIDGE,PhaseId.FRACTURE}) {
                var level=server.getLevel(PhaseWorldResolver.levelKey(phase));
                var found=level.findNearestMapStructure(TagKey.create(Registries.STRUCTURE,Identifier.withDefaultNamespace("village")),
                        server.overworld().getRespawnData().pos(),100,false);
                var row=new JsonObject(); row.addProperty("phase",phase.name());
                if(found==null) row.addProperty("status","NOT_LOCATED");
                else {
                    row.addProperty("x",found.getX()); row.addProperty("z",found.getZ());
                    for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) level.getChunk((found.getX()>>4)+dx,(found.getZ()>>4)+dz);
                    row.addProperty("height",level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,found.getX(),found.getZ()));
                    row.addProperty("status","LOCATED_AND_GENERATED_NOT_CERTIFIED_SAFE");
                }
                villages.add(row);
            }
            report.add("villages",villages);
            report.addProperty("workMs",(System.nanoTime()-start)/1e6);
            report.addProperty("heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            report.addProperty("averageTickMs",server.getAverageTickTimeNanos()/1e6);
            report.addProperty("status","PASS");
            var file=server.getWorldPath(LevelResource.ROOT).resolve("mvp-world.json");
            Files.writeString(file,JSON.toJson(report)); Files.writeString(Path.of("mvp-world.json"),JSON.toJson(report));
            server.saveEverything(false,true,true);
        } catch(Exception error) {
            report.addProperty("status","FAIL"); report.addProperty("failure",error.toString());
            try { Files.writeString(Path.of("mvp-world.json"),JSON.toJson(report)); } catch(Exception write) { error.addSuppressed(write); }
            throw new IllegalStateException("WPH015 world measurement failed",error);
        } finally { server.halt(false); }
    }
}
