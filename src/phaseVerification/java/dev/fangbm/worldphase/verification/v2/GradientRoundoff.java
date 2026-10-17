package dev.fangbm.worldphase.verification.v2;

/** Forward-error proof for the *existing* GradientNoise(1), not a replacement sampler.
 * Ideal values are real arithmetic with the stored binary64 gradient constant.
 * Full ulp rather than half ulp covers tie/binade/subnormal cases. All bound arithmetic rounds out.
 */
public final class GradientRoundoff {
    private GradientRoundoff() {}
    private record E(Interval real, double error) {
        E op(Interval r,double propagated) {
            double roundedRange=r.expand(propagated).maxAbs();
            return new E(r,Interval.upAdd(propagated,Math.ulp(roundedRange)));
        }
        E add(E b) { return op(real.add(b.real),Interval.upAdd(error,b.error)); }
        E sub(E b) { return op(real.sub(b.real),Interval.upAdd(error,b.error)); }
        E mul(E b) {
            double p=Interval.upAdd(Interval.upMul(real.maxAbs(),b.error),Interval.upMul(b.real.maxAbs(),error));
            return op(real.mul(b.real),Interval.upAdd(p,Interval.upMul(error,b.error)));
        }
        E restrict(double lo,double hi) { return new E(new Interval(lo,hi),error); }
    }
    private static E c(double x) { return new E(Interval.point(x),0); }
    private static E fade(E t) {
        // Exactly GradientNoise's t*t*t*(10+t*(-15+6*t)), then the proven fade range.
        return t.mul(t).mul(t).mul(c(10).add(t.mul(c(-15).add(c(6).mul(t))))).restrict(0,1);
    }
    private static E blend(E a,E b,E w) { return c(1).sub(w).mul(a).add(w.mul(b)); }
    public static double componentDerivativeUpper() {
        // 53/8 is exactly representable; nextUp encloses multiplication by stored INV_SQRT_TWO.
        return Math.nextUp((53.0/8)*0x1.6a09e667f3bccp-1);
    }
    public static double kernelError() {
        E t=new E(new Interval(0,1),Math.ulp(1.0)); // fractional subtraction, including negative lattice boundary
        E w=fade(t);
        double dotError=0;
        for (E x:new E[]{t,t.sub(c(1))}) for(E z:new E[]{t,t.sub(c(1))}) {
            E[] variants={x,z,x.add(z).mul(c(0x1.6a09e667f3bccp-1)),
                    x.sub(z).mul(c(0x1.6a09e667f3bccp-1))};
            for(E v:variants) dotError=Math.max(dotError,v.error);
        }
        // Negating finite coordinates is exact. Unit gradients give |dot|<=sqrt(2)<2;
        // first blend is a convex combination, so its exact value also lies in[-2,2].
        E dot=new E(new Interval(-2,2),dotError);
        E lower=blend(dot,dot,w).restrict(-2,2);
        return blend(lower,lower,w).mul(c(0x1.6a09e667f3bccp-1)).error;
    }
    public static double scaledKernelError(double length) {
        if (!(length>0) || !Double.isFinite(length)) throw new IllegalArgumentException("Invalid length");
        // World x/z supported by r1. Divide rounding can move across lattice boundaries:
        // global C1 Lipschitz of the ideal coherent field covers that move as well.
        double q=Math.nextUp(29_999_984.0/length);
        double coordinateError=Math.ulp(q);
        double perturbation=Interval.upMul(Interval.normUpper(componentDerivativeUpper(),componentDerivativeUpper()),
                Interval.normUpper(coordinateError,coordinateError));
        return Interval.upAdd(kernelError(),perturbation);
    }
    public static double heightError(double minV,double maxV) {
        E t=new E(new Interval(0,1),Math.ulp(1.0));
        E v=new E(new Interval(minV,maxV),0); // native integer outputs define reference exactly
        E a=c(1).sub(t).mul(c(1).sub(t)).mul(v);
        E b=t.mul(c(1).sub(t)).mul(v);
        E d=c(1).sub(t).mul(t).mul(v);
        E e=t.mul(t).mul(v);
        E reference=a.add(b).add(d).add(e).restrict(-1,1); // clamp is non-expansive
        E noise=new E(new Interval(-1,1),scaledKernelError(900));
        return c(63).add(c(64).mul(reference)).add(c(6).mul(noise)).error;
    }
}
