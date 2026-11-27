/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wetter-Karten ohne eigene Daten: jede Karte fuehrt zur Originalseite (DAV, DWD, ...), dort sind die Werte aktuell.
 * Die Karten erscheinen in der Liste unter "Wetter" (Bergwetter, Region).
 */
final class WeatherCards {
    static final String TOP = "Wetter";
    static final String BERG = TOP + "/Bergwetter";
    static final String REGION = TOP + "/Region";

    private static final String DAV = "https://www.alpenverein.de/bergwetter";
    private static final String DAV_OST = DAV + "/alpen/bayerische-alpen-ost-osterreich-nord";
    private static final String DWD_WARN = "https://www.dwd.de/DE/wetter/warnungen/warnWetter_node.html";
    /** Gemeindewarnungen: der Ort steht in der Adresse, die Seite zeigt gleich dessen Warnungen (22.11.2026 geprueft). */
    static final String DWD_WARN_ORT = "https://www.dwd.de/DE/wetter/warnungen_gemeinden/warnWetter_node.html?ort=";

    /** id, Titel, Zeile darunter, Gruppe, Adresse (Regionsseiten des DAV, Adressen am 22.11.2026 geprueft). */
    static final String[][] BERG_REGIONS = {
            {"bay_ost", "Bayerische Alpen Ost", "Berchtesgaden, Chiemgau · mit Österreich Nord", "Deutsche Alpen", DAV_OST},
            {"bay_west", "Allgäuer und Bayerische Alpen West", "mit Österreich Nord", "Deutsche Alpen", DAV + "/alpen/allgauer-und-bayerische-alpen-west-osterreich-nord"},
            {"zillertal", "Zillertaler Alpen, Hohe Tauern", "Zillertal, Pinzgau, Großglockner", "Salzburger Land und Tirol", DAV + "/alpen/zillertaler-alpen-hohe-tauern"},
            {"oetztal_nord", "Bündner und Ötztaler Alpen Nord, Stubaier", "Tirol West, Silvretta", "Salzburger Land und Tirol", DAV + "/alpen/bundner-und-otztaler-alpen-nord-silvretta-stubaier-alpen"},
            {"dolomiten_nord", "Dolomiten Nord, Sarntaler Alpen", "Südtirol", "Weitere Alpenregionen", DAV + "/alpen/dolomiten-nord-sarntaler-alpen"},
            {"oetztal_sued", "Ötztaler Alpen Süd, Ortler, Livigno", "Südtirol, Lombardei", "Weitere Alpenregionen", DAV + "/alpen/otztaler-alpen-sud-ortler-livigno-alpen"},
            {"gardasee", "Gardasee, Dolomiten Süd, Bergamasker", "Trentino, Lombardei", "Weitere Alpenregionen", DAV + "/alpen/gardasee-dolomiten-sud-bergamasker-alpen"},
            {"berner", "Berner Alpen, Glarner Alpen, Voralpen", "Schweiz", "Weitere Alpenregionen", DAV + "/alpen/berner-alpen-glarner-alpen-schweizer-voralpen"},
            {"aosta", "Aosta, Walliser Alpen, Tessin", "Schweiz, Italien", "Weitere Alpenregionen", DAV + "/alpen/aosta-walliser-alpen-tessin"},
    };

