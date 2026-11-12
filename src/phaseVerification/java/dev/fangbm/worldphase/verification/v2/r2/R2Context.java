package dev.fangbm.worldphase.verification.v2.r2;

import dev.fangbm.worldphase.verification.v2.*;
import dev.fangbm.worldphase.worldgen.noise.GradientNoise;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Owner-thread, fail-closed r2 preparation. No Level/chunk access. Never publishes a partial profile. */
public final class R2Context implements AutoCloseable {
    public enum State { NEW,PREPARING,PREPARATION_COMPLETED,FAILED,CLOSED }
    public record Tick(int rawCalls,long wallNanos,long cpuNanos,long allocatedBytes){}
    public static final class Node {
        public final R1.Id id;public final R1.Point center;
        public String status="UNEXAMINED";public double h;public Jet anchor;
        public final Port[][] ports=new Port[2][4];public List<R2.Dir> pair=List.of();
        Node(long seed,R1.Id id){this.id=id;this.center=R2.center(seed,id);}
    }
    public static final class Port {
        public String reason="UNEXAMINED";public Jet endpoint;public double end,delta,maxRange,maxGradient,maxError;
        public int cells;
        boolean accepted(){return reason.equals("PORT_MODEL_READY_NOT_FULL_SAFE");}
    }
    public final long seed;
    private final HcCertificate.Reference reference;
    private final Thread owner=Thread.currentThread();
    private final LongSupplier clock;
    private final boolean timed;
    private final long wallStart;
    private long stepCpuStart,stepAllocationStart,completedCpu,completedAllocation;
    private final long[] rawKeys=new long[131072];private final double[] rawValues=new double[131072];private final boolean[] rawUsed=new boolean[131072];
    public final List<Node> nodes=new ArrayList<>();public final List<Tick> ticks=new ArrayList<>();
    public State state=State.NEW;public String failure="";public long rawCalls,cacheHits,cacheMisses,heightEvaluations,edgeTests,certificateWork,jetConstructions;
    public long cpuNanos,wallNanos,ownedAllocation,maxMeasuredStepWall,retainedBytes=-1;public int rawEntries;
    private long activeStart,initializationExtraWall;private int activeCalls;
    public List<R1.Id> shortlistIds=List.of();
    private List<Node> detailed=List.of();private int anchorIndex,nodeIndex,phase,dir,cell;private int x0,z0,x1,z1;
    private static final GradientNoise NOISE=new GradientNoise(1);
    private static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    public R2Context(long seed,DensityFunction raw){this(seed,(x,z)->raw.compute(new DensityFunction.SinglePointContext(x,0,z)),System::nanoTime,true);}
    /** Explicit deterministic-clock semantic fixture; never use for measured performance evidence. */
    public R2Context(long seed,HcCertificate.Reference reference,LongSupplier clock,boolean timed){
        R2.requireSeed(seed);this.seed=seed;this.reference=reference;this.clock=clock;this.timed=timed;
        wallStart=clock.getAsLong();stepCpuStart=cpu();stepAllocationStart=allocated();
        for(var id:R2.targets())nodes.add(new Node(seed,id));
        completedCpu=cpu()-stepCpuStart;completedAllocation=allocated()-stepAllocationStart+cacheAllocation();
        // Primitive caches were allocated before constructor body: include their measured sizes in owned accounting below.
    }
    public static long cpu(){long value=BEAN.getCurrentThreadCpuTime();if(value<0)throw new IllegalStateException("CPU_MEASUREMENT_UNAVAILABLE");return value;}
    public static long allocated(){if(!BEAN.isThreadAllocatedMemorySupported()||!BEAN.isThreadAllocatedMemoryEnabled())throw new IllegalStateException("ALLOCATION_MEASUREMENT_UNAVAILABLE");return BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId());}
    private static long cacheAllocation(){return 131072L*(8+8+1)+3*32;}
    private void guard(long start,int startCalls){
        cpuNanos=completedCpu+cpu()-stepCpuStart;wallNanos=initializationExtraWall+clock.getAsLong()-wallStart;ownedAllocation=completedAllocation+allocated()-stepAllocationStart;
        if(rawCalls>52048||rawCalls>65536||rawEntries>65536||heightEvaluations>95744||edgeTests>4691456)throw new IllegalStateException("BUDGET_EXCEEDED:WORK_CAP");
        if(timed){
            if(clock.getAsLong()-start>25_000_000L)throw new IllegalStateException("BUDGET_EXCEEDED:HARD_TICK_25MS");
            if(cpuNanos>1_500_000_000L)throw new IllegalStateException("BUDGET_EXCEEDED:CPU_1500MS");
            if(wallNanos>15_000_000_000L)throw new IllegalStateException("BUDGET_EXCEEDED:WALL_15000MS");
            if(ticks.size()+1>=256)throw new IllegalStateException("BUDGET_EXCEEDED:TICKS_256");
            if(ownedAllocation>268435456L)throw new IllegalStateException("BUDGET_EXCEEDED:ALLOCATION_256MIB");
        }
        if(rawCalls-startCalls>2048)throw new IllegalStateException("BUDGET_EXCEEDED:RAW_TICK_CAP");
    }
    public void step(){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("OWNER_THREAD_REQUIRED");
        if(state!=State.NEW&&state!=State.PREPARING)throw new IllegalStateException("PREPARATION_STATE:"+state);
        state=State.PREPARING;long start=clock.getAsLong(),cpu=cpu(),alloc=allocated();stepCpuStart=cpu;stepAllocationStart=alloc;int before=(int)rawCalls;activeStart=start;activeCalls=before;long jets=Jet.constructions();
        try{
            guard(start,before);
            // One anchor or one terminal cell is the indivisible unit; check before and after it.
            while(state==State.PREPARING){
                if(rawCalls-before>2032)break;
                if(anchorIndex<nodes.size())anchor(nodes.get(anchorIndex++));
                else if(detailed.isEmpty()&&nodeIndex==0){
                    var ids=R2.shortlist(seed,nodes.stream().filter(n->n.status.equals("ANCHOR_DRY")).map(n->n.id).toList());
                    shortlistIds=ids;detailed=nodes.stream().filter(n->ids.contains(n.id)).toList();
                    for(Node n:nodes)if(n.status.equals("ANCHOR_DRY")&&!ids.contains(n.id))n.status="DEFERRED_NOT_SHORTLISTED";
                    if(detailed.isEmpty()){nodeIndex=1;finish();}
                    else work();
                }else if(nodeIndex<detailed.size())work();else finish();
                guard(start,before);
                if(timed&&clock.getAsLong()-start>=8_000_000L)break;
                if(!timed&&rawCalls-before>=2032)break;
            }
        }catch(RuntimeException ex){state=State.FAILED;failure=ex.getMessage();for(Node n:nodes)if(n.status.equals("MATH_NODE_READY"))n.status="INVALIDATED_PROFILE_FAILURE";throw ex;}
        finally{jetConstructions+=Jet.constructions()-jets;cpuNanos=completedCpu+cpu()-stepCpuStart;wallNanos=initializationExtraWall+clock.getAsLong()-wallStart;ownedAllocation=completedAllocation+allocated()-stepAllocationStart;completedCpu=cpuNanos;completedAllocation=ownedAllocation;ticks.add(new Tick((int)rawCalls-before,clock.getAsLong()-start,cpu()-cpu,allocated()-alloc));}
    }
    public void measuredStep(){
        long begin=System.nanoTime(),cpu=cpu(),alloc=allocated(),priorCpu=completedCpu,priorAlloc=completedAllocation;
        try{step();}finally{
            cpuNanos=priorCpu+cpu()-cpu;ownedAllocation=priorAlloc+allocated()-alloc;completedCpu=cpuNanos;completedAllocation=ownedAllocation;
            long elapsed=System.nanoTime()-begin;maxMeasuredStepWall=Math.max(maxMeasuredStepWall,elapsed);
            if(state!=State.FAILED&&timed){
                if(elapsed>25_000_000L)abort("BUDGET_EXCEEDED:HARD_TICK_25MS_INCLUDING_RECORDING");
                else if(cpuNanos>1_500_000_000L)abort("BUDGET_EXCEEDED:CPU_1500MS_INCLUDING_RECORDING");
                else if(ownedAllocation>268435456L)abort("BUDGET_EXCEEDED:ALLOCATION_256MIB_INCLUDING_RECORDING");
            }
        }
    }
    private void anchor(Node n){
        if(Math.abs(n.center.x())+192>29999984||Math.abs(n.center.z())+192>29999984){n.status="ROI_MISS_WORLD_BORDER";return;}
        n.anchor=hc(Jet.x(n.center.x(),n.center.x()),Jet.z(n.center.z(),n.center.z()));
        var q=R2.quantize(n.anchor);
        if(!q.reason().equals("QUANTIZED"))n.status=q.reason().equals("POINT_BIN_UNCERTAIN")?"ANCHOR_BIN_UNCERTAIN":"ANCHOR_ERROR_UNPROVEN";
        else{n.h=q.height();n.status=n.h<71?"WET_ANCHOR":n.h-32< -40||n.h+20>240?"BAND_LIMIT":"ANCHOR_DRY";}
    }
    public double raw(int x,int z){
        if(state==State.CLOSED||state==State.FAILED)throw new IllegalStateException("CACHE_INVALIDATED:"+state);
        if(Math.abs((long)x)>29999984||Math.abs((long)z)>29999984)throw new IllegalStateException("ROI_MISS_WORLD_BORDER");
        long key=((long)x<<32)^(z&0xffffffffL);int slot=(int)R1.mix(key)&(rawKeys.length-1);
        while(rawUsed[slot]){if(rawKeys[slot]==key){cacheHits++;return rawValues[slot];}slot=(slot+1)&(rawKeys.length-1);}
        if(rawEntries>=65536||rawCalls>=52048)throw new IllegalStateException("BUDGET_EXCEEDED:RAW_CACHE_OR_CALLS");
        cacheMisses++;rawCalls++;double v=reference.at(x,z);if(!Double.isFinite(v))throw new IllegalStateException("NONFINITE_REFERENCE");
        rawUsed[slot]=true;rawKeys[slot]=key;rawValues[slot]=v;rawEntries++;return v;
    }
    private boolean dry(R1.Id id){return nodes.stream().anyMatch(n->n.id.equals(id)&&(n.status.equals("ANCHOR_DRY")||n.status.equals("DEFERRED_NOT_SHORTLISTED")||n.status.equals("PORT_GEOMETRY_REJECTED")||n.status.equals("MATH_NODE_READY")));}
    public Jet hc(Jet x,Jet z){
        certificateWork++;Jet sum=null;
        for(int i=(int)Math.floor(x.v().lo());i<=Math.floor(x.v().hi());i++)for(int j=(int)Math.floor(z.v().lo());j<=Math.floor(z.v().hi());j++){
            var xx=new Jet(new Interval(Math.max(x.v().lo(),i),Math.min(x.v().hi(),i+1)),x.dx(),x.dz(),x.e()).sub(Jet.c(i));
            var zz=new Jet(new Interval(Math.max(z.v().lo(),j),Math.min(z.v().hi(),j+1)),z.dx(),z.dz(),z.e()).sub(Jet.c(j));
            var a=Jet.c(raw(i,j));var b=Jet.c(raw(i+1,j));var c=Jet.c(raw(i,j+1));var d=Jet.c(raw(i+1,j+1));
            var one=Jet.c(1);
            var v=one.sub(xx).mul(one.sub(zz)).mul(a).add(xx.mul(one.sub(zz)).mul(b)).add(one.sub(xx).mul(zz).mul(c)).add(xx.mul(zz).mul(d)).clamp(-1,1);
            // Convex bilinear range and exact edge derivatives remove artificial dependency inflation.
            double mn=Math.min(Math.min(a.v().lo(),b.v().lo()),Math.min(c.v().lo(),d.v().lo()));
            double mx=Math.max(Math.max(a.v().hi(),b.v().hi()),Math.max(c.v().hi(),d.v().hi()));
            var rx=Jet.blend(b.sub(a),d.sub(c),zz).v();var rz=Jet.blend(c.sub(a),d.sub(b),xx).v();
            var real=new Interval(Math.max(v.v().lo(),Math.clamp(mn,-1,1)),Math.min(v.v().hi(),Math.clamp(mx,-1,1)));
            var dx=rx.mul(x.dx()).add(rz.mul(z.dx()));var dz=rx.mul(x.dz()).add(rz.mul(z.dz()));
            // Clamp may remove a derivative; include zero if any of the cell crosses its bounds.
            if(mn< -1||mx>1){dx=Jet.hull(dx,Interval.point(0));dz=Jet.hull(dz,Interval.point(0));}
            v=new Jet(real,dx,dz,v.e());sum=sum==null?v:Jet.hull(sum,v);
        }
        return Jet.c(63).add(sum.scale(64)).add(LocalNoise.bound(R1.mix(seed^R1.COMMON),x.div(900),z.div(900)).scale(6));
    }
    public Jet hp(Jet x,Jet z,boolean ridge){
        heightEvaluations++;var h=hc(x,z);
        if(!ridge){var g=valley(x,z);return h.add(noise(R1.WIDE,x.div(900),z.div(900)).scale(4)).sub(g.scale(8)).add(noise(R1.HILLS,x.div(220),z.div(220)).scale(3).mul(Jet.c(1).sub(g)));}
        double angle=Math.PI*R1.u(seed,0,0,R1.DIRECTION),nx=StrictMath.cos(angle),nz=StrictMath.sin(angle);
        var normal=x.scale(nx).add(z.scale(nz));var tangent=x.scale(-nz).add(z.scale(nx));
        var u=normal.div(320).add(noise(R1.CURVE,x.div(600),z.div(600)).scale(.16));var r=u.scale(Math.PI).sin().div(.28).square().neg().exp();
        return h.add(r.scale(48)).sub(Jet.c(1).sub(r).scale(10)).add(noise(R1.ALONG,tangent.div(600),normal.div(900)).scale(4));
    }
    private Jet noise(long role,Jet x,Jet z){return LocalNoise.bound(R1.mix(seed^role),x,z);}
    private Jet valley(Jet x,Jet z){
        Jet nearest=null;int bx=Math.floorDiv((int)Math.floor(x.v().lo()),1024),bz=Math.floorDiv((int)Math.floor(z.v().lo()),1024);
        // Closed unit-cell envelope uses its lower-left neighborhood; omitted edges remain >2431 blocks away, hence binary64 Gaussian zero.
        for(int i=bx-3;i<=bx+3;i++)for(int j=bz-3;j<=bz+3;j++)for(int axis=0;axis<2;axis++){
            edgeTests++;
            var a=new R1.Id(i,j);var b=new R1.Id(i+(axis==0?1:0),j+(axis==1?1:0));if(!dry(a)||!dry(b))continue;
            if(axis==1){long hash=R1.mix(seed^0x454447455a000001L^R1.mix(i)^Long.rotateLeft(R1.mix(j),16)^Long.rotateLeft(R1.mix(b.i()),32)^Long.rotateLeft(R1.mix(b.j()),48));if((hash>>>11)*0x1.0p-53>=.35)continue;}
            var p=R2.center(seed,a);var q=R2.center(seed,b);var dx=Jet.c(q.x()).sub(Jet.c(p.x()));var dz=Jet.c(q.z()).sub(Jet.c(p.z()));
            var t=x.sub(Jet.c(p.x())).mul(dx).add(z.sub(Jet.c(p.z())).mul(dz)).div(dx.mul(dx).add(dz.mul(dz))).clamp(0,1);
            var ex=x.sub(Jet.c(p.x()).add(t.mul(dx)));var ez=z.sub(Jet.c(p.z()).add(t.mul(dz)));var d=ex.square().add(ez.square());
            if(nearest==null)nearest=d;
            else {double minHi=Math.min(nearest.v().hi(),d.v().hi());if(d.v().lo()>minHi)continue;if(nearest.v().lo()>minHi)nearest=d;
                else nearest=new Jet(new Interval(Math.min(nearest.v().lo(),d.v().lo()),minHi),Jet.hull(nearest.dx(),d.dx()),Jet.hull(nearest.dz(),d.dz()),Math.max(nearest.e(),d.e()));}
        }
        if(nearest==null)return Jet.c(0);
        var g=nearest.div(4096).neg().exp();
        // Actual scalar evaluates (sqrt(d2)/64)^2 rather than d2/4096. Both have
        // the same real model; 16 ulps of d2 cover sqrt rounding, power-of-two
        // scaling, re-square and underflow. exp on a nonpositive input is 1-Lipschitz.
        double extra=Math.nextUp(16*Math.ulp(Math.max(1,nearest.v().expand(nearest.e()).maxAbs()))/4096);
        return new Jet(g.v(),g.dx(),g.dz(),Interval.upAdd(g.e(),extra));
    }
    private void work(){
        Node n=detailed.get(nodeIndex);var d=R2.Dir.values()[dir];Port p=n.ports[phase][dir];
        if(p==null){p=new Port();n.ports[phase][dir]=p;double ex=n.center.x()+152*d.x,ez=n.center.z()+152*d.z;
            p.endpoint=hp(Jet.x(ex,ex),Jet.z(ez,ez),phase==1);var v=p.endpoint.v().expand(p.endpoint.e());p.maxError=p.endpoint.e();
            var quantized=R2.quantize(p.endpoint);
            if(!quantized.reason().equals("QUANTIZED"))p.reason=quantized.reason().equals("POINT_BIN_UNCERTAIN")?"ENDPOINT_BIN_UNCERTAIN":quantized.reason();
            else{p.end=quantized.height();p.delta=p.end-n.h;p.reason=Math.abs(p.delta)>8?"RAMP_HEIGHT_DELTA":Math.min(n.h,p.end-.5)<65?"WET_PORT_MODEL":"CERTIFYING";}
            double ax=n.center.x()+152*d.x-10*(-d.z),az=n.center.z()+152*d.z-10*d.x;
            double bx=n.center.x()+184*d.x+10*(-d.z),bz=n.center.z()+184*d.z+10*d.x;
            x0=(int)Math.floor(Math.min(ax,bx));x1=(int)Math.ceil(Math.max(ax,bx));z0=(int)Math.floor(Math.min(az,bz));z1=(int)Math.ceil(Math.max(az,bz));cell=0;
        }
        if(p.reason.equals("CERTIFYING")){
            int nx=x1-x0,nz=z1-z0;int x=x0+cell/nz,z=z0+cell%nz;
            var v=hp(Jet.x(x,x+1),Jet.z(z,z+1),phase==1);p.cells++;cell++;
            p.maxRange=Math.max(p.maxRange,v.v().sub(Interval.point(p.end)).maxAbs());p.maxGradient=Math.max(p.maxGradient,v.gradient());p.maxError=Math.max(p.maxError,v.e());
            String decision=R2.terminal(p.maxRange,p.maxGradient,p.maxError);
            if(!decision.equals("PORT_MODEL_READY_NOT_FULL_SAFE"))p.reason=decision;
            else if(cell==nx*nz)p.reason=decision;
        }
        if(!p.reason.equals("CERTIFYING")){dir++;if(dir==4){dir=0;phase++;}if(phase==2){phase=0;
            var s=EnumSet.noneOf(R2.Dir.class);var r=EnumSet.noneOf(R2.Dir.class);for(var d2:R2.Dir.values()){if(n.ports[0][d2.ordinal()].accepted())s.add(d2);if(n.ports[1][d2.ordinal()].accepted())r.add(d2);}
            n.pair=R2.pair(s,r);n.status=n.pair.isEmpty()?"PORT_GEOMETRY_REJECTED":"MATH_NODE_READY";nodeIndex++;}}
    }
    private void finish(){
        retainedBytes=R2Memory.measure(this,()->guard(activeStart,activeCalls));
        if(retainedBytes>33554432)throw new IllegalStateException("BUDGET_EXCEEDED:RETAINED_CONTEXT");
        state=State.PREPARATION_COMPLETED;
    }
    public void chargeInitialization(long cpu,long allocated,long wall){
        completedCpu=cpuNanos=cpu;completedAllocation=ownedAllocation=allocated;
        initializationExtraWall=Math.max(0,wall-(clock.getAsLong()-wallStart));wallNanos=wall;
    }
    public void abort(String reason){state=State.FAILED;failure=reason;for(Node n:nodes)if(n.status.equals("MATH_NODE_READY"))n.status="INVALIDATED_PROFILE_FAILURE";}
    public List<Node> ready(){if(state!=State.PREPARATION_COMPLETED)throw new IllegalStateException("CONTEXT_NOT_PREPARED:"+state);return nodes.stream().filter(n->n.status.equals("MATH_NODE_READY")).toList();}
    public void requireReady(int x,int z){ready();if(Arrays.stream(R2.WINDOWS).noneMatch(w->Math.abs((long)x-w[0])<=1536&&Math.abs((long)z-w[1])<=1536))throw new IllegalStateException("ROI_MISS");}
    /** Stored raw reference plus existing kernel, explicit frozen scalar order. */
    public double hcValue(double x,double z){if(!Double.isFinite(x)||!Double.isFinite(z))throw new IllegalArgumentException("NONFINITE");int i=(int)Math.floor(x),j=(int)Math.floor(z);double tx=x-i,tz=z-j;
        double v=(((1-tx)*(1-tz)*raw(i,j)+tx*(1-tz)*raw(i+1,j))+(1-tx)*tz*raw(i,j+1))+tx*tz*raw(i+1,j+1);
        return (63+64*Math.clamp(v,-1,1))+6*NOISE.sample(R1.mix(seed^R1.COMMON),x/900,z/900);}
    public double hpValue(double x,double z,boolean ridge){
        double h=hcValue(x,z);
        if(!ridge){double g=valleyValue(x,z);return ((h+4*n(R1.WIDE,x/900,z/900))-8*g)+(3*n(R1.HILLS,x/220,z/220))*(1-g);}
        double angle=Math.PI*R1.u(seed,0,0,R1.DIRECTION),nx=StrictMath.cos(angle),nz=StrictMath.sin(angle);
        double normal=x*nx+z*nz,tangent=x*(-nz)+z*nx;
        double u=normal/320+.16*n(R1.CURVE,x/600,z/600),a=StrictMath.sin(Math.PI*u)/.28,r=StrictMath.exp(-a*a);
        return ((h+48*r)-10*(1-r))+4*n(R1.ALONG,tangent/600,normal/900);
    }
    public double ridgeProfile(double x,double z){double angle=Math.PI*R1.u(seed,0,0,R1.DIRECTION);double normal=x*StrictMath.cos(angle)+z*StrictMath.sin(angle);double a=StrictMath.sin(Math.PI*(normal/320+.16*n(R1.CURVE,x/600,z/600)))/.28;return StrictMath.exp(-a*a);}
    private double n(long role,double x,double z){return NOISE.sample(R1.mix(seed^role),x,z);}
    private double valleyValue(double x,double z){
        double best=Double.POSITIVE_INFINITY;int bx=(int)Math.floor(x/1024),bz=(int)Math.floor(z/1024);
        for(int i=bx-3;i<=bx+3;i++)for(int j=bz-3;j<=bz+3;j++)for(int axis=0;axis<2;axis++){
            var a=new R1.Id(i,j);var b=new R1.Id(i+(axis==0?1:0),j+(axis==1?1:0));if(!dry(a)||!dry(b))continue;
            if(axis==1){long hash=R1.mix(seed^0x454447455a000001L^R1.mix(i)^Long.rotateLeft(R1.mix(j),16)^Long.rotateLeft(R1.mix(b.i()),32)^Long.rotateLeft(R1.mix(b.j()),48));if((hash>>>11)*0x1.0p-53>=.35)continue;}
            var p=R2.center(seed,a);var q=R2.center(seed,b);double dx=q.x()-p.x(),dz=q.z()-p.z();double t=Math.clamp(((x-p.x())*dx+(z-p.z())*dz)/(dx*dx+dz*dz),0,1);
            double ex=x-(p.x()+t*dx),ez=z-(p.z()+t*dz);best=Math.min(best,ex*ex+ez*ez);
        }
        if(best>=2048.0*2048)return 0;double d=StrictMath.sqrt(best)/64;return StrictMath.exp(-d*d);
    }
    public record Geometry(double height,double density,double protection,double b,double qsum){}
    public Geometry geometry(boolean ridge,int x,int y,int z,double coreDensity){return geometry(ridge,x,y,z,coreDensity,0);}
    public Geometry geometry(boolean ridge,int x,int y,int z,double coreDensity,double detail){
        requireReady(x,z);double hp=hpValue(x,z,ridge);
        for(Node n:ready()){
            double px=x-n.center.x(),pz=z-n.center.z(),r=StrictMath.hypot(px,pz);if(r>192)continue;
            double b=1-R2.q((r-24)/152),height=(1-b)*hp+b*n.h,density=(1-b)*coreDensity+b*((n.h-y)/16);
            double sum=0,weightedHeight=0,weightedDensity=0,control=0;
            for(var d:n.pair){double q=R2.mask(px,pz,d);if(q==0)continue;double u=px*d.x+pz*d.z,T=R2.q((u-152)/24);var p=n.ports[ridge?1:0][d.ordinal()];
                double target=u<=152?n.h+p.delta*R2.q((u-24)/128):p.end+T*(hp-p.end);
                double port=(target-y)/16+T*detail;
                sum+=q;weightedHeight+=q*target;weightedDensity+=q*port;control+=q*(1-T);
            }
            height=(1-sum)*height+weightedHeight;density=(1-sum)*density+weightedDensity;
            double w=1-(1-b)*(1-control),py=(1-R1.s(16,32,height-y))*(1-R1.s(4,20,y-height));
            return new Geometry(height,density,w*py,b,sum);
        }
        return new Geometry(hp,coreDensity,0,0,0);
    }
    @Override public void close(){state=State.CLOSED;Arrays.fill(rawUsed,false);rawEntries=0;nodes.clear();detailed=List.of();}
}
