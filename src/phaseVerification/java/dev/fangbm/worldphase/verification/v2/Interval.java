package dev.fangbm.worldphase.verification.v2;

/** Closed, outward-rounded binary64 interval. Invalid arithmetic is never an empty 'safe' interval. */
public record Interval(double lo, double hi) {
    public Interval {
        if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi)
            throw new IllegalArgumentException("Nonfinite/reversed interval: " + lo + ", " + hi);
    }
    public static Interval point(double x) { return new Interval(x, x); }
    public Interval add(Interval b) { return new Interval(Math.nextDown(lo+b.lo), Math.nextUp(hi+b.hi)); }
    public Interval sub(Interval b) { return new Interval(Math.nextDown(lo-b.hi), Math.nextUp(hi-b.lo)); }
    public Interval mul(Interval b) {
        double a=lo*b.lo, c=lo*b.hi, d=hi*b.lo, e=hi*b.hi;
        return new Interval(Math.nextDown(Math.min(Math.min(a,c),Math.min(d,e))),
                Math.nextUp(Math.max(Math.max(a,c),Math.max(d,e))));
    }
    public Interval div(Interval b) {
        if (b.lo <= 0 && b.hi >= 0) throw new IllegalArgumentException("Interval division crosses zero");
        return mul(new Interval(Math.nextDown(1/b.hi),Math.nextUp(1/b.lo)));
    }
    public Interval expand(double error) {
        if (!(error >= 0) || !Double.isFinite(error)) throw new IllegalArgumentException("Invalid error");
        return new Interval(Math.nextDown(lo-error),Math.nextUp(hi+error));
    }
    public double maxAbs() { return Math.max(Math.abs(lo), Math.abs(hi)); }
    public Interval clamp(double min, double max) {
        return new Interval(Math.clamp(lo,min,max),Math.clamp(hi,min,max));
    }
    public static double upAdd(double a,double b) { return Math.nextUp(a+b); }
    public static double upMul(double a,double b) { return Math.nextUp(a*b); }
    public static double normUpper(double x,double z) {
        double square=upAdd(upMul(x,x),upMul(z,z));
        return Math.nextUp(StrictMath.sqrt(square));
    }
}
