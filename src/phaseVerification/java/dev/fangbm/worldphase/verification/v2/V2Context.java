package dev.fangbm.worldphase.verification.v2;

import dev.fangbm.worldphase.worldgen.noise.GradientNoise;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.world.level.levelgen.DensityFunction;

/** One immutable seed/reference identity, explicitly prepared on its owner thread; never a Level. */
public final class V2Context implements AutoCloseable {
    public enum State { NEW, PREPARING, READY, FAILED, CLOSED }
    public record Tick(int calls,long wallNanos,long cpuNanos) {}
    public record Column(double hc,double height,double ridgeProfile) {}
    private record ColumnKey(int x,int z,boolean ridge) {}
    private static final GradientNoise GRADIENT=new GradientNoise(1);
    public final long seed;
    private final HcCertificate.Reference reference;
    private final Thread owner=Thread.currentThread();
    private final LongSupplier clock;
    private final List<R1.Id> targets;
    private final Map<R1.Id,HcCertificate> certificates=new TreeMap<>();
    private final List<Tick> ticks=new ArrayList<>();
    private final LinkedHashMap<ColumnKey,Column> columns=new LinkedHashMap<>(32,.75f,true);
    private volatile State state=State.NEW;
    private String failure="";
    private HcCertificate.Builder current;
    private int target,calls;
    private long cacheHits,cacheMisses,evictions;
    public V2Context(long seed,DensityFunction raw) {
        this(seed,(x,z)->raw.compute(new DensityFunction.SinglePointContext(x,0,z)),System::nanoTime);
    }
    public V2Context(long seed,HcCertificate.Reference reference,LongSupplier clock) {
        this.seed=seed;this.reference=reference;this.clock=clock;
        TreeSet<R1.Id> ids=new TreeSet<>();
        for(int[] p:new int[][]{{-384,896},{1344,1200}}) {
            int minX=Math.floorDiv(p[0]-1536,512),maxX=Math.floorDiv(p[0]+1536,512);
            int minZ=Math.floorDiv(p[1]-1536,512),maxZ=Math.floorDiv(p[1]+1536,512);
            for(int i=minX-6;i<=maxX+7;i++)for(int j=minZ-6;j<=maxZ+7;j++)ids.add(new R1.Id(i,j));
        }
        if(ids.size()>800)throw new IllegalStateException("Certificate count budget");
        targets=List.copyOf(ids);
    }
    public State state(){return state;}
    public String failure(){return failure;}
    public int calls(){return calls;}
    public int targetCount(){return targets.size();}
    public List<Tick> ticks(){return List.copyOf(ticks);}
    public Map<R1.Id,HcCertificate> certificates(){return Collections.unmodifiableMap(certificates);}
    public synchronized long[] cacheStats(){return new long[]{cacheHits,cacheMisses,evictions,columns.size()};}
    public void step() {
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Preparation must use owner thread");
        if(state==State.FAILED||state==State.CLOSED||state==State.READY)throw new IllegalStateException("Preparation state: "+state);
        state=State.PREPARING;
        long start=clock.getAsLong(),cpu=cpu();int batch=0;
        try {
            while(target<targets.size()&&batch<512) {
                if(clock.getAsLong()-start>25_000_000L)throw new IllegalStateException("TICK_BUDGET_EXCEEDED");
                if(current==null)current=new HcCertificate.Builder(R1.center(seed,targets.get(target)));
                if(++calls>3_484_800)throw new IllegalStateException("TOTAL_REFERENCE_BUDGET_EXCEEDED");
                batch++;current.next(reference);
                if(current.done()) {
                    certificates.put(targets.get(target++),current.finish());current=null;
                    if(certificates.size()>1024)throw new IllegalStateException("CERTIFICATE_CACHE_BUDGET");
                }
                if(clock.getAsLong()-start>25_000_000L)throw new IllegalStateException("TICK_BUDGET_EXCEEDED");
            }
            if(target==targets.size())state=State.READY;
        }catch(RuntimeException e){state=State.FAILED;failure=e.getMessage();throw e;}
        finally {ticks.add(new Tick(batch,clock.getAsLong()-start,cpu()-cpu));}
    }
    private static long cpu(){return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();}
    public void requireReady(int x,int z) {
        if(state!=State.READY)throw new IllegalStateException("CONTEXT_NOT_PREPARED: "+state);
        boolean in=false;
        for(int[] p:new int[][]{{-384,896},{1344,1200}})in|=Math.abs((long)x-p[0])<=1536&&Math.abs((long)z-p[1])<=1536;
        if(!in)throw new IllegalStateException("CONTEXT_NOT_PREPARED: outside frozen ROI");
    }
    public double hc(double x,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>29_999_984||Math.abs(z)>29_999_984)
            throw new IllegalArgumentException("Unsupported reference coordinate");
        int bx=(int)StrictMath.floor(x),bz=(int)StrictMath.floor(z);double tx=x-bx,tz=z-bz;
        double a=finite(reference.at(bx,bz)),b=finite(reference.at(bx+1,bz)),c=finite(reference.at(bx,bz+1)),d=finite(reference.at(bx+1,bz+1));
        double v=finite((((1-tx)*(1-tz)*a+tx*(1-tz)*b)+(1-tx)*tz*c)+tx*tz*d);
        return finite((63+64*Math.clamp(v,-1,1))+6*n(R1.COMMON,x/900,z/900));
    }
    public double n(long role,double x,double z){return GRADIENT.sample(R1.mix(seed^role),x,z);}
    public synchronized Column column(int x,int z,boolean isRidge) {
        requireReady(x,z);ColumnKey key=new ColumnKey(x,z,isRidge);Column old=columns.get(key);
        if(old!=null){cacheHits++;return old;}cacheMisses++;
        double h=hc(x,z);
        if(!isRidge) {
            double g=valley(x,z);
            double smooth=((h+4*n(R1.WIDE,x/900.0,z/900.0))-8*g)+(3*n(R1.HILLS,x/220.0,z/220.0))*(1-g);
            return cache(key,new Column(h,finite(smooth),0));
        }
        double angle=Math.PI*R1.u(seed,0,0,R1.DIRECTION),nx=StrictMath.cos(angle),nz=StrictMath.sin(angle);
        double normal=x*nx+z*nz,tangent=x*(-nz)+z*nx;
        double u=normal/320+.16*n(R1.CURVE,x/600.0,z/600.0),a=StrictMath.sin(Math.PI*u)/.28,r=StrictMath.exp(-a*a);
        double ridge=((h+48*r)-10*(1-r))+4*n(R1.ALONG,tangent/600,normal/900);
        return cache(key,new Column(finite(h),finite(ridge),finite(r)));
    }
    private Column cache(ColumnKey key,Column value) {
        if(columns.size()>=16384){columns.remove(columns.keySet().iterator().next());evictions++;}
        columns.put(key,value);return value;
    }
    private boolean eligible(R1.Id id) {
        HcCertificate c=certificates.get(id);
        if(c==null)throw new IllegalStateException("CONTEXT_NOT_PREPARED: missing node "+id);
        return c.eligible();
    }
    public double weight(int x,int z) {
        requireReady(x,z);double d=32;int bx=Math.floorDiv(x,512),bz=Math.floorDiv(z,512);
        for(int i=bx-1;i<=bx+1;i++)for(int j=bz-1;j<=bz+1;j++) {
            R1.Id id=new R1.Id(i,j);if(!eligible(id))continue;R1.Point p=R1.center(seed,id);
            d=Math.min(d,StrictMath.sqrt((x-p.x())*(x-p.x())+(z-p.z())*(z-p.z())));
        }
        return R1.s(24,32,d);
    }
    private double valley(int x,int z) {
        double best=Double.POSITIVE_INFINITY;int bx=Math.floorDiv(x,512),bz=Math.floorDiv(z,512);
        for(int i=bx-6;i<=bx+6;i++)for(int j=bz-6;j<=bz+6;j++)for(int axis=0;axis<2;axis++) {
            R1.Id a=new R1.Id(i,j),b=new R1.Id(i+(axis==0?1:0),j+(axis==1?1:0));
            if(!eligible(a)||!eligible(b))continue;
            if(axis==1) {
                long hash=R1.mix(seed^0x454447455a000001L^R1.mix(i)^Long.rotateLeft(R1.mix(j),16)^Long.rotateLeft(R1.mix(b.i()),32)^Long.rotateLeft(R1.mix(b.j()),48));
                if((hash>>>11)*0x1.0p-53>=.35)continue;
            }
            R1.Point p=R1.center(seed,a),q=R1.center(seed,b);double dx=q.x()-p.x(),dz=q.z()-p.z();
            double t=Math.clamp(((x-p.x())*dx+(z-p.z())*dz)/(dx*dx+dz*dz),0,1);
            double ex=x-(p.x()+t*dx),ez=z-(p.z()+t*dz);best=Math.min(best,ex*ex+ez*ez);
        }
        if(best>=2048.0*2048)return 0;double d=StrictMath.sqrt(best)/64;return StrictMath.exp(-d*d);
    }
    public static double finite(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite V2 output");return v;}
    public synchronized void close(){state=State.CLOSED;columns.clear();certificates.clear();current=null;}
}
