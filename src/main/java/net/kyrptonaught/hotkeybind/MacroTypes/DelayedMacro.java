package net.kyrptonaught.hotkeybind.MacroTypes;
import net.minecraft.client.entity.player.ClientPlayerEntity;
public class DelayedMacro extends BaseMacro {
    private final int delay;
    private boolean wasDown = false;
    private long triggerTime = -1;
    public DelayedMacro(String key, String keyMod, String command, int delay) {
        super(key, keyMod, command);
        this.delay = delay;
        this.type = MacroType.Delayed;
    }
    @Override public void tick(long window, ClientPlayerEntity player, long now) {
        boolean down = isTriggered(window);
        if (down && !wasDown) triggerTime = now;
        if (triggerTime > 0 && now - triggerTime >= delay) { execute(player); triggerTime = -1; }
        wasDown = down;
    }
}