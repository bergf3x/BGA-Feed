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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Webcams als Blatt von unten: eine Liste, nach Regionen gegliedert, mit Reitern zum Springen, Suche und Favoriten.
 * Einzelansicht mit Wischen zur naechsten Kamera, Vollbild, Teilen und Webseite.
 */
public class WebcamActivity extends Activity {
    private static final int THUMB_W = 400;
    private static final int PREVIEW_W = 1000;
    private static final int PANO_THUMB_W = 1000;
    private static final int PANO_W = 3000;
    private static final long[] INTERVALS = {30000L, 60000L, 300000L, 0L};
    private static final String[] INTERVAL_LABELS = {"30 Sek.", "1 Min.", "5 Min.", "Manuell"};
    private static final int[] KEEP = {3, 7, 14, 30};
    private static final String[] KEEP_LABELS = {"3 Tage", "7 Tage", "14 Tage", "30 Tage"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    /** Dekodieren und Einlesen (schnell) getrennt vom Netz (kann bis 25 Sek. dauern), damit Vorschaubilder nicht hinter Downloads warten. */
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final ExecutorService netPool = Executors.newFixedThreadPool(3);
    private Bubble b;
    private int gen;
    private Runnable up;

    // Liste
    private final List<Slot> slots = new ArrayList<>();
    private boolean ticked;
    private String query = "";
    private int listY;
    private ScrollView listScroll;
    private LinearLayout listBody;
    private LinearLayout chipBox;
    private HorizontalScrollView chipScroll;
    private HorizontalScrollView tabScroll;
    private final List<TextView> tabViews = new ArrayList<>();
    /** Gewaehlter Reiter: 0 = Favoriten, 1.. = Land (Webcams.LANDS). -1 = noch nicht gewaehlt (App-Start). */
    private int tab = -1;
    /** Gewaehlte Region im Land. Leer = erste. Der Zustand gilt nur, solange die Ansicht offen ist: jeder Start beginnt vorn. */
    private String regName = "";

    // Einzelansicht
    private int[] camList = new int[0];
    private int camPos;
    private boolean camOpen;
    private FrameLayout camHolder;
    private PanoImageView camImg;
    private ProgressBar camSpin;
    private TextView camTitle, camSub, camCount, camStatus, camSource, camLink, camNote;
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
        final TextView sub;
        final String lead;
        boolean busy;
        long shownTaken = -1;

        Slot(Webcams.Cam cam, ImageView iv, View root, TextView sub, String lead) {
            this.cam = cam;
            this.iv = iv;
            this.root = root;
            this.sub = sub;
            this.lead = lead;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        b = new Bubble(this, 330, 8);
        b.sheet();
        final Context app = getApplicationContext();
        Webcams.loadIndex(app);
        pool.execute(() -> Webcams.cleanup(app));
        showMain();
        if (Webcams.indexStale(app)) refreshIndex(app);
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
        netPool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (up != null) up.run();
        else b.close();
    }

    /** Kameraliste im Hintergrund von foto-webcam.eu holen; danach Liste neu aufbauen, sofern sie gerade zu sehen ist. */
    private void refreshIndex(final Context app) {
        pool.execute(() -> {
            try {
                Webcams.fetchIndex(app);
            } catch (Exception e) {
                return;
            }
            ui.post(() -> {
                if (isFinishing() || isDestroyed() || camOpen || listBody == null) return;
                Webcams.loadIndex(app);
                int y = listScroll.getScrollY();
                fillList();
                listScroll.post(() -> listScroll.scrollTo(0, y));
                updateMainSub();
                kickSoon();
            });
        });
    }

    // ---------------------------------------------------------------- Bausteine

    private void reset() {
        gen++;
        ticked = false;
        slots.clear();
        camOpen = false;
        listBody = null;
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

    /** Kopfzeile: optional Zurueck-Pfeil, Titel mit Unterzeile, rechts Symbolknoepfe. Tag = {Titel, Unterzeile}. */
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

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.GERMAN).replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss");
    }

    private int imageCamCount() {
        int n = 0;
        for (Webcams.Cam c : Webcams.CAMS) if (!c.link) n++;
        return n;
    }

