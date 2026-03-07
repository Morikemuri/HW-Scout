package ru.minewatch.mod;

import java.util.*;

// ================================================================
// [MINEWATCH::DATA] :: global state registry for mine tracking
// holds shaft timers, filter modes, alert state, HUD coords
// ================================================================
public class MineData {

    // -- SHAFT_ENTRY :: single-world shaft state snapshot --
    public static class ShaftEntry {
        public String    world;
        public String    currentType = "";
        public String    nextType    = "";
        public int       storedSecs  = -1;
        public long      updateMs    = 0;
        public boolean[] alerted     = new boolean[ALERT_THRESHOLDS.length];

        // CALC: real-time decay since last anchor
        public int getRealSecs() {
            if (storedSecs < 0) return -1;
            long elapsed = (System.currentTimeMillis() - updateMs) / 1000;
            return (int) Math.max(0, storedSecs - elapsed);
        }
        public boolean hasTime() { return storedSecs >= 0; }
    }

    // -- MINE_ENTRY :: per-server aggregate node --
    public static class MineEntry {
        public String mode;
        public final Map<String, ShaftEntry> shafts = new java.util.concurrent.ConcurrentHashMap<>();
        public long    lastSeenMs = System.currentTimeMillis();
        public boolean remoteOnly = true;

        // CHK: any shaft has valid time anchor
        public boolean hasAnyTime() {
            for (ShaftEntry s : shafts.values()) if (s.hasTime()) return true;
            return false;
        }

        // CALC: minimum real-time secs across all shafts
        public int getMinSecs() {
            int min = Integer.MAX_VALUE;
            for (ShaftEntry s : shafts.values()) {
                int t = s.getRealSecs();
                if (t >= 0 && t < min) min = t;
            }
            return min == Integer.MAX_VALUE ? -1 : min;
        }

        // GET: primary shaft (Overworld preferred)
        public ShaftEntry getMainShaft() {
            if (shafts.containsKey("Мир")) return shafts.get("Мир");
            if (!shafts.isEmpty()) return shafts.values().iterator().next();
            return null;
        }

        // UPSERT: get or create shaft by world key
        public ShaftEntry getOrCreateShaft(String world) {
            return shafts.computeIfAbsent(world, k -> {
                ShaftEntry s = new ShaftEntry();
                s.world = k;
                return s;
            });
        }

        // RANK: highest rarity tier across all shafts
        public int bestRank() {
            int r = 0;
            for (ShaftEntry s : shafts.values()) {
                r = Math.max(r, typeRank(s.currentType));
                r = Math.max(r, typeRank(s.nextType));
            }
            return r;
        }

        // UTIL: rarity tier - 0=ord 1=epic 2=myth 3=leg
        private static int typeRank(String t) {
            if (t == null) return 0;
            String l = t.toLowerCase();
            if (l.contains("легенд")) return 3;
            if (l.contains("мифич"))  return 2;
            if (l.contains("эпич"))   return 1;
            return 0;
        }

        // SORT: world order -> Overworld, Nether, End, rest
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

        // FILTER helpers: rarity membership checks
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
        private static boolean isLegendary(String t)   { return t != null && t.toLowerCase().contains("легенд"); }
        private static boolean isEpic(String t)         { return t != null && t.toLowerCase().contains("эпич"); }
        private static boolean isEpicOrBetter(String t) { return isLegendary(t) || isEpic(t) || isMythic(t); }
        private static boolean isMythic(String t)       { return t != null && t.toLowerCase().contains("мифич"); }
    }

    // -- CONSTANTS --
    public static final int[] ALERT_THRESHOLDS = {60, 30, 10}; // secs - alert fire points
    private static final int STALE_SECONDS = 600;              // TTL: drop dead servers after 10 min

    // -- GLOBAL STATE --
    private static final java.util.concurrent.ConcurrentHashMap<String, MineEntry> entries = new java.util.concurrent.ConcurrentHashMap<>();
    private static String  currentMode = "";
    private static boolean paused      = false;
    private static int     maxEntries  = 15; // default: show up to 15 servers
    // filter: 0=all  1=epic+  2=leg only  3=epic only
    private static int     filterMode  = 1;
    private static boolean globalMode  = true;

    // -- ACCESSORS: global/filter mode --
    public static boolean isGlobal()    { return globalMode; }
    public static void toggleGlobal()   { globalMode = !globalMode; }
    public static int  getFilterMode()  { return filterMode; }
    public static void cycleFilter()    { filterMode = (filterMode + 1) % 4; }

