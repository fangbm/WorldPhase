package dev.fangbm.worldphase.tutorial;

/** Explainable base-column heuristics. None of these fields proves a safe route or landing. */
public record TutorialScore(double fractureSlope, double smoothSlope, double heightContrast,
                            double slopeContrast, double spawnDistance, double heightContribution,
                            double slopeContribution, double distancePenalty, double hazardPenalty) {
    public static final int LOCAL_RADIUS = 16;
    // Center, E, W, S, N, SE, NE, SW, NW. Kept immutable and shared with the sampler.
    public record Offset(int x, int z) {}
    public static final java.util.List<Offset> OFFSETS = java.util.List.of(new Offset(0,0),
            new Offset(16,0), new Offset(-16,0), new Offset(0,16), new Offset(0,-16),
            new Offset(16,16), new Offset(16,-16), new Offset(-16,16), new Offset(-16,-16));

    public TutorialScore {
        for (double value : new double[]{fractureSlope,smoothSlope,heightContrast,slopeContrast,spawnDistance,
                heightContribution,slopeContribution,distancePenalty,hazardPenalty}) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Tutorial score must be finite");
        }
        if (!Double.isFinite(heightContribution+slopeContribution-distancePenalty-hazardPenalty))
            throw new IllegalArgumentException("Tutorial total score must be finite");
    }

    public double totalScore() { return heightContribution + slopeContribution - distancePenalty - hazardPenalty; }

    public boolean usefulHeuristic(int radius) {
        return heightContrast >= 4 && fractureSlope >= .375 && smoothSlope <= .25
                && slopeContrast >= .25 && hazardPenalty == 0 && spawnDistance <= radius;
    }

    public static TutorialScore evaluate(int[] fracture, int[] smooth, double distance, int radius,
                                         int seaLevel, int minY, int maxY) {
        if (fracture.length != OFFSETS.size() || smooth.length != OFFSETS.size())
            throw new IllegalArgumentException("Tutorial scoring requires center and eight neighbor heights per Phase");
        if (!Double.isFinite(distance) || distance < 0 || radius <= 0 || minY >= maxY)
            throw new IllegalArgumentException("Invalid tutorial scoring distance/radius/height bounds");
        double f = slope(fracture), s = slope(smooth), contrast = Math.abs((double) fracture[0] - smooth[0]);
        double slopeValue = 64 * clamp(f-s,-2,2) - 128 * clamp(s-.25,0,2)
                - 16 * clamp((.375-f)/.375,0,1);
        double hazard = 0;
        for (int[] heights : new int[][]{fracture,smooth}) {
            boolean low=false, invalid=false;
            for (int h : heights) { low |= h <= (double)seaLevel+1; invalid |= h < minY || h > (double)maxY-2; }
            if (low) hazard += 60; // Conservative sea/coast hint; base columns do not inspect fluids.
            if (invalid) hazard += 120; // Empty column sentinel, vertical edge or invalid data.
        }
        if (f > 2 || s > 2) hazard += 30; // Extreme local cliffs are poor first lessons.
        return new TutorialScore(f,s,contrast,f-s,distance,6*Math.min(contrast/32,1),slopeValue,
                12*distance/radius,hazard);
    }

    private static double slope(int[] heights) {
        double maximum=0;
        for (int i=1;i<heights.length;i++) {
            var offset=OFFSETS.get(i);
            maximum=Math.max(maximum,Math.abs((double)heights[i]-heights[0])/StrictMath.hypot(offset.x,offset.z));
        }
        return maximum;
    }
    private static double clamp(double value,double low,double high) { return Math.max(low,Math.min(high,value)); }
}
