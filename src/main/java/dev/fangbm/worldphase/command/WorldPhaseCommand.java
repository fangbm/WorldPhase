package dev.fangbm.worldphase.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import dev.fangbm.worldphase.worldgen.sample.*;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Server-side OP development tools. All world/teleport mathematics remain in their services. */
public final class WorldPhaseCommand {
    private static final List<PhaseId> CYCLE = List.of(PhaseId.FRACTURE, PhaseId.SMOOTH, PhaseId.RIDGE, PhaseId.CELL);
    private WorldPhaseCommand() {}

    public static void onRegister(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("wp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        var phases = Commands.literal("phase")
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("current").executes(c -> current(c.getSource())))
                .then(Commands.literal("cycle").executes(c -> cycle(c.getSource())));
        var go = Commands.literal("goto");
        var sample = Commands.literal("sample");
        var safe = Commands.literal("safepos");
        var compare = Commands.literal("compare").then(Commands.literal("all").executes(c -> compareAll(c.getSource())));
        // Literal Phase tokens provide native completion without a custom network argument type
        // or a generic string that would also match the reserved compare-all literal.
        for (PhaseId phase : CYCLE) {
            go.then(Commands.literal(token(phase)).executes(c -> go(c.getSource(), phase, TransferPolicy.BOUNDED_LANDING))
                    .then(Commands.literal("--surface").executes(c -> go(c.getSource(), phase, TransferPolicy.DEV_SURFACE))));
            sample.then(Commands.literal(token(phase)).executes(c -> sample(c.getSource(), phase)));
            safe.then(Commands.literal(token(phase)).executes(c -> safe(c.getSource(), phase)));
            var first = Commands.literal(token(phase));
            for (PhaseId second : CYCLE) first.then(Commands.literal(token(second)).executes(c -> compare(c.getSource(), phase, second)));
            compare.then(first);
        }
        dispatcher.register(root.then(phases.then(go)).then(sample).then(compare).then(safe)
                .then(Commands.literal("seedcheck").executes(c -> seedcheck(c.getSource()))));
    }

