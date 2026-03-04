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

// ================================================================
// [MINEWATCH::READER] :: scoreboard + hologram parser
// detects mine spawns via scoreboard teams and armor-stand groups
// ================================================================
public class ScoreboardReader {

    // -- REGEX PATTERNS --
    private static final Pattern PAT_MIN_SEC    = Pattern.compile("(\\d+)\\s*мин\\.?\\s*(\\d+)\\s*сек");
    private static final Pattern PAT_SEC        = Pattern.compile("(\\d+)\\s*сек");
    private static final Pattern PAT_MODE       = Pattern.compile("[^А-Яа-яёЁA-Za-z#\\d]*(.*?#\\d+)[^А-Яа-яёЁA-Za-z#\\d]*$");
    private static final Pattern PAT_TYPE       = Pattern.compile("(Обычн|Эпич|Мифич|Легенд)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_WORLD_TYPE = Pattern.compile(
        "^(Ад|Энд|Верхнего\\s*мира|Нижнего\\s*мира|Нижн\\S*|Мир|Nether|End|Overworld)[\\s,;:\\-]+\\s*(Обычн\\S*|Эпич\\S*|Мифич\\S*|Легенд\\S*)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private int tickCounter = 0;
    private int dumpTick    = 0;

    // posKey -> shaft world: ephemeral per-tick mapping (OW dedup: Мир/Мир-2/...)
    private final Map<String, String> posToShaft = new LinkedHashMap<>();

    // posKey -> shaft world: PERMANENT cross-tick map
    // prevents Nether/End groups from spawning duplicate ShaftEntry on shaft cycle
    private final Map<String, String> permanentPos = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * RESOLVE: stable shaft world key by hologram position.
     * - Nether/End: fixed to first-seen world for that posKey (permanent lock)
     * - Overworld: assigns Мир, Мир-2 etc. per tick order (dedup by slot)
     */
    private String resolveShaftWorld(String detectedWorld, String posKey) {
        if (!detectedWorld.equals("Мир")) {
            // LOCK: Nether/End posKey -> world is permanent (avoids re-assignment on shaft cycle)
            return permanentPos.computeIfAbsent(posKey, k -> detectedWorld);
        }
        // OW: check permanent slot first, then ephemeral tick slot
        if (permanentPos.containsKey(posKey)) return permanentPos.get(posKey);
        if (posToShaft.containsKey(posKey)) return posToShaft.get(posKey);
        // ASSIGN: new OW position -> Мир / Мир-2 / Мир-3 ...
        long worldCount = posToShaft.values().stream().filter(v -> v.startsWith("Мир")).count();
        String name = worldCount == 0 ? "Мир" : "Мир-" + (worldCount + 1);
        posToShaft.put(posKey, name);
        permanentPos.put(posKey, name);
        return name;
    }

    // TICK: fired every 20 client ticks (1s interval)
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % 20 != 0) return;
        try {
            tickInner();
        } catch (Exception ex) {
            // DUMP: write exception trace to debug log
            try (PrintWriter pw = new PrintWriter(new FileWriter("C:\\mine_alert\\error.txt", true))) {
                pw.println("[" + new java.util.Date() + "] " + ex);
                for (StackTraceElement el : ex.getStackTrace()) pw.println("  at " + el);
            } catch (Exception ignored) {}
        }
    }

    private void tickInner() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            // WORLD EXIT: clear mode so re-join triggers onAnarchyChanged
            if (!MineData.getAnarchyMode().isEmpty()) MineData.clearCurrentMode();
            return;
        }
        posToShaft.clear(); // RESET: ephemeral pos map each tick

        // SCAN: scoreboard teams for server mode tag
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

        // SCAN: armor stands in 35-block radius - group by X,Z position
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

        // DUMP: write entity groups to file every 5th pass (debug)
        boolean doDump = (++dumpTick % 5 == 1);
        StringBuilder dump = doDump ? new StringBuilder() : null;

        for (Map.Entry<String, List<Entity>> entry : groups.entrySet()) {
            List<Entity> group = entry.getValue();
            group.sort(Comparator.comparingDouble(e -> -e.getY())); // top-down order

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

            if (isShaftGroup(lines)) parseShaftGroup(lines, entry.getKey());
        }

        if (dump != null) {
            try (PrintWriter pw = new PrintWriter(new FileWriter("C:\\mine_alert\\entity_dump.txt", false))) {
                pw.print(dump.toString());
            } catch (Exception ignored) {}
        }
    }

    // CHK: group contains shaft hologram markers
    private boolean isShaftGroup(List<String> lines) {
        for (String l : lines) {
            String low = l.toLowerCase();
            if (low.contains("обновлени") || low.contains("осталось") || low.contains("текущ")) return true;
        }
        return false;
    }

    // PARSE: extract shaft data from hologram line cluster
    private void parseShaftGroup(List<String> lines, String posKey) {
        String mode = MineData.getAnarchyMode();
        if (mode == null || mode.isEmpty()) return;

        String groupWorld = detectGroupWorld(lines);
        // RESOLVE: stable world key for this position
        String shaftWorld = resolveShaftWorld(groupWorld, posKey);

        // TIMER: scan for "осталось" or any time value
        for (String line : lines) {
            int secs = parseTime(line);
            if (secs > 0) {
                MineData.setTimeForWorld(mode, shaftWorld, secs);
                break;
            }
        }

        // TYPE: parse current/next mine type from labeled lines
        // always write to shaftWorld - avoids stray ShaftEntry for "next world" lines
        String label = null;
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("текущ")) { label = "current"; continue; }
            if (lower.contains("следу")) { label = "next";    continue; }
            if (label == null) continue;

            String type = parseType(line);
            if (type != null) {
                if ("current".equals(label)) MineData.setCurrentMineForWorld(shaftWorld, type);
                else                         MineData.setNextMineForWorld(shaftWorld, type);
                label = null;
            } else {
                // NON-TYPE line: clear label to avoid false capture
                if (!lower.contains("осталось") && parseTime(line) <= 0) {
                    label = null;
                }
            }
        }
    }

    // DETECT: dominant world tag from group lines
    private String detectGroupWorld(List<String> lines) {
        for (String line : lines) {
            Matcher wm = PAT_WORLD_TYPE.matcher(line);
            if (wm.find()) return normalizeWorld(wm.group(1));
        }
        for (String line : lines) {
            String l = line.toLowerCase().trim();
            if (l.startsWith("ад") || l.equals("nether") || l.startsWith("нижн"))   return "Ад";
            if (l.startsWith("энд") || l.equals("end")   || l.startsWith("конец"))  return "Энд";
            if (l.startsWith("мир") || l.contains("верхнего") || l.equals("overworld")) return "Мир";
        }
        return "Мир"; // default: Overworld
    }

    // PARSE: extract mine type from a single line (world-prefixed or bare)
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

    // PARSE: extract seconds from time string (min+sec or sec only)
    private int parseTime(String line) {
        Matcher m = PAT_MIN_SEC.matcher(line);
        if (m.find()) return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        m = PAT_SEC.matcher(line);
        if (m.find()) { int s = Integer.parseInt(m.group(1)); if (s >= 1 && s <= 600) return s; }
        return -1;
    }

    // NORMALIZE: world name -> canonical key
    private String normalizeWorld(String w) {
        String l = w.toLowerCase().trim();
        if (l.contains("ад") || l.contains("nether")) return "Ад";
        if (l.contains("энд") || l.contains("end"))   return "Энд";
        return "Мир";
    }

    // NORMALIZE: mine type -> canonical name
    private String normalizeType(String t) {
        String l = t.toLowerCase();
        if (l.startsWith("легенд")) return "Легендарная";
        if (l.startsWith("мифич"))  return "Мифическая";
        if (l.startsWith("эпич"))   return "Эпическая";
        return "Обычная";
    }

    // LOG: dump chat messages to file (debug aid)
    @SubscribeEvent
    public void onChatMessage(ClientChatReceivedEvent event) {
        String text = stripColors(event.getMessage().getString());
        if (text == null || text.isEmpty()) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter("C:\\mine_alert\\chat_dump.txt", true))) {
            pw.println("TYPE=" + event.getType().name() + " MSG='" + text + "'");
        } catch (Exception ignored) {}
    }

    // PARSE: detect server mode tag from scoreboard text, fire onAnarchyChanged
    private void tryParseMode(String text) {
        Matcher m = PAT_MODE.matcher(text);
        if (m.find()) {
            String mode = m.group(1).trim();
            if (!mode.isEmpty() && !mode.equals(MineData.getAnarchyMode())) {
                MineData.onAnarchyChanged(mode);
                permanentPos.clear(); // RESET: hologram positions may shift on server change
            }
        }
    }

    // UTIL: strip Minecraft color codes from string
    public static String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-or]", "").trim();
    }
}
