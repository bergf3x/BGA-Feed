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
    String title = "";
    String link = "";
    String category = "";
    String topic = "";
    String description = "";
    String pubDate = "";
    String feedName = "";
    long time = 0;
    boolean plus = false;

    /** "Region · vor 12 Min." fuer die Zeile unter dem Titel. */
    String meta() {
        String leaf = topic.isEmpty() ? category.trim() : topic.substring(topic.lastIndexOf('/') + 1);
        if (leaf.isEmpty()) leaf = topic.isEmpty() ? "Region" : leaf;
        String t = timeText();
        return t.isEmpty() ? leaf : leaf + " · " + t;
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
        long diff = System.currentTimeMillis() - time;
        if (diff >= 0 && diff < 60L * 60000) return "vor " + Math.max(1, diff / 60000) + " Min.";
        Date d = new Date(time);
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTime(d);
        boolean today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        return new SimpleDateFormat(today ? "HH:mm" : "dd.MM. HH:mm", Locale.GERMANY).format(d);
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
                        cur.title = cur.title.trim();
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
