package de.bergf3x.bgawidget;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static final int DEFAULT_BG = 75;
    static final int DEFAULT_TEXT = 100;
    static final int DEFAULT_COLOR = 0xFFFFFF;

    /** Kennung fuer die Einstellungen der Vollbild-App (echte Widget-Kennungen sind immer groesser als 0). */
    static final int APP = -1;

    private Prefs() {}

    static SharedPreferences sp(Context c) {
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

    /** Eigene Farbe fuer Nebenzeilen (-1 = wie Titel). */
    static int colorSub(Context c, int id) {
        return sp(c).getInt("tcs_" + id, -1);
    }

    /** Eigene Farbe fuer den Wetterbereich (-1 = wie Titel). */
    static int colorWx(Context c, int id) {
        return sp(c).getInt("tcw_" + id, -1);
    }

    /** Zweite Farbe fuer den Verlauf der Nebenzeilen / des Wetterbereichs (-1 = kein Verlauf). */
    static int colorSub2(Context c, int id) {
        return sp(c).getInt("tcs2_" + id, -1);
    }

    static int colorWx2(Context c, int id) {
        return sp(c).getInt("tcw2_" + id, -1);
    }

    static void setSubWx(Context c, int id, int sub, int wx) {
        sp(c).edit().putInt("tcs_" + id, sub).putInt("tcw_" + id, wx).apply();
    }

    static void setColors2(Context c, int id, int sub2, int wx2) {
        sp(c).edit().putInt("tcs2_" + id, sub2).putInt("tcw2_" + id, wx2).apply();
    }

    static boolean compact(Context c, int id) {
        return sp(c).getBoolean("cmp_" + id, false);
    }

    static boolean warnNotify(Context c) {
        return sp(c).getBoolean("warn_notify", false);
    }

    static void setColors(Context c, int id, int sub, int wx, boolean compact, boolean notify) {
        sp(c).edit().putInt("tcs_" + id, sub).putInt("tcw_" + id, wx).putBoolean("cmp_" + id, compact)
                .putBoolean("warn_notify", notify).apply();
    }

    static String lastNotified(Context c) {
        return sp(c).getString("warn_notified", "");
    }

    static void setLastNotified(Context c, String k) {
        sp(c).edit().putString("warn_notified", k).apply();
    }

    // ---- Lawinenlagebericht ----
    static void setAval(Context c, String text) {
        sp(c).edit().putString("aval_text", text == null ? "" : text).putLong("aval_t", System.currentTimeMillis()).apply();
    }

    static String avalText(Context c) {
        return sp(c).getString("aval_text", "");
    }

    static long avalTime(Context c) {
        return sp(c).getLong("aval_t", 0);
    }

    // ---- Quellen ohne Neues ----
    static void setSrcWarn(Context c, String t) {
        sp(c).edit().putString("src_warn", t == null ? "" : t).apply();
    }

    static String srcWarn(Context c) {
        return sp(c).getString("src_warn", "");
    }

    /** Auto-Design: hell am Tag laut Systemeinstellung (Dunkelmodus aus). */
    static boolean isLight(Context c, int id) {
        return theme(c, id) == 1;
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
    /**
     * Gewaehlte Themen. Gespeichert als "Thema|Medium;Thema|Medium" (Medium leer = alle Medien).
     * Ein einzelner Eintrag entspricht dem frueheren Format.
     */
    static List<String[]> topicSel(Context c, int id) {
        List<String[]> out = new ArrayList<>();
        for (String e : topicRaw(c, id).split(";")) {
            if (e.trim().isEmpty()) continue;
            int i = e.indexOf('|');
            out.add(i < 0 ? new String[]{e, ""} : new String[]{e.substring(0, i), e.substring(i + 1)});
        }
        return out;
    }

    /** Haupt-Thema fuer Reiter und Leer-Hinweise: bei mehreren Eintraegen das gemeinsame Oberthema. */
    static String topic(Context c, int id) {
        List<String[]> sel = topicSel(c, id);
        if (sel.isEmpty()) return "";
        if (sel.size() == 1) return sel.get(0)[0];
        return sel.get(0)[0].split("/")[0];
    }

    /** Gewaehltes Thema inkl. Medium ("Region/Berchtesgaden|BGLand24"); ohne Medium nur das Thema. */
    static String topicRaw(Context c, int id) {
        return sp(c).getString("tp_" + id, "");
    }

    /** Medium, auf das das Thema beschraenkt ist; leer = alle Medien (bei Mehrfachauswahl immer leer). */
    static String topicFeed(Context c, int id) {
        List<String[]> sel = topicSel(c, id);
        return sel.size() == 1 ? sel.get(0)[1] : "";
    }

    /** Beschriftung des aktiven Reiters, z. B. "Berchtesgaden" oder "Berchtesgaden +2". */
    static String topicLabel(Context c, int id) {
        List<String[]> sel = topicSel(c, id);
        if (sel.isEmpty()) return "";
        String t = sel.get(0)[0];
        String first = t.indexOf('/') >= 0 ? t.substring(t.indexOf('/') + 1) : t;
        return sel.size() == 1 ? first : first + " +" + (sel.size() - 1);
    }

    /** Passt ein Artikel (aus Medium feedName) zur Themenauswahl? Leere Auswahl = alles. */
    static boolean matchTopic(FeedItem it, String feedName, List<String[]> sel) {
        if (sel.isEmpty()) return true;
        for (String[] e : sel) {
            if (it.inTopic(e[0]) && (e[1].isEmpty() || e[1].equals(feedName))) return true;
        }
        return false;
    }

    static void setTopic(Context c, int id, String topic) {
        sp(c).edit().putString("tp_" + id, topic).apply();
    }

    static void remove(Context c, int id) {
        sp(c).edit().remove("bg_" + id).remove("tx_" + id).remove("tc_" + id)
                .remove("fd_" + id).remove("tp_" + id).remove("pl_" + id)
                .remove("plus_" + id).remove("mute_" + id).remove("dens_" + id)
                .remove("tcs2_" + id).remove("tcw2_" + id).remove("font_" + id).remove("size_" + id).remove("theme_" + id)
                .remove("wx_" + id).remove("warn_" + id).remove("tcs_" + id).remove("tcw_" + id).remove("cmp_" + id).remove("acc_" + id).apply();
        BgStyle.remove(c, id);
    }

    /** Bezahlartikel einer Quelle anzeigen; ohne eigene Wahl gilt der fruehere gemeinsame Schalter. */
    static boolean showPlusFeed(Context c, int id, String feedId) {
        String k = "plusf_" + id + "_" + feedId;
        return sp(c).contains(k) ? sp(c).getBoolean(k, true) : showPlus(c, id);
    }

    static void setPlusFeed(Context c, int id, String feedId, boolean on) {
        sp(c).edit().putBoolean("plusf_" + id + "_" + feedId, on).apply();
    }

    // ---- Stichwoerter ----
    static String keywords(Context c) {
        return sp(c).getString("kw", "");
    }

    static boolean kwNotify(Context c) {
        return sp(c).getBoolean("kw_notify", false);
    }

    static void setKeywords(Context c, String kw, boolean notify) {
        sp(c).edit().putString("kw", kw == null ? "" : kw.trim()).putBoolean("kw_notify", notify).apply();
    }

    static boolean kwWasNotified(Context c, String link) {
        return ("\n" + sp(c).getString("kwn", "") + "\n").contains("\n" + link + "\n");
    }

    static synchronized void kwMarkNotified(Context c, String link) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String l : sp(c).getString("kwn", "").split("\n")) if (!l.isEmpty() && !l.equals(link)) list.add(l);
        list.add(link);
        while (list.size() > 200) list.remove(0);
        sp(c).edit().putString("kwn", android.text.TextUtils.join("\n", list)).apply();
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

    // ---- Aussehen ----
    /** 0 Standard, 1 Serif, 2 Schmal, 3 Monospace, 4 Handschrift. */
    static int font(Context c, int id) {
        int f = sp(c).getInt("font_" + id, 0);
        return f >= 0 && f <= 4 ? f : 0;
    }

    /** 0 klein, 1 normal, 2 gross. */
    static int size(Context c, int id) {
        int f = sp(c).getInt("size_" + id, 1);
        return f >= 0 && f <= 2 ? f : 1;
    }

    /** 0 dunkel, 1 hell, 2 automatisch. */
    /** 0 = dunkel, 1 = hell. Der frueher gespeicherte Wert "Auto" (2) wird einmalig nach dem Systemdesign aufgeloest. */
    static int theme(Context c, int id) {
        int t = sp(c).getInt("theme_" + id, 0);
        if (t == 2) {
            int mode = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES ? 0 : 1;
        }
        return t == 1 ? 1 : 0;
    }

    /** Eigene Akzentfarbe (aktiver Chip); -1 = Standard. */
    static int accent(Context c, int id) {
        return sp(c).getInt("acc_" + id, -1);
    }

    static void setAccent(Context c, int id, int rgb) {
        sp(c).edit().putInt("acc_" + id, rgb < 0 ? -1 : rgb & 0xFFFFFF).apply();
    }

    static boolean addCardHidden(Context c) {
        return sp(c).getBoolean("addcard_hidden", false);
    }

    static void setAddCardHidden(Context c, boolean hidden) {
        sp(c).edit().putBoolean("addcard_hidden", hidden).apply();
    }

    static boolean showWeather(Context c, int id) {
        return sp(c).getBoolean("wx_" + id, true);
    }

    static boolean showWarn(Context c, int id) {
        return sp(c).getBoolean("warn_" + id, true);
    }

    static void setLook(Context c, int id, int font, int size, int theme, boolean weather, boolean warn) {
        sp(c).edit().putInt("font_" + id, font).putInt("size_" + id, size).putInt("theme_" + id, theme)
                .putBoolean("wx_" + id, weather).putBoolean("warn_" + id, warn).apply();
    }

    // ---- DWD-Warnung (zuletzt berechnet) ----
    static void setWarn(Context c, int level, String text) {
        sp(c).edit().putInt("warn_level", level).putString("warn_text", text == null ? "" : text)
                .putLong("warn_t", System.currentTimeMillis()).apply();
    }

    static int warnLevel(Context c) {
        return sp(c).getInt("warn_level", 0);
    }

    static String warnText(Context c) {
        return sp(c).getString("warn_text", "");
    }

    static void invalidateWarn(Context c) {
        sp(c).edit().putLong("warn_t", 0).apply();
    }

    static long warnTime(Context c) {
        return sp(c).getLong("warn_t", 0);
    }

    // ---- gemerkte Artikel (JSON-Liste, neueste zuerst, max. 100) ----
    static org.json.JSONArray saved(Context c) {
        try {
            return new org.json.JSONArray(sp(c).getString("saved", "[]"));
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    static boolean isSaved(Context c, String link) {
        org.json.JSONArray a = saved(c);
        for (int i = 0; i < a.length(); i++) {
            if (link.equals(a.optJSONObject(i) == null ? "" : a.optJSONObject(i).optString("l"))) return true;
        }
        return false;
    }

    /** Merken oder wieder entfernen; liefert den neuen Zustand. */
    static synchronized boolean toggleSaved(Context c, String title, String meta, String text, String link) {
        org.json.JSONArray a = saved(c);
        org.json.JSONArray out = new org.json.JSONArray();
        boolean had = false;
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (link.equals(o.optString("l"))) had = true;
            else out.put(o);
        }
        if (!had) {
            try {
                org.json.JSONObject n = new org.json.JSONObject();
                n.put("t", title).put("m", meta).put("x", text).put("l", link);
                org.json.JSONArray res = new org.json.JSONArray();
                res.put(n);
                for (int i = 0; i < out.length() && res.length() < 100; i++) res.put(out.get(i));
                out = res;
            } catch (Exception ignored) {
            }
        }
        sp(c).edit().putString("saved", out.toString()).apply();
        return !had;
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

    // ---- Aktualisierungs-Intervalle (Minuten) ----
    static final int[] INTERVALS = {15, 30, 60, 180, 360, 720};
    static final String[] INTERVAL_NAMES = {"15 Min", "30 Min", "1 Std", "3 Std", "6 Std", "12 Std"};

    static int wxIdx(Context c) {
        return Math.max(0, Math.min(INTERVALS.length - 1, sp(c).getInt("iv_wx", 1)));
    }

    static int newsIdx(Context c) {
        return Math.max(0, Math.min(INTERVALS.length - 1, sp(c).getInt("iv_news", 1)));
    }

    static void setIntervals(Context c, int wx, int news) {
        sp(c).edit().putInt("iv_wx", wx).putInt("iv_news", news).apply();
    }

    /** Ab diesem Alter gilt ein Stand als faellig (85 % des Intervalls, damit ein etwas frueher Wecker nicht ausfaellt). */
    static long wxMs(Context c) {
        return INTERVALS[wxIdx(c)] * 60000L * 85 / 100;
    }

    static long newsMs(Context c) {
        return INTERVALS[newsIdx(c)] * 60000L * 85 / 100;
    }

    static long newsTry(Context c) {
        return sp(c).getLong("news_try", 0);
    }

    static void setNewsTry(Context c, long t) {
        sp(c).edit().putLong("news_try", t).apply();
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

    static String status(Context c, String feedId) {
        return sp(c).getString("status_" + feedId, "");
    }

    static void setStatus(Context c, String feedId, String s) {
        if (s == null) s = "";
        if (s.equals(status(c, feedId))) return;
        sp(c).edit().putString("status_" + feedId, s).apply();
    }

    static String catalogCache(Context c) {
        return sp(c).getString("catalog", null);
    }

    static void setCatalogCache(Context c, String json) {
        sp(c).edit().putString("catalog", json).putLong("cat_t", System.currentTimeMillis()).apply();
    }

    static long catalogTime(Context c) {
        return sp(c).getLong("cat_t", 0);
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
