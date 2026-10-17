/** Pure arithmetic check for the r1 Gaussian query proof; no Minecraft dependencies. */
class SpatialV2MathCheck {
    public static void main(String[] args) {
        if (Runtime.version().feature() != 25 || args.length != 2) {
            throw new IllegalArgumentException("Use Java25: java tools/SpatialV2MathCheck.java <width> <radius>");
        }
        double width = Double.parseDouble(args[0]), radius = Double.parseDouble(args[1]);
        if (!(width > 0) || !Double.isFinite(width) || !Double.isFinite(radius) || !(radius > width)) {
            throw new IllegalArgumentException("Require finite 0 < width < radius");
        }
        double q = radius / width;
        double exponent = -q * q;
        for (double v : new double[]{exponent, StrictMath.nextDown(exponent), Double.NEGATIVE_INFINITY}) {
            if (Double.doubleToRawLongBits(StrictMath.exp(v)) != 0L) {
                throw new AssertionError("Gaussian does not underflow to positive zero: " + v);
            }
        }
        if (StrictMath.exp(-4.0) == 0.0) throw new AssertionError("Short cutoff changes field");
        System.out.println("Java25 StrictMath binary64 Gaussian underflow: PASS");
    }
}
