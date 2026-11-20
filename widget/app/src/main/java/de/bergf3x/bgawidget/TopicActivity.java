package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/** Auswahlmenue fuer das Thema; das aktuelle Thema ist markiert. */
public class TopicActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id == 0) {
            finish();
            return;
        }
        setTitle("Thema wählen");

        final List<String> topics = new ArrayList<>();
        topics.add("");
        topics.addAll(Catalog.topicsFor(Catalog.load(this), Prefs.feeds(this, id)));
        List<String> labels = new ArrayList<>();
        for (String t : topics) labels.add(t.isEmpty() ? "Alle Themen" : t);

        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, labels));
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        int cur = topics.indexOf(Prefs.topic(this, id));
        lv.setItemChecked(Math.max(cur, 0), true);
        lv.setOnItemClickListener((parent, view, pos, rowId) -> {
            Prefs.setTopic(this, id, topics.get(pos));
            WidgetProvider.update(this, AppWidgetManager.getInstance(this), id);
            finish();
        });
        setContentView(lv);
    }
}
