package de.bergf3x.bgawidget;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Einheitliche Themenordnung fuer alle Quellen:
 * BGL / Region / Rubrik, Außerhalb / Region / Rubrik, Deutschland & Welt / Rubrik.
 * Jede Meldung wird beim Einlesen anhand von Quell-Rubrik, Quelle und Ortsnamen im Titel einsortiert.
 */
final class Taxonomy {
    private Taxonomy() {}

    static final String BGL = "BGL";
    static final String OUT = "Außerhalb";
    static final String WORLD = "Deutschland & Welt";
    static final String[] AREAS = {BGL, OUT, WORLD};

    static final String BGD = "Berchtesgaden & Talkessel";
    static final String BRH = "Bad Reichenhall & Umgebung";
    static final String FRL = "Freilassing & Rupertiwinkel";
    static final String LK = "Ganzer Landkreis";
    static final String AMT = "Amtliches";
    static final String CHG = "Chiemgau & Traunstein";
    static final String SBG = "Salzburg";
    static final String BAY = "Bayern";

    /** Reihenfolge der Unterpunkte im Menue. */
    static final String[] ORDER = {BGD, BRH, FRL, LK, AMT, CHG, SBG, BAY,
            "Nachrichten", "Politik & Gesellschaft", "Wirtschaft", "Wissen & Digital", "Leute", "Sport", "Kultur", "Blaulicht"};

    private static final Pattern P_BGD = Pattern.compile("Berchtesgaden|Bischofswiesen|Ramsau|Schönau|Königssee|Marktschellenberg|"
            + "Watzmann|Jenner|Obersalzberg|Kehlstein|Hintersee|Hochkalter|Roßfeld|Rossfeld|Strub|Loipl|Maria Gern");
    private static final Pattern P_BRH = Pattern.compile("Reichenhall|Bayerisch Gmain|Piding|Schneizlreuth|Anger\\b|Aufham|Thumsee|"
            + "Predigtstuhl|Hochstaufen|Karlstein|Marzoll|Weißbach|Unterjettenberg|Lattengebirge|Saalach");
    private static final Pattern P_FRL = Pattern.compile("Freilassing|Ainring|Saaldorf|Surheim|Laufen|Teisendorf|Mitterfelden|Leobendorf|"
            + "Neukirchen am Teisenberg|Teisenberg");
    private static final Pattern P_BGL = Pattern.compile("Berchtesgadener Land|\\bBGL\\b|Untersberg");
    private static final Pattern P_CHG = Pattern.compile("Traunstein|Traunreut|Trostberg|Waging|Tittmoning|Kirchanschöring|Petting|Chiemsee|"
            + "Chiemgau|Ruhpolding|Inzell|Siegsdorf|Reit im Winkl|Grassau|Unterwössen|Marquartstein|Prien am Chiemsee|Palling|Fridolfing");
    private static final Pattern P_SBG = Pattern.compile("Salzburg|Hallein|Flachgau|Pongau|Pinzgau|Tennengau|Lungau|Großgmain");
    private static final Pattern P_BLAU = Pattern.compile("Polizei|Unfall|Feuerwehr|Bergwacht|Einbruch|Brand\\b|brennt|Festnahme|festgenommen|"
            + "Verletzt|verletzt|tödlich|Rettung|Vermisst|vermisst|Diebstahl|Betrug|Schockanruf|Crash|Blaulicht|Gericht|Prozess", 0);

    /** Einheitlicher Themenpfad der Meldung. */
    static String classify(FeedItem it) {
        String raw = it.topic.isEmpty() ? "Region" : it.topic;
        String src = it.feedName.toLowerCase(Locale.GERMANY);
        String title = it.title;
        String low = raw.toLowerCase(Locale.GERMANY);

        if (src.contains("landratsamt") || src.contains("nationalpark")) return BGL + "/" + AMT;

        String rubric = rubric(raw, it.category, title);
        boolean regional = low.startsWith("region") || low.startsWith("bayern") || low.equals("sport/sport aus der region")
                || src.contains("chiemgau") || src.contains("orf") || (src.contains("traunsteiner") && !low.startsWith("nachrichten"));

        // Ortsbezug im Titel schlaegt die Rubrik der Quelle
        String bglRegion = bglRegion(title);
        if (bglRegion != null) return BGL + "/" + bglRegion + "/" + rubric;

        if (!regional) return WORLD + "/" + worldRubric(raw, rubric);

        // regionale Meldung ohne Ort im Titel: Rubrik/Quelle der Seite entscheidet
        if (low.contains("berchtesgaden") && !low.contains("berchtesgadener land")) return BGL + "/" + BGD + "/" + rubric;
        if (low.contains("bad reichenhall")) return BGL + "/" + BRH + "/" + rubric;
        if (low.contains("rupertiwinkel")) {
            if (P_CHG.matcher(title).find()) return OUT + "/" + CHG + "/" + rubric;
            return BGL + "/" + FRL + "/" + rubric;
        }
        if (src.contains("orf") || low.contains("salzburg") || P_SBG.matcher(title).find()) return OUT + "/" + SBG + "/" + rubric;
        if (low.contains("traunstein") || src.contains("chiemgau") || P_CHG.matcher(title).find()
                || (src.contains("traunsteiner") && !low.contains("berchtesgadener land"))) return OUT + "/" + CHG + "/" + rubric;
        if (low.startsWith("bayern")) return OUT + "/" + BAY + "/" + rubric;
        return BGL + "/" + LK + "/" + rubric; // Anzeiger-Region, BGLand24-Polizei, Regionalsport ohne Ort
    }

