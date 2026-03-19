package ru.minewatch.mod;

import java.util.*;

public class MineData {

    public static class ShaftEntry {
        public String world;
        public String currentType = "";
        public String nextType    = "";
        public int    storedSecs  = -1;
        public long   updateMs    = 0;
        public long   typeUpdateMs = 0; // last time currentType was set by scoreboard/Gist
        public boolean[] alerted  = new boolean[ALERT_THRESHOLDS.length];

        public int getRealSecs() {
            if (storedSecs < 0) return -1;
            long elapsed = (System.currentTimeMillis() - updateMs) / 1000;
            return (int) Math.max(0, storedSecs - elapsed);
        }
        public boolean hasTime() { return storedSecs >= 0; }
    }

    public static class MineEntry {
        public String mode;
        public final Map<String, ShaftEntry> shafts = new java.util.concurrent.ConcurrentHashMap<>();
        public long lastSeenMs = System.currentTimeMillis();
        public boolean remoteOnly = true;

        public boolean hasAnyTime() {
            for (ShaftEntry s : shafts.values()) if (s.hasTime()) return true;
            return false;
        }

        public int getMinSecs() {
            int min = Integer.MAX_VALUE;
            for (ShaftEntry s : shafts.values()) {
                int t = s.getRealSecs();
                if (t > 0 && t < min) min = t;
            }
            return min == Integer.MAX_VALUE ? -1 : min;
        }

        public ShaftEntry getMainShaft() {
            if (shafts.containsKey("Мир")) return shafts.get("Мир");
            if (!shafts.isEmpty()) return shafts.values().iterator().next();
            return null;
        }

        public ShaftEntry getOrCreateShaft(String world) {
            return shafts.computeIfAbsent(world, k -> {
                ShaftEntry s = new ShaftEntry();
                s.world = k;
                return s;
            });
        }

        public int bestRank() {
            int r = 0;
            for (ShaftEntry s : shafts.values()) {
                r = Math.max(r, typeRank(s.currentType));
                r = Math.max(r, typeRank(s.nextType));
            }
            return r;
        }
        private static int typeRank(String t) {
            if (t == null) return 0;
            String l = t.toLowerCase();
            if (l.contains("легенд")) return 3;
            if (l.contains("мифич"))  return 2;
            if (l.contains("эпич"))   return 1;
            return 0;
        }

        public java.util.LinkedHashMap<String, ShaftEntry> getSortedShafts() {
            java.util.LinkedHashMap<String, ShaftEntry> sorted = new java.util.LinkedHashMap<>();
            for (String w : new String[]{"Мир", "Ад", "Энд"}) {
                if (shafts.containsKey(w)) sorted.put(w, shafts.get(w));
            }
            for (Map.Entry<String, ShaftEntry> e : shafts.entrySet()) {
                if (!sorted.containsKey(e.getKey())) sorted.put(e.getKey(), e.getValue());
            }
            return sorted;
        }

        public boolean hasEpicOrBetter() {
            for (ShaftEntry s : shafts.values()) {
                if (isEpicOrBetter(s.currentType) || isEpicOrBetter(s.nextType)) return true;
            }
            return false;
        }
        public boolean hasLegendary() {
            for (ShaftEntry s : shafts.values()) {
                if (isLegendary(s.currentType) || isLegendary(s.nextType)) return true;
            }
            return false;
        }
        public boolean hasEpicOnly() {
            for (ShaftEntry s : shafts.values()) {
                if (isEpic(s.currentType) || isEpic(s.nextType)) return true;
            }
            return false;
        }
        private static boolean isLegendary(String t) { return t != null && t.toLowerCase().contains("легенд"); }
        private static boolean isEpic(String t)       { return t != null && t.toLowerCase().contains("эпич"); }
        private static boolean isEpicOrBetter(String t) { return isLegendary(t) || isEpic(t) || isMythic(t); }
        private static boolean isMythic(String t)     { return t != null && t.toLowerCase().contains("мифич"); }
    }

    public static final int[] ALERT_THRESHOLDS = {60, 30, 10};
    private static final int STALE_SECONDS = 600;

