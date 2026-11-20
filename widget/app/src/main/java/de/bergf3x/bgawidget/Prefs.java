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
                .remove("fd_" + id).remove("tp_" + id).remove("pl_" + id)
                .remove("plus_" + id).remove("mute_" + id).remove("dens_" + id).apply();
    }

    /** PLUS-Artikel anzeigen (Standard: ja). */
    static boolean showPlus(Context c, int id) {
        return sp(c).getBoolean("plus_" + id, true);
    }

    /** Ausgeblendete Themen (z. B. "Boulevard" oder "Sport/Fussball"). */
    static Set<String> muted(Context c, int id) {
        Set<String> s = sp(c).getStringSet("mute_" + id, null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    /** 0 = kompakt (Standard), 1 = luftig. */
    static int density(Context c, int id) {
        return sp(c).getInt("dens_" + id, 0) == 1 ? 1 : 0;
    }

    static void setExtras(Context c, int id, boolean plus, Set<String> muted, int density) {
        sp(c).edit().putBoolean("plus_" + id, plus)
                .putStringSet("mute_" + id, new HashSet<>(muted))
                .putInt("dens_" + id, density == 1 ? 1 : 0).apply();
    }

    // ---- gelesene Artikel (global, die letzten 400 Links) ----
    static boolean isRead(Set<String> read, String link) {
        return !link.isEmpty() && read.contains(link);
    }

    static Set<String> readSet(Context c) {
        String s = sp(c).getString("read", "");
        Set<String> out = new HashSet<>();
        for (String l : s.split("\n")) if (!l.isEmpty()) out.add(l);
        return out;
    }

    static synchronized void markRead(Context c, String link) {
        if (link == null || link.isEmpty()) return;
        String s = sp(c).getString("read", "");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String l : s.split("\n")) if (!l.isEmpty() && !l.equals(link)) list.add(l);
        list.add(link);
        while (list.size() > 400) list.remove(0);
        sp(c).edit().putString("read", android.text.TextUtils.join("\n", list)).apply();
    }

    /** Naechstes Listen-Update nur aus dem Cache (ohne Netz), z. B. nach "gelesen". */
    static void setLocalOnly(Context c) {
        sp(c).edit().putBoolean("local_only", true).apply();
    }

    static boolean takeLocalOnly(Context c) {
        boolean v = sp(c).getBoolean("local_only", false);
        if (v) sp(c).edit().putBoolean("local_only", false).apply();
        return v;
    }

    // ---- Nachrichten-Status fuer "Stand" ----
    static void setNews(Context c, boolean ok) {
        SharedPreferences.Editor e = sp(c).edit().putBoolean("news_ok", ok);
        if (ok) e.putLong("news_t", System.currentTimeMillis());
        e.apply();
    }

    static boolean newsOk(Context c) {
        return sp(c).getBoolean("news_ok", true);
    }

    static long newsTime(Context c) {
        return sp(c).getLong("news_t", 0);
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
