package dev.fangbm.worldphase.verification;

import java.util.ArrayList;
import java.util.List;

/** Pure development-report accounting. No Minecraft types or production dependency. */
public final class CoreInvariantLedger {
    private String scenario = "startup";
    private String failedInvariant;
    private int successfulTransfers, completedCycles;
    private double maxXzDeviation, maxRotationDeviation, maxYCorrection;
    private final List<String> rejected = new ArrayList<>();

    public void scenario(String value) { scenario = value; }
    public String scenario() { return scenario; }
    public String failedInvariant() { return failedInvariant; }
    public int successfulTransfers() { return successfulTransfers; }
    public int completedCycles() { return completedCycles; }
    public double maxXzDeviation() { return maxXzDeviation; }
    public double maxRotationDeviation() { return maxRotationDeviation; }
    public double maxYCorrection() { return maxYCorrection; }
    public List<String> rejectedScenarios() { return List.copyOf(rejected); }

    public void require(boolean condition, String invariant, String detail) {
        if (!condition) {
            if (failedInvariant == null) failedInvariant = invariant;
            throw new IllegalStateException("[" + invariant + "] scenario=" + scenario + ": " + detail);
        }
    }

    /** Called only after entity/inventory/landing assertions pass. Failed moves remain uncounted. */
    public void transferred(double x, double z, double y, float yaw, float pitch,
                            double actualX, double actualZ, double actualY, float actualYaw, float actualPitch) {
        require(Double.isFinite(x) && Double.isFinite(z) && Double.isFinite(y)
                && Double.isFinite(actualX) && Double.isFinite(actualZ) && Double.isFinite(actualY)
                && Float.isFinite(yaw) && Float.isFinite(pitch)
                && Float.isFinite(actualYaw) && Float.isFinite(actualPitch), "transfer.finite", "non-finite coordinate/rotation");
        maxXzDeviation = Math.max(maxXzDeviation, Math.max(Math.abs(actualX-x), Math.abs(actualZ-z)));
        maxRotationDeviation = Math.max(maxRotationDeviation, Math.max(Math.abs((double)actualYaw-yaw), Math.abs((double)actualPitch-pitch)));
        maxYCorrection = Math.max(maxYCorrection, Math.abs(actualY-y));
        require(Double.doubleToRawLongBits(x)==Double.doubleToRawLongBits(actualX)
                && Double.doubleToRawLongBits(z)==Double.doubleToRawLongBits(actualZ), "transfer.xz", "horizontal coordinates changed");
        require(Float.floatToRawIntBits(yaw)==Float.floatToRawIntBits(actualYaw)
                && Float.floatToRawIntBits(pitch)==Float.floatToRawIntBits(actualPitch), "transfer.rotation", "yaw/pitch changed");
        require(Math.abs(actualY-y)<=4, "transfer.bounded_y", "y moved beyond +/-4");
        successfulTransfers++;
    }

    public void cycleCompleted() { completedCycles++; }
    public void rejected(String outcome) { rejected.add(scenario + ": " + outcome); }
}
