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
    String pubDate = "";

    /** Rubrik und Zeit fuer die Zeile unter dem Titel. */
    String meta() {
        String time = "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            Date d = in.parse(pubDate.trim());
            if (d != null) {
                Calendar a = Calendar.getInstance();
                Calendar b = Calendar.getInstance();
                b.setTime(d);
                boolean today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                        && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
                time = new SimpleDateFormat(today ? "HH:mm" : "dd.MM. HH:mm", Locale.GERMANY).format(d);
            }
        } catch (Exception ignored) {
        }
        String cat = category.trim();
        if (cat.isEmpty()) return time;
        if (time.isEmpty()) return cat;
        return cat + " · " + time;
    }

    static List<FeedItem> parse(String xml) {
        List<FeedItem> out = new ArrayList<>();
        if (xml == null) return out;
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
                    else if ("pubDate".equals(tag)) cur.pubDate += t;
                } else if (e == XmlPullParser.END_TAG) {
                    if ("item".equals(p.getName()) && cur != null) {
                        cur.title = cur.title.trim();
                        cur.link = cur.link.trim();
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
