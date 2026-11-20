package de.bergf3x.bgawidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;

/** Auswahlmenue fuer den Wetter-Ort. */
public class PlaceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id == 0) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Wetter-Ort")
                .setSingleChoiceItems(Weather.PLACES, Prefs.place(this, id), (d, which) -> {
                    d.dismiss();
                    Prefs.setPlace(this, id, which);
                    WidgetProvider.refreshHeader(this, id);
                    // Daten neu laden lassen: ListService holt dabei das Wetter fuer den neuen Ort
                    AppWidgetManager.getInstance(this).notifyAppWidgetViewDataChanged(id, R.id.list);
                    finish();
                })
                .setOnCancelListener(d -> finish())
                .show();
    }
}
