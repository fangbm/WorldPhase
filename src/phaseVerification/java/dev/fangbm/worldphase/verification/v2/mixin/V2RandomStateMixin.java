package dev.fangbm.worldphase.verification.v2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.fangbm.worldphase.verification.v2.V2SeedVisitor;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=RandomState.class,priority=900)
abstract class V2RandomStateMixin {
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"),require=1)
    private NoiseRouter v2$seed(NoiseRouter router,DensityFunction.Visitor visitor,Operation<NoiseRouter> original,
                               NoiseGeneratorSettings settings,HolderGetter<NormalNoise.NoiseParameters> noises,long seed) {
        return original.call(router,new V2SeedVisitor(visitor,seed));
    }
}
