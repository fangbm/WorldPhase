package dev.fangbm.worldphase.worldgen.density;

import net.minecraft.world.level.levelgen.DensityFunction;

/** Delegates Vanilla wiring unchanged, then binds only WorldPhase leaves to the original world seed. */
public record PhaseNoiseSeedVisitor(DensityFunction.Visitor delegate, long worldSeed) implements DensityFunction.Visitor {
    @Override
    public DensityFunction apply(DensityFunction function) {
        DensityFunction wired = delegate.apply(function);
        return wired instanceof PhaseNoiseDensityFunction phase ? phase.withSeed(worldSeed) : wired;
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return delegate.visitNoise(noise);
    }
}
