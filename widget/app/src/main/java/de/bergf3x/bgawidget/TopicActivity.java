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
            android.widget.TextView h = new android.widget.TextView(this);
            h.setText(f.name);
            h.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            h.setTypeface(null, android.graphics.Typeface.BOLD);
            h.setTextColor(b.title);
            h.setPadding(b.px(12), b.px(6), b.px(12), b.px(2));
            body.addView(h);
            final String all1 = parent + tag;
            b.row(body, "Alle von " + f.name, current.equals(all1), () -> apply(all1), true);
            for (String t : f.topics) {
                if (!t.startsWith(topic + "/")) continue;
                final String value = t + tag;
                b.row(body, t.substring(t.indexOf('/') + 1), value.equals(current), () -> apply(value), true);
            }
        }
    }

    private void apply(String topic) {
        Prefs.setTopic(this, id, topic);
        WidgetProvider.refresh(this, id);
    }
}
