package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;

/** Unsichtbarer Zwischenschritt: setzt das Thema aus einem Chip-Tipp und aktualisiert das Widget. */
public class TopicActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        String topic = getIntent().getStringExtra("topic");
        if (id != 0) {
            Prefs.setTopic(this, id, topic == null ? "" : topic);
            WidgetProvider.update(this, AppWidgetManager.getInstance(this), id);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
