package de.bergf3x.bgawidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * TESTVERSION: Nachrichten direkt von den Webseiten holen (ohne den GitHub-Feed).
 * Port der Logik aus scrape.py; liefert denselben RSS-Text, den der Rest der App schon kennt.
 * Erstsichtung wird lokal gemerkt (die Seiten zeigen bei den Artikeln keine Zeit).
 */
final class Direct {
    private Direct() { }

    private static final int MAX_ITEMS = 400;
    private static final int MAX_PER_TOPIC = 40;
    private static final String BGA = "https://www.berchtesgadener-anzeiger.de";
    private static final String BGL = "https://www.bgland24.de";
    private static final Pattern ARID = Pattern.compile("_arid,(\\d+)\\.html");
    private static final Pattern A_BGA = Pattern.compile("<a\\b[^>]*?href=\"([^\"]*_arid,\\d+\\.html)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern A_ANY = Pattern.compile("<a\\b[^>]*?href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern HEAD = Pattern.compile("<h[1-4][^>]*>(.*?)</h[1-4]>", Pattern.DOTALL);
    private static final Pattern PARA = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL);

    /** Quellseiten des Anzeigers: Pfad, Thema, optional nur URLs mit diesem Teil. */
    private static final String[][] BGA_SOURCES = {
            {"/region-und-lokal/lokales-berchtesgadener-land.html", "Region/Berchtesgadener Land", ""},
            {"/region-und-lokal/lokalnachrichten-landkreis-traunstein.html", "Region/Landkreis Traunstein", "/startseite_artikel"},
            {"/region-und-lokal/bergsteiger-und-naturfreunde.html", "Region/Bergsteiger & Naturfreunde", ""},
            {"/nachrichten.html", "Nachrichten/Deutschland & Welt", ""},
            {"/nachrichten/wirtschaft.html", "Nachrichten/Wirtschaft", ""},
            {"/nachrichten/wissenschaft.html", "Nachrichten/Wissenschaft", ""},
            {"/nachrichten/computer.html", "Nachrichten/Digital", ""},
            {"/nachrichten/boulevard.html", "Nachrichten/Boulevard", ""},
            {"/kultur.html", "Kultur", ""},
            {"/sport.html", "Sport", ""},
            {"/sport/fussball.html", "Sport/Fußball", ""},
            {"/sport/wintersport.html", "Sport/Wintersport", ""},
            {"/sport/sport-aus-der-region.html", "Sport/Sport aus der Region", ""},
    };

    static boolean enabled(Context ctx) {
        return ctx.getResources().getBoolean(R.bool.direct_fetch);
    }

    static boolean handles(Catalog.Feed f) {
        return f.id.equals("bga") || f.id.equals("bgland24");
    }

    /** Holt die Seite(n) live, mischt mit dem gemerkten Stand und liefert den RSS-Text. */
    static String fetch(Context ctx, Catalog.Feed f) throws IOException {
        long t0 = System.currentTimeMillis();
        boolean bga = f.id.equals("bga");
        List<Map<String, Object>> found = bga ? scrapeBga() : scrapeBgland();
        if (found.isEmpty()) throw new IOException("keine Artikel gefunden");
        String xml;
        synchronized (Direct.class) {
            xml = merge(ctx, f, bga, found);
        }
        log(ctx, f.name + ": " + found.size() + " Artikel direkt, " + (System.currentTimeMillis() - t0) / 1000 + " s");
        return xml;
    }

    // ------------------------------------------------------------------ Anzeiger

