package net.kyrptonaught.hotkeybind.MacroTypes;
import net.minecraft.client.entity.player.ClientPlayerEntity;
public class ToggledRepeating extends BaseMacro {
    private final int delay;
    private boolean wasDown = false;
    private boolean active = false;
    private long lastRun = 0;
    public ToggledRepeating(String key, String keyMod, String command, int delay) {
        super(key, keyMod, command);
        this.delay = delay;
        this.type = MacroType.ToggledRepeating;
    }
    @Override public void tick(long window, ClientPlayerEntity player, long now) {
        boolean down = isTriggered(window);
        if (down && !wasDown) active = !active;
        wasDown = down;
        if (active && now - lastRun >= delay) { execute(player); lastRun = now; }
    }
}