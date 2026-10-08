package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import dev.fangbm.worldphase.phase.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real server acceptance probe. Present only in the isolated verification run, not in the mod JAR. */
@Mod(value="worldphase_verification", dist=Dist.DEDICATED_SERVER)
public final class SmoothVerification {
    private static final int[][] POINTS = {{0,0},{256,256},{-512,768},{1024,-1024},{2048,2048},
            {-2048,-2048},{4096,0},{0,4096},{-4096,2048},{8192,-4096}};

    public SmoothVerification() {
        NeoForge.EVENT_BUS.addListener(this::verify);
    }

    private void verify(ServerStartedEvent event) {
        try {
            var server = event.getServer();
            var smooth = server.getLevel(PhaseWorldResolver.levelKey(PhaseId.SMOOTH));
            var overworld = server.overworld();
            require(smooth != null, "Smooth dimension must exist");
            require(overworld.getSeed() == 123456789L && smooth.getSeed() == overworld.getSeed(), "Both dimensions must use seed 123456789");
            JsonObject report = new JsonObject();
            report.addProperty("seed", smooth.getSeed());
            JsonArray samples = new JsonArray();
            for (int[] point : POINTS) {
                JsonObject sample = new JsonObject();
                sample.addProperty("x", point[0]);sample.addProperty("z", point[1]);
                sample.add("overworld", column(overworld, point[0], point[1]));
                sample.add("smooth", column(smooth, point[0], point[1]));
                samples.add(sample);
                System.out.println("WPH006 sample " + sample);
            }
            report.add("samples", samples);
            // A server-side simulated player exercises Vanilla's actual dimension teleport path.
            // This is not a connected graphical-client observation or a gameplay transfer service.
            var player = FakePlayerFactory.getMinecraft(overworld);
            boolean entered = player.teleportTo(smooth,0.5,160,0.5,java.util.Set.of(),0,0,true);
            require(entered && player.level() == smooth, "Vanilla ServerPlayer teleport must enter Smooth");
            report.addProperty("simulatedPlayerEntered", true);
            player.discard();
            JsonObject structures = new JsonObject();
            for (String tag : new String[]{"village", "mineshaft", "ruined_portal"}) {
                var found = smooth.findNearestMapStructure(TagKey.create(Registries.STRUCTURE,
                        Identifier.withDefaultNamespace(tag)), new BlockPos(0,64,0), 100, false);
                require(found != null, "Vanilla structure tag must locate: " + tag);
                structures.addProperty(tag, found.toShortString());
                System.out.println("WPH006 structure " + tag + " " + found.toShortString());
            }
            report.add("structures", structures);
            Path baseline = Path.of("smooth-baseline.json");
            String content = new GsonBuilder().setPrettyPrinting().create().toJson(report);
            if (Files.exists(baseline)) {
                require(JsonParser.parseString(Files.readString(baseline)).equals(report), "Saved/reloaded block, biome and structure report must match");
                Files.writeString(Path.of("smooth-reload.json"), content);
                System.out.println("WPH006 RELOAD MATCH: every sampled block/biome fingerprint and structure coordinate unchanged");
            } else {
                Files.writeString(baseline, content);
                System.out.println("WPH006 BASELINE SAVED: issue Vanilla save-all flush / stop, then restart this same world");
            }
            server.saveEverything(false, true, true);
            Files.writeString(Path.of("smooth-verification-status.txt"), "PASS\n");
            System.out.println("WPH006 VERIFICATION PASS");
            server.halt(false);
        } catch (Exception error) {
            try { Files.writeString(Path.of("smooth-verification-status.txt"), "FAIL " + error); } catch (Exception ignored) {}
            event.getServer().halt(false);
            throw new IllegalStateException("WPH006 acceptance probe failed", error);
        }
    }

    private static JsonObject column(ServerLevel level, int x, int z) throws Exception {
        level.getChunk(x >> 4, z >> 4);
        int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)-1;
        JsonObject result = new JsonObject();
        result.addProperty("floor", top);
        result.addProperty("surface", level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1);
        result.addProperty("biome", level.getBiome(new BlockPos(x,top,z)).unwrapKey().orElseThrow().identifier().toString());
        result.addProperty("topBlock", level.getBlockState(new BlockPos(x,top,z)).toString());
        result.addProperty("underBlock", level.getBlockState(new BlockPos(x,top-1,z)).toString());
        int caveAir=0, water=0, lava=0;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // Stay inside the central chunk, fingerprint 25 complete columns including surface/features.
        int baseX=(x >> 4)*16+4,baseZ=(z >> 4)*16+4;
        for (int dx=0;dx<5;dx++) for(int dz=0;dz<5;dz++) for(int y=-64;y<320;y++) {
            BlockPos pos = new BlockPos(baseX+dx,y,baseZ+dz);
            var block=level.getBlockState(pos);
            digest.update((block+"|"+level.getBiome(pos).unwrapKey().orElseThrow().identifier()+"\n").getBytes(StandardCharsets.UTF_8));
            if(y<top-12 && y> -60 && block.isAir()) caveAir++;
            if(block.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) water++;
            if(block.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) lava++;
        }
        result.addProperty("caveAirBlocks",caveAir);result.addProperty("waterBlocks",water);result.addProperty("lavaBlocks",lava);
        result.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
        return result;
    }

    private static void require(boolean condition, String message) {
        if(!condition) throw new IllegalStateException(message);
    }
}
