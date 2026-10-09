package dev.fangbm.worldphase.tutorial;

import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Server-thread-only Fracture/Smooth search. All heights come from TerrainSampleService. */
public final class TutorialCandidateFinder {
    public static final int GENERATION_VERSION=3;
    private static final long CANDIDATE_SALT=0x575048313343414eL;
    private static final long INDEX_SALT=0x9e3779b97f4a7c15L;
    private static final long Z_SALT=0xd1b54a32d192ed03L;
    private final MinecraftServer server;
    private final TerrainSampleService sampling;

    public TutorialCandidateFinder(MinecraftServer server) { this(server,new TerrainSampleService(server)); }
    TutorialCandidateFinder(MinecraftServer server,TerrainSampleService sampling) {
        this.server=Objects.requireNonNull(server,"server"); this.sampling=Objects.requireNonNull(sampling,"sampling");
    }

    public record Point(int x,int z) {}
    /** Frozen legacy stencil for explicit diagnostics; no new sampling or mutable Level references. */
    public record Evaluation(TutorialCandidate candidate,List<Integer> fracture,List<Integer> smooth) {
        public Evaluation { fracture=List.copyOf(fracture); smooth=List.copyOf(smooth); }
    }
    public record Result(long seed,int centerX,int centerZ,TutorialSearchConfig config,
                         List<TutorialCandidate> candidates,int evaluatedCount,int heightSamples,
                         long computeNanos,long maxBatchNanos) {
        public Result { candidates=List.copyOf(candidates); }
    }

    public Search begin(int centerX,int centerZ,TutorialSearchConfig config) {
        Objects.requireNonNull(config,"config");
        long seed=checkedSeed();
        var f=server.getLevel(PhaseWorldResolver.levelKey(PhaseId.FRACTURE));
        var s=server.getLevel(PhaseWorldResolver.levelKey(PhaseId.SMOOTH));
        if (f.getMinY()!=s.getMinY() || f.getMaxY()!=s.getMaxY() || f.getSeaLevel()!=s.getSeaLevel())
            throw new IllegalStateException("Tutorial Phase height bounds/sea levels differ");
        return new Search(seed,centerX,centerZ,config,f.getSeaLevel(),f.getMinY(),f.getMaxY());
    }

    /** Single bounded candidate evaluation, using the same signals as a search. */
    public TutorialCandidate score(int x,int z,int centerX,int centerZ,TutorialSearchConfig config) {
        var search=begin(centerX,centerZ,config);
        checkCoordinate(x,z,TutorialScore.LOCAL_RADIUS);
        return search.evaluate(new Point(x,z));
    }

    public RefinedTutorialSearch beginRefined(int x,int z,TutorialSearchConfig config) {
        var metadata=begin(x,z,config);
        return new RefinedTutorialSearch(this::checkedSeed,sampling,metadata.seed,x,z,config,
                metadata.seaLevel,metadata.minY,metadata.maxY,null);
    }
    public RefinedTutorialSearch scoreRefined(int x,int z,int centerX,int centerZ,TutorialSearchConfig config) {
        var metadata=begin(centerX,centerZ,config); checkCoordinate(x,z,TutorialScore.LOCAL_RADIUS);
        return new RefinedTutorialSearch(this::checkedSeed,sampling,metadata.seed,centerX,centerZ,config,
                metadata.seaLevel,metadata.minY,metadata.maxY,new Point(x,z));
    }

    private long checkedSeed() {
        if (!server.isSameThread()) throw new IllegalStateException("Tutorial search must run on the server thread");
        Long seed=null;
        for(var phase:PhaseId.values()) {
            var level=server.getLevel(PhaseWorldResolver.levelKey(phase));
            if(level==null) throw new IllegalStateException("Tutorial Phase dimension is not loaded: "+PhaseWorldResolver.levelKey(phase).identifier());
            if(seed!=null && level.getSeed()!=seed) throw new IllegalStateException("Tutorial Phase raw seeds differ: "+phase);
            seed=level.getSeed();
        }
        return seed;
    }

    /** Hash-indexed disk rejection sampling, fixed salts, hard attempt cap, no mutable PRNG. */
    public static List<Point> positions(long seed,int centerX,int centerZ,TutorialSearchConfig config) {
        checkCoordinate(centerX,centerZ,config.radius()+TutorialScore.LOCAL_RADIUS);
        long base=mix(seed ^ CANDIDATE_SALT ^ pack(centerX,centerZ));
        var points=new ArrayList<Point>(); var unique=new HashSet<Point>();
        for(int attempt=0;attempt<config.candidateCount()*16 && points.size()<config.candidateCount();attempt++) {
            long channel=base+INDEX_SALT*attempt;
            int radius=config.radius(),span=2*radius+1;
            int dx=(int)Math.floorMod(mix(channel),(long)span)-radius;
            int dz=(int)Math.floorMod(mix(channel ^ Z_SALT),(long)span)-radius;
            if((long)dx*dx+(long)dz*dz>(long)radius*radius) continue;
            var point=new Point(centerX+dx,centerZ+dz);
            if(unique.add(point)) points.add(point);
        }
        if(points.size()!=config.candidateCount()) throw new IllegalStateException("Tutorial candidate generation exhausted its bounded attempts");
        return List.copyOf(points);
    }

