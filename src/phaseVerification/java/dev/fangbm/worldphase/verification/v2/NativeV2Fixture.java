package dev.fangbm.worldphase.verification.v2;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** In-memory registry/settings fixture only. No Level, dimension JSON, ChunkAccess or disk world. */
public final class NativeV2Fixture {
    public final HolderLookup.Provider lookup;
    public final NoiseGeneratorSettings vanilla;
    public final RegistryOps<JsonElement> ops;
    public final JsonElement vanillaFinal;
    public static ResourceKey<NormalNoise.NoiseParameters> detail(String phase) {
        return ResourceKey.create(Registries.NOISE,Identifier.parse("worldphase:prototype_v2/"+phase+"/detail_r1"));
    }
    public NativeV2Fixture() throws Exception {
        R1.verifyManifest(Path.of(System.getProperty("worldphase.projectRoot")));
        HolderLookup.Provider original=VanillaRegistries.createLookup();
        var noiseProvider=new RegistrySetBuilder().add(Registries.NOISE,ctx->{
            original.lookupOrThrow(Registries.NOISE).listElements().forEach(h->ctx.register(h.key(),h.value()));
            ctx.register(detail("smooth"),new NormalNoise.NoiseParameters(0,1));
            ctx.register(detail("ridge"),new NormalNoise.NoiseParameters(0,1));
        }).build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var noises=noiseProvider.lookupOrThrow(Registries.NOISE);
        lookup=HolderLookup.Provider.create(java.util.stream.Stream.concat(original.listRegistries().filter(r->!r.key().equals(Registries.NOISE)),java.util.stream.Stream.of(noises)));
        vanilla=original.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var originalOps=original.createSerializationContext(JsonOps.INSTANCE);
        var noiseOps=noiseProvider.createSerializationContext(JsonOps.INSTANCE);
        // RegistrySetBuilder holders use its UniversalOwner, not the exposed lookup wrapper.
        // Preserve each provider's actual serialization owner when combining registries.
        ops=RegistryOps.create(JsonOps.INSTANCE,new RegistryOps.RegistryInfoLookup() {
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return key.equals(Registries.NOISE)?noiseOps.lookupProvider.lookup(key):originalOps.lookupProvider.lookup(key);
            }
        });
        try(ZipFile zip=new ZipFile(System.getProperty("worldphase.minecraftJar"))) {
            var entry=zip.getEntry("data/minecraft/worldgen/noise_settings/overworld.json");
            byte[] bytes=zip.getInputStream(entry).readAllBytes();
            String got=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!got.equals("f525ae55efa6da365eade5ce8d4be9cd22a161117af7c56ce82d27e6d237824e"))throw new IllegalStateException("Pinned Vanilla noise settings changed");
            vanillaFinal=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("noise_router").get("final_density");
        }
    }
    public JsonObject json(String field) {
        JsonObject j=new JsonObject();j.addProperty("type","worldphase:prototype_v2/analytic_r1");
        j.addProperty("profile",R1.PROFILE);j.addProperty("field",field);j.addProperty("reference","minecraft:overworld/continents");
        if(field.endsWith("_core"))j.addProperty("detail",detail(field.startsWith("smooth")?"smooth":"ridge").identifier().toString());
        return j;
    }
    public PrototypeAnalyticDensityFunction leaf(String field) {return (PrototypeAnalyticDensityFunction)DensityFunction.DIRECT_CODEC.parse(ops,json(field)).getOrThrow();}
    public static DensityFunction mix(DensityFunction weight,DensityFunction first,DensityFunction second) {
        return DensityFunctions.add(DensityFunctions.mul(DensityFunctions.add(DensityFunctions.constant(1),DensityFunctions.mul(DensityFunctions.constant(-1),weight)),first),
                DensityFunctions.mul(weight,second));
    }
    public static DensityFunction slide(DensityFunction d) {
        DensityFunction top=mix(DensityFunctions.yClampedGradient(240,256,1,0),DensityFunctions.constant(-.078125),d);
        return mix(DensityFunctions.yClampedGradient(-64,-40,0,1),DensityFunctions.constant(.1171875),top);
    }
    public static DensityFunction post(DensityFunction d) {
        return DensityFunctions.mul(DensityFunctions.constant(.64),DensityFunctions.interpolated(DensityFunctions.blendDensity(d))).squeeze();
    }
    public record Composition(DensityFunction terrain,DensityFunction nativeFinal,DensityFunction commonFinal,
                              DensityFunction protection,DensityFunction finalDensity,DensityFunction surfaceReference,DensityFunction surface,int substitutions) {}
    public Composition compose(boolean ridge) {
        DensityFunction common=leaf("common_density"),weight=leaf("node_weight"),p=leaf("protection");
        DensityFunction terrain=mix(weight,common,leaf(ridge?"ridge_core":"smooth_core"));
        JsonElement encoded=DensityFunction.DIRECT_CODEC.encodeStart(ops,terrain).getOrThrow();
        int[] count={0};JsonElement ast=replace(vanillaFinal,encoded,count);
        if(count[0]!=3)throw new IllegalStateException("Expected all three sloped_cheese substitutions");
        DensityFunction nativeFinal=DensityFunction.DIRECT_CODEC.parse(ops,ast).getOrThrow(),fcommon=post(slide(common));
        DensityFunction density=mix(p,nativeFinal,fcommon);
        DensityFunction surfaceReference=mix(p,post(slide(terrain)),fcommon);
        DensityFunction surface=DensityFunctions.findTopSurface(surfaceReference,DensityFunctions.constant(ridge?200:152),-64,8);
        return new Composition(terrain,nativeFinal,fcommon,p,density,surfaceReference,surface,count[0]);
    }
    private static JsonElement replace(JsonElement value,JsonElement terrain,int[] count) {
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().equals("minecraft:overworld/sloped_cheese")){count[0]++;return terrain.deepCopy();}
        if(value.isJsonObject()) {
            JsonObject result=new JsonObject();value.getAsJsonObject().entrySet().forEach(e->result.add(e.getKey(),replace(e.getValue(),terrain,count)));return result;
        }
        if(value.isJsonArray()) {JsonArray result=new JsonArray();value.getAsJsonArray().forEach(e->result.add(replace(e,terrain,count)));return result;}
        return value.deepCopy();
    }
    @SuppressWarnings("deprecation")
    public NoiseGeneratorSettings settings(DensityFunction density,DensityFunction surface) {
        NoiseRouter b=vanilla.noiseRouter();
        NoiseRouter r=new NoiseRouter(b.barrierNoise(),b.fluidLevelFloodednessNoise(),b.fluidLevelSpreadNoise(),b.lavaNoise(),
                b.temperature(),b.vegetation(),b.continents(),b.erosion(),b.depth(),b.ridges(),surface,density,b.veinToggle(),b.veinRidged(),b.veinGap());
        return new NoiseGeneratorSettings(vanilla.noiseSettings(),vanilla.defaultBlock(),vanilla.defaultFluid(),r,vanilla.surfaceRule(),
                vanilla.spawnTarget(),vanilla.seaLevel(),vanilla.disableMobGeneration(),vanilla.isAquifersEnabled(),vanilla.oreVeinsEnabled(),vanilla.useLegacyRandomSource());
    }
    public RandomState state(NoiseGeneratorSettings settings,long seed){return RandomState.create(settings,lookup.lookupOrThrow(Registries.NOISE),seed);}
    public static Set<V2Context> contexts(DensityFunction f) {
        Set<V2Context> set=Collections.newSetFromMap(new IdentityHashMap<>());
        f.mapAll(q->{if(q instanceof PrototypeAnalyticDensityFunction leaf)set.add(leaf.context());return q;});return set;
    }
}
