package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import dev.fangbm.worldphase.phase.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Isolated acceptance tooling. No gameplay service; never included in the production JAR. */
@Mod(value="worldphase_verification", dist=Dist.DEDICATED_SERVER)
public final class PhaseVerification {
    private static final int[][] POINTS = {{0,0},{256,256},{-512,768},{1024,-1024},{2048,2048},
            {-2048,-2048},{4096,0},{0,4096},{-4096,2048},{8192,-4096}};

    public PhaseVerification() { NeoForge.EVENT_BUS.addListener(this::verify); }

    private void verify(ServerStartedEvent event) {
        if ("phase-transfer".equals(System.getProperty("worldphase.verification"))) {
            PhaseTransferVerification.verify(event.getServer());
            return;
        }
        if ("terrain-sampling".equals(System.getProperty("worldphase.verification"))) {
            TerrainSamplingVerification.verify(event.getServer());
            return;
        }
        var server=event.getServer();
        try {
            Map<String,ServerLevel> levels=new LinkedHashMap<>();
            levels.put("overworld",server.overworld());
            for(PhaseId id:new PhaseId[]{PhaseId.SMOOTH,PhaseId.RIDGE,PhaseId.CELL,PhaseId.FRACTURE}) {
                var level=server.getLevel(PhaseWorldResolver.levelKey(id));
                require(level!=null,"Missing dimension: "+id);
                levels.put(id.name().toLowerCase(Locale.ROOT),level);
            }
            for(var level:levels.values()) require(level.getSeed()==123456789L,"Every Level must use raw base seed 123456789");
            JsonObject report=new JsonObject();report.addProperty("seed",123456789L);
            JsonArray samples=new JsonArray();
            Map<String,long[]> totals=new LinkedHashMap<>();levels.keySet().forEach(name -> totals.put(name,new long[5]));
            for(int[] point:POINTS) {
                JsonObject sample=new JsonObject();sample.addProperty("x",point[0]);sample.addProperty("z",point[1]);
                for(var entry:levels.entrySet()) {
                    var result=chunk(entry.getValue(),point[0],point[1]);sample.add(entry.getKey(),result);
                    String[] fields={"caveAirBlocks","waterBlocks","lavaBlocks","oreBlocks","vegetationBlocks"};
                    for(int i=0;i<fields.length;i++) totals.get(entry.getKey())[i]+=result.get(fields[i]).getAsLong();
                }
                samples.add(sample);System.out.println("WPH007 FULL chunk " + sample);
            }
            for(var entry:totals.entrySet()) {
                long[] t=entry.getValue();
                require(t[0]>0 && t[1]>0 && t[2]>0 && t[3]>0 && t[4]>0,"Caves, water, lava, ores and vegetation must remain: "+entry.getKey());
                System.out.printf("WPH007 pipeline %s caveAir=%d water=%d lava=%d ores=%d logs/leaves=%d%n",entry.getKey(),t[0],t[1],t[2],t[3],t[4]);
            }
            report.add("samples",samples);
            // Vanilla's actual ServerPlayer teleport, separately from the graphical check.
            var player=FakePlayerFactory.getMinecraft(server.overworld());
            for(var entry:levels.entrySet()) {
                require(player.teleportTo(entry.getValue(),.5,220,.5,Set.of(),0,0,true)
                        && player.level()==entry.getValue(),"Vanilla player must enter "+entry.getKey());
            }
            player.discard();report.addProperty("simulatedPlayerEnteredAll",true);
            JsonObject structures=new JsonObject();
            for(var entry:levels.entrySet()) {
                JsonObject located=new JsonObject();
                for(String tag:new String[]{"village","mineshaft","ruined_portal"}) {
                    var found=entry.getValue().findNearestMapStructure(TagKey.create(Registries.STRUCTURE,
                            Identifier.withDefaultNamespace(tag)),new BlockPos(0,64,0),100,false);
                    require(found!=null,"Structure must locate: "+entry.getKey()+"/"+tag);
                    located.addProperty(tag,found.toShortString());
                }
                structures.add(entry.getKey(),located);
                System.out.println("WPH007 structures "+entry.getKey()+" "+located);
            }
            report.add("structures",structures);
            Path baseline=Path.of("phase-baseline.json");
            String content=new GsonBuilder().setPrettyPrinting().create().toJson(report);
            if(Files.exists(baseline)) {
                require(JsonParser.parseString(Files.readString(baseline)).equals(report),"Saved/reloaded complete block/biome/height/fluid/structure report must match");
                Files.writeString(Path.of("phase-reload.json"),content);
                System.out.println("WPH007 RELOAD MATCH: all 50 full-chunk block/biome/height fingerprints, counts and structure locations unchanged");
            } else {
                Files.writeString(baseline,content);
                System.out.println("WPH007 BASELINE SAVED: restart this same isolated world to compare persisted FULL chunks");
            }
            server.saveEverything(false,true,true);
            Files.writeString(Path.of("phase-verification-status.txt"),"PASS\n");
            System.out.println("WPH007 VERIFICATION PASS");server.halt(false);
        } catch(Exception error) {
            try { Files.writeString(Path.of("phase-verification-status.txt"),"FAIL "+error); } catch(Exception ignored) {}
            server.halt(false);throw new IllegalStateException("WPH007 acceptance failed",error);
        }
    }

