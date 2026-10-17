package dev.fangbm.worldphase.verification.v2.r2;

import dev.fangbm.worldphase.verification.v2.*;

/** Proof-only extension of the pinned eight-gradient polynomial. Actual values use GradientNoise.
 * Split every intersected lattice cell. C1 boundary continuity permits derivative hulls.
 */
public final class LocalNoise {
    private static final double K=0x1.6a09e667f3bccp-1;
    private static final double ERROR=GradientRoundoff.kernelError(),DERIVATIVE=GradientRoundoff.componentDerivativeUpper();
    private LocalNoise(){}
    public static Jet bound(long seed,Jet x,Jet z){
        Jet result=null;
        for(long i=(long)StrictMath.floor(x.v().lo());i<=StrictMath.floor(x.v().hi());i++)for(long j=(long)StrictMath.floor(z.v().lo());j<=StrictMath.floor(z.v().hi());j++){
            double a=Math.max(x.v().lo(),i),b=Math.min(x.v().hi(),i+1),c=Math.max(z.v().lo(),j),d=Math.min(z.v().hi(),j+1);
            if(a>b||c>d)continue;
            var xx=new Jet(new Interval(a,b),x.dx(),x.dz(),0).sub(Jet.c(i));var zz=new Jet(new Interval(c,d),z.dx(),z.dz(),0).sub(Jet.c(j));
            var low=Jet.blend(dot(seed,i,j,xx,zz),dot(seed,i+1,j,xx.sub(Jet.c(1)),zz),xx.fade());
            var high=Jet.blend(dot(seed,i,j+1,xx,zz.sub(Jet.c(1))),dot(seed,i+1,j+1,xx.sub(Jet.c(1)),zz.sub(Jet.c(1))),xx.fade());
            var n=Jet.blend(low,high,zz.fade()).scale(K);
            // Independent existing global error proof covers coordinate rounding across a lattice seam.
            double err=Interval.upAdd(ERROR,Interval.upMul(DERIVATIVE,Interval.upAdd(x.e(),z.e())));
            n=new Jet(n.v().clamp(-1,1),n.dx(),n.dz(),err);result=result==null?n:Jet.hull(result,n);
        }
        if(result==null)throw new IllegalStateException("UNKNOWN_NOISE_INTERVAL");return result;
    }
    private static Jet dot(long s,long i,long j,Jet x,Jet z){
        long h=R1.mix(s^0x4752414449454e54L^R1.mix(i^0x9e3779b97f4a7c15L)^Long.rotateLeft(R1.mix(j^0xd1b54a32d192ed03L),32));
        return switch((int)(h&7)){case 0->x;case 1->x.neg();case 2->z;case 3->z.neg();case 4->x.add(z).scale(K);case 5->x.sub(z).scale(K);case 6->x.neg().add(z).scale(K);default->x.neg().sub(z).scale(K);};
    }
}
