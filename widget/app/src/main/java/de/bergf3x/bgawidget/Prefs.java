/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
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
        return Math.max(0, Math.min(100, sp(c).getInt("bg_" + id, DEFAULT_BG))); // 0 % = ganz durchsichtig
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

    /** Was ein Widget zeigt: 0 Nachrichten, 1 Wetter, 2 Gemerkt. */
    static int widgetShows(Context c, int id) {
        return sp(c).getInt("shows_" + id, 0);
    }

    /** Setzt den Inhalt eines Widgets; Wetter nimmt Bergwetter oder Region als Thema, sonst wird das Wetter-Thema geloescht. */
    static void setWidgetShows(Context c, int id, int shows, boolean region) {
        android.content.SharedPreferences.Editor e = sp(c).edit().putInt("shows_" + id, shows);
        if (shows == 1) e.putString("tp_" + id, region ? WeatherCards.REGION : WeatherCards.BERG);
        else if (topic(c, id).startsWith(WeatherCards.TOP)) e.putString("tp_" + id, "");
        e.apply();
    }

    static java.util.Set<String> bergRegions(Context c) {
        return new java.util.LinkedHashSet<>(sp(c).getStringSet("wc_berg", WeatherCards.BERG_DEFAULT));
    }

    static boolean regionCards(Context c) {
        return sp(c).getBoolean("wc_region", true);
    }

    static java.util.Set<String> warnRegions(Context c) {
        return new java.util.LinkedHashSet<>(sp(c).getStringSet("wc_warn", new java.util.LinkedHashSet<>(java.util.Arrays.asList("09172"))));
    }

    static void setWeatherCards(Context c, java.util.Set<String> berg, boolean region, java.util.Set<String> warn) {
        sp(c).edit().putStringSet("wc_berg", new java.util.HashSet<>(berg)).putBoolean("wc_region", region)
                .putStringSet("wc_warn", new java.util.HashSet<>(warn)).apply();
    }

    static boolean warnNotify(Context c) {
        return sp(c).getBoolean("warn_notify", false);
    }

    static void setColors(Context c, int id, int sub, int wx, boolean compact, boolean notify) {
        sp(c).edit().putInt("tcs_" + id, sub).putInt("tcw_" + id, wx).putBoolean("cmp_" + id, compact)
                .putBoolean("warn_notify", notify).apply();
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
                .putInt("bg_" + id, Math.max(0, Math.min(100, bg)))
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

    /** Beschriftung des aktiven Reiters, z. B. "Berchtesgaden" oder "Berchtesgaden +2". */
    static String topicLabel(Context c, int id) {
        List<String[]> sel = topicSel(c, id);
        if (sel.isEmpty()) return "";
        String t = sel.get(0)[0];
        String first = t.indexOf('/') >= 0 ? Taxonomy.label(t) : t;
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
                .remove("fd_" + id).remove("tp_" + id).remove("pl_" + id).remove("pl2_" + id)
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

    /** 0 = bei keiner Quelle, 1 = bei allen, 2 = bei einigen Quellen sichtbar (nur Quellen mit Abo-Artikeln). */
    static int plusState(Context c, int id) {
        int on = 0, tot = 0;
        for (Catalog.Feed f : Catalog.load(c)) {
            if (!Catalog.hasPaywall(f.id)) continue;
            tot++;
            if (showPlusFeed(c, id, f.id)) on++;
        }
        return tot == 0 || on == tot ? 1 : on == 0 ? 0 : 2;
    }

    /** Hauptschalter: Abo-Artikel bei allen Quellen an oder aus (einzelne lassen sich danach wieder aendern). */
    static void setPlusAll(Context c, int id, boolean on) {
        android.content.SharedPreferences.Editor e = sp(c).edit().putBoolean("plus_" + id, on);
        for (Catalog.Feed f : Catalog.load(c)) if (Catalog.hasPaywall(f.id)) e.putBoolean("plusf_" + id + "_" + f.id, on);
        e.apply();
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

    /** Vorschaubilder: 0 aus, 1 nur WLAN (Standard), 2 immer. Gilt fuer App und Widgets. */
    static int imgMode(Context c) {
        return sp(c).getInt("img_mode", 1);
    }

    static void setImgMode(Context c, int m) {
        sp(c).edit().putInt("img_mode", m).apply();
    }

    static boolean flag(Context c, String key) {
        return sp(c).getBoolean(key, false);
    }

    static void setFlag(Context c, String key, boolean v) {
        sp(c).edit().putBoolean(key, v).apply();
    }

    static int intVal(Context c, String key, int def) {
        return sp(c).getInt(key, def);
    }

    static void setInt(Context c, String key, int v) {
        sp(c).edit().putInt(key, v).apply();
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

    static void setShowWeather(Context c, int id, boolean on) {
        sp(c).edit().putBoolean("wx_" + id, on).apply();
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
    static boolean toggleSaved(Context c, String title, String meta, String text, String link) {
        return toggleSaved(c, title, meta, text, link, "");
    }

    static synchronized boolean toggleSaved(Context c, String title, String meta, String text, String link, String image) {
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
                n.put("t", title).put("m", meta).put("x", text).put("l", link).put("i", image == null ? "" : image).put("d", System.currentTimeMillis());
                org.json.JSONArray res = new org.json.JSONArray();
                res.put(n);
                for (int i = 0; i < out.length() && res.length() < 100; i++) res.put(out.get(i));
                out = res;
            } catch (Exception ignored) {
            }
        }
        sp(c).edit().putString("saved", out.toString()).apply();
        savedChanged(c);
        return !had;
    }

    /** Gemerkt hat sich geaendert: App (falls offen) und alle Widgets (Leiste mit Zaehler, Liste) neu zeichnen. */
    static void savedChanged(Context c) {
        final Context app = c.getApplicationContext();
        try {
            app.sendBroadcast(new android.content.Intent(Direct.DATA_CHANGED).setPackage(app.getPackageName()));
        } catch (Exception ignored) {
        }
        try {
            android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(app);
            for (int wid : m.getAppWidgetIds(new android.content.ComponentName(app, WidgetProvider.class))) {
                WidgetProvider.refresh(app, wid);
            }
        } catch (Exception ignored) {
        }
    }

    /** Aktuelle DWD-Warnung je Region fuer die Wetter-Karten ("" = noch unbekannt, "-" = keine). */
    static String warnRegion(Context c, String ags) {
        return sp(c).getString("warnr_" + ags, "");
    }

    static void setWarnRegion(Context c, String ags, String text, int level) {
        sp(c).edit().putString("warnr_" + ags, text).putInt("warnl_" + ags, level).apply();
    }

    static int warnRegionLevel(Context c, String ags) {
        return sp(c).getInt("warnl_" + ags, 0);
    }

    /** Aufbewahrung gemerkter Artikel in Tagen; 0 = dauerhaft. */
    static int savedKeepDays(Context c) {
        return sp(c).getInt("saved_days", 0);
    }

    static void setSavedKeepDays(Context c, int days) {
        sp(c).edit().putInt("saved_days", days).apply();
    }

    /** Entfernt gemerkte Artikel, die laenger als die gewaehlte Frist gemerkt sind (Eintraege ohne Zeitstempel bekommen jetzt einen). */
    static synchronized void pruneSaved(Context c) {
        org.json.JSONArray a = saved(c);
        long now = System.currentTimeMillis();
        long keep = savedKeepDays(c) * 86400000L;
        org.json.JSONArray out = new org.json.JSONArray();
        boolean changed = false;
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            try {
                if (o.optLong("d") == 0) {
                    o.put("d", now);
                    changed = true;
                }
            } catch (Exception ignored) {
            }
            if (keep > 0 && now - o.optLong("d") > keep) {
                changed = true;
                continue;
            }
            out.put(o);
        }
        if (changed) {
            sp(c).edit().putString("saved", out.toString()).apply();
            savedChanged(c);
        }
    }

    /** Merkt sich je Artikel, ob die Quelle ihn nicht mehr liefert (gone) oder ob er erreichbar ist (checked); ungepruefte bleiben unveraendert. */
    static synchronized void markSavedGone(Context c, Set<String> gone, Set<String> checked, java.util.Map<String, String> images) {
        org.json.JSONArray a = saved(c);
        boolean changed = false;
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String l = o.optString("l");
            try {
                if (checked.contains(l) && o.optBoolean("g") != gone.contains(l)) {
                    o.put("g", gone.contains(l));
                    changed = true;
                }
                String img = images.get(l);
                if (img != null && !img.isEmpty() && o.optString("i").isEmpty()) {
                    o.put("i", img);
                    changed = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (changed) {
            sp(c).edit().putString("saved", a.toString()).apply();
            savedChanged(c);
        }
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
    /** Manuell: kein automatischer Abruf, nur auf Knopfdruck (je fuer Nachrichten und Wetter im Widget). */
    static boolean newsAuto(Context c) {
        return !sp(c).getBoolean("man_news", false);
    }

    static boolean wxAuto(Context c) {
        return !sp(c).getBoolean("man_wx", false);
    }

    static void setNewsMode(Context c, boolean manual, int idx) {
        SharedPreferences.Editor e = sp(c).edit().putBoolean("man_news", manual);
        if (idx >= 0) e.putInt("iv_news", idx);
        e.apply();
    }

    static void setWxMode(Context c, boolean manual, int idx) {
        SharedPreferences.Editor e = sp(c).edit().putBoolean("man_wx", manual);
        if (idx >= 0) e.putInt("iv_wx", idx);
        e.apply();
    }

    /** Ab diesem Alter gilt ein Stand als faellig (85 % des Intervalls, damit ein etwas frueher Wecker nicht ausfaellt). */
    static long wxMs(Context c) {
        return INTERVALS[wxIdx(c)] * 60000L * 85 / 100;
    }

    static long newsMs(Context c) {
        return INTERVALS[newsIdx(c)] * 60000L * 85 / 100;
    }

    /** Die Vollbild-App haelt ihr Wetter immer gleich frisch, unabhaengig von den Widget-Einstellungen. */
    static final long APP_WX_MS = 30L * 60000L * 85 / 100;

    /** Hoechstalter des Wetters fuer Widgets: im Automatik-Betrieb das Intervall, manuell nie (nur wenn noch nichts da ist). */
    static long wxMaxAge(Context c) {
        return wxAuto(c) ? wxMs(c) : Long.MAX_VALUE;
    }

    /** true, wenn t (Zeitstempel) existiert und juenger als maxAge ist. */
    static boolean fresh(long t, long maxAge) {
        return t != 0 && System.currentTimeMillis() - t < maxAge;
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

    /** Zweiter Wetter-Ort des Wetter-Widgets (Index in Weather.PLACES), -1 = keiner. */
    static int place2(Context c, int id) {
        int p = sp(c).getInt("pl2_" + id, -1);
        return p >= 0 && p < Weather.PLACES.length ? p : -1;
    }

    static void setPlace2(Context c, int id, int place) {
        sp(c).edit().putInt("pl2_" + id, place).apply();
    }

    static int clamp(int pct) {
        return Math.max(1, Math.min(100, pct));
    }

    /** Prozent (1-100) in Alpha-Wert (0-255). */
    static int alpha(int pct) {
        return Math.round(Math.max(0, Math.min(100, pct)) * 255f / 100f);
    }

    /** Farbe mit Deckkraft-Prozent und optionalem Faktor (z. B. 0.7 fuer Nebentext). */
    static int argb(int rgb, int pct, float factor) {
        return (Math.round(alpha(pct) * factor) << 24) | (rgb & 0xFFFFFF);
    }
}
