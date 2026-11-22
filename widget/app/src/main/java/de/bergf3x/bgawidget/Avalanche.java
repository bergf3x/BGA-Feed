/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Lawinenlagebericht des LWD Bayern (Region Berchtesgadener Alpen, DE-BY-60) ueber avalanche.report. */
final class Avalanche {
    private static final String REGION = "DE-BY-60";
    private static final long MAX_AGE = 3 * 3600000L;
    private static final String[] NAME = {"", "gering", "mäßig", "erheblich", "groß", "sehr groß"};

    private Avalanche() {}

    /** Im Hintergrund-Thread aufrufen. Ausserhalb der Saison (kein Bericht) bleibt der Text leer. */
    static void refreshIfStale(Context c) {
        if (System.currentTimeMillis() - Prefs.avalTime(c) < Math.max(MAX_AGE, Prefs.wxMs(c))) return;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
        f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        String d = f.format(new Date());
        try {
            String json = Net.download("https://static.avalanche.report/eaws_bulletins/" + d + "/" + d + "-DE-BY.json");
            JSONArray arr = new JSONObject(json).getJSONArray("bulletins");
            int best = 0;
            int bestFrom = -1;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject b = arr.getJSONObject(i);
                if (!hasRegion(b.optJSONArray("regions"))) continue;
                JSONArray dr = b.optJSONArray("dangerRatings");
                if (dr == null) continue;
                for (int k = 0; k < dr.length(); k++) {
                    JSONObject r = dr.getJSONObject(k);
                    int lv = level(r.optString("mainValue", ""));
                    if (lv > best || (lv == best && lv > 0 && bestFrom < 0)) {
                        best = lv;
                        JSONObject el = r.optJSONObject("elevation");
                        bestFrom = el != null && el.has("lowerBound") ? parse(el.optString("lowerBound")) : -1;
                    }
                }
            }
            if (best == 0) {
                Prefs.setAval(c, "");
                return;
            }
            String t = "Lawinen: Stufe " + best + " (" + NAME[best] + ")" + (bestFrom > 0 ? " ab " + bestFrom + " m" : "");
            Prefs.setAval(c, t);
        } catch (java.io.IOException e) {
            // kein Bericht (z. B. HTTP 404 ausserhalb der Saison): leer, aber nicht staendig neu versuchen
            if (String.valueOf(e.getMessage()).contains("404")) Prefs.setAval(c, "");
        } catch (Exception ignored) {
        }
    }

    private static boolean hasRegion(JSONArray regions) {
        if (regions == null) return false;
        for (int i = 0; i < regions.length(); i++) {
            JSONObject r = regions.optJSONObject(i);
            if (r != null && REGION.equals(r.optString("regionID"))) return true;
        }
        return false;
    }

    private static int level(String v) {
        switch (v) {
            case "low": return 1;
            case "moderate": return 2;
            case "considerable": return 3;
            case "high": return 4;
            case "very_high": return 5;
            default: return 0;
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