    static List<TutorialCandidate> rank(List<TutorialCandidate> raw,int radius) {
        var sorted=new ArrayList<>(raw);
        // Known unsuitable terrain cannot displace a candidate meeting the declared minimum.
        // Keep failed heuristics as diagnostics, never claim their high score proves usefulness.
        sorted.sort(Comparator.comparingInt((TutorialCandidate c)->c.score().usefulHeuristic(radius)?0:1)
                .thenComparing(Comparator.comparingDouble((TutorialCandidate c)->c.score().totalScore()).reversed())
                .thenComparingInt(TutorialCandidate::x).thenComparingInt(TutorialCandidate::z));
        var spaced=new ArrayList<TutorialCandidate>();
        for(var candidate:sorted) {
            if(spaced.stream().noneMatch(c->StrictMath.hypot((double)c.x()-candidate.x(),(double)c.z()-candidate.z())
                    < TutorialSearchConfig.MIN_SPACING)) spaced.add(candidate);
        }
        return List.copyOf(spaced);
    }

    private static long pack(int x,int z) { return ((long)x<<32) ^ (z&0xffffffffL); }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static void checkCoordinate(int x,int z,int margin) {
        if(Math.abs((long)x)+margin>=30_000_000L || Math.abs((long)z)+margin>=30_000_000L)
            throw new IllegalArgumentException("Tutorial coordinates plus stencil must stay inside +/-30000000 blocks");
    }

    /** Ephemeral incremental work; never persisted or automatically run at startup. */
    public final class Search {
        private final long seed;
        private final int centerX,centerZ,seaLevel,minY,maxY;
        private final TutorialSearchConfig config;
        private final List<Point> points;
        private final List<TutorialCandidate> raw=new ArrayList<>();
        private final Map<PhaseId,Map<Long,Integer>> cache=new EnumMap<>(PhaseId.class);
        private int cursor,heightSamples;
        private int stencilCursor;
        private final int[][] pendingHeights=new int[2][9];
        private long computeNanos,maxBatchNanos;

        private Search(long seed,int x,int z,TutorialSearchConfig config,int seaLevel,int minY,int maxY) {
            this.seed=seed; centerX=x; centerZ=z; this.config=config;
            this.seaLevel=seaLevel; this.minY=minY; this.maxY=maxY;
            points=positions(seed,x,z,config);
            cache.put(PhaseId.FRACTURE,new HashMap<>()); cache.put(PhaseId.SMOOTH,new HashMap<>());
        }
        public boolean done() { return cursor==points.size(); }
        public int evaluatedCount() { return cursor; }
        public void advance(int heightBudget) {
            if(heightBudget<1 || heightBudget>4) throw new IllegalArgumentException("Tutorial batch must contain 1..4 height columns");
            if(checkedSeed()!=seed) throw new IllegalStateException("Tutorial raw seed changed during search");
            long start=System.nanoTime();
            for(int i=0;i<heightBudget && !done();i++) {
                int phase=stencilCursor/9,offsetIndex=stencilCursor%9;
                var point=points.get(cursor); var offset=TutorialScore.OFFSETS.get(offsetIndex);
                pendingHeights[phase][offsetIndex]=height(phase==0?PhaseId.FRACTURE:PhaseId.SMOOTH,point.x+offset.x(),point.z+offset.z());
                if(++stencilCursor==18) {
                    raw.add(candidate(point,pendingHeights[0],pendingHeights[1])); cursor++; stencilCursor=0;
                }
            }
            long elapsed=System.nanoTime()-start; computeNanos+=elapsed; maxBatchNanos=Math.max(maxBatchNanos,elapsed);
        }
        public Result result() {
            if(!done()) throw new IllegalStateException("Tutorial search is not complete");
            return new Result(seed,centerX,centerZ,config,rank(raw,config.radius()),cursor,heightSamples,computeNanos,maxBatchNanos);
        }
        public List<Evaluation> diagnostics() {
            if(!done()) throw new IllegalStateException("Tutorial search is not complete");
            return raw.stream().map(c->new Evaluation(c,
                    TutorialScore.OFFSETS.stream().map(o->cache.get(PhaseId.FRACTURE).get(pack(c.x()+o.x(),c.z()+o.z()))).toList(),
                    TutorialScore.OFFSETS.stream().map(o->cache.get(PhaseId.SMOOTH).get(pack(c.x()+o.x(),c.z()+o.z()))).toList())).toList();
        }
        private TutorialCandidate evaluate(Point point) {
            int[] f=heights(PhaseId.FRACTURE,point),s=heights(PhaseId.SMOOTH,point);
            return candidate(point,f,s);
        }
        private TutorialCandidate candidate(Point point,int[] f,int[] s) {
            var score=TutorialScore.evaluate(f,s,StrictMath.hypot((double)point.x-centerX,(double)point.z-centerZ),
                    config.radius(),seaLevel,minY,maxY);
            return new TutorialCandidate(point.x,point.z,f[0],s[0],score);
        }
        private int[] heights(PhaseId phase,Point point) {
            int[] heights=new int[TutorialScore.OFFSETS.size()];
            for(int i=0;i<heights.length;i++) {
                var offset=TutorialScore.OFFSETS.get(i);
                heights[i]=height(phase,point.x+offset.x(),point.z+offset.z());
            }
            return heights;
        }
        private int height(PhaseId phase,int x,int z) {
            long key=pack(x,z); Integer height=cache.get(phase).get(key);
            if(height==null) { height=sampling.sampleHeight(phase,x,z); cache.get(phase).put(key,height); heightSamples++; }
            return height;
        }
    }
}
