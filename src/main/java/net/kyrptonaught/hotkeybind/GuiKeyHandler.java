package net.kyrptonaught.hotkeybind;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.kyrptonaught.hotkeybind.gui.MacrosScreen;

public class GuiKeyHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (HotKeybindMod.OPEN_GUI_KEY != null && HotKeybindMod.OPEN_GUI_KEY.consumeClick()) {
            mc.setScreen(new MacrosScreen(null));
        }
    }
}
