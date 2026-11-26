/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Webcams als Blatt von unten: Ordner je Region (mit Live-Titelbild), Raster, Suche, Favoriten und Einzelansicht
 * mit Wischen zur naechsten Kamera. Tipp aufs Bild der Einzelansicht = Vollbild.
 */
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
    private static final int THUMB_W = 500;
    private static final int PREVIEW_W = 1000;
    private static final long[] INTERVALS = {30000L, 60000L, 300000L, 0L};
    private static final String[] INTERVAL_LABELS = {"30 Sek.", "1 Min.", "5 Min.", "Manuell"};
    private static final int[] KEEP = {3, 7, 14, 30};
    private static final String[] KEEP_LABELS = {"3 Tage", "7 Tage", "14 Tage", "30 Tage"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private Bubble b;
    private int gen;
    private Runnable up;

    // Kacheln des aktuell offenen Ordners/Rasters, deren Bilder nachgeladen werden
    private final List<Slot> slots = new ArrayList<>();
    private boolean ticked;
    private LinearLayout resultsBody;

    // Einzelansicht
    private int[] camList = new int[0];
    private int camPos;
    private boolean camOpen;
    private FrameLayout camHolder;
    private ImageView camImg;
    private ProgressBar camSpin;
    private TextView camTitle, camSub, camCount, camStatus, camSource;
    private ImageView camStar;
    private boolean camHasImage;
    private long camShownTaken = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long iv = Webcams.intervalMs(WebcamActivity.this);
            if (iv <= 0) return;
            ticked = true;
            if (camOpen) loadCam(false);
            else kick();
            ui.postDelayed(this, Math.max(iv, 30000L));
        }
    };

    private final class Slot {
        final Webcams.Cam cam;
        final ImageView iv;
        final View root;
        boolean busy;
        long shownTaken = -1;

        Slot(Webcams.Cam cam, ImageView iv, View root) {
            this.cam = cam;
            this.iv = iv;
            this.root = root;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        b = new Bubble(this, 330, 8);
        b.sheet();
        final Context app = getApplicationContext();
        pool.execute(() -> Webcams.cleanup(app));
        showFolders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camOpen && camImg != null) loadCam(false);
        else kick();
        ui.removeCallbacks(tick);
        long iv = Webcams.intervalMs(this);
        if (iv > 0) ui.postDelayed(tick, Math.max(iv, 30000L));
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (up != null) up.run();
        else b.close();
    }

    // ---------------------------------------------------------------- Bausteine

    private void reset() {
        gen++;
        ticked = false;
        slots.clear();
        camOpen = false;
        resultsBody = null;
        hideKeyboard();
        Bubble.soft(b.box);
        b.box.removeAllViews();
    }

    private void hideKeyboard() {
        try {
            View f = getCurrentFocus();
            if (f != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private int bodyW() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        return Math.min(screenW - b.px(16), b.px(520)) - b.px(16);
    }

    private void roundClip(View v, final float dp) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), b.px(dp));
            }
        });
    }

    private ImageView iconBtn(int res, String desc, Runnable click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(b.tx);
        iv.setPadding(b.px(10), b.px(10), b.px(10), b.px(10));
        iv.setContentDescription(desc);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        iv.setBackground(new RippleDrawable(ColorStateList.valueOf(b.ripple), null, mask));
        iv.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK);
            click.run();
        });
        iv.setLayoutParams(new LinearLayout.LayoutParams(b.px(40), b.px(40)));
        return iv;
    }

    /** Kopfzeile: optional Zurueck-Pfeil, Titel mit Unterzeile, rechts Symbolknoepfe. */
    private LinearLayout head(String title, String sub, Runnable back, View... right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, b.px(6));
        if (back != null) row.addView(iconBtn(R.drawable.ic_back, "Zurück", back));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(b.px(back != null ? 2 : 6), 0, b.px(4), 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTextColor(b.title);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        s.setTextColor(b.soft);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(s);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        for (View v : right) row.addView(v);
        b.box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setTag(new TextView[]{t, s});
        return row;
    }

    /** Kachel mit Bild (zugeschnitten), Verlauf unten und Name; liefert Wurzel, Bild ueber slot. */
    private FrameLayout tile(Webcams.Cam cam, String name, int w, int h) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(b.light ? 0x22000000 : 0x33FFFFFF);
        f.setBackground(bg);
        roundClip(f, 16);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xB8000000}));
        f.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(h * 0.6f), Gravity.BOTTOM));
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(b.px(10), 0, b.px(10), b.px(8));
        f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        f.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        if (cam != null) slots.add(new Slot(cam, iv, f));
        return f;
    }

    private void addCountPill(FrameLayout tile, String text) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextColor(0xFFFFFFFF);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        c.setPadding(b.px(9), b.px(3), b.px(9), b.px(3));
        GradientDrawable p = new GradientDrawable();
        p.setColor(0x66000000);
        p.setCornerRadius(b.px(12));
        c.setBackground(p);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.setMargins(0, b.px(8), b.px(8), 0);
        tile.addView(c, lp);
    }

    private void addStar(FrameLayout tile, final Webcams.Cam cam) {
        final ImageView st = new ImageView(this);
        st.setColorFilter(0xFFFFFFFF);
        st.setPadding(b.px(7), b.px(7), b.px(7), b.px(7));
        GradientDrawable p = new GradientDrawable();
        p.setShape(GradientDrawable.OVAL);
        p.setColor(0x66000000);
        st.setBackground(p);
        st.setContentDescription("Favorit");
        st.setImageResource(Webcams.isFav(this, cam) ? R.drawable.ic_star_fill : R.drawable.ic_star);
        st.setOnClickListener(v -> {
            boolean on = Webcams.toggleFav(this, cam);
            Haptic.play(v, on ? Haptic.TOGGLE_ON : Haptic.TOGGLE_OFF);
            st.setImageResource(on ? R.drawable.ic_star_fill : R.drawable.ic_star);
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(b.px(32), b.px(32), Gravity.TOP | Gravity.END);
        lp.setMargins(0, b.px(6), b.px(6), 0);
        tile.addView(st, lp);
    }

    /** Kacheln in Zweierreihen in den Container legen. */
    private void gridRows(LinearLayout body, List<FrameLayout> tiles, int tw) {
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                if (i + k < tiles.size()) {
                    View t = tiles.get(i + k);
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
                    lp.leftMargin = k == 0 ? 0 : b.px(8);
                    r.addView(t, lp);
                }
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = b.px(8);
            body.addView(r, rp);
        }
    }

    // ---------------------------------------------------------------- Ordner

    private void showFolders() {
        reset();
        up = null;
        head("Webcams", Webcams.CAMS.length + " Kameras in " + Webcams.FOLDERS.length + " Ordnern", null,
                iconBtn(R.drawable.ic_tune, "Webcam-Einstellungen", this::showSettings));

        // Suchleiste
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(b.px(12), 0, b.px(12), 0);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(b.neutral());
        bbg.setCornerRadius(b.px(14));
        bar.setBackground(bbg);
        ImageView mag = new ImageView(this);
        mag.setImageResource(R.drawable.ic_search);
        mag.setColorFilter(b.soft);
        bar.addView(mag, new LinearLayout.LayoutParams(b.px(18), b.px(18)));
        final EditText et = new EditText(this);
        et.setHint("Webcam oder Ort suchen");
        et.setHintTextColor(b.soft);
        et.setTextColor(b.tx);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setSingleLine(true);
        et.setBackground(null);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        et.setPadding(b.px(10), b.px(11), b.px(4), b.px(11));
        bar.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, b.px(44));
        blp.bottomMargin = b.px(4);
        b.box.addView(bar, blp);

        resultsBody = b.scrollBox(0.72f);
        fillFolders(resultsBody, "");
        ScrollHook.attach(resultsBody, this::kick);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (resultsBody == null) return;
                gen++;
                slots.clear();
                resultsBody.removeAllViews();
                fillFolders(resultsBody, e.toString().trim());
                kickSoon();
            }
        });
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
        kickSoon();
    }

    private void fillFolders(LinearLayout body, String q) {
        if (!q.isEmpty()) {
            fillSearch(body, q);
            return;
        }
        int tw = (bodyW() - b.px(8)) / 2;
        int th = Math.round(tw * 0.66f);
        // Favoriten (schmal, nur wenn ein Stern gesetzt ist)
        final int[] fav = favList();
        if (fav.length > 0) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(b.px(14), b.px(12), b.px(14), b.px(12));
            GradientDrawable g = new GradientDrawable();
            g.setColor(b.light ? 0x1A000000 : 0x26FFFFFF);
            g.setCornerRadius(b.px(16));
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(b.ripple), g, null));
            ImageView st = new ImageView(this);
            st.setImageResource(R.drawable.ic_star_fill);
            st.setColorFilter(b.tx);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(b.px(20), b.px(20));
            slp.rightMargin = b.px(12);
            r.addView(st, slp);
            TextView t = new TextView(this);
            t.setText("Favoriten");
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTextColor(b.tx);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView n = new TextView(this);
            n.setText(fav.length + "  ›");
            n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            n.setTextColor(b.soft);
            r.addView(n);
            r.setOnClickListener(v -> {
                Haptic.play(v, Haptic.TICK);
                showGrid("Favoriten", favList());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = b.px(4);
            body.addView(r, lp);
        }
        List<FrameLayout> tiles = new ArrayList<>();
        for (final Webcams.Folder f : Webcams.FOLDERS) {
            int[] ids = Webcams.folderCams(f);
            Webcams.Cam cover = null;
            for (int i : ids) {
                if (i >= 0) {
                    cover = Webcams.CAMS[i];
                    break;
                }
            }
            FrameLayout t = tile(cover, f.name, tw, th);
            addCountPill(t, String.valueOf(ids.length));
            t.setOnClickListener(v -> {
                Haptic.play(v, Haptic.TICK);
                showGrid(f.name, Webcams.folderCams(f));
            });
            tiles.add(t);
        }
        gridRows(body, tiles, tw);
        b.row(body, "Weitere Webcams", false, this::showLinks, false);
        decorate(body, 0, "↗");
        ((LinearLayout.LayoutParams) body.getChildAt(body.getChildCount() - 1).getLayoutParams()).topMargin = b.px(12);
    }

    private int[] favList() {
        Set<String> f = Webcams.favs(this);
        List<Integer> r = new ArrayList<>();
        for (String id : f) {
            int i = Webcams.indexOf(id);
            if (i >= 0) r.add(i);
        }
        int[] a = new int[r.size()];
        for (int i = 0; i < a.length; i++) a[i] = r.get(i);
        return a;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.GERMAN).replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss");
    }

    private void fillSearch(LinearLayout body, String q) {
        String nq = norm(q);
        final List<Integer> hit = new ArrayList<>();
        for (int i = 0; i < Webcams.CAMS.length; i++) {
            Webcams.Cam c = Webcams.CAMS[i];
            Webcams.Folder f = Webcams.folderOf(c);
            String hay = norm(c.name + " " + (f == null ? "" : f.name) + " " + c.source);
            if (hay.contains(nq)) hit.add(i);
        }
        if (hit.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Keine Treffer für „" + q + "“");
            t.setTextColor(b.soft);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setPadding(b.px(8), b.px(16), b.px(8), b.px(16));
            body.addView(t);
            return;
        }
        final int[] list = new int[hit.size()];
        for (int i = 0; i < list.length; i++) list[i] = hit.get(i);
        for (int k = 0; k < list.length; k++) {
            final int pos = k;
            Webcams.Cam c = Webcams.CAMS[list[k]];
            Webcams.Folder f = Webcams.folderOf(c);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(b.px(12), b.px(10), b.px(12), b.px(10));
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(b.ripple), null, new android.graphics.drawable.ColorDrawable(0xFFFFFFFF)));
            TextView t = new TextView(this);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            t.setTextColor(b.tx);
            SpannableString ss = new SpannableString(c.name);
            int at = norm(c.name).indexOf(nq);
            if (at >= 0 && at + nq.length() <= c.name.length()) {
                ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), at, at + nq.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new ForegroundColorSpan(b.light ? 0xFF1A5FB4 : 0xFFFFC857), at, at + nq.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            t.setText(ss);
            r.addView(t);
            TextView s = new TextView(this);
            s.setText((f == null ? "" : f.name + " · ") + c.source);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            s.setTextColor(b.soft);
            r.addView(s);
            r.setOnClickListener(v -> {
                Haptic.play(v, Haptic.TICK);
                showCam(list, pos, this::showFolders);
            });
            body.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /** Zeile der Liste um ein Symbol vorne und eine Marke hinten ergaenzen. */
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
        tr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tr.setTextColor(b.soft);
        r.addView(tr);
    }

    // ---------------------------------------------------------------- Raster

    private void showGrid(final String title, final int[] list) {
        reset();
        up = this::showFolders;
        head(title, list.length + (list.length == 1 ? " Kamera" : " Kameras"), this::showFolders);
        LinearLayout body = b.scrollBox(0.72f);
        int tw = (bodyW() - b.px(8)) / 2;
        int th = Math.round(tw * 0.66f);
        List<FrameLayout> tiles = new ArrayList<>();
        for (int k = 0; k < list.length; k++) {
            final int pos = k;
            if (list[k] < 0) continue;
            Webcams.Cam c = Webcams.CAMS[list[k]];
            FrameLayout t = tile(c, c.name, tw, th);
            addStar(t, c);
            t.setOnClickListener(v -> {
                Haptic.play(v, Haptic.TICK);
                showCam(list, pos, () -> showGrid(title, list));
            });
            tiles.add(t);
        }
        gridRows(body, tiles, tw);
        ScrollHook.attach(body, this::kick);
        b.space(4);
        b.pillKeep("Schließen", false, () -> b.close());
        kickSoon();
    }

    /** Laesst eine Aktion laufen, wenn der umgebende ScrollView scrollt. */
    private static final class ScrollHook {
        static void attach(final View inside, final Runnable r) {
            inside.post(() -> {
                android.view.ViewParent p = inside.getParent();
                if (p instanceof android.widget.ScrollView) {
                    ((android.widget.ScrollView) p).getViewTreeObserver().addOnScrollChangedListener(r::run);
                }
            });
        }
    }

    // ---------------------------------------------------------------- Bilder der Kacheln

    /** Nach dem Aufbau und nach dem Hochfahren des Blattes pruefen, welche Kacheln sichtbar sind. */
    private void kickSoon() {
        ui.postDelayed(this::kick, 80);
        ui.postDelayed(this::kick, 450);
    }

    /** Sichtbare Kacheln mit dem letzten gespeicherten Bild fuellen und bei Bedarf neu laden. */
    private void kick() {
        final int g = gen;
        final long maxAge = ticked ? Math.max(Webcams.intervalMs(this), 30000L) : Webcams.FRESH_MS;
        final boolean allowRefresh = !ticked || Webcams.intervalMs(this) > 0;
        final Context app = getApplicationContext();
        for (final Slot s : new ArrayList<>(slots)) {
            if (s.busy) continue;
            if (!s.root.getLocalVisibleRect(new android.graphics.Rect())) continue;
            s.busy = true;
            pool.execute(() -> {
                try {
                    Webcams.Shot c = s.iv.getDrawable() == null ? Webcams.cached(app, s.cam, THUMB_W, true) : null;
                    if (c != null && c.bmp != null) {
                        final Webcams.Shot cc = c;
                        ui.post(() -> {
                            if (g == gen) {
                                setImage(s, cc);
                            }
                        });
                    }
                    if (allowRefresh && Webcams.age(app, s.cam, true) > maxAge) {
                        final Webcams.Shot n = Webcams.refresh(app, s.cam, THUMB_W, true);
                        if (n.bmp != null && !n.offline) {
                            ui.post(() -> {
                                if (g == gen) setImage(s, n);
                            });
                        }
                    }
                } finally {
                    ui.post(() -> s.busy = false);
                }
            });
        }
    }

    /** Neues Bild weich ueber das alte blenden (nur wenn sich etwas geaendert hat). */
    private void setImage(Slot s, Webcams.Shot shot) {
        if (shot.bmp == null) return;
        if (s.shownTaken >= 0 && shot.taken > 0 && shot.taken == s.shownTaken) return;
        s.shownTaken = shot.taken;
        crossfade(s.iv, shot.bmp);
    }

    private void crossfade(ImageView iv, Bitmap bmp) {
        Drawable old = iv.getDrawable();
        BitmapDrawable nd = new BitmapDrawable(getResources(), bmp);
        if (old == null || Motion.reduced(this)) {
            iv.setImageDrawable(nd);
            if (old == null && !Motion.reduced(this)) {
                iv.setAlpha(0f);
                iv.animate().alpha(1f).setDuration(260).start();
            }
            return;
        }
        if (old instanceof TransitionDrawable) old = ((TransitionDrawable) old).getDrawable(1);
        TransitionDrawable td = new TransitionDrawable(new Drawable[]{old, nd});
        td.setCrossFadeEnabled(true);
        iv.setImageDrawable(td);
        td.startTransition(320);
    }

    // ---------------------------------------------------------------- Einzelansicht

    private void showCam(final int[] list, int pos, final Runnable back) {
        reset();
        camOpen = true;
        camList = list;
        camPos = Math.max(0, Math.min(pos, list.length - 1));
        up = back;
        camShownTaken = -1;
        camHasImage = false;

        camStar = iconBtn(R.drawable.ic_star, "Favorit", () -> {
            Webcams.Cam cam = Webcams.CAMS[camList[camPos]];
            boolean on = Webcams.toggleFav(this, cam);
            camStar.setImageResource(on ? R.drawable.ic_star_fill : R.drawable.ic_star);
        });
        LinearLayout h = head("", "", back,
                iconBtn(R.drawable.ic_refresh, "Neu laden", () -> loadCam(true)),
                iconBtn(R.drawable.ic_external, "Webseite öffnen", this::openPage),
                camStar);
        TextView[] tt = (TextView[]) h.getTag();
        camTitle = tt[0];
        camSub = tt[1];

        camHolder = new FrameLayout(this);
        camHolder.setBackgroundColor(0x33000000);
        camHolder.setMinimumHeight(b.px(200));
        roundClip(camHolder, 16);
        camImg = new ImageView(this);
        camImg.setAdjustViewBounds(true);
        camImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        camImg.setMaxHeight(b.px(300));
        camHolder.addView(camImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        camSpin = new ProgressBar(this);
        camHolder.addView(camSpin, new FrameLayout.LayoutParams(b.px(32), b.px(32), Gravity.CENTER));
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                openFull();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent c, float vx, float vy) {
                if (a == null || c == null) return false;
                float dx = c.getX() - a.getX(), dy = c.getY() - a.getY();
                if (Math.abs(dx) > b.px(50) && Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(vx) > 400) {
                    step(dx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        camHolder.setOnTouchListener((v, ev) -> gd.onTouchEvent(ev));
        b.flex(camHolder, 90);
        b.box.addView(camHolder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        camCount = b.text("", 12.5f, b.tx, false);
        camCount.setPadding(b.px(4), b.px(8), b.px(4), 0);
        camStatus = b.text("", 12.5f, b.soft, false);
        camStatus.setPadding(b.px(4), b.px(2), b.px(4), 0);
        camSource = b.text("", 11, b.soft, false);
        camSource.setPadding(b.px(4), b.px(2), b.px(4), b.px(6));
        b.text("Tippen = Vollbild  ·  Wischen = nächste Kamera", 11, b.soft, false).setPadding(b.px(4), 0, b.px(4), b.px(6));
        b.pillKeep("Zurück", false, back);
        bindCam();
        loadCam(false);
    }

    private void step(final int dir) {
        if (camList.length < 2 || camHolder == null) return;
        final float shift = b.px(48) * dir;
        if (Motion.reduced(this)) {
            camPos = ((camPos + dir) % camList.length + camList.length) % camList.length;
            bindCam();
            loadCam(false);
            return;
        }
        camHolder.animate().translationX(-shift).alpha(0f).setDuration(110).withEndAction(() -> {
            camPos = ((camPos + dir) % camList.length + camList.length) % camList.length;
            bindCam();
            loadCam(false);
            camHolder.setTranslationX(shift);
            camHolder.animate().translationX(0f).alpha(1f).setDuration(150).start();
        }).start();
    }

    /** Texte, Stern und das gespeicherte Bild der aktuellen Kamera setzen. */
    private void bindCam() {
        final Webcams.Cam cam = Webcams.CAMS[camList[camPos]];
        Webcams.Folder f = Webcams.folderOf(cam);
        Webcams.viewed(this, cam);
        camTitle.setText(cam.name);
        camSub.setText(f == null ? cam.source : f.name);
        camStar.setImageResource(Webcams.isFav(this, cam) ? R.drawable.ic_star_fill : R.drawable.ic_star);
        camCount.setText((camPos + 1) + " / " + camList.length);
        camSource.setText("Quelle: " + cam.source + (cam.small ? "  ·  nur kleines Vorschaubild" : ""));
        camShownTaken = -1;
        camImg.setImageDrawable(null);
        Webcams.Shot c = Webcams.cached(this, cam, PREVIEW_W);
        if (c.bmp == null && cam.thumb != null) c = Webcams.cached(this, cam, PREVIEW_W, true);
        camHasImage = c.bmp != null;
        if (c.bmp != null) {
            camImg.setImageBitmap(c.bmp);
            camSpin.setVisibility(View.GONE);
            camStatus.setText(Webcams.status(c));
        } else {
            camSpin.setVisibility(View.VISIBLE);
            camStatus.setText("Lade …");
        }
    }

    /** Neues Bild im Hintergrund holen; das gespeicherte bleibt sichtbar, bis es da ist. */
    private void loadCam(final boolean manual) {
        if (!camOpen || camList.length == 0) return;
        final int g = gen;
        final int at = camPos;
        final Webcams.Cam cam = Webcams.CAMS[camList[at]];
        if (manual) {
            camStatus.setText("Aktualisiere …");
            if (!camHasImage) camSpin.setVisibility(View.VISIBLE);
        }
        final Context app = getApplicationContext();
        pool.execute(() -> {
            final Webcams.Shot s = Webcams.refresh(app, cam, PREVIEW_W);
            ui.post(() -> {
                if (isFinishing() || isDestroyed() || g != gen || at != camPos || !camOpen) return;
                camSpin.setVisibility(View.GONE);
                if (s.bmp != null && !(camShownTaken >= 0 && s.taken > 0 && s.taken == camShownTaken)) {
                    camShownTaken = s.taken;
                    crossfade(camImg, s.bmp);
                    camHasImage = true;
                }
                camStatus.setText(Webcams.status(s));
            });
        });
    }

    private void openFull() {
        if (camList.length == 0) return;
        startActivity(new Intent(this, CamFullActivity.class)
                .putExtra(CamFullActivity.EXTRA_IDS, camList)
                .putExtra(CamFullActivity.EXTRA_INDEX, camPos));
    }

    private void openPage() {
        if (camList.length == 0) return;
        open(Webcams.CAMS[camList[camPos]].page);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- Weitere Webcams (nur Link)

    private void showLinks() {
        reset();
        up = this::showFolders;
        head("Weitere Webcams", "öffnet den Browser, kein Bild hier", this::showFolders);
        LinearLayout body = b.scrollBox(0.7f);
        for (final String[] c : LINKS) {
            b.row(body, c[0], false, () -> open(c[1]), false);
            decorate(body, 0, "↗");
        }
        b.space(4);
        b.pillKeep("Zurück", false, this::showFolders);
    }

    // ---------------------------------------------------------------- Einstellungen

    private int sInt, sKeep;
    private boolean sWifi;

    private void showSettings() {
        reset();
        up = this::showFolders;
        head("Webcam-Einstellungen", "gilt für alle Webcams", this::showFolders);
        long iv = Webcams.intervalMs(this);
        final int i0 = indexOf(INTERVALS, iv, 1);
        final int k0 = indexOf(KEEP, Webcams.keepDays(this), 2);
        final boolean w0 = Webcams.wifiOnly(this);
        sInt = i0;
        sKeep = k0;
        sWifi = w0;
        LinearLayout body = b.scrollBox(0.7f);
        b.label(body, "Aktualisierung bei offener Ansicht");
        b.segment(body, INTERVAL_LABELS, i0, i -> sInt = i);
        b.label(body, "Gespeicherte Bilder löschen nach (ohne Ansehen)");
        b.segment(body, KEEP_LABELS, k0, i -> sKeep = i);
        b.space(6);
        b.toggle(body, "Nur im WLAN aktualisieren", w0, on -> sWifi = on);
        TextView hint = new TextView(this);
        hint.setText("Favoriten und die eingebauten Kameras bleiben immer erhalten. Insgesamt höchstens "
                + (Webcams.LIMIT_BYTES / 1024 / 1024) + " MB; Webcams werden nicht im Hintergrund geladen.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        hint.setTextColor(b.soft);
        hint.setPadding(b.px(8), b.px(6), b.px(8), b.px(6));
        body.addView(hint);
        final TextView clear = b.pillIn(body, "Speicher leeren (" + sizeText(Webcams.cacheSize(this)) + ")", false, () -> { });
        clear.setOnClickListener(v -> {
            Webcams.clearCache(this);
            Haptic.play(v, Haptic.CONFIRM);
            clear.setText("Speicher leeren (" + sizeText(Webcams.cacheSize(this)) + ")");
        });
        b.space(4);
        TextView ok = b.footer(false, this::showFolders, () -> {
            Webcams.setIntervalMs(this, INTERVALS[sInt]);
            Webcams.setKeepDays(this, KEEP[sKeep]);
            Webcams.setWifiOnly(this, sWifi);
            Webcams.cleanup(getApplicationContext());
            showFolders();
        });
        b.watch(ok, () -> sInt != i0 || sKeep != k0 || sWifi != w0);
    }

    private static int indexOf(long[] a, long v, int def) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return def;
    }

    private static int indexOf(int[] a, int v, int def) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return def;
    }

    private static String sizeText(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(0, bytes / 1024) + " KB";
        return String.format(Locale.GERMANY, "%.1f MB", bytes / 1048576.0);
    }
}
