package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Vollbild-App: Wetter und Nachrichten als eigene Ansicht, dazu "Widget hinzufuegen".
 * Nutzt dieselben Daten (Cache, Abruf, Einstellungen-Logik) wie das Widget, aber eigene Einstellungen (Prefs.APP).
 */
public class MainActivity extends Activity {
    private static final int ID = Prefs.APP;
    private static final long STALE_MS = 5 * 60000L;
    private static final int MAX_ITEMS = 60;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    /** Wetterkarte gross / als schmale Zeile (beim Scrollen der Nachrichten). */
    private View wxFull, wxMini, wxIn, wxCam, wxShield, hintCard;
    /** Halter der Wetterkarte in der Liste (feste Hoehe = volle Karte) und der Reiter-Streifen. */
    private FrameLayout wxBox;
    private View chipsWrap;
    private int wxFullH;
    private int[] winCols = {0xFF101010};
    private LinearLayout page;
    private LinearLayout top;
    private float d;
    private boolean light;
    private int tx, soft, line, cardFill, chipOn, chipOnTx, accentBar;
    private int sizeIdx;
    private Typeface face;
    private boolean syncing;
    private boolean firstResume = true;
    private long lastSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        // "Neu seit deinem letzten Besuch": Zeitpunkt, als die App zuletzt verlassen wurde
        visitMark = getSharedPreferences("bga_widget", MODE_PRIVATE).getLong("last_visit", 0);
        if (!warmed) {
            // gespeicherte Feeds im Hintergrund einlesen, damit der Start nicht ruckelt
            final android.content.Context app = getApplicationContext();
            new Thread(() -> {
                try {
                    List<Catalog.Feed> all = Catalog.load(app);
                    for (String fid : Catalog.effective(all, Prefs.feeds(app, ID))) {
                        Catalog.Feed f = Catalog.find(all, fid);
                        if (f != null) FeedCache.items(app, f.id, f.name);
                    }
                } catch (Exception ignored) {
                }
                warmed = true;
                ui.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    int y = scroll == null ? 0 : scroll.getScrollY();
                    build();
                    restore(y);
                });
            }).start();
        }
    }

    private String shownTopic;
    private long visitMark;
    private final android.content.BroadcastReceiver dataChanged = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent i) {
            if (syncing || isFinishing() || isDestroyed()) return;
            if (pullBusy()) {
                pullRebuild = true; // erst nach der Animation neu aufbauen, sonst ruckelt es
                return;
            }
            int y = scroll == null ? 0 : scroll.getScrollY();
            build();
            restore(y);
        }
    };

    private static android.graphics.Bitmap lastSky;
    private static int lastSkyPlace = -1;

    /** Gespeicherte Feeds einmal pro Prozess im Hintergrund eingelesen. */
    private static volatile boolean warmed;

    @Override
    protected void onResume() {
        super.onResume();
        android.content.IntentFilter flt = new android.content.IntentFilter(Direct.DATA_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(dataChanged, flt, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dataChanged, flt);
        int y = scroll == null ? 0 : scroll.getScrollY();
        // anderes Thema gewaehlt (Auswahl-Blase): Liste von oben zeigen, nicht an der alten Stelle
        String tp = Prefs.topicRaw(this, ID);
        if (shownTopic != null && !shownTopic.equals(tp)) y = 0;
        shownTopic = tp;
        boolean stale = System.currentTimeMillis() - Math.max(lastSync, Prefs.newsTime(this)) > STALE_MS;
        // eigene Quellen der App, die das Widget nie geladen hat: einmal gezielt holen
        boolean missing = warmed && !triedMissing && feedsMissing();
        if (missing) triedMissing = true;
        boolean due = (firstResume || stale || missing) && !syncing;
        firstResume = false;
        if (due) startSync(missing, y);
        else {
            build();
            restore(y);
        }
        ensureWeather();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences("bga_widget", MODE_PRIVATE).edit().putLong("last_visit", System.currentTimeMillis()).apply();
        try {
            unregisterReceiver(dataChanged);
        } catch (Exception ignored) {
        }
    }

    private void restore(final int y) {
        if (scroll == null) return;
        // vor dem naechsten Zeichnen setzen, damit die Liste nicht kurz oben aufblitzt
        scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                scroll.scrollTo(0, y);
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ Abruf

    /** Knopf "Aktualisieren": Wetter, Warnungen und Nachrichten sofort neu holen. */
    private void sync(boolean force) {
        if (syncing) return;
        startSync(force, 0);
    }

    private void startSync(final boolean force, final int keepY) {
        startSync(force, keepY, false);
    }

    /** quiet: kein Neuaufbau beim Start (Ziehen zum Aktualisieren), damit die Animation nicht hakt. */
    private void startSync(final boolean force, final int keepY, final boolean quiet) {
        syncing = true;
        if (!quiet) {
            build();
            restore(keepY);
        }
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            try {
                ListService.syncApp(app, force);
            } catch (Exception ignored) {
            }
            lastSync = System.currentTimeMillis();
            ui.post(() -> {
                syncing = false;
                if (isFinishing() || isDestroyed()) return;
                final int y = scroll == null ? 0 : scroll.getScrollY();
                if (pullLoading) {
                    // erst "Aktualisiert ✓" stehen lassen (Kreis steht still), dann im naechsten Bild neu aufbauen
                    pullShowDone();
                    ui.post(() -> {
                        build();
                        restore(0);
                        ui.postDelayed(this::pullBack, 450);
                    });
                    return;
                }
                build();
                restore(force ? 0 : y);
            });
        }).start();
    }

    // ------------------------------------------------------------------ Ziehen zum Aktualisieren

    private LinearLayout pullBox;
    private TextView pullLabel;
    private PullSpinner pullSpin;
    private float pullStartX, pullStartY, pullOffset;
    private boolean pullArmed, pulling, pullLoading, pullBuzzed, pullRebuild;
    private android.animation.ValueAnimator pullAnim;

    /** Glas-Kreis mit Bogen: fuellt sich beim Ziehen, dreht sich beim Laden. */
    static final class PullSpinner extends View {
        float progress;
        boolean spin;
        private float angle;
        private final android.graphics.Paint ring = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint arc = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();

        PullSpinner(android.content.Context c, int color, float dp) {
            super(c);
            fill.setColor(0x1AFFFFFF);
            ring.setStyle(android.graphics.Paint.Style.STROKE);
            ring.setStrokeWidth(dp);
            ring.setColor(0x46FFFFFF);
            arc.setStyle(android.graphics.Paint.Style.STROKE);
            arc.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            arc.setStrokeWidth(dp * 3f);
            arc.setColor(color);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f - ring.getStrokeWidth();
            c.drawCircle(w / 2, h / 2, r, fill);
            c.drawCircle(w / 2, h / 2, r, ring);
            float rr = r * 0.56f;
            box.set(w / 2 - rr, h / 2 - rr, w / 2 + rr, h / 2 + rr);
            if (spin) {
                angle = (angle + 7f) % 360f;
                c.drawArc(box, angle - 90, 280, false, arc);
                postInvalidateOnAnimation();
            } else {
                c.drawArc(box, -90, Math.max(4f, 320f * progress), false, arc);
            }
        }
    }

    private View buildPull() {
        pullBox = new LinearLayout(this);
        pullBox.setOrientation(LinearLayout.VERTICAL);
        pullBox.setGravity(Gravity.CENTER_HORIZONTAL);
        pullBox.setAlpha(0f);
        pullBox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pullLabel = text("Zum Aktualisieren ziehen", 14, tx, false);
        pullLabel.setPadding(0, 0, 0, px(8));
        pullLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        pullBox.addView(pullLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pullSpin = new PullSpinner(this, tx, getResources().getDisplayMetrics().density * 1.2f);
        pullBox.addView(pullSpin, lp(38, 38, 0, 0, 0, 0));
        return pullBox;
    }

    /** Liste um "offset" nach unten schieben und Anzeige im freien Raum darueber mitfuehren. */
    private void setPull(float offset) {
        pullOffset = offset;
        if (page != null) page.setTranslationY(offset);
        if (pullBox == null) return;
        float th = px(96);
        int h = pullBox.getHeight() == 0 ? px(70) : pullBox.getHeight();
        pullBox.setTranslationY(Math.max(-h, offset / 2f - h / 2f));
        pullBox.setAlpha(pullLoading ? Math.min(1f, offset / px(40)) : Math.min(1f, offset / th));
        if (!pullLoading) pullSpin.progress = Math.min(1f, offset / th);
        pullSpin.invalidate();
    }

    private void animatePull(float to, long ms, Runnable end) {
        if (pullAnim != null) pullAnim.cancel();
        pullAnim = android.animation.ValueAnimator.ofFloat(pullOffset, to);
        pullAnim.setDuration(ms);
        pullAnim.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
        pullAnim.addUpdateListener(a -> setPull((float) a.getAnimatedValue()));
        if (end != null) pullAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (!cancelled) end.run();
            }
        });
        pullAnim.start();
    }

    private boolean pullBusy() {
        return pulling || pullLoading || (pullAnim != null && pullAnim.isRunning()) || pullOffset > 0.5f;
    }

    private void pullText(String t) {
        if (!t.contentEquals(pullLabel.getText())) pullLabel.setText(t); // nur bei Aenderung: kein unnoetiges Neu-Layout
    }

    /** Abruf fertig: Kreis steht, "Aktualisiert ✓". */
    private void pullShowDone() {
        pullSpin.spin = false;
        pullSpin.progress = 1f;
        pullSpin.invalidate();
        boolean ok = Prefs.newsOk(this);
        pullText(ok ? "Aktualisiert ✓" : "Keine Verbindung");
        Haptic.play(pullBox, ok ? Haptic.CONFIRM : Haptic.REJECT);
    }

    /** Sanft zurueck nach oben. */
    private void pullBack() {
        animatePull(0, 420, () -> {
            pullLoading = false;
            pullText("Zum Aktualisieren ziehen");
            if (pullRebuild) {
                pullRebuild = false;
                build();
                restore(0);
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (scroll == null || pullBox == null) return super.dispatchTouchEvent(e);
        int act = e.getActionMasked();
        if (act == android.view.MotionEvent.ACTION_DOWN) {
            pullStartX = e.getRawX();
            pullStartY = e.getRawY();
            int[] at = new int[2];
            scroll.getLocationOnScreen(at);
            // nur in der Liste, ganz oben und wenn nichts geladen wird
            pullArmed = !pullLoading && !syncing && scroll.getScrollY() == 0 && e.getRawY() >= at[1];
            pulling = false;
            pullBuzzed = false;
            return super.dispatchTouchEvent(e);
        }
        if (act == android.view.MotionEvent.ACTION_MOVE && pullArmed) {
            float dy = e.getRawY() - pullStartY, dx = e.getRawX() - pullStartX;
            int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
            if (!pulling) {
                if (dy > slop && dy > Math.abs(dx) * 1.3f && scroll.getScrollY() == 0) {
                    pulling = true;
                    pullStartY = e.getRawY();
                    if (pullAnim != null) pullAnim.cancel();
                    // laufendes Antippen/Scrollen der Liste abbrechen
                    android.view.MotionEvent c = android.view.MotionEvent.obtain(e);
                    c.setAction(android.view.MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(c);
                    c.recycle();
                } else {
                    if (dy < -slop || Math.abs(dx) > slop * 2) pullArmed = false;
                    return super.dispatchTouchEvent(e);
                }
            }
            float d = Math.max(0f, e.getRawY() - pullStartY);
            float max = px(150);
            float off = max * (1f - (float) Math.exp(-d / (max * 1.1f))); // Gummiband: wird immer schwerer
            setPull(off);
            boolean ready = off >= px(96);
            pullText(ready ? "Loslassen zum Aktualisieren" : "Zum Aktualisieren ziehen");
            if (ready && !pullBuzzed) {
                pullBuzzed = true;
                Haptic.play(pullBox, Haptic.CLICK);
            } else if (!ready) {
                pullBuzzed = false;
            }
            return true;
        }
        if ((act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) && pulling) {
            pulling = false;
            pullArmed = false;
            if (act == android.view.MotionEvent.ACTION_UP && pullOffset >= px(96)) {
                pullLoading = true;
                pullSpin.spin = true;
                pullSpin.invalidate();
                pullText("Aktualisieren …");
                animatePull(px(84), 280, null);
                if (!syncing) startSync(true, 0, true);
                else {
                    // es laeuft schon ein Abruf: dessen Ende abwarten
                }
            } else {
                animatePull(0, 320, () -> {
                    if (pullRebuild) {
                        pullRebuild = false;
                        build();
                        restore(0);
                    }
                });
            }
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    private boolean triedMissing;
    private int wxLoading = -1;

    private boolean feedsMissing() {
        List<Catalog.Feed> all = Catalog.load(this);
        for (String id : Catalog.effective(all, Prefs.feeds(this, ID))) {
            Catalog.Feed f = Catalog.find(all, id);
            if (f != null && FeedCache.items(this, f.id, f.name).isEmpty()) return true;
        }
        return false;
    }

    /** Neuer Ort ohne gespeichertes Wetter (z. B. direkt nach dem Ortswechsel): nur das Wetter nachladen. */
    private void ensureWeather() {
        if (syncing || !Prefs.showWeather(this, ID)) return;
        final int place = Prefs.place(this, ID);
        if (Weather.load(this, place) != null || wxLoading == place) return;
        wxLoading = place;
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            Weather.refreshIfStale(app, place);
            ui.post(() -> {
                wxLoading = -1;
                if (!isFinishing() && !isDestroyed() && !syncing && Weather.load(app, place) != null) {
                    int y = scroll == null ? 0 : scroll.getScrollY();
                    build();
                    if (y > 0) scroll.post(() -> scroll.scrollTo(0, y));
                }
            });
        }).start();
    }

    // ------------------------------------------------------------------ Aufbau

    private int px(float dp) {
        return Math.round(dp * d);
    }

    private void loadLook() {
        light = Prefs.isLight(this, ID);
        int rgb = Prefs.color(this, ID);
        if (light && rgb == Pal.WHITE) rgb = Pal.INK;
        if (!light && rgb == Pal.INK) rgb = Pal.WHITE;
        int pct = Prefs.text(this, ID);
        tx = Prefs.argb(rgb, pct, 1f);
        soft = Prefs.argb(rgb, pct, 0.65f);
        line = light ? 0x261B1F22 : 0x26FFFFFF;
        cardFill = light ? 0x0F000000 : 0x1AFFFFFF;
        int acc = Prefs.accent(this, ID);
        chipOn = acc >= 0 ? 0xFF000000 | acc : (light ? 0xFF2A2F36 : 0xFFF2F2F2);
        chipOnTx = Pal.onColor(chipOn);
        accentBar = chipOn;
        sizeIdx = Prefs.size(this, ID);
        face = Style.typeface(Prefs.font(this, ID));
    }

    private void applyWindow() {
        int[] cols = BgStyle.colors(this, ID, light);
        GradientDrawable bg;
        if (cols.length > 1) {
            int[] c = new int[cols.length];
            for (int i = 0; i < c.length; i++) c[i] = 0xFF000000 | cols[i];
            bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, c);
        } else {
            bg = new GradientDrawable();
            bg.setColor(0xFF000000 | cols[0]);
        }
        getWindow().setBackgroundDrawable(bg);
        winCols = new int[cols.length];
        for (int i = 0; i < cols.length; i++) winCols[i] = 0xFF000000 | cols[i];
        int base = 0xFF000000 | cols[0];
        getWindow().setStatusBarColor(base);
        getWindow().setNavigationBarColor(base);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        // helle Leisten-Symbole nur, wenn der Untergrund wirklich hell ist
        boolean bright = Pal.onColor(cols[0]) == (0xFF000000 | Pal.INK);
        getWindow().getDecorView().setSystemUiVisibility(bright ? (flags | lightBars) : (flags & ~lightBars));
    }

    private void build() {
        loadLook();
        applyWindow();
        // dieselben Ansichten wiederverwenden: kein neues Fenster-Inhalt, kein Aufblitzen, Scrollposition bleibt.
        // Oben (Kopf, Wetter, Werkzeuge, Reiter) steht fest, nur die Nachrichtenliste scrollt.
        int keepY = 0;
        if (scroll == null) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            top = new LinearLayout(this);
            top.setOrientation(LinearLayout.VERTICAL);
            top.setPadding(px(14), px(8), px(14), 0);
            root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            // Wetterkarte scrollt mit der Liste weg; oben bleibt synchron eine schmale Wetterzeile + Reiter stehen
            scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateOverlay(y));
            scroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> updateOverlay(scroll.getScrollY()));
            page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(px(14), 0, px(14), px(28));
            scroll.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout stack = new FrameLayout(this);
            stack.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            stack.addView(buildPull(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
            root.addView(stack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            setContentView(root);
        } else {
            keepY = scroll.getScrollY();
            top.removeAllViews();
            page.removeAllViews();
        }

        final LinearLayout list = page;
        page = top;
        wxFull = null;
        wxMini = null;
        wxIn = null;
        wxCam = null;
        wxBox = null;
        wxShield = null;
        wxFullH = 0;
        hintCard = null;
        chipsWrap = null;
        header();
        page = list;
        if (widgetCount() == 0 && !Prefs.addCardHidden(this)) addWidgetCard(true);
        if (Prefs.showWeather(this, ID)) weatherCard();
        int before = page.getChildCount();
        newsChips();
        if (page.getChildCount() > before) {
            // Reiter in einen Streifen mit Fensterhintergrund legen: er bleibt beim Scrollen oben unter der Wetterzeile stehen
            View hs = page.getChildAt(page.getChildCount() - 1);
            page.removeView(hs);
            FrameLayout wrap = new FrameLayout(this);
            wrap.setPadding(px(14), px(10), px(14), px(10));
            wrap.setBackground(windowBg());
            wrap.setOutlineProvider(null);
            wrap.setTranslationZ(px(2));
            wrap.addView(hs, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wl.leftMargin = -px(14);
            wl.rightMargin = -px(14);
            page.addView(wrap, wl);
            chipsWrap = wrap;
        }
        news();
        addWidgetCard(false);
        footer();
        restore(keepY);
    }

    // ------------------------------------------------------------------ Bausteine

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private GradientDrawable shape(int fill, float rDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(rDp));
        if (strokeColor != 0) g.setStroke(Math.max(1, px(1)), strokeColor);
        return g;
    }

    private void press(View v, int fill, float rDp, int strokeColor) {
        GradientDrawable mask = shape(0xFFFFFFFF, rDp, 0);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(light ? 0x33000000 : 0x40FFFFFF),
                shape(fill, rDp, strokeColor), mask));
        v.setClickable(true);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w > 0 ? px(w) : w, h > 0 ? px(h) : h);
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    private ImageView iconButton(int res, String desc, final Runnable click) {
        ImageView v = new ImageView(this);
        v.setContentDescription(desc);
        v.setImageResource(res);
        v.setColorFilter(tx);
        int p = px(9);
        v.setPadding(p, p, p, p);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(light ? 0x33000000 : 0x40FFFFFF),
                shape(light ? 0x14000000 : 0x24FFFFFF, 20, 0), shape(0xFFFFFFFF, 20, 0)));
        v.setOnClickListener(x -> click.run());
        return v;
    }

    private void open(Class<?> cls) {
        Intent in = new Intent(this, cls);
        in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID);
        startActivity(in);
    }

    private void header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView appName = text("BGL Wetter & News", 28, tx, false);
        try {
            appName.setTypeface(getResources().getFont(R.font.fraunces));
            appName.setFontVariationSettings("'wght' 640, 'opsz' 72, 'SOFT' 60, 'WONK' 0");
        } catch (Exception ignored) {
        }
        appName.setLetterSpacing(-0.01f);
        float nameW = appName.getPaint().measureText("BGL Wetter & News");
        appName.getPaint().setShader(new android.graphics.LinearGradient(0, 0, nameW, 0,
                new int[]{0xFFFFFFFF, 0xFFF1F3F6, 0xFF9AA3B2},
                new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        titles.addView(appName);
        titles.addView(text(statusLine(), 11.5f, soft, false));
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (syncing) {
            android.widget.ProgressBar pb = new android.widget.ProgressBar(this);
            pb.setIndeterminateTintList(ColorStateList.valueOf(tx));
            row.addView(pb, lp(24, 24, 0, 0, 8, 0));
        }
        row.addView(iconButton(R.drawable.ic_refresh, "Aktualisieren", () -> sync(true)), lp(40, 40, 0, 0, 8, 0));
        row.addView(iconButton(R.drawable.ic_tune, "Einstellungen", () -> open(ConfigActivity.class)), lp(40, 40, 0, 0, 0, 0));
        page.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 10));
    }

    private String statusLine() {
        long t = Prefs.newsTime(this);
        String stand = t == 0 ? "" : new java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(t));
        if (syncing) return "Aktualisiere …";
        if (!Prefs.newsOk(this)) return "Offline – " + (stand.isEmpty() ? "keine Daten" : "Letzte Aktualisierung " + stand);
        String sw = Prefs.srcWarn(this);
        return stand.isEmpty() ? "Noch keine Daten" : "Letzte Aktualisierung " + stand + (sw.isEmpty() ? "" : "  ·  " + sw);
    }

    private void weatherCard() {
        int place = Prefs.place(this, ID);
        Weather w = Weather.load(this, place);
        FrameLayout card = new FrameLayout(this);
        final float r = 20;
        card.setBackground(shape(cardFill, r, line));
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(r));
            }
        });
        if (w != null) {
            ImageView sky = new ImageView(this);
            sky.setScaleType(ImageView.ScaleType.CENTER_CROP);
            sky.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            android.graphics.Bitmap bmp = WeatherBg.get(this, w.code, w.day, light, place, 900, 520);
            if (lastSky != null && lastSkyPlace != place && bmp != null && lastSky != bmp) {
                // Ortswechsel: altes Motiv weich ins neue ueberblenden
                android.graphics.drawable.TransitionDrawable td = new android.graphics.drawable.TransitionDrawable(
                        new android.graphics.drawable.Drawable[]{
                                new android.graphics.drawable.BitmapDrawable(getResources(), lastSky),
                                new android.graphics.drawable.BitmapDrawable(getResources(), bmp)});
                td.setCrossFadeEnabled(true);
                sky.setImageDrawable(td);
                td.startTransition(600);
            } else {
                sky.setImageBitmap(bmp);
            }
            if (bmp != null) {
                lastSky = bmp;
                lastSkyPlace = place;
            }
            card.addView(sky, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setPadding(px(16), px(12), px(16), px(14));
        card.addView(in, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ort = text(Weather.PLACES[place] + " ▾", 15, tx, true);
        ort.setPadding(0, px(2), px(12), px(6));
        ort.setOnClickListener(v -> open(PlaceActivity.class));
        ort.setContentDescription("Ort " + Weather.PLACES[place] + ", ändern");
        in.addView(ort, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

        if (w == null) {
            in.addView(text(syncing ? "Wetter lädt …" : "Noch keine Wetterdaten", 15, soft, false));
        } else {
            LinearLayout now = new LinearLayout(this);
            now.setOrientation(LinearLayout.HORIZONTAL);
            now.setGravity(Gravity.CENTER_VERTICAL);
            now.setOnClickListener(v -> open(HourlyActivity.class));
            ImageView ic = new ImageView(this);
            ic.setImageResource(Weather.icon(w.code, w.day));
            ic.setContentDescription(Weather.text(w.code, w.day));
            now.addView(ic, lp(54, 54, 0, 0, 12, 0));
            now.addView(text(Weather.deg(w.temp), 46, tx, false));
            LinearLayout side = new LinearLayout(this);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setPadding(px(14), 0, 0, 0);
            side.addView(text(Weather.text(w.code, w.day), 15, tx, true));
            if (w.days > 0) side.addView(text("↑ " + Weather.deg(w.hi[0]) + "   ↓ " + Weather.deg(w.lo[0]), 14, soft, false));
            now.addView(side);
            in.addView(now);

            String info = w.info();
            if (!info.isEmpty()) {
                TextView t = text(info, 12.5f, soft, false);
                t.setLineSpacing(0, 1.15f);
                t.setPadding(0, px(8), 0, 0);
                in.addView(t);
            }
            int wl = Prefs.warnLevel(this);
            if (Prefs.showWarn(this, ID) && wl >= 2 && !Prefs.warnText(this).isEmpty()
                    && System.currentTimeMillis() - Prefs.warnTime(this) < 6L * 3600000) {
                TextView t = text("⚠ " + Prefs.warnText(this), 13, Warnings.color(wl), true);
                t.setPadding(0, px(8), 0, 0);
                in.addView(t);
            }
            if (w.days > 1) in.addView(forecast(w));
        }
        // Live-Webcam: waagerechte, dezente Glas-Pille oben rechts auf Hoehe des Ortsnamens
        LinearLayout cam = new LinearLayout(this);
        cam.setOrientation(LinearLayout.HORIZONTAL);
        cam.setGravity(Gravity.CENTER_VERTICAL);
        cam.setPadding(px(12), 0, px(14), 0);
        final float cr = 20;
        GradientDrawable base = shape(light ? 0x22FFFFFF : 0x14FFFFFF, cr, light ? 0x40FFFFFF : 0x24FFFFFF);
        GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{light ? 0x40FFFFFF : 0x1CFFFFFF, 0x00FFFFFF});
        gloss.setCornerRadius(px(cr));
        cam.setBackground(new RippleDrawable(ColorStateList.valueOf(light ? 0x33000000 : 0x30FFFFFF),
                new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base, gloss}),
                shape(0xFFFFFFFF, cr, 0)));
        cam.setClickable(true);
        ImageView civ = new ImageView(this);
        civ.setImageResource(R.drawable.ic_cam);
        civ.setColorFilter(soft);
        cam.addView(civ, lp(18, 18, 0, 0, 7, 0));
        TextView ct = text("Live Webcam", 13, tx, false);
        ct.setIncludeFontPadding(false);
        cam.addView(ct, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cam.setContentDescription("Live Webcams");
        cam.setOnClickListener(v -> open(WebcamActivity.class));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(44), Gravity.TOP | Gravity.END);
        clp.topMargin = px(8);
        clp.rightMargin = px(14);
        card.addView(cam, clp);
        // schmale Wetterzeile liegt in derselben Karte (gleiches Bergfoto) und blendet beim Zusammenschieben ein
        View mini = miniWeather(w, place);
        mini.setAlpha(0f);
        mini.setVisibility(View.INVISIBLE);
        card.addView(mini, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        // Halter mit fester Hoehe in der Liste; die Karte darin schrumpft synchron zum Scrollen und bleibt oben stehen
        // deckender Untergrund: Karte und Foto sind leicht durchscheinend, die Liste darf beim Zusammenschieben nicht durchschimmern
        View under = new View(this);
        under.setBackground(windowBg());
        card.addView(under, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        // volle Breite: deckender Streifen hinter der oben stehenden Zeile, damit an den runden Ecken nichts durchscheint
        box.setPadding(px(14), 0, px(14), 0);
        View shield = new View(this);
        shield.setBackground(windowBg());
        box.addView(shield, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        wxShield = shield;
        box.setTranslationZ(px(6));
        box.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.leftMargin = -px(14);
        bl.rightMargin = -px(14);
        page.addView(box, bl);
        wxBox = box;
        wxFull = card;
        wxIn = in;
        wxCam = cam;
        wxMini = mini;
    }

    /** Schmale Wetterzeile, wenn die Nachrichten nach unten gescrollt sind; Antippen klappt die Karte wieder auf. */
    private View miniWeather(Weather w, int place) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(14), px(8), px(14), px(8));
        row.setMinimumHeight(px(52));
        row.setBackground(null);
        if (w != null) {
            ImageView ic = new ImageView(this);
            ic.setImageResource(Weather.icon(w.code, w.day));
            ic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(ic, lp(28, 28, 0, 0, 10, 0));
        }
        row.addView(text(Weather.PLACES[place], 14.5f, tx, true));
        if (w != null) {
            row.addView(text("  " + Weather.deg(w.temp), 15, tx, false));
            TextView d = text("  " + Weather.text(w.code, w.day), 13, soft, false);
            d.setSingleLine(true);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(d, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        row.addView(text("▾", 14, soft, false));
        row.setContentDescription("Wetter " + Weather.PLACES[place] + (w != null ? ", " + Weather.deg(w.temp) + ", " + Weather.text(w.code, w.day) : "") + ", aufklappen");
        row.setOnClickListener(v -> {
            if (scroll != null) scroll.smoothScrollTo(0, 0);
        });
        return row;
    }

    /**
     * Synchron zum Scrollen: die Wetterkarte bleibt oben stehen und schrumpft genau um die gescrollte Strecke,
     * bis nur noch die schmale Wetterzeile uebrig ist; die Reiter bleiben direkt darunter. Rueckwaerts genauso.
     */
    private void updateOverlay(int y) {
        int pinnedBottom = 0; // Unterkante der oben stehenden Wetterzeile (Bildschirm-Koordinate in der Liste)
        if (wxBox != null && wxFull != null && wxIn != null) {
            if (wxFullH == 0) {
                if (wxFull.getHeight() == 0) return;
                wxFullH = wxFull.getHeight();
                // Halter und Inhalt auf volle Hoehe festlegen: nichts in der Liste verschiebt sich, Inhalt wird nur abgeschnitten
                wxBox.getLayoutParams().height = wxFullH;
                wxIn.getLayoutParams().height = wxFullH;
                wxBox.requestLayout();
            }
            int miniH = px(52);
            int t = Math.max(0, y - wxBox.getTop());                 // wie weit die Karte oben "angestossen" ist
            int h = Math.max(miniH, wxFullH - t);
            // den ganzen Halter verschieben (nicht nur die Karte), damit Antippen der oben stehenden Zeile ankommt
            wxBox.setTranslationY(t);
            if (wxFull.getLayoutParams().height != h) {
                wxFull.getLayoutParams().height = h;
                wxFull.requestLayout();
            }
            float range = Math.max(1, wxFullH - miniH);
            float p = Math.min(1f, t / range);
            float full = Math.max(0f, 1f - p * 1.6f);
            float mini = Math.max(0f, Math.min(1f, (p - 0.55f) / 0.45f));
            wxIn.setAlpha(full);
            wxIn.setVisibility(full > 0.02f ? View.VISIBLE : View.INVISIBLE);
            if (wxCam != null) {
                wxCam.setAlpha(full);
                wxCam.setVisibility(full > 0.02f ? View.VISIBLE : View.INVISIBLE);
            }
            if (wxMini != null) {
                wxMini.setAlpha(mini);
                wxMini.setVisibility(mini > 0.02f ? View.VISIBLE : View.INVISIBLE);
            }
            if (t > 0) pinnedBottom = h;
            if (wxShield != null) {
                int sh = t > 0 ? h : 0;
                if (wxShield.getLayoutParams().height != sh) {
                    wxShield.getLayoutParams().height = sh;
                    wxShield.requestLayout();
                }
            }
        }
        if (chipsWrap != null) {
            // Reiter: stehen bleiben, sobald sie an die Wetterzeile (bzw. den oberen Rand) stossen
            int onScreen = chipsWrap.getTop() - y;
            chipsWrap.setTranslationY(Math.max(0, pinnedBottom - onScreen));
        }
    }

    private android.graphics.drawable.Drawable windowBg() {
        if (winCols.length > 1) return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, winCols);
        GradientDrawable g = new GradientDrawable();
        g.setColor(winCols[0]);
        return g;
    }

    private View forecast(Weather w) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, px(12), 0, 0);
        for (int i = 1; i < w.days && i < 4; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(text(w.dayName[i], 12, soft, false));
            ImageView ic = new ImageView(this);
            ic.setImageResource(Weather.icon(w.dayCode[i], true));
            col.addView(ic, lp(28, 28, 0, 3, 0, 3));
            col.addView(text(Weather.deg(w.hi[i]) + " / " + Weather.deg(w.lo[i]), 12.5f, tx, false));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private void tools() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(toolPill(R.drawable.ic_search, "Suche", () -> open(SearchActivity.class)), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 6, 0));
        row.addView(toolPill(R.drawable.ic_bookmark, "Gemerkt", () -> open(SavedActivity.class)), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 6, 0, 0, 0));
        for (int i = 0; i < 2; i++) ((LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams()).weight = 1f;
        page.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));
    }

    private View toolPill(int icon, String label, final Runnable click) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER);
        p.setPadding(px(8), px(10), px(8), px(10));
        press(p, cardFill, 16, line);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(tx);
        p.addView(iv, lp(18, 18, 0, 0, 6, 0));
        p.addView(text(label, 13, tx, false));
        p.setOnClickListener(v -> click.run());
        return p;
    }

    // ------------------------------------------------------------------ Nachrichten

    /** Themen-Reiter (bleiben oben stehen). */
    private void newsChips() {
        List<Catalog.Feed> all = Catalog.load(this);
        Set<String> selected = Catalog.effective(all, Prefs.feeds(this, ID));
        List<String> topics = Taxonomy.topics(this, ID);
        String topic = Prefs.topic(this, ID);
        if (!topic.isEmpty() && !topics.contains(topic)) {
            topic = "";
            Prefs.setTopic(this, ID, "");
        }
        chips(topics, topic);
    }

    /** Nachrichtenliste (scrollt). */
    private void news() {
        if (!warmed) {
            TextView t = text("Lade Nachrichten …", 14, soft, false);
            t.setPadding(px(6), px(18), px(6), px(18));
            t.setGravity(Gravity.CENTER);
            page.addView(t);
            return;
        }
        List<FeedItem> items = ListService.loadItems(this, ID, false, null);
        Set<String> read = Prefs.readSet(this);
        List<String> kw = Keywords.list(this);
        if (items.isEmpty()) {
            String msg = syncing ? "Lade Nachrichten …"
                    : (ListService.emptyReason.isEmpty() ? "Keine Artikel gefunden" : ListService.emptyReason);
            final List<String> plusIds = new ArrayList<>();
            int hp = syncing ? 0 : ListService.hiddenPlus(this, ID, plusIds);
            if (hp > 0) {
                String tp = Prefs.topic(this, ID);
                msg = "Keine frei lesbaren Artikel" + (tp.isEmpty() ? "" : " in „" + tp.substring(tp.indexOf('/') + 1) + "“")
                        + ".\n" + hp + (hp == 1 ? " Abo-Artikel ist" : " Abo-Artikel sind") + " ausgeblendet.";
            }
            TextView t = text(msg, 14, soft, false);
            t.setPadding(px(6), px(18), px(6), hp > 0 ? px(10) : px(18));
            t.setGravity(Gravity.CENTER);
            page.addView(t);
            if (hp > 0) {
                TextView b = text("Abo-Artikel anzeigen", 14, tx, true);
                b.setGravity(Gravity.CENTER);
                b.setPadding(px(16), px(10), px(16), px(10));
                press(b, cardFill, 18, line);
                b.setOnClickListener(v -> {
                    for (String fid : plusIds) Prefs.setPlusFeed(this, ID, fid, true);
                    build();
                });
                LinearLayout.LayoutParams blp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 18);
                blp.gravity = Gravity.CENTER_HORIZONTAL;
                page.addView(b, blp);
            }
            return;
        }
        // "Alle": BGL oben, Außerhalb und Deutschland & Welt jeweils zugeklappt darunter
        boolean split = false; // "Alle": alle Zeitungen gemischt, neueste zuerst
        List<FeedItem> main = new ArrayList<>(), out = new ArrayList<>(), world = new ArrayList<>();
        for (FeedItem it : items) {
            if (split && it.topic.startsWith(Taxonomy.OUT)) out.add(it);
            else if (split && it.topic.startsWith(Taxonomy.WORLD)) world.add(it);
            else main.add(it);
        }
        if (!syncing && !Prefs.newsOk(this)) offlineBanner();
        boolean marked = false;
        for (int i = 0; i < Math.min(main.size(), MAX_ITEMS); i++) {
            FeedItem it = main.get(i);
            if (!marked && visitMark > 0 && i > 0 && it.time > 0 && it.time <= visitMark) {
                newSinceDivider();
                marked = true;
            }
            if (it.time > 0 && it.time <= visitMark) marked = true; // Linie nur, wenn oben wirklich Neues steht
            page.addView(newsCard(it, read, kw));
        }
        foldSection(Taxonomy.OUT, out, read, kw);
        foldSection(Taxonomy.WORLD, world, read, kw);
    }

    /** Dezente Linie: alles darueber ist seit dem letzten Besuch neu. */
    private void newSinceDivider() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(2), px(4), px(2), px(12));
        View l1 = new View(this);
        l1.setBackgroundColor(line);
        row.addView(l1, new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1f));
        String hh = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(visitMark));
        TextView t = text("Bis hier neu seit " + hh, 12, soft, false);
        t.setPadding(px(10), 0, px(10), 0);
        row.addView(t);
        View l2 = new View(this);
        l2.setBackgroundColor(line);
        row.addView(l2, new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1f));
        page.addView(row);
    }

    /** Ruhiger Hinweis statt leerer Flaeche, wenn der letzte Abruf ohne Verbindung war. */
    private void offlineBanner() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(px(14), px(11), px(14), px(11));
        box.setBackground(shape(light ? 0xFFFFF4DC : 0xFF2A2620, 16, light ? 0xFFE8CF95 : 0xFF4A4030));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_wifi_off);
        ic.setColorFilter(light ? 0xFF9A6B12 : 0xFFE8C37A);
        box.addView(ic, lp(20, 20, 0, 0, 12, 0));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("Keine Verbindung", 13.5f, light ? 0xFF6B4A0C : 0xFFF0DCB0, true));
        long t = Prefs.newsTime(this);
        String hh = t == 0 ? "" : "Zuletzt geladen um " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(t)) + " · ";
        col.addView(text(hh + "Ziehen zum erneut Versuchen", 12, light ? 0xFF8A6520 : 0xFFC9B48A, false));
        box.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(box, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 10));
    }

    private static final java.util.Set<String> OPEN_FOLDS = new java.util.HashSet<>();

    /** Zugeklappter Bereich unter der BGL-Liste ("Außerhalb (12 neu) ▾"). */
    private void foldSection(final String area, List<FeedItem> list, Set<String> read, List<String> kw) {
        if (list.isEmpty()) return;
        int fresh = 0;
        for (FeedItem it : list) if (it.isFresh() && !Prefs.isRead(read, it.link)) fresh++;
        final boolean open = OPEN_FOLDS.contains(area);
        TextView more = text(area + " (" + (fresh > 0 ? fresh + " neu" : String.valueOf(list.size())) + ")  " + (open ? "▴" : "▾"),
                14, soft, true);
        more.setPadding(px(6), px(14), px(6), px(14));
        more.setMinHeight(px(44));
        more.setContentDescription(area + ", " + (open ? "zuklappen" : "aufklappen"));
        more.setOnClickListener(v -> {
            if (open) OPEN_FOLDS.remove(area);
            else OPEN_FOLDS.add(area);
            build();
            // Überschrift ganz oben in der Liste zeigen, nicht halb verdeckt
            final View h = page.findViewWithTag("fold:" + area);
            if (h != null && scroll != null) scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                    scroll.scrollTo(0, Math.max(0, h.getTop() - px(4)));
                    return true;
                }
            });
        });
        more.setTag("fold:" + area);
        page.addView(more);
        if (open) for (int i = 0; i < Math.min(list.size(), 40); i++) page.addView(newsCard(list.get(i), read, kw));
    }

    private void chips(List<String> topics, final String topic) {
        List<String> parents = new ArrayList<>();
        for (String t : topics) if (t.indexOf('/') < 0 && !t.equals(Taxonomy.OUT)) parents.add(t);
        if (parents.isEmpty()) return;
        String curParent = topic.isEmpty() ? "" : topic.split("/")[0];
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int fresh = warmed ? ListService.freshUnread(this, ID) : 0;
        row.addView(chip(fresh > 0 ? "Alle (" + Math.min(fresh, 99) + ")" : "Alle", curParent.isEmpty(), () -> setTopic("")));
        for (final String p : parents) {
            boolean hasSubs = false;
            for (String t : topics) if (t.startsWith(p + "/")) hasSubs = true;
            boolean on = p.equals(curParent);
            String label = p;
            if (on && (topic.indexOf('/') >= 0 || Prefs.topicSel(this, ID).size() > 1)) label = Prefs.topicLabel(this, ID);
            if (hasSubs) label = label + " ▾";
            final boolean subs = hasSubs;
            row.addView(chip(label, on, () -> {
                if (subs) {
                    Intent in = new Intent(this, TopicActivity.class);
                    in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID);
                    in.putExtra("topic", p);
                    in.putExtra("menu", true);
                    startActivity(in);
                } else {
                    setTopic(p);
                }
            }));
        }
        View saved = chip("Gemerkt", false, () -> open(SavedActivity.class));
        ((TextView) saved).setCompoundDrawablesRelativeWithIntrinsicBounds(tinted(R.drawable.ic_bookmark, 16), null, null, null);
        ((TextView) saved).setCompoundDrawablePadding(px(6));
        saved.setContentDescription("Gemerkte Artikel");
        row.addView(saved);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        page.addView(hs, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 10));
    }

    private android.graphics.drawable.Drawable tinted(int res, int dp) {
        android.graphics.drawable.Drawable d = getResources().getDrawable(res, getTheme()).mutate();
        d.setTint(tx);
        d.setBounds(0, 0, px(dp), px(dp));
        return d;
    }

    private void setTopic(String t) {
        Prefs.setTopic(this, ID, t);
        shownTopic = Prefs.topicRaw(this, ID);
        build();
        if (scroll != null) scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private View chip(String label, boolean on, final Runnable click) {
        TextView c = text(label, 13.5f, on ? chipOnTx : tx, on);
        c.setPadding(px(14), px(8), px(14), px(8));
        c.setMinHeight(px(40));
        c.setGravity(Gravity.CENTER);
        press(c, on ? chipOn : cardFill, 18, on ? 0 : line);
        c.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK);
            click.run();
        });
        // Bedienungshilfe: Zustand und Funktion vorlesen statt des Pfeil-Zeichens
        String spoken = label.replace(" ▾", "") + (on ? ", ausgewählt" : "") + (label.endsWith("▾") ? ", öffnet Auswahl" : "");
        c.setContentDescription(spoken);
        c.setLayoutParams(lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 8, 0));
        return c;
    }

    /** Ueberschrift eines Mediums in der Liste: Balken, Name in Grossbuchstaben. */
    private View mediumHeader(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(4), px(10), px(4), px(6));
        TextView t = text(name.toUpperCase(java.util.Locale.GERMANY), 12.5f, tx, true);
        t.setLetterSpacing(0.08f);
        row.addView(t);
        View rule = new View(this);
        rule.setBackgroundColor(FeedItem.mediumColor(name));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1f);
        lp.leftMargin = px(10);
        row.addView(rule, lp);
        row.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));
        return row;
    }

    private View newsCard(final FeedItem it, Set<String> read, List<String> kw) {
        boolean isRead = Prefs.isRead(read, it.link);
        boolean hit = Keywords.matches(kw, it);
        float dim = isRead ? 0.6f : 1f;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Prefs.density(this, ID) == 1 ? 14 : 11;
        card.setPadding(px(pad), px(pad), px(pad), px(pad));
        press(card, cardFill, 16, line);

        View bar = new View(this);
        bar.setBackground(shape(it.accentColor(), 3, 0));
        bar.setAlpha(isRead ? 0.4f : 1f);
        card.addView(bar, lp(4, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0, 11, 0));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = text((hit ? "★ " : "") + it.title, Style.titleSp(sizeIdx) + 1.5f,
                hit ? 0xFFFFD166 : tx, !isRead);
        title.setAlpha(dim);
        col.addView(title);
        String meta = it.meta() + " · " + FeedItem.shortSource(it.feedName) + (it.plus ? " · Plus" : "");
        TextView m = text((!isRead && it.isFresh() ? "● " : "") + meta, Style.metaSp(sizeIdx) + 1f, soft, false);
        m.setPadding(0, px(4), 0, 0);
        m.setAlpha(dim);
        col.addView(m);
        card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String metaText = it.meta();
        card.setOnClickListener(v -> {
            Intent in = new Intent(this, PreviewActivity.class);
            in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID);
            in.putExtra(PreviewActivity.EXTRA_TITLE, it.title);
            in.putExtra(PreviewActivity.EXTRA_META, metaText);
            in.putExtra(PreviewActivity.EXTRA_TEXT, it.description);
            in.putExtra(PreviewActivity.EXTRA_LINK, it.link);
            startActivity(in);
        });
        card.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 8));
        return card;
    }

    // ------------------------------------------------------------------ Widget hinzufuegen

    private int widgetCount() {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            return m.getAppWidgetIds(new ComponentName(this, WidgetProvider.class)).length
                    + m.getAppWidgetIds(new ComponentName(this, WeatherWidgetProvider.class)).length;
        } catch (Exception e) {
            return 0;
        }
    }

    private void addWidgetCard(boolean first) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(16), px(14), px(16), px(14));
        box.setBackground(shape(cardFill, 20, line));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(first ? "Widget auf den Startbildschirm" : "Widgets", 16, tx, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (first) {
            TextView x = text("✕", 18, soft, false);
            x.setPadding(px(12), px(4), px(4), px(4));
            x.setContentDescription("Schließen");
            x.setOnClickListener(v -> {
                Prefs.setAddCardHidden(this, true);
                Toast.makeText(this, "Du findest „Widget hinzufügen“ in den Einstellungen.", Toast.LENGTH_LONG).show();
                build();
            });
            head.addView(x);
        }
        box.addView(head);
        TextView sub = text(first
                ? "Mit einem Widget siehst du Nachrichten und Wetter direkt auf dem Home, ohne die App zu öffnen."
                : "Weitere Widgets auf den Startbildschirm legen.", 13, soft, false);
        sub.setPadding(0, px(4), 0, px(10));
        box.addView(sub);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(bigPill("Nachrichten", true, () -> pin(WidgetProvider.class)), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 5, 0));
        row.addView(bigPill("Wetter", false, () -> pin(WeatherWidgetProvider.class)), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 5, 0, 0, 0));
        for (int i = 0; i < 2; i++) ((LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams()).weight = 1f;
        box.addView(row);
        page.addView(box, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, first ? 0 : 14, 0, 12));
        if (first) hintCard = box;
    }

    private View bigPill(String label, boolean primary, final Runnable click) {
        TextView p = text("+ " + label, 14, primary ? chipOnTx : tx, true);
        p.setGravity(Gravity.CENTER);
        p.setPadding(px(10), px(12), px(10), px(12));
        press(p, primary ? chipOn : cardFill, 16, primary ? 0 : line);
        p.setOnClickListener(v -> click.run());
        return p;
    }

    /** Fragt den Launcher, das Widget auf dem Startbildschirm abzulegen. */
    private void pin(Class<?> provider) {
        WidgetPin.pin(this, provider);
    }

    private void footer() {
        TextView up = text("Aktualisierung & Akku", 13, soft, false);
        up.setGravity(Gravity.CENTER);
        up.setPadding(px(10), px(12), px(10), px(12));
        press(up, 0, 14, 0);
        up.setOnClickListener(v -> open(UpdateActivity.class));
        page.addView(up, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0));
    }
}
