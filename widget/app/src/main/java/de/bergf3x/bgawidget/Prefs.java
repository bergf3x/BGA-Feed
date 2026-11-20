package de.bergf3x.bgawidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static final int DEFAULT_BG = 75;
    static final int DEFAULT_TEXT = 100;
    static final int DEFAULT_COLOR = 0xFFFFFF;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("bga_widget", Context.MODE_PRIVATE);
    }

    static int bg(Context c, int id) {
        return clamp(sp(c).getInt("bg_" + id, DEFAULT_BG));
    }

    static int text(Context c, int id) {
        return clamp(sp(c).getInt("tx_" + id, DEFAULT_TEXT));
    }

    /** Schriftfarbe als RGB ohne Alpha. */
    static int color(Context c, int id) {
        return sp(c).getInt("tc_" + id, DEFAULT_COLOR) & 0xFFFFFF;
    }

    static void set(Context c, int id, int bg, int text, int color, Set<String> feeds) {
        sp(c).edit()
                .putInt("bg_" + id, clamp(bg))
                .putInt("tx_" + id, clamp(text))
                .putInt("tc_" + id, color & 0xFFFFFF)
                .putStringSet("fd_" + id, new HashSet<>(feeds))
                .remove("tp_" + id)
                .apply();
    }

    /** Gewaehlte Feeds; Standard: nur der Berchtesgadener Anzeiger (wie in Version 1). */
    static Set<String> feeds(Context c, int id) {
        Set<String> s = sp(c).getStringSet("fd_" + id, null);
        if (s == null || s.isEmpty()) {
            s = new HashSet<>();
            s.add("bga");
        }
        return new HashSet<>(s);
    }

    /** Aktuell gewaehltes Thema, leer = alle. */
    static String topic(Context c, int id) {
        return sp(c).getString("tp_" + id, "");
    }

    static void setTopic(Context c, int id, String topic) {
        sp(c).edit().putString("tp_" + id, topic).apply();
    }

    static void remove(Context c, int id) {
        sp(c).edit().remove("bg_" + id).remove("tx_" + id).remove("tc_" + id)
                .remove("fd_" + id).remove("tp_" + id).remove("pl_" + id).apply();
    }

    static String feedCache(Context c, String feedId) {
        return sp(c).getString("cache_" + feedId, null);
    }

    static void setFeedCache(Context c, String feedId, String xml) {
        sp(c).edit().putString("cache_" + feedId, xml).apply();
    }

    static String status(Context c, String feedId) {
        return sp(c).getString("status_" + feedId, "");
    }

    static void setStatus(Context c, String feedId, String s) {
        sp(c).edit().putString("status_" + feedId, s).apply();
    }

    static String catalogCache(Context c) {
        return sp(c).getString("catalog", null);
    }

    static void setCatalogCache(Context c, String json) {
        sp(c).edit().putString("catalog", json).apply();
    }

    static String weather(Context c, int place) {
        return sp(c).getString("weather_" + place, null);
    }

    static long weatherTime(Context c, int place) {
        return sp(c).getLong("weather_t_" + place, 0);
    }

    static void setWeather(Context c, int place, String json) {
        sp(c).edit().putString("weather_" + place, json).putLong("weather_t_" + place, System.currentTimeMillis()).apply();
    }

    /** Naechster Abruf soll fuer alle Orte sofort neu laden. */
    static void invalidateWeather(Context c) {
        SharedPreferences.Editor e = sp(c).edit();
        for (int i = 0; i < Weather.PLACES.length; i++) e.putLong("weather_t_" + i, 0);
        e.apply();
    }

    /** Gewaehlter Wetter-Ort dieses Widgets (Index in Weather.PLACES). */
    static int place(Context c, int id) {
        int p = sp(c).getInt("pl_" + id, 0);
        return p >= 0 && p < Weather.PLACES.length ? p : 0;
    }

    static void setPlace(Context c, int id, int place) {
        sp(c).edit().putInt("pl_" + id, place).apply();
    }

    /** Zeitpunkt, zu dem dieses Handy einen Artikel zum ersten Mal gesehen hat. */
    static long firstSeen(Context c, String key) {
        SharedPreferences s = sp(c);
        long t = s.getLong("seen_" + key, 0);
        if (t == 0) {
            t = System.currentTimeMillis();
            s.edit().putLong("seen_" + key, t).apply();
        }
        return t;
    }

    static int clamp(int pct) {
        return Math.max(1, Math.min(100, pct));
    }

    /** Prozent (1-100) in Alpha-Wert (0-255). */
    static int alpha(int pct) {
        return Math.round(clamp(pct) * 255f / 100f);
    }

    /** Farbe mit Deckkraft-Prozent und optionalem Faktor (z. B. 0.7 fuer Nebentext). */
    static int argb(int rgb, int pct, float factor) {
        return (Math.round(alpha(pct) * factor) << 24) | (rgb & 0xFFFFFF);
    }
}
