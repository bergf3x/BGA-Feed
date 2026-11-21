package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

/** Auswahl-Blase fuer den Wetter-Ort (alphabetisch, scrollbar). */
public class PlaceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id == 0) {
            finish();
            return;
        }
        final Bubble b = new Bubble(this, 300, 12);
        b.header("Wetter-Ort", b::close);
        int cur = Prefs.place(this, id);
        Integer[] order = new Integer[Weather.PLACES.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final Collator col = Collator.getInstance(Locale.GERMANY);
        Arrays.sort(order, (x, y) -> col.compare(Weather.PLACES[x], Weather.PLACES[y]));
        LinearLayout body = b.scrollBox(0.6f);
        for (int idx : order) {
            final int place = idx;
            b.row(body, Weather.PLACES[idx], idx == cur, () -> {
                Prefs.setPlace(this, id, place);
                WidgetProvider.refreshHeader(this, id);
                // ListService holt beim Neuladen auch das Wetter fuer den neuen Ort
                AppWidgetManager.getInstance(this).notifyAppWidgetViewDataChanged(id, R.id.list);
                android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
                if (info != null && WeatherWidgetProvider.class.getName().equals(info.provider.getClassName())) {
                    WeatherWidgetProvider.refresh(this, id);
                }
            }, true);
        }
    }
}
