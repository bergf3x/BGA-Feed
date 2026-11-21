package de.bergf3x.bgawidget;

import android.app.Activity;
import android.os.Bundle;

/** Blase "Widget hinzufuegen": Nachrichten- oder Wetter-Widget auf den Startbildschirm legen. */
public class AddWidgetActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bubble b = new Bubble(this, 300, 12);
        b.header("Widget hinzufügen", b::close);
        b.text("Mit einem Widget siehst du Nachrichten und Wetter direkt auf dem Startbildschirm.", 13, b.soft, false)
                .setPadding(b.px(4), 0, b.px(4), b.px(10));
        b.pillKeep("Nachrichten-Widget", true, () -> WidgetPin.pin(this, WidgetProvider.class));
        b.pillKeep("Wetter-Widget", false, () -> WidgetPin.pin(this, WeatherWidgetProvider.class));
        b.pill("Schließen", false, true, () -> { });
    }
}
