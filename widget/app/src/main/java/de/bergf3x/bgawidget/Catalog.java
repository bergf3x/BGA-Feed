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
        List<String> topics = new ArrayList<>();
    }

    private Catalog() {}

    private static String lastJson;
    private static List<Feed> lastList;

    static synchronized List<Feed> load(Context c) {
        String json = Prefs.catalogCache(c);
        if (json != null && lastList != null && (json == lastJson || json.equals(lastJson))) return lastList;
        List<Feed> list = parse(json);
        if (list.isEmpty()) list = fallback();
        if (Direct.enabled(c)) addDirectOnly(list);
        lastJson = json;
        lastList = list;
        return list;
    }

    /** Im Hintergrund-Thread aufrufen. */
    static void refresh(Context c) {
        // der Katalog aendert sich selten: hoechstens alle 6 Stunden neu laden
        if (Prefs.catalogCache(c) != null && System.currentTimeMillis() - Prefs.catalogTime(c) < 6L * 3600000) return;
        refreshStatus(c);
    }

    /** Wie refresh, liefert aber eine Statuszeile (Erfolg oder Fehlergrund). */
    static String refreshStatus(Context c) {
        try {
            String json = Net.download(Net.fresh(URL));
            List<Feed> list = parse(json);
            if (list.isEmpty()) return "Katalog unlesbar (" + json.length() + " Zeichen)";
            Prefs.setCatalogCache(c, json);
            int n = 0;
            for (Feed f : list) n += f.topics.size();
            return "Katalog geladen: " + list.size() + " Quellen, " + n + " Themen";
        } catch (Exception e) {
            return "Katalog-Fehler: " + e;
        }
    }

    /** Quellen mit Bezahl-Artikeln (nur dort gibt es den Abo-Schalter). */
    static boolean hasPaywall(String id) {
        return id.equals("bga") || id.equals("tt") || id.equals("bgland24") || id.equals("chiemgau24");
    }

    /** Quellen, die selten veroeffentlichen (Behoerden, Ortsfilter): keine Warnung "ohne Neues". */
    static boolean lowVolume(String id) {
        return id.equals("lra_bgl") || id.equals("npv") || id.equals("br24");
    }

    static Feed find(List<Feed> all, String id) {
        for (Feed f : all) if (f.id.equals(id)) return f;
        return null;
    }

    /** Gewaehlte Feeds, die es noch gibt; sonst der erste Feed (z. B. wenn ein entfernter Feed gewaehlt war). */
    static java.util.Set<String> effective(List<Feed> all, java.util.Set<String> selected) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Feed f : all) if (selected.contains(f.id)) out.add(f.id);
        if (out.isEmpty() && !all.isEmpty()) out.add(all.get(0).id);
        return out;
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
                JSONArray t = o.optJSONArray("topics");
                if (t != null) for (int k = 0; k < t.length(); k++) f.topics.add(t.getString(k));
                out.add(f);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    /** Quellen, die nur die Test-App direkt vom Handy abruft (kein GitHub-Feed vorhanden). */
    private static void addDirectOnly(List<Feed> list) {
        String[][] extra = {{"merkur", "Merkur", "Bayern"}};
        for (String[] e : extra) {
            if (find(list, e[0]) != null) continue;
            Feed f = new Feed();
            f.id = e[0];
            f.name = e[1];
            f.url = e[0].equals("merkur") ? RAW + "feed_merkur.xml" : "";
            f.topics.add(e[2]);
            list.add(f);
        }
    }

    private static List<Feed> fallback() {
        List<Feed> out = new ArrayList<>();
        Feed a = new Feed();
        a.id = "bga";
        a.name = "Berchtesgadener Anzeiger";
        a.url = RAW + "feed.xml";
        a.topics.add("Region");
        a.topics.add("Nachrichten");
        a.topics.add("Sport");
        a.topics.add("Kultur");
        out.add(a);
        return out;
    }
}
