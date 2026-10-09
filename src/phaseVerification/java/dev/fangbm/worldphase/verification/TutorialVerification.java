package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import dev.fangbm.worldphase.tutorial.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Explicit dev-only multi-seed acceptance. Basic search and expensive FULL observations are separate. */
final class TutorialVerification {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final MinecraftServer server;
    private final TutorialCandidateFinder finder;
    private final JsonObject report=new JsonObject();
    private final List<String> commandMessages=new ArrayList<>();
    private final Map<PhaseId,Integer> initialChunks=new EnumMap<>(PhaseId.class);
    private TutorialCandidateFinder.Search search;
    private TutorialCandidateFinder.Result first;
    private int stage,ticks;
    private CommandSourceStack commandSource;
    private boolean findSubmitted;
    private long started;
    private final int commandCount;
    private final boolean attribution="attribution".equals(System.getProperty("worldphase.tutorial.protocol"));
    private List<TutorialCandidateFinder.Evaluation> pool;

    private TutorialVerification(MinecraftServer server) {
        this.server=server; finder=new TutorialCandidateFinder(server);
        commandCount=server.overworld().getSeed()==Long.MAX_VALUE?256:8;
        var center=server.overworld().getRespawnData().pos();
        report.addProperty("seed",server.overworld().getSeed()); report.addProperty("centerX",center.getX()); report.addProperty("centerZ",center.getZ());
        report.addProperty("radius",512); report.addProperty("candidateCount",64);
        report.addProperty("generationVersion",TutorialCandidateFinder.GENERATION_VERSION);
        report.addProperty("minecraft",net.minecraft.SharedConstants.getCurrentVersion().name());
        report.addProperty("neoforge",net.neoforged.fml.ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());
        report.addProperty("java",System.getProperty("java.runtime.version"));
        for(var phase:PhaseId.values()) initialChunks.put(phase,level(phase).getChunkSource().getLoadedChunksCount());
        search=finder.begin(center.getX(),center.getZ(),TutorialSearchConfig.defaults()); started=System.nanoTime();
    }
    static void start(MinecraftServer server) {
        if("refined".equals(System.getProperty("worldphase.tutorial.protocol"))) { RefinedTutorialVerification.start(server); return; }
        var probe=new TutorialVerification(server); NeoForge.EVENT_BUS.addListener(probe::tick);
        System.out.println("WPH013 START seed="+server.overworld().getSeed()+" center="+server.overworld().getRespawnData().pos());
    }

    private void tick(ServerTickEvent.Post event) {
        if(event.getServer()!=server || stage==4) return;
        try {
            if(stage<3) {
                // Explicit isolated dev workload runs bounded batches back-to-back for measurement.
                // Production command remains at most two height columns per real server tick.
                for(int batch=0;batch<16 && !search.done();batch++) search.advance(4);
                if(!search.done()) return;
                var result=search.result();
                if(stage==0) {
                    first=result; report.add("defaultSearch",metrics(result,System.nanoTime()-started));
                    if(attribution) { pool=search.diagnostics(); report.add("legacyPool",JSON.toJsonTree(pool)); }
                    var rows=new JsonArray();
                    for(var candidate:result.candidates().stream().limit(5).toList()) { crossCheck(candidate); rows.add(candidateJson(candidate)); }
                    report.add("top5",rows);
                    System.out.println("WPH013 Top5 seed="+result.seed()+" "+rows);
                } else if(stage==1) {
                    require(result.candidates().equals(first.candidates()),"ranking.repeat: candidate/ranking changed");
                    report.addProperty("repeatRanking","PASS");
                    if(attribution) {
                        noChunks(); report.addProperty("baseSearchFullChunkRequests",0);
                        observeAttribution(); report.addProperty("status","PASS"); write(); stage=4;
                        System.out.println("WPH013 ATTRIBUTION PASS seed="+first.seed()); server.halt(false); return;
                    }
                } else {
                    report.add("maximumSearch",metrics(result,System.nanoTime()-started));
                    noChunks(); report.addProperty("baseSearchFullChunkRequests",0);
                    beginCommands(); stage++; return;
                }
                stage++; var config=stage==2?new TutorialSearchConfig(512,256):TutorialSearchConfig.defaults();
                search=finder.begin(first.centerX(),first.centerZ(),config); started=System.nanoTime(); return;
            }
            // Production handler has already advanced two height columns on this real tick.
            require(++ticks<=2000,"command.timeout");
            if(!findSubmitted) {
                if(commandMessages.stream().noneMatch(s->s.contains("tutorial score seed="))) return;
                require(server.getCommands().getDispatcher().execute("wp tutorial find "+commandCount,commandSource)==1,"command.find");
                findSubmitted=true; return;
            }
            if(commandMessages.stream().noneMatch(s->s.contains("tutorial find seed=") && s.contains("evaluated="+commandCount))) return;
            require(commandMessages.stream().anyMatch(s->s.contains("evaluated="+commandCount)),"command.find: did not finish");
            require(commandMessages.stream().anyMatch(s->s.contains("hazardPenalty=")),"command.components: missing output");
            noChunks(); report.add("commandOutput",JSON.toJsonTree(commandMessages));
            report.addProperty("commandFindCount",commandCount);
            observe(); report.addProperty("status","PASS"); write(); stage=4;
            System.out.println("WPH013 PASS seed="+first.seed()+" usefulTop1="+report.get("usefulTop1")+" report=tutorial-report.json");
            server.halt(false);
        } catch(Exception failure) {
            report.addProperty("status","FAIL"); report.addProperty("failure",failure.toString());
            try { write(); } catch(Exception writing) { failure.addSuppressed(writing); }
            stage=4; server.halt(false); throw new IllegalStateException("WPH013 probe failed at stage="+stage,failure);
        }
    }

