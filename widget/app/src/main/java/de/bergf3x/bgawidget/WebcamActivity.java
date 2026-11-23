/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Webcams: Liste, Vorschau mit dem letzten Bild in der Blase (Tipp = Vollbild) und Links zu weiteren Seiten. */
public class WebcamActivity extends Activity {
    /** Seiten ohne festes Standbild (oder ohne erlaubten Abruf): nur Link. */
    private static final String[][] LINKS = {
            {"Obersalzberg – Watzmann im Blick", "https://www.blickfix.com/webcams/webcam-obersalzberg-watzmann-im-blick?type=17023"},
            {"Hochlenzer – Schönau und Watzmann", "https://www.bergfex.com/berchtesgaden-gutshof-obersalzberg/webcams/c2341/"},
            {"Jenner Bergstation", "https://www.bergfex.com/jenner-schoenau-am-koenigssee/webcams/c23475/"},
            {"Kehlstein", "https://www.bergfex.com/jenner-schoenau-am-koenigssee/webcams/c18989/"},
            {"Alle Webcams Schönau (bergfex)", "https://www.bergfex.com/bayern/langlaufen/schoenau-am-koenigssee/webcams/"},
            {"Alle Webcams Berchtesgaden", "https://www.berchtesgaden.de/webcams"},
            {"Nationalpark: Jenner, Funtensee, Ramsau", "https://nationalpark-berchtesgaden.bayern.de/english/service/media/webcam/index.htm"},
    };
    private static final long RELOAD_MS = 60000L;
    private static final int PREVIEW_W = 1000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Bubble b;
    private int cur = -1;
    private int listY = 0;
    private android.widget.ScrollView listScroll;
    private int gen;
    private ImageView prevImg;
    private TextView prevStatus;
    private ProgressBar spin;
    private boolean hasImage;

