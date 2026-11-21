package de.bergf3x.bgawidget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/** Amtliche DWD-Wetterwarnungen fuer den Landkreis Berchtesgadener Land (Gemeindeschluessel 09172). */
final class Warnings {
    private static final String URL = "https://www.dwd.de/DWD/warnungen/warnapp/json/warnings.json";
    private static final String AGS = "09172";
    private static final long MAX_AGE = 10 * 60 * 1000L;

    private Warnings() {}

    /** Im Hintergrund-Thread aufrufen. Speichert die staerkste aktive Warnung (Stufe 2-4) oder "keine". */
    static void refreshIfStale(Context c) {
        if (System.currentTimeMillis() - Prefs.warnTime(c) < MAX_AGE) return;
        try {
            String raw = Net.download(URL);
            int a = raw.indexOf('{');
            int b = raw.lastIndexOf('}');
            if (a < 0 || b <= a) return;
            JSONObject all = new JSONObject(raw.substring(a, b + 1)).getJSONObject("warnings");
            long now = System.currentTimeMillis();
            int best = 0;
            String bestText = "";
            int count = 0;
            Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.length() < 6 || !k.substring(1, 6).equals(AGS)) continue;
                JSONArray arr = all.optJSONArray(k);
                if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject w = arr.optJSONObject(i);
                    if (w == null) continue;
                    int level = w.optInt("level", 0);
                    long start = w.optLong("start", 0);
                    long end = w.optLong("end", 0);
                    if (level < 2 || end < now || start > now + 12L * 3600000) continue;
                    count++;
                    if (level > best) {
                        best = level;
                        bestText = w.optString("event", "Warnung");
                    }
                }
            }
            if (best == 0) {
                Prefs.setWarn(c, 0, "");
            } else {
                String t = "Warnung: " + nice(bestText) + (count > 1 ? " +" + (count - 1) : "");
                Prefs.setWarn(c, best, t);
            }
        } catch (Exception ignored) {
            // bei Fehlern den alten Stand behalten
        }
    }

    private static String nice(String s) {
        if (s == null || s.isEmpty()) return "Wetter";
        String l = s.toLowerCase(java.util.Locale.GERMANY);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    /** Farbe je Stufe. */
    static int color(int level) {
        if (level >= 4) return 0xFFFF4081;
        if (level == 3) return 0xFFFF6E6E;
        return 0xFFFFB74D;
    }
}
