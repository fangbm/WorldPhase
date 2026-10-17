package dev.fangbm.worldphase.v2test;

import dev.fangbm.worldphase.verification.v2.*;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseSeedVisitor;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.*;

class NativeV2IntegrationTest {
    private static NativeV2Fixture fixture;
    private static V2Context ready;
    private static final long SEED=123456789L;
    @BeforeAll static void init() throws Exception {
        fixture=new NativeV2Fixture();
        var state=fixture.state(fixture.vanilla,SEED);var raw=R1.rawReference(state.router().continents());
        // Deterministic-clock *semantic* fixture. Actual wall/CPU gates run in a separate fresh-JVM test.
        ready=new V2Context(SEED,(x,z)->raw.compute(new DensityFunction.SinglePointContext(x,0,z)),()->0);
        while(ready.state()!=V2Context.State.READY)ready.step();
        assertEquals(0,ready.certificates().values().stream().filter(HcCertificate::eligible).count());
    }
    @AfterAll static void closeReadyContext() {
        if(ready!=null){ready.close();assertTrue(ready.certificates().isEmpty());assertEquals(0,ready.cacheStats()[3]);}
    }
    private static DensityFunction useSemanticContext(DensityFunction f) {
        return f.mapAll(q->q instanceof PrototypeAnalyticDensityFunction p?p.bind(ready):q);
    }
    @Test void slidePreservesFrozenArithmeticInsteadOfNativeLerpReassociation() {
        DensityFunction d=DensityFunctions.constant(.12345678912345678);
        for(int y:new int[]{-65,-64,-63,-57,-41,-40,200,240,241,247,255,256,319}) {
            double t=Math.clamp((256.0-y)/16,0,1);
            double top=(1-t)*(-.078125)+t*.12345678912345678;
            double b=Math.clamp((y+64.0)/24,0,1);
            double expected=(1-b)*.1171875+b*top;
            assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(NativeV2Fixture.slide(d).compute(new DensityFunction.SinglePointContext(0,y,0))));
        }
    }
    @Test void registryDispatchCodecStrictFailuresAndRoundTrip() {
        assertEquals(Identifier.parse("worldphase:prototype_v2/analytic_r1"),BuiltInRegistries.DENSITY_FUNCTION_TYPE.getKey(V2PreflightMod.ANALYTIC.get()));
        for(String field:List.of("common_density","smooth_core","ridge_core","node_weight","protection")) {
            var leaf=fixture.leaf(field);
            assertThrows(IllegalStateException.class,()->leaf.compute(new DensityFunction.SinglePointContext(0,64,0)));
            var encoded=DensityFunction.DIRECT_CODEC.encodeStart(fixture.ops,leaf).getOrThrow();
            assertEquals(fixture.json(field),encoded);
            var invalid=fixture.json(field);invalid.addProperty("seed",1);
            assertTrue(DensityFunction.DIRECT_CODEC.parse(fixture.ops,invalid).error().isPresent());
        }
        for(String key:List.of("profile","field","reference","detail")) {
            var invalid=fixture.json("smooth_core");invalid.addProperty(key,"unknown");
            assertTrue(DensityFunction.DIRECT_CODEC.parse(fixture.ops,invalid).error().isPresent());
        }
        var missing=fixture.json("smooth_core");missing.remove("detail");
        assertTrue(DensityFunction.DIRECT_CODEC.parse(fixture.ops,missing).error().isPresent());
        var extra=fixture.json("common_density");extra.addProperty("detail","worldphase:prototype_v2/smooth/detail_r1");
        assertTrue(DensityFunction.DIRECT_CODEC.parse(fixture.ops,extra).error().isPresent());
    }
    @Test void actualConstructorMixinBindsRawSeedAndAllNativeChildren() {
        for(boolean ridge:new boolean[]{false,true}) {
            var c=fixture.compose(ridge);var settings=fixture.settings(c.finalDensity(),c.surface());
            var state=fixture.state(settings,SEED);var contexts=NativeV2Fixture.contexts(state.router().finalDensity());
            assertEquals(1,contexts.size());assertEquals(SEED,contexts.iterator().next().seed);
            assertThrows(IllegalStateException.class,()->state.router().finalDensity().compute(new DensityFunction.SinglePointContext(0,70,0)));
            List<PrototypeAnalyticDensityFunction> leaves=new ArrayList<>();
            state.router().finalDensity().mapAll(q->{if(q instanceof PrototypeAnalyticDensityFunction p)leaves.add(p);return q;});
            for(var p:leaves)if(p.detail().isPresent()) {
                var n=p.detail().orElseThrow();assertNotNull(n.noise());
                assertEquals(n.noise().maxValue(),n.maxValue());assertEquals(10.0/3,n.maxValue(),1e-14);
            }
            var again=fixture.state(settings,8675309L);
            assertEquals(8675309L,NativeV2Fixture.contexts(again.router().finalDensity()).iterator().next().seed);
            double a=contexts.iterator().next().hc(-17.25,16.75);
            double b=NativeV2Fixture.contexts(again.router().finalDensity()).iterator().next().hc(-17.25,16.75);
            assertNotEquals(Double.doubleToLongBits(a),Double.doubleToLongBits(b));
        }
    }
    @Test void bothNestedVisitorOrdersPreserveSeedWiringAndDoNotDoubleVisitNoise() {
        var vanilla=fixture.state(fixture.vanilla,SEED);
        class Wiring implements DensityFunction.Visitor {
            int noises;
            public DensityFunction apply(DensityFunction f){return f;}
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder n) {
                noises++;return new DensityFunction.NoiseHolder(n.noiseData(),vanilla.getOrCreateNoise(n.noiseData().unwrapKey().orElseThrow()));
            }
        }
        Wiring first=new Wiring(),second=new Wiring(),baseline=new Wiring();
        var f=fixture.leaf("ridge_core");
        var a=(PrototypeAnalyticDensityFunction)f.mapAll(new V2SeedVisitor(new PhaseNoiseSeedVisitor(first,SEED),SEED));
        var b=(PrototypeAnalyticDensityFunction)f.mapAll(new PhaseNoiseSeedVisitor(new V2SeedVisitor(second,SEED),SEED));
        f.mapAll(baseline);assertEquals(baseline.noises,first.noises);assertEquals(first.noises,second.noises);assertTrue(first.noises>0);
        assertEquals(a.context().hc(-17.25,-16.75),b.context().hc(-17.25,-16.75));
        assertEquals(a.detail().get().getValue(1,2,3),b.detail().get().getValue(1,2,3));
        assertSame(a,a.bind(a.context()));
        var encoded=DensityFunction.DIRECT_CODEC.encodeStart(fixture.ops,a).getOrThrow();
        assertFalse(encoded.getAsJsonObject().has("seed"));
        var decoded=(PrototypeAnalyticDensityFunction)DensityFunction.DIRECT_CODEC.parse(fixture.ops,encoded).getOrThrow();
        assertThrows(IllegalStateException.class,decoded::context);
    }
    @Test void nativeContinentsFractionalExtensionMatchesFrozenOrder() {
        var raw=R1.rawReference(fixture.state(fixture.vanilla,SEED).router().continents());
        var n=new dev.fangbm.worldphase.worldgen.noise.GradientNoise(1);
        for(double x:new double[]{-512.25,Math.nextDown(-16.0),-16,Math.nextUp(-16.0),0,15.75,16}) {
            double z=-.25;int i=(int)StrictMath.floor(x),j=(int)StrictMath.floor(z);double tx=x-i,tz=z-j;
            double a=raw.compute(new DensityFunction.SinglePointContext(i,0,j));
            double b=raw.compute(new DensityFunction.SinglePointContext(i+1,0,j));
            double c=raw.compute(new DensityFunction.SinglePointContext(i,0,j+1));
            double d=raw.compute(new DensityFunction.SinglePointContext(i+1,0,j+1));
            double v=(((1-tx)*(1-tz)*a+tx*(1-tz)*b)+(1-tx)*tz*c)+tx*tz*d;
            double expected=(63+64*Math.clamp(v,-1,1))+6*n.sample(R1.mix(SEED^R1.COMMON),x/900,z/900);
            assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(ready.hc(x,z)));
        }
    }
    @Test void everyLeafMatchesDirectFormulaBoundsAndSamplingOrder() {
        int[][] points={{-17,33,16},{-1,90,-1},{0,64,0},{16,-64,-16},{32,319,32}};
        for(String field:List.of("common_density","smooth_core","ridge_core","node_weight","protection")) {
            var state=fixture.state(fixture.settings(fixture.leaf(field),fixture.vanilla.noiseRouter().preliminarySurfaceLevel()),SEED);
            var leaf=(PrototypeAnalyticDensityFunction)useSemanticContext(state.router().finalDensity());
            for(int[] p:points) {
                var ctx=new DensityFunction.SinglePointContext(p[0],p[1],p[2]);double actual=leaf.compute(ctx);
                double expected;
                if(field.equals("common_density"))expected=(ready.hc(p[0],p[2])-p[1])/16;
                else if(field.equals("node_weight"))expected=1;
                else if(field.equals("protection"))expected=0;
                else {
                    boolean ridge=field.startsWith("ridge");double[] column=oracleColumn(p[0],p[2],ridge);var n=leaf.detail().orElseThrow();
                    double noise=n.getValue(p[0]/(ridge?64.0:80),p[1]/(ridge?40.0:48),p[2]/(ridge?64.0:80))/n.noise().maxValue();
                    double amplitude=ridge?.12*(R1.s(.1,.3,column[1])*(1-R1.s(.7,.9,column[1])))*(1-R1.s(16,32,Math.abs(p[1]-column[0]))):.04;
                    expected=(column[0]-p[1])/16+amplitude*noise;
                }
                assertEquals(expected,actual,2e-15);assertTrue(actual>=leaf.minValue()&&actual<=leaf.maxValue());
                leaf.compute(new DensityFunction.SinglePointContext(17,70,-17));assertEquals(actual,leaf.compute(ctx));
                assertTrue(Double.isFinite(leaf.compute(new DensityFunction.SinglePointContext(0,Integer.MAX_VALUE,0))));
                assertTrue(Double.isFinite(leaf.compute(new DensityFunction.SinglePointContext(0,Integer.MIN_VALUE,0))));
            }
        }
    }
    @Test void nativeInterpolatorPreliminarySurfaceColumnsAndCaveComposition() {
        JsonArray probes=new JsonArray();
        for(boolean ridge:new boolean[]{false,true}) {
            var c=fixture.compose(ridge);assertEquals(3,c.substitutions());
            var settings=fixture.settings(c.finalDensity(),c.surface());var state=fixture.state(settings,SEED);
            // Prepare the actual constructor-bound context. The wall budget is enforced, not bypassed.
            for(V2Context context:NativeV2Fixture.contexts(state.router().finalDensity()))completeSemantic(context);
            var biome=fixture.lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            var generator=new NoiseBasedChunkGenerator(new FixedBiomeSource(biome),Holder.direct(settings));
            var surfaceRefState=fixture.state(fixture.settings(c.surfaceReference(),fixture.vanilla.noiseRouter().preliminarySurfaceLevel()),SEED);
            DensityFunction surfaceReference=useSemanticContext(surfaceRefState.router().finalDensity());
            for(int[] p:new int[][]{{-17,65,16},{0,95,0},{17,33,-17}}) {
                var q=new DensityFunction.SinglePointContext(p[0],p[1],p[2]);
                double raw=state.router().finalDensity().compute(q),interpolated=generator.getInterpolatedNoiseValue(state,q);
                assertTrue(Double.isFinite(raw)&&Double.isFinite(interpolated));
                double top=state.router().preliminarySurfaceLevel().compute(q);
                assertTrue(top>=-64&&top<=(ridge?200:152));assertEquals(0,((int)top+64)%8);
                if(top>-64)assertTrue(surfaceReference.compute(new DensityFunction.SinglePointContext(p[0],(int)top,p[2]))>0);
                for(int y=(int)top+8;y<=(ridge?200:152);y+=8)
                    assertTrue(surfaceReference.compute(new DensityFunction.SinglePointContext(p[0],y,p[2]))<=0);
                var column=generator.getBaseColumn(p[0],p[2],LevelHeightAccessor.create(-64,384),state);
                assertNotNull(column.getBlock(p[1]));
                JsonObject row=new JsonObject();row.addProperty("phase",ridge?"ridge":"smooth");row.addProperty("x",p[0]);row.addProperty("y",p[1]);row.addProperty("z",p[2]);
                row.addProperty("raw",raw);row.addProperty("interpolated",interpolated);row.addProperty("delta",interpolated-raw);
                row.addProperty("preliminary",top);row.addProperty("baseColumnBlock",column.getBlock(p[1]).toString());
                int highestTerrain=-65,highestNonAir=-65;
                for(int y=319;y>=-64;y--) {
                    if(highestTerrain==-65&&column.getBlock(y).equals(settings.defaultBlock()))highestTerrain=y;
                    if(highestNonAir==-65&&!column.getBlock(y).isAir())highestNonAir=y;
                }
                row.addProperty("columnHighestDefaultBlockY",highestTerrain);row.addProperty("columnHighestNonAirY",highestNonAir);
                row.addProperty("preliminaryMinusColumnDefaultBlockTop",top-highestTerrain);probes.add(row);
            }
            assertFalse(ready.certificates().values().stream().anyMatch(HcCertificate::eligible));
        }
        try {java.nio.file.Path p=java.nio.file.Path.of(System.getProperty("worldphase.projectRoot"),"build/reports/v2-preflight/native-probes.json");
            java.nio.file.Files.createDirectories(p.getParent());java.nio.file.Files.writeString(p,new GsonBuilder().setPrettyPrinting().create().toJson(probes));
        }catch(Exception e){throw new AssertionError(e);}
    }
    private static void completeSemantic(V2Context c) {
        // No real-time budget bypass: if actual timed preparation fails, this native case is genuinely blocked.
        while(c.state()!=V2Context.State.READY)c.step();
    }
    @Test void protectionSelectsCommonAfterNoodleInSameOutputDomain() {
        // Force a negative noodle cap and P=1 in the native operator structure ONLY.
        // This tests composition order, not enabled nodes, FULL chunks or safety.
        var c=fixture.compose(false);
        DensityFunction capped=DensityFunctions.min(c.nativeFinal(),DensityFunctions.constant(-.4));
        var state=fixture.state(fixture.settings(NativeV2Fixture.mix(c.protection(),capped,c.commonFinal()),c.surface()),SEED);
        DensityFunction forced=useSemanticContext(state.router().finalDensity()).mapAll(q->
                q instanceof PrototypeAnalyticDensityFunction p&&p.field()==PrototypeAnalyticDensityFunction.Field.PROTECTION?DensityFunctions.constant(1):q);
        var commonState=fixture.state(fixture.settings(c.commonFinal(),fixture.vanilla.noiseRouter().preliminarySurfaceLevel()),SEED);
        DensityFunction common=useSemanticContext(commonState.router().finalDensity());
        var below=new DensityFunction.SinglePointContext(-16,-32,16);
        assertTrue(common.compute(below)>0); // min(noodle,protected) would have the WRONG negative sign.
        assertEquals(common.compute(below),forced.compute(below),1e-15);
        for(int y:new int[]{32,64,80,96,112}) {
            var p=new DensityFunction.SinglePointContext(-16,y,16);assertEquals(common.compute(p),forced.compute(p),1e-15);
        }
    }
    private static double[] oracleColumn(int x,int z,boolean ridge) {
        // Separate expression oracle; calls only unchanged kernel + actual Hc, never V2Context.column.
        var n=new dev.fangbm.worldphase.worldgen.noise.GradientNoise(1);
        double h=ready.hc(x,z);
        if(!ridge)return new double[]{h+4*n.sample(R1.mix(SEED^R1.WIDE),x/900.0,z/900.0)
                +3*n.sample(R1.mix(SEED^R1.HILLS),x/220.0,z/220.0),0}; // no eligible graph edges => g=0
        double angle=Math.PI*R1.u(SEED,0,0,R1.DIRECTION);
        double normal=x*StrictMath.cos(angle)+z*StrictMath.sin(angle);
        double tangent=-x*StrictMath.sin(angle)+z*StrictMath.cos(angle);
        double wave=StrictMath.sin(Math.PI*(normal/320+.16*n.sample(R1.mix(SEED^R1.CURVE),x/600.0,z/600.0)))/.28;
        double r=StrictMath.exp(-wave*wave);
        return new double[]{((h+48*r)-10*(1-r))+4*n.sample(R1.mix(SEED^R1.ALONG),tangent/600,normal/900),r};
    }
    @Test void roiCacheAndInvalidationAreBounded() {
        assertThrows(IllegalStateException.class,()->ready.requireReady(99999,99999));
        long[] before=ready.cacheStats();ready.column(1,1,true);ready.column(1,1,true);
        assertTrue(ready.cacheStats()[0]>before[0]);
        assertTrue(ready.cacheStats()[3]<=16384);
        for(int x=-128;x<=0;x++)for(int z=-128;z<=0;z++)ready.column(x,z,true);
        long[] filled=ready.cacheStats();assertEquals(16384,filled[3]);assertTrue(filled[2]>0);
        var value=ready.column(-128,-128,true);ready.column(0,0,true);assertEquals(value,ready.column(-128,-128,true));
    }
}