    private final Runnable tick = () -> {
        if (cur >= 0) load();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = new Bubble(this, 330, 8);
        showList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cur >= 0 && prevImg != null) load();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (cur >= 0) showList();
        else b.close();
    }

    // ---------------------------------------------------------------- Liste

    private void showList() {
        cur = -1;
        gen++;
        ui.removeCallbacks(tick);
        Bubble.soft(b.box);
        b.box.removeAllViews();
        b.text("Webcams", 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(6));
        LinearLayout body = b.scrollBox(0.62f);
        listScroll = (android.widget.ScrollView) body.getParent();
        final int restoreY = listY;
        listScroll.post(() -> listScroll.scrollTo(0, restoreY));
        section(body, "Live-Bild", "Bild direkt hier in der Blase · " + Webcams.CAMS.length + " Kameras", 0xFF5DCAA5, 0);
        for (int i = 0; i < Webcams.CAMS.length; i++) {
            final int idx = i;
            b.row(body, Webcams.CAMS[i].name, false, () -> showCam(idx), false);
            decorate(body, R.drawable.ic_cam, "›");
        }
        section(body, "Nur Webseite", "öffnet den Browser, kein Bild hier", 0xFFF0997B, 14);
        for (final String[] c : LINKS) {
            b.row(body, c[0], false, () -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(c[1])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            }, false);
            decorate(body, 0, "↗");
        }
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
    }

    /** Abschnittskopf: farbiger Balken, fette Ueberschrift und eine Zeile Erklaerung auf eigener Flaeche. */
    private void section(LinearLayout body, String title, String sub, int accent, int topMarginDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(b.px(10), b.px(9), b.px(12), b.px(9));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(b.light ? 0x1A000000 : 0x2EFFFFFF);
        bg.setCornerRadius(b.px(12));
        row.setBackground(bg);
        View bar = new View(this);
        android.graphics.drawable.GradientDrawable barBg = new android.graphics.drawable.GradientDrawable();
        barBg.setColor(accent);
        barBg.setCornerRadius(b.px(2));
        bar.setBackground(barBg);
        row.addView(bar, new LinearLayout.LayoutParams(b.px(4), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(b.px(10), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(b.title);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView st = new TextView(this);
        st.setText(sub);
        st.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        st.setTextColor(b.soft);
        col.addView(t);
        col.addView(st);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = b.px(topMarginDp);
        lp.bottomMargin = b.px(4);
        body.addView(row, lp);
    }

    /** Zeile der Liste um ein Symbol vorne und eine Marke hinten ergaenzen (Kamera + Pfeil bzw. Aussprung-Pfeil). */
    private void decorate(LinearLayout body, int iconRes, String trailing) {
        LinearLayout r = (LinearLayout) body.getChildAt(body.getChildCount() - 1);
        if (iconRes != 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(iconRes);
            iv.setColorFilter(b.soft);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(b.px(18), b.px(18));
            ip.rightMargin = b.px(12);
            r.addView(iv, 0, ip);
        }
        TextView tr = new TextView(this);
        tr.setText(trailing);
        tr.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
        tr.setTextColor(b.soft);
        r.addView(tr);
    }

    // ---------------------------------------------------------------- Vorschau

    private void showCam(int i) {
        if (listScroll != null && cur < 0) listY = listScroll.getScrollY();
        cur = i;
        final Webcams.Cam cam = Webcams.CAMS[i];
        gen++;
        Bubble.soft(b.box);
        b.box.removeAllViews();
        b.header(cam.name, this::showList);

        FrameLayout holder = new FrameLayout(this);
        holder.setBackgroundColor(0x33000000);
        holder.setMinimumHeight(b.px(170));
        holder.setClipToOutline(true);
        holder.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), b.px(14));
            }
        });
        prevImg = new ImageView(this);
        prevImg.setAdjustViewBounds(true);
        prevImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        prevImg.setMaxHeight(b.px(300));
        holder.addView(prevImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        spin = new ProgressBar(this);
        holder.addView(spin, new FrameLayout.LayoutParams(b.px(32), b.px(32), Gravity.CENTER));
        // Tipp aufs Bild: Original-Seite der Webcam (Vollbild hat einen eigenen Knopf)
        holder.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(cam.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        b.flex(holder, 90);
        b.box.addView(holder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        prevStatus = b.text("Lade …", 12.5f, b.tx, false);
        prevStatus.setPadding(b.px(4), b.px(8), b.px(4), 0);
        b.text("Tippe aufs Bild für die Original-Seite  ·  Quelle: " + cam.source, 11, b.soft, false).setPadding(b.px(4), b.px(2), b.px(4), b.px(6));
        if (cam.small) {
            b.text("Hinweis: von dieser Quelle gibt es nur ein kleines Vorschaubild.", 11, b.soft, false).setPadding(b.px(4), 0, b.px(4), b.px(4));
        }

        Webcams.Shot c = Webcams.cached(this, cam, PREVIEW_W);
        hasImage = c.bmp != null;
        if (c.bmp != null) {
            prevImg.setImageBitmap(c.bmp);
            spin.setVisibility(View.GONE);
            prevStatus.setText(Webcams.status(c));
        }
        b.pillKeep("Vollbild", true, this::openFull);
        b.pillRow("Neu laden", () -> {
            if (!hasImage) spin.setVisibility(View.VISIBLE);
            prevStatus.setText("Aktualisiere …");
            load();
        }, "Webseite öffnen", () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(cam.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        // "Schliessen": schliesst nur die Ansicht, man ist wieder in der Auswahl; dort schliesst "Schliessen" das Fenster
        b.pillKeep("Schließen", false, this::showList);
        load();
    }

    private void openFull() {
        if (cur < 0) return;
        startActivity(new Intent(this, CamFullActivity.class).putExtra(CamFullActivity.EXTRA_INDEX, cur));
    }

    /** Neues Bild im Hintergrund holen; das gespeicherte bleibt sichtbar, bis es da ist. */
    private void load() {
        ui.removeCallbacks(tick);
        final int g = ++gen;
        final int at = cur;
        if (at < 0) return;
        final Webcams.Cam cam = Webcams.CAMS[at];
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            final Webcams.Shot s = Webcams.refresh(app, cam, PREVIEW_W);
            ui.post(() -> {
                if (isFinishing() || isDestroyed() || g != gen || at != cur) return;
                spin.setVisibility(View.GONE);
                if (s.bmp != null) {
                    prevImg.setImageBitmap(s.bmp);
                    hasImage = true;
                }
                prevStatus.setText(Webcams.status(s));
                ui.postDelayed(tick, RELOAD_MS);
            });
        }).start();
    }
}
