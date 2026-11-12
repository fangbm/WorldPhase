package dev.fangbm.worldphase.verification.v2.r2;

import dev.fangbm.worldphase.verification.v2.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Frozen B2 identities; never an authorization flag or a runtime dimension. */
public final class R2 {
    public static final String DIGEST="82a3e9676d7de3e223299feff88c7a9ea67e8e4e1c25bb1f3aa134deb59ec9bf";
    public static final String PROFILE="worldphase:prototype_v2/shared_r2";
    public static final long[] SEEDS={123456789L,8675309L,20261011L};
    public static final int[][] WINDOWS={{-384,896},{1344,1200}};
    public enum Dir { E(1,0),W(-1,0),N(0,-1),S(0,1); public final int x,z;Dir(int x,int z){this.x=x;this.z=z;} }
    private R2() {}
    public static void verify(Path root)throws Exception {
        R1.verifyManifest(root);
        byte[] bytes=Files.readString(root.resolve("docs/verification/spatial-retune/v2/r2/EXPERIMENT_MANIFEST.json")).replace("\r\n","\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(DIGEST))throw new IllegalStateException("R2_FREEZE_MISMATCH");
    }
    public static void requireSeed(long seed){if(Arrays.stream(SEEDS).noneMatch(s->s==seed))throw new IllegalArgumentException("SEED_NOT_IN_DEVELOPMENT_SET");}
    public static R1.Point center(long seed,R1.Id id) {
        requireSeed(seed);
        return new R1.Point(1024*(id.i()+.5)+128*(2*R1.u(seed,id.i(),id.j(),0x4e4f444558000002L)-1),
                1024*(id.j()+.5)+128*(2*R1.u(seed,id.i(),id.j(),0x4e4f44455a000002L)-1));
    }
    public static List<R1.Id> targets() {
        TreeSet<R1.Id> ids=new TreeSet<>();
        for(int[] w:WINDOWS)for(int i=Math.floorDiv(w[0]-1536,1024)-3;i<=Math.floorDiv(w[0]+1536,1024)+4;i++)
            for(int j=Math.floorDiv(w[1]-1536,1024)-3;j<=Math.floorDiv(w[1]+1536,1024)+4;j++)ids.add(new R1.Id(i,j));
        if(ids.size()!=132)throw new IllegalStateException("TARGET_COUNT");return List.copyOf(ids);
    }
    public static List<R1.Id> shortlist(long seed,Collection<R1.Id> dry) {
        TreeSet<R1.Id> result=new TreeSet<>();
        for(int[] w:WINDOWS)result.addAll(dry.stream().filter(id->fits(center(seed,id),w)).sorted(Comparator
                .comparing((R1.Id id)->distance(center(seed,id),w)).thenComparing(Comparator.naturalOrder())).limit(8).toList());
        return List.copyOf(result);
    }
    public static boolean fits(R1.Point c,int[] w){return Math.abs(c.x()-w[0])<=1344&&Math.abs(c.z()-w[1])<=1344;}
    private static BigDecimal distance(R1.Point p,int[] w){var x=new BigDecimal(p.x()).subtract(BigDecimal.valueOf(w[0]));var z=new BigDecimal(p.z()).subtract(BigDecimal.valueOf(w[1]));return x.multiply(x).add(z.multiply(z));}
    public static List<Dir> pair(Set<Dir> smooth,Set<Dir> ridge) {
        for(var pair:List.of(List.of(Dir.E,Dir.W),List.of(Dir.N,Dir.S)))if(smooth.containsAll(pair)&&ridge.containsAll(pair))return pair;
        return List.of();
    }
    public record Quantized(double height,String reason){}
    public static Quantized quantize(Jet value){
        if(value==null||value.e()>0x1.0p-24)return new Quantized(0,"FLOAT_ERROR_UNPROVEN");
        var interval=value.v().expand(value.e());
        if(Math.floor(interval.lo())!=Math.floor(interval.hi()))return new Quantized(0,"POINT_BIN_UNCERTAIN");
        return new Quantized(Math.floor(interval.lo())+.5,"QUANTIZED");
    }
    public static String terminal(double range,double gradient,double error){
        if(!Double.isFinite(range)||!Double.isFinite(gradient)||!Double.isFinite(error)||Math.min(range,Math.min(gradient,error))<0)return "NONFINITE_OR_UNKNOWN_BOUND";
        if(error>0x1.0p-24)return "FLOAT_ERROR_UNPROVEN";
        if(range>.5)return "TERMINAL_RANGE";
        if(gradient>1.0/16)return "TERMINAL_GRADIENT";
        return "PORT_MODEL_READY_NOT_FULL_SAFE";
    }
    public static double q(double t){t=Math.clamp(t,0,1);return (t*t*t)*(10+t*(-15+6*t));}
    public static double mask(double x,double z,Dir d){double u=x*d.x+z*d.z,v=-x*d.z+z*d.x;return q((u-16)/4)*(1-q((u-176)/8))*(1-q((Math.abs(v)-10)/4));}
}
