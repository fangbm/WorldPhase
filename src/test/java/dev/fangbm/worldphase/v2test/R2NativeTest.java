package dev.fangbm.worldphase.v2test;
import dev.fangbm.worldphase.verification.v2.*;
import dev.fangbm.worldphase.verification.v2.r2.*;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseSeedVisitor;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.LevelHeightAccessor;
import java.util.*;

class R2NativeTest {
    static NativeV2Fixture base;static R2Fixture fixture;
    @BeforeAll static void init()throws Exception{base=new NativeV2Fixture();fixture=new R2Fixture(base);R2.verify(java.nio.file.Path.of(System.getProperty("worldphase.projectRoot")));}
    @Test void strictCodecRoundTripAndUnboundFailClosed(){
        for(String field:List.of("smooth_core","ridge_core","smooth_controlled","ridge_controlled","smooth_protection","ridge_protection")){
            var leaf=fixture.leaf(field);assertThrows(IllegalStateException.class,leaf::context);
            var json=DensityFunction.DIRECT_CODEC.encodeStart(base.ops,leaf).getOrThrow();assertEquals("worldphase:prototype_v2/analytic_r2",json.getAsJsonObject().get("type").getAsString());
            var decoded=DensityFunction.DIRECT_CODEC.parse(base.ops,json).getOrThrow();assertEquals(json,DensityFunction.DIRECT_CODEC.encodeStart(base.ops,decoded).getOrThrow());
            var bad=fixture.json(field);bad.addProperty("seed",123456789);assertTrue(DensityFunction.DIRECT_CODEC.parse(base.ops,bad).error().isPresent());
            bad=fixture.json(field);bad.addProperty("profile",R1.PROFILE);assertTrue(DensityFunction.DIRECT_CODEC.parse(base.ops,bad).error().isPresent());
        }
    }
    @Test void actualRandomStateRawSeedAndHolderBoundAndBothVisitorOrders(){
        var settings=base.settings(fixture.leaf("ridge_controlled"),base.vanilla.noiseRouter().preliminarySurfaceLevel());
        var state=base.state(settings,123456789L);var leaf=(R2Density)state.router().finalDensity();assertEquals(123456789,leaf.context().seed);assertEquals(10.0/3,leaf.detail().get().maxValue(),1e-14);
        assertThrows(IllegalStateException.class,()->leaf.compute(new DensityFunction.SinglePointContext(0,80,0)));
        var vanilla=base.state(base.vanilla,123456789L);
        class Wiring implements DensityFunction.Visitor{int count;public DensityFunction apply(DensityFunction f){return f;}public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder n){count++;return new DensityFunction.NoiseHolder(n.noiseData(),vanilla.getOrCreateNoise(n.noiseData().unwrapKey().orElseThrow()));}}
        Wiring a=new Wiring(),b=new Wiring();
        var x=(R2Density)fixture.leaf("ridge_core").mapAll(new R2SeedVisitor(new PhaseNoiseSeedVisitor(a,123456789L),123456789L));
        var y=(R2Density)fixture.leaf("ridge_core").mapAll(new PhaseNoiseSeedVisitor(new R2SeedVisitor(b,123456789L),123456789L));
        assertEquals(a.count,b.count);assertTrue(a.count>0);assertEquals(x.context().hcValue(-16.25,16.75),y.context().hcValue(-16.25,16.75));
        var other=base.state(settings,8675309L);assertNotEquals(leaf.context().hcValue(-16.25,16.75),((R2Density)other.router().finalDensity()).context().hcValue(-16.25,16.75));
    }
    private R2Context syntheticContext(){
        // Synthetic input/activation fixture ONLY. No real candidate or timing/yield claim.
        var c=new R2Context(123456789L,(x,z)->.25,()->0,false);c.state=R2Context.State.PREPARATION_COMPLETED;
        var n=c.nodes.get(0);n.status="MATH_NODE_READY";n.h=96.5;n.pair=List.of(R2.Dir.E,R2.Dir.W);
        for(int phase=0;phase<2;phase++)for(var d:n.pair){var p=new R2Context.Port();p.end=phase==0?104.5:88.5;p.delta=p.end-n.h;n.ports[phase][d.ordinal()]=p;}
        return c;
    }
    @Test void selectedPairCoreDensityProtectionAndNoNorthRamp(){
        try(var c=syntheticContext()){
            var n=c.nodes.get(0); // use a center's actual stored global coordinates; no rounded-center platform.
            int x=(int)Math.round(n.center.x()),z=(int)Math.round(n.center.z());
            // First target lies outside query ROI; this standalone geometric fixture uses an in-ROI target.
            n.status="DEFERRED_NOT_SHORTLISTED";n=c.nodes.stream().filter(v->R2.fits(v.center,R2.WINDOWS[0])).findFirst().orElseThrow();
            n.status="MATH_NODE_READY";n.h=96.5;n.pair=List.of(R2.Dir.E,R2.Dir.W);
            for(int phase=0;phase<2;phase++)for(var d:n.pair){var p=new R2Context.Port();p.end=phase==0?104.5:88.5;p.delta=p.end-n.h;n.ports[phase][d.ordinal()]=p;}
            x=(int)Math.round(n.center.x());z=(int)Math.round(n.center.z());
            var a=c.geometry(false,x,90,z,1.2);var b=c.geometry(true,x,90,z,-2.1);assertEquals((96.5-90)/16,a.density());assertEquals(a.density(),b.density());assertEquals(1,a.protection());
            var north=c.geometry(false,x,90,z-80,1.2);assertEquals(0,north.qsum());double expected=(1-north.b())*c.hpValue(x,z-80,false)+north.b()*96.5;assertEquals(expected,north.height());
        }
    }
    @Test void protectedCoreOverridesNegativeNoodleAfterNativePostAndSharesDensity(){
        double previous=Double.NaN;
        for(boolean ridge:new boolean[]{false,true})try(var synthetic=syntheticContext()){
            synthetic.nodes.forEach(n->n.status="DEFERRED_NOT_SHORTLISTED");var n=synthetic.nodes.stream().filter(v->R2.fits(v.center,R2.WINDOWS[0])).findFirst().orElseThrow();
            n.status="MATH_NODE_READY";n.h=96.5;n.pair=List.of(R2.Dir.E,R2.Dir.W);
            for(int phase=0;phase<2;phase++)for(var d:n.pair){var p=new R2Context.Port();p.end=96.5;p.delta=0;n.ports[phase][d.ordinal()]=p;}
            var c=fixture.compose(ridge);var capped=DensityFunctions.min(c.nativeFinal(),DensityFunctions.constant(-.4));
            var state=base.state(base.settings(NativeV2Fixture.mix(c.protection(),capped,c.solid()),c.surface()),123456789L);
            var f=state.router().finalDensity().mapAll(q->q instanceof R2Density leaf?leaf.bind(synthetic):q);
            var q=new DensityFunction.SinglePointContext((int)Math.round(n.center.x()),90,(int)Math.round(n.center.z()));
            double actual=f.compute(q);assertTrue(actual>0);if(Double.isFinite(previous))assertEquals(previous,actual);previous=actual;
        }
    }
    @Test void nativeRouterInterpolationPreliminaryAndBaseColumnOnSyntheticPreparedInput()throws Exception{
        JsonArray probes=new JsonArray();
        for(boolean ridge:new boolean[]{false,true})try(var synthetic=syntheticContext()){
            // No node in these local probes: validates r2 operator routing with native noise, not real node yield.
            synthetic.nodes.forEach(n->n.status="DEFERRED_NOT_SHORTLISTED");
            var c=fixture.compose(ridge);assertEquals(3,c.substitutions());
            var settings=base.settings(c.density(),c.surface());var state=base.state(settings,123456789L);
            var router=state.router().mapAll(q->q instanceof R2Density leaf?leaf.bind(synthetic):q);
            var boundSettings=new NoiseGeneratorSettings(settings.noiseSettings(),settings.defaultBlock(),settings.defaultFluid(),router,settings.surfaceRule(),settings.spawnTarget(),settings.seaLevel(),settings.disableMobGeneration(),settings.isAquifersEnabled(),settings.oreVeinsEnabled(),settings.useLegacyRandomSource());
            // RandomState normally remaps/binds new contexts; generator consumes the already bound state router.
            var generator=new NoiseBasedChunkGenerator(new FixedBiomeSource(base.lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)),Holder.direct(boundSettings));
            // Test seam injects ONLY synthetic prepared contexts, not density/interpolation results.
            // RandomState's native noise maps and all NoiseChunk/base-column code remain real.
            var routerField=RandomState.class.getDeclaredField("router");routerField.setAccessible(true);routerField.set(state,router);
            var finalBound=state.router().finalDensity();
            for(int[] p:new int[][]{{-17,80,16},{0,80,0},{17,80,-17}}){
                double raw=finalBound.compute(new DensityFunction.SinglePointContext(p[0],p[1],p[2]));assertTrue(Double.isFinite(raw));
                double interpolated=generator.getInterpolatedNoiseValue(state,new DensityFunction.SinglePointContext(p[0],p[1],p[2]));assertTrue(Double.isFinite(interpolated));
                var column=generator.getBaseColumn(p[0],p[2],LevelHeightAccessor.create(-64,384),state);assertNotNull(column.getBlock(p[1]));
                double preliminary=router.preliminarySurfaceLevel().compute(new DensityFunction.SinglePointContext(p[0],p[1],p[2]));assertTrue(Double.isFinite(preliminary));
                JsonObject row=new JsonObject();row.addProperty("phase",ridge?"ridge":"smooth");row.addProperty("x",p[0]);row.addProperty("y",p[1]);row.addProperty("z",p[2]);row.addProperty("raw",raw);row.addProperty("interpolated",interpolated);row.addProperty("delta",interpolated-raw);row.addProperty("baseColumnBlock",column.getBlock(p[1]).toString());row.addProperty("preliminary",preliminary);
                int highest=-65;for(int y=319;y>=-64;y--)if(column.getBlock(y).equals(settings.defaultBlock())){highest=y;break;}
                row.addProperty("columnHighestDefaultBlockY",highest);row.addProperty("preliminaryMinusColumnTop",preliminary-highest);probes.add(row);
            }
        }
        var file=java.nio.file.Path.of(System.getProperty("worldphase.projectRoot"),"build/reports/r2-native-preflight/synthetic-native-probes.json");java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(probes));
    }
}
