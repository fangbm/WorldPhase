package dev.fangbm.worldphase.verification.v2;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.*;

/** Dev-only frozen analytic fields. Runtime seed/context never serialized. */
public final class PrototypeAnalyticDensityFunction implements DensityFunction {
    public enum Field { COMMON_DENSITY, SMOOTH_CORE, RIDGE_CORE, NODE_WEIGHT, PROTECTION }
    private record Config(String profile,String field,DensityFunction reference,Optional<NoiseHolder> detail) {}
    private static final MapCodec<Config> CONFIG=RecordCodecBuilder.mapCodec(i->i.group(
            Codec.STRING.fieldOf("profile").forGetter(Config::profile),
            Codec.STRING.fieldOf("field").forGetter(Config::field),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("reference").forGetter(Config::reference),
            NoiseHolder.CODEC.optionalFieldOf("detail").forGetter(Config::detail)).apply(i,Config::new));
    private static final MapCodec<PrototypeAnalyticDensityFunction> BASE=CONFIG.flatXmap(PrototypeAnalyticDensityFunction::decode,
            f->DataResult.success(new Config(R1.PROFILE,f.field.name().toLowerCase(Locale.ROOT),f.serialReference,f.detail)));
    public static final KeyDispatchDataCodec<PrototypeAnalyticDensityFunction> CODEC=KeyDispatchDataCodec.of(new MapCodec<>() {
        public <T> DataResult<PrototypeAnalyticDensityFunction> decode(DynamicOps<T> ops,MapLike<T> map) {
            Set<String> allowed=Set.of("type","profile","field","reference","detail");
            for(var pair:map.entries().toList()) {
                var key=ops.getStringValue(pair.getFirst()).result();
                if(key.isEmpty()||!allowed.contains(key.get()))return DataResult.error(()->"Unknown analytic_r1 field: "+pair.getFirst());
            }
            return BASE.decode(ops,map);
        }
        public <T> RecordBuilder<T> encode(PrototypeAnalyticDensityFunction f,DynamicOps<T> ops,RecordBuilder<T> out){return BASE.encode(f,ops,out);}
        public <T> Stream<T> keys(DynamicOps<T> ops){return BASE.keys(ops);}
    });
    private final Field field;
    private final DensityFunction serialReference,input;
    private final Optional<NoiseHolder> detail;
    private final V2Context context;
    private PrototypeAnalyticDensityFunction(Field field,DensityFunction serialReference,DensityFunction input,Optional<NoiseHolder> detail,V2Context context) {
        this.field=field;this.serialReference=serialReference;this.input=input;this.detail=detail;this.context=context;
    }
    private static DataResult<PrototypeAnalyticDensityFunction> decode(Config c) {
        try {
            if(!R1.PROFILE.equals(c.profile))throw new IllegalArgumentException("Unknown r1 profile");
            Field f=Field.valueOf(c.field.toUpperCase(Locale.ROOT));
            if(!f.name().toLowerCase(Locale.ROOT).equals(c.field))throw new IllegalArgumentException("Field must use lower-case r1 name");
            if(!(c.reference instanceof DensityFunctions.HolderHolder h)||h.function().unwrapKey().isEmpty()
                    ||!h.function().unwrapKey().orElseThrow().identifier().toString().equals("minecraft:overworld/continents"))
                throw new IllegalArgumentException("reference must be named minecraft:overworld/continents");
            boolean core=f==Field.SMOOTH_CORE||f==Field.RIDGE_CORE;
            if(core!=c.detail.isPresent())throw new IllegalArgumentException("detail required exactly for core fields");
            if(core) {
                NoiseHolder noise=c.detail.orElseThrow();
                String expected="worldphase:prototype_v2/"+(f==Field.SMOOTH_CORE?"smooth":"ridge")+"/detail_r1";
                if(!noise.noiseData().unwrapKey().orElseThrow().identifier().toString().equals(expected))throw new IllegalArgumentException("Wrong detail holder");
                var p=noise.noiseData().value();
                if(p.firstOctave()!=0||p.amplitudes().size()!=1||p.amplitudes().getDouble(0)!=1)throw new IllegalArgumentException("Wrong detail parameters");
            }
            return DataResult.success(new PrototypeAnalyticDensityFunction(f,c.reference,c.reference,c.detail,null));
        }catch(RuntimeException e){return DataResult.error(()->"Invalid analytic_r1 config: "+e.getMessage());}
    }
    public PrototypeAnalyticDensityFunction bind(V2Context value) {
        if(context==value)return this;
        if(detail.isPresent()&&detail.get().noise()==null)throw new IllegalStateException("V2 detail holder not wired");
        return new PrototypeAnalyticDensityFunction(field,serialReference,input,detail,value);
    }
    public DensityFunction input(){return input;}
    public V2Context context(){if(context==null)throw new IllegalStateException("Unbound analytic_r1 raw world seed");return context;}
    public Field field(){return field;}
    public Optional<NoiseHolder> detail(){return detail;}
    public double compute(FunctionContext c) {
        V2Context state=context();int x=c.blockX(),z=c.blockZ(),y=c.blockY();state.requireReady(x,z);
        double result=switch(field) {
            case COMMON_DENSITY -> (state.hc(x,z)-y)/16;
            case NODE_WEIGHT -> state.weight(x,z);
            case PROTECTION -> {
                double h=state.hc(x,z);
                yield (1-state.weight(x,z))*((1-R1.s(16,32,h-y))*(1-R1.s(4,20,y-h)));
            }
            case SMOOTH_CORE,RIDGE_CORE -> {
                boolean ridge=field==Field.RIDGE_CORE;var column=state.column(x,z,ridge);NoiseHolder n=detail.orElseThrow();
                double bound=n.maxValue();
                if(n.noise()==null||!Double.isFinite(bound)||bound<=0)throw new IllegalStateException("Unwired/invalid NormalNoise maxValue");
                double v=V2Context.finite(n.getValue(x/(ridge?64.0:80),y/(ridge?40.0:48),z/(ridge?64.0:80))/bound);
                if(Math.abs(v)>1)throw new IllegalStateException("NormalNoise bound violated");
                double detailValue=ridge?((.12*(R1.s(.10,.30,column.ridgeProfile())*(1-R1.s(.70,.90,column.ridgeProfile()))))
                        *(1-R1.s(16,32,Math.abs(y-column.height()))))*v:.04*v;
                yield (column.height()-y)/16+detailValue;
            }
        };
        V2Context.finite(result);
        if(result<minValue()||result>maxValue())throw new IllegalStateException("analytic_r1 min/max violation");
        return result;
    }
    public void fillArray(double[] values,ContextProvider provider){provider.fillAllDirectly(values,this);}
    public DensityFunction mapAll(Visitor visitor) {
        DensityFunction child=input.mapAll(visitor);
        Optional<NoiseHolder> mapped=detail.map(visitor::visitNoise);
        return visitor.apply(new PrototypeAnalyticDensityFunction(field,serialReference,child,mapped,context));
    }
    public double minValue(){return field==Field.NODE_WEIGHT||field==Field.PROTECTION?0:-134217731;}
    public double maxValue(){return field==Field.NODE_WEIGHT||field==Field.PROTECTION?1:134217741;}
    public KeyDispatchDataCodec<? extends DensityFunction> codec(){return CODEC;}
}
