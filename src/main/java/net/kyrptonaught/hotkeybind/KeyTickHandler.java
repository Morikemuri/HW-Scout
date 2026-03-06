package net.kyrptonaught.hotkeybind;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.kyrptonaught.hotkeybind.MacroTypes.BaseMacro;

public class KeyTickHandler {

    private int debugTickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        // Debug every 200 ticks (~10 sec): log state so we can see if handler is alive
        debugTickCounter++;
        if (debugTickCounter >= 200) {
            debugTickCounter = 0;
            HotKeybindMod.logger.info("[hotkeybind] tick alive | player={} | screen={} | enabled={} | macros={}",
                mc.player != null,
                mc.screen != null ? mc.screen.getClass().getSimpleName() : "null",
                HotKeybindMod.config != null && HotKeybindMod.config.enabled,
                HotKeybindMod.macros.size());
        }

        if (mc.player == null || mc.screen != null) return;
        if (HotKeybindMod.config == null || !HotKeybindMod.config.enabled) return;

        long window = mc.getWindow().getWindow();
        long now = System.currentTimeMillis();
        for (BaseMacro macro : HotKeybindMod.macros) {
            macro.tick(window, mc.player, now);
        }
    }
}
