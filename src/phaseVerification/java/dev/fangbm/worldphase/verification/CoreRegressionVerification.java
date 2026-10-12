package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/** Headless integration test only. Real worlds/player/native teleport; outbound packets discarded. */
final class CoreRegressionVerification {
    private static final long SEED=123456789L;
    private static final List<PhaseId> CYCLE=List.of(PhaseId.FRACTURE,PhaseId.SMOOTH,PhaseId.RIDGE,PhaseId.CELL);
    private static final Vec3 POINT=new Vec3(-16.125,220.5,15.9375);
    private static final GameProfile PROFILE=new GameProfile(UUID.fromString("a3b83e90-28f9-4a94-b89c-8c46af811012"),"WPH012Probe");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final MinecraftServer server;
    private final CoreInvariantLedger ledger=new CoreInvariantLedger();
    private final Map<PhaseId,ServerLevel> levels=new EnumMap<>(PhaseId.class);
    private final JsonObject report=new JsonObject();
    private final JsonArray resources=new JsonArray();
    private ServerPlayer player;
    private Inventory inventory;
    private List<ItemStack> items;
    private int loopTransfers, inventoryChecks;

    private CoreRegressionVerification(MinecraftServer server) { this.server=server; }
    static void verify(MinecraftServer server) { new CoreRegressionVerification(server).run(); }

    private void run() {
        String stage=System.getProperty("worldphase.core.stage", "invalid");
        Path output=Path.of("core-"+stage+".json");
        try {
            report.addProperty("minecraft",SharedConstants.getCurrentVersion().name());
            report.addProperty("neoforge",ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());
            report.addProperty("java",System.getProperty("java.runtime.version"));
            report.addProperty("seed",SEED); report.addProperty("stage",stage);
            report.add("phases",JSON.toJsonTree(CYCLE.stream().map(p->PhaseWorldResolver.levelKey(p).identifier().toString()).toList()));
            ledger.require(stage.equals("cold") || stage.equals("reload"),"run.stage","expected cold or reload");
            String injected=System.getProperty("worldphase.core.failInvariant", "");
            if(!injected.isEmpty()) ledger.require(false,injected,"deliberate dev-only failure-report self-test");
            ledger.require(server.isSameThread(),"server.thread","probe must execute on server thread");
            var rawSeeds=new JsonObject(); report.add("rawSeeds",rawSeeds);
            for(var phase:CYCLE) {
                var level=server.getLevel(PhaseWorldResolver.levelKey(phase));
                ledger.require(level!=null,"world.loaded",phase+" missing");
                rawSeeds.addProperty(phase.name(),level.getSeed());
                ledger.require(level.getSeed()==SEED,"world.raw_seed",phase+" actual="+level.getSeed());
                levels.put(phase,level);
            }
            Path baselinePath=server.getWorldPath(LevelResource.ROOT).resolve("core-baseline.json");
            JsonObject baseline=null;
            ledger.scenario(stage+":terrain");
            var terrain=terrain(); report.add("terrain",terrain);
            if(stage.equals("reload")) {
                ledger.require(Files.isRegularFile(baselinePath),"reload.baseline","cold checkpoint missing");
                baseline=JsonParser.parseString(Files.readString(baselinePath)).getAsJsonObject();
                ledger.require(baseline.get("terrain").equals(terrain),"reload.terrain","base samples or FULL chunk fingerprints changed");
                ledger.require(levels.get(PhaseId.CELL).getBlockState(new BlockPos(-17,209,15)).is(Blocks.GOLD_BLOCK),
                        "reload.fixture","saved collision-area marker missing");
                restorePlayer(baseline.getAsJsonObject("player"));
                report.addProperty("saveReloadCheck","PASS: terrain, marker and Vanilla player data restored before fixture initialization");
            } else {
                ledger.require(!Files.exists(baselinePath),"cold.fresh_world","cold task must use a new world");
                player=PhaseTransferVerification.createPlayer(server,levels.get(PhaseId.FRACTURE),PROFILE,POINT);
                inventory=player.getInventory();
                inventory.setItem(0,new ItemStack(Items.DIAMOND,7)); inventory.setItem(10,new ItemStack(Items.IRON_INGOT,23));
                inventory.setItem(35,new ItemStack(Items.CLOCK)); inventory.setItem(36,new ItemStack(Items.DIAMOND_BOOTS));
                inventory.setItem(40,new ItemStack(Items.SHIELD)); items=copyInventory();
                player.level().addNewPlayer(player);
            }
            platforms(); checkPlayer(PhaseId.FRACTURE,POINT,true);
            failures("before");
            int beforeLoop=ledger.successfulTransfers();
            resourceSample("before_loop");
            for(int cycle=1;cycle<=50;cycle++) {
                for(int step=1;step<=4;step++) {
                    var phase=CYCLE.get(step%4);
                    ledger.scenario("cycle="+cycle+" step="+step+" target="+phase);
                    var source=player.position(); float yaw=player.getYRot(),pitch=player.getXRot();
                    int result=commandCycle();
                    ledger.require(result==1,"command.cycle_result","expected SUCCESS, result="+result);
                    checkPlayer(phase,point(phase),true);
                    observeMove(source,yaw,pitch); loopTransfers++;
                }
                ledger.cycleCompleted();
                if(cycle%10==0) {
                    resourceSample("cycle_"+cycle);
                    System.out.println("WPH012 "+stage+" cycle="+cycle+"/50 successful loop transfers="+loopTransfers);
                }
            }
            ledger.require(loopTransfers==200 && ledger.successfulTransfers()-beforeLoop==200,"cycle.count","requires 50 complete four-Phase loops");
            failures("after");
            ledger.scenario(stage+":save");
            var savedPlayer=playerState();
            server.getPlayerList().getPlayerIo().save(player);
            ledger.require(Files.isRegularFile(server.getPlayerList().getPlayerIo().getPlayerDir().toPath().resolve(PROFILE.id()+".dat")),
                    "save.player_file","Vanilla player save did not produce .dat");
            levels.get(PhaseId.CELL).setBlock(new BlockPos(-17,209,15),Blocks.GOLD_BLOCK.defaultBlockState(),3);
            player.discard(); server.saveEverything(false,true,true);
            if(stage.equals("cold")) {
                var checkpoint=new JsonObject(); checkpoint.add("terrain",terrain); checkpoint.add("player",savedPlayer);
                Files.writeString(baselinePath,JSON.toJson(checkpoint));
                report.addProperty("saveReloadCheck","CHECKPOINT_SAVED: reload task must verify it");
            }
            report.add("savedPlayer",savedPlayer); report.addProperty("status","PASS");
            finishReport(); Files.writeString(output,JSON.toJson(report));
            System.out.println("WPH012 "+stage+" PASS loops=50 shifts=200 totalSuccess="+ledger.successfulTransfers()+" report="+output);
            server.halt(false);
        } catch(Exception failure) {
            report.addProperty("status","FAIL");
            report.addProperty("failedInvariant",ledger.failedInvariant()==null?"core.unexpected_exception":ledger.failedInvariant());
            report.addProperty("scenario",ledger.scenario()); report.addProperty("failure",failure.toString());
            finishReport();
            try { Files.writeString(output,JSON.toJson(report)); } catch(Exception writing) { failure.addSuppressed(writing); }
            server.halt(false);
            throw new IllegalStateException("WPH012 "+report.get("failedInvariant")+" at "+ledger.scenario()+"; inspect "+output,failure);
        }
    }

