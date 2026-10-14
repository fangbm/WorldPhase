package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Supplemental native Survival exploration. Not human play and not a production pathfinder. */
final class MvpExplorationClient {
    private final JsonObject report=new JsonObject();
    private final JsonArray caves=new JsonArray(),metrics=new JsonArray(),segments=new JsonArray();
    private final String runId=System.getProperty("worldphase.mvp.supplement");
    private volatile boolean busy;
    private volatile Throwable error;
    private volatile JsonObject reply;
    private List<Vec3> route=List.of();
    private JsonObject cave,travel;
    private int ticks,stage,step,wait,waypoint,walkTicks,phaseIndex,totalInspected,totalWaypoints;
    private boolean started,ending,returning,checkpointRequested;
    private double travelX,travelZ;
    private final Set<BlockPos> blockedWaypoints=new LinkedHashSet<>();
    private final long startedAt=System.currentTimeMillis();
    private final PhaseId travelPhase=PhaseId.valueOf(System.getProperty("worldphase.mvp.travelPhase","SMOOTH"));
    private final java.util.concurrent.atomic.AtomicLong newChunks=new java.util.concurrent.atomic.AtomicLong(),clientChunks=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicBoolean walking=new java.util.concurrent.atomic.AtomicBoolean();
    private long travelNewStart,travelClientStart,lastTickNanos;private double maxTickGap;

