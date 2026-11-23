/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Themen-Blase fuer einen Bereich (BGL, Außerhalb, Deutschland & Welt), gleich in App und Widget:
 * Regionen als aufklappbare Reiter (immer nur einer offen), darin Rubriken (Nachrichten, Sport, Kultur, Blaulicht);
 * Mehrfachauswahl, leere Rubriken werden nicht gezeigt, Anzahl in Klammern.
 */
public class TopicActivity extends Activity {
    private int id;
    private String parent;
    private Bubble b;
    private LinearLayout body;
    private Map<String, Integer> counts;
    private final List<String> sel = new ArrayList<>();
    private String open = "";

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
            save(topic.isEmpty() ? new ArrayList<>() : java.util.Collections.singletonList(topic));
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        parent = topic;
        counts = Taxonomy.counts(this, id);
        for (String[] e : Prefs.topicSel(this, id)) {
            if (e[0].equals(parent) || e[0].startsWith(parent + "/")) sel.add(e[0]);
        }
        for (String s : sel) {
            String[] p = s.split("/");
            if (p.length > 2) {
                open = p[0] + "/" + p[1];
                break;
            }
        }
        b = new Bubble(this, 300, 8);
        b.text(parent, 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(2));
        b.text("Mehrere Themen wählbar", 11, b.soft, false).setPadding(b.px(10), 0, b.px(10), b.px(6));
        body = b.scrollBox(0.7f);
        render();
        b.space(4);
        final java.util.Set<String> init = new java.util.HashSet<>(sel);
        TextView ok = b.footer(true, () -> { }, () -> save(sel));
        b.watch(ok, () -> !init.equals(new java.util.HashSet<>(sel)));
    }

    private List<String> children(String path) {
        List<String> out = new ArrayList<>();
        int depth = path.split("/").length;
        for (String k : counts.keySet()) {
            if (k.startsWith(path + "/") && k.split("/").length == depth + 1) out.add(k);
        }
        java.util.Collections.sort(out, Taxonomy::compare);
        return out;
    }

    private String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private int count(String path) {
        Integer c = counts.get(path);
        return c == null ? 0 : c;
    }

    private void render() {
        Bubble.soft(body);
        body.removeAllViews();
        String all = parent.equals(Taxonomy.PAPERS) ? "Alle Zeitungen gemischt" : "Alles in „" + parent + "“";
        b.row(body, all + " (" + count(parent) + ")", sel.contains(parent), () -> {
            sel.clear();
            sel.add(parent);
            render();
        }, false);
        for (final String region : children(parent)) {
            final List<String> subs = children(region);
            if (subs.size() <= 1) {
                // keine Untergliederung: direkt waehlbar
                // gleiche Glas-Kachel wie die aufklappbaren Eintraege, nur mit Haken statt Pfeil
                body.addView(entry(name(region), count(region), sel.contains(region), () -> toggle(region)));
                continue;
            }
            int picked = 0;
            for (String s : sel) if (s.equals(region) || s.startsWith(region + "/")) picked++;
            final boolean expanded = region.equals(open);
            body.addView(header(name(region), count(region), picked, expanded, () -> {
                open = expanded ? "" : region; // nur ein Reiter offen
                render();
            }));
            if (!expanded) continue;
            b.row(body, (parent.equals(Taxonomy.PAPERS) ? "Alles aus " : "Alles aus „") + name(region)
                    + (parent.equals(Taxonomy.PAPERS) ? "" : "“"), sel.contains(region), () -> toggle(region), false);
            for (final String sub : subs) {
                b.row(body, "   " + name(sub) + " (" + count(sub) + ")", sel.contains(sub), () -> toggle(sub), false);
            }
        }
    }

    /** An-/abwaehlen; ein Oberpunkt und seine Unterpunkte schliessen sich gegenseitig aus. */
    private void toggle(String value) {
        if (sel.contains(value)) {
            sel.remove(value);
        } else {
            sel.remove(parent);
            java.util.Iterator<String> it = sel.iterator();
            while (it.hasNext()) {
                String s = it.next();
                if (s.startsWith(value + "/") || value.startsWith(s + "/")) it.remove();
            }
            sel.add(value);
        }
        render();
    }

    private void save(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(';');
            sb.append(v);
        }
        Prefs.setTopic(this, id, sb.toString());
        WidgetProvider.refresh(this, id);
    }

    /** Direkt waehlbarer Eintrag im selben Glas-Stil; gewaehlt = hell mit Haken. */
    private View entry(String name, int n, boolean on, Runnable click) {
        View v = header(name, n, 0, false, click);
        LinearLayout row = (LinearLayout) v;
        TextView t = (TextView) row.getChildAt(0);
        TextView c = (TextView) row.getChildAt(1);
        TextView mark = (TextView) row.getChildAt(2);
        mark.setText(on ? "✓" : "");
        if (on) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(b.hi);
            bg.setCornerRadius(b.px(12));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33000000), bg, null));
            t.setTextColor(b.hiTx);
            c.setTextColor(b.hiTx);
            mark.setTextColor(b.hiTx);
        }
        row.setContentDescription(name + ", " + n + " Meldungen" + (on ? ", ausgewählt" : ""));
        return row;
    }

    /** Aufklappbarer Regions-Reiter im Glas-Stil: Name, Anzahl bzw. Auswahl, Pfeil. */
    private View header(String name, int n, int picked, boolean expanded, Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(b.px(12), b.px(10), b.px(12), b.px(10));
        row.setMinimumHeight(b.px(44));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(b.light ? 0x14000000 : 0x1FFFFFFF);
        bg.setCornerRadius(b.px(12));
        bg.setStroke(Math.max(1, b.px(1)), b.line);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(b.light ? 0x22000000 : 0x33FFFFFF), bg, null));
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(b.tx);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView c = new TextView(this);
        c.setText(picked > 0 ? picked + " gewählt" : String.valueOf(n));
        c.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        c.setTextColor(b.soft);
        c.setPadding(0, 0, b.px(8), 0);
        row.addView(c);
        TextView arrow = new TextView(this);
        arrow.setText(expanded ? "▴" : "▾");
        arrow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        arrow.setTextColor(b.tx);
        row.addView(arrow);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            Haptic.play(v, Haptic.TICK);
            click.run();
        });
        row.setContentDescription(name + ", " + n + " Meldungen, " + (expanded ? "zuklappen" : "aufklappen"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = b.px(6);
        lp.bottomMargin = b.px(2);
        row.setLayoutParams(lp);
        return row;
    }
}
