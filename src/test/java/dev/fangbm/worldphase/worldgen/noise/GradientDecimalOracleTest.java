package dev.fangbm.worldphase.worldgen.noise;

import static org.junit.jupiter.api.Assertions.*;
import dev.fangbm.worldphase.verification.v2.GradientRoundoff;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Exact-decimal evaluation of the ideal stored-constant polynomial; uses existing hash, not a new sampler. */
class GradientDecimalOracleTest {
    private static final BigDecimal INV=new BigDecimal(0x1.6a09e667f3bccp-1),ONE=BigDecimal.ONE;
    private static BigDecimal fade(BigDecimal t){return t.pow(3).multiply(BigDecimal.TEN.add(t.multiply(BigDecimal.valueOf(-15).add(BigDecimal.valueOf(6).multiply(t)))));}
    private static BigDecimal blend(BigDecimal a,BigDecimal b,BigDecimal t){return ONE.subtract(t).multiply(a).add(t.multiply(b));}
    private static BigDecimal dot(long seed,long x,long z,BigDecimal a,BigDecimal b) {
        return switch((int)(NoiseHash.cell(seed,x,z,0x4752414449454e54L)&7)) {
            case 0->a;case 1->a.negate();case 2->b;case 3->b.negate();
            case 4->a.add(b).multiply(INV);case 5->a.subtract(b).multiply(INV);
            case 6->b.subtract(a).multiply(INV);default->a.negate().subtract(b).multiply(INV);
        };
    }
    @Test void genericForwardErrorContainsAllTestedGradientChoicesAndBoundarySides() {
        GradientNoise sampler=new GradientNoise(1);BigDecimal error=new BigDecimal(GradientRoundoff.kernelError());
        for(long seed:new long[]{19,-23,0})for(double x:new double[]{-16.125,-1,Math.nextDown(-1),-.125,0,.125,Math.nextUp(1),16.75})
            for(double z:new double[]{-1.125,0,.75}) {
                long ix=(long)Math.floor(x),iz=(long)Math.floor(z);
                BigDecimal a=new BigDecimal(x).subtract(BigDecimal.valueOf(ix)),b=new BigDecimal(z).subtract(BigDecimal.valueOf(iz));
                BigDecimal low=blend(dot(seed,ix,iz,a,b),dot(seed,ix+1,iz,a.subtract(ONE),b),fade(a));
                BigDecimal high=blend(dot(seed,ix,iz+1,a,b.subtract(ONE)),dot(seed,ix+1,iz+1,a.subtract(ONE),b.subtract(ONE)),fade(a));
                BigDecimal ideal=blend(low,high,fade(b)).multiply(INV);
                assertTrue(new BigDecimal(sampler.sample(seed,x,z)).subtract(ideal).abs().compareTo(error)<=0);
            }
    }
}
