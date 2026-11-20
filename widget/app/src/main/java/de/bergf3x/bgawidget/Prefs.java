package de.bergf3x.bgawidget;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FEED_URL =
            "https://raw.githubusercontent.com/bergf3x/BGA-Feed/main/feed.xml";
    static final int DEFAULT_BG = 75;
    static final int DEFAULT_TEXT = 100;

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

    static void set(Context c, int id, int bg, int text) {
        sp(c).edit().putInt("bg_" + id, clamp(bg)).putInt("tx_" + id, clamp(text)).apply();
    }

    static void remove(Context c, int id) {
        sp(c).edit().remove("bg_" + id).remove("tx_" + id).apply();
    }

    static String cache(Context c) {
        return sp(c).getString("cache", null);
    }

    static void setCache(Context c, String xml) {
        sp(c).edit().putString("cache", xml).apply();
    }

    static int clamp(int pct) {
        return Math.max(1, Math.min(100, pct));
    }

    /** Prozent (1-100) in Alpha-Wert (0-255). */
    static int alpha(int pct) {
        return Math.round(clamp(pct) * 255f / 100f);
    }
}
