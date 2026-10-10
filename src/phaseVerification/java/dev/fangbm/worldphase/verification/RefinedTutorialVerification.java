package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.tutorial.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Explicit v4 experiment, original ten plus independent holdout. Not a normal startup search. */
final class RefinedTutorialVerification {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final MinecraftServer server;
    private final TutorialCandidateFinder finder;
    private final JsonObject report=new JsonObject();
    private final Map<PhaseId,Integer> initialChunks=new EnumMap<>(PhaseId.class);
    private final List<String> messages=new ArrayList<>();
    private RefinedTutorialSearch search;
    private RefinedTutorialSearch.Result first;
    private int stage,ticks,cooldown;
    private long started;
    private CommandSourceStack source;
    private final int commandCount;

    private RefinedTutorialVerification(MinecraftServer server) {
        this.server=server; finder=new TutorialCandidateFinder(server);
        long seed=server.overworld().getSeed(); commandCount=seed==0?64:seed==Long.MIN_VALUE?256:0;
        var center=server.overworld().getRespawnData().pos();
        report.addProperty("seed",seed); report.addProperty("centerX",center.getX()); report.addProperty("centerZ",center.getZ());
        report.addProperty("version",4); report.addProperty("radius",512); report.addProperty("candidateCount",64);
        report.addProperty("minecraft",net.minecraft.SharedConstants.getCurrentVersion().name());
        report.addProperty("neoforge",net.neoforged.fml.ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());
        report.addProperty("java",System.getProperty("java.runtime.version"));
        for(var phase:PhaseId.values()) initialChunks.put(phase,server.getLevel(PhaseWorldResolver.levelKey(phase)).getChunkSource().getLoadedChunksCount());
        search=finder.beginRefined(center.getX(),center.getZ(),TutorialSearchConfig.defaults()); started=System.nanoTime();
    }
    static void start(MinecraftServer server) {
        var probe=new RefinedTutorialVerification(server); NeoForge.EVENT_BUS.addListener(probe::tick);
        System.out.println("WPH013 V4 START seed="+server.overworld().getSeed());
    }
    private void tick(ServerTickEvent.Post event) {
        if(event.getServer()!=server || stage==6) return;
        try {
            require(++ticks<=4000,"probe.tick_limit");
            if(stage==0 || stage==1 || stage==2) {
                if(stage==0) search.advance(2);
                else for(int i=0;i<4 && !search.done();i++) search.advance(4); // Isolated repeat/max only.
                if(!search.done()) return;
                var result=search.result();
                require(result.heightSamples()<=RefinedTutorialSearch.maximumHeightCalls(result.config().candidateCount()),"budget.height_calls");
                if(stage==0) {
                    first=result; report.add("defaultSearch",metrics(result,System.nanoTime()-started));
                    report.add("top5",JSON.toJsonTree(result.candidates().stream().limit(5).toList()));
                    report.add("analyzed",JSON.toJsonTree(result.analyzed())); crossCheck();
                    stage=1; search=finder.beginRefined(first.centerX(),first.centerZ(),TutorialSearchConfig.defaults());
                } else if(stage==1) {
                    require(result.candidates().equals(first.candidates()) && result.analyzed().equals(first.analyzed()),"ranking.repeat");
                    report.addProperty("repeatRanking","PASS");
                    if(commandCount==256) { stage=2; search=finder.beginRefined(first.centerX(),first.centerZ(),new TutorialSearchConfig(512,256)); started=System.nanoTime(); }
                    else { stage=3; cooldown=200; }
                } else { report.add("maximumSearch",metrics(result,System.nanoTime()-started)); stage=3; cooldown=200; }
                return;
            }
            if(stage==3) {
                if(commandCount==0) { finish(); return; }
                if(--cooldown>0) return; // Drain dev audit/repeat tick debt before measuring actual commands.
                startCommands(); stage=4; return;
            }
            if(stage==4) {
                if(messages.stream().noneMatch(m->m.contains("tutorial score seed="))) return;
                require(server.getCommands().getDispatcher().execute("wp tutorial find "+commandCount,source)==1,"command.find");
                stage=5; return;
            }
            if(stage==5 && messages.stream().anyMatch(m->m.contains("tutorial find seed=") && m.contains("evaluated="+commandCount))) {
                require(messages.stream().anyMatch(m->m.contains("INTERESTING_BUT_SHIFT_UNVERIFIED") || m.contains("INSUFFICIENT_DIRECTIONAL_CONTRAST")),"command.unverified-label");
                require(source.getPlayerOrException().position().equals(source.getPosition()),"command.moved-player");
                report.add("commandOutput",JSON.toJsonTree(messages)); finish();
            }
        } catch(Exception failure) {
            report.addProperty("status","FAIL"); report.addProperty("failure",failure.toString());
            try { write(); } catch(Exception writing) { failure.addSuppressed(writing); }
            stage=6; server.halt(false); throw new IllegalStateException("WPH013 v4 failed",failure);
        }
    }
    private void crossCheck() {
        for(var candidate:first.analyzed()) {
            int[][] heights=new int[2][33];
            for(int phase=0;phase<2;phase++) {
                var level=server.getLevel(PhaseWorldResolver.levelKey(phase==0?PhaseId.FRACTURE:PhaseId.SMOOTH));
                for(int i=0;i<33;i++) { var o=TutorialCorridor.OFFSETS.get(i);
                    heights[phase][i]=level.getChunkSource().getGenerator().getBaseHeight(candidate.candidate().x()+o.x(),candidate.candidate().z()+o.z(),Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1; }
                if(phase==1) require(TutorialCorridor.evaluate(candidate.candidate().x(),candidate.candidate().z(),heights[0],heights[1],candidate.candidate().score().spawnDistance(),512,level.getSeaLevel(),level.getMinY(),level.getMaxY()).equals(candidate),"generator.corridor-evidence");
            }
        }
        report.addProperty("generatorCrossCheck","PASS");
    }
    private void startCommands() throws Exception {
        var player=PhaseTransferVerification.createPlayer(server,server.overworld(),new GameProfile(UUID.fromString("a3b83e90-28f9-4a94-b89c-8c46af811413"),"V4Command"),new Vec3(first.centerX()+.125,200.5,first.centerZ()+.9375));
        CommandSource sink=new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message.getString()); }
            public boolean acceptsSuccess() { return true; } public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        source=new CommandSourceStack(sink,player.position(),Vec2.ZERO,server.overworld(),LevelBasedPermissionSet.GAMEMASTER,"V4",Component.literal("V4"),server,player);
        var ambiguities=new ArrayList<String>();
        server.getCommands().getDispatcher().findAmbiguities((parent,child,sibling,inputs)-> {
            if(server.getCommands().getDispatcher().getPath(parent).stream().findFirst().orElse("").equals("wp")) ambiguities.add(inputs.toString()); });
        require(ambiguities.isEmpty(),"command.ambiguity");
        require(server.getCommands().getDispatcher().execute("wp tutorial score",source)==1,"command.score");
    }
    private void finish() throws Exception {
        for(var phase:PhaseId.values()) require(server.getLevel(PhaseWorldResolver.levelKey(phase)).getChunkSource().getLoadedChunksCount()==initialChunks.get(phase),"search.requested-full-chunks");
        report.addProperty("baseSearchFullChunkRequests",0);
        var oracle=new TutorialCorridorVerification(server); var observations=new JsonArray();
        for(int i=0;i<Math.min(3,first.candidates().size());i++) observations.add(oracle.observe(first.candidates().get(i),i));
        require(oracle.fullRequests()<=96,"validation.full-footprint-limit");
        report.add("observations",observations); report.addProperty("validationFullRequests",oracle.fullRequests());
        report.addProperty("usefulTop1",!observations.isEmpty() && observations.get(0).getAsJsonObject().get("usefulActual").getAsBoolean());
        report.addProperty("shiftTop1",!observations.isEmpty() && observations.get(0).getAsJsonObject().get("boundedShiftProved").getAsBoolean());
        boolean anyUseful=false,anyShift=false;
        for(var observed:observations) { anyUseful|=observed.getAsJsonObject().get("usefulActual").getAsBoolean(); anyShift|=observed.getAsJsonObject().get("boundedShiftProved").getAsBoolean(); }
        report.addProperty("usefulTop3",anyUseful); report.addProperty("shiftTop3",anyShift);
        report.addProperty("status","PASS"); write(); stage=6;
        System.out.println("WPH013 V4 PASS seed="+first.seed()+" usefulTop1="+anyTop1()+" usefulTop3="+anyUseful+" shiftTop3="+anyShift); server.halt(false);
    }
    private boolean anyTop1() { return report.get("usefulTop1").getAsBoolean(); }
    private static JsonObject metrics(RefinedTutorialSearch.Result r,long wall) {
        var result=new JsonObject(); result.addProperty("heightSamples",r.heightSamples()); result.addProperty("ticks",r.batches());
        result.addProperty("centers",r.evaluatedCount()); result.addProperty("cardinal",r.cardinalCount()); result.addProperty("fine",r.analyzed().size());
        result.addProperty("computeMs",r.computeNanos()/1e6); result.addProperty("cpuMs",r.cpuNanos()<0?-1:r.cpuNanos()/1e6);
        result.addProperty("wallMs",wall/1e6); result.addProperty("maxBatchMs",r.maxBatchNanos()/1e6); return result;
    }
    private void write() throws Exception { Files.writeString(Path.of("refined-report.json"),JSON.toJson(report)); }
    private static void require(boolean condition,String name) { if(!condition) throw new IllegalStateException(name); }
}
