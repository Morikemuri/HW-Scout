package ru.minewatch.mod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// ================================================================
// [MINEWATCH] :: client-side mine surveillance module v1.0.0
// boots API sync daemon + registers JVM shutdown hook
// ================================================================
@Mod(MineWatchMod.MOD_ID)
public class MineWatchMod {

    public static final String MOD_ID = "minewatch";
    public static final Logger LOGGER  = LogManager.getLogger(MOD_ID);

    public MineWatchMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new MineHudRenderer()); // HOOK: HUD overlay
        MinecraftForge.EVENT_BUS.register(new ScoreboardReader()); // HOOK: hologram scanner
        ApiSync.start();                                            // LAUNCH: remote sync daemon
        Runtime.getRuntime().addShutdownHook(new Thread(ApiSync::stop)); // HOOK: graceful JVM exit
        LOGGER.info("MineWatch loaded!");
    }
}
