package dev.fangbm.worldphase.verification.v2;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Loaded exclusively with phaseVerification output; never in the production mod JAR. */
@Mod("worldphase_v2_preflight")
public final class V2PreflightMod {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES=DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE,"worldphase");
    public static final java.util.function.Supplier<MapCodec<? extends DensityFunction>> ANALYTIC=TYPES.register("prototype_v2/analytic_r1",()->PrototypeAnalyticDensityFunction.CODEC.codec());
    public V2PreflightMod(IEventBus bus){TYPES.register(bus);}
}
