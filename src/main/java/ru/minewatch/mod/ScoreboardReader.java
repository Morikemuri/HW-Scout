package ru.minewatch.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScoreboardReader {

    private static final Pattern PAT_MIN_SEC    = Pattern.compile("(\\d+)\\s*мин\\.?\\s*(\\d+)\\s*сек");
    private static final Pattern PAT_SEC        = Pattern.compile("(\\d+)\\s*сек");
    private static final Pattern PAT_MODE       = Pattern.compile("[^А-Яа-яёЁA-Za-z#\\d]*(.*?#\\d+)[^А-Яа-яёЁA-Za-z#\\d]*$");
    private static final Pattern PAT_TYPE       = Pattern.compile("(Обычн|Эпич|Мифич|Легенд)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_WORLD_TYPE = Pattern.compile(
        "^(Ад|Энд|Верхнего\\s*мира|Нижнего\\s*мира|Нижн\\S*|Мир|Nether|End|Overworld)[\\s,;:—\\-]+\\s*(Обычн\\S*|Эпич\\S*|Мифич\\S*|Легенд\\S*)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private int tickCounter = 0;
    private int dumpTick    = 0;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % 20 != 0) return;
        try {
            tickInner();
        } catch (Exception ex) {
            try (PrintWriter pw = new PrintWriter(new FileWriter("C:\\mine_alert\\error.txt", true))) {
                pw.println("[" + new java.util.Date() + "] " + ex);
                for (StackTraceElement el : ex.getStackTrace()) pw.println("  at " + el);
            } catch (Exception ignored) {}
        }
    }

    private void tickInner() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            // Вышли из мира — сбросить режим, чтобы при повторном входе
            // на тот же сервер onAnarchyChanged сработал и данные обновились.
            if (!MineData.getAnarchyMode().isEmpty()) MineData.clearCurrentMode();
            return;
        }

        Scoreboard sb = mc.level.getScoreboard();
        if (sb != null) {
            for (ScorePlayerTeam team : sb.getPlayerTeams()) {
                String name   = team.getName();
                String prefix = stripColors(team.getPlayerPrefix().getString());
                String suffix = stripColors(team.getPlayerSuffix().getString());
                String full   = (prefix + suffix).trim();
                if (full.isEmpty()) continue;
                if (name.startsWith("boards:") || name.startsWith("boardp:"))
                    tryParseMode(full);
            }
        }

        AxisAlignedBB box = mc.player.getBoundingBox().inflate(35);
        List<Entity> raw  = mc.level.getEntities(mc.player, box);

        Map<String, List<Entity>> groups = new LinkedHashMap<>();
        for (Entity e : raw) {
            if (!(e instanceof ArmorStandEntity)) continue;
            String n = e.hasCustomName()
                ? stripColors(e.getCustomName().getString())
                : stripColors(e.getName().getString());
            if (n.isEmpty() || n.equals("Стойка для брони")) continue;
            String key = Math.round(e.getX()) + "," + Math.round(e.getZ());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        boolean doDump = (++dumpTick % 5 == 1);
        StringBuilder dump = doDump ? new StringBuilder() : null;

        for (Map.Entry<String, List<Entity>> entry : groups.entrySet()) {
            List<Entity> group = entry.getValue();
            group.sort(Comparator.comparingDouble(e -> -e.getY()));

            List<String> lines = new ArrayList<>();
            for (Entity e : group) {
                String n = e.hasCustomName()
                    ? stripColors(e.getCustomName().getString())
                    : stripColors(e.getName().getString());
                lines.add(n);
            }

            if (dump != null) {
                dump.append("GROUP ").append(entry.getKey()).append(":\n");
                for (String l : lines) dump.append("  '").append(l).append("'\n");
            }

            if (isShaftGroup(lines)) parseShaftGroup(lines);
        }

        if (dump != null) {
            try (PrintWriter pw = new PrintWriter(new FileWriter("C:\\mine_alert\\entity_dump.txt", false))) {
                pw.print(dump.toString());
            } catch (Exception ignored) {}
        }
        MineData.checkZeroTrigger();
    }

    private boolean isShaftGroup(List<String> lines) {
        for (String l : lines) {
            String low = l.toLowerCase();
            if (low.contains("обновлени") || low.contains("осталось") || low.contains("текущ")) return true;
        }
        return false;
    }

    private void parseShaftGroup(List<String> lines) {
        String mode = MineData.getAnarchyMode();
        if (mode == null || mode.isEmpty()) return;

        String groupWorld = detectGroupWorld(lines);

        for (String line : lines) {
            int secs = parseTime(line);
            if (secs > 0) { MineData.setTimeForWorld(mode, groupWorld, secs); break; }
        }

        String label = null;
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("текущ")) { label = "current"; continue; }
            if (lower.contains("следу")) { label = "next";    continue; }
            if (label == null) continue;

            String type = parseType(line);
            if (type != null) {
                String world = groupWorld;
                Matcher wm = PAT_WORLD_TYPE.matcher(line);
                if (wm.find()) world = normalizeWorld(wm.group(1));

                if ("current".equals(label)) MineData.setCurrentMineForWorld(world, type);
                else                         MineData.setNextMineForWorld(world, type);
                label = null;
            }
        }
    }

    private String detectGroupWorld(List<String> lines) {
        for (String line : lines) {
            Matcher wm = PAT_WORLD_TYPE.matcher(line);
            if (wm.find()) return normalizeWorld(wm.group(1));
        }
        for (String line : lines) {
            String l = line.toLowerCase().trim();
            // Убраны строгие equals — теперь startsWith, чтобы работали "Ад:", "Ад," и т.п.
            if (l.startsWith("ад") || l.equals("nether") || l.startsWith("нижн"))   return "Ад";
            if (l.startsWith("энд") || l.equals("end")   || l.startsWith("конец"))  return "Энд";
            if (l.startsWith("мир") || l.contains("верхнего") || l.equals("overworld")) return "Мир";
        }
        return "Мир";
    }

    private String parseType(String line) {
        if (line == null || line.isEmpty()) return null;
        Matcher wm = PAT_WORLD_TYPE.matcher(line);
        if (wm.find()) {
            String world = normalizeWorld(wm.group(1));
            String type  = normalizeType(wm.group(2));
            return world.equals("Мир") ? type : world + " · " + type;
        }
        Matcher m = PAT_TYPE.matcher(line);
        if (m.find()) return normalizeType(m.group(1));
        return null;
    }

    private int parseTime(String line) {
        Matcher m = PAT_MIN_SEC.matcher(line);
        if (m.find()) return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        m = PAT_SEC.matcher(line);
        if (m.find()) { int s = Integer.parseInt(m.group(1)); if (s >= 1 && s <= 600) return s; }
        return -1;
    }

    private String normalizeWorld(String w) {
        String l = w.toLowerCase().trim();
        if (l.contains("ад") || l.contains("nether")) return "Ад";
        if (l.contains("энд") || l.contains("end"))   return "Энд";
        return "Мир";
    }

    private String normalizeType(String t) {
        String l = t.toLowerCase();
        if (l.startsWith("легенд")) return "Легендарная";
        if (l.startsWith("мифич"))  return "Мифическая";
        if (l.startsWith("эпич"))   return "Эпическая";
        return "Обычная";
    }

    @SubscribeEvent
    public void onChatMessage(ClientChatReceivedEvent event) {
        String text = stripColors(event.getMessage().getString());
        if (text == null || text.isEmpty()) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter("C:\\mine_alert\\chat_dump.txt", true))) {
            pw.println("TYPE=" + event.getType().name() + " MSG='" + text + "'");
        } catch (Exception ignored) {}
    }

    private void tryParseMode(String text) {
        Matcher m = PAT_MODE.matcher(text);
        if (m.find()) {
            String mode = m.group(1).trim();
            if (!mode.isEmpty() && !mode.equals(MineData.getAnarchyMode()))
                MineData.onAnarchyChanged(mode);
        }
    }

    public static String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-or]", "").trim();
    }
}