    MvpExplorationClient(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::chunkLoaded);}
    private void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event){
        if(!walking.get())return;
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel && event.isNewChunk())newChunks.incrementAndGet();
        else if(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)clientChunks.incrementAndGet();
    }

    private void request(Function<ServerPlayer,JsonObject> action) {
        if(busy)throw new IllegalStateException("Overlapping exploration requests");
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        busy=true;reply=null;
        server.execute(()->{try{reply=action.apply(Objects.requireNonNull(server.getPlayerList().getPlayer(uuid)));}
            catch(Throwable e){error=e;}finally{busy=false;}});
    }
    private static void release(Minecraft mc){mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);}
    private JsonObject snapshot(ServerPlayer p) {
        var j=new JsonObject();j.addProperty("phase",PhaseWorldResolver.phaseId(p.level()).map(Enum::name).orElse("UNKNOWN"));
        j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());j.addProperty("health",p.getHealth());
        j.addProperty("uuid",p.getUUID().toString());j.addProperty("safe",LandingSafety.isSafe(p,p.level(),p.position(),TransferPolicy.BOUNDED_LANDING));
        j.addProperty("support",p.level().getBlockState(BlockPos.containing(p.position().add(0,-.01,0))).toString());
        var blocks=new TreeMap<String,Integer>();
        for(var pos:BlockPos.betweenClosed(p.blockPosition().offset(-5,-2,-5),p.blockPosition().offset(5,4,5))) {
            String name=p.level().getBlockState(pos).getBlock().toString();
            if(name.contains("ore") || name.contains("water") || name.contains("lava") || name.contains("fire"))blocks.merge(name,1,Integer::sum);
        }
        j.add("nearbyOreFluidFireBlocks",MvpWorldVerification.JSON.toJsonTree(blocks));
        j.addProperty("gameMode",p.gameMode.getGameModeForPlayer().getName());
        j.addProperty("surfaceHeight",p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition().getX(),p.blockPosition().getZ()));
        j.addProperty("bodyAabb",p.getBoundingBox().toString());j.addProperty("eyeBlock",p.level().getBlockState(BlockPos.containing(p.getEyePosition())).toString());
        var entities=new JsonArray();for(var entity:p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,p.getBoundingBox().inflate(12),entity->entity!=p)){
            var e=new JsonObject();e.addProperty("type",entity.getType().toString());e.addProperty("health",entity.getHealth());entities.add(e);}
        j.add("nearbyLivingEntities",entities);

        return j;
    }
    private void tick(ClientTickEvent.Post ignored) {
        if(ending)return;walking.set(false);long now=System.nanoTime();if(lastTickNanos!=0)maxTickGap=Math.max(maxTickGap,(now-lastTickNanos)/1e6);lastTickNanos=now;var mc=Minecraft.getInstance();ticks++;
        try {
            if(error!=null)throw new IllegalStateException("Server exploration action failed",error);
            if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen s) {
                for(var c:s.children())if(c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().equals(net.minecraft.client.gui.screens.BackupConfirmScreen.BACKUP_AND_JOIN)){b.onPress(null);return;}
            }
            if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
            if(!started){
                if(runId==null || runId.isBlank())throw new IllegalStateException("Exploration requires isolated supplement run id");
                var world=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("saves/wph015/mvp-world.json"))).getAsJsonObject();
                if(world.get("seed").getAsLong()!=123456789L)throw new IllegalStateException("Wrong frozen exploration seed");
                report.add("world",world);report.addProperty("kind","SUPPLEMENTAL_EXPLORATION");report.addProperty("runId",runId);
                report.addProperty("startedAt",startedAt);report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("os",System.getProperty("os.name"));
                report.addProperty("workload","scripted native exploration; no human participation; actual Survival input and physics");
                report.add("caves",caves);report.add("metrics",metrics);report.add("travelSegments",segments);
                mc.options.renderDistance().set(16);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;
                mc.options.cloudStatus().set(CloudStatus.OFF);started=true;if(Boolean.getBoolean("worldphase.mvp.travelOnly"))stage=1;save("RUNNING");
            }
            if(busy)return;
            if(mc.player.isDeadOrDying())throw new IllegalStateException("Native Survival exploration death; no rescue/respawn counted");
            if(mc.screen!=null || mc.isPaused()){release(mc);return;}
            if(ticks%100==0)measure(mc);
            if(stage==0)cave(mc);else if(stage==1)travel(mc);else finish(mc);
        }catch(Exception e){release(mc);ending=true;report.addProperty("failure",e.toString());try{save("FAIL");}catch(Exception w){e.addSuppressed(w);}e.printStackTrace();System.err.println("WPH015 EXPLORATION FAIL "+e);mc.stop();}
    }
    private JsonObject setup(ServerPlayer p,PhaseId phase) {
        var level=p.level().getServer().getLevel(PhaseWorldResolver.levelKey(phase));
        // All setup is excluded from measured Survival movement. No blocks are changed.
        p.setGameMode(GameType.SPECTATOR);p.teleportTo(level,-383.5,220,896.5,Set.of(),0,0,true);
        p.setGameMode(GameType.SURVIVAL);
        int cx=Integer.getInteger("worldphase.mvp.caveX",-384),cz=Integer.getInteger("worldphase.mvp.caveZ",896);
        var plan=new MvpLocalWalkSetup(p,level,12000,cx-62,cx+62,cz-62,cz+62).cave();
        var data=new JsonObject();data.addProperty("phase",phase.name());data.addProperty("inspected",plan.inspected());data.addProperty("fullChunks",plan.fullChunks());data.addProperty("discovery",plan.reason());
        data.addProperty("searchCenterX",cx);data.addProperty("searchCenterZ",cz);
        data.add("waypoints",points(plan.points()));
        if(!plan.points().isEmpty()){
            var mouth=plan.points().getFirst();p.teleportTo(level,mouth.x,mouth.y,mouth.z,Set.of(),0,0,true);
            p.setHealth(20);p.getFoodData().setFoodLevel(20);
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,24000,0,false,false));
            data.addProperty("assistance","OP bootstrap at natural outdoor mouth, health/food reset and night vision BEFORE Survival walk; no terrain edits, no in-walk teleport/heal");
            data.add("mouth",snapshot(p));
        }
        return data;
    }
    private void cave(Minecraft mc)throws Exception {
        var phase=phaseIndex==0?PhaseId.FRACTURE:PhaseId.SMOOTH;
        if(step==0){release(mc);request(p->setup(p,phase));step=1;wait=0;return;}
        if(step==1){
            if(reply==null)return;
            cave=reply.deepCopy();route=parsePoints(reply.getAsJsonArray("waypoints"));
            if(route.isEmpty()){cave.addProperty("status","DISCOVERY_FAILED");caves.add(cave);nextCave();save("RUNNING");return;}
            if(++wait<80)return;capture(mc,"cave-"+phase+"-mouth");waypoint=1;walkTicks=0;returning=false;step=2;return;
        }
        if(step==2){
            int outcome=walk(mc);
            if(outcome==0)return;
            release(mc);cave.addProperty(returning?"returnWalkTicks":"inwardWalkTicks",walkTicks);
            if(outcome<0){cave.addProperty("status",returning?"RETURN_BLOCKED":"INGRESS_BLOCKED");request(this::snapshot);step=5;return;}
            reply=null;checkpointRequested=false;step=returning?4:3;wait=0;return;
        }
        if(step==3){
            wait++;
            if(wait%40==0 && wait<=160){mc.player.setYRot((wait/40-1)*90);mc.player.setXRot(0);capture(mc,"cave-"+phase+"-survey-"+(wait/40));}
            if(wait<200)return;
            if(!checkpointRequested){request(this::snapshot);checkpointRequested=true;return;}
            if(reply==null)return;
            cave.add("interior",reply.deepCopy());capture(mc,"cave-"+phase+"-interior");
            var back=new ArrayList<>(route);Collections.reverse(back);route=back;waypoint=1;walkTicks=0;wait=0;returning=true;step=2;return;
        }
        if(step==4 || step==5){
            if(++wait<40)return;
            if(step==4 && !checkpointRequested){request(this::snapshot);checkpointRequested=true;return;}
            if(reply==null)return;
            cave.add(step==4?"returned":"blockedAt",reply.deepCopy());
            if(step==4)cave.addProperty("status","NATIVE_CAVE_RETURN_COMPLETED");
            capture(mc,"cave-"+phase+"-"+(step==4?"return":"blocked"));caves.add(cave);nextCave();save("RUNNING");
        }
    }
    private void nextCave(){phaseIndex++;step=0;reply=null;wait=0;if(phaseIndex>=2)stage=Boolean.getBoolean("worldphase.mvp.cavesOnly")?2:1;}
    private int walk(Minecraft mc){
        if(waypoint>=route.size())return 1;
        var target=route.get(waypoint);double dx=target.x-mc.player.getX(),dz=target.z-mc.player.getZ();
        if(dx*dx+dz*dz<.04 && Math.abs(target.y-mc.player.getY())<.65 && mc.player.onGround()){
            release(mc);waypoint++;wait=0;return waypoint>=route.size()?1:0;
        }
        if(++wait>120 || ++walkTicks>24000)return -1;
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));mc.player.setXRot(8);
        walking.set(true);mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(target.y>mc.player.getY()+.2 && mc.player.onGround());return 0;
    }
    private void travel(Minecraft mc)throws Exception {
        if(step==0){
            release(mc);
            request(p->{var level=p.level().getServer().getLevel(PhaseWorldResolver.levelKey(travelPhase));
                p.setGameMode(GameType.SPECTATOR);p.teleportTo(level,-383.5,220,896.5,Set.of(),180,0,true);
                var result=new PhaseTransferService(p.level().getServer()).transfer(p,travelPhase,TransferPolicy.DEV_SURFACE);
                if(result!=TransferResult.SUCCESS)throw new IllegalStateException("No safe natural travel bootstrap: "+result);
                p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);return snapshot(p);});
            step=1;wait=0;return;
        }
        if(step==1){
            if(reply==null || ++wait<80)return;travel=new JsonObject();travel.add("start",reply.deepCopy());report.add("travel",travel);
            travelX=reply.get("x").getAsDouble();travelZ=reply.get("z").getAsDouble();
            travel.addProperty("destinationX",travelX);travel.addProperty("destinationZ",travelZ-384);travel.addProperty("corridorHalfWidth",24);
            travel.addProperty("declaredBeforeMovement",true);travel.addProperty("startedAt",System.currentTimeMillis());travelNewStart=newChunks.get();travelClientStart=clientChunks.get();capture(mc,"travel-start");step=2;save("RUNNING");return;
        }
        if(step==2){
            release(mc);final int budget=18000-totalInspected;
            if(budget<=0 || totalWaypoints>=256 || mc.player.getZ()<=travelZ-384){step=6;return;}
            request(p->{long begin=System.nanoTime();var plan=new MvpLocalWalkSetup(p,p.level(),Math.min(1800,budget),
                    (int)Math.floor(travelX)-24,(int)Math.floor(travelX)+24,(int)Math.floor(travelZ)-384,(int)Math.floor(p.getZ())+2,blockedWaypoints)
                    .north(p.position(),Math.max((int)Math.floor(travelZ)-384,(int)Math.floor(p.getZ())-24));
                var j=new JsonObject();j.add("waypoints",points(plan.points()));j.addProperty("inspected",plan.inspected());j.addProperty("fullChunks",plan.fullChunks());j.addProperty("reason",plan.reason());j.addProperty("planMs",(System.nanoTime()-begin)/1e6);return j;});
            step=3;return;
        }
        if(step==3){
            if(reply==null)return;var segment=reply.deepCopy();segments.add(segment);route=parsePoints(segment.getAsJsonArray("waypoints"));
            if(route.size()>256-totalWaypoints)route=new ArrayList<>(route.subList(0,256-totalWaypoints));
            totalInspected+=segment.get("inspected").getAsInt();totalWaypoints+=route.size();
            if(route.size()<2){travel.addProperty("obstruction","No supported dry corridor within fixed budget");step=6;return;}
            waypoint=1;walkTicks=0;wait=0;step=4;return;
        }
        if(step==4){int outcome=walk(mc);if(outcome==0)return;release(mc);capture(mc,"travel-segment-"+segments.size());
            segments.get(segments.size()-1).getAsJsonObject().addProperty("nativeWalk",outcome>0?"SEGMENT_WALKED":"PHYSICALLY_BLOCKED");
            if(outcome<0){
                travel.addProperty("obstruction","Native walking could not follow conservative floor corridor");
                if(Boolean.getBoolean("worldphase.mvp.adaptiveWalk") && blockedWaypoints.size()<8 && waypoint<route.size()){
                    blockedWaypoints.add(BlockPos.containing(route.get(waypoint)));travel.addProperty("blockedWaypointsAvoided",blockedWaypoints.size());step=2;
                }else step=6;
            }else step=2;save("RUNNING");return;}
        if(step==6){
            release(mc);request(p->{var j=snapshot(p);j.addProperty("inspected",totalInspected);j.addProperty("plannedWaypoints",totalWaypoints);return j;});step=7;return;
        }
        if(step==7){
            if(reply==null)return;travel.add("end",reply.deepCopy());travel.addProperty("northProgress",travelZ-reply.get("z").getAsDouble());
            travel.addProperty("destinationReached",reply.get("z").getAsDouble()<=travelZ-384);travel.addProperty("status","BOUNDED_GROUND_ATTEMPT_RECORDED");travel.addProperty("endedAt",System.currentTimeMillis());travel.addProperty("newServerChunksDuringWalk",newChunks.get()-travelNewStart);travel.addProperty("receivedClientChunksDuringWalk",clientChunks.get()-travelClientStart);travel.addProperty("maxClientTickGapMs",maxTickGap);capture(mc,"travel-end");
            // Adaptive choice uses actual obstructed position, normal policy only; no surface reset.
            request(p->{var j=new JsonObject();j.add("before",snapshot(p));var before=p.position();
                var result=new PhaseTransferService(p.level().getServer()).transfer(p,PhaseId.FRACTURE,TransferPolicy.BOUNDED_LANDING);
                j.addProperty("result",result.name());j.add("after",snapshot(p));
                if(before.x!=p.getX() || before.z!=p.getZ())throw new IllegalStateException("Adaptive shift moved horizontally");
                j.addProperty("classification",result==TransferResult.SUCCESS?"SHIFT_POSITION_FEASIBLE":"OBSERVED_TERRAIN_DIFFERENCE");return j;});step=8;wait=0;return;
        }
        if(step==8){
            if(reply==null || ++wait<40)return;report.add("adaptiveChoice",reply.deepCopy());capture(mc,"adaptive-choice");
            // A successful entry alone never proves a route. Try a short actual alternate walk next.
            if(reply.get("result").getAsString().equals("SUCCESS")){totalInspected=0;step=9;}else{stage=2;step=0;}save("RUNNING");return;
        }
        if(step==9){
            request(p->{var plan=new MvpLocalWalkSetup(p,p.level(),1800,p.blockPosition().getX()-8,p.blockPosition().getX()+8,p.blockPosition().getZ()-16,p.blockPosition().getZ()+2).north(p.position(),p.blockPosition().getZ()-12);
                var j=new JsonObject();j.add("waypoints",points(plan.points()));j.addProperty("reason",plan.reason());return j;});step=10;return;
        }
        if(step==10){if(reply==null)return;report.getAsJsonObject("adaptiveChoice").add("alternatePlan",reply.deepCopy());route=parsePoints(reply.getAsJsonArray("waypoints"));waypoint=1;walkTicks=0;wait=0;step=11;return;}
        if(step==11){int outcome=walk(mc);if(outcome==0)return;release(mc);request(p->{var j=snapshot(p);var result=new PhaseTransferService(p.level().getServer()).transfer(p,PhaseId.SMOOTH,TransferPolicy.BOUNDED_LANDING);j.addProperty("returnResult",result.name());j.add("afterReturn",snapshot(p));return j;});step=12;wait=0;return;}
        if(step==12){if(reply==null || ++wait<40)return;report.getAsJsonObject("adaptiveChoice").add("exitAttempt",reply.deepCopy());capture(mc,"adaptive-exit");stage=2;step=0;}
    }
    private void measure(Minecraft mc){var j=new JsonObject();j.addProperty("clientTick",ticks);j.addProperty("stage",stage);j.addProperty("fps",mc.getFps());
        j.addProperty("heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        j.addProperty("gcCount",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum());
        j.addProperty("gcTimeMs",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum());
        j.addProperty("integratedAverageTickMs",mc.getSingleplayerServer().getAverageTickTimeNanos()/1e6);j.addProperty("newServerChunksDuringWalk",newChunks.get());j.addProperty("receivedClientChunksDuringWalk",clientChunks.get());j.addProperty("maxClientTickGapMs",maxTickGap);j.addProperty("x",mc.player.getX());j.addProperty("z",mc.player.getZ());metrics.add(j);}
    private void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"wph015-supplement-"+name+".png",mc.getMainRenderTarget(),1,t->System.out.println("WPH015 exploration screenshot "+t.getString()));}
    private void finish(Minecraft mc)throws Exception{release(mc);if(step==0){request(p->{p.level().getServer().saveEverything(false,true,true);return snapshot(p);});step=1;return;}if(reply==null)return;report.add("finalPlayer",reply.deepCopy());measure(mc);save("EXPLORATION_RECORDED_REVIEW_REQUIRED");ending=true;mc.stop();}
    private void save(String status)throws Exception{report.addProperty("status",status);report.addProperty("clientTicks",ticks);report.addProperty("updatedAt",System.currentTimeMillis());Files.writeString(Path.of("mvp-exploration.json"),MvpWorldVerification.JSON.toJson(report));}
    private static JsonArray points(List<Vec3> points){var a=new JsonArray();for(var p:points){var j=new JsonObject();j.addProperty("x",p.x);j.addProperty("y",p.y);j.addProperty("z",p.z);a.add(j);}return a;}
    private static List<Vec3> parsePoints(JsonArray array){var points=new ArrayList<Vec3>();for(var e:array){var j=e.getAsJsonObject();points.add(new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble()));}return points;}
}