    private static JsonObject chunk(ServerLevel level,int x,int z) throws Exception {
        var chunk=level.getChunk(x>>4,z>>4);
        int top=level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)-1;
        JsonObject result=new JsonObject();result.addProperty("floor",top);
        result.addProperty("surface",level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1);
        result.addProperty("biome",level.getBiome(new BlockPos(x,top,z)).unwrapKey().orElseThrow().identifier().toString());
        result.addProperty("topBlock",level.getBlockState(new BlockPos(x,top,z)).toString());
        result.addProperty("underBlock",level.getBlockState(new BlockPos(x,top-1,z)).toString());
        MessageDigest blocks=MessageDigest.getInstance("SHA-256"),biomes=MessageDigest.getInstance("SHA-256"),heights=MessageDigest.getInstance("SHA-256");
        Map<BlockState,byte[]> encoded=new HashMap<>();Map<Identifier,byte[]> biomeBytes=new HashMap<>();
        int caveAir=0,water=0,lava=0,ores=0,vegetation=0;
        Map<String,Integer> surfaces=new TreeMap<>();
        for(int dx=0;dx<16;dx++) for(int dz=0;dz<16;dz++) {
            int columnX=chunk.getPos().getMinBlockX()+dx,columnZ=chunk.getPos().getMinBlockZ()+dz;
            int floor=level.getHeight(Heightmap.Types.OCEAN_FLOOR,columnX,columnZ)-1;
            int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE,columnX,columnZ)-1;
            heights.update((floor+","+surface+"\n").getBytes(StandardCharsets.UTF_8));
            String surfaceBlock=BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(columnX,floor,columnZ)).getBlock()).toString();
            surfaces.merge(surfaceBlock,1,Integer::sum);
            for(int y=level.getMinY();y<level.getMaxY();y++) {
                BlockPos pos=new BlockPos(columnX,y,columnZ);var block=level.getBlockState(pos);
                blocks.update(encoded.computeIfAbsent(block,b -> (b+"\n").getBytes(StandardCharsets.UTF_8)));
                var biome=level.getBiome(pos).unwrapKey().orElseThrow().identifier();
                biomes.update(biomeBytes.computeIfAbsent(biome,b -> (b+"\n").getBytes(StandardCharsets.UTF_8)));
                if(y<floor-12 && y> -60 && block.isAir()) caveAir++;
                if(block.getFluidState().is(FluidTags.WATER)) water++;
                if(block.getFluidState().is(FluidTags.LAVA)) lava++;
                if(BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath().endsWith("_ore")) ores++;
                if(block.is(BlockTags.LOGS) || block.is(BlockTags.LEAVES)) vegetation++;
            }
        }
        result.addProperty("caveAirBlocks",caveAir);result.addProperty("waterBlocks",water);result.addProperty("lavaBlocks",lava);
        result.addProperty("oreBlocks",ores);result.addProperty("vegetationBlocks",vegetation);
        result.add("surfaceBlocks",new Gson().toJsonTree(surfaces));
        result.addProperty("blockSha256",HexFormat.of().formatHex(blocks.digest()));
        result.addProperty("biomeSha256",HexFormat.of().formatHex(biomes.digest()));
        result.addProperty("heightSha256",HexFormat.of().formatHex(heights.digest()));return result;
    }

    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
