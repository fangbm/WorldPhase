package dev.fangbm.worldphase.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.worldgen.noise.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Preset diagnostics through real codecs/RandomState/base columns, not golden terrain snapshots. */
class PhaseWorldTest {
    private static final long SEED = 123456789L;
    private static final int[][] POINTS = {{0,0},{256,256},{-512,768},{1024,-1024},{2048,2048},
            {-2048,-2048},{4096,0},{0,4096},{-4096,2048},{8192,-4096}};
    private static final int[][] CENTERS = {{-512,768},{2048,2048},{-2048,-2048},{-4096,2048},{8192,-4096}};
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64,384);
    private record World(LevelStem stem, NoiseGeneratorSettings settings, NoiseBasedChunkGenerator generator,
                         RandomState state, RandomState repeated) {
        int height(int x,int z) { return generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,HEIGHT,state)-1; }
    }
    private static final Map<String,World> WORLDS = new LinkedHashMap<>();
    private static JsonObject vanillaJson;
    private static net.minecraft.core.HolderLookup.Provider lookup;

    private static JsonElement resource(String path) {
        try(var reader=new InputStreamReader(Objects.requireNonNull(PhaseWorldTest.class.getResourceAsStream(path)),StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch(Exception error) { throw new IllegalStateException(path,error); }
    }

    private static JsonElement resolve(JsonElement element) {
        if(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().startsWith("worldphase:")) {
            String name=element.getAsString().substring(11);
            boolean settings=Arrays.stream(PhaseId.values()).anyMatch(id -> id.name().toLowerCase(Locale.ROOT).equals(name));
            var entry=resource("/data/worldphase/worldgen/"+(settings?"noise_settings":"density_function")+"/"+name+".json");
            // A registry entry uses DIRECT_CODEC: a root holder alias is invalid even though
            // nested holder references are legal. Do not let this resolver mask that distinction.
            assertTrue(entry.isJsonObject() || (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isNumber()),
                    "Registry entry must be a direct codec value, not a root holder alias: "+name);
            return resolve(entry);
        }
        if(element.isJsonObject()) {
            JsonObject out=new JsonObject();
            element.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(),e.getKey().equals("type")?e.getValue():resolve(e.getValue())));
            return out;
        }
        if(element.isJsonArray()) { JsonArray out=new JsonArray();element.getAsJsonArray().forEach(e -> out.add(resolve(e)));return out; }
        return element;
    }

    @BeforeAll static void load() throws Exception {
        lookup=VanillaRegistries.createLookup();
        var ops=RegistryOps.create(JsonOps.INSTANCE,lookup);
        var noises=lookup.lookupOrThrow(Registries.NOISE);
        for(String name:new String[]{"smooth","ridge","cell","fracture"}) {
            var stem=LevelStem.CODEC.parse(ops,resolve(resource("/data/worldphase/dimension/"+name+".json"))).getOrThrow();
            var generator=(NoiseBasedChunkGenerator)stem.generator();
            var settings=generator.generatorSettings().value();
            WORLDS.put(name,new World(stem,settings,generator,RandomState.create(settings,noises,SEED),RandomState.create(settings,noises,SEED)));
        }
        var vanilla=lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var generator=new NoiseBasedChunkGenerator(WORLDS.get("smooth").generator.getBiomeSource(),Holder.direct(vanilla));
        WORLDS.put("overworld",new World(null,vanilla,generator,RandomState.create(vanilla,noises,SEED),RandomState.create(vanilla,noises,SEED)));
        try(var jar=new java.util.zip.ZipFile(System.getProperty("worldphase.minecraftJar"));
            var reader=new InputStreamReader(jar.getInputStream(jar.getEntry("data/minecraft/worldgen/noise_settings/overworld.json")),StandardCharsets.UTF_8)) {
            vanillaJson=JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test void allResourcesDecodeAndKeepVanillaClimateAndPipeline() {
        var vanilla=WORLDS.get("overworld").settings;
        for(var entry:WORLDS.entrySet()) {
            String name=entry.getKey(); if(name.equals("overworld")) continue;
            World world=entry.getValue();
            assertEquals("worldphase:"+name,PhaseCatalog.get(PhaseId.valueOf(name.toUpperCase(Locale.ROOT))).levelKey().identifier().toString());
            var dimension=resource("/data/worldphase/dimension/"+name+".json").getAsJsonObject();
            assertEquals("minecraft:overworld",dimension.get("type").getAsString());
            var generatorJson=dimension.getAsJsonObject("generator");
            assertEquals("minecraft:noise",generatorJson.get("type").getAsString());
            assertEquals(JsonParser.parseString("{\"type\":\"minecraft:multi_noise\",\"preset\":\"minecraft:overworld\"}"),generatorJson.get("biome_source"));
            assertTrue(world.stem.seedOverride().isEmpty());
            assertEquals(vanilla.noiseSettings(),world.settings.noiseSettings());
            assertEquals(vanilla.surfaceRule(),world.settings.surfaceRule());
            assertEquals(vanilla.defaultBlock(),world.settings.defaultBlock());
            assertEquals(vanilla.defaultFluid(),world.settings.defaultFluid());
            assertTrue(world.settings.isAquifersEnabled());assertTrue(world.settings.oreVeinsEnabled());
            JsonObject ours=resource("/data/worldphase/worldgen/noise_settings/"+name+".json").getAsJsonObject();
            for(String field:vanillaJson.keySet()) if(!field.equals("noise_router")) assertEquals(vanillaJson.get(field),ours.get(field),name+" "+field);
            var router=ours.getAsJsonObject("noise_router");var original=vanillaJson.getAsJsonObject("noise_router");
            for(String field:original.keySet()) if(!field.equals("final_density") && !field.equals("preliminary_surface_level"))
                assertEquals(original.get(field),router.get(field),name+" climate/aquifer/ore "+field);
            // Reversing just the intended references must recover the entire Vanilla density tree.
            String density=router.get("final_density").toString().replace("worldphase:"+name+"_terrain","minecraft:overworld/sloped_cheese");
            assertEquals(original.get("final_density"),JsonParser.parseString(density),name+" cave/slide/blending tree");
        }
    }

    @Test void sameSeedColumnsRepeatAndAllInterpretationsDiffer() {
        Map<String,int[]> heights=new LinkedHashMap<>();
        WORLDS.keySet().forEach(name -> heights.put(name,new int[POINTS.length]));
        for(int i=0;i<POINTS.length;i++) {
            int x=POINTS[i][0],z=POINTS[i][1];StringBuilder row=new StringBuilder("WPH007 ("+x+","+z+")");
            for(var entry:WORLDS.entrySet()) {
                World world=entry.getValue();
                var a=world.generator.getBaseColumn(x,z,HEIGHT,world.state);
                var b=world.generator.getBaseColumn(x,z,HEIGHT,world.repeated);
                for(int y=-64;y<320;y++) assertEquals(a.getBlock(y),b.getBlock(y),entry.getKey()+" y="+y);
                int top=world.height(x,z);heights.get(entry.getKey())[i]=top;row.append(" ").append(entry.getKey()).append("=").append(top);
            }
            System.out.println(row);
        }
        var names=new ArrayList<>(heights.keySet());
        for(int i=0;i<names.size();i++) for(int j=i+1;j<names.size();j++) {
            int different=0;double delta=0;
            for(int k=0;k<POINTS.length;k++) { int d=Math.abs(heights.get(names.get(i))[k]-heights.get(names.get(j))[k]);if(d>0) different++;delta+=d; }
            System.out.printf("WPH007 pair %s/%s differences=%d/10 mean absolute height delta=%.2f%n",names.get(i),names.get(j),different,delta/POINTS.length);
            assertTrue(different>=7,"Distinct interpretations: "+names.get(i)+"/"+names.get(j));
        }
    }

    @Test void macroSignaturesAndClimateStayDistinct() {
        Map<String,Double> slopes=new LinkedHashMap<>();Map<String,Integer> relief=new LinkedHashMap<>();
        for(var entry:WORLDS.entrySet()) {
            long sum=0;int edges=0,min=320,max=-64,belowSea=0,high=0,connectedHigh=0;World world=entry.getValue();
            for(int[] center:CENTERS) {
                int[][] grid=new int[5][5];
                for(int i=0;i<5;i++) for(int j=0;j<5;j++) {
                    int x=center[0]+(i-2)*64,z=center[1]+(j-2)*64;
                    grid[i][j]=world.height(x,z);min=Math.min(min,grid[i][j]);max=Math.max(max,grid[i][j]);
                    if(grid[i][j]<63) belowSea++;
                    assertEquals(WORLDS.get("overworld").state.sampler().sample(x>>2,20,z>>2),world.state.sampler().sample(x>>2,20,z>>2));
                    if(i>0) {sum+=Math.abs(grid[i][j]-grid[i-1][j]);edges++;}
                    if(j>0) {sum+=Math.abs(grid[i][j]-grid[i][j-1]);edges++;}
                }
                for(int i=0;i<5;i++) for(int j=0;j<5;j++) if(grid[i][j]>100) {
                    high++;
                    if((i>0 && grid[i-1][j]>90)||(i<4 && grid[i+1][j]>90)
                            ||(j>0 && grid[i][j-1]>90)||(j<4 && grid[i][j+1]>90)) connectedHigh++;
                }
            }
            slopes.put(entry.getKey(),(double)sum/edges);relief.put(entry.getKey(),max-min);
            System.out.printf("WPH007 macro %s samples=125 edges=%d min=%d max=%d relief=%d mean64Delta=%.3f%n",entry.getKey(),edges,min,max,max-min,(double)sum/edges);
            if(entry.getKey().equals("ridge")) {
                System.out.printf("WPH007 Ridge below-sea valleys=%d/125 high samples=%d connected to neighboring high ground=%d%n",belowSea,high,connectedHigh);
                assertTrue(belowSea>=10,"Ridge should include real low valleys, not uplift every column");
                assertTrue(high>=10 && connectedHigh>=high*.7,"Crests should continue through neighboring samples, not isolated needles");
            }
        }
        assertTrue(slopes.get("smooth")<slopes.get("overworld")*.85);
        assertTrue(slopes.get("ridge")>slopes.get("smooth")*1.5);
        assertTrue(relief.get("ridge")>relief.get("smooth")*1.5);
        assertTrue(relief.get("smooth")>10);
    }

    @Test void cellCentersFormBasinsWithRaisedEnclosingRims() {
        var config=resource("/data/worldphase/worldgen/density_function/cell_noise.json").getAsJsonObject();
        var noise=new WorleyNoise(config.get("frequency").getAsDouble());var world=WORLDS.get("cell");
        double centerSum=0,rimSum=0;int centers=0,rims=0,enclosed=0,candidates=0;
        // An irregular feature layout, sampled without accessing/copying the point hashing algorithm.
        for(int x=-768;x<=768;x+=32) for(int z=-768;z<=768;z+=32) {
            double distance=noise.sample(SEED,x,z);
            if(distance<.10) {
                int floor=world.height(x,z);centerSum+=floor;centers++;
                if(x%64==0 && z%64==0) {
                    int raised=0;
                    for(int direction=0;direction<8;direction++) {
                        double angle=direction*Math.PI/4;
                        int outer=world.height(x+(int)Math.round(Math.cos(angle)*160),z+(int)Math.round(Math.sin(angle)*160));
                        if(outer>floor+12) raised++;
                    }
                    candidates++;if(raised>=6) enclosed++;
                }
            }
            if(distance>.62) {rimSum+=world.height(x,z);rims++;}
        }
        assertTrue(centers>20 && rims>20 && candidates>5);
        double centerMean=centerSum/centers,rimMean=rimSum/rims;
        System.out.printf("WPH007 Cell center samples=%d mean=%.2f far-point rim samples=%d mean=%.2f enclosed basins=%d/%d (6/8 radial directions rise >12 blocks at r=160)%n",centers,centerMean,rims,rimMean,enclosed,candidates);
        assertTrue(rimMean>centerMean+50,"F1 remap must create strong center/interior/rim roles in generated columns");
        assertTrue(enclosed>=candidates*.5,"Most sampled feature centers should have enclosing rising terrain");
    }

    @Test void fractureWarpDisplacesTheFieldRatherThanOnlyAmplifyingIt() {
        var config=resource("/data/worldphase/worldgen/density_function/fracture_noise.json").getAsJsonObject();
        double frequency=config.get("frequency").getAsDouble();
        var warped=new DomainWarpNoise(frequency,config.get("warp_frequency").getAsDouble(),config.get("warp_strength").getAsDouble());
        var unwarped=new GradientNoise(frequency);
        double error=0;int changed=0,samples=0;
        for(int x=-1024;x<=1024;x+=64) for(int z=-1024;z<=1024;z+=64) {
            double a=warped.sample(SEED,x,z),b=unwarped.sample(SEED,x,z);
            error+=Math.abs(a-b);if(Math.abs(a-b)>.05) changed++;samples++;
        }
        System.out.printf("WPH007 Fracture same-frequency warped/unwarped mean absolute kernel delta=%.5f changed >.05=%d/%d%n",error/samples,changed,samples);
        assertTrue(changed>samples*.5);
        assertTrue(error/samples>.07);
    }

    @Test void disablingOnlyWarpChangesActualFractureTerrain() {
        var json=resolve(resource("/data/worldphase/dimension/fracture.json"));
        removeWarp(json);
        var stem=LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,lookup),json).getOrThrow();
        var generator=(NoiseBasedChunkGenerator)stem.generator();
        var state=RandomState.create(generator.generatorSettings().value(),lookup.lookupOrThrow(Registries.NOISE),SEED);
        int changed=0,samples=0;long delta=0;
        for(int[] center:CENTERS) for(int dx=-128;dx<=128;dx+=64) for(int dz=-128;dz<=128;dz+=64) {
            int x=center[0]+dx,z=center[1]+dz;
            int d=Math.abs(WORLDS.get("fracture").height(x,z)-(generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,HEIGHT,state)-1));
            if(d>8) changed++;delta+=d;samples++;
        }
        System.out.printf("WPH007 Fracture actual terrain warp on/off: same amplitude/frequency/seed mean height delta=%.2f changed >8=%d/%d%n",(double)delta/samples,changed,samples);
        assertTrue(changed>samples*.5);assertTrue((double)delta/samples>10);
    }

    private static void removeWarp(JsonElement element) {
        if(element.isJsonObject()) {
            JsonObject object=element.getAsJsonObject();
            if(object.has("algorithm") && object.get("algorithm").getAsString().equals("fracture")) object.addProperty("warp_strength",0.0);
            object.entrySet().forEach(e -> removeWarp(e.getValue()));
        } else if(element.isJsonArray()) element.getAsJsonArray().forEach(PhaseWorldTest::removeWarp);
    }
}
