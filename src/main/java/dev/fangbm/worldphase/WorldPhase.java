package dev.fangbm.worldphase;

import com.mojang.logging.LogUtils;
import dev.fangbm.worldphase.registry.ModDensityFunctions;
import dev.fangbm.worldphase.command.WorldPhaseCommand;
import dev.fangbm.worldphase.command.TutorialCommand;
import net.neoforged.neoforge.common.NeoForge;
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
        NeoForge.EVENT_BUS.addListener(WorldPhaseCommand::onRegister);
        NeoForge.EVENT_BUS.addListener(TutorialCommand::onTick);
        NeoForge.EVENT_BUS.addListener(TutorialCommand::onStopped);
        LOGGER.info("WorldPhase bootstrap loaded (mod id: {}).", MOD_ID);
    }
}
