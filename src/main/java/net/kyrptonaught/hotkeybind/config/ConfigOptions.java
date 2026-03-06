package net.kyrptonaught.hotkeybind.config;

import java.util.ArrayList;
import java.util.List;

public class ConfigOptions {
    public boolean enabled = true;
    public List<ConfigMacro> macros = new ArrayList<>();

    public static class ConfigMacro {
        public String command = "/say Command Macros!";
        public String keyName = "key.keyboard.unknown";
        public String keyModName = "key.keyboard.unknown";
        public String type = "SingleUse";
        public int delay = 0;
    }
}