    private static final java.util.concurrent.ConcurrentHashMap<String, MineEntry> entries = new java.util.concurrent.ConcurrentHashMap<>();
    private static String  currentMode = "";
    private static boolean paused      = false;
    private static int     maxEntries  = 10;
    // 0=all 1=epic+ 2=leg 3=epic
    private static int filterMode  = 1;
    private static boolean globalMode = true;
    public static boolean isGlobal()    { return globalMode; }
    public static void toggleGlobal()   { globalMode = !globalMode; }
    public static int  getFilterMode()  { return filterMode; }
    public static void cycleFilter()    { filterMode = (filterMode + 1) % 4; }

    private static int hudX = 10, hudY = 10;
    private static boolean dragging = false;
    private static int dragOffsetX, dragOffsetY;
    private static boolean hudVisible = true;
    public static boolean isHudVisible()    { return hudVisible; }
    public static void toggleHudVisible()   { hudVisible = !hudVisible; }

    // Таймер обнулился → пишем trigger.txt для userbot
    private static final java.util.Set<String> triggeredZero =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static void checkZeroTrigger() {
        if (currentMode == null || currentMode.isEmpty()) return;
        MineEntry e = entries.get(currentMode);
        if (e == null) return;
        boolean writeTrigger = false;
        for (ShaftEntry s : e.shafts.values()) {
            String key = currentMode + "|" + s.world;
            int secs = s.getRealSecs();
            if (secs < 0) { triggeredZero.remove(key); continue; }
            if (secs > 60) { triggeredZero.remove(key); continue; }
            if (secs <= 5 && !triggeredZero.contains(key)) {
                triggeredZero.add(key);
                writeTrigger = true;
            }
        }
        if (writeTrigger) writeTriggerFile();
    }

