package dev.fangbm.worldphase.tutorial;

import java.util.*;

/** Directional base-column evidence. No field certifies actual collision, water safety or travel. */
public record TutorialCorridor(TutorialCandidate candidate, int dx, int dz, double fractureStep,
        double smoothStep, double heightContrast, int heightCompatiblePoints, Risk risk,
        double score, boolean interesting, List<TutorialCandidateFinder.Point> shiftProbePoints) {
    public enum Risk { INVALID, REQUIRES_VALIDATION, LOW_RISK }
    public static final List<TutorialCandidateFinder.Point> AXES=List.of(
            new TutorialCandidateFinder.Point(1,0),new TutorialCandidateFinder.Point(0,1),
            new TutorialCandidateFinder.Point(1,1),new TutorialCandidateFinder.Point(1,-1));
    public static final List<TutorialCandidateFinder.Point> OFFSETS=offsets();

    public TutorialCorridor {
        Objects.requireNonNull(candidate); Objects.requireNonNull(risk);
        shiftProbePoints=List.copyOf(shiftProbePoints);
        if(shiftProbePoints.size()!=9 || heightCompatiblePoints<0 || heightCompatiblePoints>9
                || !Double.isFinite(fractureStep) || !Double.isFinite(smoothStep)
                || !Double.isFinite(heightContrast) || !Double.isFinite(score))
            throw new IllegalArgumentException("Invalid bounded corridor evidence");
    }
    public String status() { return interesting ? "INTERESTING_BUT_SHIFT_UNVERIFIED" : "INSUFFICIENT_DIRECTIONAL_CONTRAST"; }
    public int priority() { return risk==Risk.INVALID?3:interesting?(heightCompatiblePoints>0?0:1):2; }

    /** Same deterministic priority is used for choosing an axis and ranking different centers. */
    public static final Comparator<TutorialCorridor> ORDER=Comparator.comparingInt(TutorialCorridor::priority)
            .thenComparing(Comparator.comparingDouble(TutorialCorridor::score).reversed())
            .thenComparingInt(c->c.candidate.x()).thenComparingInt(c->c.candidate.z());

    public static TutorialCorridor evaluate(int x,int z,int[] fracture,int[] smooth,double distance,
            int radius,int seaLevel,int minY,int maxY) {
        if(fracture.length!=33 || smooth.length!=33) throw new IllegalArgumentException("Corridors require33 columns per Phase");
        int[] oldF=new int[9],oldS=new int[9];
        for(int i=0;i<9;i++) {
            var o=TutorialScore.OFFSETS.get(i); int index=OFFSETS.indexOf(new TutorialCandidateFinder.Point(o.x(),o.z()));
            oldF[i]=fracture[index]; oldS[i]=smooth[index];
        }
        var legacy=TutorialScore.evaluate(oldF,oldS,distance,radius,seaLevel,minY,maxY);
        var candidate=new TutorialCandidate(x,z,fracture[0],smooth[0],legacy);
        TutorialCorridor best=null;
        for(var axis:AXES) {
            double fStep=0,sStep=0,contrast=0; int compatible=0,previous=-1;
            boolean invalid=false,coast=false;
            var probes=new ArrayList<TutorialCandidateFinder.Point>();
            for(int t=-16;t<=16;t+=4) {
                int index=OFFSETS.indexOf(new TutorialCandidateFinder.Point(axis.x()*t,axis.z()*t));
                double gap=Math.abs((double)fracture[index]-smooth[index]); contrast=Math.max(contrast,gap);
                if(gap<=4) compatible++;
                for(int h:new int[]{fracture[index],smooth[index]}) {
                    invalid|=h<minY || h>(double)maxY-2; coast|=h<=(double)seaLevel+1;
                }
                if(previous>=0) {
                    double length=4*StrictMath.hypot(axis.x(),axis.z());
                    fStep=Math.max(fStep,Math.abs((double)fracture[index]-fracture[previous])/length);
                    sStep=Math.max(sStep,Math.abs((double)smooth[index]-smooth[previous])/length);
                }
                previous=index; probes.add(new TutorialCandidateFinder.Point(x+axis.x()*t,z+axis.z()*t));
            }
            var risk=invalid?Risk.INVALID:coast||fStep>2||sStep>2?Risk.REQUIRES_VALIDATION:Risk.LOW_RISK;
            double value=6*Math.min(contrast/32,1)+64*clamp(fStep-sStep,-2,2)
                    -128*clamp(sStep-.25,0,2)-16*clamp((.375-fStep)/.375,0,1)
                    -12*distance/radius-(risk==Risk.REQUIRES_VALIDATION?8:0)+4*Math.min(compatible,3)/3.0;
            boolean interesting=risk!=Risk.INVALID && fStep>=.375 && sStep<=.25 && fStep-sStep>=.25
                    && contrast>=4 && distance<=radius;
            var option=new TutorialCorridor(candidate,axis.x(),axis.z(),fStep,sStep,contrast,compatible,risk,value,interesting,probes);
            if(best==null || ORDER.compare(option,best)<0) best=option; // Equal: stable AXES order.
        }
        return best;
    }
    private static double clamp(double n,double min,double max) { return Math.max(min,Math.min(max,n)); }
    private static List<TutorialCandidateFinder.Point> offsets() {
        var result=new LinkedHashSet<TutorialCandidateFinder.Point>(); result.add(new TutorialCandidateFinder.Point(0,0));
        for(var axis:AXES) for(int t=-16;t<=16;t+=4) result.add(new TutorialCandidateFinder.Point(axis.x()*t,axis.z()*t));
        return List.copyOf(result);
    }
}