    /** Gipfel und Orte je Region (Adressen von den DAV-Regionsseiten, 23.11.2026): Region-ID -> {Name, Adressende, G = Gipfel / O = Ort}. */
    static final java.util.Map<String, String[][]> BERG_STATIONS = new java.util.HashMap<>();
    static {
        BERG_STATIONS.put("bay_ost", new String[][]{
                {"Watzmann", "watzmann", "G"}, {"Wendelstein", "wendelstein", "G"}, {"Kampenwand", "kampenwand", "G"},
                {"Wilder Kaiser", "wilder-kaiser", "G"}, {"Hochkönig", "hochkonig", "G"}, {"Dachstein", "dachstein", "G"},
                {"Totes Gebirge", "totes-gebirge", "G"}, {"Großer Rettenstein", "grosser-rettenstein", "G"},
                {"Berchtesgaden", "berchtesgaden", "O"}, {"Lofer", "lofer", "O"}, {"Salzburg", "salzburg", "O"},
                {"Chiemsee", "chiemsee", "O"}, {"Tegernsee", "tegernsee", "O"}, {"Kitzbühel", "kitzbuhel", "O"},
                {"Kelchsau", "kelchsau", "O"}, {"Salzkammergut", "salzkammergut", "O"}});
        BERG_STATIONS.put("bay_west", new String[][]{
                {"Zugspitze", "zugspitze", "G"}, {"Hochvogel", "hochvogel", "G"}, {"Benediktenwand", "benediktenwand", "G"},
                {"Birkkarspitze", "birkkarspitze", "G"}, {"Hohe Munde", "hohe-munde", "G"}, {"Kanisfluh", "kanisfluh", "G"},
                {"Parseierspitze", "parseierspitze", "G"}, {"Schesaplana", "schesaplana", "G"},
                {"München", "munchen", "O"}, {"Oberstdorf", "oberstdorf", "O"}, {"Füssen", "fussen", "O"},
                {"Kempten", "kempten", "O"}, {"Oberammergau", "oberammergau", "O"}, {"Innsbruck", "innsbruck", "O"},
                {"Achensee", "achensee", "O"}, {"Lech", "lech", "O"}, {"Mittelberg", "mittelberg", "O"},
                {"Bludenz", "bludenz", "O"}, {"Bregenz", "bregenz", "O"}});
        BERG_STATIONS.put("zillertal", new String[][]{
                {"Glocknergruppe", "glocknergruppe", "G"}, {"Großvenediger", "grossvenediger", "G"},
                {"Hoher Sonnblick", "hoher-sonnblick", "G"}, {"Kitzsteinhorn", "kitzsteinhorn", "G"},
                {"Hochalmspitze", "hochalmspitze", "G"}, {"Großer Löffler", "grosser-loffler", "G"},
                {"Hochfeilergruppe", "hochfeilergruppe", "G"}, {"Kreuzeckgruppe", "kreuzeckgruppe", "G"},
                {"Petzeck", "petzeck", "G"}, {"Rastkogel", "rastkogel", "G"}, {"Rieserfernergruppe", "rieserfernergruppe", "G"},
                {"Villgratner Berge", "villgratner-berge", "G"},
                {"Zillertal", "zillertal", "O"}, {"Badgastein", "badgastein", "O"}, {"Mallnitz", "mallnitz", "O"},
                {"Brenner", "brenner", "O"}});
        BERG_STATIONS.put("oetztal_nord", new String[][]{
                {"Piz Buin", "piz-buin", "G"}, {"Piz Ela", "piz-ela", "G"}, {"Kaunergrat", "kaunergrat", "G"},
                {"Zuckerhütl", "zuckerhutl", "G"},
                {"St. Moritz", "st-moritz", "O"}, {"Chur", "chur", "O"}, {"Oetz", "oetz", "O"},
                {"Stubaital (Neustift)", "stubaital-neustift", "O"}});
    }
    static final String OPEN = "oeffnen:";
    static final Set<String> BERG_DEFAULT = new LinkedHashSet<>(java.util.Arrays.asList("bay_ost", "bay_west", "zillertal", "oetztal_nord"));

    /** Warn-Regionen (DWD, Kreis): Schluessel, Name, Ort fuer die DWD-Suche. */
    static final String[][] WARN_REGIONS = {
            {"09172", "Berchtesgadener Land", "Berchtesgaden"},
            {"09189", "Chiemgau, Traunstein", "Traunstein"},
            {"09187", "Rosenheim, Landkreis", "Rosenheim"},
    };

    private WeatherCards() {}
    private static FeedItem card(String path, String group, String title, String sub, String text, String link) {
        FeedItem it = new FeedItem();
        it.topic = path;
        it.rawTopic = path;
        it.paper = path;
        it.category = group;
        it.feedName = TOP;
        it.title = title;
        it.sub = sub;
        it.description = text;
        it.link = link;
        return it;
    }

