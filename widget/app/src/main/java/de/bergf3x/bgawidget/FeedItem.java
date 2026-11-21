package de.bergf3x.bgawidget;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class FeedItem {
    /** Wandelt HTML-Entities (&raquo;, &#8222; ...) in echte Zeichen um. */
    static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        for (int i = 0; i < 2 && s.indexOf('&') >= 0; i++) {
            s = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return s.replace('\u00A0', ' ').replace("\uFFFC", "");
    }

    String title = "";
    String link = "";
    String category = "";
    String topic = "";
    String description = "";
    String pubDate = "";
    String feedName = "";
    long time = 0;
    boolean plus = false;
    /** Platzhalter fuer eine Medien-Ueberschrift in der Liste (title = Name des Mediums). */
    boolean header = false;

    /** "Region · vor 12 Min." fuer die Zeile unter dem Titel. */
    String meta() {
        String leaf = topic.isEmpty() ? category.trim() : topic.substring(topic.lastIndexOf('/') + 1);
        if (leaf.isEmpty()) leaf = topic.isEmpty() ? "Region" : leaf;
        String t = timeText();
        return t.isEmpty() ? leaf : leaf + " · " + t;
    }

    /** "(berchtesgadener-anzeiger.de)": woher der Artikel stammt, aus dem Link; leer wenn kein Web-Link. */
    static String source(String link) {
        try {
            if (link == null || !(link.startsWith("http://") || link.startsWith("https://"))) return "";
            String h = android.net.Uri.parse(link).getHost();
            if (h == null || h.isEmpty()) return "";
            if (h.startsWith("www.")) h = h.substring(4);
            return "(" + h + ")";
        } catch (Exception e) {
            return "";
        }
    }

    /** Hausfarbe eines Mediums (nur die Farbe, kein Logo): Anzeiger blau, BGLand24 gelb, sonst Orange. */
    static int mediumColor(String name) {
        String low = name.toLowerCase(Locale.GERMANY);
        if (low.contains("bgland")) return 0xFFF7DD45;
        if (low.contains("anzeiger")) return 0xFF459BDB;
        if (low.contains("chiemgau")) return 0xFF5BBF8A;
        if (low.contains("orf")) return 0xFFE5484D;
        return 0xFFF0997B;
    }

    int accentColor() {
        String parent = topic.isEmpty() ? "Region" : topic.split("/")[0];
        switch (parent) {
            case "Region": return 0xFF5DCAA5;
            case "Nachrichten": return 0xFF85B7EB;
            case "Sport": return 0xFFF0997B;
            case "Kultur": return 0xFFED93B1;
            default: return 0xFFAFA9EC;
        }
    }

    String timeText() {
        if (time == 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - time;
        if (diff < 60L * 60000) return "vor " + Math.max(1, diff / 60000) + " Min.";
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(time);
        a.set(Calendar.HOUR_OF_DAY, 0); a.set(Calendar.MINUTE, 0); a.set(Calendar.SECOND, 0); a.set(Calendar.MILLISECOND, 0);
        b.set(Calendar.HOUR_OF_DAY, 0); b.set(Calendar.MINUTE, 0); b.set(Calendar.SECOND, 0); b.set(Calendar.MILLISECOND, 0);
        long dayDiff = Math.round((a.getTimeInMillis() - b.getTimeInMillis()) / 86400000.0);
        Date d = new Date(time);
        if (dayDiff <= 0) return "heute " + new SimpleDateFormat("HH:mm", Locale.GERMANY).format(d);
        if (dayDiff == 1) return "gestern " + new SimpleDateFormat("HH:mm", Locale.GERMANY).format(d);
        return new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(d);
    }

    /** Neu = juengerer als 12 Stunden. */
    boolean isFresh() {
        return time != 0 && System.currentTimeMillis() - time < 12L * 3600000;
    }

    /** Gehoert der Artikel zu diesem Thema (gleich oder Unterthema)? */
    boolean inTopic(String t) {
        String mine = topic.isEmpty() ? "Region" : topic;
        return mine.equals(t) || mine.startsWith(t + "/");
    }

    static List<FeedItem> parse(String xml, String feedName) {
        List<FeedItem> out = new ArrayList<>();
        if (xml == null) return out;
        SimpleDateFormat in = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(xml));
            FeedItem cur = null;
            String tag = null;
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
                if (e == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("item".equals(tag)) cur = new FeedItem();
                } else if (e == XmlPullParser.TEXT && cur != null && tag != null) {
                    String t = p.getText();
                    if ("title".equals(tag)) cur.title += t;
                    else if ("link".equals(tag)) cur.link += t;
                    else if ("category".equals(tag)) cur.category += t;
                    else if ("topic".equals(tag)) cur.topic += t;
                    else if ("description".equals(tag)) cur.description += t;
                    else if ("pubDate".equals(tag)) cur.pubDate += t;
                } else if (e == XmlPullParser.END_TAG) {
                    if ("item".equals(p.getName()) && cur != null) {
                        cur.title = unescape(cur.title).trim();
                        cur.description = unescape(cur.description);
                        if (cur.title.startsWith("\uD83D\uDD12")) {
                            cur.plus = true;
                            cur.title = cur.title.substring(2).trim();
                        }
                        cur.link = cur.link.trim();
                        cur.topic = cur.topic.trim();
                        cur.description = cur.description.trim();
                        cur.feedName = feedName;
                        try {
                            Date d = in.parse(cur.pubDate.trim());
                            if (d != null) cur.time = d.getTime();
                        } catch (Exception ignored) {
                        }
                        if (!cur.title.isEmpty()) out.add(cur);
                        cur = null;
                    }
                    tag = null;
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
