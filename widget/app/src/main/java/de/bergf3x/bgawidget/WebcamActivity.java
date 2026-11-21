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
        b.box.removeAllViews();
        b.text("Webcams", 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(6));
        LinearLayout body = b.scrollBox(0.62f);
        b.label(body, "Live-Bild in der Blase");
        for (int i = 0; i < Webcams.CAMS.length; i++) {
            final int idx = i;
            b.row(body, Webcams.CAMS[i].name, false, () -> showCam(idx), false);
        }
        b.label(body, "Nur Webseite (öffnet im Browser)");
        for (final String[] c : LINKS) {
            b.row(body, c[0], false, () -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(c[1])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            }, false);
        }
    }

    // ---------------------------------------------------------------- Vorschau

    private void showCam(int i) {
        cur = i;
        final Webcams.Cam cam = Webcams.CAMS[i];
        gen++;
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
        holder.setOnClickListener(v -> openFull());
        b.box.addView(holder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        prevStatus = b.text("Lade …", 12.5f, b.tx, false);
        prevStatus.setPadding(b.px(4), b.px(8), b.px(4), 0);
        b.text("Tippe aufs Bild für Vollbild  ·  Quelle: " + cam.source, 11, b.soft, false).setPadding(b.px(4), b.px(2), b.px(4), b.px(6));
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
        b.pillKeep("Neu laden", false, () -> {
            if (!hasImage) spin.setVisibility(View.VISIBLE);
            prevStatus.setText("Aktualisiere …");
            load();
        });
        b.pillKeep("Webseite öffnen", false, () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(cam.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
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
