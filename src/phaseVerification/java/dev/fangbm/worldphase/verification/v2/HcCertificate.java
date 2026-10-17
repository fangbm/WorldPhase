package dev.fangbm.worldphase.verification.v2;

/** Continuous model enclosure plus uniform program error, never sparse-sample certification.
 * A rounded nonconstant function is not globally Lipschitz with the model's derivative bound.
 * r1 has no accepted minimum separation/error-slack slope semantics, so such nodes fail closed.
 */
public record HcCertificate(Interval height, double modelLipschitzUpper, double heightError,
                            boolean modelCandidate, String reason, int referenceCalls) {
    @FunctionalInterface public interface Reference { double at(int x,int z); }
    public boolean eligible() { return reason.equals("CERTIFIED"); }
    public double incrementUpper(double distance) {
        if (!(distance>=0) || !Double.isFinite(distance)) throw new IllegalArgumentException("Invalid distance");
        return Interval.upAdd(Interval.upMul(modelLipschitzUpper,distance),Interval.upMul(2,heightError));
    }
    public static final class Builder {
        private final int x0,z0,nx,nz;
        private final double[] values;
        private int cursor;
        public Builder(R1.Point c) {
            if(!Double.isFinite(c.x())||!Double.isFinite(c.z())||Math.abs(c.x())+33>29_999_984||Math.abs(c.z())+33>29_999_984)
                throw new IllegalArgumentException("Certificate region outside supported world coordinates");
            // Exact endpoints of the stored center avoid an inward-rounded c+/-32 region.
            var cx=new java.math.BigDecimal(c.x());var cz=new java.math.BigDecimal(c.z());
            var radius=java.math.BigDecimal.valueOf(32);
            x0=cx.subtract(radius).setScale(0,java.math.RoundingMode.FLOOR).intValueExact();
            z0=cz.subtract(radius).setScale(0,java.math.RoundingMode.FLOOR).intValueExact();
            nx=cx.add(radius).setScale(0,java.math.RoundingMode.CEILING).intValueExact()-x0+1;
            nz=cz.add(radius).setScale(0,java.math.RoundingMode.CEILING).intValueExact()-z0+1;
            if(nx>66||nz>66)throw new IllegalStateException("r1 envelope budget");
            values=new double[nx*nz];
        }
        public boolean done() { return cursor==values.length; }
        public int size() { return values.length; }
        public void next(Reference f) {
            if(done())throw new IllegalStateException("Certificate already sampled");
            double v=f.at(x0+cursor/nz,z0+cursor%nz);
            if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite native continentalness reference");
            values[cursor++]=v;
        }
        public HcCertificate finish() {
            if(!done())throw new IllegalStateException("Incomplete continuous envelope");
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,bx=0,bz=0;
            for(double v:values){min=Math.min(min,v);max=Math.max(max,v);}
            for(int x=0;x<nx-1;x++)for(int z=0;z<nz-1;z++) {
                double v00=values[x*nz+z],v10=values[(x+1)*nz+z],v01=values[x*nz+z+1],v11=values[(x+1)*nz+z+1];
                bx=Math.max(bx,Math.max(Interval.point(v10).sub(Interval.point(v00)).maxAbs(),Interval.point(v11).sub(Interval.point(v01)).maxAbs()));
                bz=Math.max(bz,Math.max(Interval.point(v01).sub(Interval.point(v00)).maxAbs(),Interval.point(v11).sub(Interval.point(v10)).maxAbs()));
            }
            double error=GradientRoundoff.heightError(min,max);
            Interval c=new Interval(min,max).clamp(-1,1);
            Interval heights=Interval.point(63).add(Interval.point(64).mul(c)).add(new Interval(-6,6)).expand(error);
            double n=Interval.point(6).mul(Interval.point(GradientRoundoff.componentDerivativeUpper())).div(Interval.point(900)).hi();
            double lx=Interval.upAdd(Interval.upMul(64,bx),n),lz=Interval.upAdd(Interval.upMul(64,bz),n);
            double slope=Interval.normUpper(lx,lz);
            boolean dry=heights.lo()>=71&&Math.nextDown(heights.lo()-32)>=-40&&Math.nextUp(heights.hi()+20)<=240;
            boolean candidate=dry&&slope<=.125;
            String reason=!dry?"WET_OR_HEIGHT_REJECTED":slope>.125?"MODEL_SLOPE_REJECTED":"R1_ROUNDED_LIPSCHITZ_UNPROVEN";
            return new HcCertificate(heights,slope,error,candidate,reason,values.length);
        }
    }
}
