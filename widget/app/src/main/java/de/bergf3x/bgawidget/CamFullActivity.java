/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Webcam im Vollbild: schwarzer Hintergrund, Zoom, Wischen zur naechsten Kamera, Bild erneuert sich jede Minute. */
public class CamFullActivity extends Activity {
    static final String EXTRA_INDEX = "idx";
    /** Kamera-Indizes (Webcams.CAMS) in der Reihenfolge, durch die gewischt wird; ohne Angabe alle. */
    static final String EXTRA_IDS = "ids";
    private static final int MAX_W = 2048;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ZoomImageView img;
    private View bar;
    private TextView title, sub, count;
    private ProgressBar spinner;
    private int index;
    private int[] list;
    private int gen;
    private boolean hasBmp;
    private boolean barVisible = true;
    private float d;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            load(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        list = getIntent().getIntArrayExtra(EXTRA_IDS);
        if (list == null || list.length == 0) {
            list = new int[Webcams.CAMS.length];
            for (int i = 0; i < list.length; i++) list[i] = i;
        }
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (index < 0 || index >= list.length) index = 0;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        img = new ZoomImageView(this);
        img.setListener(new ZoomImageView.Listener() {
            @Override
            public void onSwipe(int dir) {
                show(index + dir);
            }

            @Override
            public void onTap() {
                toggleBar();
            }
        });
        root.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER);
        root.addView(spinner, sp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(px(6), px(10), px(6), px(22));
        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000});
        top.setBackground(scrim);
        top.addView(iconButton(R.drawable.ic_back, "Zurück", true, this::finish));
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub = new TextView(this);
        sub.setTextColor(0xCCFFFFFF);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cols.addView(title);
        cols.addView(sub);
        top.addView(cols, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(iconButton(R.drawable.ic_refresh, "Neu laden", false, () -> load(true)));
        top.addView(iconButton(R.drawable.ic_share, "Teilen", false, () -> Webcams.share(this, Webcams.CAMS[list[index]])));
        bar = top;
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        count = new TextView(this);
        count.setTextColor(0xCCFFFFFF);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        count.setPadding(px(14), px(6), px(14), px(6));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x66000000);
        pill.setCornerRadius(px(14));
        count.setBackground(pill);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin = px(24);
        root.addView(count, cp);
        setContentView(root);
        show(index);
    }

    private int px(float dp) {
        return Math.round(dp * d);
    }

    /** Runder Symbolknopf im Linien-Stil; circle = halbtransparent hinterlegt (Zurueck). */
    private android.widget.ImageView iconButton(int res, String desc, boolean circle, Runnable click) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Color.WHITE);
        iv.setContentDescription(desc);
        iv.setPadding(px(11), px(11), px(11), px(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(circle ? 0x40000000 : 0);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        iv.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), bg, mask));
        iv.setOnClickListener(v -> click.run());
        iv.setLayoutParams(new LinearLayout.LayoutParams(px(46), px(46)));
        return iv;
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        ui.removeCallbacks(tick);
        scheduleTick();
    }

    private void scheduleTick() {
        long iv = Webcams.intervalMs(this);
        if (iv > 0) ui.postDelayed(tick, Math.max(iv, 30000L));
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void toggleBar() {
        barVisible = !barVisible;
        bar.animate().alpha(barVisible ? 1f : 0f).setDuration(160).start();
        count.animate().alpha(barVisible ? 1f : 0f).setDuration(160).start();
    }

    /** Zu einer Kamera wechseln (endlos in beide Richtungen): erst das gespeicherte Bild, dann das neue. */
    private void show(int i) {
        int n = list.length;
        index = ((i % n) + n) % n;
        Webcams.Cam cam = Webcams.CAMS[list[index]];
        Webcams.viewed(this, cam);
        title.setText(cam.name);
        count.setText((index + 1) + " / " + n + "  ·  Wischen = nächste Kamera");
        Webcams.Shot c = Webcams.cached(this, cam, MAX_W);
        hasBmp = c.bmp != null;
        img.showBitmap(c.bmp, true);
        sub.setText(c.bmp != null ? Webcams.status(c) : "Lade …");
        spinner.setVisibility(c.bmp != null ? View.GONE : View.VISIBLE);
        load(false);
    }

    private void load(boolean manual) {
        ui.removeCallbacks(tick);
        final int g = ++gen;
        final int at = index;
        final Webcams.Cam cam = Webcams.CAMS[list[at]];
        if (manual) {
            sub.setText("Aktualisiere …");
            if (!hasBmp) spinner.setVisibility(View.VISIBLE);
        }
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            final Webcams.Shot s = Webcams.refresh(app, cam, MAX_W);
            ui.post(() -> {
                if (isFinishing() || isDestroyed() || g != gen || at != index) return;
                spinner.setVisibility(View.GONE);
                if (s.bmp != null) {
                    img.showBitmap(s.bmp, !hasBmp);
                    hasBmp = true;
                }
                sub.setText(Webcams.status(s));
                scheduleTick();
            });
        }).start();
    }

}