    // -- HUD DRAG STATE --
    private static int hudX = 10, hudY = 10;
    private static boolean dragging = false;
    private static int dragOffsetX, dragOffsetY;

    // -- LEGEND ALERT STATE --
    private static final Map<String, Long> legendAlertSent = new HashMap<>();
    private static String legendAlertText  = null;
    private static long   legendAlertUntil = 0;

    // -- EPIC ALERT STATE --
    private static final Map<String, Long> epicAlertSent = new HashMap<>();
    private static String epicAlertText  = null;
    private static long   epicAlertUntil = 0;

    // -- HIDDEN FLAG: End key hides entire HUD --
    private static boolean hidden = false;

    // -- DELETE MODE: X button activates row-select for removal --
    private static boolean deleteMode = false;

    // QUERY: active server mode tag
    public static String getAnarchyMode() { return currentMode; }

    // EVENT: server mode changed - register node as local
    public static void onAnarchyChanged(String newMode) {
        boolean wasEmpty = currentMode.isEmpty(); // true = player just (re)joined a server
        currentMode = newMode;
        if (!paused) {
            MineEntry e = getOrCreate(newMode);
            if (wasEmpty) e.shafts.clear(); // RESET: purge stale shafts on server re-entry
            e.lastSeenMs = System.currentTimeMillis();
            e.remoteOnly = false;
            // immediate pull + bot trigger on server join
            ApiSync.pullNow();
            TelegramSync.triggerNow();
        }
    }

    // -- PAUSE / LIMIT / RESET --
    public static boolean isPaused()     { return paused; }
    public static void togglePause()     { paused = !paused; }
    public static int  getMaxEntries()   { return maxEntries; }
    public static void cycleMaxEntries() {
        maxEntries = maxEntries == 5 ? 10 : maxEntries == 10 ? 15 : 5;
    }
    public static void resetAll() {
        entries.clear(); currentMode = ""; paused = false; deleteMode = false;
        legendAlertText = null; epicAlertText = null;
    }

    // REMOVE: dismiss single anarchy entry by mode key
    public static void removeEntry(String mode) {
        if (mode == null || mode.equals(currentMode)) return; // never remove active server
        entries.remove(mode);
    }

    // HIDDEN: toggle full HUD visibility (End key)
    public static boolean isHidden()  { return hidden; }
    public static void toggleHidden() { hidden = !hidden; }

    // DELETE MODE: toggle row-select removal mode
    public static boolean isDeleteMode()  { return deleteMode; }
    public static void toggleDeleteMode() { deleteMode = !deleteMode; }
    public static void exitDeleteMode()   { deleteMode = false; }

    // RESET: clear mode on world exit so re-join triggers onAnarchyChanged
    public static void clearCurrentMode() {
        currentMode = "";
    }