    /** Alle Karten laut Einstellungen, in fester Reihenfolge. */
    static List<FeedItem> items(Context c) {
        List<FeedItem> out = new ArrayList<>();
        Set<String> berg = Prefs.bergRegions(c);
        if (!berg.isEmpty()) {
            out.add(card(BERG, "Wochenende", "Bergbericht fürs Wochenende", "DAV · erscheint zum Wochenende",
                    "Der Bergbericht des Deutschen Alpenvereins für das kommende Wochenende. Er erscheint auf der Seite des DAV.",
                    "https://www.alpenverein.de/thema/bergbericht"));
            for (String[] r : BERG_REGIONS) {
                if (!berg.contains(r[0])) continue;
                String[][] st = BERG_STATIONS.get(r[0]);
                if (st == null) {
                    out.add(card(BERG, r[3], r[1], r[2],
                            "Die aktuelle Vorhersage mit Schneefallgrenze, Wind, Gewitterrisiko und den Tagen bis Samstag steht auf der Seite des Deutschen Alpenvereins.",
                            r[4]));
                    continue;
                }
                // Tipp oeffnet das Fenster mit den Reitern Orte und Gipfel (Link beginnt mit "oeffnen:")
                out.add(card(BERG, r[3], r[1], r[2] + " · " + st.length + " Links",
                        "Orte und Gipfel dieser Region mit Direktlinks zum Alpenverein.", OPEN + r[0]));
            }
        }
        if (Prefs.regionCards(c)) {
            out.add(card(REGION, "Vorhersage", "Berchtesgaden", "WetterOnline · 4 Tage und Stundenwerte",
                    "Die Vorhersage für Berchtesgaden mit Temperatur, Niederschlag und Sonnenstunden steht auf der Seite von WetterOnline.",
                    "https://www.wetteronline.de/wetter/berchtesgaden"));
            out.add(card(REGION, "Vorhersage", "Salzburg", "WetterOnline · 4 Tage und Stundenwerte",
                    "Die Vorhersage für Salzburg mit Temperatur, Niederschlag und Sonnenstunden steht auf der Seite von WetterOnline.",
                    "https://www.wetteronline.de/wetter/salzburg"));
            out.add(card(REGION, "Radar", "Regenradar Berchtesgaden", "Wo regnet es gerade",
                    "Das Regenradar zeigt Niederschlag der letzten und nächsten Stunden rund um Berchtesgaden.",
                    "https://www.wetteronline.de/regenradar/berchtesgaden"));
            out.add(card(REGION, "Radar", "Regenradar Salzburg", "Wo regnet es gerade",
                    "Das Regenradar zeigt Niederschlag der letzten und nächsten Stunden rund um Salzburg.",
                    "https://www.wetteronline.de/regenradar/salzburg"));
            for (String[] w : WARN_REGIONS) {
                String wr = Prefs.warnRegion(c, w[0]);
                out.add(card(REGION, "Warnlage", w[1], wr.isEmpty() ? "DWD · amtliche Warnungen" : wr.equals("-") ? "Keine Warnung aktiv · DWD" : wr,
                        "Die amtlichen Warnungen des Deutschen Wetterdienstes für " + w[2] + " stehen auf der Seite des DWD.",
                        DWD_WARN_ORT + java.net.URLEncoder.encode(w[2], java.nio.charset.StandardCharsets.UTF_8)));
            }
        }
        return out;
    }

    static String[] region(String id) {
        if (id == null) return null;
        for (String[] r : BERG_REGIONS) if (r[0].equals(id)) return r;
        return null;
    }

    static String stationUrl(String regionId, String slug) {
        String[] r = region(regionId);
        return r == null ? DAV : r[4] + "/" + slug;
    }

    /** Gehoert der Link zu einer Wetter-Karte? Dann nicht als gelesen abdunkeln. */
    static boolean isCardLink(Context c, String link) {
        for (FeedItem it : items(c)) if (it.link.equals(link)) return true;
        return link.startsWith(DAV) || link.equals(DWD_WARN) || link.startsWith(DWD_WARN_ORT);
    }
}
