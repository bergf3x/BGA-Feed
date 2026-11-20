package de.bergf3x.bgawidget;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liest die Startseite von alpin.de (kein RSS vorhanden) direkt auf dem Handy aus. */
final class AlpinParser {
    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\b[^>]*?href=[\"']([^\"']*/(\\d{4,9})/(?:artikel|galerie|produkttest|tour)_[^\"']*)[\"'][^>]*>(.*?)</a>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern H1 =
            Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern H2 =
            Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HAS_WORD = Pattern.compile("[A-Za-zÄÖÜäöüß]{3,}");
    private static final Pattern SKIP =
            Pattern.compile("[\\d:,.\\s]*(Std\\.?|Hm|km|Min\\.?)?");
    private static final String BASE = "https://www.alpin.de";

    private static final String[][] TOPICS = {
            {"/home/news/", "News"},
            {"/touren/bergbericht/", "Bergbericht"},
            {"/touren/", "Touren"},
            {"/tests-produkte/", "Tests & Produkte"},
            {"/sicher-am-berg/", "Sicherheit & Technik"},
            {"/home/interviews/", "Interviews"},
            {"/home/historisches/", "Historie"},
            {"/home/olaf-klaert-das-schon/", "Ratgeber"},
            {"/foto-video/", "Foto & Video"},
    };

    private AlpinParser() {}

    static List<FeedItem> parse(String html, String feedName) {
        List<FeedItem> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        Matcher am = ANCHOR.matcher(html);
        while (am.find() && out.size() < 40) {
            String href = am.group(1);
            String id = am.group(2);
            String inner = am.group(3);
            if (seen.contains(id)) continue;

            String title;
            String category = "";
            String teaser = "";
            Matcher m1 = H1.matcher(inner);
            Matcher m2 = H2.matcher(inner);
            boolean has1 = m1.find();
            boolean has2 = m2.find();
            if (has1) {
                title = text(m1.group(1));
                if (has2) category = text(m2.group(1));
            } else if (has2) {
                title = text(m2.group(1));
                List<String> parts = parts(inner);
                int idx = parts.indexOf(title);
                if (idx >= 0) {
                    if (idx > 0) category = parts.get(idx - 1);
                    if (idx + 1 < parts.size()) teaser = parts.get(idx + 1);
                }
            } else {
                continue; // reiner Bild-Link ohne Titel; der Textlink kommt evtl. spaeter
            }
            if (title.isEmpty()) continue;
            seen.add(id);

            String path = href.startsWith(BASE) ? href.substring(BASE.length()) : href;
            FeedItem it = new FeedItem();
            it.title = title;
            it.link = href.startsWith("http") ? href : BASE + href;
            it.category = category;
            it.description = teaser;
            it.topic = topicOf(path);
            it.feedName = feedName;
            out.add(it);
        }
        return out;
    }

    static String toXml(List<FeedItem> items) {
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss><channel>");
        for (FeedItem it : items) {
            sb.append("<item><title>").append(esc(it.title)).append("</title><link>")
                    .append(esc(it.link)).append("</link><pubDate>")
                    .append(fmt.format(new Date(it.time))).append("</pubDate><category>")
                    .append(esc(it.category)).append("</category><topic>")
                    .append(esc(it.topic)).append("</topic><description>")
                    .append(esc(it.description)).append("</description></item>");
        }
        return sb.append("</channel></rss>").toString();
    }

    private static String topicOf(String path) {
        for (String[] t : TOPICS) if (path.startsWith(t[0])) return t[1];
        return "Sonstiges";
    }

    private static List<String> parts(String art) {
        List<String> out = new ArrayList<>();
        String s = art.replaceAll("(?is)<(script|style)\\b.*?</\\1>", " ").replaceAll("<[^>]+>", "\n");
        for (String line : s.split("\n")) {
            String t = text(line);
            if (!t.isEmpty() && HAS_WORD.matcher(t).find() && !SKIP.matcher(t).matches()) out.add(t);
        }
        return out;
    }

    private static String text(String html) {
        String s = html.replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").replace("&apos;", "'");
        Matcher m = Pattern.compile("&#(\\d+);").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
