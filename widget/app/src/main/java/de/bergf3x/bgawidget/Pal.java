/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.RemoteViews;

/** Farben fuer Hell/Dunkel: Schrift, Symbole und Hintergruende passend zum gewaehlten Design. */
final class Pal {
    static final int INK = 0x1B1F22;
    static final int WHITE = 0xFFFFFF;

    private Pal() {}

    /** Textfarbe; override = eigene Farbe (-1 = Standardfarbe des Designs). f = Deckkraft-Faktor. */
    static void text(Context c, int id, RemoteViews rv, int viewId, int override, float f) {
        rv.setTextColor(viewId, Prefs.argb(override >= 0 ? override : Prefs.color(c, id), Prefs.text(c, id), f));
    }

    /** Farbfilter fuer Symbole (Aktualisieren, Einstellungen, Werkzeuge). */
    static void filter(Context c, int id, RemoteViews rv, int viewId, int override) {
        rv.setInt(viewId, "setColorFilter", 0xFF000000 | (override >= 0 ? override : Prefs.color(c, id)));
    }

    /** Beliebige Farbe mit zwei Werten (hell/dunkel) auf eine Methode setzen, z. B. setColorFilter. */
    static void pair(Context c, int id, RemoteViews rv, int viewId, String method, int light, int dark) {
        rv.setInt(viewId, method, Prefs.isLight(c, id) ? light : dark);
    }

    /** Text mit zwei festen Farben (hell/dunkel). */
    static void textPair(Context c, int id, RemoteViews rv, int viewId, int light, int dark) {
        pair(c, id, rv, viewId, "setTextColor", light, dark);
    }

    /** Hintergrund-Drawable der Chips. */
    static int cardBg(Context c, int id) {
        return Prefs.isLight(c, id) ? R.drawable.card_bg_light : R.drawable.card_bg;
    }

    /** Lesbare Schriftfarbe (dunkel oder hell) auf einem Untergrund. */
    static int onColor(int rgb) {
        double l = (0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255)) / 255.0;
        return 0xFF000000 | (l > 0.6 ? INK : WHITE);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Text mit Farbverlauf: jedes Zeichen in einem Zwischenton, Zeile fuer Zeile. */
    static CharSequence gradient(CharSequence t, int c1, int c2, int txPct, float f) {
        SpannableString s = new SpannableString(t);
        String str = t.toString();
        int start = 0;
        while (start <= str.length()) {
            int end = str.indexOf('\n', start);
            if (end < 0) end = str.length();
            int n = end - start;
            for (int i = 0; i < n; i++) {
                float r = n > 1 ? i / (float) (n - 1) : 0f;
                s.setSpan(new ForegroundColorSpan(Prefs.argb(mix(c1, c2, r), txPct, f)),
                        start + i, start + i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (end >= str.length()) break;
            start = end + 1;
        }
        return s;
    }

    /** Text setzen und einfaerben: einfarbig, Verlauf (c1 und c2 gesetzt) oder Standardfarbe (c1 = -1). */
    static void set(Context c, int id, RemoteViews rv, int viewId, CharSequence text, int c1, int c2, float f) {
        if (c1 >= 0 && c2 >= 0 && text != null && text.length() > 0) {
            rv.setTextViewText(viewId, gradient(text, c1, c2, Prefs.text(c, id), f));
            return;
        }
        rv.setTextViewText(viewId, text);
        text(c, id, rv, viewId, c1, f);
    }
}
