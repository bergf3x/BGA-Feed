/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
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
    /** Rubrik, wie die Quelle sie liefert (vor der Einordnung). */
    String rawTopic = "";
    /** Pfad in der Zeitungs-Sicht ("Zeitungen/BGLand24/Berchtesgaden"). */
    String paper = "";
    String description = "";
    String pubDate = "";
    String feedName = "";
    /** Adresse des Vorschaubildes (leer, wenn die Quelle keins liefert). */
    String image = "";
    long time = 0;
    boolean plus = false;
    /** Gemerkter Artikel, den die Quelle nicht mehr liefert. */
    boolean gone = false;
    /** Platzhalter fuer eine Medien-Ueberschrift in der Liste (title = Name des Mediums). */
    boolean header = false;
    /** Zeile unter dem Titel bei Wetter-Karten (statt Rubrik und Zeit). */
    String sub = "";

    boolean weather() {
        return topic.startsWith(WeatherCards.TOP + "/");
    }

    /** "Region · vor 12 Min." fuer die Zeile unter dem Titel. */
    String meta() {
        if (!sub.isEmpty()) return sub;
        String leaf = Taxonomy.label(topic);
        if (leaf.isEmpty()) leaf = category.trim();
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
    /** Kurzname der Quelle fuer die Meta-Zeile. */
    static String shortSource(String feedName) {
        String low = feedName.toLowerCase(Locale.GERMANY);
        if (low.contains("anzeiger")) return "Anzeiger";
        if (low.contains("traunsteiner")) return "Tagblatt";
        if (low.contains("bgland")) return "BGLand24";
        if (low.contains("chiemgau")) return "chiemgau24";
        if (low.contains("orf")) return "ORF";
        if (low.contains("landratsamt")) return "Landratsamt";
        if (low.contains("nationalpark")) return "Nationalpark";
        if (low.contains("br24")) return "BR24";
        if (low.contains("merkur")) return "Merkur";
        return feedName;
    }

    int accentColor() {
        String leaf = topic.substring(topic.lastIndexOf('/') + 1);
        if (weather()) return 0xFF5AA9FF;
        if (leaf.equals("Sport")) return 0xFFF0997B;
        if (leaf.equals("Kultur")) return 0xFFED93B1;
        if (leaf.equals("Blaulicht")) return 0xFFE57373;
        if (topic.startsWith(Taxonomy.BGL)) return 0xFF5DCAA5;
        if (topic.startsWith(Taxonomy.OUT)) return 0xFFAFA9EC;
        return 0xFF85B7EB;
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
        if (t.startsWith(Taxonomy.PAPERS)) return paper.equals(t) || paper.startsWith(t + "/");
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
                    else if (cur != null && "enclosure".equals(tag)) {
                        String u = p.getAttributeValue(null, "url");
                        if (u != null && u.startsWith("http")) cur.image = u.trim();
                    }
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
                        cur.rawTopic = cur.topic;
                        cur.topic = Taxonomy.classify(cur);
                        cur.paper = Taxonomy.paper(cur);
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
