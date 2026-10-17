package dev.fangbm.worldphase.verification.v2;

import net.minecraft.world.level.levelgen.DensityFunction;

/** Constructor-scoped context only: no global/current seed or Level references. */
public final class V2SeedVisitor implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    private final long seed;
    private V2Context context;
    public V2SeedVisitor(DensityFunction.Visitor delegate,long seed){this.delegate=delegate;this.seed=seed;}
    public DensityFunction apply(DensityFunction f) {
        DensityFunction wired=delegate.apply(f);
        if(wired instanceof PrototypeAnalyticDensityFunction v2) {
            if(context==null)context=new V2Context(seed,R1.rawReference(v2.input()));
            return v2.bind(context);
        }
        return wired;
    }
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder n){return delegate.visitNoise(n);}
}
