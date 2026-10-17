package dev.fangbm.worldphase.verification.v2;

import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real server-tick budget observation on legacy worlds only. No V2 generator/settings/Level is installed. */
public final class V2PreparationVerification {
    private final MinecraftServer server;
    private final V2Context context;
    private final JsonObject report=new JsonObject();
    private final long wall=System.nanoTime(),cpu=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
    private final long heapBefore=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    private boolean done;
    private V2PreparationVerification(MinecraftServer server)throws Exception {
        this.server=server;R1.verifyManifest(Path.of(System.getProperty("worldphase.projectRoot")));
        long seed=server.overworld().getSeed();
        if(seed!=123456789L&&seed!=8675309L&&seed!=20261011L)throw new IllegalStateException("Seed not in frozen development set");
        for(var level:server.getAllLevels())if(level.dimension().identifier().getPath().startsWith("prototype_v2"))throw new IllegalStateException("V2 Level forbidden");
        context=new V2Context(seed,R1.rawReference(server.overworld().getChunkSource().randomState().router().continents()));
        report.addProperty("manifest",R1.DIGEST);report.addProperty("seed",seed);report.addProperty("jvm",System.getProperty("java.runtime.version"));
        report.addProperty("mode","dedicated server Post tick; cold certificate/column caches; Vanilla noise JVM already used during legacy spawn");
        report.addProperty("v2WorldsCreated",0);report.addProperty("preparationFullChunkRequests",0);report.addProperty("holdoutsSampled",false);
        report.addProperty("numericGate","BLOCKED_R1_ROUNDED_LIPSCHITZ_CONTRACT");report.addProperty("v2TerrainGenerationAuthorized",false);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    public static void start(MinecraftServer server) {
        try{new V2PreparationVerification(server);}catch(Exception e){throw new IllegalStateException("V2 preparation setup failed",e);}
    }
    private void tick(ServerTickEvent.Post event) {
        if(done||event.getServer()!=server)return;
        try {
            context.step();
            if(context.state()==V2Context.State.READY)finish("COMPLETED",null);
        }catch(RuntimeException e){finish("BLOCKED",e);}
    }
    private void finish(String status,RuntimeException error) {
        done=true;report.addProperty("executionStatus",status);
        if(error!=null)report.addProperty("failure",error.toString());
        report.addProperty("wallNanosIncludingTickGaps",System.nanoTime()-wall);
        report.addProperty("serverThreadCpuNanosIncludingLegacyTicks",ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpu);
        report.addProperty("serverAverageMspt",server.getAverageTickTimeNanos()/1e6);
        report.addProperty("targetNodes",context.targetCount());report.addProperty("completedNodes",context.certificates().size());report.addProperty("rawCalls",context.calls());
        report.addProperty("modelCandidates",context.certificates().values().stream().filter(HcCertificate::modelCandidate).count());
        report.addProperty("enabledNodes",context.certificates().values().stream().filter(HcCertificate::eligible).count());
        report.addProperty("remainingUnexaminedNodes",context.targetCount()-context.certificates().size());
        JsonObject reasons=new JsonObject();context.certificates().values().stream().map(HcCertificate::reason).distinct().sorted().forEach(r->
                reasons.addProperty(r,context.certificates().values().stream().filter(c->c.reason().equals(r)).count()));report.add("rejections",reasons);
        JsonArray ticks=new JsonArray();long sumCpu=0,sumWall=0;for(var t:context.ticks()) {
            JsonObject tick=new JsonObject();tick.addProperty("calls",t.calls());tick.addProperty("wallNanos",t.wallNanos());tick.addProperty("cpuNanos",t.cpuNanos());ticks.add(tick);sumCpu+=t.cpuNanos();sumWall+=t.wallNanos();
        }report.add("ticks",ticks);report.addProperty("preparationStepCpuNanos",sumCpu);report.addProperty("preparationStepWallNanos",sumWall);
        report.addProperty("heapBefore",heapBefore);report.addProperty("heapAfter",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        report.addProperty("certificateCacheEntries",context.certificates().size());report.addProperty("columnCacheEntries",context.cacheStats()[3]);
        context.close();report.addProperty("cacheEntriesAfterClose",context.certificates().size()+context.cacheStats()[3]);
        try {
            Files.writeString(Path.of("v2-preparation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            server.saveEverything(false,true,true);server.halt(false);
        }catch(Exception e){server.halt(false);throw new IllegalStateException("V2 preparation report/save failed",e);}
    }
}
