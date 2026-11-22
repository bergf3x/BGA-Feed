/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Untermenue der Einstellungen: Farben der Nebenzeilen und des Wetterbereichs, mit Verlauf und Live-Vorschau. */
public class ColorsActivity extends Activity {
    /** Grosse Palette. */
    private static final int[] PAL = {
            0xFFFFFF, 0xD9D9D9, 0x9CC9FF, 0x4DA3FF, 0x7CE3BE, 0x2ED47A, 0xFFE08A,
            0xFFB020, 0xFF8A5B, 0xFF5C5C, 0xFF8AD1, 0xB58CFF, 0x1B1F22
    };

    private interface IntCb {
        void on(int v);
    }

    private int widgetId;
    private int txPct;
    private int theme;
    private Bubble b;
    private GradientDrawable prevFill;
    private TextView prevTitle;
    private TextView prevMeta;
    private TextView prevWx;
    private Pick pickSub;
    private Pick pickWx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (widgetId == 0) {
            finish();
            return;
        }
        txPct = Prefs.text(this, widgetId);
        theme = Prefs.theme(this, widgetId);
        b = new Bubble(this, 360, 16);
        b.header("Farben", b::close);

        FrameLayout backdrop = new FrameLayout(this);
        GradientDrawable bd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3B6EA5, 0xFF8E5BA8});
        bd.setCornerRadius(b.px(16));
        backdrop.setBackground(bd);
        backdrop.setPadding(b.px(10), b.px(10), b.px(10), b.px(10));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(b.px(14), b.px(10), b.px(14), b.px(10));
        prevFill = new GradientDrawable();
        prevFill.setCornerRadius(b.px(16));
        card.setBackground(prevFill);
        prevTitle = new TextView(this);
        prevTitle.setText("Beispiel: Neue Schlagzeile");
        prevTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        prevTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        prevMeta = new TextView(this);
        prevMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        prevWx = new TextView(this);
        prevWx.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        prevWx.setPadding(0, b.px(6), 0, 0);
        card.addView(prevTitle);
        card.addView(prevMeta);
        card.addView(prevWx);
        backdrop.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.box.addView(backdrop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = b.scrollBox(0.5f);
        pickSub = new Pick(body, "Farbe der Nebenzeilen", Prefs.colorSub(this, widgetId), Prefs.colorSub2(this, widgetId));
        pickWx = new Pick(body, "Farbe des Wetterbereichs", Prefs.colorWx(this, widgetId), Prefs.colorWx2(this, widgetId));
        refreshPreview();

        b.space(6);
        final int[] init = {pickSub.c1, pickSub.c2, pickWx.c1, pickWx.c2};
        TextView ok = b.footer(true, () -> { }, this::save);
        b.watch(ok, () -> init[0] != pickSub.c1 || init[1] != pickSub.c2 || init[2] != pickWx.c1 || init[3] != pickWx.c2);
    }

    private void save() {
        Prefs.setSubWx(this, widgetId, pickSub.c1, pickWx.c1);
        Prefs.setColors2(this, widgetId, pickSub.c1 >= 0 ? pickSub.c2 : -1, pickWx.c1 >= 0 ? pickWx.c2 : -1);
        WidgetProvider.updateAny(this, widgetId);
    }

    private void tint(TextView t, String text, int c1, int c2, int base, float f) {
        if (c1 >= 0 && c2 >= 0) {
            t.setText(Pal.gradient(text, c1, c2, txPct, f));
        } else {
            t.setText(text);
            t.setTextColor(Prefs.argb(c1 >= 0 ? c1 : base, txPct, f));
        }
    }

    private static boolean tooBright(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0 > 0.75;
    }

    private static boolean tooDark(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0 < 0.25;
    }

    private View swatch(int c, int cur, String lbl, IntCb pick) {
        TextView v = new TextView(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        boolean on = c == cur;
        g.setColor(c < 0 ? 0x00000000 : 0xFF000000 | c);
        g.setStroke(b.px(on ? 3 : 1), on ? b.title : b.soft);
        v.setBackground(g);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTextColor(b.tx);
        if (c < 0) v.setText(lbl);
        v.setOnClickListener(x -> pick.on(c));
        return v;
    }

    private void fillRows(LinearLayout host, int cur, String firstLbl, IntCb pick) {
        host.removeAllViews();
        int[] all = new int[PAL.length + 1];
        all[0] = -1;
        System.arraycopy(PAL, 0, all, 1, PAL.length);
        int sz = b.px(34);
        LinearLayout row = null;
        for (int i = 0; i < all.length; i++) {
            if (i % 7 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = b.px(i == 0 ? 0 : 6);
                host.addView(row, rl);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.rightMargin = b.px(7);
            row.addView(swatch(all[i], cur, firstLbl, pick), lp);
        }
    }

    /** Farbwahl mit Palette, Hex-Feld und optionalem zweiten Farbton fuer einen Verlauf. */
    private final class Pick {
        int c1;
        int c2;
        private final LinearLayout h1;
        private final LinearLayout h2;
        private final EditText e1;
        private final EditText e2;
        private final TextView warn;
        private boolean setting;

        Pick(LinearLayout body, String title, int c1, int c2) {
            this.c1 = c1;
            this.c2 = c2;
            b.label(body, title);
            h1 = new LinearLayout(ColorsActivity.this);
            h1.setOrientation(LinearLayout.VERTICAL);
            body.addView(h1);
            e1 = hex(body, v -> {
                this.c1 = v;
                paint();
            });
            b.label(body, "Verlauf bis (optional, nur mit eigener Startfarbe)");
            h2 = new LinearLayout(ColorsActivity.this);
            h2.setOrientation(LinearLayout.VERTICAL);
            body.addView(h2);
            e2 = hex(body, v -> {
                this.c2 = v;
                paint();
            });
            warn = b.label(body, "");
            warn.setTextColor(0xFFFFB74D);
            setTexts();
            paint();
        }

        private EditText hex(LinearLayout body, IntCb cb) {
            EditText e = new EditText(ColorsActivity.this);
            e.setSingleLine(true);
            e.setHint("Hex, z. B. 4DA3FF");
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            e.setTextColor(b.tx);
            e.setHintTextColor(b.soft);
            e.setBackground(Depth.sunken(b.light ? 0xFFE2E5EC : 0xFF17191E, b.px(10), b.d, b.light));
        e.setPadding(b.px(10), b.px(8), b.px(10), b.px(8));
            e.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

                @Override
                public void onTextChanged(CharSequence s, int a, int c, int d) {}

                @Override
                public void afterTextChanged(Editable ed) {
                    if (setting) return;
                    String t = ed.toString().trim();
                    if (t.startsWith("#")) t = t.substring(1);
                    if (t.length() == 6) {
                        try {
                            cb.on(Integer.parseInt(t, 16) & 0xFFFFFF);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            });
            body.addView(e, new LinearLayout.LayoutParams(b.px(150), ViewGroup.LayoutParams.WRAP_CONTENT));
            return e;
        }

        void paint() {
            fillRows(h1, c1, "T", v -> {
                c1 = v;
                setTexts();
                paint();
            });
            fillRows(h2, c2, "–", v -> {
                c2 = v;
                setTexts();
                paint();
            });
            boolean bright = (c1 >= 0 && tooBright(c1)) || (c1 >= 0 && c2 >= 0 && tooBright(c2));
            boolean dark = (c1 >= 0 && tooDark(c1)) || (c1 >= 0 && c2 >= 0 && tooDark(c2));
            String w = "";
            if (bright && theme == 1) w = "Achtung: Diese Farbe ist auf hellem Design schlecht lesbar.";
            else if (dark && theme == 0) w = "Achtung: Diese Farbe ist auf dunklem Design schlecht lesbar.";
            warn.setText(w);
            warn.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
            refreshPreview();
        }

        private void setTexts() {
            setting = true;
            e1.setText(c1 >= 0 ? String.format("%06X", c1) : "");
            e2.setText(c2 >= 0 ? String.format("%06X", c2) : "");
            setting = false;
        }
    }

    private void refreshPreview() {
        if (prevFill == null) return;
        boolean light = Prefs.isLight(this, widgetId);
        prevFill.setColor(Prefs.argb(light ? 0xEEF1F6 : 0x1E1E26, Prefs.bg(this, widgetId), 1f));
        int shown = Prefs.color(this, widgetId);
        prevTitle.setTextColor(Prefs.argb(shown, txPct, 1f));
        if (pickSub != null) tint(prevMeta, "Region · vor 12 Min.", pickSub.c1, pickSub.c2, shown, 0.7f);
        if (pickWx != null) {
            tint(prevWx, "Regen 100 %   ·   Wind 8 km/h\nSonne 07:21 – 16:25\nNullgrad 1900 m   ·   Schnee ab ca. 1500 m",
                    pickWx.c1, pickWx.c2, shown, 0.7f);
        }
    }
}
