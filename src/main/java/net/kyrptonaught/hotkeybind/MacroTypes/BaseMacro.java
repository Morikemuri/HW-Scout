package net.kyrptonaught.hotkeybind.MacroTypes;

import net.kyrptonaught.hotkeybind.HotKeybindMod;
import net.kyrptonaught.hotkeybind.config.ConfigOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.util.InputMappings;
import org.lwjgl.glfw.GLFW;

public abstract class BaseMacro {

    public enum MacroType { SingleUse, Repeating, Delayed, DisplayOnly, ToggledRepeating }

    protected InputMappings.Input primaryKey;
    protected InputMappings.Input modifierKey;
    protected String command;
    public MacroType type;

    // Store raw GLFW codes at resolve time so we don't rely on getNumericKeyValue()
    protected int primaryCode  = GLFW.GLFW_KEY_UNKNOWN;
    protected int modifierCode = GLFW.GLFW_KEY_UNKNOWN;
    protected boolean primaryIsMouse  = false;
    protected boolean modifierIsMouse = false;

    BaseMacro(String keyName, String keyModName, String command) {
        this.command     = command;
        this.primaryKey  = resolveKey(keyName);
        this.modifierKey = resolveKey(keyModName);
        primaryCode   = resolveCode(keyName);
        modifierCode  = resolveCode(keyModName);
        primaryIsMouse  = keyName  != null && keyName.startsWith("key.mouse.");
        modifierIsMouse = keyModName != null && keyModName.startsWith("key.mouse.");
    }

