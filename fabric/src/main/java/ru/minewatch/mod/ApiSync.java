package ru.minewatch.mod;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

// ================================================================
// [MINEWATCH::SYNC] :: remote data sync daemon
// push local state to Supabase, pull peer nodes every 3s
// heartbeat ping every 60s
// ================================================================
public class ApiSync {

    // -- ENDPOINT CONFIG --
    private static final String BASE    = "https://csmdyxvduilvjxvcyzwj.supabase.co/rest/v1";
    private static final String KEY     = "sb_publishable_0u537mImzUbruIh4-QvrRA_ykDEWbRz";
    static final         String VERSION = "1.0.0";

    static  String uuid;                           // unique node identity
    private static ScheduledExecutorService exec;  // daemon thread pool
    private static long lastPing = 0;

    // BOOT: start single-thread daemon scheduler
    public static void start() {
        uuid = loadUuid();
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mw-sync");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(ApiSync::tick, 5, 3, TimeUnit.SECONDS);
    }

    // STOP: shutdown executor (called from JVM shutdown hook)
    public static void stop() {
        if (exec != null) exec.shutdownNow();
    }

    // TICK: main sync cycle - push, pull, ping
    private static void tick() {
        try { push(); } catch (Exception ignored) {}
        try { pull(); } catch (Exception ignored) {}
        if (System.currentTimeMillis() - lastPing > 60_000) {
            try { ping(); lastPing = System.currentTimeMillis(); } catch (Exception ignored) {}
        }
    }

    // PUSH: transmit local shaft state to backend registry
    private static void push() throws Exception {
        String mode = MineData.getAnarchyMode();
        if (mode == null || mode.isEmpty()) return;
        MineData.MineEntry e = MineData.getEntry(mode);
        if (e == null) return;
        for (MineData.ShaftEntry s : e.shafts.values()) {
            if (!s.hasTime()) continue;
            String body = "{\"server\":"        + q(mode)            +
                          ",\"world\":"          + q(s.world)          +
                          ",\"current_type\":"   + q(s.currentType)    +
                          ",\"next_type\":"      + q(s.nextType)       +
                          ",\"secs_left\":"      + Math.max(0, s.getRealSecs()) +
                          ",\"reporter_uuid\":"  + q(uuid)             + "}";
            post("/mines", body, "resolution=merge-duplicates");
        }
    }

    // PULL: fetch remote peer nodes from backend (last 120s window)
    private static void pull() throws Exception {
        String cutoff = iso(System.currentTimeMillis() - 120_000);
        String resp   = get("/mines?select=*&updated_at=gte." + URLEncoder.encode(cutoff, "UTF-8"));
        if (resp == null || resp.length() < 5) return;

        String myMode = MineData.getAnarchyMode();
        for (String obj : parseArray(resp)) {
            String server = str(obj, "server");
            String world  = str(obj, "world");
            String cur    = str(obj, "current_type");
            String nxt    = str(obj, "next_type");
            int    secs   = num(obj, "secs_left");
            String updAt  = str(obj, "updated_at");

            if (server.isEmpty() || server.equals(myMode)) continue;

            // ADJUST: compensate server-side timestamp drift
            long updMs  = parseIso(updAt);
            int  actual = secs < 0 ? -1 : (int) Math.max(0, secs - (System.currentTimeMillis() - updMs) / 1000L);
            MineData.setRemoteData(server, world, cur, nxt, actual);
        }
    }

    // PING: heartbeat beacon to node registry
    private static void ping() throws Exception {
        String body = "{\"uuid\":" + q(uuid) + ",\"mod_version\":" + q(VERSION) + "}";
        post("/pings", body, "resolution=merge-duplicates");
    }

    // ---- HTTP LAYER ----

    // POST: send JSON payload to endpoint
    private static void post(String path, String body, String prefer) throws Exception {
        HttpURLConnection c = conn("POST", BASE + path);
        if (prefer != null) c.setRequestProperty("Prefer", prefer);
        c.setDoOutput(true);
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.setRequestProperty("Content-Length", String.valueOf(b.length));
        c.getOutputStream().write(b);
        c.getResponseCode();
        c.disconnect();
    }

    // GET: fetch raw JSON from endpoint
    private static String get(String path) throws Exception {
        HttpURLConnection c = conn("GET", BASE + path);
        int code = c.getResponseCode();
        if (code != 200) { c.disconnect(); return null; }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096]; int n;
        InputStream is = c.getInputStream();
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        c.disconnect();
        return buf.toString("UTF-8");
    }

    // CONN: build authenticated HTTP connection
    private static HttpURLConnection conn(String method, String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        c.setRequestProperty("apikey",        KEY);
        c.setRequestProperty("Authorization", "Bearer " + KEY);
        c.setRequestProperty("Content-Type",  "application/json");
        c.setRequestProperty("Accept",        "application/json");
        return c;
    }

    // ---- UUID LAYER ----

    // LOAD: read or generate persistent node UUID from disk
    private static String loadUuid() {
        File f = new File("C:\\mine_alert\\mwid.txt");
        try {
            f.getParentFile().mkdirs();
            if (f.exists()) {
                String s = new String(Files.readAllBytes(f.toPath())).trim();
                if (!s.isEmpty()) return s;
            }
            String id = UUID.randomUUID().toString();
            Files.write(f.toPath(), id.getBytes(StandardCharsets.UTF_8));
            return id;
        } catch (Exception e) {
            return UUID.randomUUID().toString(); // fallback: ephemeral id
        }
    }

    // ---- JSON PARSER (minimal, no deps) ----

    // PARSE: split top-level JSON array into object strings
    private static List<String> parseArray(String arr) {
        List<String> res = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < arr.length(); i++) {
            char ch = arr.charAt(i);
            if      (ch == '{') { if (depth++ == 0) start = i; }
            else if (ch == '}') { if (--depth == 0 && start >= 0) { res.add(arr.substring(start, i + 1)); start = -1; } }
        }
        return res;
    }

    // EXTRACT: string value from JSON object by key
    private static String str(String o, String k) {
        String key = "\"" + k + "\":\"";
        int i = o.indexOf(key);
        if (i < 0) return "";
        i += key.length();
        int j = i;
        while (j < o.length()) {
            if (o.charAt(j) == '"' && (j == 0 || o.charAt(j - 1) != '\\')) break;
            j++;
        }
        return o.substring(i, j);
    }

    // EXTRACT: numeric value from JSON object by key
    private static int num(String o, String k) {
        String key = "\"" + k + "\":";
        int i = o.indexOf(key);
        if (i < 0) return -1;
        i += key.length();
        int j = i;
        while (j < o.length() && (Character.isDigit(o.charAt(j)) || o.charAt(j) == '-')) j++;
        try { return Integer.parseInt(o.substring(i, j)); } catch (Exception e) { return -1; }
    }

    // UTIL: JSON-quote a string value
    private static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // UTIL: format epoch ms as ISO-8601 UTC string
    private static String iso(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    // UTIL: parse ISO-8601 timestamp to epoch ms
    private static long parseIso(String s) {
        if (s == null || s.length() < 19) return System.currentTimeMillis();
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s.substring(0, 19)).getTime();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }
}
