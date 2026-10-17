package dev.fangbm.worldphase.v2test;

import dev.fangbm.worldphase.verification.v2.*;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Run alone in a fresh Gradle test worker. Timing is evidence/gating, never a machine-speed assertion. */
@EnabledIfSystemProperty(named="worldphase.v2.measure",matches="true")
class V2PreparationTest {
    @Test void observeColdPreparationAndStopAtFirstActualBudgetFailure() throws Exception {
        long outerStart=System.nanoTime();var bean=ManagementFactory.getThreadMXBean();long outerCpu=bean.getCurrentThreadCpuTime();
        var fixture=new NativeV2Fixture();
        JsonObject report=new JsonObject();report.addProperty("manifest",R1.DIGEST);
        report.addProperty("jvm",System.getProperty("java.runtime.version"));report.addProperty("minecraft","26.1");report.addProperty("neoforge","26.1.0.19-beta");
        report.addProperty("mode","fresh test-worker JVM; cold node/column caches; registry bootstrap measured separately; OS disk cache uncontrolled");
        report.addProperty("registryBootstrapWallNanos",System.nanoTime()-outerStart);report.addProperty("registryBootstrapCpuNanos",bean.getCurrentThreadCpuTime()-outerCpu);
        report.addProperty("worldsCreated",0);report.addProperty("fullChunkRequests",0);report.addProperty("holdoutsSampled",false);
        JsonArray seeds=new JsonArray();boolean stopped=false;
        for(long seed:new long[]{123456789L,8675309L,20261011L}) {
            JsonObject row=new JsonObject();row.addProperty("seed",seed);seeds.add(row);
            if(stopped){row.addProperty("status","NOT_RUN_AFTER_BUDGET_FAILURE");continue;}
            long heapBefore=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long init=System.nanoTime(),initCpu=bean.getCurrentThreadCpuTime();
            var state=fixture.state(fixture.vanilla,seed);
            var context=new V2Context(seed,R1.rawReference(state.router().continents()));
            row.addProperty("randomStateInitWallNanos",System.nanoTime()-init);row.addProperty("randomStateInitCpuNanos",bean.getCurrentThreadCpuTime()-initCpu);
            long wall=System.nanoTime(),cpu=bean.getCurrentThreadCpuTime();
            try {
                while(context.state()!=V2Context.State.READY)context.step();
                row.addProperty("status","PREPARATION_COMPLETED");
            } catch(RuntimeException failure) {
                row.addProperty("status","BLOCKED");row.addProperty("failure",failure.toString());stopped=true;
            }
            row.addProperty("wallNanos",System.nanoTime()-wall);row.addProperty("cpuNanos",bean.getCurrentThreadCpuTime()-cpu);
            row.addProperty("targetNodes",context.targetCount());row.addProperty("completedNodes",context.certificates().size());row.addProperty("rawCalls",context.calls());
            row.addProperty("modelCandidates",context.certificates().values().stream().filter(HcCertificate::modelCandidate).count());
            row.addProperty("enabledNodes",context.certificates().values().stream().filter(HcCertificate::eligible).count());
            JsonObject reasons=new JsonObject();context.certificates().values().stream().map(HcCertificate::reason).distinct().sorted().forEach(reason->
                    reasons.addProperty(reason,context.certificates().values().stream().filter(c->c.reason().equals(reason)).count()));row.add("rejections",reasons);
            JsonArray ticks=new JsonArray();for(var t:context.ticks()) {
                assertTrue(t.calls()<=512);JsonObject tick=new JsonObject();tick.addProperty("calls",t.calls());tick.addProperty("wallNanos",t.wallNanos());tick.addProperty("cpuNanos",t.cpuNanos());ticks.add(tick);
            }row.add("ticks",ticks);
            row.addProperty("heapBefore",heapBefore);row.addProperty("heapAfter",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            assertTrue(context.calls()<=3_484_800);assertTrue(context.targetCount()<=800);assertTrue(context.certificates().size()<=1024);
            context.close();assertTrue(context.certificates().isEmpty());assertEquals(0,context.cacheStats()[3]);row.addProperty("cacheClearedOnClose",true);
        }
        report.add("seeds",seeds);report.addProperty("preparationGate",stopped?"BLOCKED_BUDGET_OR_REFERENCE_FAILURE":"COMPLETED_WITHIN_OBSERVED_TICK_BUDGET");
        report.addProperty("numericGate","BLOCKED_R1_ROUNDED_LIPSCHITZ_CONTRACT");
        report.addProperty("v2TerrainGenerationAuthorized",false);
        Path dir=Path.of(System.getProperty("worldphase.projectRoot"),"build/reports/v2-preflight");Files.createDirectories(dir);
        String json=new GsonBuilder().setPrettyPrinting().create().toJson(report);
        Path file=dir.resolve("preparation-"+UUID.randomUUID()+".json");Files.writeString(file,json);
        Files.writeString(dir.resolve("preparation-latest.json"),json);
        System.out.println("V2 preparation evidence: "+file+"; preparationGate="+report.get("preparationGate"));
    }
}
