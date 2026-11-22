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

    private static final float HEADER = 48, WARN = 22, TOOLS = 36, CHIPS = 31, LIST = 80, FC = 58, LINE = 16, PAD = 34;

    static Plan of(Context ctx, int id, boolean weatherWidget) {
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
}
