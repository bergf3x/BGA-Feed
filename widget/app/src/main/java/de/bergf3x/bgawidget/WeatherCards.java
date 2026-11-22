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
    private static final String DAV_OST = "https://www.alpenverein.de/bergwetter/alpen/bayerische-alpen-ost-osterreich-nord";
    private static final String DWD_WARN = "https://www.dwd.de/DE/wetter/warnungen/warnWetter_node.html";

    /** id, Titel, Zeile darunter, Gruppe, Adresse (noch nicht bestaetigte Regionsadressen fuehren zur DAV-Uebersicht). */
    static final String[][] BERG_REGIONS = {
            {"bay_ost", "Bayerische Alpen Ost", "Berchtesgaden, Chiemgau · mit Österreich Nord", "Deutsche Alpen", DAV_OST},
            {"bay_west", "Allgäuer und Bayerische Alpen West", "mit Österreich Nord", "Deutsche Alpen", DAV},
            {"zillertal", "Zillertaler Alpen, Hohe Tauern", "Zillertal, Pinzgau, Großglockner", "Salzburger Land und Tirol", DAV},
            {"oetztal_nord", "Bündner und Ötztaler Alpen Nord, Stubaier", "Tirol West, Silvretta", "Salzburger Land und Tirol", DAV},
            {"dolomiten_nord", "Dolomiten Nord, Sarntaler Alpen", "Südtirol", "Weitere Alpenregionen", DAV},
            {"oetztal_sued", "Ötztaler Alpen Süd, Ortler, Livigno", "Südtirol, Lombardei", "Weitere Alpenregionen", DAV},
            {"gardasee", "Gardasee, Dolomiten Süd, Bergamasker", "Trentino, Lombardei", "Weitere Alpenregionen", DAV},
            {"berner", "Berner Alpen, Glarner Alpen, Voralpen", "Schweiz", "Weitere Alpenregionen", DAV},
            {"aosta", "Aosta, Walliser Alpen, Tessin", "Schweiz, Italien", "Weitere Alpenregionen", DAV},
    };
    static final Set<String> BERG_DEFAULT = new LinkedHashSet<>(java.util.Arrays.asList("bay_ost", "bay_west", "zillertal", "oetztal_nord"));

    /** Warn-Regionen (DWD, Kreis): Schluessel, Name. */
    static final String[][] WARN_REGIONS = {
            {"09172", "Berchtesgadener Land"},
            {"09189", "Chiemgau, Traunstein"},
            {"09187", "Rosenheim, Landkreis"},
    };

    private WeatherCards() {}

    static boolean any(Context c) {
        return !Prefs.bergRegions(c).isEmpty() || Prefs.regionCards(c);
    }

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
                out.add(card(BERG, r[3], r[1], r[2],
                        "Die aktuelle Vorhersage mit Schneefallgrenze, Wind, Gewitterrisiko und den Tagen bis Samstag steht auf der Seite des Deutschen Alpenvereins.",
                        r[4]));
            }
        }
        if (Prefs.regionCards(c)) {
            out.add(card(REGION, "Vorhersage", "Südbayern und Alpenrand", "DWD · mehrmals täglich neu",
                    "Die Vorhersage des Deutschen Wetterdienstes für die nächsten 24 Stunden und den Trend bis zum zehnten Tag steht auf der Originalseite.",
                    "https://www.dwd.de/DE/wetter/wetter_node.html"));
            out.add(card(REGION, "Vorhersage", "Salzburg und Salzburger Land", "GeoSphere Austria",
                    "Vorhersage und Warnungen des österreichischen Wetterdienstes GeoSphere Austria.",
                    "https://wetter.geosphere.at/"));
            out.add(card(REGION, "Radar", "Regenradar Bayern und Österreich", "Wo regnet es gerade",
                    "Das Regenradar zeigt Niederschlag der letzten und nächsten Stunden, auch für Salzburg und Berchtesgaden.",
                    "https://www.wetteronline.de/regenradar/salzburg"));
            out.add(card(REGION, "Warnlage", "Berchtesgadener Land", "DWD · Kreis",
                    "Die amtlichen Warnungen des Deutschen Wetterdienstes für den Kreis stehen auf der Seite des DWD.", DWD_WARN));
            out.add(card(REGION, "Warnlage", "Chiemgau, Landkreis Traunstein", "DWD · Kreis",
                    "Die amtlichen Warnungen des Deutschen Wetterdienstes für den Kreis stehen auf der Seite des DWD.", DWD_WARN));
        }
        return out;
    }

    /** Gehoert der Link zu einer Wetter-Karte? Dann nicht als gelesen abdunkeln. */
    static boolean isCardLink(Context c, String link) {
        for (FeedItem it : items(c)) if (it.link.equals(link)) return true;
        return link.startsWith(DAV) || link.equals(DWD_WARN);
    }
}
