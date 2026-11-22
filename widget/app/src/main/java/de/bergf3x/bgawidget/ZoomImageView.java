/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Bildflaeche mit Zoom (Spreizen, Doppeltipp), Verschieben, Wischen zum Wechseln und Tipp. */
final class ZoomImageView extends ImageView {
    interface Listener {
        /** dir = 1 naechstes, -1 vorheriges Bild. */
        void onSwipe(int dir);

        void onTap();
    }

    private final Matrix m = new Matrix();
    private final float[] v = new float[9];
    private float minScale = 1f;
    private final ScaleGestureDetector sg;
    private final GestureDetector gd;
    private Listener listener;

    ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float cur = scale();
                if (cur <= 0f) return true;
                float target = Math.max(minScale, Math.min(minScale * 8f, cur * d.getScaleFactor()));
                float f = target / cur;
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                apply();
                return true;
            }
        });
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (sg.isInProgress()) return false;
                m.postTranslate(-dx, -dy);
                apply();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (scale() <= minScale * 1.05f && Math.abs(vx) > 1200 && Math.abs(vx) > Math.abs(vy) * 1.5f && listener != null) {
                    listener.onSwipe(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (scale() > minScale * 1.1f) {
                    fit();
                } else {
                    float f = Math.min(minScale * 2.5f, minScale * 8f) / Math.max(scale(), 0.0001f);
                    m.postScale(f, f, e.getX(), e.getY());
                    apply();
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Neues Bild zeigen; reset = Ansicht auf "ganzes Bild" zuruecksetzen, sonst Zoom und Position behalten. */
    void showBitmap(Bitmap b, boolean reset) {
        super.setImageBitmap(b);
        if (reset) fit();
        else apply();
    }

    private float scale() {
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    private void fit() {
        Drawable d = getDrawable();
        int vw = getWidth(), vh = getHeight();
        if (d == null || vw == 0 || vh == 0 || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) return;
        float bw = d.getIntrinsicWidth(), bh = d.getIntrinsicHeight();
        float s = Math.min(vw / bw, vh / bh);
        minScale = s;
        m.reset();
        m.postScale(s, s);
        m.postTranslate((vw - bw * s) / 2f, (vh - bh * s) / 2f);
        setImageMatrix(m);
    }

    /** Bild nie aus dem Rahmen schieben: kleiner als der Rahmen = zentriert, groesser = Rand bleibt am Rahmen. */
    private void apply() {
        Drawable d = getDrawable();
        if (d != null && getWidth() > 0 && getHeight() > 0) {
            m.getValues(v);
            float s = v[Matrix.MSCALE_X];
            float iw = d.getIntrinsicWidth() * s, ih = d.getIntrinsicHeight() * s;
            float tx = v[Matrix.MTRANS_X], ty = v[Matrix.MTRANS_Y];
            float nx = iw <= getWidth() ? (getWidth() - iw) / 2f : Math.min(0f, Math.max(getWidth() - iw, tx));
            float ny = ih <= getHeight() ? (getHeight() - ih) / 2f : Math.min(0f, Math.max(getHeight() - ih, ty));
            m.postTranslate(nx - tx, ny - ty);
        }
        setImageMatrix(m);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        sg.onTouchEvent(e);
        if (!sg.isInProgress()) gd.onTouchEvent(e);
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
