package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;

/** Auswahl-Blase fuer den Wetter-Ort. */
public class PlaceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id == 0) {
            finish();
            return;
        }
        final Bubble b = new Bubble(this, 270, 8);
        b.text("Wetter-Ort", 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(6));
        int cur = Prefs.place(this, id);
        for (int i = 0; i < Weather.PLACES.length; i++) {
            final int place = i;
            b.row(Weather.PLACES[i], i == cur, () -> {
                Prefs.setPlace(this, id, place);
                WidgetProvider.refreshHeader(this, id);
                // ListService holt beim Neuladen auch das Wetter fuer den neuen Ort
                AppWidgetManager.getInstance(this).notifyAppWidgetViewDataChanged(id, R.id.list);
            });
        }
    }
}
