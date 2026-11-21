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
        if (light) {
            tx = 0xFF1F3B57; soft = 0xB31B1F22; title = 0xFF1B1F22;
            hi = 0xFF1B1F22; hiTx = 0xFFFFFFFF; ripple = 0x33000000; line = 0x331B1F22;
        } else {
            tx = 0xFFB5D4F4; soft = 0x99FFFFFF; title = 0xFFFFFFFF;
            hi = 0xFFF2F2F2; hiTx = 0xFF1B1F22; ripple = 0x40FFFFFF; line = 0x2EFFFFFF;
        }
        act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        act.getWindow().setDimAmount(light ? 0.12f : 0.3f);
        boolean blur = false;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                WindowManager wm = (WindowManager) act.getSystemService(Activity.WINDOW_SERVICE);
                if (wm.isCrossWindowBlurEnabled()) {
                    act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    WindowManager.LayoutParams wlp = act.getWindow().getAttributes();
                    wlp.setBlurBehindRadius(Math.round(20 * d));
                    act.getWindow().setAttributes(wlp);
                    blur = true;
                }
            } catch (Throwable ignored) {
            }
        }

        FrameLayout root = new FrameLayout(act);
        root.setOnClickListener(v -> close());

        box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(padDp);
        box.setPadding(pad, pad, pad, pad);
        // Glas: halbtransparent mit Lichtverlauf von links oben und feinem hellen Rand
        int a1 = blur ? 0xB0 : 0xE6, a2 = blur ? 0x98 : 0xD8;
        int[] cols = light
                ? new int[]{(a1 << 24) | 0xFFFFFF, (a2 << 24) | 0xE4E9F1}
                : new int[]{(a1 << 24) | 0x343846, (a2 << 24) | 0x1A1B24};
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, cols);
        bg.setCornerRadius(px(28));
        bg.setStroke(Math.max(1, px(1)), light ? 0x99FFFFFF : 0x40FFFFFF);
        box.setBackground(bg);
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
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(12), px(10), px(12), px(10));
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
            close();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(1);
        box.addView(r, lp);
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

    /** Schalterzeile, die man antippen kann ohne die Blase zu schliessen; an = hell mit Haekchen. */
    void toggle(ViewGroup parent, String label, boolean on, java.util.function.Consumer<Boolean> cb) {
        final boolean[] state = {on};
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(12), px(9), px(12), px(9));
        final TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView c = new TextView(act);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        r.addView(c);
        Runnable paint = () -> {
            pressable(r, state[0] ? hi : 0, state[0] ? 0x33000000 : ripple);
            t.setTextColor(state[0] ? hiTx : tx);
            c.setTextColor(hiTx);
            c.setText(state[0] ? "✓" : "");
        };
        paint.run();
        r.setOnClickListener(v -> {
            state[0] = !state[0];
            paint.run();
            cb.accept(state[0]);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(1);
        parent.addView(r, lp);
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
        final int max = Math.round(act.getResources().getDisplayMetrics().heightPixels * heightFraction);
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
