package de.bergf3x.bgawidget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Liste der verfuegbaren Feeds samt Themen (feeds.json im Repo, mit eingebautem Notfall-Stand). */
final class Catalog {
    static final String URL = "https://raw.githubusercontent.com/bergf3x/BGA-Feed/main/feeds.json";
    private static final String RAW = "https://raw.githubusercontent.com/bergf3x/BGA-Feed/main/";

    static final class Feed {
        String id;
        String name;
        String url;
        String direct = "";
        List<String> topics = new ArrayList<>();
    }

    private Catalog() {}

    static List<Feed> load(Context c) {
        List<Feed> list = parse(Prefs.catalogCache(c));
        return list.isEmpty() ? fallback() : list;
    }

    /** Im Hintergrund-Thread aufrufen. */
    static void refresh(Context c) {
        try {
            String json = Net.download(Net.fresh(URL));
            if (!parse(json).isEmpty()) Prefs.setCatalogCache(c, json);
        } catch (Exception ignored) {
        }
    }

    static Feed find(List<Feed> all, String id) {
        for (Feed f : all) if (f.id.equals(id)) return f;
        return null;
    }

    /** Alle Themen der gewaehlten Feeds, ohne Doppelte, in Katalog-Reihenfolge. */
    static List<String> topicsFor(List<Feed> all, java.util.Set<String> selected) {
        List<String> out = new ArrayList<>();
        for (Feed f : all) {
            if (!selected.contains(f.id)) continue;
            for (String t : f.topics) if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    private static List<Feed> parse(String json) {
        List<Feed> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONObject(json).getJSONArray("feeds");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Feed f = new Feed();
                f.id = o.getString("id");
                f.name = o.getString("name");
                f.url = o.optString("url", "");
                f.direct = o.optString("direct", "");
                JSONArray t = o.optJSONArray("topics");
                if (t != null) for (int k = 0; k < t.length(); k++) f.topics.add(t.getString(k));
                out.add(f);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    private static List<Feed> fallback() {
        List<Feed> out = new ArrayList<>();
        Feed a = new Feed();
        a.id = "bga";
        a.name = "Berchtesgadener Anzeiger";
        a.url = RAW + "feed.xml";
        a.topics.add("Region");
        a.topics.add("Nachrichten");
        a.topics.add("Regionalsport");
        a.topics.add("Sport");
        a.topics.add("Kultur");
        out.add(a);
        Feed b = new Feed();
        b.id = "alpin";
        b.name = "ALPIN";
        b.url = "";
        b.direct = "https://www.alpin.de/";
        for (String t : new String[]{"News", "Touren", "Bergbericht", "Tests & Produkte",
                "Sicherheit & Technik", "Interviews", "Historie", "Foto & Video", "Ratgeber"}) {
            b.topics.add(t);
        }
        out.add(b);
        return out;
    }
}
