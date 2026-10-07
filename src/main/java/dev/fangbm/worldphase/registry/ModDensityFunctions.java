package dev.fangbm.worldphase.registry;

import com.mojang.serialization.MapCodec;
import dev.fangbm.worldphase.WorldPhase;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers one composable density-function type in Vanilla's existing type registry. */
public final class ModDensityFunctions {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, WorldPhase.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<PhaseNoiseDensityFunction>> PHASE_NOISE =
            TYPES.register("phase_noise", () -> PhaseNoiseDensityFunction.CODEC.codec());

    private ModDensityFunctions() {}
}
