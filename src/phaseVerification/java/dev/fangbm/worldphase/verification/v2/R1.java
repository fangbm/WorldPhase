package dev.fangbm.worldphase.verification.v2;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.world.level.levelgen.*;

/** Frozen r1 constants; manifest is immutable, approval is external to that historical JSON. */
public final class R1 {
    public static final String DIGEST="521fc8d6e854570ae79f851129109d5948943f9ec372aee50643939d840ad2eb";
    public static final String PROFILE="worldphase:prototype_v2/shared_r1";
    public static final long COMMON=0x434f4d4d4f4e0001L, WIDE=0x534d4f4f54480001L, HILLS=0x534d4f4f54480002L;
    public static final long CURVE=0x5249444745000001L, ALONG=0x5249444745000002L;
    public static final long NODE_X=0x4e4f444558000001L,NODE_Z=0x4e4f44455a000001L,DIRECTION=0x5249444745444952L;
    private R1() {}
    public static void verifyManifest(Path root) throws Exception {
        String text=Files.readString(root.resolve("docs/verification/spatial-retune/v2/EXPERIMENT_MANIFEST.json")).replace("\r\n","\n");
        String got=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        if (!DIGEST.equals(got)) throw new IllegalStateException("Frozen r1 manifest digest mismatch: "+got);
        var locks=com.google.gson.JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("locks").getAsJsonObject("tracked_text_sha256");
        for(var entry:locks.entrySet()) {
            if(!entry.getKey().startsWith("src/main/java/dev/fangbm/worldphase/worldgen/noise/"))continue;
            byte[] bytes=Files.readString(root.resolve(entry.getKey())).replace("\r\n","\n").getBytes(StandardCharsets.UTF_8);
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!actual.equals(entry.getValue().getAsString()))throw new IllegalStateException("Existing kernel proof input changed: "+entry.getKey());
        }
    }
    public static long mix(long v) { v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31); }
    public static double u(long seed,long i,long j,long tag) {
        long h=mix(seed^mix(i^0x9e3779b97f4a7c15L)^Long.rotateLeft(mix(j^0xd1b54a32d192ed03L),32)^tag);
        return (h>>>11)*0x1.0p-53;
    }
    public record Id(int i,int j) implements Comparable<Id> {
        public int compareTo(Id b) { int c=Integer.compare(i,b.i);return c!=0?c:Integer.compare(j,b.j); }
    }
    public record Point(double x,double z) {}
    public static Point center(long seed,Id id) {
        return new Point(512*(id.i+.5)+64*(2*u(seed,id.i,id.j,NODE_X)-1),512*(id.j+.5)+64*(2*u(seed,id.i,id.j,NODE_Z)-1));
    }
    public static double s(double a,double b,double v) { double t=Math.clamp((v-a)/(b-a),0,1);return t*t*(3-2*t); }
    public static DensityFunction rawReference(DensityFunction f) {
        return f.mapAll(new DensityFunction.Visitor() {
            public DensityFunction apply(DensityFunction q) {
                if(q instanceof DensityFunctions.HolderHolder h)return h.function().value();
                if(q instanceof DensityFunctions.MarkerOrMarked m)return m.wrapped();
                return q;
            }
        });
    }
}
