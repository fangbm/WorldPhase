package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import dev.fangbm.worldphase.phase.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Fixed-camera real GPU captures of unchanged worlds; no gameplay/route acceptance. */
final class SpatialBaselineClient {
    private final JsonObject report=new JsonObject();
    private final JsonArray photos=new JsonArray();
    private boolean initialized,submitted,ending;
    private volatile boolean busy;
    private volatile Throwable error;
    private int index,wait,settled;
    private long seed;
    SpatialBaselineClient(){
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::camera);
    }
    private void camera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event){
        if(initialized && !ending){event.setYaw(135);event.setPitch(60);event.setRoll(0);}
    }
    private void tick(ClientTickEvent.Post event){
        if(ending)return;
        var mc=Minecraft.getInstance();
        try{
            if(error!=null)throw new IllegalStateException("Native baseline server action failed",error);
            if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen){
                for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().equals(net.minecraft.client.gui.screens.BackupConfirmScreen.BACKUP_AND_JOIN)){button.onPress(null);return;}
            }
            if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
            if(!initialized){
                var data=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("saves/spatial-legacy-v1/spatial-baseline.json"))).getAsJsonObject();
                if(!data.get("status").getAsString().equals("PASS"))throw new IllegalStateException("Missing measured server baseline");
                seed=data.get("seed").getAsLong();report.addProperty("seed",seed);report.addProperty("protocol",SpatialBaselineVerification.PROTOCOL);
                report.addProperty("workload","scripted native fixed-camera observation; not human gameplay or route validation");report.add("photos",photos);
                mc.options.graphicsPreset().set(net.minecraft.client.GraphicsPreset.FANCY);
                mc.options.renderDistance().set(16);mc.options.simulationDistance().set(5);mc.options.fov().set(70);
                mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);mc.options.gamma().set(.5);
                mc.options.particles().set(net.minecraft.server.level.ParticleStatus.MINIMAL);mc.options.pauseOnLostFocus=false;
                mc.options.hideGui=true;mc.options.save();initialized=true;save("RUNNING");
            }
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.setYRot(135);mc.player.setXRot(60);mc.player.yRotO=135;mc.player.xRotO=60;
            if(busy)return;
            if(index==10){save("CAPTURES_COMPLETE_NOT_TERRAIN_OR_GAMEPLAY_APPROVAL");ending=true;mc.stop();return;}
            int w=index/5;String name=SpatialBaselineVerification.WORLDS[index%5];int x=SpatialBaselineVerification.CENTERS[w][0],z=SpatialBaselineVerification.CENTERS[w][1];
            var key=name.equals("overworld")?Level.OVERWORLD:PhaseWorldResolver.levelKey(PhaseId.valueOf(name.toUpperCase(Locale.ROOT)));
            if(!submitted){
                busy=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                server.execute(()->{try{
                    var player=Objects.requireNonNull(server.getPlayerList().getPlayer(uuid));var level=Objects.requireNonNull(server.getLevel(key));
                    level.getGameRules().set(GameRules.ADVANCE_TIME,false,server);level.getGameRules().set(GameRules.ADVANCE_WEATHER,false,server);
                    var commands=server.getCommands();var source=server.createCommandSourceStack().withLevel(level);
                    commands.performPrefixedCommand(source,"time set 6000");commands.performPrefixedCommand(source,"weather clear");
                    level.setRainLevel(0);level.setThunderLevel(0);
                    if(!level.getBlockState(new BlockPos(x,240,z)).isAir())throw new IllegalStateException("Frozen camera is occluded: "+name);
                    player.setGameMode(GameType.SPECTATOR);
                    if(!player.teleportTo(level,x+.5,240,z+.5,Set.of(),135,60,true))throw new IllegalStateException("Camera teleport rejected");
                }catch(Throwable problem){error=problem;}finally{busy=false;}});
                submitted=true;wait=0;settled=0;return;
            }
            if(!mc.level.dimension().equals(key))return;
            if(++wait>2400)throw new IllegalStateException("Rendering timeout "+name+"/"+w);
            boolean ready=true;
            for(int dx=-10;dx<=10;dx++)for(int dz=-10;dz<=10;dz++)if(dx*dx+dz*dz<=100 && mc.level.getChunkSource().getChunk((x>>4)+dx,(z>>4)+dz,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null)ready=false;
            if(wait<360 || !ready || !mc.levelRenderer.hasRenderedAllSections()){settled=0;return;}
            if(++settled<20)return;
            if(mc.gameRenderer.getMainCamera().yRot()!=135 || mc.gameRenderer.getMainCamera().xRot()!=60)throw new IllegalStateException("Camera rotation mismatch");
            if(mc.options.simulationDistance().get()!=5)throw new IllegalStateException("Unmatched simulation distance");
            if(mc.getWindow().getWidth()!=1280 || mc.getWindow().getHeight()!=720)throw new IllegalStateException("Unexpected framebuffer size");
            var row=new JsonObject();row.addProperty("world",name);row.addProperty("window",SpatialBaselineVerification.WINDOWS[w]);
            row.addProperty("x",mc.player.getX());row.addProperty("y",mc.player.getY());row.addProperty("z",mc.player.getZ());row.addProperty("yaw",mc.gameRenderer.getMainCamera().yRot());row.addProperty("pitch",mc.gameRenderer.getMainCamera().xRot());
            row.addProperty("time",mc.level.getOverworldClockTime());row.addProperty("rain",mc.level.getRainLevel(1));row.addProperty("thunder",mc.level.getThunderLevel(1));
            if(mc.level.getOverworldClockTime()!=6000 || mc.level.getRainLevel(1)!=0 || mc.level.getThunderLevel(1)!=0)throw new IllegalStateException("Unmatched environment");
            row.addProperty("fov",mc.options.fov().get());row.addProperty("graphics",mc.options.graphicsPreset().get().toString());row.addProperty("graphicsBase","FANCY");row.addProperty("clouds",mc.options.cloudStatus().get().toString());row.addProperty("particles",mc.options.particles().get().toString());row.addProperty("ambientOcclusion",mc.options.ambientOcclusion().get());row.addProperty("mipmapLevels",mc.options.mipmapLevels().get());row.addProperty("biomeBlendRadius",mc.options.biomeBlendRadius().get());row.addProperty("gamma",mc.options.gamma().get());
            row.addProperty("viewDistance",mc.options.renderDistance().get());row.addProperty("simulationDistance",mc.options.simulationDistance().get());row.addProperty("settleTicks",wait);
            row.addProperty("fps",mc.getFps());row.addProperty("averageIntegratedTickMs",mc.getSingleplayerServer().getAverageTickTimeNanos()/1e6);
            row.addProperty("sections",mc.levelRenderer.getSectionStatistics());row.addProperty("resourcePacks",mc.options.resourcePacks.toString());
            String file="wph016-"+seed+"-"+SpatialBaselineVerification.WINDOWS[w]+"-"+name+".png";row.addProperty("screenshot",file);
            Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),1,text->System.out.println("WPH016 screenshot "+text.getString()));
            photos.add(row);save("RUNNING");System.out.println("WPH016 native photo "+row);index++;submitted=false;
        }catch(Exception problem){ending=true;report.addProperty("failure",problem.toString());try{save("FAIL");}catch(Exception write){problem.addSuppressed(write);}problem.printStackTrace();mc.stop();}
    }
    private void save(String status)throws Exception{report.addProperty("status",status);Files.writeString(Path.of("spatial-client.json"),MvpWorldVerification.JSON.toJson(report));}
}
