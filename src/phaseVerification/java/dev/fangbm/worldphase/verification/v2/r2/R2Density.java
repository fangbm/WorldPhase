package dev.fangbm.worldphase.verification.v2.r2;

import dev.fangbm.worldphase.verification.v2.*;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.*;

/** Dev-only frozen analytic fields. Runtime seed/context never serialized. */
public final class R2Density implements DensityFunction {
    public enum Field { SMOOTH_CORE, RIDGE_CORE, SMOOTH_CONTROLLED, RIDGE_CONTROLLED, SMOOTH_PROTECTION, RIDGE_PROTECTION }
    private record Config(String profile,String field,DensityFunction reference,Optional<NoiseHolder> detail) {}
    private static final MapCodec<Config> CONFIG=RecordCodecBuilder.mapCodec(i->i.group(
            Codec.STRING.fieldOf("profile").forGetter(Config::profile),
            Codec.STRING.fieldOf("field").forGetter(Config::field),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("reference").forGetter(Config::reference),
            NoiseHolder.CODEC.optionalFieldOf("detail").forGetter(Config::detail)).apply(i,Config::new));
    private static final MapCodec<R2Density> BASE=CONFIG.flatXmap(R2Density::decode,
            f->DataResult.success(new Config(R2.PROFILE,f.field.name().toLowerCase(Locale.ROOT),f.serialReference,f.detail)));
    public static final KeyDispatchDataCodec<R2Density> CODEC=KeyDispatchDataCodec.of(new MapCodec<>() {
        public <T> DataResult<R2Density> decode(DynamicOps<T> ops,MapLike<T> map) {
            Set<String> allowed=Set.of("type","profile","field","reference","detail");
            for(var pair:map.entries().toList()) {
                var key=ops.getStringValue(pair.getFirst()).result();
                if(key.isEmpty()||!allowed.contains(key.get()))return DataResult.error(()->"Unknown analytic_r2 field: "+pair.getFirst());
            }
            return BASE.decode(ops,map);
        }
        public <T> RecordBuilder<T> encode(R2Density f,DynamicOps<T> ops,RecordBuilder<T> out){return BASE.encode(f,ops,out);}
        public <T> Stream<T> keys(DynamicOps<T> ops){return BASE.keys(ops);}
    });
    private final Field field;
    private final DensityFunction serialReference,input;
    private final Optional<NoiseHolder> detail;
    private final R2Context context;
    private R2Density(Field field,DensityFunction serialReference,DensityFunction input,Optional<NoiseHolder> detail,R2Context context) {
        this.field=field;this.serialReference=serialReference;this.input=input;this.detail=detail;this.context=context;
    }
    private static DataResult<R2Density> decode(Config c) {
        try {
            if(!R2.PROFILE.equals(c.profile))throw new IllegalArgumentException("Unknown r2 profile");
            Field f=Field.valueOf(c.field.toUpperCase(Locale.ROOT));
            if(!f.name().toLowerCase(Locale.ROOT).equals(c.field))throw new IllegalArgumentException("Field must use lower-case r2 name");
            if(!(c.reference instanceof DensityFunctions.HolderHolder h)||h.function().unwrapKey().isEmpty()
                    ||!h.function().unwrapKey().orElseThrow().identifier().toString().equals("minecraft:overworld/continents"))
                throw new IllegalArgumentException("reference must be named minecraft:overworld/continents");
            boolean core=!f.name().endsWith("PROTECTION");
            if(core!=c.detail.isPresent())throw new IllegalArgumentException("detail required exactly for core fields");
            if(core) {
                NoiseHolder noise=c.detail.orElseThrow();
                String expected="worldphase:prototype_v2/"+(f.name().startsWith("SMOOTH")?"smooth":"ridge")+"/detail_r1";
                if(!noise.noiseData().unwrapKey().orElseThrow().identifier().toString().equals(expected))throw new IllegalArgumentException("Wrong detail holder");
                var p=noise.noiseData().value();
                if(p.firstOctave()!=0||p.amplitudes().size()!=1||p.amplitudes().getDouble(0)!=1)throw new IllegalArgumentException("Wrong detail parameters");
            }
            return DataResult.success(new R2Density(f,c.reference,c.reference,c.detail,null));
        }catch(RuntimeException e){return DataResult.error(()->"Invalid analytic_r2 config: "+e.getMessage());}
    }
    public R2Density bind(R2Context value) {
        if(context==value)return this;
        if(detail.isPresent()&&detail.get().noise()==null)throw new IllegalStateException("V2 detail holder not wired");
        return new R2Density(field,serialReference,input,detail,value);
    }
    public DensityFunction input(){return input;}
    public R2Context context(){if(context==null)throw new IllegalStateException("Unbound analytic_r2 raw world seed");return context;}
    public Field field(){return field;}
    public Optional<NoiseHolder> detail(){return detail;}
    public double compute(FunctionContext c) {
        R2Context state=context();int x=c.blockX(),z=c.blockZ(),y=c.blockY();state.requireReady(x,z);
        boolean ridge=field.name().startsWith("RIDGE");double hp=state.hpValue(x,z,ridge),core=(hp-y)/16,detailValue=0;
        if(!field.name().endsWith("PROTECTION")){
            NoiseHolder n=detail.orElseThrow();double bound=n.maxValue();
            if(n.noise()==null||!Double.isFinite(bound)||bound<=0)throw new IllegalStateException("INVALID_HOLDER_BOUND");
            double v=n.getValue(x/(ridge?64.0:80),y/(ridge?40.0:48),z/(ridge?64.0:80))/bound;
            if(!Double.isFinite(v)||Math.abs(v)>1)throw new IllegalStateException("HOLDER_BOUND_VIOLATION");
            double r=state.ridgeProfile(x,z);
            double a=ridge?.12*(R1.s(.1,.3,r)*(1-R1.s(.7,.9,r)))*(1-R1.s(16,32,Math.abs(y-hp))):.04;
            detailValue=a*v;core+=detailValue;
        }
        double result=field.name().endsWith("CORE")?core:field.name().endsWith("PROTECTION")?state.geometry(ridge,x,y,z,core,detailValue).protection():state.geometry(ridge,x,y,z,core,detailValue).density();
        if(!Double.isFinite(result))throw new IllegalStateException("NONFINITE_DENSITY");
        if(result<minValue()||result>maxValue())throw new IllegalStateException("analytic_r2 min/max violation");
        return result;
    }
    public void fillArray(double[] values,ContextProvider provider){provider.fillAllDirectly(values,this);}
    public DensityFunction mapAll(Visitor visitor) {
        DensityFunction child=input.mapAll(visitor);
        Optional<NoiseHolder> mapped=detail.map(visitor::visitNoise);
        return visitor.apply(new R2Density(field,serialReference,child,mapped,context));
    }
    public double minValue(){return field.name().endsWith("PROTECTION")?0:-134217750;}
    public double maxValue(){return field.name().endsWith("PROTECTION")?1:134217750;}
    public KeyDispatchDataCodec<? extends DensityFunction> codec(){return CODEC;}
}
