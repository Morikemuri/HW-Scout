package net.kyrptonaught.hotkeybind.MacroTypes;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.kyrptonaught.hotkeybind.HotKeybindMod;
import org.lwjgl.glfw.GLFW;

public class SingleMacro extends BaseMacro {
    private boolean wasDown = false;
    private int debugCounter = 0;

    public SingleMacro(String key, String keyMod, String command) {
        super(key, keyMod, command);
        this.type = MacroType.SingleUse;
        HotKeybindMod.logger.info("[hotkeybind] SingleMacro: key='{}' code={} | mod='{}' code={} | cmd='{}'",
            key, primaryCode, keyMod, modifierCode, command);
    }

    @Override public void tick(long window, ClientPlayerEntity player, long now) {
        debugCounter++;
        if (debugCounter >= 60) {
            debugCounter = 0;
            boolean down = isCodeDown(window, primaryCode, primaryIsMouse);
            HotKeybindMod.logger.info("[hotkeybind] DEBUG key='{}' code={} isDown={} window={}",
                command, primaryCode, down, window);
        }
        boolean down = isTriggered(window);
        if (down && !wasDown) {
            HotKeybindMod.logger.info("[hotkeybind] TRIGGERED cmd='{}'", command);
            execute(player);
        }
        wasDown = down;
    }
}
