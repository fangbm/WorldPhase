package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import dev.fangbm.worldphase.tutorial.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Explicit, expensive dev oracle. Never runs inside ordinary scoring/ranking and never teleports. */
final class TutorialCorridorVerification {
    private static final Gson JSON=new Gson();
    private final MinecraftServer server;
    private final Map<PhaseId,Set<Long>> prepared=new EnumMap<>(PhaseId.class);
    private int fullRequests;
    TutorialCorridorVerification(MinecraftServer server) {
        this.server=server; prepared.put(PhaseId.FRACTURE,new HashSet<>()); prepared.put(PhaseId.SMOOTH,new HashSet<>());
    }
    int fullRequests() { return fullRequests; }
    JsonObject observe(TutorialCorridor corridor,int index) {
        if(!server.isSameThread()) throw new IllegalStateException("FULL tutorial validation requires server thread");
        int x=corridor.candidate().x(),z=corridor.candidate().z();
        var f=level(PhaseId.FRACTURE); var s=level(PhaseId.SMOOTH);
        for(var phase:List.of(PhaseId.FRACTURE,PhaseId.SMOOTH)) {
            var level=level(phase);
            for(int cx=(x-18)>>4;cx<=(x+18)>>4;cx++) for(int cz=(z-18)>>4;cz<=(z+18)>>4;cz++) {
                long key=((long)cx<<32)^(cz&0xffffffffL);
                if(prepared.get(phase).add(key)) { level.getChunk(cx,cz); fullRequests++; }
            }
        }
        var profile=new GameProfile(UUID.nameUUIDFromBytes(("WPH013-v4-"+server.overworld().getSeed()+"-"+index).getBytes(StandardCharsets.UTF_8)),"TutorialProbe");
        var body=PhaseTransferVerification.createPlayer(server,f,profile,Vec3.ZERO);
        int[] fh=new int[33],sh=new int[33]; boolean safeF=true,safeS=true;
        int unsafeF=0,unsafeS=0,fStep=0,sStep=0,maxContrast=0;
        var fHeights=new JsonArray(); var sHeights=new JsonArray();
        for(int t=-16;t<=16;t++) {
            int i=t+16,px=x+corridor.dx()*t,pz=z+corridor.dz()*t;
            fh[i]=ground(f,px,pz); sh[i]=ground(s,px,pz);
            boolean a=LandingSafety.isSafe(body,f,new Vec3(px+.5,fh[i]+1,pz+.5),TransferPolicy.BOUNDED_LANDING);
            boolean b=LandingSafety.isSafe(body,s,new Vec3(px+.5,sh[i]+1,pz+.5),TransferPolicy.BOUNDED_LANDING);
            if(!a) unsafeF++; if(!b) unsafeS++; safeF&=a; safeS&=b;
            if(i>0) { fStep=Math.max(fStep,Math.abs(fh[i]-fh[i-1])); sStep=Math.max(sStep,Math.abs(sh[i]-sh[i-1])); }
            maxContrast=Math.max(maxContrast,Math.abs(fh[i]-sh[i])); fHeights.add(fh[i]); sHeights.add(sh[i]);
        }
        var shifts=new JsonArray(); int sourceUnsafe=0,forwardRejected=0,reverseRejected=0;
        for(int t=-16;t<=16;t+=4) {
            int px=x+corridor.dx()*t,pz=z+corridor.dz()*t;
            var source=new Vec3(px+.5,fh[t+16]+1,pz+.5);
            var player=PhaseTransferVerification.createPlayer(server,f,profile,source);
            var inventory=player.getInventory(); var id=player.getUUID();
            if(!LandingSafety.isSafe(player,f,source,TransferPolicy.BOUNDED_LANDING)) { sourceUnsafe++; continue; }
            var destination=SafeLandingResolver.gameplay().resolve(player,s,source,TransferPolicy.BOUNDED_LANDING);
            if(destination.isEmpty()) { forwardRejected++; continue; }
            var to=destination.get();
            require(TransferPolicy.BOUNDED_LANDING.permits(source,to) && LandingSafety.isSafe(player,s,to,TransferPolicy.BOUNDED_LANDING),"shift.forward-policy");
            var reverse=SafeLandingResolver.gameplay().resolve(player,f,to,TransferPolicy.BOUNDED_LANDING);
            if(reverse.isEmpty()) { reverseRejected++; continue; }
            require(TransferPolicy.BOUNDED_LANDING.permits(to,reverse.get()) && LandingSafety.isSafe(player,f,reverse.get(),TransferPolicy.BOUNDED_LANDING),"shift.reverse-policy");
            require(player.level()==f && player.position().equals(source) && player.getUUID().equals(id)
                    && player.getInventory()==inventory && player.getYRot()==123.25f && player.getXRot()==-37.5f,"shift.mutated-player");
            var proof=new JsonObject(); proof.add("source",JSON.toJsonTree(source)); proof.add("destination",JSON.toJsonTree(to));
            proof.add("reverse",JSON.toJsonTree(reverse.get())); shifts.add(proof);
        }
        boolean obstacle=fStep>=2 && sStep<=1 && fStep-sStep>=1 && maxContrast>=4;
        boolean useful=obstacle && safeF && safeS && !shifts.isEmpty() && corridor.candidate().score().spawnDistance()<=512;
        var result=new JsonObject(); result.add("corridor",JSON.toJsonTree(corridor));
        result.add("fractureGround",fHeights); result.add("smoothGround",sHeights);
        result.addProperty("fractureAdjacentStep",fStep); result.addProperty("smoothAdjacentStep",sStep);
        result.addProperty("maximumHeightContrast",maxContrast); result.addProperty("unsafeFractureColumns",unsafeF); result.addProperty("unsafeSmoothColumns",unsafeS);
        result.addProperty("obstacleAndImprovement",obstacle); result.addProperty("usefulActual",useful);
        result.addProperty("boundedShiftProved",!shifts.isEmpty()); result.add("shiftProofs",shifts);
        result.addProperty("sourceUnsafe",sourceUnsafe); result.addProperty("forwardRejected",forwardRejected); result.addProperty("reverseRejected",reverseRejected);
        result.addProperty("shiftStatus",shifts.isEmpty()?"NO_FEASIBLE_SHIFT":"FEASIBLE_BOUNDED_SHIFT");
        result.addProperty("qualityStatus",useful?"USEFUL_LOCAL_CORRIDOR":!obstacle?"NO_ACTUAL_DIRECTIONAL_IMPROVEMENT":!safeF||!safeS?"UNSAFE_CORRIDOR": "NO_FEASIBLE_SHIFT");
        return result;
    }
    private ServerLevel level(PhaseId id) { return Objects.requireNonNull(server.getLevel(PhaseWorldResolver.levelKey(id))); }
    private static int ground(ServerLevel level,int x,int z) {
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        while(y>=level.getMinY()) {
            var state=level.getBlockState(new BlockPos(x,y,z));
            if(!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && Heightmap.Types.OCEAN_FLOOR.isOpaque().test(state)) break;
            y--;
        }
        return y;
    }
    private static void require(boolean condition,String cause) { if(!condition) throw new IllegalStateException(cause); }
}
