/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Stundenvorhersage der naechsten 12 Stunden als Glas-Blase. */
public class HourlyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        int place = Prefs.place(this, id);
        Weather w = Weather.load(this, place);
        final Bubble b = new Bubble(this, 330, 16);
        b.text("Nächste Stunden – " + Weather.PLACES[place], 16, b.title, true).setPadding(0, 0, 0, b.px(8));
        List<String> lines = w == null ? new java.util.ArrayList<String>() : w.hourlyLines(12);
        if (lines.isEmpty()) {
            b.text("Noch keine Stundenwerte. Tippe auf den Aktualisieren-Pfeil im Widget.", 14, b.tx, false);
        } else {
            LinearLayout body = b.scrollBox(0.5f);
            for (String l : lines) {
                TextView t = new TextView(this);
                t.setText(l);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                t.setTextColor(b.tx);
                t.setPadding(b.px(4), b.px(6), b.px(4), b.px(6));
                body.addView(t);
            }
        }
        b.space(6);
        b.pill("Schließen", false, true, () -> { });
    }
}
