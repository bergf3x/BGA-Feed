/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
    private static final long RELOAD_MS = 60000L;
    private static final int MAX_W = 2048;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ZoomImageView img;
    private View bar;
    private TextView title, sub, count;
    private ProgressBar spinner;
    private int index;
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
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (index < 0 || index >= Webcams.CAMS.length) index = 0;

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
        top.addView(button("✕", "Schließen", this::finish));
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
        top.addView(button("↻", "Neu laden", () -> load(true)));
        top.addView(button("↗", "Webseite öffnen", this::openPage));
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

    private TextView button(String label, String desc, Runnable click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setContentDescription(desc);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(px(46));
        t.setMinHeight(px(46));
        t.setOnClickListener(v -> click.run());
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, RELOAD_MS);
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
        int n = Webcams.CAMS.length;
        index = ((i % n) + n) % n;
        Webcams.Cam cam = Webcams.CAMS[index];
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
        final Webcams.Cam cam = Webcams.CAMS[at];
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
                ui.postDelayed(tick, RELOAD_MS);
            });
        }).start();
    }

    private void openPage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Webcams.CAMS[index].page)));
        } catch (Exception ignored) {
        }
    }
}
