package net.kyrptonaught.hotkeybind.MacroTypes;
import net.minecraft.client.entity.player.ClientPlayerEntity;
public class RepeatingMacro extends BaseMacro {
    private final int delay;
    private long lastRun = 0;
    public RepeatingMacro(String key, String keyMod, String command, int delay) {
        super(key, keyMod, command);
        this.delay = delay;
        this.type = MacroType.Repeating;
    }
    @Override public void tick(long window, ClientPlayerEntity player, long now) {
        if (isTriggered(window) && now - lastRun >= delay) { execute(player); lastRun = now; }
    }
}