package dev.fangbm.worldphase;

import com.mojang.logging.LogUtils;
import dev.fangbm.worldphase.registry.ModDensityFunctions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Common entry point; must remain safe to load on a dedicated server. */
@Mod(WorldPhase.MOD_ID)
public final class WorldPhase {
    public static final String MOD_ID = "worldphase";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldPhase(IEventBus modBus) {
        ModDensityFunctions.TYPES.register(modBus);
        LOGGER.info("WorldPhase bootstrap loaded (mod id: {}).", MOD_ID);
    }
}
