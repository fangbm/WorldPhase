package dev.fangbm.worldphase.verification.v2.r2;
import dev.fangbm.worldphase.verification.v2.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
/** One legacy host server, three independent in-memory raw-seed profiles, no V2 Levels/settings. */
public final class R2ServerPreflight {
    private final MinecraftServer server;private final NativeV2Fixture fixture;private int index;private R2Context current;private boolean done;private long initWall,initCpu,initAllocation;
    private R2ServerPreflight(MinecraftServer server)throws Exception{
        this.server=server;R2.verify(Path.of(System.getProperty("worldphase.projectRoot")));
        for(var l:server.getAllLevels())if(l.dimension().identifier().getPath().contains("prototype_v2"))throw new IllegalStateException("V2_LEVEL_FORBIDDEN");
        fixture=new NativeV2Fixture();NeoForge.EVENT_BUS.addListener(this::tick);
    }
    public static void start(MinecraftServer server){try{new R2ServerPreflight(server);}catch(Exception e){throw new IllegalStateException("R2 setup failed",e);}}
    private void tick(ServerTickEvent.Post event){if(done||event.getServer()!=server)return;
        try{
            if(current==null){long wall=System.nanoTime(),cpu=R2Context.cpu(),alloc=R2Context.allocated();
                var state=fixture.state(fixture.vanilla,R2.SEEDS[index]);current=new R2Context(R2.SEEDS[index],R1.rawReference(state.router().continents()));
                initWall=System.nanoTime()-wall;initCpu=R2Context.cpu()-cpu;initAllocation=R2Context.allocated()-alloc;current.chargeInitialization(initCpu,initAllocation,initWall);
                if(initWall>25_000_000L){current.abort("BUDGET_EXCEEDED:INITIALIZATION_HARD_TICK_25MS");finish();return;}
                // Initialization and first step share this server tick, so defer sampling to the next tick.
                return;
            }
            try{current.measuredStep();}catch(RuntimeException failure){if(current.state!=R2Context.State.FAILED)throw failure;}
            if(current.state==R2Context.State.FAILED||current.state==R2Context.State.PREPARATION_COMPLETED)finish();
        }catch(Exception e){done=true;server.halt(false);throw new IllegalStateException("R2 evidence failure",e);}
    }
    private void finish()throws Exception{
        var report=R2Evidence.report(current,"dedicated legacy host Post tick; separate raw-seed RandomState profiles; cold per-profile caches",initWall,initCpu,initAllocation);
        report.addProperty("legacyHostSeed",server.overworld().getSeed());report.addProperty("hostAverageMspt",server.getAverageTickTimeNanos()/1e6);
        R2Evidence.write(Path.of("r2-reports/seed-"+R2.SEEDS[index]+".json"),report);current.close();current=null;index++;
        if(index==R2.SEEDS.length){done=true;server.saveEverything(false,true,true);server.halt(false);}
    }
}