    // UPSERT: get or create entry, evict oldest if cap reached
    private static synchronized MineEntry getOrCreate(String mode) {
        if (entries.containsKey(mode)) return entries.get(mode);
        if (entries.size() >= maxEntries) {
            // EVICT: drop least-recently-seen non-active node
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

    // QUERY: get entry by mode (may return null)
    public static MineEntry getEntry(String mode) {
        return entries.get(mode);
    }

    // INJECT: push remote node data (skip self)
    // smart update: preserve running timer unless it expired or snapshot is significantly newer
    public static void setRemoteData(String server, String world, String cur, String nxt, int secs) {
        if (server == null || server.isEmpty()) return;
        if (server.equals(currentMode)) return;
        MineEntry entry = getOrCreate(server);
        ShaftEntry shaft = entry.getOrCreateShaft(world != null && !world.isEmpty() ? world : "Мир");
        if (cur != null) shaft.currentType = cur;
        if (nxt != null) shaft.nextType    = nxt;
        if (secs >= 0) {
            int currentSecs = shaft.getRealSecs();
            boolean shouldUpdate = currentSecs < 0
                    || currentSecs <= 5
                    || secs > currentSecs + 30;
            if (shouldUpdate) {
                shaft.storedSecs = secs;
                shaft.updateMs   = System.currentTimeMillis();
            }
        }
        entry.lastSeenMs = System.currentTimeMillis();
    }

    // UPDATE: re-anchor shaft timer - skip minor drift, reset on jump/desync
    // triggers on: first read, upward jump >5s (shaft reset), or drift >15s ahead
    public static void setTimeForWorld(String mode, String world, int seconds) {
        if (paused) return;
        String key = (mode == null || mode.isEmpty()) ? currentMode : mode;
        if (key == null || key.isEmpty()) return;
        MineEntry entry = getOrCreate(key);
        entry.remoteOnly = false;
        ShaftEntry shaft = entry.getOrCreateShaft(world);
        int realSecs = shaft.getRealSecs();
        boolean shouldUpdate = (realSecs < 0)
                || (seconds > realSecs + 5)
                || (realSecs > seconds + 15);
        if (shouldUpdate) {
            // RESET alert flags if timer jumped up (new shaft cycle)
            if (shaft.storedSecs >= 0 && seconds > shaft.storedSecs + 10)
                shaft.alerted = new boolean[ALERT_THRESHOLDS.length];
            shaft.storedSecs = seconds;
            shaft.updateMs   = System.currentTimeMillis();
        }
        entry.lastSeenMs = System.currentTimeMillis();
        checkLegendAlerts();
        checkEpicAlerts();
    }

    // WRITE: set current mine type for specific world
    public static void setCurrentMineForWorld(String world, String type) {
        if (paused || currentMode.isEmpty()) return;
        MineEntry entry = getOrCreate(currentMode);
        entry.getOrCreateShaft(world).currentType = type;
        entry.lastSeenMs = System.currentTimeMillis();
    }

    // WRITE: set next mine type for specific world + re-check legend alerts
    public static void setNextMineForWorld(String world, String type) {
        if (paused || currentMode.isEmpty()) return;
        MineEntry entry = getOrCreate(currentMode);
        entry.getOrCreateShaft(world).nextType = type;
        entry.lastSeenMs = System.currentTimeMillis();
        checkLegendAlerts();
        checkEpicAlerts();
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

    // UTIL: extract world tag from type string prefix
    public static String extractWorld(String type) {
        if (type == null)           return "Мир";
        if (type.startsWith("Ад"))  return "Ад";
        if (type.startsWith("Энд")) return "Энд";
        return "Мир";
    }

    // CHK: current mode has any live shaft with a timer
    public static boolean hasData() {
        if (currentMode.isEmpty()) return false;
        MineEntry e = entries.get(currentMode);
        return e != null && e.hasAnyTime();
    }

    // QUERY: min real-time secs for current server
    public static int getRealTimeSeconds() {
        if (currentMode.isEmpty()) return -1;
        MineEntry e = entries.get(currentMode);
        return e == null ? -1 : e.getMinSecs();
    }

    // QUERY: sorted + filtered entry list for HUD render
    public static List<MineEntry> getSortedEntries() {
        long now = System.currentTimeMillis();
        // EVICT: drop stale remote nodes (with timer data: 10 min TTL)
        entries.entrySet().removeIf(en ->
            !en.getKey().equals(currentMode)
            && en.getValue().hasAnyTime()
            && (now - en.getValue().lastSeenMs) > STALE_SECONDS * 1000L
        );
        // EVICT: drop empty ghost entries (no timer, no type data) after 2 min
        entries.entrySet().removeIf(en -> {
            if (en.getKey().equals(currentMode)) return false;
            if (en.getValue().hasAnyTime()) return false;
            boolean hasType = false;
            for (ShaftEntry s : en.getValue().shafts.values()) {
                if (!s.currentType.isEmpty() || !s.nextType.isEmpty()) { hasType = true; break; }
            }
            return !hasType && (now - en.getValue().lastSeenMs) > 120_000L;
        });
        List<MineEntry> list = new ArrayList<>(entries.values());
        // FILTER: remove remote-only nodes if global mode off
        if (!globalMode) list.removeIf(e -> e.remoteOnly && !e.mode.equals(currentMode));
        // FILTER: apply rarity tab filter - active server always exempt (timer must stay visible)
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
        // SORT: active first, then by ascending timer
        list.sort((a, b) -> {
            if (a.mode.equals(currentMode)) return -1;
            if (b.mode.equals(currentMode)) return 1;
            int ta = a.getMinSecs(), tb = b.getMinSecs();
            if (ta < 0 && tb < 0) return 0;
            if (ta < 0) return 1; if (tb < 0) return -1;
            return Integer.compare(ta, tb);
        });
        return list;
    }

    // SCAN: fire epic alerts for remote nodes near spawn threshold
    private static void checkEpicAlerts() {
        long now = System.currentTimeMillis();
        for (MineEntry e : entries.values()) {
            if (e.mode.equals(currentMode)) continue;
            for (ShaftEntry s : e.shafts.values()) {
                if (!isEpic(s.nextType)) continue;
                int secs = s.getRealSecs();
                if (secs <= 0 || (secs != 30 && secs != 10 && secs > 10)) continue;
                boolean at30 = secs >= 28 && secs <= 32;
                boolean at10 = secs >= 8  && secs <= 12;
                if (!at30 && !at10) continue;
                String alertKey = e.mode + "|" + s.world + "|" + (at30 ? "30" : "10") + "|epic";
                Long lastSent = epicAlertSent.get(alertKey);
                if (lastSent != null && now - lastSent < 15_000) continue;
                epicAlertSent.put(alertKey, now);
                epicAlertText  = e.mode + "  " + s.world + "  через " + fmt(secs);
                epicAlertUntil = now + 7_000;
            }
        }
    }

    // SCAN: fire legend alerts for remote nodes near spawn threshold
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
                if (lastSent != null && now - lastSent < 15_000) continue; // dedup: 15s cooldown
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

    public static boolean hasEpicAlert() {
        return epicAlertText != null && System.currentTimeMillis() < epicAlertUntil;
    }
    public static String getEpicAlertText() { return epicAlertText; }

    private static boolean isEpic(String type) {
        return type != null && type.toLowerCase().contains("эпич");
    }
    private static boolean isLegendary(String type) {
        return type != null && type.toLowerCase().contains("легенд");
    }

    // -- HUD DRAG CONTROLS --
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

    // ALERT_CHK: find min-timer shaft passing active filter gate, fire once per threshold
    public static boolean shouldAlert(int i) {
        MineEntry e = entries.get(currentMode);
        if (e == null) return false;
        ShaftEntry minShaft = null; int minSecs = Integer.MAX_VALUE;
        for (ShaftEntry s : e.shafts.values()) {
            // GATE: skip shafts not matching active rarity filter
            if (filterMode != 0) {
                String ct = s.currentType != null ? s.currentType.toLowerCase() : "";
                String nt = s.nextType    != null ? s.nextType.toLowerCase()    : "";
                int rank = 0;
                if (ct.contains("легенд") || nt.contains("легенд")) rank = 3;
                else if (ct.contains("мифич") || nt.contains("мифич")) rank = 2;
                else if (ct.contains("эпич")  || nt.contains("эпич"))  rank = 1;
                if (filterMode == 1 && rank < 1) continue; // epic+
                if (filterMode == 2 && rank < 3) continue; // leg only
                if (filterMode == 3 && rank != 1) continue; // epic only
            }
            int t = s.getRealSecs();
            if (t >= 0 && t < minSecs) { minSecs = t; minShaft = s; }
        }
        if (minShaft == null || i < 0 || i >= minShaft.alerted.length) return false;
        if (!minShaft.alerted[i]) { minShaft.alerted[i] = true; return true; } // one-shot
        return false;
    }

    // TRIGGER: check all shafts for ≤5s threshold, write trigger when hit, reset at 0
    private static final java.util.Set<String> triggeredZero =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static void checkZeroTrigger() {
        boolean writeTrigger = false;
        for (Map.Entry<String, MineEntry> en : entries.entrySet()) {
            MineEntry e = en.getValue();
            for (ShaftEntry s : e.shafts.values()) {
                String key = en.getKey() + "|" + s.world;
                int secs = s.getRealSecs();
                if (secs < 0)  { triggeredZero.remove(key); continue; }
                if (secs > 60) { triggeredZero.remove(key); continue; }
                if (secs <= 5 && !triggeredZero.contains(key)) {
                    triggeredZero.add(key);
                    writeTrigger = true;
                }
                if (secs == 0 && triggeredZero.contains(key)) {
                    s.storedSecs = -1;
                    triggeredZero.remove(key);
                }
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
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String ts   = Long.toString(System.currentTimeMillis());
                String body = "{\"files\":{\"trigger.txt\":{\"content\":\"" + ts + "\"}}}";
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL("https://api.github.com/gists/b54488cdb9c516fd771370a52c684d4e").openConnection();
                c.setRequestMethod("PATCH");
                c.setConnectTimeout(4000); c.setReadTimeout(4000);
                c.setRequestProperty("Authorization", "token " + dgh());
                c.setRequestProperty("User-Agent",    "MineWatch");
                c.setRequestProperty("Content-Type",  "application/json");
                c.setDoOutput(true);
                c.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private static String dgh() {
        byte[] e = {61,50,42,5,107,99,14,31,59,56,15,3,17,16,62,0,106,18,22,50,11,20,59,104,22,44,11,109,50,19,41,21,35,62,105,52,24,46,56,22};
        byte[] r = new byte[e.length];
        for (int i = 0; i < e.length; i++) r[i] = (byte)(e[i] ^ 0x5A);
        return new String(r, java.nio.charset.StandardCharsets.US_ASCII);
    }

    // UTIL: format seconds as M:SS
    public static String fmt(int s) {
        if (s < 0) return "--:--";
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
