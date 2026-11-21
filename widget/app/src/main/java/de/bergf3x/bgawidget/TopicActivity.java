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

/**
 * Chip-Tipp: Thema direkt setzen oder (bei Oberthemen mit Unterthemen) die Auswahl-Blase zeigen.
 * Gleiche Blase fuer alle Oberthemen (Region, Nachrichten, Sport ...) in App und Widget:
 * je Medium ein aufklappbarer Reiter (immer nur einer offen), darin Mehrfachauswahl der Unterthemen.
 */
public class TopicActivity extends Activity {
    private int id;
    private String parent;
    private Bubble b;
    private LinearLayout body;
    private final List<Catalog.Feed> media = new ArrayList<>();
    /** Auswahl als "Thema|Medium" (Medium leer = alle Medien). */
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
        List<Catalog.Feed> all = Catalog.load(this);
        java.util.Set<String> on = Catalog.effective(all, Prefs.feeds(this, id));
        for (Catalog.Feed f : all) {
            if (!on.contains(f.id)) continue;
            for (String t : f.topics) if (t.startsWith(parent + "/")) {
                media.add(f);
                break;
            }
        }
        // bisherige Auswahl uebernehmen, soweit sie zu diesem Oberthema gehoert
        for (String[] e : Prefs.topicSel(this, id)) {
            if (e[0].equals(parent) || e[0].startsWith(parent + "/")) sel.add(e[1].isEmpty() ? e[0] : e[0] + "|" + e[1]);
        }
        for (String s : sel) {
            int i = s.indexOf('|');
            if (i > 0) {
                open = s.substring(i + 1);
                break;
            }
        }
        if (open.isEmpty() && media.size() == 1) open = media.get(0).name;

        b = new Bubble(this, 300, 8);
        b.text(parent, 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(2));
        b.text("Mehrere Themen wählbar", 11, b.soft, false).setPadding(b.px(10), 0, b.px(10), b.px(6));
        body = b.scrollBox(0.7f);
        render();
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
    }

    private void render() {
        body.removeAllViews();
        boolean allMedia = sel.isEmpty() || sel.contains(parent);
        b.row(body, "Alles in „" + parent + "“ (alle Medien)", allMedia && !sel.isEmpty(), () -> {
            sel.clear();
            sel.add(parent);
            save(sel);
        }, true);
        for (final Catalog.Feed f : media) {
            final String tag = "|" + f.name;
            int count = 0;
            for (String s : sel) if (s.endsWith(tag)) count++;
            boolean expanded = f.name.equals(open);
            body.addView(mediumHeader(f.name, count, expanded, () -> {
                open = f.name.equals(open) ? "" : f.name; // nur ein Reiter offen
                render();
            }));
            if (!expanded) continue;
            final String whole = parent + tag;
            b.row(body, "Alle von " + f.name, sel.contains(whole), () -> toggle(whole, f.name), false);
            for (String t : f.topics) {
                if (!t.startsWith(parent + "/")) continue;
                final String value = t + tag;
                b.row(body, "   " + t.substring(t.indexOf('/') + 1), sel.contains(value), () -> toggle(value, f.name), false);
            }
        }
    }

    /** Eintrag an- oder abwaehlen; "Alle von X" und einzelne Themen von X schliessen sich gegenseitig aus. */
    private void toggle(String value, String medium) {
        String whole = parent + "|" + medium;
        if (sel.contains(value)) {
            sel.remove(value);
        } else {
            sel.remove(parent);
            if (value.equals(whole)) {
                java.util.Iterator<String> it = sel.iterator();
                while (it.hasNext()) if (it.next().endsWith("|" + medium)) it.remove();
            } else {
                sel.remove(whole);
            }
            sel.add(value);
        }
        save(sel);
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

    /** Aufklappbarer Medien-Reiter: Markenfarbe, Name, Anzahl gewaehlter Themen, Pfeil. */
    private View mediumHeader(String name, int count, boolean expanded, Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(b.px(12), b.px(10), b.px(12), b.px(10));
        // neutral im Glas-Stil der App (keine Markenfarben)
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(b.light ? 0x14000000 : 0x1FFFFFFF);
        bg.setCornerRadius(b.px(12));
        bg.setStroke(Math.max(1, b.px(1)), b.line);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(b.light ? 0x22000000 : 0x33FFFFFF), bg, null));
        int fg = b.tx;
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(fg);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (count > 0) {
            TextView c = new TextView(this);
            c.setText(count + " gewählt");
            c.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            c.setTextColor(fg);
            c.setPadding(0, 0, b.px(8), 0);
            row.addView(c);
        }
        TextView arrow = new TextView(this);
        arrow.setText(expanded ? "▴" : "▾");
        arrow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        arrow.setTextColor(fg);
        row.addView(arrow);
        row.setClickable(true);
        row.setOnClickListener(v -> click.run());
        row.setContentDescription(name + (expanded ? ", zuklappen" : ", aufklappen"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = b.px(8);
        lp.bottomMargin = b.px(2);
        row.setLayoutParams(lp);
        return row;
    }
}
