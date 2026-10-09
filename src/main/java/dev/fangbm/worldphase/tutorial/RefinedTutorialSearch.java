package dev.fangbm.worldphase.tutorial;

import dev.fangbm.worldphase.phase.PhaseId;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.util.*;
import java.util.function.LongSupplier;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/** Fixed v4 two-stage budget. Ephemeral, server-thread-only; actual FULL validation is separate. */
public final class RefinedTutorialSearch {
    public static final int VERSION=4, CARDINAL_LIMIT=24, FINE_LIMIT=8;
    private static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
    private enum Stage { CENTERS, CARDINALS, FINE, DONE }
    private record Sample(PhaseId phase,TutorialCandidateFinder.Point point) {}
    public record Result(long seed,int centerX,int centerZ,TutorialSearchConfig config,
            List<TutorialCorridor> candidates,List<TutorialCorridor> analyzed,int evaluatedCount,
            int cardinalCount,int heightSamples,int batches,long computeNanos,long cpuNanos,long maxBatchNanos) {
        public Result { candidates=List.copyOf(candidates); analyzed=List.copyOf(analyzed); }
    }
    private final LongSupplier checkedSeed;
    private final TerrainSampleService sampling;
    private final long seed;
    private final int centerX,centerZ,seaLevel,minY,maxY;
    private final TutorialSearchConfig config;
    private final Map<Sample,Integer> cache=new HashMap<>();
    private List<TutorialCandidateFinder.Point> selected;
    private List<Sample> work;
    private final List<TutorialCorridor> analyzed=new ArrayList<>();
    private Stage stage;
    private int cursor,heightSamples,cardinalCount,batches;
    private final int evaluatedCount;
    private long computeNanos,cpuNanos,maxBatchNanos;

