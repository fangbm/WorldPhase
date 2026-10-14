package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Native graphical test workload, excluded from the mod JAR. No synthetic PASS or gameplay implementation. */
final class MvpPlaytestClient {
    private enum Stage { VIEWS, VILLAGES, ROUTES, SURVIVAL, DONE, RELOAD }
    private final boolean reload=Boolean.getBoolean("worldphase.mvp.reload");
    private final boolean routesOnly=Boolean.getBoolean("worldphase.mvp.routesOnly");
    private final int routeFrom=Integer.getInteger("worldphase.mvp.routeFrom",0),routeTo=Integer.getInteger("worldphase.mvp.routeTo",36);
    private final JsonObject report=new JsonObject();
    private final JsonArray photos=new JsonArray(),routes=new JsonArray(),smokes=new JsonArray(),metrics=new JsonArray();
    private Stage stage=Stage.VIEWS;
    private JsonObject world,trial;
    private volatile JsonObject reply;
    private volatile Throwable actionError;
    private volatile boolean busy;
    private volatile boolean smoking;
    private int ticks,index,wait,settled,routeStep,routeTicks,smokeIndex,smokeTicks;
    private int breaks,placements;
    private int startBreaks,startPlacements;
    private double originX,originZ,baselineProgress;
    private long previousNano,activeNanos;
    private boolean initialized,submitted;
    private boolean ending,deathSubmitted;
    private final String supplement=System.getProperty("worldphase.mvp.supplement", "");

