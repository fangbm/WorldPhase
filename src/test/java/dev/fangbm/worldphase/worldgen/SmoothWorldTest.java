package dev.fangbm.worldphase.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.phase.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SmoothWorldTest {
    private static HolderLookup.Provider lookup;
    private static LevelStem smooth;
    private static NoiseGeneratorSettings settings;

    private static JsonElement resource(String path) {
        try (var reader = new InputStreamReader(SmoothWorldTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception error) { throw new IllegalStateException(path, error); }
    }

    // Resolve our own resource references before decoding with Vanilla RegistryOps.
    // Actual dynamic registry loading is separately verified by the dedicated-server run.
    private static JsonElement resolve(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                && element.getAsString().startsWith("worldphase:")) {
            String name = element.getAsString().substring("worldphase:".length());
            String registry = name.equals("smooth") ? "noise_settings" : "density_function";
            return resolve(resource("/data/worldphase/worldgen/" + registry + "/" + name + ".json"));
        }
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(entry -> out.add(entry.getKey(),
                    entry.getKey().equals("type") ? entry.getValue() : resolve(entry.getValue())));
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            element.getAsJsonArray().forEach(value -> out.add(resolve(value)));
            return out;
        }
        return element;
    }

    @BeforeAll static void load() {
        lookup = VanillaRegistries.createLookup();
        JsonElement json = resource("/data/worldphase/dimension/smooth.json");
        smooth = LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, lookup), resolve(json)).getOrThrow();
        settings = ((NoiseBasedChunkGenerator) smooth.generator()).generatorSettings().value();
    }

    @Test void dimensionAndVanillaPipelineDecode() {
        assertEquals("worldphase:smooth", PhaseCatalog.get(PhaseId.SMOOTH).levelKey().identifier().toString());
        assertTrue(smooth.seedOverride().isEmpty());
        var vanilla = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        assertEquals(vanilla.noiseSettings(), settings.noiseSettings());
        assertEquals(vanilla.defaultBlock(), settings.defaultBlock());
        assertEquals(vanilla.defaultFluid(), settings.defaultFluid());
        assertEquals(vanilla.seaLevel(), settings.seaLevel());
        assertTrue(settings.isAquifersEnabled());
        assertTrue(settings.oreVeinsEnabled());
        assertEquals(vanilla.surfaceRule(), settings.surfaceRule());
        JsonObject ours = resource("/data/worldphase/worldgen/noise_settings/smooth.json").getAsJsonObject().getAsJsonObject("noise_router");
        JsonObject original;
        try (var jar = new java.util.zip.ZipFile(System.getProperty("worldphase.minecraftJar"));
             var reader = new InputStreamReader(jar.getInputStream(jar.getEntry(
                     "data/minecraft/worldgen/noise_settings/overworld.json")), StandardCharsets.UTF_8)) {
            original = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("noise_router");
        } catch (Exception error) { throw new IllegalStateException(error); }
        for (String field : ours.keySet()) {
            if (!field.equals("final_density") && !field.equals("preliminary_surface_level")) assertEquals(original.get(field), ours.get(field), field);
        }
    }

    @Test void fixedSeedColumnsAreDeterministicAndRetainRelief() {
        var generator = (NoiseBasedChunkGenerator) smooth.generator();
        var vanilla = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var original = new NoiseBasedChunkGenerator(generator.getBiomeSource(), net.minecraft.core.Holder.direct(vanilla));
        var noises = lookup.lookupOrThrow(Registries.NOISE);
        var state = RandomState.create(settings, noises, 123456789L);
        var repeated = RandomState.create(settings, noises, 123456789L);
        var baseline = RandomState.create(vanilla, noises, 123456789L);
        var height = LevelHeightAccessor.create(-64, 384);
        int differences = 0, min = 320, max = -64;
        for (int[] xz : new int[][]{{0,0},{256,256},{-512,768},{1024,-1024},{2048,2048},{-2048,-2048},{4096,0},{0,4096},{-4096,2048},{8192,-4096}}) {
            int x = xz[0], z = xz[1];
            var a = generator.getBaseColumn(x,z,height,state);
            var b = generator.getBaseColumn(x,z,height,repeated);
            var v = original.getBaseColumn(x,z,height,baseline);
            int top = -64, vanillaTop = -64;
            for (int y=-64;y<320;y++) {
                assertEquals(a.getBlock(y), b.getBlock(y));
                if (!a.getBlock(y).isAir() && a.getBlock(y).getFluidState().isEmpty()) top=y;
                if (!v.getBlock(y).isAir() && v.getBlock(y).getFluidState().isEmpty()) vanillaTop=y;
            }
            if (top != vanillaTop) differences++;
            min=Math.min(min,top);max=Math.max(max,top);
            System.out.printf("WPH006 column (%d,%d) Vanilla=%d Smooth=%d%n",x,z,vanillaTop,top);
        }
        assertTrue(differences >= 5);
        assertTrue(max-min > 10, "Smooth should retain hills and ocean relief");
        assertTrue(min < 63 && max > 63);
    }

    @Test void macroSlopesAreReducedAndClimateRoutingIsUnchanged() {
        var generator = (NoiseBasedChunkGenerator) smooth.generator();
        var vanilla = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var original = new NoiseBasedChunkGenerator(generator.getBiomeSource(), net.minecraft.core.Holder.direct(vanilla));
        var noises = lookup.lookupOrThrow(Registries.NOISE);
        var state = RandomState.create(settings,noises,123456789L);
        var baseline = RandomState.create(vanilla,noises,123456789L);
        var height = LevelHeightAccessor.create(-64,384);
        long originalSlope=0, smoothSlope=0;
        int edges=0;
        // Several neighborhoods: measure 64-block macro changes, not only spawn.
        for (int[] center : new int[][]{{-512,768},{2048,2048},{-2048,-2048},{-4096,2048},{8192,-4096}}) {
            int[][] a = new int[5][5], b = new int[5][5];
            for(int i=0;i<5;i++) for(int j=0;j<5;j++) {
                int x=center[0]+(i-2)*64,z=center[1]+(j-2)*64;
                a[i][j]=original.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,height,baseline);
                b[i][j]=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,height,state);
                assertEquals(baseline.sampler().sample(x>>2,20,z>>2),state.sampler().sample(x>>2,20,z>>2));
                if(i>0) { originalSlope+=Math.abs(a[i][j]-a[i-1][j]);smoothSlope+=Math.abs(b[i][j]-b[i-1][j]);edges++; }
                if(j>0) { originalSlope+=Math.abs(a[i][j]-a[i][j-1]);smoothSlope+=Math.abs(b[i][j]-b[i][j-1]);edges++; }
            }
        }
        System.out.printf("WPH006 macro edges=%d mean absolute 64-block height change Vanilla=%.3f Smooth=%.3f%n",
                edges,(double)originalSlope/edges,(double)smoothSlope/edges);
        assertTrue(smoothSlope < originalSlope*0.85, "Preset should reduce macro slope without snapshotting exact heights");
    }
}
