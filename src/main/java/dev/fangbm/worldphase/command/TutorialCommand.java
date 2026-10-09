package dev.fangbm.worldphase.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.tutorial.*;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** OP-only diagnostics. One ephemeral search per server, advanced only by server ticks. */
public final class TutorialCommand {
    private record Pending(CommandSourceStack source,TutorialCandidateFinder.Search search,long started) {}
    private static final Map<MinecraftServer,Pending> ACTIVE=new ConcurrentHashMap<>();
    private TutorialCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("tutorial")
                .then(Commands.literal("score").executes(c->score(c.getSource())))
                .then(Commands.literal("find").executes(c->find(c.getSource(),TutorialSearchConfig.DEFAULT_COUNT))
                        .then(Commands.argument("candidateCount",IntegerArgumentType.integer(TutorialSearchConfig.MIN_COUNT,TutorialSearchConfig.MAX_COUNT))
                                .executes(c->find(c.getSource(),IntegerArgumentType.getInteger(c,"candidateCount")))));
    }
    private static int score(CommandSourceStack source) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        long start=System.nanoTime();
        try {
            var center=center(source.getServer());
            var config=TutorialSearchConfig.defaults();
            var candidate=new TutorialCandidateFinder(source.getServer()).score(Mth.floor(player.position().x),Mth.floor(player.position().z),center.getX(),center.getZ(),config);
            long seed=source.getServer().getLevel(PhaseWorldResolver.levelKey(PhaseId.FRACTURE)).getSeed();
            say(source,String.format(Locale.ROOT,"tutorial score seed=%d center=Overworld shared spawn (%d,%d) radius=%d samples=18 computeMs=%.2f; base heuristic, no FULL chunks/path proof",
                    seed,center.getX(),center.getZ(),config.radius(),(System.nanoTime()-start)/1e6));
            row(source,1,candidate,config.radius()); return 1;
        } catch(IllegalArgumentException | IllegalStateException error) { return fail(source,error.getMessage()); }
    }
    private static int find(CommandSourceStack source,int count) {
        var server=source.getServer();
        if(ACTIVE.containsKey(server)) return fail(source,"tutorial search already active; wait for its bounded completion");
        try {
            var center=center(server);
            var config=new TutorialSearchConfig(TutorialSearchConfig.DEFAULT_RADIUS,count);
            var search=new TutorialCandidateFinder(server).begin(center.getX(),center.getZ(),config);
            ACTIVE.put(server,new Pending(source,search,System.nanoTime()));
            say(source,"tutorial find started center=Overworld shared spawn ("+center.getX()+","+center.getZ()+") radius="+config.radius()
                    +" candidates="+count+"; at most two height columns/tick, no FULL chunks, no teleport");
            return 1;
        } catch(IllegalArgumentException | IllegalStateException error) { return fail(source,error.getMessage()); }
    }

    private static BlockPos center(MinecraftServer server) {
        var overworld=server.getLevel(Level.OVERWORLD);
        if(overworld==null) throw new IllegalStateException("Tutorial dev center requires a loaded Overworld");
        var spawn=overworld.getRespawnData();
        if(spawn.dimension()!=Level.OVERWORLD) throw new IllegalStateException("Tutorial dev center requires an Overworld shared spawn, actual="+spawn.dimension().identifier());
        return spawn.pos();
    }

    public static void onTick(ServerTickEvent.Post event) { advance(event.getServer()); }
    public static void onStopped(ServerStoppedEvent event) { ACTIVE.remove(event.getServer()); }
    // Also used by the dedicated probe and command unit tests: same production tick work.
    public static void advance(MinecraftServer server) {
        var pending=ACTIVE.get(server); if(pending==null) return;
        try {
            pending.search.advance(2);
            if(!pending.search.done()) return;
            ACTIVE.remove(server);
            var result=pending.search.result();
            say(pending.source,String.format(Locale.ROOT,"tutorial find seed=%d center=(%d,%d) radius=%d evaluated=%d spaced=%d heightSamples=%d computeMs=%.2f wallMs=%.2f maxBatchMs=%.2f; heuristic only",
                    result.seed(),result.centerX(),result.centerZ(),result.config().radius(),result.evaluatedCount(),result.candidates().size(),result.heightSamples(),
                    result.computeNanos()/1e6,(System.nanoTime()-pending.started)/1e6,result.maxBatchNanos()/1e6));
            for(int i=0;i<Math.min(5,result.candidates().size());i++) row(pending.source,i+1,result.candidates().get(i),result.config().radius());
        } catch(IllegalArgumentException | IllegalStateException error) {
            ACTIVE.remove(server); fail(pending.source,"tutorial search failed: "+error.getMessage());
        }
    }
    private static void row(CommandSourceStack source,int rank,TutorialCandidate candidate,int radius) {
        var score=candidate.score();
        say(source,String.format(Locale.ROOT,"#%d x=%d z=%d F=%d S=%d | slope F=%.3f S=%.3f contrast=%+.3f | heightContrast=%.1f",
                rank,candidate.x(),candidate.z(),candidate.fractureHeight(),candidate.smoothHeight(),score.fractureSlope(),score.smoothSlope(),score.slopeContrast(),score.heightContrast()));
        say(source,String.format(Locale.ROOT,"total=%+.2f height=%+.2f slope=%+.2f distancePenalty=%.2f hazardPenalty=%.2f distance=%.1f usefulHeuristic=%s (not landing/path validation)",
                score.totalScore(),score.heightContribution(),score.slopeContribution(),score.distancePenalty(),score.hazardPenalty(),score.spawnDistance(),score.usefulHeuristic(radius)));
    }
    private static void say(CommandSourceStack source,String text) { source.sendSuccess(()->Component.literal("[wp] "+text),false); }
    private static int fail(CommandSourceStack source,String text) { source.sendFailure(Component.literal("[wp] "+text)); return 0; }
}
