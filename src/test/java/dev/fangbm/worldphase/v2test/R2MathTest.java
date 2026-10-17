package dev.fangbm.worldphase.v2test;
import dev.fangbm.worldphase.verification.v2.*;
import dev.fangbm.worldphase.verification.v2.r2.*;
import dev.fangbm.worldphase.worldgen.noise.GradientNoise;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class R2MathTest {
    private R2Context synthetic(){return new R2Context(123456789L,(x,z)->.25,()->0,false);}
    @Test void frozenHashLayoutShortlistAndQueryOrder()throws Exception {
        R2.verify(java.nio.file.Path.of(System.getProperty("worldphase.projectRoot")));
        assertEquals(132,R2.targets().size());var ids=new ArrayList<>(R2.targets());
        for(long seed:R2.SEEDS){var a=R2.shortlist(seed,ids);Collections.reverse(ids);assertEquals(a,R2.shortlist(seed,ids));assertTrue(a.size()<=16);
            assertEquals(seed==123456789?12:13,a.size());}
        var point=R2.center(123456789,new R1.Id(-1,-1));assertEquals(-626.9495252684669,point.x(),1e-9);
        assertThrows(IllegalArgumentException.class,()->R2.center(-1,new R1.Id(0,0)));
    }
    @Test void localKernelIntervalsEncloseActualExistingKernelAcrossLatticeEdges(){
        var n=new GradientNoise(1);
        for(double x:new double[]{-1.001,-1,Math.nextUp(-1.),-.001,0,.999,1})for(double z:new double[]{-.001,0,.999}){
            var bound=LocalNoise.bound(42,Jet.x(x,x+.002),Jet.z(z,z+.002));
            for(double dx:new double[]{0,.001,.002})for(double dz:new double[]{0,.001,.002}){
                double value=n.sample(42,x+dx,z+dz);var v=bound.v().expand(bound.e());assertTrue(value>=v.lo()&&value<=v.hi(),x+","+z+":"+value+" vs "+v);
            }
            assertTrue(bound.gradient()<20);
        }
    }
    @Test void outwardHcPointAndRegionAcrossNegativeChunkEdges(){
        try(var c=synthetic()){
            for(double x:new double[]{-16.25,-16,-.25,0,15.75,16}){
                var b=c.hc(Jet.x(x,x+.25),Jet.z(-.25,.25));
                for(double z:new double[]{-.25,0,.25}){double h=c.hcValue(x,z);assertTrue(h>=b.v().lo()-b.e()&&h<=b.v().hi()+b.e());}
                assertTrue(b.e()<0x1.0p-24);
            }
            double first=c.hcValue(-16.25,15.75);c.hcValue(16,-16);assertEquals(first,c.hcValue(-16.25,15.75));assertTrue(c.cacheHits>0);
            c.close();assertThrows(IllegalStateException.class,()->c.ready());assertEquals(0,c.rawEntries);
        }
    }
    @Test void finiteScaleAndTerminalThresholdsAreUnchanged(){
        assertEquals(.1171875,15.0/128);assertTrue(15.0/128+8*0x1.0p-20<.125);assertTrue(13.0/128+8*0x1.0p-20<.125);
        var slope=Jet.x(0,1).scale(1.0/16);assertTrue(slope.gradient()>=1.0/16);assertTrue(slope.gradient()<=Math.nextUp(1.0/16)+1e-15);
        assertThrows(IllegalArgumentException.class,()->new Interval(Double.NaN,1));assertThrows(IllegalArgumentException.class,()->new Jet(Interval.point(0),Interval.point(0),Interval.point(0),Double.NaN));
        assertThrows(IllegalArgumentException.class,()->Jet.c(1).div(Jet.x(-1,1)));
    }
    @Test void commonPairActivationPriorityFallbackAndMismatch(){
        var all=EnumSet.allOf(R2.Dir.class);assertEquals(List.of(R2.Dir.E,R2.Dir.W),R2.pair(all,all));
        var ns=EnumSet.of(R2.Dir.N,R2.Dir.S);assertEquals(List.of(R2.Dir.N,R2.Dir.S),R2.pair(ns,ns));
        assertTrue(R2.pair(EnumSet.of(R2.Dir.E,R2.Dir.W),ns).isEmpty());
        assertEquals(0,R2.mask(0,80,R2.Dir.E));assertEquals(1,R2.mask(80,0,R2.Dir.E));
    }
    @Test void nonfiniteReferenceFailsClosedAndNeverPublishesPartialReady(){
        var c=new R2Context(123456789L,(x,z)->Double.NaN,()->0,false);
        c.nodes.get(1).status="MATH_NODE_READY";
        assertThrows(IllegalStateException.class,c::step);assertEquals(R2Context.State.FAILED,c.state);
        assertEquals("INVALIDATED_PROFILE_FAILURE",c.nodes.get(1).status);assertThrows(IllegalStateException.class,c::ready);
    }
    @Test void coldTickTimeoutStopsBeforePublishing(){
        AtomicLong clock=new AtomicLong();var c=new R2Context(123456789L,(x,z)->.25,()->clock.addAndGet(30_000_001),true);
        assertThrows(IllegalStateException.class,c::step);assertTrue(c.failure.startsWith("BUDGET_EXCEEDED"));assertThrows(IllegalStateException.class,c::ready);
    }
    @Test void anchorAndEndpointBinAmbiguityUnknownErrorAndBoundaryComparisons(){
        assertEquals("POINT_BIN_UNCERTAIN",R2.quantize(Jet.c(71)).reason());
        assertEquals("QUANTIZED",R2.quantize(Jet.c(71.25)).reason());assertEquals(71.5,R2.quantize(Jet.c(71.25)).height());
        assertEquals("FLOAT_ERROR_UNPROVEN",R2.quantize(null).reason());
        assertEquals("FLOAT_ERROR_UNPROVEN",R2.quantize(new Jet(Interval.point(71.25),Interval.point(0),Interval.point(0),Math.nextUp(0x1.0p-24))).reason());
        assertEquals("PORT_MODEL_READY_NOT_FULL_SAFE",R2.terminal(.5,1.0/16,0x1.0p-24));
        assertEquals("TERMINAL_RANGE",R2.terminal(Math.nextUp(.5),1.0/16,0));
        assertEquals("TERMINAL_GRADIENT",R2.terminal(.5,Math.nextUp(1.0/16),0));
        assertEquals("NONFINITE_OR_UNKNOWN_BOUND",R2.terminal(Double.NaN,0,0));
    }
    @Test void localHpErrorEnclosesActualScalarAndBothPhaseFields(){
        try(var c=synthetic()){
            c.nodes.forEach(n->n.status="ANCHOR_DRY");
            for(boolean ridge:new boolean[]{false,true})for(double x:new double[]{-16.25,-16,0,16.75}){
                var b=c.hp(Jet.x(x,x),Jet.z(80.25,80.25),ridge);double actual=c.hpValue(x,80.25,ridge);
                assertTrue(actual>=b.v().lo()-b.e()&&actual<=b.v().hi()+b.e());assertTrue(b.e()<0x1.0p-24);
            }
        }
    }
    @Test void measuredOwnedGraphIsNotProcessHeapAndCacheInvalidates(){
        try(var c=synthetic()){
            long size=R2Memory.measure(c,()->{});assertTrue(size>131072L*17);assertTrue(size<33554432);
            c.hcValue(-16.25,16.75);assertTrue(c.rawEntries>0);c.close();
            assertThrows(IllegalStateException.class,()->c.hcValue(-16.25,16.75));
        }
    }
    @Test void ownerThreadAndRawCallCap(){
        try(var c=synthetic()){
            var result=new java.util.concurrent.atomic.AtomicReference<Throwable>();Thread t=Thread.ofPlatform().start(()->{try{c.step();}catch(Throwable e){result.set(e);}});
            assertDoesNotThrow(()->{t.join();});assertInstanceOf(IllegalStateException.class,result.get());
            for(int i=0;i<52048;i++)c.raw(i,0);
            assertThrows(IllegalStateException.class,()->c.raw(52048,0));assertEquals(52048,c.rawCalls);
        }
    }
}
