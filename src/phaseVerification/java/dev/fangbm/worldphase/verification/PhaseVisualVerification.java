package dev.fangbm.worldphase.verification;

import dev.fangbm.worldphase.phase.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native client rendering smoke check; isolated dev source set only. */
@Mod(value="worldphase_verification", dist=Dist.CLIENT)
public final class PhaseVisualVerification {
    private static final int[][] VIEWS={{-384,896},{128,128},{-2048,-2048}};
    private static final String[] NAMES={"overworld","smooth","ridge","cell","fracture"};
    private static final PhaseId[] PHASES={PhaseId.SMOOTH,PhaseId.RIDGE,PhaseId.CELL,PhaseId.FRACTURE};
    private int ticks, stage, wait, settled;
    private boolean moving;

    public PhaseVisualVerification() {
        if(Boolean.getBoolean("worldphase.mvp.client")) new MvpPlaytestClient();
        else NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(++ticks > 18000) {
            System.err.println("WPH007 visual probe timed out");mc.stop();return;
        }
        // Only the isolated copied test world: accept Vanilla's experimental-world backup prompt.
        if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {
            for(var child:screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().equals(net.minecraft.client.gui.screens.BackupConfirmScreen.BACKUP_AND_JOIN)) {
                button.onPress(null);return;
            }
        }
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null) return;
        if(stage>=VIEWS.length*NAMES.length) {
            try {
                for(int i=0;i<VIEWS.length*NAMES.length;i++) if(!Files.exists(Path.of("screenshots",name(i)))) return;
                Files.writeString(Path.of("phase-visual-status.txt"),"PASS\n");
                System.out.println("WPH007 CLIENT PASS: 15 native screenshots of all five dimensions saved");
                mc.stop();
            } catch(Exception error) { throw new IllegalStateException(error); }
            return;
        }
        if(!moving) {
            int current=stage;
            var server=mc.getSingleplayerServer();
            var uuid=mc.player.getUUID();
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(uuid);
                var target=server.getLevel(key(current));
                if(player==null || target==null) throw new IllegalStateException("Visual verification Level/player missing");
                player.setGameMode(GameType.SPECTATOR);
                int[] view=VIEWS[current/NAMES.length];
                player.teleportTo(target,view[0]+0.5,220,view[1]+0.5,java.util.Set.of(),135,45,true);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(target),"time set day");
            });
            mc.options.hideGui=true;
            mc.options.renderDistance().set(16);
            mc.options.simulationDistance().set(5); // 26.1's normal client option minimum (server.properties allows 2).
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            moving=true;wait=0;settled=0;
        }
        var target=key(stage);
        if(!mc.level.dimension().equals(target)) return;
        if(++wait<360) return; // Eighteen seconds for actual rendering to settle at each view.
        // A fixed delay alone can capture floating decoration/holes while distant chunks arrive.
        // Require the inner view circle and the renderer's actual compile queue, then one stable second.
        if(!chunksReady(mc) || !mc.levelRenderer.hasRenderedAllSections()) {settled=0;return;}
        if(++settled<20) return;
        System.out.println("WPH007 ready "+name(stage)+" chunks="+mc.level.getChunkSource().getLoadedChunksCount()
                +" "+mc.levelRenderer.getSectionStatistics());
        Screenshot.grab(mc.gameDirectory,name(stage),mc.getMainRenderTarget(),1,
                component -> System.out.println("WPH007 screenshot " + component.getString()));
        stage++;moving=false;
    }

    private static String name(int index) {
        return "wph007-"+(index/NAMES.length)+"-"+NAMES[index%NAMES.length]+".png";
    }

    private static net.minecraft.resources.ResourceKey<Level> key(int index) {
        int phase=index%NAMES.length;
        return phase==0?Level.OVERWORLD:PhaseWorldResolver.levelKey(PHASES[phase-1]);
    }

    private boolean chunksReady(Minecraft mc) {
        int[] view=VIEWS[stage/NAMES.length];int cx=view[0]>>4,cz=view[1]>>4;
        for(int dx=-14;dx<=14;dx++) for(int dz=-14;dz<=14;dz++) if(dx*dx+dz*dz<=14*14) {
            if(mc.level.getChunkSource().getChunk(cx+dx,cz+dz,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null) return false;
        }
        return true;
    }
}
