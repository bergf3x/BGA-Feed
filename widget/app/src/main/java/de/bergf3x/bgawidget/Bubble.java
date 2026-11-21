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
    static final int FILL = 0xFF2A2A34;
    static final int BORDER = 0x38FFFFFF;
    static final int TEXT = 0xFFB5D4F4;
    static final int LIGHT = 0xFFF2F2F2;
    static final int DARK = 0xFF1B1F22;

    final Activity act;
    final LinearLayout box;
    final float d;
    private boolean closing;

    Bubble(Activity act, int widthDp, int padDp) {
        this.act = act;
        d = act.getResources().getDisplayMetrics().density;
        act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        act.getWindow().setDimAmount(0.3f);

        FrameLayout root = new FrameLayout(act);
        root.setOnClickListener(v -> close());

        box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(padDp);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FILL);
        bg.setCornerRadius(px(22));
        bg.setStroke(Math.max(1, px(1)), BORDER);
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
        pressable(r, selected ? LIGHT : 0, selected ? 0x33000000 : 0x40FFFFFF);
        TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(selected ? DARK : TEXT);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            TextView c = new TextView(act);
            c.setText("✓");
            c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            c.setTextColor(DARK);
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

    /** Knopf in Pillenform; primaer = hell, sonst nur Text. */
    void pill(String label, boolean primary, boolean enabled, Runnable onClick) {
        TextView t = new TextView(act);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(primary ? DARK : TEXT);
        t.setPadding(px(12), px(11), px(12), px(11));
        pressable(t, primary ? LIGHT : 0, primary ? 0x33000000 : 0x40FFFFFF);
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
            pressable(r, state[0] ? LIGHT : 0, state[0] ? 0x33000000 : 0x40FFFFFF);
            t.setTextColor(state[0] ? DARK : TEXT);
            c.setTextColor(DARK);
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
                pressable(tv[i], on ? LIGHT : 0, on ? 0x33000000 : 0x40FFFFFF);
                tv[i].setTextColor(on ? DARK : TEXT);
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
        t.setTextColor(0xB3B5D4F4);
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
        t.setTextColor(primary ? DARK : TEXT);
        t.setPadding(px(12), px(11), px(12), px(11));
        pressable(t, primary ? LIGHT : 0, primary ? 0x33000000 : 0x40FFFFFF);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.addView(t, lp);
        return t;
    }
}
