package ru.minewatch.mod;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Polls Telegram for mine-state messages from @liteeventbot.
 *
 * Setup:
 *   1. Create a bot via @BotFather – get a token.
 *   2. Create a Telegram group, add @liteeventbot + your new bot as members.
 *   3. Get the group chat-id: send any message to the group, then open
 *      https://api.telegram.org/bot<TOKEN>/getUpdates and look for "chat":{"id":...}
 *   4. Create C:\mine_alert\tg_config.txt with:
 *        token=<your_bot_token>
 *        chat=<group_chat_id>     (negative number, e.g. -1001234567890)
 *        trigger=true             (optional – sends "Снимок шахт" every N minutes)
 *        interval=5               (optional – minutes between triggers, default 5)
 */
public class TelegramSync {

    private static final String TG_API = "https://api.telegram.org/bot";

    // Config (loaded from tg_config.txt)
    private static String  botToken     = "";
    private static String  chatId       = "";
    private static boolean triggerOn    = false;
    private static int     intervalMins = 5;

    // Gist URL для чтения снимков из облачного userbot (Railway)
    private static final String GIST_URL =
        "https://gist.githubusercontent.com/Morikemuri/b54488cdb9c516fd771370a52c684d4e/raw/snapshot.txt";

    private static long lastUpdateId    = 0;
    private static long lastTriggerMs   = 0;
    private static long lastSnapshotMs  = 0; // время последнего чтения snapshot.txt
    private static int  lastGistHash    = 0; // хэш последнего содержимого Gist

    private static ScheduledExecutorService exec;

