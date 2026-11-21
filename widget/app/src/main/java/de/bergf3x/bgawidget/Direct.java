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
    private static final String C24 = "https://www.chiemgau24.de";
    private static final String ORF_SBG = "https://rss.orf.at/salzburg.xml";

    /** Ippen-Seiten: Abo-Pruefung je Artikel, nur echte Artikel-Links. */
    static boolean ippen(Catalog.Feed f) {
        return f.id.equals("bgland24") || f.id.equals("chiemgau24");
    }
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
        return f.id.equals("bga") || f.id.equals("bgland24") || f.id.equals("chiemgau24") || f.id.equals("orf_salzburg");
    }

    /** Holt die Seite(n) live, mischt mit dem gemerkten Stand und liefert den RSS-Text. */
    static String fetch(Context ctx, Catalog.Feed f) throws IOException {
        long t0 = System.currentTimeMillis();
        try {
            boolean bga = f.id.equals("bga");
            StringBuilder rep = new StringBuilder();
            List<Map<String, Object>> found;
            if (bga) found = scrapeBga();
            else if (f.id.equals("bgland24")) found = scrapeIppen(BGL, java.util.Arrays.asList(BG_SECTIONS), true, rep);
            else if (f.id.equals("chiemgau24")) found = scrapeChiemgau(rep);
            else found = scrapeOrf(rep);
            if (found.isEmpty()) throw new IOException("keine Artikel gefunden");
            int timed = 0;
            if (bga) {
                try {
                    timed = resolveTimes(ctx, f, found);
                } catch (Exception ignored) {
                }
            }
            String xml;
            synchronized (Direct.class) {
                xml = merge(ctx, f, bga, found);
            }
            if (ippen(f)) {
                // Ippen-Seiten: Zeiten/Bezahl-Kennzeichen im Hintergrund nachladen, damit die Liste sofort erscheint
                final Context app = ctx.getApplicationContext();
                final Catalog.Feed ff = f;
                final List<Map<String, Object>> fl = found;
                Thread th = new Thread(() -> {
                    try {
                        int n = resolveTimes(app, ff, fl);
                        String x2;
                        synchronized (Direct.class) {
                            x2 = merge(app, ff, false, fl);
                        }
                        if (n > 0) FeedCache.put(app, ff.id, x2, FeedItem.parse(x2, ff.name)); // Abo-Kennzeichen sofort wirksam
                        log(app, ff.name + ": " + n + " Artikel auf Abo geprüft (Hintergrund)");
                    } catch (Throwable ignored) {
                    }
                });
                th.setDaemon(true);
                th.start();
            }
            int regionN = 0;
            for (Map<String, Object> it : found) if (String.valueOf(it.get("topic")).startsWith("Region")) regionN++;
            log(ctx, f.name + ": " + found.size() + " Artikel direkt (davon Region " + regionN + "), " + timed + " Zeiten, "
                    + (System.currentTimeMillis() - t0) / 1000 + " s" + (bga ? "" : " | " + rep));
            return xml;
        } catch (Exception e) {
            log(ctx, f.name + ": FEHLER direkt (" + e + ") - nehme GitHub-Feed");
            return Net.download(Net.fresh(f.url));
        }
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

    private static final Pattern BG_ART = Pattern.compile("-\\d{6,}\\.html$");

    /** Rubriken von BGLand24; jede hat einen eigenen RSS-Feed (Ippen-Muster: <rubrik>rssfeed.rdf). */
    private static final String[] BG_SECTIONS = {
            "/bgland/region-berchtesgaden/", "/bgland/region-bad-reichenhall/", "/bgland/rupertiwinkel/",
            "/bgland/region-traunstein/", "/bgland/region-salzburg/", "/bgland/", "/",
    };
    /** Ippen-Seite: je Rubrik der RSS-Feed (sonst die Rubrikseite), optional die Startseite dazu. */
    private static List<Map<String, Object>> scrapeIppen(final String base, List<String> sections, boolean home, StringBuilder rep)
            throws IOException {
        ExecutorService ex = Executors.newFixedThreadPool(5);
        List<Callable<Object[]>> tasks = new ArrayList<>();
        for (final String sec : sections) {
            tasks.add(() -> {
                List<Map<String, Object>> got = new ArrayList<>();
                String how;
                try {
                    got = parseRss(Net.download(base + sec + "rssfeed.rdf"), true);
                    how = "RSS " + got.size();
                } catch (Exception e) {
                    how = "RSS-Fehler " + e.getMessage();
                }
                if (got.isEmpty() && !sec.equals("/")) {
                    try {
                        got = parseIppen(base, Net.download(base + sec));
                        how += ", Seite " + got.size();
                    } catch (Exception e) {
                        how += ", Seiten-Fehler " + e.getMessage();
                    }
                }
                return new Object[]{sec, how, got};
            });
        }
        // Startseite ergaenzt Themen ausserhalb der Region (Bayern, Sport ...)
        if (home) tasks.add(() -> {
            try {
                List<Map<String, Object>> got = parseIppen(base, Net.download(base + "/"));
                return new Object[]{"Startseite", "Seite " + got.size(), got};
            } catch (Exception e) {
                return new Object[]{"Startseite", "Fehler " + e.getMessage(), new ArrayList<Map<String, Object>>()};
            }
        });
        Map<String, Map<String, Object>> byUrl = new java.util.LinkedHashMap<>();
        try {
            for (Future<Object[]> fu : ex.invokeAll(tasks, 20, TimeUnit.SECONDS)) {
                if (fu.isCancelled()) {
                    rep.append("Zeitlimit; ");
                    continue;
                }
                try {
                    Object[] r = fu.get();
                    String sec = (String) r[0];
                    rep.append(sec.replace("/bgland/", "").replace("/chiemgau/", "").replace("region-", "").replace("/", "")).append(": ").append(r[1]).append("; ");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> got = (List<Map<String, Object>>) r[2];
                    for (Map<String, Object> it : got) {
                        String u = (String) it.get("url");
                        Map<String, Object> old = byUrl.get(u);
                        if (old == null) byUrl.put(u, it);
                        else if (!old.containsKey("pub") && it.containsKey("pub")) old.put("pub", it.get("pub"));
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ex.shutdownNow();
        }
        return new ArrayList<>(byUrl.values());
    }

    /** chiemgau24: Lokalteil /chiemgau/, Unterrubriken aus den Artikel-Adressen; nur Regionales. */
    private static List<Map<String, Object>> scrapeChiemgau(StringBuilder rep) throws IOException {
        List<String> secs = new ArrayList<>();
        secs.add("/chiemgau/");
        try {
            for (Map<String, Object> it : parseRss(Net.download(C24 + "/chiemgau/rssfeed.rdf"), true)) {
                List<String> seg = new ArrayList<>();
                for (String x : new java.net.URI((String) it.get("url")).getPath().split("/")) if (!x.isEmpty()) seg.add(x);
                if (seg.size() > 2 && seg.get(0).equals("chiemgau")) {
                    String sub = "/chiemgau/" + seg.get(1) + "/";
                    if (!secs.contains(sub) && secs.size() < 11) secs.add(sub);
                }
            }
        } catch (Exception e) {
            rep.append("Rubriksuche-Fehler ").append(e.getMessage()).append("; ");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> it : scrapeIppen(C24, secs, false, rep)) {
            if (String.valueOf(it.get("topic")).startsWith("Region")) out.add(it);
        }
        return out;
    }

    /** ORF Salzburg: offizieller RSS-Feed, alles ins Thema Region/Salzburg. */
    private static List<Map<String, Object>> scrapeOrf(StringBuilder rep) throws IOException {
        List<Map<String, Object>> got = parseRss(Net.download(ORF_SBG), false);
        for (Map<String, Object> it : got) it.put("topic", "Region/Salzburg");
        rep.append("RSS ").append(got.size());
        return got;
    }

    private static final Pattern RSS_ITEM = Pattern.compile("<(item|entry)\\b[^>]*>(.*?)</\\1>", Pattern.DOTALL);

    private static String rssTag(String blk, String name) {
        Matcher m = Pattern.compile("<" + name + "\\b[^>]*>(.*?)</" + name + ">", Pattern.DOTALL).matcher(blk);
        if (!m.find()) return "";
        String v = m.group(1).trim();
        if (v.startsWith("<![CDATA[") && v.endsWith("]]>")) v = v.substring(9, v.length() - 3);
        return v.trim();
    }

    /** RSS 2.0 / RDF / Atom tolerant lesen (nur echte Artikel-Links). */
    static List<Map<String, Object>> parseRss(String xml, boolean art) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        SimpleDateFormat rfc = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        Matcher m = RSS_ITEM.matcher(xml);
        while (m.find()) {
            String blk = m.group(2);
            String link = rssTag(blk, "link");
            if (link.isEmpty()) {
                Matcher h = Pattern.compile("<link[^>]+href=\"([^\"]+)\"").matcher(blk);
                if (h.find()) link = h.group(1);
            }
            link = FeedItem.unescape(link.split("#")[0]).trim();
            if (link.isEmpty() || (art && !BG_ART.matcher(link.split("\\?")[0]).find())) continue;
            String title = clean(rssTag(blk, "title"));
            if (title.length() < 10) continue;
            Map<String, Object> it = new HashMap<>();
            CRC32 c = new CRC32();
            c.update(link.getBytes("UTF-8"));
            it.put("sid", c.getValue());
            it.put("url", link);
            it.put("title", title);
            String teaser = clean(rssTag(blk, "description"));
            it.put("teaser", teaser.length() > 300 ? teaser.substring(0, 300) : teaser);
            it.put("category", clean(rssTag(blk, "category")));
            it.put("topic", bgTopic(link));
            it.put("plus", false);
            String raw = rssTag(blk, "pubDate");
            long t = -1;
            try {
                if (!raw.isEmpty()) {
                    Date d = rfc.parse(raw);
                    if (d != null) t = d.getTime();
                } else {
                    raw = rssTag(blk, "dc:date");
                    if (raw.isEmpty()) raw = rssTag(blk, "published");
                    if (!raw.isEmpty()) t = parseTime("\"datePublished\":\"" + raw + "\"");
                }
            } catch (Exception ignored) {
            }
            if (t > 0) it.put("pub", t);
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> parseIppen(String base, String html) throws IOException {
        String host = base.replaceFirst("^https?://(www\\.)?", "");
        String[] skip = {"/category/", "/tag/", "/author/", "/page/", "/impressum", "/datenschutz", "/kontakt", "/wp-", "/feed", "/tags/", "/kategorie"};
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher m = A_ANY.matcher(html);
        while (m.find()) {
            String href = m.group(1).split("#")[0];
            String url;
            if (href.startsWith("http")) url = href;
            else if (href.startsWith("/")) url = base + href;
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
            if (bad || !BG_ART.matcher(path).find()) continue;
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
            case "bgland": case "chiemgau": case "lokal": case "lokales":
                return seg.size() > 2 ? "Region/" + nice(seg.get(1)) : "Region";
            case "welt": case "deutschland": case "politik": case "wirtschaft": return "Nachrichten/" + nice(top);
            case "sport": return "Sport";
            case "wintersport": return "Sport/Wintersport";
            case "bayern": return seg.size() > 2 && seg.get(1).startsWith("landkreis") ? "Bayern/" + nice(seg.get(1)) : "Bayern";
            case "leben": case "stars": return "Nachrichten/" + nice(top);
            default: return nice(top);
        }
    }

    // ------------------------------------------------------------------ Echte Veroeffentlichungszeit

    private static final Pattern P_DATUM = Pattern.compile("Datum:?\\s*(?:<[^>]*>\\s*)*(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s*,?\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern P_ISO = Pattern.compile("(?:article:published_time|datePublished|og:article:published_time)[\"']?\\s*(?:content=|:)?\\s*[\"']?(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)");
    private static final Pattern P_ISO2 = Pattern.compile("content=[\"'](\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}[^\"']*)[\"'][^>]*(?:published_time)|(?:published_time)[^>]*content=[\"'](\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}[^\"']*)[\"']");

    private static final Pattern PAID = Pattern.compile("\"isAccessibleForFree\"\\s*:\\s*\"?false\"?|\"paid\"\\s*:\\s*1\\b|\"paywall\"\\s*:\\s*\"hard\"", Pattern.CASE_INSENSITIVE);

    /** Liest die echte Zeit aus einer Artikelseite; -1 wenn nicht gefunden. */
    static long parseTime(String html) {
        try {
            Matcher m = P_DATUM.matcher(html);
            if (m.find()) {
                java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
                c.clear();
                c.set(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), 0);
                return c.getTimeInMillis();
            }
            Matcher i = P_ISO2.matcher(html);
            String iso = null;
            if (i.find()) iso = i.group(1) != null ? i.group(1) : i.group(2);
            if (iso == null) {
                Matcher j = P_ISO.matcher(html);
                if (j.find()) iso = j.group(1);
            }
            if (iso != null) {
                iso = iso.replace(',', '.').replaceAll("\\.\\d+", "");
                if (iso.length() == 16) iso += ":00";
                String fmtS = "yyyy-MM-dd'T'HH:mm:ss";
                boolean hasZone = iso.matches(".*(Z|[+-]\\d{2}:?\\d{2})$");
                if (iso.endsWith("Z")) iso = iso.substring(0, iso.length() - 1) + "+0000";
                else if (hasZone) iso = iso.replaceAll("([+-]\\d{2}):(\\d{2})$", "$1$2");
                SimpleDateFormat f = new SimpleDateFormat(hasZone ? fmtS + "Z" : fmtS, Locale.ENGLISH);
                if (!hasZone) f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
                Date d = f.parse(iso);
                if (d != null) return d.getTime();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /** Holt fuer noch nie gesehene Artikel (und alte Eintraege ohne echte Zeit) die echte Zeit von der Artikelseite. */
    private static int resolveTimes(Context ctx, Catalog.Feed f, List<Map<String, Object>> found) {
        SharedPreferences sp = ctx.getSharedPreferences("direct", Context.MODE_PRIVATE);
        java.util.Set<String> done = new java.util.HashSet<>();
        try {
            JSONArray a = new JSONArray(sp.getString("state_" + f.id, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                boolean ok = f.id.equals("bga") ? o.optBoolean("tok") : o.optBoolean("pc");
                if (ok || o.optInt("tries") >= 2) done.add(String.valueOf(o.getLong("sid")));
            }
        } catch (Exception ignored) {
        }
        // neueste zuerst: BGA nach Artikel-ID, BGLand24 nach Veroeffentlichung
        List<Map<String, Object>> cand = new ArrayList<>(found);
        if (f.id.equals("bga")) Collections.sort(cand, (x, y) -> Long.compare((Long) y.get("sid"), (Long) x.get("sid")));
        else Collections.sort(cand, (x, y) -> Long.compare(x.containsKey("pub") ? 0 : 1, y.containsKey("pub") ? 0 : 1) != 0
                ? Long.compare(x.containsKey("pub") ? 0 : 1, y.containsKey("pub") ? 0 : 1)
                : Long.compare(y.containsKey("pub") ? (Long) y.get("pub") : 0L, x.containsKey("pub") ? (Long) x.get("pub") : 0L));
        final List<Map<String, Object>> todo = new ArrayList<>();
        for (Map<String, Object> it : cand) {
            if (!done.contains(String.valueOf(it.get("sid")))) todo.add(it);
            if (todo.size() >= 60) break;
        }
        if (todo.isEmpty()) return 0;
        ExecutorService ex = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (final Map<String, Object> it : todo) {
            tasks.add(() -> {
                try {
                    String page = Net.download((String) it.get("url"));
                    if (!it.containsKey("pub")) {
                        long t = parseTime(page);
                        if (t > 0) it.put("pub", t);
                    }
                    if (!f.id.equals("bga")) {
                        if (PAID.matcher(page).find()) it.put("plus", true);
                        it.put("pc", true);
                    }
                } catch (Exception ignored) {
                }
                return true;
            });
        }
        int ok = 0;
        try {
            ex.invokeAll(tasks, f.id.equals("bga") ? 8 : 25, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ex.shutdownNow();
        }
        for (Map<String, Object> it : todo) {
            it.put("tried", true);
            if (f.id.equals("bga") ? it.containsKey("pub") : it.containsKey("pc")) ok++;
        }
        return ok;
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
        if (ippen(f)) {
            boolean onlyRegion = f.id.equals("chiemgau24");
            java.util.Iterator<Map.Entry<String, JSONObject>> iter = state.entrySet().iterator();
            while (iter.hasNext()) {
                JSONObject o = iter.next().getValue();
                String u = o.optString("url").split("\\?")[0];
                if (!BG_ART.matcher(u).find() || (onlyRegion && !o.optString("topic", "Region").startsWith("Region"))) iter.remove();
            }
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
                if (it.containsKey("pub")) {
                    o.put("first", (Long) it.get("pub"));
                    o.put("tok", true);
                }
                if (it.containsKey("pc")) o.put("pc", true);
                boolean done = bga ? o.optBoolean("tok") : o.optBoolean("pc");
                if (Boolean.TRUE.equals(it.get("tried")) && !done) o.put("tries", o.optInt("tries") + 1);
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
        s = s.replace("&shy;", "");
        s = FeedItem.unescape(s).replaceAll("\\s+", " ").trim();
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