    /** Observations, not timing assertions. The probe runs at startup: no steady-state TPS claim. */
    private void resourceSample(String label) {
        var row=new JsonObject();row.addProperty("label",label);row.addProperty("nanoTime",System.nanoTime());
        row.addProperty("heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        row.addProperty("gcCount",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum());
        row.addProperty("gcTimeMs",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum());
        resources.add(row);report.add("resources",resources);
    }

    private JsonObject terrain() throws Exception {
        var result=new JsonObject(); var samples=new JsonArray();
        var sampling=new TerrainSampleService(server);
        var phaseHeights=new EnumMap<PhaseId,List<Integer>>(PhaseId.class);
        for(var phase:CYCLE) phaseHeights.put(phase,new ArrayList<>());
        for(int[] coordinate:new int[][]{{-512,768},{256,256},{1024,-1024}}) {
            var row=new JsonObject(); row.addProperty("x",coordinate[0]); row.addProperty("z",coordinate[1]);
            var heights=new HashSet<Integer>();
            for(var phase:CYCLE) {
                var sample=sampling.sample(phase,coordinate[0],coordinate[1]);
                var again=sampling.sample(phase,coordinate[0],coordinate[1]);
                ledger.require(sample.equals(again),"noise.determinism",phase+" "+Arrays.toString(coordinate));
                ledger.require(Double.isFinite(sample.rawNoise()),"noise.finite",phase.toString());
                heights.add(sample.baseHeight());
                phaseHeights.get(phase).add(sample.baseHeight());
                var data=new JsonObject(); data.addProperty("baseHeight",sample.baseHeight());
                data.addProperty("rawNoise",sample.rawNoise());
                data.addProperty("rawNoiseBits",Long.toUnsignedString(Double.doubleToRawLongBits(sample.rawNoise())));
                row.add(phase.name(),data);
            }
            ledger.require(heights.size()>1,"terrain.phase_difference","all four heights identical at "+Arrays.toString(coordinate));
            samples.add(row);
        }
        result.add("samples",samples);
        for(int first=0;first<CYCLE.size();first++) for(int second=first+1;second<CYCLE.size();second++) {
            ledger.require(!phaseHeights.get(CYCLE.get(first)).equals(phaseHeights.get(CYCLE.get(second))),
                    "terrain.phase_pair_difference",CYCLE.get(first)+" and "+CYCLE.get(second)+" have identical heights at all probe coordinates");
        }
        var full=new JsonObject();
        for(var phase:CYCLE) full.add(phase.name(),PhaseVerification.chunk(levels.get(phase),-512,768));
        result.add("fullChunks",full); return result;
    }

    private void restorePlayer(JsonObject expected) throws Exception {
        ledger.scenario("reload:player");
        var tag=server.getPlayerList().loadPlayerData(new net.minecraft.server.players.NameAndId(PROFILE.id(),PROFILE.name()));
        ledger.require(tag.isPresent(),"reload.player_data","Vanilla saved player missing");
        var key=ResourceKey.create(Registries.DIMENSION,Identifier.parse(tag.orElseThrow().getString(ServerPlayer.TAG_DIMENSION).orElseThrow()));
        var restoredLevel=server.getLevel(key);
        ledger.require(restoredLevel!=null && PhaseWorldResolver.phaseId(key).isPresent(),"reload.player_level","unknown stored dimension "+key);
        player=PhaseTransferVerification.createPlayer(server,restoredLevel,PROFILE,Vec3.ZERO);
        try(var reporter=new ProblemReporter.ScopedCollector(player.problemPath(),LogUtils.getLogger())) {
            player.load(TagValueInput.create(reporter,server.registryAccess(),tag.orElseThrow()));
        }
        // No snapTo, replacement inventory or fixture placement before this comparison.
        ledger.require(playerState().equals(expected),"reload.player_state","position/rotation/UUID/full inventory differs from saved checkpoint");
        inventory=player.getInventory(); items=copyInventory(); player.level().addNewPlayer(player);
        checkPlayer(PhaseId.FRACTURE,POINT,true);
        report.add("restoredPlayer",playerState());
    }

    private JsonObject playerState() {
        var state=new JsonObject(); state.addProperty("dimension",player.level().dimension().identifier().toString());
        state.addProperty("uuid",player.getUUID().toString());
        state.addProperty("x",player.position().x); state.addProperty("y",player.position().y); state.addProperty("z",player.position().z);
        state.addProperty("yaw",player.getYRot()); state.addProperty("pitch",player.getXRot());
        var slots=new JsonArray(); var ops=player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(var item:copyInventory()) slots.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,item).getOrThrow());
        state.add("inventory",slots); return state;
    }

    private List<ItemStack> copyInventory() {
        var result=new ArrayList<ItemStack>();
        for(int i=0;i<player.getInventory().getContainerSize();i++) result.add(player.getInventory().getItem(i).copy());
        return result;
    }

    private void platforms() {
        for(var phase:CYCLE) { PhaseTransferVerification.clear(levels.get(phase)); PhaseTransferVerification.slab(levels.get(phase),phase==PhaseId.RIDGE?221:220); }
    }
    private static Vec3 point(PhaseId phase) { return phase==PhaseId.RIDGE?POINT.add(0,1,0):POINT; }

    private void checkPlayer(PhaseId phase,Vec3 expected,boolean safe) {
        try {
            if(safe) PhaseTransferVerification.arrived(player,levels.get(phase),expected,PROFILE,inventory,items);
            else PhaseTransferVerification.assertPlayer(player,levels.get(phase),expected,PROFILE,inventory,items);
        } catch(RuntimeException failure) { ledger.require(false,"player.state_or_landing",failure.getMessage()); }
        inventoryChecks++;
        int dropped=0, registeredPlayers=0;
        for(var level:server.getAllLevels()) for(var entity:level.getAllEntities()) {
            if(entity.getUUID().equals(PROFILE.id())) {
                ledger.require(entity==player && level==levels.get(phase),"player.identity","replacement or stale player entity in "+level.dimension());
                registeredPlayers++;
            }
            if(entity instanceof ItemEntity item && items.stream().anyMatch(stack->!stack.isEmpty() && stack.is(item.getItem().getItem()))) dropped++;
        }
        ledger.require(registeredPlayers==1,"player.duplicate_entities","matching player entities="+registeredPlayers);
        ledger.require(dropped==0,"inventory.dropped_duplicates","dropped test item entities="+dropped);
    }

    private void observeMove(Vec3 source,float yaw,float pitch) {
        var destination=player.position();
        ledger.require(TransferPolicy.BOUNDED_LANDING.permits(source,destination),"transfer.policy_candidates","destination outside exact +/-4 candidates");
        ledger.transferred(source.x,source.z,source.y,yaw,pitch,destination.x,destination.z,destination.y,player.getYRot(),player.getXRot());
    }

    private int commandCycle() throws Exception {
        return server.getCommands().getDispatcher().execute("wp phase cycle",player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER));
    }

    @FunctionalInterface private interface Attempt { TransferResult run() throws Exception; }
    private void rejection(String scenario,TransferResult expected,Attempt attempt) throws Exception {
        ledger.scenario(scenario);
        var source=player.position(); var phase=PhaseWorldResolver.phaseId(player.level()).orElseThrow();
        var actual=attempt.run();
        ledger.require(actual==expected,"rejection.result","expected "+expected+", actual="+actual);
        checkPlayer(phase,source,false); ledger.rejected(expected.name());
    }

    private void recover(PhaseTransferService service) {
        var smooth=levels.get(PhaseId.SMOOTH); PhaseTransferVerification.clear(smooth); PhaseTransferVerification.slab(smooth,220);
        for(var phase:List.of(PhaseId.SMOOTH,PhaseId.FRACTURE)) {
            var source=player.position(); float yaw=player.getYRot(),pitch=player.getXRot();
            ledger.require(service.transfer(player,phase,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"recovery.transfer","failed after "+ledger.scenario());
            checkPlayer(phase,point(phase),true); observeMove(source,yaw,pitch);
        }
    }

    private void failures(String when) throws Exception {
        var target=levels.get(PhaseId.SMOOTH); var service=new PhaseTransferService(server);
        Attempt shift=()->service.transfer(player,PhaseId.SMOOTH,TransferPolicy.BOUNDED_LANDING);
        PhaseTransferVerification.clear(target);
        for(int y=215;y<=230;y++) PhaseTransferVerification.layer(target,y,Blocks.STONE.defaultBlockState());
        rejection(when+":all_blocked",TransferResult.NO_SAFE_POSITION,shift); recover(service);
        PhaseTransferVerification.clear(target);
        rejection(when+":unsupported",TransferResult.NO_SAFE_POSITION,shift); recover(service);
        PhaseTransferVerification.layer(target,221,Blocks.POWDER_SNOW.defaultBlockState());
        rejection(when+":dangerous_powder_snow",TransferResult.NO_SAFE_POSITION,shift); recover(service);
        double size=target.getWorldBorder().getSize(); target.getWorldBorder().setSize(4);
        try { rejection(when+":world_border",TransferResult.OUT_OF_WORLD,shift); }
        finally { target.getWorldBorder().setSize(size); }
        recover(service);
        Consumer<EntityTravelToDimensionEvent> veto=e->{
            if(e.getEntity()==player && e.getDimension()==PhaseWorldResolver.levelKey(PhaseId.SMOOTH)) e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            rejection(when+":native_travel_veto",TransferResult.ERROR,shift);
            ledger.require(commandCycle()==0,"command.cycle_veto","cycle must reach same native veto through transfer");
            checkPlayer(PhaseId.FRACTURE,POINT,false);
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        recover(service);
        PhaseTransferVerification.clear(target); PhaseTransferVerification.slab(target,225);
        ledger.require(LandingSafety.isSafe(player,target,POINT.add(0,5,0),TransferPolicy.BOUNDED_LANDING),"fixture.safe_plus_five","fixture must have real safe support at +5");
        rejection(when+":safe_plus_five_forbidden",TransferResult.NO_SAFE_POSITION,shift);
        ledger.require(commandCycle()==0,"command.cycle_bounded","cycle must not fallback to surface or +5");
        checkPlayer(PhaseId.FRACTURE,POINT,false); recover(service);
        rejection(when+":same_phase_noop",TransferResult.SAME_PHASE,()->service.transfer(player,PhaseId.FRACTURE,TransferPolicy.BOUNDED_LANDING));
        recover(service);
    }

    private void finishReport() {
        report.addProperty("successfulTransfers",ledger.successfulTransfers()); report.addProperty("loopTransfers",loopTransfers);
        report.addProperty("completeCycles",ledger.completedCycles()); report.add("rejectedScenarios",JSON.toJsonTree(ledger.rejectedScenarios()));
        report.addProperty("maxXzDeviation",ledger.maxXzDeviation()); report.addProperty("maxYawPitchDeviation",ledger.maxRotationDeviation());
        report.addProperty("maxYCorrection",ledger.maxYCorrection()); report.addProperty("inventoryIdentityAndContentChecks",inventoryChecks);
        report.addProperty("duplicatePlayerEntities",report.has("status") && report.get("status").getAsString().equals("PASS")?0:-1);
        report.addProperty("droppedTestItemEntities",report.has("status") && report.get("status").getAsString().equals("PASS")?0:-1);
        report.addProperty("transport","real ServerPlayer/native teleport, simulated outbound packet sends; no GUI/login acceptance claim");
    }
}
