package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import dev.fangbm.worldphase.phase.*;
import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Thirty seconds of real village/entity ticks; no repair, relocation or artificial villagers. */
final class MvpVillageVerification {
    private final MinecraftServer server;
    private final JsonObject report=new JsonObject();
    private final JsonArray metrics=new JsonArray(), observations=new JsonArray();
    private final JsonArray villages;
    private final Set<String> embedded=new TreeSet<>();
    private final Map<String,Float> firstHealth=new TreeMap<>(),minHealth=new TreeMap<>();
    private int ticks;

    static void start(MinecraftServer server) {
        try {new MvpVillageVerification(server);} catch(Exception error) {server.halt(false);throw new IllegalStateException(error);}
    }
    private MvpVillageVerification(MinecraftServer server) throws Exception {
        this.server=server;
        var world=JsonParser.parseString(Files.readString(Path.of("mvp-world.json"))).getAsJsonObject();
        villages=world.getAsJsonArray("villages");report.addProperty("seed",server.overworld().getSeed());
        report.add("metrics",metrics);report.add("villagerSnapshots",observations);
        for(var element:villages) {
            var row=element.getAsJsonObject();if(!row.has("x"))continue;
            var level=server.getLevel(PhaseWorldResolver.levelKey(PhaseId.valueOf(row.get("phase").getAsString())));
            int x=row.get("x").getAsInt(),z=row.get("z").getAsInt();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level),
                    "forceload add "+(x-48)+" "+(z-48)+" "+(x+64)+" "+(z+64));
        }
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ServerTickEvent.Post event) {
        if(event.getServer()!=server)return;
        try {
            ticks++;
            if(ticks%20==0) {
                var metric=new JsonObject();metric.addProperty("tick",ticks);
                metric.addProperty("averageTickMs",server.getAverageTickTimeNanos()/1e6);
                metric.addProperty("heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());metrics.add(metric);
                for(var element:villages) {
                    var village=element.getAsJsonObject();if(!village.has("x"))continue;
                    var phase=PhaseId.valueOf(village.get("phase").getAsString());var level=server.getLevel(PhaseWorldResolver.levelKey(phase));
                    int x=village.get("x").getAsInt(),z=village.get("z").getAsInt();
                    for(var entity:level.getEntitiesOfClass(Villager.class,new AABB(x-96,level.getMinY(),z-96,x+96,level.getMaxY(),z+96))) {
                        String id=phase+":"+entity.getUUID();firstHealth.putIfAbsent(id,entity.getHealth());
                        minHealth.merge(id,entity.getHealth(),Math::min);
                        if(entity.isInWall()) embedded.add(id);
                        if(ticks==20 || ticks==600 || entity.isInWall()) {
                            var row=new JsonObject();row.addProperty("tick",ticks);row.addProperty("phase",phase.name());row.addProperty("uuid",entity.getUUID().toString());
                            row.addProperty("position",entity.position().toString());row.addProperty("health",entity.getHealth());row.addProperty("isInWall",entity.isInWall());observations.add(row);
                        }
                    }
                }
            }
            if(ticks<600)return;
            report.addProperty("ticks",ticks);report.addProperty("observedVillagers",firstHealth.size());
            report.add("inWallObserved",MvpWorldVerification.JSON.toJsonTree(embedded));
            report.add("healthDecreased",MvpWorldVerification.JSON.toJsonTree(firstHealth.keySet().stream().filter(k->minHealth.get(k)<firstHealth.get(k)).toList()));
            report.addProperty("status","OBSERVATION_COMPLETED_NOT_STRUCTURE_SAFETY_CERTIFICATION");
            Files.writeString(Path.of("mvp-villages.json"),MvpWorldVerification.JSON.toJson(report));server.saveEverything(false,true,true);server.halt(false);
        } catch(Exception error) {
            report.addProperty("status","FAIL");report.addProperty("failure",error.toString());
            try {Files.writeString(Path.of("mvp-villages.json"),MvpWorldVerification.JSON.toJson(report));}catch(Exception writing){error.addSuppressed(writing);}
            server.halt(false);throw new IllegalStateException(error);
        }
    }
}
