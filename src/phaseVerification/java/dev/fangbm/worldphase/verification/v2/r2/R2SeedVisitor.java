package dev.fangbm.worldphase.verification.v2.r2;
import dev.fangbm.worldphase.verification.v2.R1;
import net.minecraft.world.level.levelgen.DensityFunction;
/** Same constructor-scoped raw seed contract; no static context or Level. */
public final class R2SeedVisitor implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;private final long seed;private R2Context context;
    public R2SeedVisitor(DensityFunction.Visitor delegate,long seed){this.delegate=delegate;this.seed=seed;}
    public DensityFunction apply(DensityFunction f){var wired=delegate.apply(f);if(wired instanceof R2Density d){if(context==null)context=new R2Context(seed,R1.rawReference(d.input()));return d.bind(context);}return wired;}
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder n){return delegate.visitNoise(n);}
}
