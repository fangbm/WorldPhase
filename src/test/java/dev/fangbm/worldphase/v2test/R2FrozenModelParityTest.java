package dev.fangbm.worldphase.v2test;
import dev.fangbm.worldphase.verification.v2.r2.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Emits synthetic Java geometry for comparison with the UNCHANGED frozen B2 Python oracle.
 * Not an actual terrain, node-yield, FULL or performance experiment.
 */
class R2FrozenModelParityTest {
    @Test void emitAllSelectedPairCasesWithoutWorldOrTerrainSampling()throws Exception{
        JsonObject report=new JsonObject();report.addProperty("kind","SYNTHETIC_GEOMETRY_ONLY_NOT_REAL_SEED_YIELD");report.addProperty("manifest",R2.DIGEST);JsonArray rows=new JsonArray();
        for(int scene=0;scene<3;scene++)try(var c=new R2Context(123456789L,(x,z)->.25,()->0,false)){
            c.state=R2Context.State.PREPARATION_COMPLETED;var n=c.nodes.stream().filter(v->R2.fits(v.center,R2.WINDOWS[0])).findFirst().orElseThrow();n.h=96.5;
            Set<R2.Dir> smooth=scene==0?EnumSet.of(R2.Dir.E,R2.Dir.W,R2.Dir.N):scene==1?EnumSet.of(R2.Dir.N,R2.Dir.S):EnumSet.of(R2.Dir.E,R2.Dir.W);
            Set<R2.Dir> ridge=scene==0?smooth:EnumSet.of(R2.Dir.N,R2.Dir.S);n.pair=R2.pair(smooth,ridge);n.status=n.pair.isEmpty()?"PORT_GEOMETRY_REJECTED":"MATH_NODE_READY";
            for(int p=0;p<2;p++)for(var d:R2.Dir.values()){var port=new R2Context.Port();port.end=p==0?104.5:88.5;port.delta=port.end-n.h;n.ports[p][d.ordinal()]=port;}
            for(boolean isRidge:new boolean[]{false,true})for(int[] offset:new int[][]{{0,0},{80,0},{-80,0},{0,80},{0,-80},{2,-100},{153,0},{176,0},{184,0},{192,0},{-16,16}}){
                int x=(int)Math.round(n.center.x())+offset[0],z=(int)Math.round(n.center.z())+offset[1];double hp=c.hpValue(x,z,isRidge),detail=.037,core=(hp-90)/16+detail;
                var geometry=c.geometry(isRidge,x,90,z,core,detail);assertTrue(Double.isFinite(geometry.height()));
                JsonObject row=new JsonObject();row.addProperty("scene",scene);row.addProperty("phase",isRidge?"RIDGE":"SMOOTH");row.addProperty("x",x);row.addProperty("z",z);row.addProperty("centerX",n.center.x());row.addProperty("centerZ",n.center.z());row.addProperty("h",n.h);row.addProperty("backgroundHp",hp);row.addProperty("height",geometry.height());row.addProperty("density",geometry.density());row.addProperty("protection",geometry.protection());row.addProperty("qsum",geometry.qsum());row.addProperty("detail",detail);row.addProperty("y",90);
                row.add("smoothEligible",new Gson().toJsonTree(smooth));row.add("ridgeEligible",new Gson().toJsonTree(ridge));row.add("selectedPair",new Gson().toJsonTree(n.pair));rows.add(row);
            }
        }
        report.add("rows",rows);assertEquals(66,rows.size());var path=Path.of(System.getProperty("worldphase.projectRoot"),"build/reports/r2-native-preflight/synthetic-geometry-probes.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
