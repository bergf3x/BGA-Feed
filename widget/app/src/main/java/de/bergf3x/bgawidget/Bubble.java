/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Glas-Blase im Stil des Widgets: wird fuer Auswahlmenues und die Artikel-Vorschau benutzt. */
final class Bubble {
    /** Widget, zu dem das Fenster gehoert (fuer Design und Farben). */
    static int widgetId(Activity a) {
        int id = a.getIntent() == null ? 0 : a.getIntent().getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id != 0) return id;
        try {
            android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(a);
            int[] ids = m.getAppWidgetIds(new android.content.ComponentName(a, WidgetProvider.class));
            if (ids.length > 0) return ids[0];
            ids = m.getAppWidgetIds(new android.content.ComponentName(a, WeatherWidgetProvider.class));
            if (ids.length > 0) return ids[0];
        } catch (Exception ignored) {
        }
        return 0;
    }

    final boolean light;
    final int tx, soft, title, hi, hiTx, ripple, line;
    final Activity act;
    final LinearLayout box;
    final float d;
    private boolean closing;
    /** Abstand der Blase vom oberen Rand (Anteil der Hoehe). */
    private float topFrac = 0.18f;

    /** Blatt von unten (Quellen-Auswahl): volle Breite, unten angesetzt, faehrt hoch statt aus der Karte zu wachsen. */
    private boolean sheet;

    void sheet() {
        sheet = true;
        origin = null;
        android.view.ViewGroup.LayoutParams p = box.getLayoutParams();
        if (p instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) p;
            int screenW = act.getResources().getDisplayMetrics().widthPixels;
            f.width = Math.min(screenW - px(16), px(520));
            f.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            f.topMargin = Math.round(act.getResources().getDisplayMetrics().heightPixels * 0.12f);
            f.bottomMargin = px(8);
            box.setLayoutParams(f);
        }
        // unten Platz fuer die Gesten-/Navigationsleiste lassen
        box.setOnApplyWindowInsetsListener((v, ins) -> {
            android.view.ViewGroup.LayoutParams q = v.getLayoutParams();
            if (q instanceof FrameLayout.LayoutParams) {
                int want = px(8) + ins.getSystemWindowInsetBottom();
                if (((FrameLayout.LayoutParams) q).bottomMargin != want) {
                    ((FrameLayout.LayoutParams) q).bottomMargin = want;
                    v.setLayoutParams(q);
                }
            }
            return ins;
        });
    }

    /** Blase weiter oben beginnen lassen (fuer lange Menues wie die Einstellungen). */
    void high() {
        topFrac = 0.08f;
        android.view.ViewGroup.LayoutParams p = box.getLayoutParams();
        if (p instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) p).topMargin = Math.round(act.getResources().getDisplayMetrics().heightPixels * topFrac);
            box.setLayoutParams(p);
        }
    }

    Bubble(Activity act, int widthDp, int padDp) {
        this.act = act;
        d = act.getResources().getDisplayMetrics().density;
        int wid = widgetId(act);
        light = wid != 0 && Prefs.isLight(act, wid);
        // Farben wie im Widget: Schriftfarbe und -deckkraft, Auswahl wie die aktive Chip-Markierung
        int rgb = wid != 0 ? Prefs.color(act, wid) : (light ? 0x1B1F22 : 0xFFFFFF);
        int txPct = wid != 0 ? Prefs.text(act, wid) : 90;
        tx = Prefs.argb(rgb, txPct, 1f);
        title = tx;
        soft = Prefs.argb(rgb, txPct, 0.65f);
        hi = light ? 0xFF2A2F36 : 0xFFF2F2F2;
        hiTx = light ? 0xFFFFFFFF : 0xFF1B1F22;
        ripple = light ? 0x33000000 : 0x40FFFFFF;
        line = light ? 0x331B1F22 : 0x2EFFFFFF;
        int bgPct = Math.max(wid != 0 ? Prefs.bg(act, wid) : 80, 94);
        act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dimMax = light ? 0.40f : 0.60f;
        act.getWindow().setDimAmount(0f);
        dim(0f, dimMax, OPEN_MS);

        FrameLayout root = new FrameLayout(act);
        root.setOnClickListener(v -> close());

        box = new FitBox(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(padDp);
        box.setPadding(pad, pad, pad, pad);
        int[] fillColors = wid != 0 ? BgStyle.colors(act, wid, light) : new int[]{light ? BgStyle.LIGHT_BASE : BgStyle.DARK_BASE};
        float rDp = wid != 0 ? BgStyle.radiusDp(act, wid) : 28f;
        box.setBackground(new Glass(light, bgPct, d, fillColors, rDp));
        box.setClickable(true);

        int screenW = act.getResources().getDisplayMetrics().widthPixels;
        int screenH = act.getResources().getDisplayMetrics().heightPixels;
        int w = Math.min(px(widthDp), screenW - px(40));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Math.round(screenH * topFrac);
        root.addView(box, lp);
        act.setContentView(root);

        track(act, backHandler());
        takeOrigin();
        registerBackCallback();
        box.setAlpha(0f);
        box.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                box.getViewTreeObserver().removeOnPreDrawListener(this);
                playOpen();
                return true;
            }
        });
        // Inhaltswechsel (Reiter, Untermenues) spaeter weich einblenden
        box.post(() -> soft(box));
    }

    /** Einheitliche Zeiten fuer alle Fenster (ms). */
    static final int OPEN_MS = 220, CLOSE_MS = 180, SWAP_MS = 140;
    /** Wie stark der Hintergrund schrumpft, solange ein Fenster offen ist. */
    private static final float DEPTH = 0.95f;
    private static final android.view.animation.Interpolator ZOOM_IN = new android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    private static final android.view.animation.Interpolator ZOOM_OUT = new android.view.animation.PathInterpolator(0.4f, 0f, 0.6f, 1f);
    private float dimMax;
    /** Angetippte Karte (Bildschirmkoordinaten): daraus waechst das Fenster und dorthin schrumpft es zurueck. */
    private android.graphics.Rect origin;
    /** Inhalt des Fensters, aus dem geoeffnet wurde: schrumpft leicht, solange dieses Fenster offen ist. */
    private View depth;
    private int baseX, baseY;

    // ---- letzter Tipp (fuer den Zoom aus der angetippten Karte)
    private static android.graphics.Rect tapRect;
    private static java.lang.ref.WeakReference<View> tapContent;
    private static long tapTime;

    /**
     * Merkt sich bei jedem Antippen die angetippte Karte und das Fenster darunter; optional faengt es die Zurueck-Taste ab.
     * Wird von der App und von jedem Fenster einmal aufgerufen.
     */
    static void track(final Activity a, final Runnable back) {
        final android.view.Window w = a.getWindow();
        final android.view.Window.Callback orig = w.getCallback();
        if (orig == null) return;
        w.setCallback((android.view.Window.Callback) java.lang.reflect.Proxy.newProxyInstance(
                android.view.Window.Callback.class.getClassLoader(), new Class<?>[]{android.view.Window.Callback.class},
                (proxy, m, args) -> {
                    String n = m.getName();
                    if (n.equals("dispatchTouchEvent") && args[0] instanceof android.view.MotionEvent
                            && ((android.view.MotionEvent) args[0]).getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                        android.view.MotionEvent e = (android.view.MotionEvent) args[0];
                        noteTap(a, Math.round(e.getRawX()), Math.round(e.getRawY()));
                    } else if (n.equals("onWindowFocusChanged") && (Boolean) args[0]) {
                        View c = a.findViewById(android.R.id.content);
                        if (c != null && c.getScaleX() != 1f) {
                            c.animate().cancel();
                            c.setScaleX(1f);
                            c.setScaleY(1f);
                        }
                    } else if (back != null && n.equals("dispatchKeyEvent") && args[0] instanceof android.view.KeyEvent
                            && ((android.view.KeyEvent) args[0]).getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                        android.view.KeyEvent k = (android.view.KeyEvent) args[0];
                        if (k.getAction() == android.view.KeyEvent.ACTION_UP && !k.isCanceled()) back.run();
                        return true;
                    }
                    try {
                        return m.invoke(orig, args);
                    } catch (java.lang.reflect.InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                }));
    }

    private static void noteTap(Activity a, int x, int y) {
        tapRect = null;
        View hit = hitClickable(a.getWindow().getDecorView(), x, y);
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        if (hit != null && hit.getWidth() > 0 && hit.getHeight() > 0
                && hit.getWidth() < dm.widthPixels * 0.98f && hit.getHeight() < dm.heightPixels * 0.5f) {
            int[] l = new int[2];
            hit.getLocationOnScreen(l);
            tapRect = new android.graphics.Rect(l[0], l[1], l[0] + hit.getWidth(), l[1] + hit.getHeight());
        }
        tapContent = new java.lang.ref.WeakReference<>(a.findViewById(android.R.id.content));
        tapTime = android.os.SystemClock.uptimeMillis();
    }

    /** Tiefstes anklickbares Element an dieser Stelle. */
    private static View hitClickable(View v, int x, int y) {
        if (v.getVisibility() != View.VISIBLE) return null;
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        if (x < l[0] || y < l[1] || x >= l[0] + v.getWidth() || y >= l[1] + v.getHeight()) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = hitClickable(g.getChildAt(i), x, y);
                if (r != null) return r;
            }
        }
        return v.isClickable() ? v : null;
    }

    /** Uebernimmt den letzten Tipp, wenn das Fenster direkt danach geoeffnet wurde. */
    private void takeOrigin() {
        if (android.os.SystemClock.uptimeMillis() - tapTime < 1500) {
            origin = tapRect;
            View c = tapContent == null ? null : tapContent.get();
            if (c != null && c.getContext() != act) depth = c;
        }
        tapRect = null;
        tapContent = null;
        tapTime = 0;
    }

    /** Zurueck-Taste soll das Fenster mit Animation schliessen, ausser die Activity regelt Zurueck selbst. */
    private Runnable backHandler() {
        try {
            if (act.getClass().getMethod("onBackPressed").getDeclaringClass() != Activity.class) return null;
        } catch (NoSuchMethodException e) {
            return null;
        }
        return this::close;
    }

    private void registerBackCallback() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && backHandler() != null) {
            act.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::close);
        }
    }

    private void playOpen() {
        int[] l = new int[2];
        box.getLocationOnScreen(l);
        baseX = l[0];
        baseY = l[1];
        if (Motion.reduced(act)) {
            // Animationen reduziert: nur kurz einblenden, kein Zoomen/Schieben, Hintergrund bleibt stehen
            android.animation.ObjectAnimator.ofFloat(box, View.ALPHA, 0f, 1f).setDuration(120).start();
            return;
        }
        if (sheet) {
            box.setTranslationY(box.getHeight() + px(24));
            box.animate().translationY(0f).setDuration(OPEN_MS + 40).setInterpolator(ZOOM_IN).start();
            android.animation.ObjectAnimator.ofFloat(box, View.ALPHA, 0f, 1f).setDuration(140).start();
            if (depth != null) depth.animate().scaleX(DEPTH).scaleY(DEPTH).setDuration(OPEN_MS).setInterpolator(ZOOM_IN).start();
            return;
        }
        if (origin != null && box.getWidth() > 0 && box.getHeight() > 0) {
            box.setPivotX(0f);
            box.setPivotY(0f);
            box.setTranslationX(origin.left - baseX);
            box.setTranslationY(origin.top - baseY);
            box.setScaleX(origin.width() / (float) box.getWidth());
            box.setScaleY(origin.height() / (float) box.getHeight());
        } else {
            box.setPivotX(box.getWidth() / 2f);
            box.setPivotY(0f);
            box.setScaleX(0.88f);
            box.setScaleY(0.88f);
        }
        box.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(OPEN_MS).setInterpolator(ZOOM_IN).start();
        android.animation.ObjectAnimator.ofFloat(box, View.ALPHA, 0f, 1f).setDuration(140).start();
        if (depth != null) depth.animate().scaleX(DEPTH).scaleY(DEPTH).setDuration(OPEN_MS).setInterpolator(ZOOM_IN).start();
    }

    private void dim(float from, float to, int ms) {
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(from, to);
        va.setDuration(ms);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(a -> act.getWindow().setDimAmount((float) a.getAnimatedValue()));
        va.start();
    }

    /**
     * Neu aufgebauter Inhalt blendet weich ein (kein Aufblitzen): nur "neue Kinder" werden animiert,
     * Entfernen geschieht sofort. Mehrfacher Aufruf ist unschaedlich.
     */
    static void soft(ViewGroup g) {
        if (g == null || g.getLayoutTransition() != null) return;
        android.animation.LayoutTransition t = new android.animation.LayoutTransition();
        t.setAnimator(android.animation.LayoutTransition.APPEARING,
                android.animation.ObjectAnimator.ofFloat(null, "alpha", 0f, 1f));
        t.setDuration(android.animation.LayoutTransition.APPEARING, SWAP_MS);
        t.setStartDelay(android.animation.LayoutTransition.APPEARING, 0);
        t.setInterpolator(android.animation.LayoutTransition.APPEARING, new DecelerateInterpolator());
        t.disableTransitionType(android.animation.LayoutTransition.DISAPPEARING);
        t.disableTransitionType(android.animation.LayoutTransition.CHANGE_APPEARING);
        t.disableTransitionType(android.animation.LayoutTransition.CHANGE_DISAPPEARING);
        t.disableTransitionType(android.animation.LayoutTransition.CHANGING);
        g.setLayoutTransition(t);
    }

    /** ScrollView, das bei Platzmangel schrumpft (siehe FitBox): so bleiben die Knoepfe darunter immer sichtbar. */
    static final class Flex extends ScrollView {
        int max;
        int cap = Integer.MAX_VALUE;
        final int minPx;

        Flex(Activity a, int maxPx, int minPx) {
            super(a);
            max = maxPx;
            this.minPx = minPx;
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(Math.max(minPx, Math.min(max, cap)), View.MeasureSpec.AT_MOST));
        }
    }

    /**
     * Inhalt der Blase: passt sich der verfuegbaren Hoehe an. Reicht sie nicht, schrumpfen zuerst die scrollbaren
     * Bereiche, dann markierte Bilder (flex); Titel und Knoepfe unten bleiben immer sichtbar.
     */
    private static final class FitBox extends LinearLayout {
        private final java.util.HashMap<View, Integer> orig = new java.util.HashMap<>();

        FitBox(Activity a) {
            super(a);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int mode = View.MeasureSpec.getMode(h);
            int avail = View.MeasureSpec.getSize(h) - Math.round(20 * getResources().getDisplayMetrics().density);
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                View c = getChildAt(i);
                if (c instanceof Flex) ((Flex) c).cap = Integer.MAX_VALUE;
                Integer o = orig.get(c);
                if (o != null) c.getLayoutParams().height = o;
            }
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int over = getMeasuredHeight() - avail;
            if (mode == View.MeasureSpec.UNSPECIFIED || over <= 0) return;
            for (int i = 0; i < n && over > 0; i++) {
                View c = getChildAt(i);
                if (!(c instanceof Flex)) continue;
                Flex f = (Flex) c;
                int cut = Math.min(over, Math.max(0, c.getMeasuredHeight() - f.minPx));
                if (cut > 0) {
                    f.cap = c.getMeasuredHeight() - cut;
                    over -= cut;
                }
            }
            for (int i = 0; i < n && over > 0; i++) {
                View c = getChildAt(i);
                Object t = c.getTag(FLEX_TAG);
                if (!(t instanceof Integer)) continue;
                int cur = c.getMeasuredHeight();
                int cut = Math.min(over, Math.max(0, cur - (Integer) t));
                if (cut > 0) {
                    orig.put(c, c.getLayoutParams().height);
                    c.getLayoutParams().height = cur - cut;
                    over -= cut;
                }
            }
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        }
    }

    private static final int FLEX_TAG = 0x7f0b0fff;

    /** Ansicht darf bei Platzmangel bis minDp schrumpfen (z. B. ein Vorschaubild mit fester Hoehe). */
    void flex(View v, int minDp) {
        v.setTag(FLEX_TAG, px(minDp));
    }

    /** Glas-Flaeche wie der Widget-Hintergrund: Fuellung, Lichtreflex links oben, feiner heller Rand. */
    private static final class Glass extends android.graphics.drawable.Drawable {
        private final boolean light;
        private final int bgPct;
        private final float r;
        private final float stroke;
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint gloss = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint edge = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();

        private final int[] colors;

        Glass(boolean light, int bgPct, float density, int[] colors, float rDp) {
            this.light = light;
            this.bgPct = bgPct;
            this.colors = colors;
            r = rDp * density;
            stroke = Math.max(1.2f, 1.2f * density);
            fill.setColor(0xFF000000 | colors[0]);
            fill.setAlpha(Prefs.alpha(bgPct));
            edge.setStyle(android.graphics.Paint.Style.STROKE);
            edge.setStrokeWidth(stroke);
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect b) {
            float w = b.width(), h = b.height();
            if (colors.length > 1) {
                int[] cs = new int[colors.length];
                for (int i = 0; i < cs.length; i++) cs[i] = 0xFF000000 | colors[i];
                fill.setShader(new android.graphics.LinearGradient(b.left, 0, b.right, 0, cs, null,
                        android.graphics.Shader.TileMode.CLAMP));
            }
            rect.set(b.left + stroke / 2, b.top + stroke / 2, b.right - stroke / 2, b.bottom - stroke / 2);
            int top = light ? 0x70FFFFFF : 0x2EFFFFFF;
            gloss.setShader(new android.graphics.LinearGradient(b.left, b.top, b.left + w * .75f, b.top + h * .45f,
                    top, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
            int a = Math.round(Math.max(bgPct, 25) * 255f / 100f);
            int hi = ((int) (a * (light ? .9f : .7f)) << 24) | 0xFFFFFF;
            int lo = ((int) (a * (light ? .35f : .22f)) << 24) | (light ? 0x8890A0 : 0xFFFFFF);
            edge.setShader(new android.graphics.LinearGradient(b.left, b.top, b.right, b.bottom,
                    new int[]{hi, lo, lo}, new float[]{0f, .5f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            c.drawRoundRect(rect, r, r, fill);
            c.drawRoundRect(rect, r, r, gloss);
            c.drawRoundRect(rect, r, r, edge);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    int px(float dp) {
        return Math.round(dp * d);
    }

    /** Zurueck in die Karte zoomen (bzw. sanft ausblenden), danach die Activity beenden. */
    void close() {
        if (closing) return;
        closing = true;
        dim(dimMax, 0f, CLOSE_MS);
        android.view.ViewPropertyAnimator a = box.animate().setDuration(CLOSE_MS).setInterpolator(ZOOM_OUT);
        if (Motion.reduced(act)) {
            a.setDuration(120);
        } else if (sheet) {
            a.translationY(box.getHeight() + px(24));
        } else if (origin != null && box.getWidth() > 0 && box.getHeight() > 0) {
            a.translationX(origin.left - baseX).translationY(origin.top - baseY)
                    .scaleX(origin.width() / (float) box.getWidth()).scaleY(origin.height() / (float) box.getHeight());
        } else {
            a.scaleX(0.96f).scaleY(0.96f);
        }
        a.alpha(0f).withEndAction(() -> {
            act.finish();
            act.overridePendingTransition(0, 0);
        }).start();
        if (depth != null && depth.getScaleX() != 1f) depth.animate().scaleX(1f).scaleY(1f).setDuration(CLOSE_MS).setInterpolator(ZOOM_OUT).start();
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        box.addView(t);
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    /** Fuellung eines normalen (nicht hervorgehobenen) erhabenen Knopfs. */
    int neutral() {
        return light ? 0x1A000000 : 0x26FFFFFF;
    }

    private void pressable(View v, int fillColor, int rippleColor) {
        if (fillColor != 0 && (fillColor >>> 24) != 0) {
            int base = light ? 0xFFEDEFF3 : 0xFF2A2D34;
            v.setBackground(Depth.raised(Depth.blend(fillColor, base), px(14), d, light));
            v.setClickable(true);
            return;
        }
        GradientDrawable mask = rounded(0xFFFFFFFF, 14);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor),
                fillColor == 0 ? null : rounded(fillColor, 14), mask));
    }

    /** Auswahlzeile; die gewaehlte ist hell hinterlegt und hat ein Haekchen. */
    void row(String label, boolean selected, Runnable onClick) {
        row(box, label, selected, onClick, true);
    }

    /** Wie row, aber in einem beliebigen Container; closeAfter = Blase danach schliessen. */
    void row(ViewGroup parent, String label, boolean selected, Runnable onClick, boolean closeAfter) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(12), px(10), px(12), px(10));
        r.setMinimumHeight(px(44));
        r.setContentDescription(label.trim() + (selected ? ", ausgewählt" : ""));
        pressable(r, selected ? hi : 0, selected ? 0x33000000 : ripple);
        TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(selected ? hiTx : tx);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            TextView c = new TextView(act);
            c.setText("✓");
            c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            c.setTextColor(hiTx);
            r.addView(c);
        }
        r.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK); // vor dem Neuaufbau, sonst ist die Zeile schon weg
            onClick.run();
            if (closeAfter) close();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(1);
        parent.addView(r, lp);
    }

    /** Kachel fuer ein Untermenue: Titel, kurze Zusammenfassung, Pfeil. */
    void tile(String title, String sub, Runnable onClick) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(16), px(10), px(14), px(10));
        pressable(r, light ? 0x14000000 : 0x1AFFFFFF, ripple);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        t.setTextColor(tx);
        t.setTypeface(null, Typeface.BOLD);
        col.addView(t);
        TextView s = new TextView(act);
        s.setText(sub);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        s.setTextColor(soft);
        s.setMaxLines(1);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(s);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView c = new TextView(act);
        c.setText("›");
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        c.setTextColor(soft);
        r.addView(c);
        r.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.addView(r, lp);
    }

    /** Kopf eines Untermenues: nur der Titel (Zurueck erledigen die Knoepfe unten). */
    void header(String title, Runnable onBack) {
        text(title, 18, this.title, true).setPadding(px(4), 0, 0, px(8));
    }

    /** Auswahlzeile mit Symbol vor dem Text. */
    void rowIcon(int iconRes, String label, boolean selected, Runnable onClick) {
        row(label, selected, onClick);
        LinearLayout r = (LinearLayout) box.getChildAt(box.getChildCount() - 1);
        android.widget.ImageView iv = new android.widget.ImageView(act);
        iv.setImageResource(iconRes);
        iv.setColorFilter(selected ? hiTx : tx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(20), px(20));
        lp.rightMargin = px(12);
        r.addView(iv, 0, lp);
    }

    /** Duenne Trennlinie zwischen Gruppen. */
    void divider() {
        View v = new View(act);
        v.setBackgroundColor(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
        lp.setMargins(px(14), px(6), px(14), px(6));
        box.addView(v, lp);
    }

    /** Knopf in Pillenform; primaer = hell, sonst nur Text. */
    void pill(String label, boolean primary, boolean enabled, Runnable onClick) {
        TextView t = new TextView(act);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(primary ? hiTx : tx);
        t.setPadding(px(12), px(11), px(12), px(11));
        pressable(t, primary ? hi : neutral(), primary ? 0x33000000 : ripple);
        if (!enabled) {
            t.setAlpha(0.4f);
        } else {
            t.setOnClickListener(v -> {
                onClick.run();
                close();
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.addView(t, lp);
    }

    /** Scrollbarer Bereich mit begrenzter Hoehe (damit lange Texte die Blase nicht sprengen). */
    TextView scrollText(String s, float sp, int color) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        final int max = Math.round(act.getResources().getDisplayMetrics().heightPixels * 0.35f);
        ScrollView sv = new Flex(act, max, px(70));
        sv.addView(t);
        box.addView(sv);
        return t;
    }

    void space(int dp) {
        View v = new FrameLayout(act);
        box.addView(v, new LinearLayout.LayoutParams(1, px(dp)));
    }

    /**
     * Schalterzeile im iPhone-Stil: Text links, animierter Schalter rechts; die ganze Zeile ist antippbar.
     * Beginnt der Text mit Leerzeichen, wird die Zeile als Unterpunkt eingerueckt und etwas kleiner dargestellt.
     */
    void toggle(ViewGroup parent, String label, boolean on, java.util.function.Consumer<Boolean> cb) {
        boolean sub = label.startsWith(" ");
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(sub ? 26 : 8), px(8), px(6), px(8));
        r.setMinimumHeight(px(48));
        pressable(r, 0, ripple);
        final TextView t = new TextView(act);
        t.setText(label.trim());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sub ? 13 : 14.5f);
        t.setTextColor(sub ? soft : tx);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final IosSwitch sw = new IosSwitch(act, on, hi, light ? 0x261B1F22 : 0x33FFFFFF, hiTx, light ? 0xFFFFFFFF : 0xFFB8BCC4);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = px(12);
        r.addView(sw, slp);
        r.setContentDescription(label.trim());
        r.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(sw.isOn());
                info.setClassName("android.widget.Switch");
            }
        });
        r.setOnClickListener(v -> {
            boolean now = !sw.isOn();
            sw.setOn(now, true);
            Haptic.play(v, now ? Haptic.TOGGLE_ON : Haptic.TOGGLE_OFF);
            cb.accept(now);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        parent.addView(r, lp);
        // feine Trennlinie wie in iOS-Listen
        View line = new View(act);
        line.setBackgroundColor(this.line);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(0.6f)));
        llp.leftMargin = px(sub ? 26 : 8);
        parent.addView(line, llp);
    }

    /** Erklaerzeile unter dem Titel eines Untermenues. */
    void hint(String s) {
        TextView t = text(s, 12.5f, soft, false);
        t.setPadding(px(4), 0, px(4), px(6));
        t.setLineSpacing(0, 1.1f);
    }

    /** Gruppen-Ueberschrift im Hauptmenue ("Inhalte", "Aussehen", "System"). */
    void group(String s) {
        TextView t = text(s, 12, soft, false);
        t.setPadding(px(6), px(12), 0, px(2));
    }

    /** Kachel mit Symbol. */
    void tile(int icon, String title, String sub, Runnable onClick) {
        tile(title, sub, onClick);
        LinearLayout r = (LinearLayout) box.getChildAt(box.getChildCount() - 1);
        android.widget.ImageView iv = new android.widget.ImageView(act);
        iv.setImageResource(icon);
        iv.setColorFilter(tx);
        iv.setAlpha(0.9f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(22), px(22));
        lp.rightMargin = px(14);
        r.addView(iv, 0, lp);
    }

    /** Auswahl nebeneinander (z. B. kompakt / luftig); genau eine ist aktiv. */
    void segment(ViewGroup parent, String[] labels, int selected, java.util.function.IntConsumer cb) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] tv = new TextView[labels.length];
        final int[] sel = {selected};
        Runnable paint = () -> {
            for (int i = 0; i < tv.length; i++) {
                boolean on = i == sel[0];
                pressable(tv[i], on ? hi : (light ? 0x14000000 : 0x1FFFFFFF), on ? 0x33000000 : ripple);
                tv[i].setTextColor(on ? hiTx : tx);
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = new TextView(act);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setPadding(px(4), px(10), px(4), px(10));
            t.setMaxLines(1);
            if (labels.length >= 4) {
                // viele Felder (z. B. Schriftarten): Schrift verkleinert sich statt umzubrechen
                t.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
            }
            t.setOnClickListener(v -> {
                sel[0] = idx;
                paint.run();
                cb.accept(idx);
            });
            tv[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = i == 0 ? 0 : px(4);
            row.addView(t, lp);
        }
        paint.run();
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Scrollbarer Container (Hoehe hoechstens heightFraction des Bildschirms) fuer beliebige Inhalte. */
    LinearLayout scrollBox(float heightFraction) {
        // Hoehe begrenzen: Blase beginnt bei 18 % der Hoehe; Kopf und "Schliessen" muessen immer sichtbar bleiben
        int screenH = act.getResources().getDisplayMetrics().heightPixels;
        final int max = Math.max(px(160), Math.min(Math.round(screenH * heightFraction), Math.round(screenH * (1f - topFrac)) - px(200)));
        ScrollView sv = new Flex(act, max, px(90));
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(sv);
        return body;
    }

    TextView label(ViewGroup parent, String s) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(soft);
        t.setPadding(px(4), px(14), 0, px(4));
        parent.addView(t);
        return t;
    }

    /** Knopf, der die Blase offen laesst (z. B. Merken); liefert die TextView zum Umbeschriften. */
    TextView pillKeep(String label, boolean primary, Runnable onClick) {
        return pillIn(box, label, primary, onClick);
    }

    /** Wie pillKeep, aber in einem beliebigen Container (z. B. im scrollbaren Bereich). */
    TextView pillIn(ViewGroup parent, String label, boolean primary, Runnable onClick) {
        TextView t = new TextView(act);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(primary ? hiTx : tx);
        t.setPadding(px(12), px(11), px(12), px(11));
        pressable(t, primary ? hi : neutral(), primary ? 0x33000000 : ripple);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        parent.addView(t, lp);
        return t;
    }


    /** Zwei Knoepfe nebeneinander (z. B. Merken | Teilen); die Blase bleibt offen. */
    void pillRow(String l1, Runnable r1, String l2, Runnable r2) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        pillIn(row, l1, false, r1);
        pillIn(row, l2, false, r2);
        for (int i = 0; i < 2; i++) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
            p.width = 0;
            p.weight = 1f;
            p.leftMargin = i == 0 ? 0 : px(3);
            p.rightMargin = i == 0 ? px(3) : 0;
        }
        box.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * Einheitliche Fusszeile fuer alles, was Einstellungen aendert: links "Abbrechen", rechts "OK".
     * "OK" ist erst hell, wenn etwas geaendert ist (mit watch()). closeAfter = Blase danach schliessen.
     */
    TextView footer(boolean closeAfter, Runnable onCancel, Runnable onApply) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        pillIn(row, "Abbrechen", false, () -> {
            onCancel.run();
            if (closeAfter) close();
        });
        TextView ok = pillIn(row, "OK", false, () -> {
            onApply.run();
            if (closeAfter) close();
        });
        ok.setTag(Boolean.FALSE);
        for (int i = 0; i < 2; i++) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
            p.width = 0;
            p.weight = 1f;
            p.leftMargin = i == 0 ? 0 : px(3);
            p.rightMargin = i == 0 ? px(3) : 0;
        }
        box.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return ok;
    }

    /** Hell hervorgehoben (aktiv) oder normaler erhabener Knopf (inaktiv). */
    void setActive(TextView t, boolean on) {
        if (Boolean.valueOf(on).equals(t.getTag())) return;
        t.setTag(on);
        pressable(t, on ? hi : neutral(), on ? 0x33000000 : ripple);
        t.setTextColor(on ? hiTx : tx);
    }

    /** Prueft alle 250 ms, ob etwas geaendert wurde, und faerbt den Knopf entsprechend. */
    void watch(final TextView btn, final java.util.function.BooleanSupplier dirty) {
        final boolean[] on = {false};
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            if (!on[0]) return;
            try {
                setActive(btn, dirty.getAsBoolean());
            } catch (Exception ignored) {
            }
            btn.postDelayed(r[0], 250);
        };
        btn.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                on[0] = true;
                v.post(r[0]);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                on[0] = false;
            }
        });
    }
}
