package net.kyrptonaught.hotkeybind.MacroTypes;
import net.minecraft.client.entity.player.ClientPlayerEntity;
public class DisplayMacro extends BaseMacro {
    public DisplayMacro(String key, String keyMod, String command) {
        super(key, keyMod, command);
        this.type = MacroType.DisplayOnly;
    }
    @Override public void tick(long window, ClientPlayerEntity player, long now) {}
}