    private int regionCount() {
        int n = 0;
        for (Webcams.Region r : Webcams.REGS) if (regionSize(r) > 0) n++;
        return n;
    }

    /** Anzahl der Kameras einer Region, die gerade bekannt sind. */
    private int regionSize(Webcams.Region r) {
        int n = 0;
        for (String id : r.ids) if (Webcams.indexOf(id) >= 0) n++;
        return n;
    }

    private TextView mainSub;

    private void updateMainSub() {
        if (mainSub != null) mainSub.setText(imageCamCount() + " Kameras · " + regionCount() + " Regionen");
    }

    /**
     * Zeile unter dem Namen. In Liste einer Region: Hoehe, Aufnahmezeit, Quelle. Bei Favoriten und Suche steht
     * vorn "Land · Region" (lead), danach die Aufnahmezeit.
     */
    private String subText(Webcams.Cam c, String lead) {
        StringBuilder sb = new StringBuilder(lead);
        if (c.link) {
            if (sb.length() > 0) sb.append(" · ");
            return sb.append(c.source).toString();
        }
        if (lead.isEmpty()) {
            if (c.elev > 0) sb.append(String.format(Locale.GERMANY, "%,d m", c.elev)).append(" · ");
            String ago = Webcams.agoLabel(this, c, true);
            if (!ago.isEmpty()) sb.append(ago).append(" · ");
            sb.append(c.source);
        } else {
            String ago = Webcams.agoLabel(this, c, true);
            if (!ago.isEmpty()) sb.append(" · ").append(ago);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- Liste

    private void showMain() {
        if (Webcams.dirty) Webcams.loadIndex(getApplicationContext());
        reset();
        up = null;
        LinearLayout mh = head("Webcams", imageCamCount() + " Kameras · " + regionCount() + " Regionen", null,
                iconBtn(R.drawable.ic_tune, "Webcam-Einstellungen", this::showSettings));
        mainSub = ((TextView[]) mh.getTag())[1];

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
        et.setText(query);
        bar.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, b.px(44));
        blp.bottomMargin = b.px(8);
        b.box.addView(bar, blp);

        // Reiter: Favoriten (fest vorn), dann die Laender
        if (tab < 0) tab = favList().length > 0 ? 0 : 1;
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout tabBox = new LinearLayout(this);
        tabBox.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(tabBox, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabViews.clear();
        for (int i = 0; i <= Webcams.LANDS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setGravity(Gravity.CENTER);
            t.setPadding(b.px(16), 0, b.px(16), 0);
            t.setSingleLine(true);
            t.setOnClickListener(v -> {
                if (tab == idx) return;
                Haptic.play(v, Haptic.TICK);
                tab = idx;
                regName = "";
                listY = 0;
                if (listBody == null) return;
                // beim Wechsel steht alles wieder vorn: Regionen-Leiste und Liste
                chipScroll.scrollTo(0, 0);
                listScroll.scrollTo(0, 0);
                fillList();
                kickSoon();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, b.px(38));
            lp.rightMargin = b.px(6);
            tabBox.addView(t, lp);
            tabViews.add(t);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = b.px(6);
        b.box.addView(tabScroll, tlp);

        // Regionen des gewaehlten Landes
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setHorizontalFadingEdgeEnabled(true);
        chipScroll.setFadingEdgeLength(b.px(28));
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipBox = new LinearLayout(this);
        chipBox.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipBox, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.box.addView(chipScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        listBody = b.scrollBox(0.72f);
        listScroll = (ScrollView) listBody.getParent();
        // feste Hoehe: egal ob 14 oder 4 Kameras, das Fenster springt nicht (darunter bleibt es leer)
        if (listScroll instanceof Bubble.Flex) listBody.setMinimumHeight(((Bubble.Flex) listScroll).max);
        fillList();
        final int restore = listY;
        listScroll.post(() -> listScroll.scrollTo(0, restore));
        listScroll.getViewTreeObserver().addOnScrollChangedListener(this::kickThrottled);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (listBody == null) return;
                query = e.toString().trim();
                listY = 0;
                listScroll.scrollTo(0, 0);
                fillList();
                kickSoon();
            }
        });
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
        kickSoon();
    }

    private void paintTabs() {
        int favN = favList().length;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = tabViews.get(i);
            boolean on = i == tab;
            t.setText(i == 0 ? "★ Favoriten" + (favN > 0 ? "  " + favN : "") : Webcams.LANDS[i - 1]);
            t.setTextColor(on ? b.hiTx : b.tx);
            t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(b.px(19));
            g.setColor(on ? b.hi : b.neutral());
            t.setBackground(g);
        }
    }

    /** Liste und Reiter neu aufbauen (Start, Suche, Favoriten geaendert, Reiter gewechselt). */
    private void fillList() {
        gen++;
        slots.clear();
        listBody.removeAllViews();
        chipBox.removeAllViews();
        favSet = Webcams.favs(this);
        String nq = norm(query);
        boolean searching = !nq.isEmpty();
        paintTabs();
        tabScroll.setVisibility(searching ? View.GONE : View.VISIBLE);
        chipScroll.setVisibility(View.GONE);
        int shown = 0;

        if (searching) {
            // Suche ueber alle Laender: Treffer mit "Land · Region"
            List<Integer> hit = new ArrayList<>();
            List<String> lead = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Webcams.Region r : Webcams.REGS) {
                for (String id : r.ids) {
                    int i = Webcams.indexOf(id);
                    if (i < 0 || !seen.add(id)) continue;
                    Webcams.Cam c = Webcams.CAMS[i];
                    if (!norm(c.name + " " + r.name + " " + r.land + " " + c.source + " " + c.desc + Webcams.peakNames(c.id)).contains(nq)) continue;
                    hit.add(i);
                    lead.add(r.land + " · " + r.name);
                }
            }
            int[] nav = navOf(hit);
            for (int k = 0; k < hit.size(); k++) row(Webcams.CAMS[hit.get(k)], nav, navPos(nav, hit.get(k)), nq, lead.get(k));
            shown = hit.size();
        } else if (tab == 0) {
            int[] fav = favList();
            for (int k = 0; k < fav.length; k++) {
                Webcams.Cam c = Webcams.CAMS[fav[k]];
                Webcams.Region r = Webcams.regionOf(c);
                row(c, fav, k, "", r == null ? "" : r.land + " · " + r.name);
            }
            shown = fav.length;
            if (shown == 0) {
                note("Noch keine Favoriten. Tippe bei einer Webcam auf den Stern.");
                return;
            }
        } else {
            String land = Webcams.LANDS[tab - 1];
            List<Webcams.Region> regs = new ArrayList<>();
            for (Webcams.Region r : Webcams.REGS) if (r.land.equals(land) && regionSize(r) > 0) regs.add(r);
            // groesste Region zuerst; bei gleicher Zahl bleibt die feste Reihenfolge
            final java.util.Map<Webcams.Region, Integer> size = new java.util.HashMap<>();
            for (Webcams.Region r : regs) size.put(r, regionSize(r));
            java.util.Collections.sort(regs, (x, y) -> Integer.compare(size.get(y), size.get(x)));
            Webcams.Region sel = null;
            for (Webcams.Region r : regs) if (r.name.equals(regName)) sel = r;
            if (sel == null && !regs.isEmpty()) {
                sel = regs.get(0);
                regName = sel.name;
            }
            if (sel == null) {
                note("Für dieses Land gibt es noch keine Webcams.");
                return;
            }
            for (final Webcams.Region r : regs) chip(r, r == sel, size.get(r));
            chipScroll.setVisibility(View.VISIBLE);
            List<Integer> all = new ArrayList<>();
            for (String id : sel.ids) {
                int i = Webcams.indexOf(id);
                if (i >= 0) all.add(i);
            }
            int[] nav = navOf(all);
            for (int i : all) row(Webcams.CAMS[i], nav, navPos(nav, i), "", "");
            shown = all.size();
        }
        if (shown == 0) note("Keine Treffer für „" + query + "“");
    }

