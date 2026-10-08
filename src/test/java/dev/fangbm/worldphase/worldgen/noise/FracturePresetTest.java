package dev.fangbm.worldphase.worldgen.noise;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.worldgen.density.PhaseNoiseDensityFunction;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import org.junit.jupiter.api.Test;

/** Same-package diagnostic uses the existing stable channels; no copied hashing/kernel mathematics. */
class FracturePresetTest {
    @Test void presetProducesLargeContinuousSpatialDisplacementWithTheRawSeed() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream(
                "/data/worldphase/worldgen/density_function/fracture_noise.json"),StandardCharsets.UTF_8)) {
            var config=PhaseNoiseDensityFunction.Config.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseReader(reader)).getOrThrow();
            var adapter=new PhaseNoiseDensityFunction(config).withSeed(123456789L);
            var warp=new GradientNoise(config.warpFrequency());var base=new GradientNoise(config.frequency());
            long channelX=NoiseHash.channel(123456789L,DomainWarpNoise.WARP_X_CHANNEL);
            long channelZ=NoiseHash.channel(123456789L,DomainWarpNoise.WARP_Z_CHANNEL);
            double sum=0,max=0,neighborChange=0;int samples=0;
            for(int x=-1024;x<=1024;x+=64) for(int z=-1024;z<=1024;z+=64) {
                double dx=warp.sample(channelX,x,z)*config.warpStrength(),dz=warp.sample(channelZ,x,z)*config.warpStrength();
                double length=StrictMath.hypot(dx,dz);sum+=length;max=Math.max(max,length);samples++;
                assertEquals(base.sample(123456789L,x+dx,z+dz)*config.amplitude(),adapter.compute(new SinglePointContext(x,80,z)));
                double nextX=warp.sample(channelX,x+1,z)*config.warpStrength(),nextZ=warp.sample(channelZ,x+1,z)*config.warpStrength();
                neighborChange=Math.max(neighborChange,StrictMath.hypot(nextX-dx,nextZ-dz));
            }
            System.out.printf("WPH007 Fracture displacement samples=%d mean=%.2f blocks max=%.2f max one-block vector change=%.3f%n",samples,sum/samples,max,neighborChange);
            assertTrue(sum/samples>100,"Preset should move macro landforms, not merely increase amplitude");
            assertTrue(neighborChange<5,"Warp vectors must vary continuously across neighboring blocks");
        }
    }
}
