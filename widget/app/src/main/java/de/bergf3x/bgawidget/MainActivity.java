/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
    private View wxFull, wxMini, wxIn, wxCam, wxShield;
    /** Halter der Wetterkarte in der Liste (feste Hoehe = volle Karte) und der Reiter-Streifen. */
    private FrameLayout wxBox;
    private View chipsWrap;
    private int wxFullH;
    private int[] winCols = {0xFF101010};
    private LinearLayout page;
    private LinearLayout top;
    private LinearLayout navBar;
    /** Duenner Strich an der unteren Leiste, nur beim Wischen sichtbar. */
    private View navLine;
    private float d;
    private boolean light;
    private int tx, soft, line, cardFill, chipOn, chipOnTx, accentBar;
    private int sizeIdx;
    private Typeface face;
    private boolean syncing;
    private boolean firstResume = true;
    /** Zustand beim Verlassen: kehrt man aus einem Fenster zurueck und nichts hat sich geaendert, bleibt die Liste unangetastet. */
    private int pausedSig;
    private boolean stopped;
    private long lastSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bubble.track(this, null);
        Prefs.setFlag(this, "tab_saved", false);
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
    private boolean hasSeen;
    private boolean badgeAlert;
    private Set<String> seenSnap = new java.util.HashSet<>();
    private Set<String> newTop = new java.util.HashSet<>();
    private final android.content.BroadcastReceiver dataChanged = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent i) {
            if (syncing || isFinishing() || isDestroyed()) return;
            if (pullBusy()) {
                pullRebuild = true; // erst nach der Animation neu aufbauen, sonst ruckelt es
                return;
            }
            hasSeen = Prefs.hasSeen(MainActivity.this);
            seenSnap = Prefs.seenSet(MainActivity.this);
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
        // "Neu" = seit dem letzten Verlassen der App dazugekommen (oder nach dem Aktualisieren)
        final boolean unchanged = scroll != null && !stopped && pausedSig == Prefs.sp(this).getAll().hashCode();
        stopped = false;
        if (!unchanged) {
            hasSeen = Prefs.hasSeen(this);
            seenSnap = Prefs.seenSet(this);
            updateNav();
        }
        if (savedTab() && warmed && !unchanged) build();
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
        else if (!unchanged) {
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
        pausedSig = Prefs.sp(this).getAll().hashCode();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopped = true;
    }

    private void restore(final int y) {
        if (scroll == null) return;
        final ScrollView sv = scroll;
        // vor dem naechsten Zeichnen setzen, damit die Liste nicht kurz oben aufblitzt
        sv.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                sv.getViewTreeObserver().removeOnPreDrawListener(this);
                sv.scrollTo(0, y);
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

    // ------------------------------------------------------------------ Seitenwischen (News | Wetter | Gemerkt)

    /** Eine Seite des Pagers: eigene Liste, eigene Scrollposition, eigene Wetterkarte/Reiterzeile. */
    private static final class Pg {
        ScrollView scroll;
        LinearLayout page;
        View wxFull, wxMini, wxIn, wxCam, wxShield, chipsWrap;
        FrameLayout wxBox;
        int wxFullH;
        boolean built, dirty;
    }

    private final Pg[] pgs = new Pg[3];
    /** Traeger der drei Seiten (liegen nebeneinander, verschoben per translationX). */
    private FrameLayout pager;
    /** Aktive Seite und stufenlose Stellung waehrend des Wischens. */
    private int cur;
    private float pos;
    /** Seite, die gerade aufgebaut wird (-1 = keine): savedTab()/weatherTab() gelten dann fuer sie. */
    private int buildIdx = -1;
    private int bound = -1;
    /** Zuletzt gewaehlte Wetter-Unterseite (Bergwetter/Region). */
    private static String wxTopic = WeatherCards.BERG;
    private float pgX0, pgY0, pgDx;
    private boolean pgArmed, paging, pgBusy;
    private long pgBusyAt;
    private android.view.VelocityTracker pgVt;
    private android.animation.ValueAnimator pgAnim;
    private final Runnable prebuild = this::prebuildNext;

    private int pageIdx() {
        return cur;
    }

    /** Felder (scroll, page, Wetterkarte, Reiter) auf Seite i umstellen. */
    private void bind(int i) {
        Pg p = pgs[i];
        scroll = p.scroll;
        page = p.page;
        wxFull = p.wxFull;
        wxMini = p.wxMini;
        wxIn = p.wxIn;
        wxCam = p.wxCam;
        wxShield = p.wxShield;
        wxBox = p.wxBox;
        wxFullH = p.wxFullH;
        chipsWrap = p.chipsWrap;
        bound = i;
    }

    private void store() {
        if (bound < 0) return;
        Pg p = pgs[bound];
        p.wxFull = wxFull;
        p.wxMini = wxMini;
        p.wxIn = wxIn;
        p.wxCam = wxCam;
        p.wxShield = wxShield;
        p.wxBox = wxBox;
        p.wxFullH = wxFullH;
        p.chipsWrap = chipsWrap;
    }

    /** Etwas fuer Seite i ausfuehren und danach wieder die aktive Seite anbinden. */
    private void withPage(int i, Runnable r) {
        int keep = bound < 0 ? cur : bound;
        store();
        bind(i);
        try {
            r.run();
        } finally {
            store();
            bind(keep);
        }
    }

    /** Thema, mit dem eine (nicht aktive) Seite aufgebaut wird. */
    private String topicFor(int i) {
        if (i == 0) return Prefs.newsTopic(this, ID);
        if (i == 1) return wxTopic;
        return null;
    }

    /** Seiten nebeneinander legen: Stellung p (0..2, stufenlos). Weit entfernte Seiten werden nicht gezeichnet. */
    private void layoutPages(float p) {
        pos = p;
        if (pager == null) return;
        float w = pager.getWidth();
        for (int i = 0; i < 3; i++) {
            ScrollView sv = pgs[i].scroll;
            boolean near = Math.abs(i - p) < 0.999f;
            sv.setVisibility(near ? View.VISIBLE : View.INVISIBLE);
            sv.setTranslationX(w > 0 ? (i - p) * w : 0f);
        }
        setNavPos(Math.max(0f, Math.min(2f, p)));
    }

    /** Eine Seite (neu) aufbauen, auch wenn sie gerade nicht sichtbar ist. */
    private void buildPage(final int i) {
        final String ov = i == cur ? null : topicFor(i);
        withPage(i, () -> {
            buildIdx = i;
            Prefs.appTopicOverride = ov;
            try {
                buildContent(i);
            } finally {
                buildIdx = -1;
                Prefs.appTopicOverride = null;
            }
        });
        pgs[i].built = true;
        pgs[i].dirty = false;
    }

    /** Nachbarseiten im Hintergrund fertig aufbauen (eine pro Bild), damit das Wischen nur fertigen Inhalt bewegt. */
    private void schedulePrebuild() {
        ui.removeCallbacks(prebuild);
        ui.postDelayed(prebuild, 300);
    }

    private void prebuildNext() {
        if (isFinishing() || isDestroyed() || !warmed || pager == null) return;
        if (paging || pgBusy) {
            ui.postDelayed(prebuild, 200);
            return;
        }
        int[] order = {cur - 1, cur + 1, cur - 2, cur + 2};
        for (int j : order) {
            if (j < 0 || j > 2 || (pgs[j].built && !pgs[j].dirty)) continue;
            buildPage(j);
            layoutPages(pos);
            ui.postDelayed(prebuild, 32);
            return;
        }
    }

    /** Seite i wird aktiv: Einstellungen merken, Kopf und Leiste anpassen. */
    private void setCur(int i) {
        if (i == cur) return;
        if (cur == 1) wxTopic = Prefs.topic(this, ID);
        store();
        cur = i;
        if (i == 2) {
            Prefs.setFlag(this, "tab_saved", true);
        } else {
            Prefs.setFlag(this, "tab_saved", false);
            Prefs.setTopic(this, ID, i == 1 ? wxTopic : Prefs.newsTopic(this, ID));
        }
        shownTopic = Prefs.topicRaw(this, ID);
        bind(i);
        if (!pgs[i].built || pgs[i].dirty) buildPage(i);
        buildHeader();
        updateNav();
        layoutPages(i);
    }

    /** Nachbarseite sicher fertig, bevor sie ins Bild kommt. */
    private void ensureBuilt(int i) {
        if (i < 0 || i > 2) return;
        if (!pgs[i].built || pgs[i].dirty) buildPage(i);
    }

    /** Stellung waehrend des Ziehens; am Rand Gummiband (wird schnell schwer). */
    private void dragTo(float dx) {
        float w = Math.max(1, pager.getWidth());
        float p = cur - dx / w;
        if (p < 0) p = -rubber(-p);
        else if (p > 2) p = 2 + rubber(p - 2);
        pgDx = dx;
        layoutPages(p);
    }

    private static float rubber(float over) {
        return 0.16f * (1f - (float) Math.exp(-over / 0.28f));
    }

    /**
     * Weich zur Zielseite gleiten. Nach dem Loslassen uebernimmt die Bewegung das Tempo des Fingers und
     * bremst sanft ab; bei "Animationen reduzieren" wird direkt umgeschaltet.
     */
    private void glideTo(final int target, float velocity, boolean fromRest) {
        if (pgAnim != null) pgAnim.cancel();
        final float from = pos;
        if (Motion.reduced(this) || pager.getWidth() <= 0) {
            layoutPages(target);
            setCur(target);
            pgBusy = false;
            hideNavLine();
            return;
        }
        float w = pager.getWidth();
        float distPx = Math.abs(target - from) * w;
        long dur;
        if (fromRest) {
            dur = Math.round(300 + 60 * Math.min(2f, Math.abs(target - from)));
        } else {
            float v = Math.abs(velocity);
            // Tempo des Fingers fortsetzen, aber nie hektisch und nie zaeh
            dur = v > px(250) ? Math.round(distPx / v * 1000f * 2.0f) : 340;
            dur = Math.max(220, Math.min(420, dur));
        }
        pgBusy = true;
        pgBusyAt = System.currentTimeMillis();
        pgAnim = android.animation.ValueAnimator.ofFloat(from, target);
        pgAnim.setDuration(dur);
        pgAnim.setInterpolator(fromRest ? new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
                : new android.view.animation.PathInterpolator(0.25f, 0.6f, 0.3f, 1f));
        pgAnim.addUpdateListener(a -> layoutPages((float) a.getAnimatedValue()));
        pgAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                pgBusy = false;
                if (cancelled) return;
                layoutPages(target);
                setCur(target);
                hideNavLine();
                schedulePrebuild();
            }
        });
        pgAnim.start();
    }

    /** Tipp auf einen Reiter der unteren Leiste: weich zur Seite gleiten (auch ueber eine Seite hinweg). */
    private void animateTo(final int target) {
        if (pgBusy || pager == null || target == cur) return;
        for (int j = Math.min(cur, target); j <= Math.max(cur, target); j++) ensureBuilt(j);
        showNavLine();
        glideTo(target, 0f, true);
    }

    /** Wischen nur unterhalb der Reiterzeile und in der Liste (nicht im Kopf, nicht auf den Reitern). */
    private boolean canPage(android.view.MotionEvent e) {
        if (pager == null || pgBusy || pulling || pullLoading || !Motion.swipe(this)) return false;
        int[] at = new int[2];
        pager.getLocationOnScreen(at);
        float x = e.getRawX(), y = e.getRawY();
        if (y < at[1] || y > at[1] + pager.getHeight()) return false;
        if (chipsWrap != null && chipsWrap.isShown()) {
            int[] c = new int[2];
            chipsWrap.getLocationOnScreen(c);
            if (y >= c[1] && y <= c[1] + chipsWrap.getHeight() && x >= c[0] && x <= c[0] + chipsWrap.getWidth()) return false;
        }
        return true;
    }

    /** Gibt true zurueck, wenn das Ereignis vom Seitenwischen verbraucht wurde. Kein Vibrieren beim Wischen. */
    private boolean pageTouch(android.view.MotionEvent e) {
        int act = e.getActionMasked();
        if (act == android.view.MotionEvent.ACTION_DOWN) {
            pgArmed = canPage(e);
            paging = false;
            pgX0 = e.getRawX();
            pgY0 = e.getRawY();
            if (pgVt != null) pgVt.recycle();
            pgVt = android.view.VelocityTracker.obtain();
            pgVt.addMovement(e);
            return false;
        }
        if (pgVt != null) pgVt.addMovement(e);
        if (act == android.view.MotionEvent.ACTION_MOVE) {
            float dx = e.getRawX() - pgX0, dy = e.getRawY() - pgY0;
            if (!paging) {
                if (!pgArmed || pulling) return false;
                int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                    pgArmed = false; // senkrechtes Scrollen hat Vorrang
                    return false;
                }
                if (Math.abs(dx) <= slop || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                // Gemerkt (letzte Seite): nach links wischen entfernt die Karte wie bisher
                if (cur == 2 && dx < 0) {
                    pgArmed = false;
                    return false;
                }
                paging = true;
                pullArmed = false;
                pgX0 += Math.signum(dx) * slop; // ohne Sprung beim Einsetzen
                ensureBuilt(cur - 1);
                ensureBuilt(cur + 1);
                showNavLine();
                android.view.MotionEvent c = android.view.MotionEvent.obtain(e);
                c.setAction(android.view.MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(c);
                c.recycle();
            }
            dragTo(e.getRawX() - pgX0);
            return true;
        }
        if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) {
            pgArmed = false;
            if (!paging) return false;
            paging = false;
            float vx = 0;
            if (pgVt != null) {
                pgVt.computeCurrentVelocity(1000);
                vx = pgVt.getXVelocity();
            }
            boolean fling = Math.abs(vx) > px(500) && Math.signum(vx) == Math.signum(pgDx);
            boolean commit = act == android.view.MotionEvent.ACTION_UP && (Math.abs(pgDx) > px(70) || fling);
            int target = cur;
            if (commit) target = Math.max(0, Math.min(2, pgDx < 0 ? cur + 1 : cur - 1));
            glideTo(target, vx, false);
            return true;
        }
        return paging;
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (scroll == null || pullBox == null || pager == null) return super.dispatchTouchEvent(e);
        // Sicherung: nie laenger als 1,5 s gesperrt
        if (pgBusy && System.currentTimeMillis() - pgBusyAt > 1500) pgBusy = false;
        if (pgBusy && e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) return true; // waehrend des Einrastens nichts antippen
        if (pageTouch(e)) return true;
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
        // dieselben Ansichten wiederverwenden: kein neuer Fenster-Inhalt, kein Aufblitzen, Scrollposition bleibt.
        // Oben steht der Kopf fest; darunter liegen drei Seiten (News, Wetter, Gemerkt) nebeneinander.
        if (pager == null) createShell();
        buildHeader();
        int wc = widgetCount();
        int seen = Prefs.intVal(this, "widget_seen", -1);
        if (seen >= 0 && wc > seen) {
            // neues Widget: Freigaben (Akku, Benachrichtigungen, Autostart) noch einmal anbieten
            Prefs.setFlag(this, "perm_card_hidden", false);
            for (String k : new String[]{"notify", "battery", "autostart"}) Prefs.setFlag(this, "perm_later_" + k, false);
        }
        if (seen != wc) Prefs.setInt(this, "widget_seen", wc);
        buildPage(cur);
        for (int j = 0; j < 3; j++) if (j != cur && pgs[j].built) pgs[j].dirty = true;
        updateNav();
        if (!paging && !pgBusy) layoutPages(cur);
        schedulePrebuild();
    }

    /** Grundgeruest einmalig: Kopf, drei Seiten nebeneinander, Ziehen-Anzeige, untere Leiste. */
    private void createShell() {
        // Start immer auf News; die zuletzt gewaehlte Wetter-Unterseite bleibt fuer den Reiter Wetter erhalten
        cur = 0;
        String startTopic = Prefs.topic(this, ID);
        if (startTopic.startsWith(Taxonomy.WETTER)) {
            wxTopic = startTopic;
            Prefs.setTopic(this, ID, Prefs.newsTopic(this, ID));
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(px(14), px(8), px(14), 0);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout stack = new FrameLayout(this);
        pager = new FrameLayout(this);
        pager.setClipChildren(true);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            final Pg p = new Pg();
            p.scroll = new ScrollView(this);
            p.scroll.setVerticalScrollBarEnabled(false);
            p.scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            // Wetterkarte scrollt mit der Liste weg; oben bleibt synchron eine schmale Wetterzeile + Reiter stehen
            p.scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> withPage(idx, () -> updateOverlay(y)));
            p.scroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> withPage(idx, () -> updateOverlay(p.scroll.getScrollY())));
            p.page = new LinearLayout(this);
            p.page.setOrientation(LinearLayout.VERTICAL);
            p.page.setPadding(px(14), 0, px(14), px(28));
            p.scroll.addView(p.page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            pgs[i] = p;
            pager.addView(p.scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        pager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) layoutPages(pos);
        });
        stack.addView(pager, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stack.addView(buildPull(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        root.addView(stack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackground(navBg());
        navBar.setElevation(px(6));
        navBar.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, 0, 0, ins.getSystemWindowInsetBottom());
            return ins;
        });
        // duenner Strich oben an der Leiste: erscheint nur beim Wischen und laeuft stufenlos mit
        FrameLayout navWrap = new FrameLayout(this);
        navWrap.addView(navBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        navLine = new View(this);
        navLine.setAlpha(0f);
        navLine.setElevation(px(7));
        navWrap.addView(navLine, new FrameLayout.LayoutParams(px(36), Math.max(2, px(2)), Gravity.TOP | Gravity.START));
        root.addView(navWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        bind(cur);
    }

    /** Kopfzeile (App-Name, Abo, Filter, Aktualisieren, Einstellungen) fuer die aktive Seite. */
    private void buildHeader() {
        LinearLayout keep = page;
        page = top;
        top.removeAllViews();
        header();
        page = keep;
    }

    /** Inhalt einer Seite; Felder sind bereits auf diese Seite umgestellt (withPage). */
    private void buildContent(int i) {
        int keepY = scroll.getScrollY();
        page.removeAllViews();
        wxFull = null;
        wxMini = null;
        wxIn = null;
        wxCam = null;
        wxBox = null;
        wxShield = null;
        wxFullH = 0;
        chipsWrap = null;
        int wc = widgetCount();
        boolean newsTab = i == 0;
        if (newsTab && wc == 0 && !Prefs.addCardHidden(this)) addWidgetCard();
        if (newsTab && (wc > 0 || Prefs.addCardHidden(this))) permCard();
        if (i != 2 && Prefs.showWeather(this, ID)) weatherCard();
        int before = page.getChildCount();
        if (i != 2) newsChips();
        if (page.getChildCount() > before) {
            // Reiter in einen Streifen mit Fensterhintergrund legen: er bleibt beim Scrollen oben unter der Wetterzeile stehen
            View hs = page.getChildAt(page.getChildCount() - 1);
            page.removeView(hs);
            FrameLayout wrap = new FrameLayout(this);
            wrap.setPadding(px(14), px(10), px(14), px(6));
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
        restore(keepY);
    }

    /** Gemerkt war beim letzten Mal offen (wird beim Start zurueckgesetzt, daher meist false). */
    private boolean savedTab() {
        return (buildIdx >= 0 ? buildIdx : cur) == 2;
    }

    private boolean weatherTab() {
        return (buildIdx >= 0 ? buildIdx : cur) == 1;
    }

    /** Flache untere Leiste: News | Wetter | Gemerkt. Aktiv = heller und minimal groesser, keine Pille. */
    private void updateNav() {
        if (navBar == null) return;
        navBar.setBackground(navBg());
        navBar.removeAllViews();
        final int cur = pageIdx();
        int n = Prefs.saved(this).length();
        badgeAlert = Prefs.savedUnread(this) > 0;
        String[] names = {"News", "Wetter", "Gemerkt"};
        int[] icons = {R.drawable.ic_news, R.drawable.ic_cloud, R.drawable.ic_bookmark};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            String badge = i == 2 && n > 0 ? (n > 99 ? "99+" : String.valueOf(n)) : "";
            navBar.addView(navItem(i, icons[i], names[i], i == cur, badge, () -> {
                if (k != pageIdx()) animateTo(k);
                else if (scroll != null) scroll.smoothScrollTo(0, 0);
            }), new LinearLayout.LayoutParams(0, px(64), 1f));
        }
        if (navLine != null) navLine.setBackground(shape(tx, 1, 0));
        setNavPos(cur);
    }

    /** Untere Leiste: Fensterfarbe der App, oben eine feine helle Kante. */
    private android.graphics.drawable.Drawable navBg() {
        android.graphics.drawable.LayerDrawable l = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{windowBg(), new android.graphics.drawable.ColorDrawable(light ? 0x1F000000 : 0x2EFFFFFF)});
        l.setLayerGravity(1, Gravity.TOP);
        l.setLayerHeight(1, Math.max(1, px(1)));
        return l;
    }

    private final ImageView[] navIcon = new ImageView[3];
    private final TextView[] navLab = new TextView[3];

    private View navItem(int idx, int icon, String label, boolean on, String badge, final Runnable click) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        android.widget.FrameLayout ifl = new android.widget.FrameLayout(this);
        ifl.setClipChildren(false);
        ifl.addView(iv, new android.widget.FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER));
        if (!badge.isEmpty()) {
            TextView b = text(badge, 11, 0xFFFFFFFF, true);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(px(19));
            b.setMinHeight(px(19));
            b.setPadding(px(5), 0, px(5), 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(badgeAlert ? 0xFFE53935 : 0xFF6B7077);
            bg.setCornerRadius(px(10));
            bg.setStroke(Math.max(2, px(2)), baseColor());
            b.setBackground(bg);
            android.widget.FrameLayout.LayoutParams bp = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            bp.rightMargin = px(-8);
            bp.topMargin = px(-8);
            ifl.addView(b, bp);
        }
        it.setClipChildren(false);
        it.addView(ifl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView lab = text(label, 12, soft, false);
        it.addView(lab, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 3, 0, 0));
        navIcon[idx] = iv;
        navLab[idx] = lab;
        it.setContentDescription(label + (on ? ", ausgewählt" : "") + (badge.isEmpty() ? "" : ", " + badge));
        it.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK);
            click.run();
        });
        return it;
    }

    /** Farbe zwischen zwei ARGB-Werten (t = 0..1). */
    private static int blendArgb(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /**
     * Stellung der unteren Leiste (0 = News, 1 = Wetter, 2 = Gemerkt, stufenlos dazwischen):
     * Symbol und Text werden nach Abstand heller, das Symbol bis 1,1fach groesser; der Strich laeuft mit.
     */
    private void setNavPos(float pos) {
        for (int i = 0; i < 3; i++) {
            if (navIcon[i] == null) continue;
            float t = Math.max(0f, Math.min(1f, 1f - Math.abs(pos - i)));
            int c = blendArgb(NAV_OFF, tx, t);
            navIcon[i].setColorFilter(c);
            navIcon[i].setScaleX(1f + 0.1f * t);
            navIcon[i].setScaleY(1f + 0.1f * t);
            navLab[i].setTextColor(c);
        }
        if (navLine != null && navBar != null && navBar.getWidth() > 0) {
            float cell = navBar.getWidth() / 3f;
            navLine.setTranslationX(cell * (pos + 0.5f) - navLine.getWidth() / 2f);
        }
    }

    /** Inaktive Reiter der unteren Leiste: grau. */
    private static final int NAV_OFF = 0xFF9AA2AD;

    private void showNavLine() {
        if (navLine == null) return;
        navLine.animate().cancel();
        navLine.animate().alpha(1f).setStartDelay(0).setDuration(160).start();
    }

    private void hideNavLine() {
        if (navLine == null) return;
        navLine.animate().cancel();
        navLine.animate().alpha(0f).setStartDelay(220).setDuration(380)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
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

    private int baseColor() {
        return winCols != null && winCols.length > 0 ? winCols[0] : (light ? 0xFFEFF1F5 : 0xFF1B1D24);
    }

    private void press(View v, int fill, float rDp, int strokeColor) {
        if (fill != 0 && rDp >= 14) {
            // Tiefe: helle Oberkante, dunklere Unterkante, gedrueckt sinkt die Flaeche ein
            v.setBackground(Depth.raised(Depth.blend(fill, baseColor()), px(rDp), d, light));
            v.setClickable(true);
            return;
        }
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
        int p = px(8);
        v.setPadding(p, p, p, p);
        // Symbol ohne Hintergrund, nur Druck-Effekt
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(light ? 0x33000000 : 0x40FFFFFF),
                null, shape(0xFFFFFFFF, 20, 0)));
        v.setOnClickListener(x -> click.run());
        return v;
    }

    private void open(Class<?> cls) {
        Intent in = new Intent(this, cls);
        in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID);
        startActivity(in);
    }

    private void header() {
        // Zeile 1: Name allein, volle Breite (immer einzeilig). Zeile 2: Stand links, vier Symbole rechts.
        TextView appName = text("BGL Wetter & News", 28, tx, false);
        try {
            appName.setTypeface(getResources().getFont(R.font.fraunces));
            appName.setFontVariationSettings("'wght' 640, 'opsz' 72, 'SOFT' 60, 'WONK' 0");
        } catch (Exception ignored) {
        }
        appName.setLetterSpacing(-0.01f);
        float nameW = appName.getPaint().measureText("BGL Wetter & News");
        appName.getPaint().setShader(new android.graphics.LinearGradient(0, 0, nameW, 0,
                light ? new int[]{0xFF14171B, 0xFF2A2F36, 0xFF6B7380} : new int[]{0xFFFFFFFF, 0xFFF1F3F6, 0xFF9AA3B2},
                new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        appName.setMaxLines(1);
        appName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appName.setAutoSizeTextTypeUniformWithConfiguration(20, 28, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        appName.setPadding(px(2), 0, 0, 0);
        page.addView(appName, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView stand = text(statusLine(), 11.5f, soft, false);
        stand.setSingleLine(true);
        stand.setEllipsize(android.text.TextUtils.TruncateAt.END);
        stand.setPadding(px(2), 0, px(6), 0);
        row.addView(stand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Filter und Abo gibt es nur bei News; auf den anderen Seiten bleibt der Platz leer, damit nichts springt
        final boolean onNews = !savedTab() && !weatherTab();
        boolean filt = onNews && warmed && ListService.filtered(this, ID);
        ImageView fb = iconButton(R.drawable.ic_filter, filt ? "Quellen filtern, Filter aktiv" : "Quellen filtern", this::openSources);
        FrameLayout ff = new FrameLayout(this);
        ff.addView(fb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (filt) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFFE53935);
            g.setStroke(Math.max(2, px(2)), baseColor());
            dot.setBackground(g);
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(px(10), px(10), Gravity.TOP | Gravity.END);
            dl.topMargin = px(3);
            dl.rightMargin = px(3);
            ff.addView(dot, dl);
        }
        ff.setVisibility(onNews ? View.VISIBLE : View.INVISIBLE);
        row.addView(ff, lp(36, 36, 0, 0, 2, 0));

        // Abo-Schloss: offen = Abo-Artikel sichtbar, geschlossen = ausgeblendet
        final int ps = Prefs.plusState(this, ID);
        ImageView lock = iconButton(ps == 0 ? R.drawable.ic_lock : R.drawable.ic_lock_open,
                ps == 0 ? "Abo-Artikel ausgeblendet, antippen zum Anzeigen" : "Abo-Artikel sichtbar, antippen zum Ausblenden", () -> {
                    Prefs.setPlusAll(this, ID, ps != 1);
                    Haptic.play(scroll, Haptic.TICK);
                    android.widget.Toast.makeText(this, ps != 1 ? "Abo-Artikel werden angezeigt" : "Abo-Artikel sind ausgeblendet",
                            android.widget.Toast.LENGTH_SHORT).show();
                    build();
                });
        if (ps == 0) lock.setAlpha(0.6f);
        lock.setVisibility(onNews ? View.VISIBLE : View.INVISIBLE);
        row.addView(lock, lp(36, 36, 0, 0, 2, 0));

        if (syncing) {
            // Ladekreis an der Stelle des Aktualisieren-Knopfs
            FrameLayout sp = new FrameLayout(this);
            android.widget.ProgressBar pb = new android.widget.ProgressBar(this);
            pb.setIndeterminateTintList(ColorStateList.valueOf(tx));
            sp.addView(pb, new FrameLayout.LayoutParams(px(22), px(22), Gravity.CENTER));
            row.addView(sp, lp(36, 36, 0, 0, 2, 0));
        } else {
            row.addView(iconButton(R.drawable.ic_refresh, "Aktualisieren", () -> sync(true)), lp(36, 36, 0, 0, 2, 0));
        }
        row.addView(iconButton(R.drawable.ic_tune, "Einstellungen", () -> open(ConfigActivity.class)), lp(36, 36, 0, 0, 0, 0));
        page.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 6));
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
                TextView t = text("⚠ " + Prefs.warnText(this), 13, light ? (wl >= 3 ? 0xFFC62828 : 0xFFB35C00) : Warnings.color(wl), true);
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
    // ------------------------------------------------------------------ Nachrichten

    /** Themen-Reiter (bleiben oben stehen). */
    private void newsChips() {
        List<Catalog.Feed> all = Catalog.load(this);
        Set<String> selected = Catalog.effective(all, Prefs.feeds(this, ID));
        List<String> topics = Taxonomy.topics(this, ID);
        String topic = Prefs.topic(this, ID);
        // Gespeicherte Auswahl bleibt erhalten, auch wenn das Thema gerade keine Artikel hat (z. B. Abo aus, Neuabruf)
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
        if (savedTab()) {
            List<FeedItem> sv = ListService.savedItems(this);
            Set<String> rd = Prefs.savedReadSet(this);
            List<String> kw0 = Keywords.list(this);
            if (!sv.isEmpty()) {
                // schmale Zeile: Aufbewahrung einstellen (Auswahl in der Blase, mit Uebernehmen/Abbrechen)
                int keep = Prefs.savedKeepDays(this);
                TextView h = text("Aufbewahrung: " + (keep == 0 ? "dauerhaft" : keep + " Tage") + "  ›", 12.5f, soft, false);
                h.setPadding(px(6), px(10), px(6), px(10));
                h.setMinHeight(px(40));
                h.setGravity(Gravity.CENTER_VERTICAL);
                h.setContentDescription("Aufbewahrung gemerkter Artikel: " + (keep == 0 ? "dauerhaft" : keep + " Tage") + ", antippen zum Ändern");
                h.setOnClickListener(v -> startActivity(new Intent(this, SavedActivity.class)));
                page.addView(h);
            }
            if (sv.isEmpty()) {
                TextView t = text("Noch nichts gemerkt. In der Artikel-Vorschau gibt es den Knopf „Merken“.", 14, soft, false);
                t.setPadding(px(6), px(18), px(6), px(18));
                t.setGravity(Gravity.CENTER);
                page.addView(t);
            }
            for (FeedItem f : sv) page.addView(swipeCard(f, newsCard(f, rd, kw0)));
            return;
        }
        warnRow();
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
        String lastGroup = "";
        for (int i = 0; i < Math.min(main.size(), MAX_ITEMS); i++) {
            FeedItem it = main.get(i);
            if (!marked && visitMark > 0 && i > 0 && it.time > 0 && it.time <= visitMark) {
                newSinceDivider();
                marked = true;
            }
            if (it.time > 0 && it.time <= visitMark) marked = true; // Linie nur, wenn oben wirklich Neues steht
            if (it.weather() && !it.category.isEmpty() && !it.category.equals(lastGroup)) {
                lastGroup = it.category;
                TextView gh = text(it.category, Style.metaSp(sizeIdx) + 1f, soft, true);
                gh.setPadding(px(4), px(10), 0, px(2));
                page.addView(gh);
            }
            page.addView(newsCard(it, read, kw));
        }
        foldSection(Taxonomy.OUT, out, read, kw);
        foldSection(Taxonomy.WORLD, world, read, kw);
    }

    /** Gemerkte Karte: nach links wischen entfernt sie aus Gemerkt; nach rechts wechselt die Seite zurueck zu Wetter. */
    private View swipeCard(final FeedItem f, final View card) {
        final FrameLayout fl = new FrameLayout(this);
        final TextView del = text("Entfernen", 13, 0xFFFFFFFF, true);
        del.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        del.setPadding(0, 0, px(18), 0);
        del.setBackground(shape(0xFFC0392B, 16, 0));
        del.setVisibility(View.INVISIBLE);
        ViewGroup.LayoutParams outer = card.getLayoutParams();
        card.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fl.addView(del, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fl.addView(card);
        fl.setLayoutParams(outer);
        final int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            boolean sw;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        sw = false;
                        return false;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!sw && dx < -slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            sw = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            v.setPressed(false);
                        }
                        if (!sw) return false;
                        v.setTranslationX(Math.min(0f, dx));
                        del.setVisibility(dx < 0 ? View.VISIBLE : View.INVISIBLE);
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        if (!sw) return false;
                        sw = false;
                        float dx = v.getTranslationX();
                        float w = v.getWidth();
                        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && dx < -w * 0.35f) {
                            v.animate().translationX(-w).setDuration(140).withEndAction(() -> {
                                Prefs.toggleSaved(MainActivity.this, f.title, "", "", f.link);
                                build();
                            }).start();
                        } else {
                            v.animate().translationX(0).setDuration(140).withEndAction(() -> {
                                del.setVisibility(View.INVISIBLE);
                            }).start();
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
        return fl;
    }

    /** Warnzeile ueber der Liste: nur bei aktiver DWD-Warnung ab Stufe 3 in einer der gewaehlten Regionen. */
    private void warnRow() {
        String[] best = null;
        int lv = 0, n = 0;
        java.util.Set<String> on = Prefs.warnRegions(this);
        for (String[] r : WeatherCards.WARN_REGIONS) {
            if (!on.contains(r[0])) continue;
            int l = Prefs.warnRegionLevel(this, r[0]);
            if (l < 3) continue;
            n++;
            if (l > lv) {
                lv = l;
                best = r;
            }
        }
        if (best == null) return;
        String wt = Prefs.warnRegion(this, best[0]);
        if (wt.isEmpty() || wt.equals("-")) return;
        final String ort = best[2];
        int fill = lv >= 4 ? (lv >= 5 ? (light ? 0xFFFFD6D6 : 0xFF5C1E1E) : (light ? 0xFFFFE2C2 : 0xFF5A3510)) : (light ? 0xFFFFF0C2 : 0xFF5A4210);
        int fg = light ? 0xFF3A2A05 : 0xFFF6E8C0;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(12), px(10), px(12), px(12));
        press(row, fill, 16, 0);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_warn_tri);
        ic.setColorFilter(lv >= 5 ? 0xFFE04848 : 0xFFF5A623);
        row.addView(ic, lp(22, 22, 0, 0, 10, 0));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(wt, 14, fg, true));
        TextView sub = text(best[1] + (n > 1 ? " · +" + (n - 1) + (n == 2 ? " weitere Region" : " weitere Regionen") : "")
                + " · DWD · Stufe " + lv + " · Tippen für Details", 11.5f, fg, false);
        sub.setAlpha(0.85f);
        col.addView(sub);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setContentDescription("Wetterwarnung: " + wt + ", " + best[1]);
        row.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK);
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(WeatherCards.DWD_WARN_ORT
                        + java.net.URLEncoder.encode(ort, "UTF-8"))));
            } catch (Exception ignored) {
            }
        });
        page.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 10));
    }

    /** Dezente Linie: alles darueber ist seit dem letzten Besuch neu. */
    private void newSinceDivider() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(2), px(4), px(2), px(12));
        View l1 = new View(this);
        l1.setBackgroundColor((soft & 0xFFFFFF) | 0x66000000);
        row.addView(l1, new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1f));
        String hh = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(visitMark));
        TextView t = text("Neu seit " + hh, 12, soft, true);
        t.setPadding(px(10), 0, px(10), 0);
        row.addView(t);
        View l2 = new View(this);
        l2.setBackgroundColor((soft & 0xFFFFFF) | 0x66000000);
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
        if (weatherTab()) {
            // exakt dieselben Reiter wie bei den Nachrichten (gleiche Kapsel, Hoehe, Schrift, Abstand)
            LinearLayout wbox = new LinearLayout(this);
            wbox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout wrow = new LinearLayout(this);
            wrow.setOrientation(LinearLayout.HORIZONTAL);
            wrow.addView(tab("Bergwetter", !topic.equals(WeatherCards.REGION), false, () -> setTopic(WeatherCards.BERG)));
            wrow.addView(tab("Region", topic.equals(WeatherCards.REGION), false, () -> setTopic(WeatherCards.REGION)));
            tabStrip(wbox, wrow);
            page.addView(wbox, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 4));
            return;
        }
        // Zeitungen stehen nicht als Reiter da, sondern in der Quellen-Auswahl ("m Quellen ▾")
        List<String> parents = new ArrayList<>();
        for (String t : topics) if (t.indexOf('/') < 0 && !t.equals(Taxonomy.OUT) && !t.equals(Taxonomy.WETTER) && !t.equals(Taxonomy.PAPERS)) parents.add(t);
        String curParent = topic.isEmpty() ? "" : topic.split("/")[0];
        newTop = warmed ? ListService.newTopics(this, ID) : new java.util.HashSet<String>();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        // 1. ruhige Text-Reiter in voller Breite, aktiver unterstrichen, am Rand weich auslaufend
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(tab("Alle", curParent.isEmpty(), !newTop.isEmpty(), () -> setTopic("")));
        for (final String p : parents) {
            boolean hasSubs = false;
            for (String t : topics) if (t.startsWith(p + "/")) hasSubs = true;
            boolean on = p.equals(curParent);
            String label = p;
            if (on && (topic.indexOf('/') >= 0 || Prefs.topicSel(this, ID).size() > 1)) label = Prefs.topicLabel(this, ID);
            if (hasSubs) label = label + " ▾";
            final boolean subs = hasSubs;
            row.addView(tab(label, on, newTop.contains(p), () -> {
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
        tabStrip(box, row);

        // 2. Info-Zeile nur bei Filter oder Ungelesenem: "Gefiltert: x von y Quellen · n ungelesen", rechts "Alle als gelesen markieren"
        final int unread = warmed && hasSeen ? ListService.newItems(this, ID).size() : 0;
        String infoText = warmed ? ListService.infoLine(this, ID, unread) : "";
        if (!infoText.isEmpty()) {
            boolean filt = ListService.filtered(this, ID);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.HORIZONTAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            TextView src = text(infoText, 12.5f, filt ? tx : soft, false);
            src.setSingleLine(true);
            src.setEllipsize(android.text.TextUtils.TruncateAt.END);
            src.setPadding(px(4), px(9), px(8), px(9));
            src.setMinHeight(px(40));
            src.setGravity(Gravity.CENTER_VERTICAL);
            if (filt) {
                // Tipp auf "Gefiltert" oeffnet ebenfalls die Quellen-Auswahl
                press(src, 0, 10, 0);
                src.setContentDescription(infoText + ", Quellen ändern");
                src.setOnClickListener(v -> openSources());
            }
            info.addView(src, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (unread > 0) {
                TextView mark = text("Alle als gelesen markieren", 12.5f, tx, false);
                mark.setPadding(px(8), px(9), px(4), px(9));
                mark.setMinHeight(px(40));
                mark.setGravity(Gravity.CENTER_VERTICAL);
                press(mark, 0, 10, 0);
                mark.setContentDescription("Alle " + unread + " ungelesenen Nachrichten als gelesen markieren");
                mark.setOnClickListener(v -> {
                    Haptic.play(v, Haptic.CONFIRM);
                    ListService.markAllRead(this);
                });
                info.addView(mark);
            }
            box.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        page.addView(box, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 4));
    }

    /** Reiterzeile der Themen (scrollbar, weich auslaufend). */
    private void tabStrip(LinearLayout box, LinearLayout row) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setHorizontalFadingEdgeEnabled(true);
        hs.setFadingEdgeLength(px(32));
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.addView(row);
        box.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Quellen-Auswahl (Zeitungen mit Regionen) als Blatt von unten. */
    private void openSources() {
        Intent in = new Intent(this, TopicActivity.class);
        in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID);
        in.putExtra("topic", Taxonomy.PAPERS);
        in.putExtra("menu", true);
        in.putExtra("sheet", true);
        startActivity(in);
    }

    /** Themen-Reiter: aktiv als weiche Kapsel, sonst nur gedaempfter Text; roter Punkt nur bei Ungelesenem. */
    private View tab(String label, boolean on, boolean dot, final Runnable click) {
        LinearLayout lr = new LinearLayout(this);
        lr.setOrientation(LinearLayout.HORIZONTAL);
        lr.setGravity(Gravity.CENTER);
        TextView tv = text(label, 13.5f, on ? tx : soft, on);
        lr.addView(tv);
        if (dot) {
            View d0 = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFFE53935);
            d0.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(6), px(6));
            dl.leftMargin = px(5);
            dl.bottomMargin = px(7);
            lr.addView(d0, dl);
        }
        lr.setPadding(px(14), 0, px(14), 0);
        lr.setMinimumHeight(px(36));
        lr.setClickable(true);
        lr.setBackground(new RippleDrawable(ColorStateList.valueOf(light ? 0x22000000 : 0x33FFFFFF),
                on ? shape(light ? 0x1A000000 : 0x26FFFFFF, 18, 0) : null, shape(0xFFFFFFFF, 18, 0)));
        String spoken = label.replace(" ▾", "") + (on ? ", ausgewählt" : "") + (dot ? ", neue Meldungen" : "") + (label.endsWith("▾") ? ", öffnet Auswahl" : "");
        lr.setContentDescription(spoken);
        lr.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK);
            click.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(36));
        lp.rightMargin = px(6);
        lp.topMargin = px(2);
        lp.bottomMargin = px(2);
        lr.setLayoutParams(lp);
        return lr;
    }

    private android.graphics.drawable.Drawable tinted(int res, int dp) {
        android.graphics.drawable.Drawable d = getResources().getDrawable(res, getTheme()).mutate();
        d.setTint(tx);
        d.setBounds(0, 0, px(dp), px(dp));
        return d;
    }

    private void setTopic(String t) {
        if (t.startsWith(Taxonomy.WETTER)) wxTopic = t;
        Prefs.setTopic(this, ID, t);
        shownTopic = Prefs.topicRaw(this, ID);
        build();
        if (scroll != null) { final ScrollView sv0 = scroll; sv0.post(() -> sv0.scrollTo(0, 0)); }
    }

    private View newsCard(final FeedItem it, Set<String> read, List<String> kw) {
        boolean isRead = !it.weather() && Prefs.isRead(read, it.link);
        boolean hit = !it.weather() && Keywords.matches(kw, it);
        float dim = isRead ? 0.6f : 1f;
        final boolean isNew = !it.weather() && hasSeen && !it.link.isEmpty() && !seenSnap.contains(it.link) && !isRead;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Prefs.density(this, ID) == 1 ? 14 : 11;
        card.setPadding(px(pad), px(pad), px(pad), px(pad));
        press(card, cardFill, 16, line);

        View bar = new View(this);
        bar.setBackground(shape(isNew ? 0xFFE53935 : it.accentColor(), 3, 0));
        bar.setAlpha(isRead ? 0.4f : 1f);
        card.addView(bar, lp(4, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0, 11, 0));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = text((hit ? "★ " : "") + it.title, Style.titleSp(sizeIdx) + 1.5f,
                hit ? 0xFFFFD166 : tx, !isRead);
        title.setAlpha(dim);
        col.addView(title);
        String meta = it.weather() ? it.meta() : it.meta() + " · " + FeedItem.shortSource(it.feedName) + (it.plus ? " · \uD83D\uDD12 Abo" : "");
        TextView m = text(meta, Style.metaSp(sizeIdx) + 1f, soft, false);
        if (isNew) {
            android.text.SpannableString sp = new android.text.SpannableString("NEU · " + meta);
            sp.setSpan(new android.text.style.ForegroundColorSpan(0xFFE53935), 0, 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            m.setText(sp);
        }
        m.setPadding(0, px(4), 0, 0);
        m.setAlpha(dim);
        col.addView(m);
        card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!it.image.isEmpty() && ImageStore.mode(this) != ImageStore.OFF) {
            ImageView th = new ImageView(this);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.setBackground(shape(light ? 0x14000000 : 0x1FFFFFFF, 10, 0));
            ImageStore.round(th, px(10));
            th.setAlpha(isRead ? 0.6f : 1f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(px(78), px(58));
            tlp.leftMargin = px(10);
            tlp.gravity = Gravity.CENTER_VERTICAL;
            card.addView(th, tlp);
            ImageStore.into(th, it.image, 200, true);
        }

        final String metaText = it.meta();
        card.setOnClickListener(v -> {
            if (it.link.startsWith(WeatherCards.OPEN)) {
                Intent bi = new Intent(this, BergActivity.class);
                bi.putExtra(BergActivity.EXTRA_REGION, it.link.substring(WeatherCards.OPEN.length()));
                bi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID);
                startActivity(bi);
                return;
            }
            Intent in = new Intent(this, PreviewActivity.class);
            in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID);
            in.putExtra(PreviewActivity.EXTRA_TITLE, it.title);
            in.putExtra(PreviewActivity.EXTRA_META, metaText);
            in.putExtra(PreviewActivity.EXTRA_TEXT, it.description);
            in.putExtra(PreviewActivity.EXTRA_LINK, it.link);
            in.putExtra(PreviewActivity.EXTRA_IMAGE, it.image);
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

    /** Auswahl in der Widget-Karte: 0 = Nachrichten, 1 = Wetter, 2 = Beides; bleibt beim Neuaufbau erhalten. */
    private int wSel;
    private boolean wOpen;
    private static final String[] W_NAME = {"Nachrichten", "Wetter", "Beides"};
    private static final String[] W_DESC = {"Nur die Nachrichten", "Wetter mit Webcams", "Wetter und Nachrichten"};
    private static final int[] W_ICON = {R.drawable.ic_news, R.drawable.ic_cloud, R.drawable.ic_layers};

    /** Karte ganz oben, solange noch kein Widget existiert: Art waehlen und auf den Startbildschirm legen. */
    private void addWidgetCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(16), px(14), px(16), px(14));
        box.setBackground(shape(cardFill, 20, line));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Widget auf den Startbildschirm", 16, tx, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Runnable hide = () -> {
            Prefs.setAddCardHidden(this, true);
            Toast.makeText(this, "Du findest „Widget hinzufügen“ in den Einstellungen.", Toast.LENGTH_LONG).show();
            build();
        };
        TextView x = text("✕", 18, soft, false);
        x.setPadding(px(12), px(4), px(4), px(4));
        x.setContentDescription("Schließen");
        x.setOnClickListener(v -> hide.run());
        head.addView(x);
        box.addView(head);
        TextView sub = text("Welche Art von Widget?", 13, soft, false);
        sub.setPadding(0, px(4), 0, px(10));
        box.addView(sub);

        final LinearLayout dd = new LinearLayout(this);
        dd.setOrientation(LinearLayout.VERTICAL);
        dd.setBackground(shape(light ? 0x0D000000 : 0x14FFFFFF, 14, line));
        dd.setClipToOutline(true);
        android.animation.LayoutTransition lt = new android.animation.LayoutTransition();
        lt.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        lt.setDuration(180);
        dd.setLayoutTransition(lt);
        fillDropdown(dd);
        box.addView(dd);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(bigPill("Abbrechen", false, hide), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 5, 0));
        row.addView(bigPill("Hinzufügen", true, () -> {
            if (wSel == 1) WidgetPin.pin(this, WeatherWidgetProvider.class, -1);
            else WidgetPin.pin(this, WidgetProvider.class, wSel == 2 ? 1 : 0);
        }), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 5, 0, 0, 0));
        for (int i = 0; i < 2; i++) ((LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams()).weight = 1f;
        box.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));
        page.addView(box, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));
    }

    /** Zugeklappt: nur die gewaehlte Art mit Pfeil. Aufgeklappt: alle drei, die gewaehlte mit Haken. */
    private void fillDropdown(final LinearLayout dd) {
        dd.removeAllViews();
        for (int i = 0; i < 3; i++) {
            if (!wOpen && i != wSel) continue;
            final int k = i;
            LinearLayout o = new LinearLayout(this);
            o.setOrientation(LinearLayout.HORIZONTAL);
            o.setGravity(Gravity.CENTER_VERTICAL);
            o.setPadding(px(14), px(11), px(14), px(11));
            press(o, 0, 0, 0);
            ImageView ic = new ImageView(this);
            ic.setImageResource(W_ICON[i]);
            ic.setColorFilter(tx);
            o.addView(ic, lp(20, 20, 0, 0, 12, 0));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(W_NAME[i], 15, tx, true));
            col.addView(text(W_DESC[i], 12, soft, false));
            o.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            int mark = !wOpen ? R.drawable.ic_chevron_down : (i == wSel ? R.drawable.ic_check : 0);
            if (mark != 0) {
                ImageView m = new ImageView(this);
                m.setImageResource(mark);
                m.setColorFilter(wOpen ? tx : soft);
                o.addView(m, lp(22, 22, 8, 0, 0, 0));
            }
            o.setOnClickListener(v -> {
                Haptic.play(v, Haptic.TICK);
                if (wOpen) wSel = k;
                wOpen = !wOpen;
                fillDropdown(dd);
            });
            if (dd.getChildCount() > 0) {
                View sep = new View(this);
                sep.setBackgroundColor(line);
                dd.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(0.5f))));
            }
            dd.addView(o, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View bigPill(String label, boolean primary, final Runnable click) {
        TextView p = text(label, 14, primary ? chipOnTx : tx, true);
        p.setGravity(Gravity.CENTER);
        p.setPadding(px(10), px(12), px(10), px(12));
        press(p, primary ? chipOn : cardFill, 16, primary ? 0 : line);
        p.setOnClickListener(v -> {
            Haptic.play(v, Haptic.CLICK);
            click.run();
        });
        return p;
    }

    /** Hinweis-Karte: was das Handy noch freigeben muss, mit Sprung direkt in die passende Systemseite. */
    private void permCard() {
        if (Prefs.flag(this, "perm_card_hidden") || Perms.open(this) == 0) return;
        final int warnC = light ? 0xFF9A6B12 : 0xFFE8C37A;
        final int okC = light ? 0xFF2E7D4F : 0xFF8FD19E;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(16), px(14), px(16), px(8));
        box.setBackground(shape(cardFill, 20, line));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView sh = new ImageView(this);
        sh.setImageResource(R.drawable.ic_shield);
        sh.setColorFilter(warnC);
        head.addView(sh, lp(20, 20, 0, 0, 8, 0));
        head.addView(text("Damit alles zuverlässig läuft", 16, tx, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = text("✕", 18, soft, false);
        x.setPadding(px(12), px(4), px(4), px(4));
        x.setContentDescription("Schließen");
        x.setOnClickListener(v -> {
            Prefs.setFlag(this, "perm_card_hidden", true);
            Toast.makeText(this, "Ohne diese Freigaben läuft die App unter Umständen nicht korrekt. Du findest sie unter Einstellungen → Aktualisierung.", Toast.LENGTH_LONG).show();
            build();
        });
        head.addView(x);
        box.addView(head);
        int n = Perms.open(this);
        TextView sub = text("Noch " + n + (n == 1 ? " Einstellung" : " Einstellungen") + " offen. Ohne sie kann die App unter Umständen nicht korrekt funktionieren.",
                12.5f, soft, false);
        sub.setPadding(0, px(4), 0, px(4));
        box.addView(sub);

        permRow(box, "notify", R.drawable.ic_bell, "Benachrichtigungen", "Für Unwetter und Stichwörter",
                "Ohne Benachrichtigungen kommen keine Unwetter- und Stichwort-Meldungen.",
                Perms.notifyOk(this), "Erlauben", () -> Perms.askNotify(this), null, okC);
        permRow(box, "battery", R.drawable.ic_battery, "Akku-Optimierung aus", "Damit das Widget aktuell bleibt",
                "Ohne diese Ausnahme wird das Widget im Hintergrund unter Umständen nicht aktualisiert.",
                Perms.batteryOk(this), "Erlauben", () -> Perms.askBattery(this), null, okC);
        if (Perms.needsAutostart()) {
            boolean opened = Prefs.flag(this, "perm_autostart_opened");
            permRow(box, "autostart", R.drawable.ic_autostart, Perms.autostartTitle(), Perms.autostartHint(),
                    "Ohne Autostart funktioniert das Widget nach einem Neustart unter Umständen nicht korrekt.",
                    Perms.autostartDone(this), opened ? "Erledigt" : "Erlauben",
                    opened ? () -> {
                        Perms.setAutostartDone(this, true);
                        build();
                    } : () -> {
                        Prefs.setFlag(this, "perm_autostart_opened", true);
                        Perms.openAutostart(this);
                    },
                    opened ? () -> Perms.openAutostart(this) : null, okC);
        }
        page.addView(box, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));
    }

    private void permRow(LinearLayout box, final String key, int icon, String title, String hint, String warn, boolean ok,
                         String btn, final Runnable act, final Runnable reopen, int okC) {
        final boolean later = !ok && Prefs.flag(this, "perm_later_" + key);
        final int warnC = light ? 0xFF9A6B12 : 0xFFE8C37A;
        if (box.getChildCount() > 2) {
            View sep = new View(this);
            sep.setBackgroundColor(line);
            box.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(0.5f))));
        }
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, px(10), 0, px(10));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(tx);
        r.addView(ic, lp(20, 20, 0, 0, 12, 0));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 14.5f, tx, true));
        col.addView(later ? text(warn, 12, warnC, false) : text(hint, 12, soft, false));
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (ok) {
            LinearLayout done = new LinearLayout(this);
            done.setGravity(Gravity.CENTER_VERTICAL);
            ImageView ck = new ImageView(this);
            ck.setImageResource(R.drawable.ic_check);
            ck.setColorFilter(okC);
            done.addView(ck, lp(16, 16, 0, 0, 4, 0));
            done.addView(text("Erledigt", 12.5f, okC, true));
            r.addView(done);
        } else {
            if (!later) {
                TextView lt = text("Später", 12.5f, soft, true);
                lt.setPadding(px(10), px(7), px(10), px(7));
                press(lt, 0, 14, line);
                lt.setOnClickListener(v -> {
                    Haptic.play(v, Haptic.TICK);
                    Prefs.setFlag(this, "perm_later_" + key, true);
                    build();
                });
                r.addView(lt, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 0, 0));
            }
            if (reopen != null) {
                TextView o = text("Öffnen", 12.5f, soft, true);
                o.setPadding(px(10), px(7), px(10), px(7));
                press(o, 0, 14, line);
                o.setOnClickListener(v -> {
                    Haptic.play(v, Haptic.CLICK);
                    reopen.run();
                });
                r.addView(o, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 6, 0));
            }
            TextView b = text(btn, 12.5f, chipOnTx, true);
            b.setPadding(px(12), px(7), px(12), px(7));
            press(b, chipOn, 14, 0);
            b.setOnClickListener(v -> {
                Haptic.play(v, Haptic.CLICK);
                act.run();
            });
            r.addView(b, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, reopen == null ? 8 : 0, 0, 0, 0));
        }
        box.addView(r);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == Perms.REQ_NOTIFY) build();
    }
}
