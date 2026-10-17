package dev.fangbm.worldphase.verification.v2.r2;

import dev.fangbm.worldphase.verification.v2.*;

/** Real interval + component derivative intervals + uniform binary64 forward error.
 * Native integer continentalness outputs and stored binary64 constants define the real model.
 * Full ulp per arithmetic op, two ulps for StrictMath transcendentals; never a sampled gradient.
 */
public record Jet(Interval v,Interval dx,Interval dz,double e) {
    private static final ThreadLocal<long[]> WORK=ThreadLocal.withInitial(()->new long[1]);
    public static long constructions(){return WORK.get()[0];}
    public Jet {WORK.get()[0]++;if(!Double.isFinite(e)||e<0)throw new IllegalArgumentException("UNKNOWN_ERROR");}
    public static Jet c(double n){return new Jet(Interval.point(n),Interval.point(0),Interval.point(0),0);}
    public static Jet x(double lo,double hi){return new Jet(new Interval(lo,hi),Interval.point(1),Interval.point(0),0);}
    public static Jet z(double lo,double hi){return new Jet(new Interval(lo,hi),Interval.point(0),Interval.point(1),0);}
    private static double round(Interval v,double e){return Interval.upAdd(e,Math.ulp(v.expand(e).maxAbs()));}
    public Jet add(Jet b){var r=v.add(b.v);return new Jet(r,dx.add(b.dx),dz.add(b.dz),round(r,Interval.upAdd(e,b.e)));}
    public Jet neg(){return new Jet(new Interval(-v.hi(),-v.lo()),new Interval(-dx.hi(),-dx.lo()),new Interval(-dz.hi(),-dz.lo()),e);}
    public Jet sub(Jet b){return add(b.neg());}
    public Jet mul(Jet b){var r=v.mul(b.v);double err=Interval.upAdd(Interval.upAdd(Interval.upMul(v.maxAbs(),b.e),Interval.upMul(b.v.maxAbs(),e)),Interval.upMul(e,b.e));return new Jet(r,dx.mul(b.v).add(v.mul(b.dx)),dz.mul(b.v).add(v.mul(b.dz)),round(r,err));}
    public Jet scale(double n){return mul(c(n));}
    public Jet div(double n){if(!(n>0))throw new IllegalArgumentException("DIVISOR");var r=v.div(Interval.point(n));return new Jet(r,dx.div(Interval.point(n)),dz.div(Interval.point(n)),round(r,Math.nextUp(e/n)));}
    public Jet div(Jet b){
        if(b.v.lo()<=0&&b.v.hi()>=0)throw new IllegalArgumentException("DIVISION_CROSSES_ZERO");
        double min=Math.min(Math.abs(b.v.lo()),Math.abs(b.v.hi()));
        if(b.e>=min)throw new IllegalArgumentException("ROUNDED_DIVISION_UNPROVEN");
        var r=v.div(b.v);var denominator=b.v.mul(b.v);
        double err=Interval.upAdd(e,Interval.upMul(r.maxAbs(),b.e));err=Math.nextUp(err/Math.nextDown(min-b.e));
        return new Jet(r,dx.mul(b.v).sub(v.mul(b.dx)).div(denominator),dz.mul(b.v).sub(v.mul(b.dz)).div(denominator),round(r,err));
    }
    public Jet clamp(double lo,double hi){var range=v.clamp(lo,hi);if(v.hi()<lo||v.lo()>hi)return new Jet(range,Interval.point(0),Interval.point(0),e);return new Jet(range,v.lo()>=lo&&v.hi()<=hi?dx:hull(dx,Interval.point(0)),v.lo()>=lo&&v.hi()<=hi?dz:hull(dz,Interval.point(0)),e);}
    public Jet exp(){var r=new Interval(down2(StrictMath.exp(v.lo())),up2(StrictMath.exp(v.hi()))).clamp(0,Double.MAX_VALUE);double derivative=up2(StrictMath.exp(Math.nextUp(v.hi()+e)));return new Jet(r,r.mul(dx),r.mul(dz),round(r,Interval.upAdd(Interval.upMul(derivative,e),2*Math.ulp(r.maxAbs()))));}
    public Jet sin(){var r=sinRange(v);var derivative=sinRange(v.add(Interval.point(Math.PI/2))).expand(4*Math.ulp(Math.PI));return new Jet(r,derivative.mul(dx),derivative.mul(dz),round(r,Interval.upAdd(e,2*Math.ulp(1.0))));}
    public static Interval sinRange(Interval a){if(a.hi()-a.lo()>=2*Math.PI)return new Interval(-1,1);double lo=Math.min(StrictMath.sin(a.lo()),StrictMath.sin(a.hi())),hi=Math.max(StrictMath.sin(a.lo()),StrictMath.sin(a.hi()));
        // Include possible extrema, with a generous outward phase margin (stored pi versus real pi).
        for(long k=(long)Math.floor((a.lo()-Math.PI/2)/Math.PI)-1;k<=(long)Math.ceil((a.hi()-Math.PI/2)/Math.PI)+1;k++){
            double t=Math.PI/2+k*Math.PI,err=16*Math.ulp(Math.max(1,Math.abs(t)));if(t+err>=a.lo()&&t-err<=a.hi()){if((k&1)==0)hi=1;else lo=-1;}}
        return new Interval(down2(lo),up2(hi)).clamp(-1,1);}
    public static double down2(double x){return Math.nextDown(Math.nextDown(x));}
    public static double up2(double x){return Math.nextUp(Math.nextUp(x));}
    public static Interval hull(Interval a,Interval b){return new Interval(Math.min(a.lo(),b.lo()),Math.max(a.hi(),b.hi()));}
    public static Jet hull(Jet a,Jet b){return new Jet(hull(a.v,b.v),hull(a.dx,b.dx),hull(a.dz,b.dz),Math.max(a.e,b.e));}
    public double gradient(){return Interval.normUpper(dx.maxAbs(),dz.maxAbs());}
    public Jet fade(){return mul(this).mul(this).mul(c(10).add(mul(c(-15).add(scale(6)))));}
    public static Jet blend(Jet a,Jet b,Jet w){return c(1).sub(w).mul(a).add(w.mul(b));}
    public Jet square(){var r=mul(this);double lo=v.lo()<=0&&v.hi()>=0?0:Math.min(v.lo()*v.lo(),v.hi()*v.hi());return new Jet(new Interval(Math.max(0,Math.nextDown(lo)),r.v.hi()),r.dx,r.dz,r.e);}
}