    private static void writeTriggerFile() {
        try {
            java.io.File f = new java.io.File("C:\\mine_alert\\trigger.txt");
            f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(),
                Long.toString(System.currentTimeMillis()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private static final Map<String, Long> legendAlertSent = new HashMap<>();
    private static String legendAlertText = null;
    private static long   legendAlertUntil = 0;

    public static String getAnarchyMode() { return currentMode; }

    public static void onAnarchyChanged(String newMode) {
        currentMode = newMode;
        if (!paused) {
            MineEntry e = getOrCreate(newMode);
            e.lastSeenMs = System.currentTimeMillis();
            e.remoteOnly = false;
            // При заходе на любой сервер — сразу запросить свежие данные
            ApiSync.pullNow();
            TelegramSync.triggerNow();
        }
    }

    public static boolean isPaused()     { return paused; }
    public static void togglePause()     { paused = !paused; }
    public static int  getMaxEntries()   { return maxEntries; }
    public static void cycleMaxEntries() {
        maxEntries = maxEntries == 5 ? 10 : maxEntries == 10 ? 15 : 5;
    }
    private static boolean deleteMode = false;
    public static boolean isDeleteMode()   { return deleteMode; }
    public static void toggleDeleteMode()  { deleteMode = !deleteMode; }
    public static void exitDeleteMode()    { deleteMode = false; }

    public static void removeEntry(String mode) {
        if (mode != null && !mode.equals(currentMode)) entries.remove(mode);
    }

    public static void resetAll() {
        entries.clear(); currentMode = ""; paused = false; legendAlertText = null; deleteMode = false;
    }

    /** Вызывать при выходе из мира (mc.level == null), чтобы перезаход на тот же сервер
     *  снова вызвал onAnarchyChanged и обновил данные. */
    public static void clearCurrentMode() {
        currentMode = "";
    }

    private static synchronized MineEntry getOrCreate(String mode) {
        if (entries.containsKey(mode)) return entries.get(mode);
        if (entries.size() >= maxEntries) {
            String oldest = null; long oldMs = Long.MAX_VALUE;
            for (Map.Entry<String, MineEntry> en : entries.entrySet()) {
                if (!en.getKey().equals(currentMode) && en.getValue().lastSeenMs < oldMs) {
                    oldMs = en.getValue().lastSeenMs; oldest = en.getKey();
                }
            }
            if (oldest != null) entries.remove(oldest);
        }
        return entries.computeIfAbsent(mode, k -> { MineEntry e = new MineEntry(); e.mode = k; return e; });
    }

    public static MineEntry getEntry(String mode) {
        return entries.get(mode);
    }

    public static void setRemoteData(String server, String world, String cur, String nxt, int secs) {
        if (server == null || server.isEmpty()) return;
        // Dispatch to main client thread to avoid race with HUD renderer
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(() -> setRemoteData(server, world, cur, nxt, secs));
            return;
        }
        if (server.equals(currentMode)) return;
        MineEntry entry = getOrCreate(server);
        ShaftEntry shaft = entry.getOrCreateShaft(world != null && !world.isEmpty() ? world : "Мир");
        // Only overwrite types for remote-only entries; player-loaded data takes priority
        if (entry.remoteOnly) {
            if (cur != null) { shaft.currentType = cur; shaft.typeUpdateMs = System.currentTimeMillis(); }
            if (nxt != null) shaft.nextType = nxt;
        }
        if (secs >= 0) {
            shaft.storedSecs = secs;
            shaft.updateMs   = System.currentTimeMillis();
        }
        entry.lastSeenMs = System.currentTimeMillis();
    }

    public static void setTimeForWorld(String mode, String world, int seconds) {
        if (paused) return;
        String key = (mode == null || mode.isEmpty()) ? currentMode : mode;
        if (key == null || key.isEmpty()) return;
        MineEntry entry = getOrCreate(key);
        entry.remoteOnly = false;
        ShaftEntry shaft = entry.getOrCreateShaft(world);
        int realSecs = shaft.getRealSecs();
        // Обновляем якорь таймера только при реальном изменении:
        // первое чтение, скачок вверх (сброс шахты) или значительное расхождение (>15 сек).
        // Иначе таймер не отсчитывает — updateMs сбрасывается каждый тик при том же значении.
        boolean shouldUpdate = (realSecs < 0)
                || (seconds > realSecs + 5)
                || (realSecs > seconds + 15);
        if (shouldUpdate) {
            if (shaft.storedSecs >= 0 && seconds > shaft.storedSecs + 10)
                shaft.alerted = new boolean[ALERT_THRESHOLDS.length];
            shaft.storedSecs = seconds;
            shaft.updateMs   = System.currentTimeMillis();
        }
        entry.lastSeenMs = System.currentTimeMillis();
        checkLegendAlerts();
    }

    public static void setCurrentMineForWorld(String world, String type) {
        if (paused || currentMode.isEmpty()) return;
        MineEntry entry = getOrCreate(currentMode);
        ShaftEntry s = entry.getOrCreateShaft(world);
        s.currentType = type;
        s.typeUpdateMs = System.currentTimeMillis();
        entry.lastSeenMs = System.currentTimeMillis();
    }

    public static void setNextMineForWorld(String world, String type) {
        if (paused || currentMode.isEmpty()) return;
        MineEntry entry = getOrCreate(currentMode);
        entry.getOrCreateShaft(world).nextType = type;
        entry.lastSeenMs = System.currentTimeMillis();
        checkLegendAlerts();
    }

    public static void setCurrentMine(String type) {
        if (paused || currentMode.isEmpty()) return;
        setCurrentMineForWorld(extractWorld(type), type);
    }
    public static void setNextMine(String type) {
        if (paused || currentMode.isEmpty()) return;
        setNextMineForWorld(extractWorld(type), type);
    }
    public static void setTimeSecondsForMode(String mode, int seconds) {
        setTimeForWorld(mode, "Мир", seconds);
    }

    public static String extractWorld(String type) {
        if (type == null)              return "Мир";
        if (type.startsWith("Ад"))     return "Ад";
        if (type.startsWith("Энд"))    return "Энд";
        return "Мир";
    }

    public static boolean hasData() {
        if (currentMode.isEmpty()) return false;
        MineEntry e = entries.get(currentMode);
        return e != null && e.hasAnyTime();
    }

    public static int getRealTimeSeconds() {
        if (currentMode.isEmpty()) return -1;
        MineEntry e = entries.get(currentMode);
        return e == null ? -1 : e.getMinSecs();
    }

    public static List<MineEntry> getSortedEntries() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(en ->
            !en.getKey().equals(currentMode)
            && en.getValue().hasAnyTime()
            && (now - en.getValue().lastSeenMs) > STALE_SECONDS * 1000L
        );
        List<MineEntry> list = new ArrayList<>(entries.values());
        if (!globalMode) list.removeIf(e -> e.remoteOnly && !e.mode.equals(currentMode));
        if (filterMode != 0) {
            list.removeIf(e -> {
                if (e.mode.equals(currentMode)) return false; // never hide active server
                switch (filterMode) {
                    case 1: return !e.hasEpicOrBetter();
                    case 2: return !e.hasLegendary();
                    case 3: return !e.hasEpicOnly();
                    default: return false;
                }
            });
        }
        // SORT: snapshot timers once to prevent comparator inconsistency when a second ticks mid-sort
        java.util.Map<String,Integer> secsSnap = new java.util.HashMap<>();
        for (MineEntry e : list) secsSnap.put(e.mode, e.getMinSecs());
        list.sort((a, b) -> {
            if (a.mode.equals(currentMode)) return -1;
            if (b.mode.equals(currentMode)) return 1;
            int ta = secsSnap.getOrDefault(a.mode, -1), tb = secsSnap.getOrDefault(b.mode, -1);
            if (ta < 0 && tb < 0) return a.mode.compareTo(b.mode);
            if (ta < 0) return 1; if (tb < 0) return -1;
            int ba = ta / 30, bb = tb / 30; // 30-sec buckets — same bucket = alphabetical, stable
            int cmp = Integer.compare(ba, bb);
            return cmp != 0 ? cmp : a.mode.compareTo(b.mode);
        });
        return list;
    }

    private static void checkLegendAlerts() {
        long now = System.currentTimeMillis();
        for (MineEntry e : entries.values()) {
            if (e.mode.equals(currentMode)) continue;
            for (ShaftEntry s : e.shafts.values()) {
                if (!isLegendary(s.nextType)) continue;
                int secs = s.getRealSecs();
                if (secs <= 0 || (secs != 30 && secs != 10 && secs > 10)) continue;
                boolean at30 = secs >= 28 && secs <= 32;
                boolean at10 = secs >= 8  && secs <= 12;
                if (!at30 && !at10) continue;
                String alertKey = e.mode + "|" + s.world + "|" + (at30 ? "30" : "10");
                Long lastSent = legendAlertSent.get(alertKey);
                if (lastSent != null && now - lastSent < 15_000) continue;
                legendAlertSent.put(alertKey, now);
                legendAlertText  = e.mode + "  " + s.world + "  через " + fmt(secs);
                legendAlertUntil = now + 7_000;
            }
        }
    }

    public static boolean hasLegendAlert() {
        return legendAlertText != null && System.currentTimeMillis() < legendAlertUntil;
    }
    public static String getLegendAlertText() { return legendAlertText; }

    private static boolean isLegendary(String type) {
        return type != null && type.toLowerCase().contains("легенд");
    }

    public static int  getHudX()       { return hudX; }
    public static int  getHudY()       { return hudY; }
    public static boolean isDragging() { return dragging; }
    public static void startDrag(int mx, int my) {
        dragging = true; dragOffsetX = mx - hudX; dragOffsetY = my - hudY;
    }
    public static void updateDrag(int mx, int my) {
        if (dragging) { hudX = mx - dragOffsetX; hudY = my - dragOffsetY; }
    }
    public static void stopDrag() { dragging = false; }

    public static boolean shouldAlert(int i) {
        MineEntry e = entries.get(currentMode);
        if (e == null) return false;
        ShaftEntry minShaft = null; int minSecs = Integer.MAX_VALUE;
        for (ShaftEntry s : e.shafts.values()) {
            int t = s.getRealSecs();
            if (t >= 0 && t < minSecs) { minSecs = t; minShaft = s; }
        }
        if (minShaft == null || i < 0 || i >= minShaft.alerted.length) return false;
        if (!minShaft.alerted[i]) { minShaft.alerted[i] = true; return true; }
        return false;
    }

    public static String fmt(int s) {
        if (s < 0) return "--:--";
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
