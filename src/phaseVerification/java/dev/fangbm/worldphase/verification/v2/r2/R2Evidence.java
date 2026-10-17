package dev.fangbm.worldphase.verification.v2.r2;
import com.google.gson.*;
import dev.fangbm.worldphase.verification.v2.*;
import java.util.*;
/** Evidence serialization is outside preparation, never used to rescue a failed profile. */
public final class R2Evidence {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private R2Evidence(){}
    public static JsonObject report(R2Context c,String mode,long initWall,long initCpu,long initAllocation){
        JsonObject r=new JsonObject();r.addProperty("schema",1);r.addProperty("manifest",R2.DIGEST);r.addProperty("sourceCommit",System.getProperty("worldphase.sourceCommit","UNRECORDED"));
        r.addProperty("seed",c.seed);r.addProperty("mode",mode);r.addProperty("minecraft","26.1");r.addProperty("neoforge","26.1.0.19-beta");r.addProperty("java",System.getProperty("java.runtime.version"));
        r.addProperty("executionStatus",c.state.toString());r.addProperty("failure",c.failure);
        r.addProperty("initializationWallNanos",initWall);r.addProperty("initializationCpuNanos",initCpu);r.addProperty("initializationAllocatedBytes",initAllocation);
        r.addProperty("activePreparationCpuNanos",c.cpuNanos);r.addProperty("scheduledWallNanos",c.wallNanos);r.addProperty("ownedAllocationBytes",c.ownedAllocation);
        r.addProperty("tickCount",c.ticks.size()+1);r.addProperty("tickKind",mode.startsWith("dedicated")?"REAL_SERVER_POST_EVENTS":"WORKER_SLICES_NOT_SERVER_TICKS");r.addProperty("maxMeasuredStepIncludingRecordingNanos",c.maxMeasuredStepWall);r.addProperty("maxTickWallNanos",Math.max(Math.max(initWall,c.maxMeasuredStepWall),c.ticks.stream().mapToLong(R2Context.Tick::wallNanos).max().orElse(0)));r.add("ticks",GSON.toJsonTree(c.ticks));
        r.addProperty("rawCalls",c.rawCalls);r.addProperty("cacheHits",c.cacheHits);r.addProperty("cacheMisses",c.cacheMisses);r.addProperty("rawCacheEntries",c.rawEntries);
        r.addProperty("heightEvaluations",c.heightEvaluations);r.addProperty("intervalDerivativeWork",c.certificateWork);r.addProperty("smoothValleyEdgeTests",c.edgeTests);r.addProperty("intervalJetConstructions",c.jetConstructions);
        long diag=System.nanoTime();try{r.addProperty("retainedContextBytes",R2Memory.measure(c,()->{}));r.addProperty("retainedMeasurement","Instrumentation reachable owned graph; shared immutable metadata overcounted; borrowed native reference excluded");}catch(RuntimeException e){r.addProperty("retainedMeasurement","UNPROVEN: "+e);}
        r.addProperty("postPreparationMemoryDiagnosticWallNanos",System.nanoTime()-diag);
        r.addProperty("anchors",c.nodes.size());r.addProperty("examinedAnchors",c.nodes.stream().filter(n->n.anchor!=null).count());
        r.addProperty("dryAnchors",c.nodes.stream().filter(n->n.anchor!=null&&n.h>=71&&Set.of("ANCHOR_DRY","DEFERRED_NOT_SHORTLISTED","PORT_GEOMETRY_REJECTED","MATH_NODE_READY","INVALIDATED_PROFILE_FAILURE").contains(n.status)).count());
        r.addProperty("shortlistedNodes",c.shortlistIds.size());r.add("shortlistIds",GSON.toJsonTree(c.shortlistIds));
        boolean complete=c.state==R2Context.State.PREPARATION_COMPLETED;
        long ready=complete?c.nodes.stream().filter(n->n.status.equals("MATH_NODE_READY")).count():0;r.addProperty("publishedMathNodes",ready);
        JsonArray windows=new JsonArray();boolean yield=complete;
        for(int[] w:R2.WINDOWS){JsonObject row=new JsonObject();row.addProperty("x",w[0]);row.addProperty("z",w[1]);long count=complete?c.nodes.stream().filter(n->n.status.equals("MATH_NODE_READY")&&R2.fits(n.center,w)).count():0;
            row.addProperty("mathNodes",count);row.addProperty("gate",!complete?"FAIL_INCOMPLETE_PREPARATION":count>=2?"PASS":"FAIL_INSUFFICIENT_NODE_YIELD");yield&=count>=2;windows.add(row);}
        r.add("windows",windows);r.addProperty("nodeYieldGate",yield?"PASS":"FAIL");
        Map<String,Integer> reasons=new TreeMap<>();int[] accepted=new int[2];
        for(var n:c.nodes)for(int phase=0;phase<2;phase++)for(var port:n.ports[phase])if(port!=null){reasons.merge(port.reason,1,Integer::sum);if(port.accepted())accepted[phase]++;}
        r.add("portOutcomes",GSON.toJsonTree(reasons));r.addProperty("smoothPassingPorts",accepted[0]);r.addProperty("ridgePassingPorts",accepted[1]);r.add("nodes",GSON.toJsonTree(c.nodes));
        r.add("anchorGraph",anchorGraph(c));
        JsonObject endpoint=new JsonObject();endpoint.addProperty("meaning","per-node core-to-selected-terminal mathematical edges only; no inter-node walking edges proved");endpoint.addProperty("components",ready);endpoint.addProperty("publishedCoreCount",ready);endpoint.addProperty("interNodeVerifiedWalkingEdges",0);r.add("endpointGraph",endpoint);
        r.addProperty("nativeNumericValidated",false);r.addProperty("fullPairValidated",false);r.addProperty("shiftPositionFeasible",false);r.addProperty("routeCompleted",0);r.addProperty("worldGenerationAuthorized",false);
        r.addProperty("v2WorldsCreated",0);r.addProperty("preflightFullChunkRequests",0);r.addProperty("holdoutsSampled",false);
        r.addProperty("fullDensityProgramErrorCertificate","UNPROVEN: complete radial/mask/detail/native interpolation error certificate not published; scalar terminal enclosure is separate");
        r.addProperty("recommendation","BLOCKED");return r;
    }
    private static JsonObject anchorGraph(R2Context c){
        Set<R1.Id> ids=new TreeSet<>();for(var n:c.nodes)if(Set.of("ANCHOR_DRY","DEFERRED_NOT_SHORTLISTED","PORT_GEOMETRY_REJECTED","MATH_NODE_READY","INVALIDATED_PROFILE_FAILURE").contains(n.status))ids.add(n.id);
        Map<R1.Id,Set<R1.Id>> adjacency=new TreeMap<>();ids.forEach(id->adjacency.put(id,new TreeSet<>()));JsonArray edges=new JsonArray();
        for(var a:ids)for(int axis=0;axis<2;axis++){var b=new R1.Id(a.i()+(axis==0?1:0),a.j()+(axis==1?1:0));if(!ids.contains(b))continue;
            if(axis==1){long hash=R1.mix(c.seed^0x454447455a000001L^R1.mix(a.i())^Long.rotateLeft(R1.mix(a.j()),16)^Long.rotateLeft(R1.mix(b.i()),32)^Long.rotateLeft(R1.mix(b.j()),48));if((hash>>>11)*0x1.0p-53>=.35)continue;}
            adjacency.get(a).add(b);adjacency.get(b).add(a);edges.add(GSON.toJsonTree(List.of(a,b)));}
        Set<R1.Id> remaining=new TreeSet<>(ids);JsonArray components=new JsonArray();
        while(!remaining.isEmpty()){TreeSet<R1.Id> component=new TreeSet<>();ArrayDeque<R1.Id> pending=new ArrayDeque<>();pending.add(remaining.iterator().next());
            while(!pending.isEmpty()){var id=pending.removeFirst();if(!remaining.remove(id))continue;component.add(id);pending.addAll(adjacency.get(id));}components.add(GSON.toJsonTree(component));}
        JsonObject graph=new JsonObject();graph.addProperty("meaning","ANCHOR_DRY geometric valley graph, not safe walking routes");graph.addProperty("completeAnchorSet",c.nodes.stream().allMatch(n->n.anchor!=null));graph.add("nodes",GSON.toJsonTree(ids));graph.add("edges",edges);graph.add("components",components);return graph;
    }
    public static void write(java.nio.file.Path file,JsonObject report)throws Exception{java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(file,GSON.toJson(report)+"\n");}
}
