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
        final Bubble b = new Bubble(this, 270, 8);
        b.text(topic, 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(6));
        String current = Prefs.topic(this, id);
        final String parent = topic;
        b.row("Alle in „" + topic + "“", current.equals(topic), () -> apply(parent));
        List<Catalog.Feed> all = Catalog.load(this);
        for (String t : Catalog.topicsFor(all, Catalog.effective(all, Prefs.feeds(this, id)))) {
            if (!t.startsWith(topic + "/")) continue;
            final String value = t;
            b.row(t.substring(t.indexOf('/') + 1), t.equals(current), () -> apply(value));
        }
    }

    private void apply(String topic) {
        Prefs.setTopic(this, id, topic);
        WidgetProvider.refresh(this, id);
    }
}
