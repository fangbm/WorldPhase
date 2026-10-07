package dev.fangbm.worldphase.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseSeedVisitor;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.1 has no public raw-seed callback for custom density leaves; bind at Vanilla's wiring pass. */
@Mixin(RandomState.class)
abstract class RandomStateMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"), require = 1)
    private NoiseRouter worldphase$bindSeed(NoiseRouter router, DensityFunction.Visitor vanilla,
                                          Operation<NoiseRouter> original, NoiseGeneratorSettings settings,
                                          HolderGetter<NormalNoise.NoiseParameters> noises, long worldSeed) {
        return original.call(router, new PhaseNoiseSeedVisitor(vanilla, worldSeed));
    }
}
