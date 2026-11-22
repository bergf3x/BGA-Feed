/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/** Amtliche DWD-Wetterwarnungen fuer den Landkreis Berchtesgadener Land (Gemeindeschluessel 09172). */
final class Warnings {
    private static final String URL = "https://www.dwd.de/DWD/warnungen/warnapp/json/warnings.json";
    private static final String AGS = "09172";

    private Warnings() {}

    /** Im Hintergrund-Thread aufrufen. Speichert die staerkste aktive Warnung (Stufe 2-4) oder "keine". */
    static void refreshIfStale(Context c) {
        refreshIfStale(c, Prefs.wxMaxAge(c));
    }

    static void refreshIfStale(Context c, long maxAge) {
        if (Prefs.fresh(Prefs.warnTime(c), maxAge)) return;
        try {
            String raw = Net.download(URL);
            int a = raw.indexOf('{');
            int b = raw.lastIndexOf('}');
            if (a < 0 || b <= a) return;
            JSONObject all = new JSONObject(raw.substring(a, b + 1)).getJSONObject("warnings");
            long now = System.currentTimeMillis();
            java.util.Set<String> extra = Prefs.warnRegions(c);
            java.util.Map<String, int[]> perRegion = new java.util.HashMap<>();
            java.util.Map<String, String> perText = new java.util.HashMap<>();
            for (String r : extra) { perRegion.put(r, new int[2]); perText.put(r, ""); }
            java.util.Map<String, String> shown = new java.util.HashMap<>();
            java.util.Map<String, Integer> shownLv = new java.util.HashMap<>();
            java.util.Map<String, Integer> shownN = new java.util.HashMap<>();
            for (String[] r : WeatherCards.WARN_REGIONS) { shown.put(r[0], ""); shownLv.put(r[0], 0); shownN.put(r[0], 0); }
            int best = 0;
            String bestText = "";
            int count = 0;
            Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.length() < 6) continue;
                String ags = k.substring(1, 6);
                if (shown.containsKey(ags)) {
                    JSONArray da = all.optJSONArray(k);
                    for (int i = 0; da != null && i < da.length(); i++) {
                        JSONObject w = da.optJSONObject(i);
                        if (w == null) continue;
                        int lv = w.optInt("level", 0);
                        long end = w.optLong("end", 0);
                        if (lv < 2 || end < now || w.optLong("start", 0) > now + 12L * 3600000) continue;
                        shownN.put(ags, shownN.get(ags) + 1);
                        if (lv > shownLv.get(ags)) {
                            shownLv.put(ags, lv);
                            java.util.Calendar ce = java.util.Calendar.getInstance();
                            ce.setTimeInMillis(end);
                            java.util.Calendar cn = java.util.Calendar.getInstance();
                            boolean same = ce.get(java.util.Calendar.DAY_OF_YEAR) == cn.get(java.util.Calendar.DAY_OF_YEAR)
                                    && ce.get(java.util.Calendar.YEAR) == cn.get(java.util.Calendar.YEAR);
                            String until = new java.text.SimpleDateFormat(same ? "HH:mm" : "EEE HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(end));
                            shown.put(ags, "Warnung: " + nice(w.optString("event", "Wetter")) + " bis " + until);
                        }
                    }
                }
                int[] pr = perRegion.get(ags);
                if (pr != null) {
                    JSONArray ra = all.optJSONArray(k);
                    for (int i = 0; ra != null && i < ra.length(); i++) {
                        JSONObject w = ra.optJSONObject(i);
                        if (w == null) continue;
                        int lv = w.optInt("level", 0);
                        if (lv < 3 || w.optLong("end", 0) < now || w.optLong("start", 0) > now + 12L * 3600000) continue;
                        pr[1]++;
                        if (lv > pr[0]) { pr[0] = lv; perText.put(ags, "Warnung: " + nice(w.optString("event", "Wetter"))); }
                    }
                }
                if (!ags.equals(AGS)) continue;
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
            for (String[] r : WeatherCards.WARN_REGIONS) {
                String t = shown.get(r[0]);
                int n = shownN.get(r[0]);
                Prefs.setWarnRegion(c, r[0], t.isEmpty() ? "-" : t + (n > 1 ? " +" + (n - 1) : ""));
            }
            if (best == 0) {
                Prefs.setWarn(c, 0, "");
            } else {
                String t = "Warnung: " + nice(bestText) + (count > 1 ? " +" + (count - 1) : "");
                Prefs.setWarn(c, best, t);
                if (extra.contains(AGS)) Notifier.warn(c, best, t, "Berchtesgadener Land", AGS);
            }
            for (String[] r : WeatherCards.WARN_REGIONS) {
                if (r[0].equals(AGS) || !extra.contains(r[0])) continue;
                int[] pr = perRegion.get(r[0]);
                if (pr != null && pr[0] > 0) Notifier.warn(c, pr[0], perText.get(r[0]) + (pr[1] > 1 ? " +" + (pr[1] - 1) : ""), r[1], r[0]);
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
