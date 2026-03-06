package net.kyrptonaught.hotkeybind;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.kyrptonaught.hotkeybind.config.ConfigOptions;
import net.kyrptonaught.hotkeybind.config.MacroConfig;
import net.kyrptonaught.hotkeybind.gui.MacrosScreen;
import net.kyrptonaught.hotkeybind.MacroTypes.BaseMacro;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod("hotkeybind")
public class HotKeybindMod {

    public static final Logger logger = LogManager.getLogger("hotkeybind");
    public static ConfigOptions config;
    public static List<BaseMacro> macros = new ArrayList<>();

    public static KeyBinding OPEN_GUI_KEY;

    public HotKeybindMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        ModLoadingContext.get().registerExtensionPoint(
                ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));

        ModLoadingContext.get().registerExtensionPoint(
                ExtensionPoint.CONFIGGUIFACTORY,
                () -> (mc, parent) -> new MacrosScreen(parent));
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            OPEN_GUI_KEY = new KeyBinding(
                    "key.hotkeybind.open_gui",
                    GLFW.GLFW_KEY_GRAVE_ACCENT,
                    "key.categories.hotkeybind"
            );
            ClientRegistry.registerKeyBinding(OPEN_GUI_KEY);
        });
        MacroConfig.load();
        rebuildMacros();
        MinecraftForge.EVENT_BUS.register(new KeyTickHandler());
        MinecraftForge.EVENT_BUS.register(new GuiKeyHandler());
        logger.info("[hotkeybind] loaded, macros: {}", macros.size());
    }

    public static void rebuildMacros() {
        macros.clear();
        if (config == null || !config.enabled) return;
        for (ConfigOptions.ConfigMacro cm : config.macros) {
            BaseMacro m = BaseMacro.fromConfig(cm);
            if (m != null) macros.add(m);
        }
    }
}
