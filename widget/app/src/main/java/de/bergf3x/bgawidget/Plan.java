/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

/**
 * Hoehenbudget: entscheidet je nach Widget-Hoehe, was sichtbar ist, damit unten nichts abgeschnitten wird.
 * Reihenfolge der Wichtigkeit: Wetterkopf, Warnung, Werkzeugzeile, 2 Infozeilen, Themen-Chips,
 * Nachrichtenliste, danach Zusaetze (Vorschau 3 Tage, weitere Infozeilen).
 * Beim Verkleinern verschwinden die Teile in umgekehrter Reihenfolge; der Wetterkopf bleibt immer.
 */
final class Plan {
    boolean warn, tools, fc, chips, list;
    int infoLines;      // 0 = Infotext ausblenden
    /** Nachrichten-Widget: 0 = klein (Wetter eine Zeile), 1 = mittel (+3 Tage), 2 = gross (alles). */
    int level = 2;
    /** Wetter von Hand aufgeklappt (Tipp auf den Pfeil); nur bei klein/mittel. */
    boolean open;
    /** Warnung als Symbol in der Wetterzeile statt als eigene Zeile. */
    boolean warnIcon;

    private static final float HEADER = 48, WARN = 22, TOOLS = 36, CHIPS = 31, LIST = 80, FC = 58, LINE = 16, PAD = 34;

    /** Platz fuer etwa vier Meldungen: der bleibt den Nachrichten immer vorbehalten. */
    private static final float NEWS = 256, ROW = 52, HEAD = 44, PAD_N = 36;

    /** Tatsaechliche Hoehe: hochkant gilt MAX_HEIGHT, quer MIN_HEIGHT. */
    static int heightDp(Context ctx, int id) {
        android.os.Bundle o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id);
        boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int h = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (h <= 0) h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300);
        return h;
    }

    static Plan of(Context ctx, int id, boolean weatherWidget) {
        if (!weatherWidget) return news(ctx, id);
        Plan p = new Plan();
        int minH = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300);
        float fs = Math.max(1f, ctx.getResources().getConfiguration().fontScale);
        float left = minH - PAD - HEADER;

        Weather w = Weather.load(ctx, Prefs.place(ctx, id));
        boolean on = weatherWidget || Prefs.showWeather(ctx, id);
        if (!on) {
            // Wetter aus: nur Chips und Liste
            left = minH - PAD;
            p.chips = left >= CHIPS + LIST;
            p.list = p.chips;
            if (!p.chips) p.list = left >= LIST;
            return p;
        }
        int wl = Prefs.warnLevel(ctx);
        boolean hasWarn = Prefs.showWarn(ctx, id) && wl >= 2 && !Prefs.warnText(ctx).isEmpty()
                && System.currentTimeMillis() - Prefs.warnTime(ctx) < 6L * 3600000;
        String info = w == null ? "" : w.info();
        int total = info.isEmpty() ? 0 : info.split("\n").length;
        boolean compact = Prefs.compact(ctx, id);
        if (compact) total = 0;

        if (hasWarn && left >= WARN * fs) { p.warn = true; left -= WARN * fs; }
        if (left >= TOOLS) { p.tools = true; left -= TOOLS; }
        int base = Math.min(2, total);
        if (base > 0 && left >= (base * LINE + 4) * fs) { p.infoLines = base; left -= (base * LINE + 4) * fs; }
        if (!weatherWidget) {
            if (left >= CHIPS + LIST) {
                p.chips = true;
                p.list = true;
                left -= CHIPS + LIST;
            }
        }
        if (!compact && w != null && left >= FC) { p.fc = true; left -= FC; }
        if (p.infoLines > 0 && total > p.infoLines) {
            int more = (int) Math.min(total - p.infoLines, Math.floor(left / (LINE * fs)));
            if (more > 0) p.infoLines += more;
        }
        return p;
    }

    /**
     * Nachrichten-Widget: die Liste hat Vorrang. Das Wetter bekommt nur den Platz, der nach
     * etwa vier Meldungen uebrig bleibt: klein = eine Zeile, mittel = plus drei Tage, gross = alles.
     */
    private static Plan news(Context ctx, int id) {
        Plan p = new Plan();
        p.chips = true;
        p.list = true;
        float fs = Math.max(1f, ctx.getResources().getConfiguration().fontScale);
        int h = heightDp(ctx, id);
        if (!Prefs.showWeather(ctx, id)) {
            p.level = 0;
            return p;
        }
        Weather w = Weather.load(ctx, Prefs.place(ctx, id));
        int wl = Prefs.warnLevel(ctx);
        boolean hasWarn = Prefs.showWarn(ctx, id) && wl >= 2 && !Prefs.warnText(ctx).isEmpty()
                && System.currentTimeMillis() - Prefs.warnTime(ctx) < 6L * 3600000;
        String info = w == null ? "" : w.info();
        int total = info.isEmpty() || Prefs.compact(ctx, id) ? 0 : info.split("\n").length;
        float extra = h - PAD_N - HEAD - ROW - CHIPS - NEWS * fs;
        float full = FC + TOOLS + (hasWarn ? WARN * fs : 0) + (total > 0 ? (Math.min(2, total) * LINE + 4) * fs : 0);
        if (w != null && extra >= full) p.level = 2;
        else if (w != null && extra >= FC) p.level = 1;
        else p.level = 0;
        p.open = p.level < 2 && Prefs.flag(ctx, "wxopen_" + id);
        boolean details = p.level == 2 || p.open;
        p.fc = w != null && (p.level >= 1 || p.open);
        p.tools = p.level == 2;
        p.warn = hasWarn && details;
        p.warnIcon = hasWarn && !details;
        if (details && total > 0) {
            p.infoLines = Math.min(2, total);
            if (p.level == 2) {
                float rest = extra - full;
                int more = (int) Math.min(total - p.infoLines, Math.floor(rest / (LINE * fs)));
                if (more > 0) p.infoLines += more;
            } else {
                p.infoLines = total;
            }
        }
        return p;
    }
}
