package dev.fangbm.worldphase.verification.v2.r2;
import dev.fangbm.worldphase.verification.v2.*;
import com.google.gson.*;
import net.minecraft.world.level.levelgen.*;
/** Pure in-memory settings; never registered as a dimension or installed on a Level. */
public final class R2Fixture {
    public final NativeV2Fixture base;
    public R2Fixture(NativeV2Fixture base){this.base=base;}
    public JsonObject json(String field){JsonObject j=base.json(field.startsWith("smooth")?"smooth_core":"ridge_core");j.addProperty("type","worldphase:prototype_v2/analytic_r2");j.addProperty("profile",R2.PROFILE);j.addProperty("field",field);if(field.endsWith("protection"))j.remove("detail");return j;}
    public R2Density leaf(String field){return (R2Density)DensityFunction.DIRECT_CODEC.parse(base.ops,json(field)).getOrThrow();}
    public record Composition(DensityFunction controlled,DensityFunction nativeFinal,DensityFunction solid,DensityFunction protection,DensityFunction density,DensityFunction surface,int substitutions){}
    public Composition compose(boolean ridge){String phase=ridge?"ridge":"smooth";var controlled=leaf(phase+"_controlled");var p=leaf(phase+"_protection");int[] count={0};var json=DensityFunction.DIRECT_CODEC.encodeStart(base.ops,controlled).getOrThrow();
        var nativeFinal=DensityFunction.DIRECT_CODEC.parse(base.ops,replace(base.vanillaFinal,json,count)).getOrThrow();if(count[0]!=3)throw new IllegalStateException("SLOPED_CHEESE_SUBSTITUTIONS");
        var solid=NativeV2Fixture.post(NativeV2Fixture.slide(controlled));var density=NativeV2Fixture.mix(p,nativeFinal,solid);
        var surface=DensityFunctions.findTopSurface(solid,DensityFunctions.constant(ridge?200:152),-64,8);
        return new Composition(controlled,nativeFinal,solid,p,density,surface,count[0]);
    }
    private static JsonElement replace(JsonElement v,JsonElement terrain,int[] count){
        if(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString()&&v.getAsString().equals("minecraft:overworld/sloped_cheese")){count[0]++;return terrain.deepCopy();}
        if(v.isJsonArray()){var out=new JsonArray();v.getAsJsonArray().forEach(x->out.add(replace(x,terrain,count)));return out;}
        if(v.isJsonObject()){var out=new JsonObject();v.getAsJsonObject().entrySet().forEach(e->out.add(e.getKey(),replace(e.getValue(),terrain,count)));return out;}return v.deepCopy();
    }
}
