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
public final class SmoothVisualVerification {
    private static final int[][] VIEWS={{-384,896},{128,128},{1152,-896}};
    private int ticks, stage, wait;
    private boolean moving;

    public SmoothVisualVerification() { NeoForge.EVENT_BUS.addListener(this::tick); }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(++ticks > 3600) {
            System.err.println("WPH006 visual probe timed out");mc.stop();return;
        }
        // Only the isolated copied test world: accept Vanilla's experimental-world backup prompt.
        if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {
            for(var child:screen.children()) if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().equals(net.minecraft.client.gui.screens.BackupConfirmScreen.BACKUP_AND_JOIN)) {
                button.onPress(null);return;
            }
        }
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null) return;
        if(stage>=6) {
            try {
                for(int i=0;i<6;i++) if(!Files.exists(Path.of("screenshots",name(i)))) return;
                Files.writeString(Path.of("smooth-visual-status.txt"),"PASS\n");
                System.out.println("WPH006 CLIENT PASS: native screenshots of both dimensions saved");
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
                var target=server.getLevel(current%2==0 ? Level.OVERWORLD : PhaseWorldResolver.levelKey(PhaseId.SMOOTH));
                if(player==null || target==null) throw new IllegalStateException("Visual verification Level/player missing");
                player.setGameMode(GameType.SPECTATOR);
                int[] view=VIEWS[current/2];
                player.teleportTo(target,view[0]+0.5,140,view[1]+0.5,java.util.Set.of(),135,25,true);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(target),"time set day");
            });
            mc.options.hideGui=true;
            mc.options.renderDistance().set(6);
            moving=true;wait=0;
        }
        var target=stage%2==0 ? Level.OVERWORLD : PhaseWorldResolver.levelKey(PhaseId.SMOOTH);
        if(!mc.level.dimension().equals(target)) return;
        if(++wait<240) return; // Allow actual client chunk rendering to settle for twelve seconds.
        Screenshot.grab(mc.gameDirectory,name(stage),mc.getMainRenderTarget(),1,
                component -> System.out.println("WPH006 screenshot " + component.getString()));
        stage++;moving=false;
    }

    private static String name(int index) {
        return "wph006-"+(index/2)+"-"+(index%2==0?"overworld":"smooth")+".png";
    }
}
