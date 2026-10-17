package dev.fangbm.worldphase.v2test;

import dev.fangbm.worldphase.verification.v2.*;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class HcCertificateTest {
    private static HcCertificate certify(double x,double z,HcCertificate.Reference f) {
        var b=new HcCertificate.Builder(new R1.Point(x,z));while(!b.done())b.next(f);return b.finish();
    }
    @Test void intervalsContainExactArithmeticAndRejectNonfinite() {
        for(double a:new double[]{-987.25,-1.0/3,0,Double.MIN_VALUE,.1,1e100})
            for(double b:new double[]{-3,.25,7}) {
                Interval x=Interval.point(a),y=Interval.point(b);
                check(x.add(y),new BigDecimal(a).add(new BigDecimal(b)));
                check(x.sub(y),new BigDecimal(a).subtract(new BigDecimal(b)));
                check(x.mul(y),new BigDecimal(a).multiply(new BigDecimal(b)));
            }
        assertThrows(IllegalArgumentException.class,()->Interval.point(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new Interval(2,1));
        assertThrows(IllegalArgumentException.class,()->Interval.point(1).div(new Interval(-1,1)));
        assertThrows(IllegalArgumentException.class,()->Interval.point(Double.MAX_VALUE).mul(Interval.point(2)));
    }
    private static void check(Interval i,BigDecimal exact) {
        assertTrue(new BigDecimal(i.lo()).compareTo(exact)<=0);
        assertTrue(new BigDecimal(i.hi()).compareTo(exact)>=0);
    }
    @Test void continuousBilinearEnvelopeCoversInteriorExtremaAndEdges() {
        HcCertificate.Reference f=(x,z)->.5+Math.scalb(x,-18)-Math.scalb(z,-19);
        for(double x:new double[]{-512.25,-16,Math.nextDown(256.0),Math.nextUp(256.0)}) {
            var a=certify(x,-.25,f);var b=certify(x,-.25,f);
            assertEquals(a,b);assertTrue(a.referenceCalls()<=4356);
            V2Context sampler=new V2Context(19,f,()->0);
            for(double dx:new double[]{-32,-.125,0,31.999,32})for(double dz:new double[]{-32,0,32}) {
                double value=sampler.hc(x+dx,-.25+dz);
                assertTrue(value>=a.height().lo()&&value<=a.height().hi());
            }
            assertTrue(a.heightError()>0);assertTrue(a.modelLipschitzUpper()<.125);
            assertTrue(a.modelCandidate());assertFalse(a.eligible());
            assertEquals("R1_ROUNDED_LIPSCHITZ_UNPROVEN",a.reason());
        }
    }
    @Test void wetSteepIncompleteAndInvalidReferencesReject() {
        assertEquals("WET_OR_HEIGHT_REJECTED",certify(256,256,(x,z)->0).reason());
        assertEquals("MODEL_SLOPE_REJECTED",certify(256,256,(x,z)->.5+(x%2)*.01).reason());
        var b=new HcCertificate.Builder(new R1.Point(-256,256));
        assertThrows(IllegalStateException.class,b::finish);
        assertThrows(IllegalArgumentException.class,()->b.next((x,z)->Double.NaN));
        assertThrows(IllegalArgumentException.class,()->b.next((x,z)->Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->new HcCertificate.Builder(new R1.Point(Double.NaN,0)));
    }
    @Test void roundoffAndIncrementBoundAreExplicitNotAnInventedSlopeTolerance() {
        double e=GradientRoundoff.kernelError();assertTrue(Double.isFinite(e)&&e>0);
        assertTrue(GradientRoundoff.scaledKernelError(900)>=e);
        var c=certify(256,256,(x,z)->.5);
        assertTrue(c.incrementUpper(0)>0); // additive error is not a homogeneous Lipschitz bound
        assertTrue(c.incrementUpper(1)>=c.modelLipschitzUpper()+2*c.heightError());
    }
    @Test void actualRoundedHcHasAnAdjacentDoubleJumpNotCoveredByModelSlope() {
        // Synthetic constant integer continents; actual unchanged GradientNoise, no terrain/holdout search.
        V2Context c=new V2Context(19,(x,z)->.5,()->0);
        double lo=0,hi=16;int z=0;
        while(c.hc(lo,z)==c.hc(hi,z)&&z<32)z++;
        assertNotEquals(c.hc(lo,z),c.hc(hi,z));double first=c.hc(lo,z);
        while(Math.nextUp(lo)<hi) {
            double mid=lo+(hi-lo)/2;
            if(c.hc(mid,z)==first)lo=mid;else hi=mid;
        }
        double jump=Math.abs(c.hc(hi,z)-c.hc(lo,z))/(hi-lo);
        assertTrue(jump>.125,"This concrete rounded program cannot use the ideal model's slope as its actual slope");
        System.out.printf("V2 R1 rounding counterexample lo=%s hi=%s z=%d quotient=%g%n",Double.toHexString(lo),Double.toHexString(hi),z,jump);
    }
    @Test void deterministicBudgetFailureAndNoPartialReadiness() {
        AtomicLong clock=new AtomicLong();
        V2Context c=new V2Context(19,(x,z)->{clock.addAndGet(26_000_000);return .5;},clock::get);
        assertThrows(IllegalStateException.class,c::step);assertEquals(1,c.calls());
        assertEquals(V2Context.State.FAILED,c.state());assertEquals("TICK_BUDGET_EXCEEDED",c.failure());
        assertThrows(IllegalStateException.class,()->c.requireReady(0,0));
        assertThrows(IllegalStateException.class,c::step);
    }
    @Test void referenceFailureAndWrongThreadFailClosed() throws Exception {
        V2Context c=new V2Context(19,(x,z)->Double.NaN,()->0);
        assertThrows(IllegalArgumentException.class,c::step);assertEquals(V2Context.State.FAILED,c.state());
        V2Context owned=new V2Context(19,(x,z)->.5,()->0);
        var result=new java.util.concurrent.CompletableFuture<Throwable>();
        var wrong=new Thread(()->{try{owned.step();result.complete(null);}catch(Throwable error){result.complete(error);}});
        wrong.start();wrong.join();assertInstanceOf(IllegalStateException.class,result.get());
        assertEquals(V2Context.State.NEW,owned.state());owned.close();
        c.close();assertEquals(V2Context.State.CLOSED,c.state());assertTrue(c.certificates().isEmpty());
    }
}
