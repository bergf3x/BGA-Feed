/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Schalter im iPhone-Stil: Knopf gleitet weich mit leichtem Nachfedern, Farbe blendet fliessend ueber.
 * Wird von der ganzen Zeile ausgeloest (setOn(..., true)).
 */
final class IosSwitch extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int onColor, offColor, thumbOn, thumbOff;
    private float pos; // 0 = aus, 1 = an
    private boolean on;
    private ValueAnimator anim;

    IosSwitch(Context c, boolean on, int onColor, int offColor, int thumbOn, int thumbOff) {
        super(c);
        this.on = on;
        this.pos = on ? 1f : 0f;
        this.onColor = onColor;
        this.offColor = offColor;
        this.thumbOn = thumbOn;
        this.thumbOff = thumbOff;
        shadow.setColor(0x33000000);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    boolean isOn() {
        return on;
    }

    void setOn(boolean value, boolean animate) {
        if (value == on && (anim == null || !anim.isRunning())) return;
        on = value;
        if (anim != null) anim.cancel();
        if (!animate) {
            pos = on ? 1f : 0f;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, on ? 1f : 0f);
        anim.setDuration(260);
        // leichtes Ueberschwingen am Ende wie beim iPhone
        anim.setInterpolator(new PathInterpolator(0.3f, 1.35f, 0.5f, 1f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    @Override
    protected void onMeasure(int w, int h) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension(Math.round(51 * d), Math.round(31 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
        float clamp = Math.max(0f, Math.min(1f, pos));
        track.setColor(blend(offColor, onColor, clamp));
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2, h / 2, track);
        float pad = 2 * d, rad = h / 2 - pad;
        float x0 = pad + rad, x1 = w - pad - rad;
        float cx = x0 + (x1 - x0) * pos; // pos darf kurz >1 / <0 sein (Nachfedern)
        cx = Math.max(x0 - 2 * d, Math.min(x1 + 2 * d, cx));
        c.drawCircle(cx, h / 2 + d * 0.8f, rad, shadow);
        thumb.setColor(blend(thumbOff, thumbOn, clamp));
        c.drawCircle(cx, h / 2, rad, thumb);
    }
}