    private void beginCommands() throws Exception {
        var player=PhaseTransferVerification.createPlayer(server,server.overworld(),
                new GameProfile(UUID.fromString("a3b83e90-28f9-4a94-b89c-8c46af811013"),"WPH013Probe"),new Vec3(first.centerX()+.125,200.5,first.centerZ()+.9375));
        var original=player.position(); var originalLevel=player.level();
        CommandSource sink=new CommandSource() {
            public void sendSystemMessage(Component message) { commandMessages.add(message.getString()); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        var source=new CommandSourceStack(sink,original,Vec2.ZERO,server.overworld(),LevelBasedPermissionSet.GAMEMASTER,"Probe",Component.literal("Probe"),server,player);
        var dispatcher=server.getCommands().getDispatcher();
        var ambiguous=new ArrayList<String>(); dispatcher.findAmbiguities((parent,child,sibling,inputs)-> {
            if(dispatcher.getPath(parent).stream().findFirst().orElse("").equals("wp")) ambiguous.add(inputs.toString());
        }); require(ambiguous.isEmpty(),"command.ambiguity: "+ambiguous);
        require(dispatcher.execute("wp tutorial score",source)==1,"command.score");
        commandSource=source;
        require(player.level()==originalLevel && player.position().equals(original),"command.moved_player");
    }

    private void crossCheck(TutorialCandidate candidate) {
        for(var phase:List.of(PhaseId.FRACTURE,PhaseId.SMOOTH)) {
            var level=level(phase); var chunks=level.getChunkSource();
            int[] heights=new int[9];
            for(int i=0;i<9;i++) {
                var offset=TutorialScore.OFFSETS.get(i);
                heights[i]=chunks.getGenerator().getBaseHeight(candidate.x()+offset.x(),candidate.z()+offset.z(),Heightmap.Types.OCEAN_FLOOR_WG,level,chunks.randomState())-1;
            }
            require(heights[0]==(phase==PhaseId.FRACTURE?candidate.fractureHeight():candidate.smoothHeight()),"generator.base_height: "+candidate);
            double slope=0;
            for(int i=1;i<9;i++) { var o=TutorialScore.OFFSETS.get(i); slope=Math.max(slope,Math.abs((double)heights[i]-heights[0])/StrictMath.hypot(o.x(),o.z())); }
            require(slope==(phase==PhaseId.FRACTURE?candidate.score().fractureSlope():candidate.score().smoothSlope()),"generator.local_slope: "+candidate);
        }
    }

    private void observe() {
        var observations=new JsonArray();
        var top=first.candidates().getFirst(); observations.add(observation(top,"top1"));
        // Two predeclared seeds also inspect a low-ranked candidate, not a post-hoc ranking rescue.
        if(first.seed()==0 || first.seed()==42) observations.add(observation(first.candidates().getLast(),"low-ranked"));
        report.add("observations",observations);
        report.addProperty("usefulTop1",observations.get(0).getAsJsonObject().get("usefulActual").getAsBoolean());
    }

    private void observeAttribution() {
        var observations=new JsonArray(); var inspected=new HashSet<TutorialCandidate>();
        for(int i=0;i<Math.min(3,first.candidates().size());i++) {
            var candidate=first.candidates().get(i); inspected.add(candidate);
            observations.add(observation(candidate,"old-top"+(i+1)));
        }
        // Fixed one-candidate audit of a shape-qualified coast hint, never a complete pool oracle.
        pool.stream().map(TutorialCandidateFinder.Evaluation::candidate)
                .filter(c->!inspected.contains(c) && c.score().heightContrast()>=4 && c.score().fractureSlope()>=.375
                        && c.score().smoothSlope()<=.25 && c.score().slopeContrast()>=.25
                        && c.score().hazardPenalty()>0 && c.score().hazardPenalty()<=120
                        && c.score().fractureSlope()<=2 && c.score().smoothSlope()<=2)
                .sorted(Comparator.comparingDouble((TutorialCandidate c)->c.score().totalScore()).reversed()
                        .thenComparingInt(TutorialCandidate::x).thenComparingInt(TutorialCandidate::z))
                .findFirst().ifPresent(c->observations.add(observation(c,"coastal-audit")));
        report.add("observations",observations);
        report.addProperty("usefulTop1",observations.get(0).getAsJsonObject().get("usefulActual").getAsBoolean());
        report.addProperty("auditLimit",4);
    }

    private JsonObject observation(TutorialCandidate candidate,String label) {
        var result=new JsonObject(); result.addProperty("label",label); result.add("candidate",candidateJson(candidate));
        var probe=PhaseTransferVerification.createPlayer(server,server.overworld(),
                new GameProfile(UUID.fromString("a3b83e90-28f9-4a94-b89c-8c46af811113"),"WPH013Safety"),Vec3.ZERO);
        int[][] local=new int[2][9]; boolean safe=true; int phaseIndex=0;
        for(var phase:List.of(PhaseId.FRACTURE,PhaseId.SMOOTH)) {
            var level=level(phase); var data=new JsonObject(); var heights=new JsonArray(); var blocks=new JsonArray();
            // Explicit FULL chunk observation: footprint radius16, separate from scoring workload.
            for(int cx=(candidate.x()-18)>>4;cx<=(candidate.x()+18)>>4;cx++)
                for(int cz=(candidate.z()-18)>>4;cz<=(candidate.z()+18)>>4;cz++) level.getChunk(cx,cz);
            for(int dz=-16;dz<=16;dz++) {
                var row=new JsonArray(); var blockRow=new JsonArray();
                for(int dx=-16;dx<=16;dx++) {
                    int x=candidate.x()+dx,z=candidate.z()+dz,y=ground(level,x,z);
                    row.add(y); blockRow.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x,y,z)).getBlock()).toString());
                }
                heights.add(row); blocks.add(blockRow);
            }
            data.add("groundHeights",heights); data.add("groundBlocks",blocks);
            for(int i=0;i<9;i++) { var o=TutorialScore.OFFSETS.get(i); local[phaseIndex][i]=ground(level,candidate.x()+o.x(),candidate.z()+o.z()); }
            var foot=new Vec3(candidate.x()+.5,local[phaseIndex][0]+1,candidate.z()+.5);
            boolean centerSafe=LandingSafety.isSafe(probe,level,foot,TransferPolicy.BOUNDED_LANDING);
            safe &= centerSafe; data.addProperty("centerLandingSafe",centerSafe); result.add(phase.name(),data); phaseIndex++;
            if(attribution) {
                var stencilSafe=new JsonArray();
                for(int i=0;i<9;i++) { var o=TutorialScore.OFFSETS.get(i);
                    stencilSafe.add(LandingSafety.isSafe(probe,level,new Vec3(candidate.x()+o.x()+.5,local[phaseIndex-1][i]+1,candidate.z()+o.z()+.5),TransferPolicy.BOUNDED_LANDING)); }
                data.add("stencilStandingSafe",stencilSafe);
            }
        }
        var level=level(PhaseId.FRACTURE);
        var actual=TutorialScore.evaluate(local[0],local[1],candidate.score().spawnDistance(),512,level.getSeaLevel(),level.getMinY(),level.getMaxY());
        result.add("actualScore",JSON.toJsonTree(actual)); result.addProperty("usefulActual",actual.usefulHeuristic(512)&&safe);
        result.addProperty("usefulBase",candidate.score().usefulHeuristic(512));
        return result;
    }

    private static int ground(ServerLevel level,int x,int z) {
        // The observation footprint is already explicitly FULL. Skip canopy/trunk for actual ground shape.
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        while(y>=level.getMinY()) {
            var state=level.getBlockState(new BlockPos(x,y,z));
            if(!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && Heightmap.Types.OCEAN_FLOOR.isOpaque().test(state)) break;
            y--;
        }
        return y;
    }
    private void noChunks() {
        for(var phase:PhaseId.values()) require(level(phase).getChunkSource().getLoadedChunksCount()==initialChunks.get(phase),"search.requested_full_chunks: "+phase);
    }
    private ServerLevel level(PhaseId phase) { return Objects.requireNonNull(server.getLevel(PhaseWorldResolver.levelKey(phase))); }
    private static JsonObject metrics(TutorialCandidateFinder.Result result,long wallNanos) {
        var data=new JsonObject(); data.addProperty("evaluated",result.evaluatedCount()); data.addProperty("heightSamples",result.heightSamples());
        data.addProperty("computeMs",result.computeNanos()/1e6); data.addProperty("wallMs",wallNanos/1e6); data.addProperty("maxBatchMs",result.maxBatchNanos()/1e6);
        return data;
    }
    private void write() throws Exception { Files.writeString(Path.of(attribution?"attribution-report.json":"tutorial-report.json"),JSON.toJson(report)); }
    private static JsonObject candidateJson(TutorialCandidate candidate) {
        var json=JSON.toJsonTree(candidate).getAsJsonObject();
        json.addProperty("totalScore",candidate.score().totalScore());
        json.addProperty("usefulHeuristic",candidate.score().usefulHeuristic(512)); return json;
    }
    private static void require(boolean condition,String detail) { if(!condition) throw new IllegalStateException(detail); }
}
