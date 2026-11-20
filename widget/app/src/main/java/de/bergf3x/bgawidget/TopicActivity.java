package de.bergf3x.bgawidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/** Chip-Tipp: Thema direkt setzen oder (bei Oberthemen mit Unterthemen) ein kleines Untermenue zeigen. */
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
            return;
        }
        final List<String> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        values.add(topic);
        labels.add("Alle in „" + topic + "“");
        List<Catalog.Feed> all = Catalog.load(this);
        for (String t : Catalog.topicsFor(all, Catalog.effective(all, Prefs.feeds(this, id)))) {
            if (t.startsWith(topic + "/")) {
                values.add(t);
                labels.add(t.substring(t.indexOf('/') + 1));
            }
        }
        int cur = values.indexOf(Prefs.topic(this, id));
        new AlertDialog.Builder(this)
                .setTitle(topic)
                .setSingleChoiceItems(labels.toArray(new String[0]), cur, (d, which) -> {
                    d.dismiss();
                    apply(values.get(which));
                })
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void apply(String topic) {
        Prefs.setTopic(this, id, topic);
        WidgetProvider.refresh(this, id);
        finish();
        overridePendingTransition(0, 0);
    }
}
