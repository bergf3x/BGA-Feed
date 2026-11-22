/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.os.Bundle;

/** Blase "Widgets": nur Nachrichten, nur Wetter (mit Webcams) oder beides auf den Startbildschirm legen. */
public class AddWidgetActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bubble b = new Bubble(this, 320, 12);
        b.header("Widget hinzufügen", b::close);
        b.hint("Wähle, was das Widget auf dem Startbildschirm zeigen soll. Ändern kannst du es später in den Widget-Einstellungen.");
        option(b, R.drawable.ic_news, "Nur Nachrichten", "Liste mit Reitern, ohne Wetter",
                () -> WidgetPin.pin(this, WidgetProvider.class, 0));
        option(b, R.drawable.ic_cloud, "Nur Wetter", "Ort, 3 Tage, Warnungen, Webcams",
                () -> WidgetPin.pin(this, WeatherWidgetProvider.class, -1));
        option(b, R.drawable.ic_widgets, "Beides", "Wetter oben, Nachrichten darunter",
                () -> WidgetPin.pin(this, WidgetProvider.class, 1));
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
    }

    private void option(Bubble b, int icon, String title, String sub, Runnable go) {
        b.tile(icon, title, sub, () -> {
            Haptic.play(b.box, Haptic.TICK);
            go.run();
        });
    }
}
