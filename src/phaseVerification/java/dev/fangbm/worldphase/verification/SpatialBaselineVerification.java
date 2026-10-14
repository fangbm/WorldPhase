package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Measurement of unchanged production settings. No new terrain implementation or FULL requests. */
final class SpatialBaselineVerification {
    static final String PROTOCOL="WPH01601_LEGACY_BASELINE_V1";
    static final int[][] CENTERS={{-384,896},{1344,1200}};
    static final String[] WINDOWS={"forest","highland"},WORLDS={"smooth","ridge","cell","fracture","overworld"};
    private final MinecraftServer server;
    private final TerrainSampleService sampler;
    private final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
    private final JsonObject report=new JsonObject();
    private final Map<String,BufferedWriter> writers=new LinkedHashMap<>();
    private final Map<String,Integer> rows=new TreeMap<>();
    private final long started=System.nanoTime(),cpuStart=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
    private int ticks,completed;private double maxWorkMillis;private boolean finished;

    static void start(MinecraftServer server){
        try{new SpatialBaselineVerification(server);}catch(Exception e){
            try{var failure=new JsonObject();failure.addProperty("protocol",PROTOCOL);failure.addProperty("status","FAIL");failure.addProperty("failure",e.toString());Files.writeString(Path.of("spatial-baseline.json"),MvpWorldVerification.JSON.toJson(failure));}catch(IOException write){e.addSuppressed(write);}
            throw new IllegalStateException("Spatial baseline setup failed",e);
        }
    }
    private SpatialBaselineVerification(MinecraftServer server)throws Exception {
        if(!server.isSameThread())throw new IllegalStateException("Server thread required");
        this.server=server;sampler=new TerrainSampleService(server);
        long seed=server.overworld().getSeed();
        report.addProperty("protocol",PROTOCOL);report.addProperty("seed",seed);report.addProperty("status","RUNNING");
        report.addProperty("minecraft","26.1");report.addProperty("neoforge","26.1.0.19-beta");report.addProperty("java",System.getProperty("java.runtime.version"));
        report.addProperty("os",System.getProperty("os.name"));report.addProperty("phaseFullChunkRequests",0);
        report.addProperty("densityMeaning","wired router compute with empty Blender; not NoiseChunk-interpolated density or FULL blocks");
        report.addProperty("columnMeaning","native getBaseColumn; no structure beardifier, surface rules, carver/feature stages or player edits");
        var spawn=server.overworld().getRespawnData().pos();var spawnJson=new JsonObject();spawnJson.addProperty("x",spawn.getX());spawnJson.addProperty("y",spawn.getY());spawnJson.addProperty("z",spawn.getZ());report.add("sharedSpawn",spawnJson);
        writer("heights","world,window,grid,x,z,height");
        writer("columns","world,window,axis,x,z,states_y_minus64_to319");
        writer("density","world,window,kind,x,y,z,density");
        writer("probes","world,window,x,y,z,raw,interpolated");
        writer("attribution","world,window,x,y,z,g0,gphase,base3d,terrain,offset,factor");
        var levels=new JsonObject();report.add("rawSeeds",levels);
        for(String name:WORLDS){
            var level=name.equals("overworld")?server.overworld():server.getLevel(PhaseWorldResolver.levelKey(PhaseId.valueOf(name.toUpperCase(Locale.ROOT))));
            if(level==null || level.getSeed()!=seed)throw new IllegalStateException("Missing/mismatched Phase seed "+name);
            levels.addProperty(name,level.getSeed());
            var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();var state=level.getChunkSource().randomState();
            var heightCache=new HashMap<Long,Integer>();var attributed=attribution(level,name,generator.generatorSettings().value());
            for(int w=0;w<CENTERS.length;w++){
                final String window=WINDOWS[w];int cx=CENTERS[w][0],cz=CENTERS[w][1];
                for(String grid:new String[]{"coarse","fine"}){
                    int radius=grid.equals("coarse")?1024:128,step=grid.equals("coarse")?64:16;
                    for(int dx=-radius;dx<=radius;dx+=step)for(int dz=-radius;dz<=radius;dz+=step){
                        final int x=cx+dx,z=cz+dz;
                        jobs.add(()->write("heights",name,window,grid,x,z,height(name,level,generator,x,z,heightCache)));
                    }
                }
                for(String axis:new String[]{"x","z"})for(int d=-512;d<=512;d+=8){
                    final int x=cx+(axis.equals("x")?d:0),z=cz+(axis.equals("z")?d:0);
                    jobs.add(()->{
                        var column=generator.getBaseColumn(x,z,level,state);var blocks=new StringBuilder(384);
                        for(int y=-64;y<320;y++){
                            var block=column.getBlock(y);
                            blocks.append(!block.getFluidState().isEmpty()?(block.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)?'L':'W'):block.isAir()?'A':'S');
                        }
                        write("columns",name,window,axis,x,z,blocks);
                    });
                }
                for(String axis:new String[]{"x","z"})for(int d=-512;d<=512;d+=8)for(int iy=-64;iy<320;iy+=8){
                    final int x=cx+(axis.equals("x")?d:0),y=iy,z=cz+(axis.equals("z")?d:0);
                    jobs.add(()->write("density",name,window,axis,x,y,z,finite(state.router().finalDensity().compute(new DensityFunction.SinglePointContext(x,y,z)))));
                }
                for(int dx=-64;dx<=64;dx+=8)for(int dz=-64;dz<=64;dz+=8)for(int iy=-32;iy<=160;iy+=8){
                    final int x=cx+dx,y=iy,z=cz+dz;
                    jobs.add(()->write("density",name,window,"volume",x,y,z,finite(state.router().finalDensity().compute(new DensityFunction.SinglePointContext(x,y,z)))));
                }
                for(int dx:new int[]{1,17,33})for(int dz:new int[]{1,17,33})for(int iy:new int[]{33,65,97}){
                    final int x=cx+dx,y=iy,z=cz+dz;
                    jobs.add(()->{
                        var context=new DensityFunction.SinglePointContext(x,y,z);
                        double raw=finite(state.router().finalDensity().compute(context));
                        if(Double.doubleToLongBits(raw)!=Double.doubleToLongBits(state.router().finalDensity().compute(context)))throw new IllegalStateException("Density repeated-call drift");
                        write("probes",name,window,x,y,z,raw,finite(generator.getInterpolatedNoiseValue(state,context)));
                    });
                }
                for(int dx:new int[]{-512,0,512})for(int dz:new int[]{-512,0,512})for(int dy:new int[]{-8,0,8}){
                    final int x=cx+dx,z=cz+dz,delta=dy;
                    jobs.add(()->{
                        int y=height(name,level,generator,x,z,heightCache)+delta;var context=new DensityFunction.SinglePointContext(x,y,z);
                        double g0=attributed.barrierNoise().compute(context),gp=attributed.fluidLevelFloodednessNoise().compute(context),b3=attributed.fluidLevelSpreadNoise().compute(context),terrain=attributed.lavaNoise().compute(context);
                        if(Math.abs(terrain-(gp+b3))>1e-10)throw new IllegalStateException("Gradient-replacement algebra mismatch "+name);
                        write("attribution",name,window,x,y,z,finite(g0),finite(gp),finite(b3),finite(terrain),finite(attributed.temperature().compute(context)),finite(attributed.vegetation().compute(context)));
                    });
                }
            }
        }
        report.addProperty("scheduledOperations",jobs.size());save();
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private int height(String name,ServerLevel level,NoiseBasedChunkGenerator generator,int x,int z,Map<Long,Integer> cache){
        long key=((long)x<<32)^(z&0xffffffffL);Integer previous=cache.get(key);if(previous!=null)return previous;
        int value=name.equals("overworld")?generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1:sampler.sampleHeight(PhaseId.valueOf(name.toUpperCase(Locale.ROOT)),x,z);
        if(cache.size()>=4096)throw new IllegalStateException("Height-cache budget exceeded");cache.put(key,value);return value;
    }
    @SuppressWarnings("deprecation")
    private NoiseRouter attribution(ServerLevel level,String name,NoiseGeneratorSettings base)throws Exception{
        var registry=level.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION);
        var ops=RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
        java.util.function.Function<String,DensityFunction> get=id->new DensityFunctions.HolderHolder(registry.getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION,Identifier.parse(id))));
        DensityFunction g0=get.apply("worldphase:vanilla_terrain_gradient"),gp=g0;
        if(name.equals("smooth")){
            try(var reader=level.getServer().getResourceManager().getResourceOrThrow(Identifier.fromNamespaceAndPath("worldphase","worldgen/density_function/smooth_terrain.json")).openAsReader()){
                var json=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("argument2").get("argument1");
                gp=DensityFunction.DIRECT_CODEC.parse(ops,json).getOrThrow();
            }
        }else if(!name.equals("overworld"))gp=get.apply("worldphase:"+name+"_gradient");
        var r=base.noiseRouter();
        var bundle=new NoiseRouter(g0,gp,get.apply("minecraft:overworld/base_3d_noise"),get.apply(name.equals("overworld")?"minecraft:overworld/sloped_cheese":"worldphase:"+name+"_terrain"),
                get.apply(name.equals("overworld")?"minecraft:overworld/offset":"worldphase:"+name+"_offset"),get.apply("minecraft:overworld/factor"),r.continents(),r.erosion(),r.depth(),r.ridges(),r.preliminarySurfaceLevel(),r.finalDensity(),r.veinToggle(),r.veinRidged(),r.veinGap());
        var temporary=new NoiseGeneratorSettings(base.noiseSettings(),base.defaultBlock(),base.defaultFluid(),bundle,base.surfaceRule(),base.spawnTarget(),base.seaLevel(),base.disableMobGeneration(),base.isAquifersEnabled(),base.oreVeinsEnabled(),base.useLegacyRandomSource());
        return RandomState.create(temporary,level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed()).router();
    }
    private void tick(ServerTickEvent.Post event){
        if(event.getServer()!=server || finished)return;
        long begin=System.nanoTime();int operations=0;ticks++;
        try{
            while(!jobs.isEmpty() && operations<512 && System.nanoTime()-begin<25_000_000L){jobs.removeFirst().run();operations++;completed++;}
            maxWorkMillis=Math.max(maxWorkMillis,(System.nanoTime()-begin)/1e6);
            if(ticks%200==0){report.addProperty("completedOperations",completed);save();System.out.println("WPH016 baseline progress "+completed+" remaining="+jobs.size());}
            if(jobs.isEmpty())finish();
        }catch(Exception error){fail(error);}
    }
    private void writer(String name,String header)throws Exception{
        var writer=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(Path.of(name+".csv.gz"))),StandardCharsets.UTF_8));writers.put(name,writer);rows.put(name,0);writer.write(header);writer.newLine();
    }
    private void write(String file,Object...fields){
        try{var writer=writers.get(file);for(int i=0;i<fields.length;i++){if(i>0)writer.write(',');writer.write(fields[i].toString());}writer.newLine();rows.compute(file,(k,n)->n+1);}catch(IOException error){throw new UncheckedIOException(error);}
    }
    private static double finite(double value){if(!Double.isFinite(value))throw new IllegalStateException("Non-finite baseline density");return value;}
    private void save()throws IOException{Files.writeString(Path.of("spatial-baseline.json"),MvpWorldVerification.JSON.toJson(report));}
    private void finish()throws Exception{
        finished=true;for(var writer:writers.values())writer.close();
        report.add("rows",MvpWorldVerification.JSON.toJsonTree(rows));report.addProperty("completedOperations",completed);report.addProperty("ticks",ticks);
        report.addProperty("wallMs",(System.nanoTime()-started)/1e6);report.addProperty("serverThreadCpuMs",(ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpuStart)/1e6);report.addProperty("maxWorkTickMs",maxWorkMillis);
        report.addProperty("status","PASS");save();
        Files.copy(Path.of("spatial-baseline.json"),server.getWorldPath(LevelResource.ROOT).resolve("spatial-baseline.json"),StandardCopyOption.REPLACE_EXISTING);
        server.saveEverything(false,true,true);server.halt(false);
    }
    private void fail(Exception error){
        finished=true;report.addProperty("status","FAIL");report.addProperty("failure",error.toString());
        try{for(var writer:writers.values())writer.close();save();}catch(Exception write){error.addSuppressed(write);}
        error.printStackTrace();server.halt(false);
    }
}