    private static String bglRegion(String title) {
        if (P_BGD.matcher(title).find()) return BGD;
        if (P_BRH.matcher(title).find()) return BRH;
        if (P_FRL.matcher(title).find()) return FRL;
        if (P_BGL.matcher(title).find()) return LK;
        return null;
    }

    private static String rubric(String raw, String category, String title) {
        String l = (raw + " " + category).toLowerCase(Locale.GERMANY);
        if (l.contains("sport") || l.contains("fußball") || l.contains("wintersport")) return "Sport";
        if (l.contains("kultur") || l.contains("tv kino") || l.contains("tv & kino")) return "Kultur";
        if (l.contains("polizei") || P_BLAU.matcher(title).find()) return "Blaulicht";
        return "Nachrichten";
    }

    private static String worldRubric(String raw, String rubric) {
        if (rubric.equals("Sport") || rubric.equals("Kultur")) return rubric;
        String l = raw.toLowerCase(Locale.GERMANY);
        if (l.contains("wirtschaft")) return "Wirtschaft";
        if (l.contains("wissen") || l.contains("digital") || l.contains("netzwelt")) return "Wissen & Digital";
        if (l.contains("boulevard") || l.contains("stars") || l.contains("leben")) return "Leute";
        return "Politik & Gesellschaft";
    }

    /** Anzahl Meldungen je Themenpfad (inkl. aller Oberpfade) fuer die gewaehlten Quellen. */
    static Map<String, Integer> counts(Context ctx, int id) {
        Map<String, Integer> m = new LinkedHashMap<>();
        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> sel = Catalog.effective(all, Prefs.feeds(ctx, id));
        for (Catalog.Feed f : all) {
            if (!sel.contains(f.id)) continue;
            boolean plus = Prefs.showPlusFeed(ctx, id, f.id);
            for (FeedItem it : FeedCache.items(ctx, f.id, f.name)) {
                if (it.plus && !plus) continue;
                String p = it.topic;
                while (!p.isEmpty()) {
                    Integer c = m.get(p);
                    m.put(p, c == null ? 1 : c + 1);
                    int i = p.lastIndexOf('/');
                    p = i < 0 ? "" : p.substring(0, i);
                }
            }
        }
        return m;
    }

    /** Alle vorhandenen Themenpfade, sortiert nach fester Reihenfolge (fuer Reiter, Menue, Ausblenden). */
    static List<String> topics(Context ctx, int id) {
        List<String> out = new ArrayList<>(counts(ctx, id).keySet());
        java.util.Collections.sort(out, Taxonomy::compare);
        return out;
    }

    static int compare(String a, String b) {
        String[] x = a.split("/"), y = b.split("/");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            int c = Integer.compare(rank(x[i]), rank(y[i]));
            if (c != 0) return c;
            c = x[i].compareTo(y[i]);
            if (c != 0) return c;
        }
        return Integer.compare(x.length, y.length);
    }

    private static int rank(String s) {
        for (int i = 0; i < AREAS.length; i++) if (AREAS[i].equals(s)) return i;
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(s)) return 10 + i;
        return 99;
    }

    /** Kurze Ortsangabe fuer die Infozeile, z. B. "Berchtesgaden · Sport". */
    static String label(String path) {
        String[] p = path.split("/");
        if (p.length == 0) return "";
        if (p[0].equals(WORLD)) return p.length > 1 ? p[1] : WORLD;
        String region = p.length > 1 ? shortRegion(p[1]) : p[0];
        if (p.length > 2 && !p[2].equals("Nachrichten")) return region + " · " + p[2];
        return region;
    }

    static String shortRegion(String r) {
        switch (r) {
            case BGD: return "Berchtesgaden";
            case BRH: return "Bad Reichenhall";
            case FRL: return "Freilassing";
            case LK: return "Landkreis";
            case CHG: return "Chiemgau";
            default: return r;
        }
    }
}
