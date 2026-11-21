package de.bergf3x.bgawidget;

import android.content.Context;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.RemoteViews;

/**
 * Farben fuer Hell/Dunkel/Auto. Bei Auto bekommt das Widget beide Farbwerte mit (ab Android 12),
 * das System waehlt dann selbst und schaltet beim Wechsel des Systemdesigns sofort um.
 */
final class Pal {
    static final int INK = 0x1B1F22;
    static final int WHITE = 0xFFFFFF;

    private Pal() {}

    static boolean auto(Context c, int id) {
        return Prefs.theme(c, id) == 2;
    }

    private static boolean dual() {
        return Build.VERSION.SDK_INT >= 31;
    }

    /** Textfarbe; override = eigene Farbe (-1 = Standardfarbe des Designs). f = Deckkraft-Faktor. */
    static void text(Context c, int id, RemoteViews rv, int viewId, int override, float f) {
        int tx = Prefs.text(c, id);
        if (override >= 0 || !auto(c, id)) {
            rv.setTextColor(viewId, Prefs.argb(override >= 0 ? override : Prefs.color(c, id), tx, f));
            return;
        }
        int l = Prefs.argb(INK, tx, f);
        int d = Prefs.argb(WHITE, tx, f);
        if (dual()) rv.setColorInt(viewId, "setTextColor", l, d);
        else rv.setTextColor(viewId, Prefs.isLight(c, id) ? l : d);
    }

    /** Farbfilter fuer Symbole (Aktualisieren, Einstellungen, Werkzeuge). */
    static void filter(Context c, int id, RemoteViews rv, int viewId, int override) {
        if (override >= 0 || !auto(c, id)) {
            rv.setInt(viewId, "setColorFilter", 0xFF000000 | (override >= 0 ? override : Prefs.color(c, id)));
            return;
        }
        if (dual()) rv.setColorInt(viewId, "setColorFilter", 0xFF000000 | INK, 0xFF000000 | WHITE);
        else rv.setInt(viewId, "setColorFilter", 0xFF000000 | (Prefs.isLight(c, id) ? INK : WHITE));
    }

    /** Beliebige Farbe mit zwei Werten (hell/dunkel) auf eine Methode setzen, z. B. setColorFilter. */
    static void pair(Context c, int id, RemoteViews rv, int viewId, String method, int light, int dark) {
        if (!auto(c, id)) {
            rv.setInt(viewId, method, Prefs.isLight(c, id) ? light : dark);
            return;
        }
        if (dual()) rv.setColorInt(viewId, method, light, dark);
        else rv.setInt(viewId, method, Prefs.isLight(c, id) ? light : dark);
    }

    /** Text mit zwei festen Farben (hell/dunkel). */
    static void textPair(Context c, int id, RemoteViews rv, int viewId, int light, int dark) {
        pair(c, id, rv, viewId, "setTextColor", light, dark);
    }

    /** Hintergrund-Drawable: Auto nutzt Ressourcen mit Nacht-Variante. */
    static int chipBg(Context c, int id) {
        if (auto(c, id)) return R.drawable.chip_bg_auto;
        return Prefs.isLight(c, id) ? R.drawable.chip_bg_light : R.drawable.chip_bg;
    }

    static int cardBg(Context c, int id) {
        if (auto(c, id)) return R.drawable.card_bg_auto;
        return Prefs.isLight(c, id) ? R.drawable.card_bg_light : R.drawable.card_bg;
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
