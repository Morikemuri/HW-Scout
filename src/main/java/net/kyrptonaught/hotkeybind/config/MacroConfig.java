package net.kyrptonaught.hotkeybind.config;

import com.google.gson.*;
import net.kyrptonaught.hotkeybind.HotKeybindMod;

import java.io.*;
import java.nio.file.*;

public class MacroConfig {

    public static void load() {
        try {
            Path dir = Paths.get("config", "hotkeybind");
            Files.createDirectories(dir);
            Path file = dir.resolve("macros.json");
            if (Files.exists(file)) {
                String json = new String(Files.readAllBytes(file));
                HotKeybindMod.config = new Gson().fromJson(json, ConfigOptions.class);
                if (HotKeybindMod.config == null) HotKeybindMod.config = new ConfigOptions();
            } else {
                HotKeybindMod.config = new ConfigOptions();
                HotKeybindMod.config.macros.add(new ConfigOptions.ConfigMacro());
                save();
            }
        } catch (Exception e) {
            HotKeybindMod.logger.error("Failed to load config", e);
            HotKeybindMod.config = new ConfigOptions();
        }
    }

    public static void save() {
        try {
            Path file = Paths.get("config", "hotkeybind", "macros.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.write(file, gson.toJson(HotKeybindMod.config).getBytes());
        } catch (Exception e) {
            HotKeybindMod.logger.error("Failed to save config", e);
        }
    }
}