    static String token(PhaseId phase) { return PhaseWorldResolver.levelKey(phase).identifier().getPath(); }
    static PhaseId next(PhaseId phase) { return CYCLE.get((CYCLE.indexOf(phase) + 1) % CYCLE.size()); }
    private static void say(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal("[wp] " + text), false); }
    private static int fail(CommandSourceStack source, String text) { source.sendFailure(Component.literal("[wp] " + text)); return 0; }
    private static String format(String pattern, Object... args) { return String.format(Locale.ROOT, pattern, args); }

    private static int list(CommandSourceStack source) {
        for (PhaseId phase : CYCLE) say(source, token(phase) + " | " + PhaseWorldResolver.levelKey(phase).identifier()
                + " | " + (source.getServer().getLevel(PhaseWorldResolver.levelKey(phase)) != null ? "loaded" : "MISSING"));
        return CYCLE.size();
    }

    private static int current(CommandSourceStack source) {
        var level = source.getLevel();
        say(source, "current=" + PhaseWorldResolver.phaseId(level).map(WorldPhaseCommand::token).orElse("NON_PHASE")
                + " | " + level.dimension().identifier());
        return 1;
    }

    private static int go(CommandSourceStack source, PhaseId phase, TransferPolicy policy) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var result = new PhaseTransferService(source.getServer()).transfer(player, phase, policy);
        var message = token(phase) + " " + policy + " " + describe(result);
        if (result != TransferResult.SUCCESS && result != TransferResult.SAME_PHASE) return fail(source, message);
        say(source, message + format(" | x=%.6f y=%.6f z=%.6f", player.position().x, player.position().y, player.position().z));
        return 1;
    }

    private static int cycle(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var phase = PhaseWorldResolver.phaseId(player.level());
        if (phase.isEmpty()) return fail(source, "INVALID_SOURCE: current dimension is NON_PHASE; enter a Phase with Vanilla /execute first");
        return go(source, next(phase.get()), TransferPolicy.BOUNDED_LANDING);
    }

    private static int sample(CommandSourceStack source, PhaseId phase) throws CommandSyntaxException {
        var pos = source.getPlayerOrException().position();
        int x=Mth.floor(pos.x), z=Mth.floor(pos.z);
        try {
            var result = new TerrainSampleService(source.getServer()).sample(phase,x,z);
            say(source, format("block x=%d z=%d | rawNoise=configured kernel signal, not final density",x,z));
            row(source,result); return 1;
        } catch (IllegalStateException error) { return fail(source, "Sampling unavailable: " + error.getMessage()); }
    }

    private static void row(CommandSourceStack source, TerrainSample sample) {
        say(source, format("%-8s baseHeight=%d rawNoise=%+.6f",token(sample.phase()),sample.baseHeight(),sample.rawNoise()));
    }

    private static int compare(CommandSourceStack source, PhaseId first, PhaseId second) throws CommandSyntaxException {
        var pos=source.getPlayerOrException().position(); int x=Mth.floor(pos.x), z=Mth.floor(pos.z);
        try {
            var result=new TerrainSampleService(source.getServer()).compare(first,second,x,z);
            say(source,format("block x=%d z=%d | rawNoise=configured signal, not final density",x,z));
            row(source,result.firstSample()); row(source,result.secondSample());
            say(source,format("delta %s - %s: height=%+d rawNoise=%+.6f",token(second),token(first),result.heightDelta(),result.noiseDelta()));
            return 1;
        } catch (IllegalStateException error) { return fail(source,"Sampling unavailable: "+error.getMessage()); }
    }

    private static int compareAll(CommandSourceStack source) throws CommandSyntaxException {
        var pos=source.getPlayerOrException().position(); int x=Mth.floor(pos.x), z=Mth.floor(pos.z);
        try {
            var sampling=new TerrainSampleService(source.getServer());
            // Gather first so a configuration failure cannot print a misleading partial comparison.
            var results=CYCLE.stream().map(p -> sampling.sample(p,x,z)).toList();
            say(source,format("block x=%d z=%d | rawNoise=configured signal, not final density",x,z));
            results.forEach(s -> row(source,s)); return results.size();
        } catch (IllegalStateException error) { return fail(source,"Sampling unavailable: "+error.getMessage()); }
    }

    private static int safe(CommandSourceStack source, PhaseId phase) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        var result=new PhaseTransferService(source.getServer()).inspectLanding(player,phase,TransferPolicy.BOUNDED_LANDING);
        say(source,format("safepos %s source=(%.6f, %.6f, %.6f); actual chunks may load/generate",token(phase),result.original().x,result.original().y,result.original().z));
        if(result.result()!=TransferResult.SUCCESS) return fail(source,"exactSafe="
                +(result.result()==TransferResult.NO_SAFE_POSITION || result.result()==TransferResult.OUT_OF_WORLD?"false":"unknown")
                +"; "+describe(result.result()));
        say(source,format("exactSafe=%s destinationY=%.6f deltaY=%+.6f; diagnostic only",result.exactSafe(),result.destination().orElseThrow().y,
                result.destination().orElseThrow().y-result.original().y)); return 1;
    }

    private static int seedcheck(CommandSourceStack source) {
        Long expected=null; boolean pass=true;
        for(PhaseId phase:CYCLE) {
            var level=source.getServer().getLevel(PhaseWorldResolver.levelKey(phase));
            if(level==null) { say(source,token(phase)+" MISSING "+PhaseWorldResolver.levelKey(phase).identifier()); pass=false; continue; }
            long seed=level.getSeed(); if(expected==null) expected=seed;
            boolean same=seed==expected; pass &= same;
            say(source,token(phase)+" seed="+seed+(same?"":" MISMATCH expected="+expected));
        }
        if(!pass) return fail(source,"seedcheck FAIL: Phase Level missing or raw seeds differ");
        say(source,"seedcheck PASS: all four actual raw Level seeds match"); return 1;
    }

    static String describe(TransferResult result) {
        return result + ": " + switch(result) {
            case SUCCESS -> "safe destination reached";
            case SAME_PHASE -> "already in this Phase; unchanged";
            case TARGET_LEVEL_MISSING -> "target Phase Level is not loaded";
            case NO_SAFE_POSITION -> "no safe landing within this policy; unchanged";
            case CHUNK_NOT_READY -> "target FULL chunks not ready; unchanged";
            case CHUNK_FAILURE -> "target chunk acquisition failed; unchanged";
            case OUT_OF_WORLD -> "destination body outside WorldBorder/build height; unchanged";
            case INVALID_SOURCE -> "invalid/stale source; Vanilla entry requires /execute in a Phase first";
            case ERROR -> "native teleport refused before commit; unchanged";
        };
    }
}