    RefinedTutorialSearch(LongSupplier checkedSeed,TerrainSampleService sampling,long seed,int x,int z,
            TutorialSearchConfig config,int seaLevel,int minY,int maxY,TutorialCandidateFinder.Point single) {
        this.checkedSeed=checkedSeed; this.sampling=sampling; this.seed=seed; centerX=x; centerZ=z;
        this.config=config; this.seaLevel=seaLevel; this.minY=minY; this.maxY=maxY;
        selected=single==null?TutorialCandidateFinder.positions(seed,x,z,config):List.of(single);
        evaluatedCount=selected.size(); stage=single==null?Stage.CENTERS:Stage.FINE;
        schedule(single==null?List.of(new TutorialCandidateFinder.Point(0,0)):TutorialCorridor.OFFSETS);
    }
    public boolean done() { return stage==Stage.DONE; }
    public void advance(int budget) {
        if(budget<1 || budget>4) throw new IllegalArgumentException("Tutorial batch must contain1..4 height columns");
        if(checkedSeed.getAsLong()!=seed) throw new IllegalStateException("Tutorial raw seed changed during search");
        long start=System.nanoTime(),cpuStart=threadCpu(); batches++;
        for(int i=0;i<budget && !done();i++) {
            var sample=work.get(cursor++);
            cache.put(sample,sampling.sampleHeight(sample.phase,sample.point.x(),sample.point.z())); heightSamples++;
            if(cursor==work.size()) finishStage();
        }
        long elapsed=System.nanoTime()-start; computeNanos+=elapsed; maxBatchNanos=Math.max(maxBatchNanos,elapsed);
        long cpuEnd=threadCpu();
        if(cpuStart<0 || cpuEnd<0) cpuNanos=-1;
        else if(cpuNanos>=0) cpuNanos+=Math.max(0,cpuEnd-cpuStart);
    }
    public Result result() {
        if(!done()) throw new IllegalStateException("Tutorial search is not complete");
        var ranked=new ArrayList<>(analyzed); ranked.sort(TutorialCorridor.ORDER);
        return new Result(seed,centerX,centerZ,config,ranked,analyzed,evaluatedCount,cardinalCount,heightSamples,batches,computeNanos,cpuNanos,maxBatchNanos);
    }
    private static long threadCpu() {
        return CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled()?CPU.getCurrentThreadCpuTime():-1;
    }
    public static int maximumHeightCalls(int count) {
        if(count<8 || count>256) throw new IllegalArgumentException("candidateCount must be8..256");
        return 2*count+8*Math.min(count,CARDINAL_LIMIT)+56*Math.min(count,FINE_LIMIT);
    }
    private void finishStage() {
        if(stage==Stage.CENTERS) {
            selected=select(selected,Comparator.comparingDouble(this::centerScore).reversed()
                    .thenComparingInt(TutorialCandidateFinder.Point::x).thenComparingInt(TutorialCandidateFinder.Point::z),CARDINAL_LIMIT,32);
            cardinalCount=selected.size(); stage=Stage.CARDINALS;
            schedule(List.of(new TutorialCandidateFinder.Point(16,0),new TutorialCandidateFinder.Point(-16,0),
                    new TutorialCandidateFinder.Point(0,16),new TutorialCandidateFinder.Point(0,-16)));
        } else if(stage==Stage.CARDINALS) {
            selected=select(selected,Comparator.comparingInt((TutorialCandidateFinder.Point p)->cardinalInteresting(p)?0:1)
                    .thenComparing(Comparator.comparingDouble(this::cardinalScore).reversed())
                    .thenComparingInt(TutorialCandidateFinder.Point::x).thenComparingInt(TutorialCandidateFinder.Point::z),FINE_LIMIT,64);
            stage=Stage.FINE; schedule(TutorialCorridor.OFFSETS);
        } else if(stage==Stage.FINE) {
            for(var point:selected) {
                int[][] heights=new int[2][33];
                for(int phase=0;phase<2;phase++) for(int i=0;i<33;i++) {
                    var o=TutorialCorridor.OFFSETS.get(i);
                    heights[phase][i]=height(phase==0?PhaseId.FRACTURE:PhaseId.SMOOTH,point,o.x(),o.z());
                }
                analyzed.add(TutorialCorridor.evaluate(point.x(),point.z(),heights[0],heights[1],distance(point),
                        config.radius(),seaLevel,minY,maxY));
            }
            stage=Stage.DONE;
        }
    }
    private void schedule(List<TutorialCandidateFinder.Point> offsets) {
        var tasks=new LinkedHashSet<Sample>();
        for(var point:selected) for(var offset:offsets) for(var phase:List.of(PhaseId.FRACTURE,PhaseId.SMOOTH)) {
            var sample=new Sample(phase,new TutorialCandidateFinder.Point(point.x()+offset.x(),point.z()+offset.z()));
            if(!cache.containsKey(sample)) tasks.add(sample);
        }
        work=List.copyOf(tasks); cursor=0;
        if(work.isEmpty()) finishStage();
    }
    private List<TutorialCandidateFinder.Point> select(List<TutorialCandidateFinder.Point> input,
            Comparator<TutorialCandidateFinder.Point> order,int limit,int spacing) {
        var sorted=new ArrayList<>(input); sorted.sort(order);
        var result=new ArrayList<TutorialCandidateFinder.Point>();
        for(var p:sorted) if(valid(p) && result.stream().noneMatch(q->StrictMath.hypot((double)p.x()-q.x(),(double)p.z()-q.z())<spacing)) {
            result.add(p); if(result.size()==limit) break;
        }
        return List.copyOf(result);
    }
    private int height(PhaseId phase,TutorialCandidateFinder.Point p,int dx,int dz) {
        return cache.get(new Sample(phase,new TutorialCandidateFinder.Point(p.x()+dx,p.z()+dz)));
    }
    private boolean valid(TutorialCandidateFinder.Point p) {
        for(var phase:List.of(PhaseId.FRACTURE,PhaseId.SMOOTH)) {
            int h=height(phase,p,0,0); if(h<minY || h>(double)maxY-2) return false;
        }
        return true;
    }
    private double distance(TutorialCandidateFinder.Point p) { return StrictMath.hypot((double)p.x()-centerX,(double)p.z()-centerZ); }
    private boolean coast(TutorialCandidateFinder.Point p) { return height(PhaseId.FRACTURE,p,0,0)<=(double)seaLevel+1 || height(PhaseId.SMOOTH,p,0,0)<=(double)seaLevel+1; }
    private double centerScore(TutorialCandidateFinder.Point p) {
        double h=Math.abs((double)height(PhaseId.FRACTURE,p,0,0)-height(PhaseId.SMOOTH,p,0,0));
        return 6*Math.min(h/32,1)+8/(1+h/4)-12*distance(p)/config.radius()-(coast(p)?8:0);
    }
    private double[] cardinal(TutorialCandidateFinder.Point p) {
        double best=-Double.MAX_VALUE; boolean interesting=false;
        for(var axis:TutorialCorridor.AXES.subList(0,2)) {
            double[] slopes=new double[2]; double h=0,minGap=Double.MAX_VALUE; boolean coast=false;
            for(int t:new int[]{-16,0,16}) {
                int f=height(PhaseId.FRACTURE,p,axis.x()*t,axis.z()*t),s=height(PhaseId.SMOOTH,p,axis.x()*t,axis.z()*t);
                double gap=Math.abs((double)f-s); h=Math.max(h,gap); minGap=Math.min(minGap,gap);
                coast|=Math.min(f,s)<=(double)seaLevel+1;
                if(t!=0) { slopes[0]=Math.max(slopes[0],Math.abs((double)f-height(PhaseId.FRACTURE,p,0,0))/16);
                    slopes[1]=Math.max(slopes[1],Math.abs((double)s-height(PhaseId.SMOOTH,p,0,0))/16); }
            }
            interesting|=slopes[0]>=.375 && slopes[1]<=.25 && slopes[0]-slopes[1]>=.25 && h>=4 && minGap<=4;
            double value=6*Math.min(h/32,1)+64*Math.max(-2,Math.min(2,slopes[0]-slopes[1]))
                    -128*Math.max(0,Math.min(2,slopes[1]-.25))-16*Math.max(0,Math.min(1,(.375-slopes[0])/.375))
                    -12*distance(p)/config.radius()-(coast?8:0)+(minGap<=4?8:0);
            best=Math.max(best,value);
        }
        return new double[]{best,interesting?1:0};
    }
    private double cardinalScore(TutorialCandidateFinder.Point p) { return cardinal(p)[0]; }
    private boolean cardinalInteresting(TutorialCandidateFinder.Point p) { return cardinal(p)[1]!=0; }
}
