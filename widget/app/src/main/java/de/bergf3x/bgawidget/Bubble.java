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
        act.getWindow().setDimAmount(light ? 0.40f : 0.60f);

        FrameLayout root = new FrameLayout(act);
        root.setOnClickListener(v -> close());

        box = new LinearLayout(act);
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
        lp.topMargin = Math.round(screenH * 0.18f);
        root.addView(box, lp);
        act.setContentView(root);

        // weich aufgehen: leicht kleiner und durchsichtig -> voll
        box.setAlpha(0f);
        box.setScaleX(0.88f);
        box.setScaleY(0.88f);
        box.setPivotX(w / 2f);
        box.setPivotY(0f);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190)
                .setInterpolator(new DecelerateInterpolator()).start();
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

    /** Sanft schliessen, danach die Activity beenden. */
    void close() {
        if (closing) return;
        closing = true;
        box.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(120)
                .withEndAction(() -> {
                    act.finish();
                    act.overridePendingTransition(0, 0);
                }).start();
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

    private void pressable(View v, int fillColor, int rippleColor) {
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
        r.setPadding(px(16), px(12), px(14), px(12));
        pressable(r, light ? 0x14000000 : 0x1AFFFFFF, ripple);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
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
        lp.topMargin = px(7);
        box.addView(r, lp);
    }

    /** Kopf eines Untermenues: Zurueck-Zeile und Titel. */
    void header(String title, Runnable onBack) {
        TextView back = text("‹  Zurück", 14, soft, false);
        back.setPadding(px(4), px(2), px(14), px(8));
        back.setOnClickListener(v -> onBack.run());
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
        pressable(t, primary ? hi : 0, primary ? 0x33000000 : ripple);
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
        ScrollView sv = new ScrollView(act) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
            }
        };
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
                pressable(tv[i], on ? hi : 0, on ? 0x33000000 : ripple);
                tv[i].setTextColor(on ? hiTx : tx);
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = new TextView(act);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setPadding(px(8), px(10), px(8), px(10));
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
        final int max = Math.max(px(160), Math.min(Math.round(screenH * heightFraction), Math.round(screenH * 0.82f) - px(200)));
        ScrollView sv = new ScrollView(act) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
            }
        };
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
        TextView t = new TextView(act);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(primary ? hiTx : tx);
        t.setPadding(px(12), px(11), px(12), px(11));
        pressable(t, primary ? hi : 0, primary ? 0x33000000 : ripple);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.addView(t, lp);
        return t;
    }
}