    // "Эпическая Шахта Верхнего мира:" / "Ад - Легендарная Шахта Ада и Энда:"
    private static final Pattern PAT_HEADER = Pattern.compile(
        "^(Ад|Энд)?\\s*[-\u2013\u2014]?\\s*(Легендарн\\S+|Эпическ\\S+|Мифическ\\S+|Обычн\\S+)\\s+Шахт\\S*.*$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // "КланЛайт #55 169 сек." (repeated, comma-separated)
    private static final Pattern PAT_ENTRY = Pattern.compile(
        "([А-Яа-яёЁA-Za-z]+\\s*#\\d+)\\s+(\\d+)\\s*сек",
        Pattern.UNICODE_CASE
    );

    // ─── Config ──────────────────────────────────────────────────────────────

    public static boolean isConfigured() {
        return !botToken.isEmpty() && !chatId.isEmpty();
    }

    private static void loadConfig() {
        File f = new File("C:\\mine_alert\\tg_config.txt");
        if (!f.exists()) return;
        try {
            for (String raw : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if      (line.startsWith("token="))    botToken     = line.substring(6).trim();
                else if (line.startsWith("chat="))     chatId       = line.substring(5).trim();
                else if (line.startsWith("trigger="))  triggerOn    = line.substring(8).trim().equalsIgnoreCase("true");
                else if (line.startsWith("interval=")) {
                    try { intervalMins = Integer.parseInt(line.substring(9).trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    public static void start() {
        loadConfig();
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mw-tgsync");
            t.setDaemon(true);
            return t;
        });
        // Читаем snapshot.txt всегда — даже без Telegram бота
        exec.scheduleAtFixedRate(TelegramSync::tick, 5, 10, TimeUnit.SECONDS);
        if (isConfigured()) {
            // При старте — сразу запросить снимок шахт через 5 секунд
            exec.schedule(() -> {
                try { sendTrigger(); lastTriggerMs = System.currentTimeMillis(); } catch (Exception ignored) {}
                try { Thread.sleep(4000); } catch (Exception ignored) {}
                try { poll(); } catch (Exception ignored) {}
            }, 5, TimeUnit.SECONDS);
            MineWatchMod.LOGGER.info("TelegramSync started (chat={})", chatId);
        } else {
            MineWatchMod.LOGGER.info("TelegramSync: нет конфига, только snapshot.txt");
        }
    }

    public static void stop() {
        if (exec != null) exec.shutdownNow();
    }

    // ─── Main loop ───────────────────────────────────────────────────────────

    private static void tick() {
        try { checkSnapshotFile(); } catch (Exception ignored) {}
        try { checkGistSnapshot(); } catch (Exception ignored) {}
        try { poll(); } catch (Exception ignored) {}
        if (triggerOn) {
            long now = System.currentTimeMillis();
            if (now - lastTriggerMs >= (long) intervalMins * 60_000L) {
                try { sendTrigger(); lastTriggerMs = now; }
                catch (Exception ignored) {}
            }
        }
    }

    /** Fetch new updates via getUpdates long-polling (offset = lastUpdateId+1). */
    private static void poll() throws Exception {
        String url = TG_API + botToken
                + "/getUpdates?offset=" + (lastUpdateId + 1)
                + "&limit=100&timeout=1";
        String resp = httpGet(url);
        if (resp == null || !resp.contains("\"ok\":true")) return;

        for (String update : extractResultObjects(resp)) {
            long uid = jsonLong(update, "update_id");
            if (uid > lastUpdateId) lastUpdateId = uid;

            int mi = update.indexOf("\"message\":{");
            if (mi < 0) continue;
            String msg = extractObject(update, mi + 10);
            if (msg.isEmpty()) continue;

            // Optional: filter by chat id
            if (!chatId.isEmpty()) {
                int ci = msg.indexOf("\"chat\":{");
                if (ci >= 0) {
                    String chatObj = extractObject(msg, ci + 7);
                    String mid = jsonRaw(chatObj, "id");
                    if (!mid.isEmpty() && !mid.equals(chatId)) continue;
                }
            }

            String text = jsonStr(msg, "text");
            if (text != null && !text.isEmpty()) parseMineMessage(text);
        }
    }

    /**
     * Немедленный триггер "Снимок шахт" — вызывается при заходе на сервер.
     * Кулдаун 30 секунд чтобы не спамить.
     */
    public static void triggerNow() {
        if (!isConfigured() || exec == null || exec.isShutdown()) return;
        long now = System.currentTimeMillis();
        if (now - lastTriggerMs < 30_000) return;
        exec.submit(() -> {
            try { sendTrigger(); lastTriggerMs = System.currentTimeMillis(); } catch (Exception ignored) {}
            try { Thread.sleep(4000); } catch (Exception ignored) {}
            try { poll(); } catch (Exception ignored) {}
        });
    }

    /** Send "Снимок шахт" to the group to trigger @liteeventbot. */
    private static void sendTrigger() throws Exception {
        String url  = TG_API + botToken + "/sendMessage";
        String body = "{\"chat_id\":" + chatId + ",\"text\":\"Снимок шахт\"}";
        httpPost(url, body);
    }

    // ─── Локальный файл снимка (от userbot.py) ───────────────────────────────

    private static void checkSnapshotFile() throws Exception {
        java.io.File f = new java.io.File("C:\\mine_alert\\snapshot.txt");
        if (!f.exists()) return;
        long modified = f.lastModified();
        if (modified <= lastSnapshotMs) return; // уже читали этот снимок
        lastSnapshotMs = modified;
        String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        if (!text.isEmpty()) {
            parseMineMessage(text);
            MineWatchMod.LOGGER.info("TelegramSync: snapshot.txt загружен ({} байт)", text.length());
        }
    }

    // ─── Gist snapshot (облачный userbot на Railway) ─────────────────────────

    private static void checkGistSnapshot() throws Exception {
        String text = httpGet(GIST_URL + "?nocache=" + System.currentTimeMillis());
        if (text == null || text.isEmpty()) return;
        int hash = text.hashCode();
        if (hash == lastGistHash) return;
        lastGistHash = hash;
        parseMineMessage(text);
        MineWatchMod.LOGGER.info("TelegramSync: Gist-снимок загружен ({} байт)", text.length());
    }

    // ─── Message parser ──────────────────────────────────────────────────────

    /**
     * Parses a mine-snapshot message and feeds data into MineData.
     *
     * Expected format (one or more blocks):
     *   Эпическая Шахта Верхнего мира:
     *   КланЛайт #55 169 сек., КланЛайт #61 388 сек., ...
     *
     *   Ад - Легендарная Шахта Ада и Энда:
     *   ДуоЛайт #18 389 сек., ТриоЛайт #46 391 сек., ...
     */
    static void parseMineMessage(String text) {
        String[] lines = text.split("\n");
        String world = null;
        String type  = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                // blank line resets context
                world = null;
                type  = null;
                continue;
            }

            Matcher hm = PAT_HEADER.matcher(line);
            if (hm.matches()) {
                world = normalizeWorld(hm.group(1));
                type  = normalizeType(hm.group(2));
                continue;
            }

            if (world != null && type != null) {
                Matcher em = PAT_ENTRY.matcher(line);
                while (em.find()) {
                    String party = em.group(1).trim();
                    int    secs  = Integer.parseInt(em.group(2));
                    // null for nextType – we don't know it from the snapshot
                    MineData.setRemoteData(party, world, type, null, secs);
                }
            }
        }
    }

    private static String normalizeWorld(String w) {
        if (w == null || w.isEmpty()) return "Мир";
        String l = w.toLowerCase();
        if (l.contains("ад"))  return "Ад";
        if (l.contains("энд")) return "Энд";
        return "Мир";
    }

    private static String normalizeType(String t) {
        if (t == null) return "Обычная";
        String l = t.toLowerCase();
        if (l.startsWith("легенд")) return "Легендарная";
        if (l.startsWith("мифич"))  return "Мифическая";
        if (l.startsWith("эпич"))   return "Эпическая";
        return "Обычная";
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────

    private static String httpGet(String rawUrl) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(rawUrl).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(5000);
        c.setReadTimeout(6000);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        if (code != 200) { c.disconnect(); return null; }
        String s = readAll(c.getInputStream());
        c.disconnect();
        return s;
    }

    private static void httpPost(String rawUrl, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(rawUrl).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setDoOutput(true);
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.getOutputStream().write(b);
        c.getResponseCode();
        c.disconnect();
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toString("UTF-8");
    }

    // ─── Minimal JSON (no external deps) ────────────────────────────────────

    /** Extract all top-level objects from the "result":[...] array. */
    private static List<String> extractResultObjects(String json) {
        List<String> res = new ArrayList<>();
        int ri = json.indexOf("\"result\":[");
        if (ri < 0) return res;
        int i = ri + 9; // points to '['
        while (i < json.length() && json.charAt(i) != '[') i++;
        if (i >= json.length()) return res;
        i++; // skip '['

        int depth = 0, start = -1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if      (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start >= 0) { res.add(json.substring(start, i + 1)); start = -1; } }
            else if (c == ']' && depth == 0) break;
            i++;
        }
        return res;
    }

    /** Extract the JSON object starting at (or after) fromIdx. */
    private static String extractObject(String json, int fromIdx) {
        int depth = 0, start = -1;
        for (int i = fromIdx; i < json.length(); i++) {
            char c = json.charAt(i);
            if      (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start >= 0) return json.substring(start, i + 1); }
        }
        return "";
    }

    private static long jsonLong(String json, String key) {
        try { return Long.parseLong(jsonRaw(json, key)); } catch (Exception e) { return 0L; }
    }

    /** Extract a JSON string value (handles \n \r \t \\ \" escapes). */
    private static String jsonStr(String json, String key) {
        String k = "\"" + key + "\":\"";
        int i = json.indexOf(k);
        if (i < 0) return "";
        i += k.length();
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char nx = json.charAt(i + 1);
                switch (nx) {
                    case '"':  sb.append('"');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case '\\': sb.append('\\'); break;
                    default:   sb.append(nx);   break;
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** Extract a raw (unquoted) JSON value: number, boolean, null. */
    private static String jsonRaw(String json, String key) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) return "";
        i += k.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        // Skip string values
        if (i < json.length() && json.charAt(i) == '"') return "";
        int j = i;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == ',' || c == '}' || c == ']' || c == ' ') break;
            j++;
        }
        return json.substring(i, j);
    }
}
