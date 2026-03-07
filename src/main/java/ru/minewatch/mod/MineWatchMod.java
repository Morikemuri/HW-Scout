package ru.minewatch.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// ================================================================
// [MINEWATCH] :: client-side mine surveillance module v1.0.0
// boots API sync daemon + registers JVM shutdown hook
// ================================================================
@Environment(EnvType.CLIENT)
public class MineWatchMod implements ClientModInitializer {

    public static final String MOD_ID = "minewatch";
    public static final Logger LOGGER  = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ApiSync.start();
        TelegramSync.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ApiSync.stop();
            TelegramSync.stop();
        }));
        LOGGER.info("MineWatch (Fabric) loaded!");
    }
}