    MvpPlaytestClient() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::broken);
        NeoForge.EVENT_BUS.addListener(this::placed);
    }
    private void broken(BlockEvent.BreakEvent e) { if(smoking) breaks++; }
    private void placed(BlockEvent.EntityPlaceEvent e) { if(smoking && e.getEntity() instanceof ServerPlayer) placements++; }

    private void request(Function<ServerPlayer,JsonObject> action) {
        if(busy) throw new IllegalStateException("WPH015 overlapping server actions");
        var mc=Minecraft.getInstance(); var server=mc.getSingleplayerServer(); var uuid=mc.player.getUUID();
        busy=true; reply=null;
        server.execute(() -> {
            try { reply=action.apply(Objects.requireNonNull(server.getPlayerList().getPlayer(uuid))); }
            catch(Throwable error) { actionError=error; }
            finally { busy=false; }
        });
    }
    private void tick(ClientTickEvent.Post e) {
        if(ending)return;
        var mc=Minecraft.getInstance(); ticks++;
        try {
            if(actionError!=null) throw new IllegalStateException("WPH015 server action failed",actionError);
            if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {
                for(var child:screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().equals(net.minecraft.client.gui.screens.BackupConfirmScreen.BACKUP_AND_JOIN)) {
                    button.onPress(null); return;
                }
            }
            if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null) return;
            if(!initialized) {
                world=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("saves/"+(routesOnly?"wph015-routes":"wph015")+"/mvp-world.json"))).getAsJsonObject();
                mc.getWindow().setTitle("WorldPhase WPH015 seed "+world.get("seed").getAsLong());
                report.add("world",world); report.addProperty("workload","native client input automation, real integrated ServerPlayer/physics/world; no route platforms");
                report.add("photos",photos); report.add("routeTrials",routes); report.add("survival",smokes); report.add("metrics",metrics);
                if(!supplement.isEmpty()) {
                    report.addProperty("kind","SUPPLEMENTAL_REPLAY");report.addProperty("runId",supplement);
                    report.addProperty("os",System.getProperty("os.name"));report.addProperty("java",System.getProperty("java.runtime.version"));
                }
                if(Boolean.getBoolean("worldphase.mvp.resumeViews")) {
                    var previous=JsonParser.parseString(Files.readString(Path.of("mvp-client.json"))).getAsJsonObject();
                    if(!previous.get("stage").getAsString().equals("VIEWS") || !previous.get("world").equals(world)
                            || !previous.getAsJsonArray("routeTrials").isEmpty() || !previous.getAsJsonArray("survival").isEmpty())
                        throw new IllegalStateException("Only an interrupted matching observation stage may resume; no route/Survival time is carried");
                    photos.addAll(previous.getAsJsonArray("photos"));metrics.addAll(previous.getAsJsonArray("metrics"));index=photos.size();
                    report.addProperty("resumedObservations",index);
                    report.addProperty("priorFailure","glfw.dll native crash; not a passed run; route and Survival work starts fresh");
                }
                mc.options.renderDistance().set(16); mc.options.simulationDistance().set(5);
                mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF); mc.options.pauseOnLostFocus=false;
                initialized=true; previousNano=System.nanoTime(); save("RUNNING");
                if(reload) {stage=Stage.RELOAD;wait=0;}
                if(routesOnly) {
                    if(routeFrom<0 || routeTo>36 || routeTo<=routeFrom)throw new IllegalArgumentException("Route interval must be within0..36");
                    stage=Stage.ROUTES;index=routeFrom;report.addProperty("routeFrom",routeFrom);report.addProperty("routeTo",routeTo);
                    report.addProperty("returnPolicy","release keys, wait at least20 ticks for onGround, at most100 ticks; same route criteria; separate clone for replay");
                }
            }
            if(busy) return;
            if(mc.player.isDeadOrDying() && !supplement.isEmpty() && stage==Stage.ROUTES && trial!=null) {
                release(mc);
                if(!deathSubmitted) {
                    request(p->{var data=shiftSnapshot(p);data.addProperty("serverDead",p.isDeadOrDying());
                        data.addProperty("health",p.getHealth());data.addProperty("cause",p.getLastDamageSource()==null?"unknown":p.getLastDamageSource().getMsgId());return data;});
                    deathSubmitted=true;return;
                }
                if(reply==null)return;
                if(!reply.get("serverDead").getAsBoolean())throw new IllegalStateException("Client death not confirmed by server");
                trial.add("death",reply.deepCopy());trial.addProperty("classification","OBSERVED_TERRAIN_DIFFERENCE");
                trial.addProperty("reason","FATAL_FALL_OR_DEATH_DURING_NATIVE_TRIAL");trial.addProperty("routeStep",routeStep);trial.addProperty("walkTicks",routeTicks);
                report.add("abortedTrial",trial.deepCopy());capture(mc,"route-"+index+"-death");save("RECORDED_NEGATIVE_OUTCOME");ending=true;mc.stop();return;
            }
            if(mc.player.isDeadOrDying()) throw new IllegalStateException("Native player died in "+stage+"; Survival activeSeconds="+activeNanos/1e9);
            if(mc.screen!=null || mc.isPaused()) { release(mc); previousNano=System.nanoTime(); return; }
            if(ticks%200==0) measure(mc);
            switch(stage) {
                case VIEWS,VILLAGES -> photo(mc);
                case ROUTES -> route(mc);
                case SURVIVAL -> survival(mc);
                case DONE -> finish(mc);
                case RELOAD -> reload(mc);
            }
        } catch(Exception error) {
            ending=true;
            release(mc); smoking=false; report.addProperty("failure",error.toString());
            try { save("FAIL"); } catch(Exception write) { error.addSuppressed(write); }
            System.err.println("WPH015 FAIL "+error); mc.stop();
        }
    }
    private void measure(Minecraft mc) {
        var row=new JsonObject(); row.addProperty("clientTick",ticks); row.addProperty("stage",stage.name());
        row.addProperty("jvmStartTime",ManagementFactory.getRuntimeMXBean().getStartTime());
        row.addProperty("fps",mc.getFps()); row.addProperty("heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        row.addProperty("gcCount",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum());
        row.addProperty("gcTimeMs",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum());
        // Tick-time accessor is an observational metric only; no Level sampling is moved off-thread.
        row.addProperty("integratedAverageTickMs",mc.getSingleplayerServer().getAverageTickTimeNanos()/1e6);
        metrics.add(row);
    }
    private void photo(Minecraft mc) throws Exception {
        int count=stage==Stage.VIEWS?40:world.getAsJsonArray("villages").size();
        if(index>=count) { stage=stage==Stage.VIEWS?Stage.VILLAGES:Stage.ROUTES;index=0;submitted=false;save("RUNNING");return; }
        PhaseId phase; int x,z; double y=220; String name;
        if(stage==Stage.VIEWS) {
            phase=MvpWorldVerification.PHASES[index%4];var point=MvpWorldVerification.POINTS[index/4];x=point[0];z=point[1];
            name="point-"+(index/4)+"-"+phase.name().toLowerCase(Locale.ROOT);
        } else {
            var village=world.getAsJsonArray("villages").get(index).getAsJsonObject();
            if(!village.has("x")) {index++;return;}
            phase=PhaseId.valueOf(village.get("phase").getAsString());x=village.get("x").getAsInt();z=village.get("z").getAsInt();
            y=Math.max(145,village.get("height").getAsInt()+60); name="village-"+phase.name().toLowerCase(Locale.ROOT);
        }
        if(!submitted) {
            release(mc);mc.options.hideGui=true;final double height=y;
            request(player -> {
                var level=player.level().getServer().getLevel(PhaseWorldResolver.levelKey(phase));
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level,x+0.5,height,z+0.5,Set.of(),135,45,true);
                level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level),"time set day");
                return new JsonObject();
            }); submitted=true;wait=0;settled=0;return;
        }
        if(!mc.level.dimension().equals(PhaseWorldResolver.levelKey(phase))) return;
        if(++wait>2400) throw new IllegalStateException("Rendering timeout "+name);
        if(wait<360 || !ready(mc,x,z) || !mc.levelRenderer.hasRenderedAllSections()) {settled=0;return;}
        if(++settled<20) return;
        capture(mc,name);
        var row=new JsonObject();row.addProperty("name",name);row.addProperty("phase",phase.name());row.addProperty("x",x+0.5);row.addProperty("z",z+0.5);
        row.addProperty("y",y);row.addProperty("renderSettleTicks",wait);row.addProperty("fps",mc.getFps());row.addProperty("sections",mc.levelRenderer.getSectionStatistics());
        photos.add(row);System.out.println("WPH015 PHOTO "+row);index++;submitted=false;save("RUNNING");
    }
    private boolean ready(Minecraft mc,int x,int z) {
        for(int dx=-6;dx<=6;dx++) for(int dz=-6;dz<=6;dz++) if(dx*dx+dz*dz<=36
                && mc.level.getChunkSource().getChunk((x>>4)+dx,(z>>4)+dz,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null) return false;
        return true;
    }
    private static final int[][] ENTRANCES={{128,128},{464,-128},{-384,896}};
    private static final PhaseId[][] PAIRS={{PhaseId.FRACTURE,PhaseId.SMOOTH},{PhaseId.RIDGE,PhaseId.SMOOTH},{PhaseId.CELL,PhaseId.FRACTURE}};
    private static final int[] DX={0,1,0,-1},DZ={-1,0,1,0};
    private static final float[] YAW={180,-90,0,90};
    private double progress(Minecraft mc,int direction) { return (mc.player.getX()-originX)*DX[direction]+(mc.player.getZ()-originZ)*DZ[direction]; }
    private JsonObject bootstrap(ServerPlayer player,PhaseId phase,int x,int z,float yaw) {
        var server=player.level().getServer();var target=server.getLevel(PhaseWorldResolver.levelKey(phase));
        player.setGameMode(GameType.SPECTATOR);player.teleportTo(target,x+0.5,220,z+0.5,Set.of(),yaw,0,true);
        var result=new PhaseTransferService(server).transfer(player,phase,TransferPolicy.DEV_SURFACE);
        var row=new JsonObject();row.addProperty("bootstrap",result.name());
        if(result==TransferResult.SUCCESS) {
            player.setHealth(20);player.getFoodData().setFoodLevel(20);player.setGameMode(GameType.SURVIVAL);
            row.addProperty("x",player.getX());row.addProperty("y",player.getY());row.addProperty("z",player.getZ());
        }
        return row;
    }
    private JsonObject shift(ServerPlayer player,PhaseId target) {
        var before=player.position();float yaw=player.getYRot(),pitch=player.getXRot();long start=System.nanoTime();
        var previous=shiftSnapshot(player);var inventory=new ArrayList<net.minecraft.world.item.ItemStack>();
        for(int i=0;i<player.getInventory().getContainerSize();i++) inventory.add(player.getInventory().getItem(i).copy());
        var result=new PhaseTransferService(player.level().getServer()).transfer(player,target,TransferPolicy.BOUNDED_LANDING);
        var row=new JsonObject();row.addProperty("result",result.name());row.addProperty("elapsedMs",(System.nanoTime()-start)/1e6);
        row.addProperty("before",before.toString());row.addProperty("after",player.position().toString());
        row.addProperty("level",player.level().dimension().identifier().toString());row.addProperty("uuid",player.getUUID().toString());
        if(before.x!=player.getX() || before.z!=player.getZ() || yaw!=player.getYRot() || pitch!=player.getXRot())
            throw new IllegalStateException("Native route shift violated coordinates/rotation");
        if(result==TransferResult.SUCCESS && !LandingSafety.isSafe(player,player.level(),player.position(),TransferPolicy.BOUNDED_LANDING))
            throw new IllegalStateException("Unsafe native route shift");
        for(int i=0;i<inventory.size();i++) if(!net.minecraft.world.item.ItemStack.matches(inventory.get(i),player.getInventory().getItem(i)))
            throw new IllegalStateException("Native shift changed inventory slot "+i);
        int count=0;for(var level:player.level().getServer().getAllLevels()) if(level.getEntity(player.getUUID())!=null)count++;
        if(count!=1 || player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player)
            throw new IllegalStateException("Native shift duplicated/replaced player");
        if(result!=TransferResult.SUCCESS && !previous.equals(shiftSnapshot(player)))
            throw new IllegalStateException("Rejected native shift changed source snapshot");
        row.addProperty("inventoryAndPlayerIdentity","PASS");row.addProperty("playerEntities",count);
        return row;
    }
    private void route(Minecraft mc) throws Exception {
        mc.options.hideGui=false;
        if(index>=(routesOnly?routeTo:36)) {index=0;submitted=false;routeStep=0;stage=!routesOnly && world.get("seed").getAsLong()==123456789L?Stage.SURVIVAL:Stage.DONE;save("RUNNING");return;}
        int direction=index%4,pair=(index/4)%3,point=index/12;
        var source=PAIRS[pair][0];var target=PAIRS[pair][1];var entrance=ENTRANCES[point];
        if(routeStep==0) {
            release(mc);trial=new JsonObject();trial.addProperty("index",index);trial.addProperty("source",source.name());trial.addProperty("targetPhase",target.name());
            trial.addProperty("entranceX",entrance[0]+0.5);trial.addProperty("entranceZ",entrance[1]+0.5);trial.addProperty("direction",direction);
            request(p->bootstrap(p,source,entrance[0],entrance[1],YAW[direction]));routeStep=1;wait=0;return;
        }
        if(routeStep==1 || routeStep==3) {
            if(reply==null) return;
            if(!reply.get("bootstrap").getAsString().equals("SUCCESS")) {trial.add("setup",reply);endTrial(mc,"OBSERVED_TERRAIN_DIFFERENCE","NO_SAFE_BOOTSTRAP");return;}
            if(++wait<40) return;
            originX=reply.get("x").getAsDouble();originZ=reply.get("z").getAsDouble();routeTicks=0;
            if(routeStep==1) {trial.add("setup",reply.deepCopy());capture(mc,"route-"+index+"-entrance");routeStep=2;}
            else {request(p->shift(p,target));routeStep=4;wait=0;}
            return;
        }
        if(routeStep==2 || routeStep==5) {
            routeTicks++;mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(routeTicks%20<2);
            mc.player.setYRot(YAW[direction]);mc.player.setXRot(0);
            if(routeTicks<160 && progress(mc,direction)<24 && mc.player.getHealth()>8 && !mc.player.isDeadOrDying()) return;
            release(mc);double distance=progress(mc,direction);
            if(routeStep==2) {
                baselineProgress=distance;trial.addProperty("baselineProgress",distance);capture(mc,"route-"+index+"-baseline");
                request(p->bootstrap(p,source,entrance[0],entrance[1],YAW[direction]));routeStep=3;wait=0;
            } else {
                trial.addProperty("alternateProgress",distance);capture(mc,"route-"+index+"-alternate");
                if(distance<24) endTrial(mc,"SHIFT_POSITION_FEASIBLE","ALTERNATE_WALK_INCOMPLETE");
                else {routeStep=7;wait=0;}
            }
            return;
        }
        if(routeStep==4) {
            if(reply==null) return;
            trial.add("entryShift",reply.deepCopy());
            if(!reply.get("result").getAsString().equals("SUCCESS")) {endTrial(mc,"OBSERVED_TERRAIN_DIFFERENCE","ENTRY_SHIFT_REFUSED");return;}
            if(!mc.level.dimension().equals(PhaseWorldResolver.levelKey(target)) || ++wait<20) return;
            routeStep=5;routeTicks=0;capture(mc,"route-"+index+"-shifted");return;
        }
        if(routeStep==6) {
            if(reply==null) return;
            boolean returned=reply.get("result").getAsString().equals("SUCCESS");
            if(returned && (!mc.level.dimension().equals(PhaseWorldResolver.levelKey(source)) || ++wait<20)) return;
            trial.add("returnShift",reply.deepCopy());capture(mc,"route-"+index+"-return");
            endTrial(mc,returned && baselineProgress<12?"ROUTE_COMPLETED":"SHIFT_POSITION_FEASIBLE",
                    !returned?"RETURN_SHIFT_REFUSED":baselineProgress>=12?"SOURCE_NOT_SUBSTANTIALLY_BLOCKED":"SHORT_NATIVE_BYPASS_COMPLETED");
        }
        if(routeStep==7) {
            release(mc);
            if(++wait>100) {endTrial(mc,"SHIFT_POSITION_FEASIBLE","NO_SETTLED_RETURN_POSITION");return;}
            if(wait<20 || !mc.player.onGround())return;
            request(p->shift(p,source));routeStep=6;wait=0;
        }
    }
    private void endTrial(Minecraft mc,String classification,String reason) throws Exception {
        release(mc);trial.addProperty("classification",classification);trial.addProperty("reason",reason);routes.add(trial);
        System.out.println("WPH015 ROUTE "+trial);index++;routeStep=0;reply=null;save("RUNNING");
    }
    private void survival(Minecraft mc) throws Exception {
        var phase=smokeIndex==0?PhaseId.FRACTURE:PhaseId.SMOOTH;
        if(!submitted) {
            release(mc);request(p->bootstrap(p,phase,128,128,0));submitted=true;wait=0;activeNanos=0;smokeTicks=0;smoking=false;return;
        }
        if(!smoking) {
            if(reply==null) return;
            if(!reply.get("bootstrap").getAsString().equals("SUCCESS")) {
                var row=reply.deepCopy();row.addProperty("phase",phase.name());row.addProperty("status","BOOTSTRAP_FAILED");row.addProperty("activeSeconds",0);smokes.add(row);
                nextSmoke(mc);return;
            }
            if(++wait<60) return;
            smoking=true;previousNano=System.nanoTime();startBreaks=breaks;startPlacements=placements;capture(mc,"survival-"+phase.name()+"-start");
            System.out.println("WPH015 SURVIVAL START "+phase+" at "+mc.player.position());return;
        }
        long now=System.nanoTime();activeNanos+=Math.min(now-previousNano,200_000_000L);previousNano=now;smokeTicks++;
        if(mc.player.isDeadOrDying()) {throw new IllegalStateException("Survival death "+phase+" activeSeconds="+activeNanos/1e9);}
        int part=smokeTicks%240;mc.options.keyUp.setDown(part<80);mc.options.keyJump.setDown(part<80 && part%30<2);
        mc.player.setYRot((smokeTicks/240%4)*90);mc.player.setXRot(part<80?0:45);
        mc.options.keyAttack.setDown(part>=100 && part<150);mc.options.keyUse.setDown(part>=180 && part<185);
        if(part==175) for(int slot=0;slot<9;slot++) if(mc.player.getInventory().getItem(slot).getItem() instanceof BlockItem) {
            mc.player.getInventory().setSelectedSlot(slot);break;
        }
        if(smokeTicks%1200==0) {capture(mc,"survival-"+phase.name()+"-"+(smokeTicks/1200));save("RUNNING");System.out.println("WPH015 SURVIVAL "+phase+" activeSeconds="+activeNanos/1e9+" hp="+mc.player.getHealth()+" at="+mc.player.position());}
        if(activeNanos<900_000_000_000L || smokeTicks<18000) return;
        release(mc);smoking=false;
        var row=new JsonObject();row.addProperty("phase",phase.name());row.addProperty("activeSeconds",activeNanos/1e9);row.addProperty("clientTicks",smokeTicks);
        row.addProperty("blocksBroken",breaks-startBreaks);row.addProperty("blocksPlaced",placements-startPlacements);row.addProperty("health",mc.player.getHealth());
        row.addProperty("position",mc.player.position().toString());row.addProperty("status","TIMED_NATIVE_SURVIVAL_COMPLETED");smokes.add(row);nextSmoke(mc);
    }
    private void nextSmoke(Minecraft mc) throws Exception {
        release(mc);smoking=false;smokeIndex++;submitted=false;reply=null;save("RUNNING");if(smokeIndex>=2) {stage=Stage.DONE;wait=0;}
    }
    private void finish(Minecraft mc) throws Exception {
        release(mc);if(++wait<80) return;
        if(!submitted) {request(p -> {p.level().getServer().saveEverything(false,true,true);return shiftSnapshot(p);});submitted=true;return;}
        if(reply==null) return;report.add("finalPlayer",reply.deepCopy());measure(mc);save("COMPLETED_NOT_A_GAMEPLAY_GATE_PASS");
        System.out.println("WPH015 native workload complete; inspect route/survival findings before deciding gate");mc.stop();
    }
    private JsonObject shiftSnapshot(ServerPlayer player) {
        var row=new JsonObject();row.addProperty("uuid",player.getUUID().toString());row.addProperty("level",player.level().dimension().identifier().toString());
        row.addProperty("position",player.position().toString());row.addProperty("yaw",player.getYRot());row.addProperty("pitch",player.getXRot());
        var inventory=new JsonArray();for(int i=0;i<player.getInventory().getContainerSize();i++) inventory.add(player.getInventory().getItem(i).toString());
        row.add("inventory",inventory);return row;
    }
    private void reload(Minecraft mc) throws Exception {
        release(mc);
        if(!submitted) {request(this::shiftSnapshot);submitted=true;return;}
        if(reply==null) return;
        var baseline=JsonParser.parseString(Files.readString(Path.of("mvp-client.json"))).getAsJsonObject().getAsJsonObject("finalPlayer");
        report.add("restoredPlayer",reply.deepCopy());report.add("expectedPlayer",baseline);
        if(!reply.equals(baseline)) throw new IllegalStateException("Native reload player/identity/inventory snapshot differs");
        capture(mc,"reload");save("RELOAD_PASS");System.out.println("WPH015 RELOAD PASS without bootstrap or transfer");mc.stop();
    }
    private void capture(Minecraft mc,String suffix) {
        if(routesOnly) suffix="settled-"+suffix;
        Screenshot.grab(mc.gameDirectory,"wph015-"+world.get("seed").getAsLong()+"-"+suffix+".png",mc.getMainRenderTarget(),1,
                text->System.out.println("WPH015 screenshot "+text.getString()));
    }
    private static void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
    }
    private void save(String status) throws Exception {
        report.addProperty("status",status);report.addProperty("stage",stage.name());report.addProperty("clientTicks",ticks);
        Files.writeString(Path.of(reload?"mvp-reload.json":routesOnly?"mvp-routes.json":"mvp-client.json"),MvpWorldVerification.JSON.toJson(report));
    }
}