    private void note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(b.soft);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(b.px(8), b.px(16), b.px(8), b.px(16));
        listBody.addView(t);
    }

    /** Kameras mit Bild aus einer Trefferliste (fuer das Wischen in der Einzelansicht). */
    private int[] navOf(List<Integer> idx) {
        List<Integer> r = new ArrayList<>();
        for (int i : idx) if (!Webcams.CAMS[i].link) r.add(i);
        int[] a = new int[r.size()];
        for (int k = 0; k < a.length; k++) a[k] = r.get(k);
        return a;
    }

    private int navPos(int[] nav, int camIndex) {
        for (int k = 0; k < nav.length; k++) if (nav[k] == camIndex) return k;
        return -1;
    }

    /** Reiter einer Region: Name und Anzahl; der gewaehlte ist hervorgehoben (weiche Kapsel). */
    private void chip(final Webcams.Region r, boolean on, int count) {
        TextView c = new TextView(this);
        c.setText(r.name + "  " + count);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        c.setGravity(Gravity.CENTER);
        c.setPadding(b.px(14), 0, b.px(14), 0);
        c.setSingleLine(true);
        c.setTextColor(on ? b.tx : b.soft);
        c.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(b.px(11));
        g.setColor(on ? (b.light ? 0x22000000 : 0x26FFFFFF) : 0);
        c.setBackground(g);
        c.setOnClickListener(v -> {
            if (r.name.equals(regName)) return;
            Haptic.play(v, Haptic.TICK);
            regName = r.name;
            listY = 0;
            listScroll.scrollTo(0, 0);
            fillList();
            kickSoon();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, b.px(36));
        lp.rightMargin = b.px(6);
        chipBox.addView(c, lp);
    }

    /** Eine Zeile: Vorschaubild, Name, Aufnahmezeit und Quelle, Stern; bei Nur-Link-Seiten Pfeil-Symbol. */
    private void row(final Webcams.Cam c, final int[] nav, final int pos, String hl, final String lead) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(b.px(4), b.px(6), b.px(4), b.px(6));
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(b.px(14));
        mask.setColor(0xFFFFFFFF);
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(b.ripple), null, mask));

        FrameLayout th = new FrameLayout(this);
        GradientDrawable thbg = new GradientDrawable();
        thbg.setColor(b.light ? 0x1A000000 : 0x26FFFFFF);
        th.setBackground(thbg);
        roundClip(th, 12);
        ImageView iv;
        if (c.pano) {
            PanoImageView pv = new PanoImageView(this);
            pv.setPan(c.panX);
            iv = pv;
        } else {
            iv = new ImageView(this);
        }
        if (c.link) {
            iv.setImageResource(R.drawable.ic_external);
            iv.setColorFilter(b.soft);
            th.addView(iv, new FrameLayout.LayoutParams(b.px(22), b.px(22), Gravity.CENTER));
        } else {
            if (!c.pano) iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        r.addView(th, new LinearLayout.LayoutParams(b.px(76), b.px(54)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(b.px(12), 0, b.px(4), 0);
        TextView name = new TextView(this);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        name.setTextColor(b.tx);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        SpannableString ss = new SpannableString(c.name);
        int at = hl.isEmpty() ? -1 : norm(c.name).indexOf(hl);
        if (at >= 0 && at + hl.length() <= c.name.length()) {
            ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), at, at + hl.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(b.light ? 0xFF1A5FB4 : 0xFFFFC857), at, at + hl.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        name.setText(ss);
        col.addView(name);
        TextView sub = new TextView(this);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTextColor(b.soft);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setText(subText(c, lead));
        col.addView(sub);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!c.link) {
            final ImageView st = new ImageView(this);
            st.setPadding(b.px(10), b.px(10), b.px(10), b.px(10));
            st.setContentDescription("Favorit");
            final boolean isFav = favSet.contains(c.id);
            st.setImageResource(isFav ? R.drawable.ic_star_fill : R.drawable.ic_star);
            st.setColorFilter(isFav ? (b.light ? 0xFF1A5FB4 : 0xFFFFC857) : b.soft);
            st.setOnClickListener(v -> {
                boolean on = Webcams.toggleFav(this, c);
                Haptic.play(v, on ? Haptic.TOGGLE_ON : Haptic.TOGGLE_OFF);
                // Favoriten-Abschnitt aendert sich: Liste an gleicher Stelle neu aufbauen
                int y = listScroll.getScrollY();
                fillList();
                listScroll.post(() -> listScroll.scrollTo(0, y));
                kickSoon();
            });
            r.addView(st, new LinearLayout.LayoutParams(b.px(40), b.px(40)));
            slots.add(new Slot(c, iv, r, sub, lead));
        }
        r.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK);
            if (c.link) open(c.page);
            else {
                listY = listScroll.getScrollY();
                showCam(nav, pos, this::showMain);
            }
        });
        listBody.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Favoriten, einmal je Listenaufbau gelesen (statt je Zeile). */
    private Set<String> favSet = new java.util.HashSet<>();

    private int[] favList() {
        Set<String> f = Webcams.favs(this);
        List<Integer> r = new ArrayList<>();
        for (String id : f) {
            int i = Webcams.indexOf(id);
            if (i >= 0 && !Webcams.CAMS[i].link) r.add(i);
        }
        // zuletzt hinzugefuegt zuerst
        int[] a = new int[r.size()];
        for (int i = 0; i < a.length; i++) a[i] = r.get(r.size() - 1 - i);
        return a;
    }

    // ---------------------------------------------------------------- Bilder der Zeilen

    private boolean kickPending;

    /** Beim Scrollen hoechstens alle 120 ms pruefen, welche Zeilen sichtbar sind (nicht bei jedem Pixel). */
    private void kickThrottled() {
        if (kickPending) return;
        kickPending = true;
        ui.postDelayed(() -> {
            kickPending = false;
            kick();
        }, 120);
    }

    /** Nach dem Aufbau und nach dem Hochfahren des Blattes pruefen, welche Zeilen sichtbar sind. */
    private void kickSoon() {
        ui.postDelayed(this::kick, 80);
        ui.postDelayed(this::kick, 450);
    }

    /** Sichtbare Zeilen mit dem letzten gespeicherten Bild fuellen und bei Bedarf neu laden. */
    private void kick() {
        if (camOpen) return;
        final int g = gen;
        final long maxAge = ticked ? Math.max(Webcams.intervalMs(this), 30000L) : Webcams.FRESH_MS;
        final boolean allowRefresh = !ticked || Webcams.intervalMs(this) > 0;
        final Context app = getApplicationContext();
        for (final Slot s : new ArrayList<>(slots)) {
            if (s.busy) continue;
            if (!s.root.getLocalVisibleRect(new android.graphics.Rect())) continue;
            s.busy = true;
            final boolean needImage = s.iv.getDrawable() == null;
            pool.execute(() -> {
                boolean fetch = false;
                try {
                    if (needImage) {
                        final Webcams.Shot cc = Webcams.cached(app, s.cam, s.cam.pano ? PANO_THUMB_W : THUMB_W, true);
                        if (cc.bmp != null) ui.post(() -> {
                            if (g == gen) setImage(s, cc);
                        });
                    }
                    fetch = allowRefresh && Webcams.age(app, s.cam, true) > maxAge;
                } finally {
                    if (!fetch) ui.post(() -> s.busy = false);
                }
                if (!fetch) return;
                // Netzabruf in eigenem Pool: ein langsamer Download haelt keine Vorschaubilder auf
                try {
                    netPool.execute(() -> {
                        try {
                            final Webcams.Shot n = Webcams.refresh(app, s.cam, s.cam.pano ? PANO_THUMB_W : THUMB_W, true, false);
                            if (n.offline) return;
                            if (n.bmp != null) {
                                ui.post(() -> {
                                    if (g == gen) setImage(s, n);
                                });
                            } else if (n.unchanged) {
                                ui.post(() -> {
                                    if (g == gen && s.sub != null) s.sub.setText(subText(s.cam, s.lead));
                                });
                            }
                        } finally {
                            ui.post(() -> s.busy = false);
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    ui.post(() -> s.busy = false);
                }
            });
        }
    }

    /** Neues Bild weich ueber das alte blenden (nur wenn sich etwas geaendert hat). */
    private void setImage(Slot s, Webcams.Shot shot) {
        if (shot.bmp == null) return;
        if (s.sub != null) s.sub.setText(subText(s.cam, s.lead));
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

    private Button2 bigButton(int icon, String label, boolean primary, Runnable click) {
        return new Button2(icon, label, primary, click);
    }

    /** Knopf mit Symbol und Text; primaer = hell hervorgehoben. */
    private final class Button2 {
        final LinearLayout view;

        Button2(int icon, String label, boolean primary, final Runnable click) {
            view = new LinearLayout(WebcamActivity.this);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER);
            view.setPadding(b.px(12), 0, b.px(12), 0);
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(b.px(14));
            fill.setColor(primary ? b.hi : b.neutral());
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(b.px(14));
            mask.setColor(0xFFFFFFFF);
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33000000 : b.ripple), fill, mask));
            ImageView iv = new ImageView(WebcamActivity.this);
            iv.setImageResource(icon);
            iv.setColorFilter(primary ? b.hiTx : b.tx);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(b.px(20), b.px(20));
            ip.rightMargin = b.px(8);
            view.addView(iv, ip);
            TextView t = new TextView(WebcamActivity.this);
            t.setText(label);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setTextColor(primary ? b.hiTx : b.tx);
            if (primary) t.setTypeface(null, android.graphics.Typeface.BOLD);
            t.setSingleLine(true);
            view.addView(t);
            view.setContentDescription(label);
            view.setOnClickListener(v -> {
                Haptic.play(v, Haptic.TICK);
                click.run();
            });
        }
    }

    private void showCam(final int[] list, int pos, final Runnable back) {
        if (list.length == 0) return;
        reset();
        camOpen = true;
        camList = list;
        camPos = Math.max(0, Math.min(pos, list.length - 1));
        up = back;
        camShownTaken = -1;
        camHasImage = false;

        camStar = iconBtn(R.drawable.ic_star, "Favorit", () -> {
            Webcams.Cam cam = Webcams.CAMS[camList[camPos]];
            Webcams.toggleFav(this, cam);
            paintStar(cam);
        });
        LinearLayout h = head("", "", back, iconBtn(R.drawable.ic_refresh, "Neu laden", () -> loadCam(true)), camStar);
        TextView[] tt = (TextView[]) h.getTag();
        camTitle = tt[0];
        camSub = tt[1];

        camHolder = new FrameLayout(this);
        camHolder.setBackgroundColor(0x33000000);
        camHolder.setMinimumHeight(b.px(200));
        roundClip(camHolder, 16);
        camImg = new PanoImageView(this);
        camImg.setAdjustViewBounds(true);
        camImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        camImg.setMaxHeight(b.px(280));
        camHolder.addView(camImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        camSpin = new ProgressBar(this);
        camHolder.addView(camSpin, new FrameLayout.LayoutParams(b.px(32), b.px(32), Gravity.CENTER));
        camCount = new TextView(this);
        camCount.setTextColor(0xFFFFFFFF);
        camCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        camCount.setPadding(b.px(10), b.px(3), b.px(10), b.px(3));
        GradientDrawable cp = new GradientDrawable();
        cp.setColor(0x66000000);
        cp.setCornerRadius(b.px(12));
        camCount.setBackground(cp);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        clp.setMargins(0, b.px(10), b.px(10), 0);
        camHolder.addView(camCount, clp);
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

        camStatus = b.text("", 12.5f, b.tx, false);
        camStatus.setPadding(b.px(4), b.px(8), b.px(4), 0);
        camSource = b.text("", 11.5f, b.soft, false);
        camSource.setPadding(b.px(4), b.px(2), b.px(4), b.px(8));

        // Knoepfe: Vollbild gross, darunter Teilen und Webseite
        LinearLayout box = b.box;
        LinearLayout.LayoutParams full = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, b.px(46));
        box.addView(bigButton(R.drawable.ic_fullscreen, "Vollbild", true, this::openFull).view, full);
        LinearLayout two = new LinearLayout(this);
        two.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, b.px(46), 1f);
        l1.rightMargin = b.px(3);
        two.addView(bigButton(R.drawable.ic_share, "Teilen", false, () -> Webcams.share(this, Webcams.CAMS[camList[camPos]])).view, l1);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, b.px(46), 1f);
        l2.leftMargin = b.px(3);
        two.addView(bigButton(R.drawable.ic_external, "Webseite", false, () -> open(Webcams.CAMS[camList[camPos]].page)).view, l2);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = b.px(6);
        box.addView(two, tp);

        camLink = b.text("", 11.5f, b.soft, false);
        camLink.setPadding(b.px(4), b.px(10), b.px(4), 0);
        camLink.setSingleLine(true);
        camLink.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        camNote = b.text("", 11, b.soft, false);
        camNote.setPadding(b.px(4), b.px(2), b.px(4), b.px(4));
        bindCam();
        loadCam(false);
    }

    private void paintStar(Webcams.Cam cam) {
        boolean on = Webcams.isFav(this, cam);
        camStar.setImageResource(on ? R.drawable.ic_star_fill : R.drawable.ic_star);
        camStar.setColorFilter(on ? (b.light ? 0xFF1A5FB4 : 0xFFFFC857) : b.tx);
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
        paintStar(cam);
        camCount.setText((camPos + 1) + " / " + camList.length);
        camSource.setText("Quelle: " + cam.source + (cam.small ? "  ·  nur kleines Vorschaubild" : ""));
        camLink.setText(cam.page.replace("https://", "").replace("www.", ""));
        camNote.setText(cam.generic
                ? "Hinweis: Dieser Link führt auf die gemeinsame Webcam-Seite von Berchtesgaden, nicht auf genau diese Kamera."
                : cam.pano
                ? "Ausschnitt aus dem 360°-Panorama  ·  Vollbild zeigt das ganze Bild (Spreizen = zoomen, Ziehen = verschieben)"
                : "Tippen aufs Bild = Vollbild  ·  Wischen = nächste Kamera");
        camShownTaken = -1;
        camImg.setImageDrawable(null);
        // Panorama: normaler Ausschnitt in fester Hoehe, sonst Bild einpassen
        FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) camImg.getLayoutParams();
        ilp.height = cam.pano ? b.px(200) : ViewGroup.LayoutParams.WRAP_CONTENT;
        camImg.setAdjustViewBounds(!cam.pano);
        camImg.setLayoutParams(ilp);
        camImg.setPan(cam.pano ? cam.panX : -1f);
        Webcams.Shot c = Webcams.cached(this, cam, previewW(cam));
        if (c.bmp == null && cam.thumb != null) c = Webcams.cached(this, cam, previewW(cam), true);
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
            final Webcams.Shot s = Webcams.refresh(app, cam, previewW(cam));
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

    private int previewW(Webcams.Cam cam) {
        return cam.pano ? PANO_W : PREVIEW_W;
    }

    private void openFull() {
        if (camList.length == 0) return;
        String[] ids = new String[camList.length];
        for (int i = 0; i < ids.length; i++) ids[i] = Webcams.CAMS[camList[i]].id;
        startActivity(new Intent(this, CamFullActivity.class)
                .putExtra(CamFullActivity.EXTRA_IDS, ids)
                .putExtra(CamFullActivity.EXTRA_INDEX, camPos));
    }

    // ---------------------------------------------------------------- Einstellungen

    private int sInt, sKeep;
    private boolean sWifi;

    private void showSettings() {
        reset();
        up = this::showMain;
        head("Webcam-Einstellungen", "gilt für alle Webcams", this::showMain);
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
        TextView ok = b.footer(false, this::showMain, () -> {
            Webcams.setIntervalMs(this, INTERVALS[sInt]);
            Webcams.setKeepDays(this, KEEP[sKeep]);
            Webcams.setWifiOnly(this, sWifi);
            Webcams.cleanup(getApplicationContext());
            showMain();
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
