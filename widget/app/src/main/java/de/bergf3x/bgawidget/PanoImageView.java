/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * Zeigt aus einem sehr breiten Panorama einen normalen Ausschnitt (fuellt die Flaeche, Mitte bei pan * Bildbreite).
 * pan < 0: normales Bild (einpassen).
 */
final class PanoImageView extends ImageView {
    private float pan = -1f;

    PanoImageView(Context c) {
        super(c);
    }

    /** Mitte des Ausschnitts als Anteil der Bildbreite (0 bis 1); negativ = ganzes Bild einpassen. */
    void setPan(float p) {
        pan = p;
        setScaleType(p < 0 ? ScaleType.FIT_CENTER : ScaleType.MATRIX);
        fit();
    }

    @Override
    public void setImageDrawable(Drawable d) {
        super.setImageDrawable(d);
        fit();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    private void fit() {
        if (pan < 0) return;
        Drawable d = getDrawable();
        int w = getWidth(), h = getHeight();
        if (d == null || w == 0 || h == 0) return;
        int dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float s = Math.max((float) w / dw, (float) h / dh);
        float tx = w / 2f - pan * dw * s;
        tx = Math.min(0f, Math.max(w - dw * s, tx));
        float ty = (h - dh * s) / 2f;
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate(tx, ty);
        setImageMatrix(m);
    }
}
