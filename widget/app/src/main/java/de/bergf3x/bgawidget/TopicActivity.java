package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;

import java.util.List;

/** Chip-Tipp: Thema direkt setzen oder (bei Oberthemen mit Unterthemen) die Auswahl-Blase zeigen. */
public class TopicActivity extends Activity {
    private int id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        String topic = getIntent().getStringExtra("topic");
        if (topic == null) topic = "";
        if (id == 0) {
            finish();
            return;
        }
        if (!getIntent().getBooleanExtra("menu", false)) {
            apply(topic);
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        final Bubble b = new Bubble(this, 290, 8);
        b.text(topic, 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(6));
        String current = Prefs.topicRaw(this, id);
        final String parent = topic;
        List<Catalog.Feed> all = Catalog.load(this);
        java.util.Set<String> sel = Catalog.effective(all, Prefs.feeds(this, id));
        List<Catalog.Feed> media = new java.util.ArrayList<>();
        for (Catalog.Feed f : all) {
            if (!sel.contains(f.id)) continue;
            for (String t : f.topics) if (t.equals(topic) || t.startsWith(topic + "/")) {
                media.add(f);
                break;
            }
        }
        android.widget.LinearLayout body = b.scrollBox(0.7f);
        if (media.size() <= 1) {
            // nur ein Medium aktiv: Liste wie bisher
            b.row(body, "Alle in „" + topic + "“", current.equals(topic), () -> apply(parent), true);
            for (String t : Catalog.topicsFor(all, sel)) {
                if (!t.startsWith(topic + "/")) continue;
                final String value = t;
                b.row(body, t.substring(t.indexOf('/') + 1), t.equals(current), () -> apply(value), true);
            }
            b.space(4);
            b.pill("Schließen", false, true, () -> { });
            return;
        }
        // mehrere Medien: je Medium ein eigener Block mit Ueberschrift, Auswahl gilt nur fuer dieses Medium
        b.row(body, "Alle Medien in „" + topic + "“", current.equals(topic), () -> apply(parent), true);
        for (Catalog.Feed f : media) {
            final String tag = "|" + f.name;
            android.view.View sep = new android.view.View(this);
            sep.setBackgroundColor(b.line);
            android.widget.LinearLayout.LayoutParams sp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, b.px(1)));
            sp.setMargins(b.px(14), b.px(8), b.px(14), b.px(4));
            body.addView(sep, sp);
            body.addView(mediumHeader(b, f.name));
            final String all1 = parent + tag;
            b.row(body, "Alle von " + f.name, current.equals(all1), () -> apply(all1), true);
            for (String t : f.topics) {
                if (!t.startsWith(topic + "/")) continue;
                final String value = t + tag;
                b.row(body, t.substring(t.indexOf('/') + 1), value.equals(current), () -> apply(value), true);
            }
        }
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
    }

    /** Hervorgehobene Medien-Ueberschrift mit kleinem Kennzeichen (eigene Buchstaben-Marke, kein Original-Logo). */
    private android.view.View mediumHeader(Bubble b, String name) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(b.px(10), b.px(8), b.px(10), b.px(8));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(b.light ? 0x1A000000 : 0x2EFFFFFF);
        bg.setCornerRadius(b.px(12));
        row.setBackground(bg);
        String low = name.toLowerCase(java.util.Locale.GERMANY);
        String mark;
        int color;
        if (low.contains("bgland")) {
            mark = "B24";
            color = 0xFF85B7EB;
        } else if (low.contains("anzeiger")) {
            mark = "BA";
            color = 0xFF5DCAA5;
        } else {
            mark = name.substring(0, Math.min(2, name.length())).toUpperCase(java.util.Locale.GERMANY);
            color = 0xFFF0997B;
        }
        android.widget.TextView badge = new android.widget.TextView(this);
        badge.setText(mark);
        badge.setGravity(android.view.Gravity.CENTER);
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setTextColor(0xFF10151B);
        android.graphics.drawable.GradientDrawable bb = new android.graphics.drawable.GradientDrawable();
        bb.setColor(color);
        bb.setCornerRadius(b.px(7));
        badge.setBackground(bb);
        row.addView(badge, new android.widget.LinearLayout.LayoutParams(b.px(32), b.px(24)));
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(name);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(b.title);
        t.setPadding(b.px(10), 0, 0, 0);
        row.addView(t);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = b.px(10);
        lp.bottomMargin = b.px(2);
        row.setLayoutParams(lp);
        return row;
    }

    private void apply(String topic) {
        Prefs.setTopic(this, id, topic);
        WidgetProvider.refresh(this, id);
    }
}