    private static List<Map<String, Object>> scrapeBga() {
        ExecutorService ex = Executors.newFixedThreadPool(5);
        List<Callable<List<Map<String, Object>>>> tasks = new ArrayList<>();
        for (final String[] src : BGA_SOURCES) {
            tasks.add(() -> {
                String html = Net.download(BGA + src[0]);
                List<Map<String, Object>> out = parseBga(html);
                List<Map<String, Object>> keep = new ArrayList<>();
                for (Map<String, Object> it : out) {
                    if (!src[2].isEmpty() && !((String) it.get("url")).contains(src[2])) continue;
                    String topic = src[1];
                    if (!topic.startsWith("Sport/") && ((String) it.get("category")).toLowerCase(Locale.GERMANY).startsWith("regionalsport")) {
                        topic = "Sport/Sport aus der Region";
                    }
                    it.put("topic", topic);
                    keep.add(it);
                }
                return keep;
            });
        }
        Map<Long, Map<String, Object>> found = new java.util.LinkedHashMap<>();
        try {
            List<Future<List<Map<String, Object>>>> fs = ex.invokeAll(tasks, 40, TimeUnit.SECONDS);
            for (Future<List<Map<String, Object>>> fu : fs) {
                if (fu.isCancelled()) continue;
                List<Map<String, Object>> list;
                try {
                    list = fu.get();
                } catch (Exception e) {
                    continue; // eine Teilseite darf ausfallen
                }
                for (Map<String, Object> it : list) {
                    Long sid = (Long) it.get("sid");
                    Map<String, Object> old = found.get(sid);
                    if (old != null) {
                        if ((Boolean) old.get("plus")) it.put("plus", true);
                        String ot = (String) old.get("topic"), nt = (String) it.get("topic");
                        if (!ot.split("/")[0].equals(nt.split("/")[0])) it.put("topic", ot);
                    }
                    found.put(sid, it);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ex.shutdownNow();
        }
        return new ArrayList<>(found.values());
    }

    static List<Map<String, Object>> parseBga(String html) {
        Map<Long, Map<String, Object>> items = new java.util.LinkedHashMap<>();
        Matcher m = A_BGA.matcher(html);
        while (m.find()) {
            String href = m.group(1), inner = m.group(2);
            Matcher am = ARID.matcher(href);
            if (!am.find()) continue;
            long sid = Long.parseLong(am.group(1));
            String url = href.startsWith("http") ? href : BGA + (href.startsWith("/") ? "" : "/") + href;
            String title = "";
            Matcher hm = HEAD.matcher(inner);
            if (hm.find()) title = clean(hm.group(1));
            String teaser = "";
            Matcher pm = PARA.matcher(inner);
            while (pm.find()) {
                if (!pm.group(1).contains("<img")) {
                    teaser = clean(pm.group(1));
                    break;
                }
            }
            List<String> parts = new ArrayList<>();
            for (String s : inner.split("<[^>]+>")) {
                String c = clean(s);
                if (!c.isEmpty()) parts.add(c);
            }
            String category = "";
            if (!title.isEmpty()) {
                int idx = parts.indexOf(title);
                if (idx > 0) category = parts.get(idx - 1);
            } else {
                for (String p : parts) if (p.length() > title.length()) title = p;
            }
            if (title.isEmpty()) continue;
            boolean plus = Pattern.compile("<img[^>]*plus|class=\"[^\"]*plus", Pattern.CASE_INSENSITIVE).matcher(inner).find();
            Map<String, Object> prev = items.get(sid);
            if (prev != null) {
                plus = plus || (Boolean) prev.get("plus");
                if (((String) prev.get("teaser")).length() >= teaser.length()) {
                    prev.put("plus", plus);
                    continue;
                }
            }
            Map<String, Object> it = new HashMap<>();
            it.put("sid", sid);
            it.put("url", url);
            it.put("title", title);
            it.put("teaser", teaser);
            it.put("category", category);
            it.put("plus", plus);
            items.put(sid, it);
        }
        return new ArrayList<>(items.values());
    }

    // ------------------------------------------------------------------ BGLand24

    private static List<Map<String, Object>> scrapeBgland() throws IOException {
        String html = Net.download(BGL + "/");
        String host = "bgland24.de";
        String[] skip = {"/category/", "/tag/", "/author/", "/page/", "/impressum", "/datenschutz", "/kontakt", "/wp-", "/feed", "/tags/", "/kategorie"};
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher m = A_ANY.matcher(html);
        while (m.find()) {
            String href = m.group(1).split("#")[0];
            String url;
            if (href.startsWith("http")) url = href;
            else if (href.startsWith("/")) url = BGL + href;
            else continue;
            java.net.URI u;
            try {
                u = new java.net.URI(url);
            } catch (Exception e) {
                continue;
            }
            String h = u.getHost() == null ? "" : u.getHost().replace("www.", "");
            String path = u.getPath() == null ? "" : u.getPath();
            if (!h.equals(host)) continue;
            boolean bad = false;
            for (String s : skip) if (path.contains(s)) bad = true;
            if (bad) continue;
            String slug = path.replaceAll("/+$", "");
            slug = slug.substring(slug.lastIndexOf('/') + 1);
            String inner = m.group(2);
            Matcher hm = HEAD.matcher(inner);
            String title = hm.find() ? clean(hm.group(1)) : clean(inner);
            if (!seen.add(url) || slug.length() < 15 || !slug.contains("-") || title.length() < 20) continue;
            Map<String, Object> it = new HashMap<>();
            CRC32 c = new CRC32();
            c.update(url.getBytes("UTF-8"));
            it.put("sid", c.getValue());
            it.put("url", url);
            it.put("title", title);
            it.put("teaser", "");
            it.put("category", "");
            it.put("topic", bgTopic(url));
            it.put("plus", false);
            out.add(it);
        }
        return out;
    }

    private static String nice(String x) {
        x = x.replaceFirst("^(region|landkreis)-", "");
        StringBuilder sb = new StringBuilder();
        for (String w : x.split("-")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    static String bgTopic(String url) {
        List<String> seg = new ArrayList<>();
        try {
            for (String s : new java.net.URI(url).getPath().split("/")) if (!s.isEmpty()) seg.add(s);
        } catch (Exception ignored) {
        }
        if (seg.isEmpty()) return "Region";
        String top = seg.get(0);
        switch (top) {
            case "bgland": return seg.size() > 2 ? "Region/" + nice(seg.get(1)) : "Region";
            case "welt": case "deutschland": case "politik": case "wirtschaft": return "Nachrichten/" + nice(top);
            case "sport": return "Sport";
            case "wintersport": return "Sport/Wintersport";
            case "bayern": return seg.size() > 2 && seg.get(1).startsWith("landkreis") ? "Bayern/" + nice(seg.get(1)) : "Bayern";
            case "leben": case "stars": return "Nachrichten/" + nice(top);
            default: return nice(top);
        }
    }

    // ------------------------------------------------------------------ Zustand + RSS

    private static String merge(Context ctx, Catalog.Feed f, boolean bga, List<Map<String, Object>> found) {
        SharedPreferences sp = ctx.getSharedPreferences("direct", Context.MODE_PRIVATE);
        Map<String, JSONObject> state = new java.util.LinkedHashMap<>();
        try {
            JSONArray a = new JSONArray(sp.getString("state_" + f.id, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                state.put(String.valueOf(o.getLong("sid")), o);
            }
        } catch (Exception ignored) {
        }
        long now = System.currentTimeMillis();
        try {
            for (Map<String, Object> it : found) {
                String key = String.valueOf(it.get("sid"));
                JSONObject o = state.get(key);
                if (o == null) {
                    o = new JSONObject();
                    o.put("sid", it.get("sid"));
                    o.put("url", it.get("url"));
                    o.put("first", now);
                    state.put(key, o);
                }
                o.put("title", it.get("title"));
                o.put("teaser", it.get("teaser"));
                o.put("category", it.get("category"));
                o.put("topic", it.get("topic"));
                o.put("plus", o.optBoolean("plus") || (Boolean) it.get("plus"));
            }
        } catch (Exception ignored) {
        }
        List<JSONObject> sorted = new ArrayList<>(state.values());
        final boolean byId = bga;
        Collections.sort(sorted, (x, y) -> byId ? Long.compare(y.optLong("sid"), x.optLong("sid"))
                : Long.compare(y.optLong("first"), x.optLong("first")));
        List<JSONObject> ordered = new ArrayList<>();
        Map<String, Integer> per = new HashMap<>();
        for (JSONObject o : sorted) {
            String t = o.optString("topic", "Region");
            int n = per.containsKey(t) ? per.get(t) + 1 : 1;
            per.put(t, n);
            if (n <= MAX_PER_TOPIC && ordered.size() < MAX_ITEMS) ordered.add(o);
        }
        JSONArray save = new JSONArray();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\"><channel><title>")
                .append(esc(f.name)).append("</title>\n");
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (JSONObject o : ordered) {
            save.put(o);
            String title = (o.optBoolean("plus") ? "\uD83D\uDD12 " : "") + o.optString("title");
            xml.append("<item><title>").append(esc(title)).append("</title><link>").append(esc(o.optString("url")))
                    .append("</link><guid isPermaLink=\"false\">").append(f.id).append('-').append(o.optLong("sid"))
                    .append("</guid><pubDate>").append(fmt.format(new Date(o.optLong("first", now))))
                    .append("</pubDate><category>").append(esc(o.optString("category"))).append("</category><topic>")
                    .append(esc(o.optString("topic", "Region"))).append("</topic><description>")
                    .append(esc(o.optString("teaser"))).append("</description></item>\n");
        }
        xml.append("</channel></rss>\n");
        sp.edit().putString("state_" + f.id, save.toString()).apply();
        return xml.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&shy;", "");
        s = s.replaceAll("&#(\\d+);", "").replaceAll("\\s+", " ").trim();
        return s;
    }

    // ------------------------------------------------------------------ Protokoll fuer das Info-Menue

    private static void log(Context ctx, String line) {
        SharedPreferences sp = ctx.getSharedPreferences("direct", Context.MODE_PRIVATE);
        String stamp = new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(new Date());
        String old = sp.getString("log", "");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(stamp + "  " + line);
        for (int i = 0; i < Math.min(lines.length, 11); i++) sb.append('\n').append(lines[i]);
        sp.edit().putString("log", sb.toString()).apply();
    }

    static String logText(Context ctx) {
        return ctx.getSharedPreferences("direct", Context.MODE_PRIVATE).getString("log", "Noch kein Abruf.");
    }
}