    static int resolveCode(String name) {
        if (name == null || name.isEmpty() || name.equals("key.keyboard.unknown")) return GLFW.GLFW_KEY_UNKNOWN;
        if (name.startsWith("key.keyboard.")) return nameToGlfw(name.substring("key.keyboard.".length()));
        if (name.startsWith("key.mouse.")) {
            try { return Integer.parseInt(name.substring("key.mouse.".length())); } catch (Exception ignored) {}
        }
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    /**
     * Resolves a key name like "key.keyboard.j" to InputMappings.Input.
     * Uses GLFW key name lookup table since InputMappings.getKey(String)
     * is not reliably available in 1.16.5 Forge mappings.
     */
    static InputMappings.Input resolveKey(String name) {
        if (name == null || name.isEmpty() || name.equals("key.keyboard.unknown"))
            return InputMappings.UNKNOWN;

        // Direct GLFW lookup — bypasses Forge InputMappings which breaks in obfuscated runtime
        if (name.startsWith("key.keyboard.")) {
            int glfwKey = nameToGlfw(name.substring("key.keyboard.".length()));
            if (glfwKey != GLFW.GLFW_KEY_UNKNOWN)
                return InputMappings.Type.KEYSYM.getOrCreate(glfwKey);
        }
        if (name.startsWith("key.mouse.")) {
            try {
                int btn = Integer.parseInt(name.substring("key.mouse.".length()));
                return InputMappings.Type.MOUSE.getOrCreate(btn);
            } catch (Exception ignored) {}
        }
        return InputMappings.UNKNOWN;
    }

    /**
     * Maps the suffix of "key.keyboard.X" to a GLFW key code.
     */
    private static int nameToGlfw(String s) {
        switch (s) {
            case "0": return GLFW.GLFW_KEY_0;
            case "1": return GLFW.GLFW_KEY_1;
            case "2": return GLFW.GLFW_KEY_2;
            case "3": return GLFW.GLFW_KEY_3;
            case "4": return GLFW.GLFW_KEY_4;
            case "5": return GLFW.GLFW_KEY_5;
            case "6": return GLFW.GLFW_KEY_6;
            case "7": return GLFW.GLFW_KEY_7;
            case "8": return GLFW.GLFW_KEY_8;
            case "9": return GLFW.GLFW_KEY_9;
            case "a": return GLFW.GLFW_KEY_A;
            case "b": return GLFW.GLFW_KEY_B;
            case "c": return GLFW.GLFW_KEY_C;
            case "d": return GLFW.GLFW_KEY_D;
            case "e": return GLFW.GLFW_KEY_E;
            case "f": return GLFW.GLFW_KEY_F;
            case "g": return GLFW.GLFW_KEY_G;
            case "h": return GLFW.GLFW_KEY_H;
            case "i": return GLFW.GLFW_KEY_I;
            case "j": return GLFW.GLFW_KEY_J;
            case "k": return GLFW.GLFW_KEY_K;
            case "l": return GLFW.GLFW_KEY_L;
            case "m": return GLFW.GLFW_KEY_M;
            case "n": return GLFW.GLFW_KEY_N;
            case "o": return GLFW.GLFW_KEY_O;
            case "p": return GLFW.GLFW_KEY_P;
            case "q": return GLFW.GLFW_KEY_Q;
            case "r": return GLFW.GLFW_KEY_R;
            case "s": return GLFW.GLFW_KEY_S;
            case "t": return GLFW.GLFW_KEY_T;
            case "u": return GLFW.GLFW_KEY_U;
            case "v": return GLFW.GLFW_KEY_V;
            case "w": return GLFW.GLFW_KEY_W;
            case "x": return GLFW.GLFW_KEY_X;
            case "y": return GLFW.GLFW_KEY_Y;
            case "z": return GLFW.GLFW_KEY_Z;
            case "f1":  return GLFW.GLFW_KEY_F1;
            case "f2":  return GLFW.GLFW_KEY_F2;
            case "f3":  return GLFW.GLFW_KEY_F3;
            case "f4":  return GLFW.GLFW_KEY_F4;
            case "f5":  return GLFW.GLFW_KEY_F5;
            case "f6":  return GLFW.GLFW_KEY_F6;
            case "f7":  return GLFW.GLFW_KEY_F7;
            case "f8":  return GLFW.GLFW_KEY_F8;
            case "f9":  return GLFW.GLFW_KEY_F9;
            case "f10": return GLFW.GLFW_KEY_F10;
            case "f11": return GLFW.GLFW_KEY_F11;
            case "f12": return GLFW.GLFW_KEY_F12;
            case "space":      return GLFW.GLFW_KEY_SPACE;
            case "enter":      return GLFW.GLFW_KEY_ENTER;
            case "escape":     return GLFW.GLFW_KEY_ESCAPE;
            case "tab":        return GLFW.GLFW_KEY_TAB;
            case "backspace":  return GLFW.GLFW_KEY_BACKSPACE;
            case "insert":     return GLFW.GLFW_KEY_INSERT;
            case "delete":     return GLFW.GLFW_KEY_DELETE;
            case "right":      return GLFW.GLFW_KEY_RIGHT;
            case "left":       return GLFW.GLFW_KEY_LEFT;
            case "down":       return GLFW.GLFW_KEY_DOWN;
            case "up":         return GLFW.GLFW_KEY_UP;
            case "page.up":    return GLFW.GLFW_KEY_PAGE_UP;
            case "page.down":  return GLFW.GLFW_KEY_PAGE_DOWN;
            case "home":       return GLFW.GLFW_KEY_HOME;
            case "end":        return GLFW.GLFW_KEY_END;
            case "left.shift":   return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "left.control": return GLFW.GLFW_KEY_LEFT_CONTROL;
            case "left.alt":     return GLFW.GLFW_KEY_LEFT_ALT;
            case "right.shift":   return GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "right.control": return GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "right.alt":     return GLFW.GLFW_KEY_RIGHT_ALT;
            case "grave.accent": return GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "minus":        return GLFW.GLFW_KEY_MINUS;
            case "equal":        return GLFW.GLFW_KEY_EQUAL;
            case "left.bracket":  return GLFW.GLFW_KEY_LEFT_BRACKET;
            case "right.bracket": return GLFW.GLFW_KEY_RIGHT_BRACKET;
            case "backslash":    return GLFW.GLFW_KEY_BACKSLASH;
            case "semicolon":    return GLFW.GLFW_KEY_SEMICOLON;
            case "apostrophe":   return GLFW.GLFW_KEY_APOSTROPHE;
            case "comma":        return GLFW.GLFW_KEY_COMMA;
            case "period":       return GLFW.GLFW_KEY_PERIOD;
            case "slash":        return GLFW.GLFW_KEY_SLASH;
            case "keypad.0": return GLFW.GLFW_KEY_KP_0;
            case "keypad.1": return GLFW.GLFW_KEY_KP_1;
            case "keypad.2": return GLFW.GLFW_KEY_KP_2;
            case "keypad.3": return GLFW.GLFW_KEY_KP_3;
            case "keypad.4": return GLFW.GLFW_KEY_KP_4;
            case "keypad.5": return GLFW.GLFW_KEY_KP_5;
            case "keypad.6": return GLFW.GLFW_KEY_KP_6;
            case "keypad.7": return GLFW.GLFW_KEY_KP_7;
            case "keypad.8": return GLFW.GLFW_KEY_KP_8;
            case "keypad.9": return GLFW.GLFW_KEY_KP_9;
            default: return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    public abstract void tick(long window, ClientPlayerEntity player, long now);

    protected boolean isTriggered(long window) {
        if (primaryCode == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (!isCodeDown(window, primaryCode, primaryIsMouse)) return false;
        if (modifierCode != GLFW.GLFW_KEY_UNKNOWN)
            return isCodeDown(window, modifierCode, modifierIsMouse);
        return true;
    }

    protected void execute(ClientPlayerEntity player) {
        if (command == null || command.isEmpty()) return;
        final String cmd = command;
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.chat(cmd);
        });
    }

    protected static boolean isCodeDown(long window, int code, boolean isMouse) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (isMouse) return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }

    protected static boolean isKeyDown(long window, InputMappings.Input key) {
        // kept for compatibility but not used in main path
        return false;
    }

    public static BaseMacro fromConfig(ConfigOptions.ConfigMacro cm) {
        try {
            switch (cm.type) {
                case "Repeating":        return new RepeatingMacro(cm.keyName, cm.keyModName, cm.command, cm.delay);
                case "Delayed":          return new DelayedMacro(cm.keyName, cm.keyModName, cm.command, cm.delay);
                case "ToggledRepeating": return new ToggledRepeating(cm.keyName, cm.keyModName, cm.command, cm.delay);
                case "DisplayOnly":      return new DisplayMacro(cm.keyName, cm.keyModName, cm.command);
                default:                 return new SingleMacro(cm.keyName, cm.keyModName, cm.command);
            }
        } catch (Exception e) { return null; }
    }
}
