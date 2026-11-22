/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;

/** Dezente Tiefe: erhabene Flaechen (helle Oberkante, dunklere Unterkante) und eingesenkte Felder. */
final class Depth {
    private Depth() {}

    /** Legt eine (halb)transparente Farbe auf einen Untergrund und liefert eine deckende Farbe. */
    static int blend(int top, int base) {
        int a = top >>> 24;
        if (a == 255) return top;
        int r = ((top >> 16 & 255) * a + (base >> 16 & 255) * (255 - a)) / 255;
        int g = ((top >> 8 & 255) * a + (base >> 8 & 255) * (255 - a)) / 255;
        int b = ((top & 255) * a + (base & 255) * (255 - a)) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int shade(int c, float f) {
        int r = Math.min(255, Math.max(0, Math.round((c >> 16 & 255) * f)));
        int g = Math.min(255, Math.max(0, Math.round((c >> 8 & 255) * f)));
        int b = Math.min(255, Math.max(0, Math.round((c & 255) * f)));
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static int lift(int c, float amount) {
        int r = Math.round((c >> 16 & 255) + (255 - (c >> 16 & 255)) * amount);
        int g = Math.round((c >> 8 & 255) + (255 - (c >> 8 & 255)) * amount);
        int b = Math.round((c & 255) + (255 - (c & 255)) * amount);
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static GradientDrawable shape(int top, int bottom, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(r);
        return g;
    }

    private static LayerDrawable layers(int edge, int topC, int botC, float r, int insetTop, int insetBottom) {
        LayerDrawable l = new LayerDrawable(new Drawable[]{shape(edge, edge, r), shape(topC, botC, r)});
        l.setLayerInset(1, 0, insetTop, 0, insetBottom);
        return l;
    }

    /** Erhaben: helle Oberkante, dunklere Unterkante; gedrueckt sinkt die Flaeche ein. */
    static Drawable raised(int body, float rPx, float dPx, boolean light) {
        int edge = shade(body, light ? 0.72f : 0.50f);
        int hi = lift(body, light ? 0.55f : 0.10f);
        int e = Math.max(1, Math.round(2.5f * dPx));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                layers(edge, shade(body, 0.94f), shade(body, 0.97f), rPx, Math.max(1, Math.round(dPx * 2)), Math.max(1, Math.round(dPx))));
        s.addState(new int[0], layers(edge, hi, body, rPx, 0, e));
        return s;
    }

    /** Eingesenkt: dunklere Flaeche mit Schatten an der Oberkante (Eingabefelder, Schalter-Rinnen). */
    static Drawable sunken(int body, float rPx, float dPx, boolean light) {
        int deep = shade(body, light ? 0.90f : 0.62f);
        int low = shade(body, light ? 0.96f : 0.80f);
        GradientDrawable g = shape(deep, low, rPx);
        return g;
    }
}
