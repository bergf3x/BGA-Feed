/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;

/** Widget auf den Startbildschirm legen (oder Anleitung zeigen, wenn der Launcher das nicht direkt annimmt). */
final class WidgetPin {
    private WidgetPin() { }

    static void pin(final android.app.Activity act, Class<?> provider) {
        pin(act, provider, -1);
    }

    /** withWeather: 1 = Nachrichten mit Wetter ("Beides"), 0 = nur Nachrichten, -1 = egal (Wetter-Widget). */
    static void pin(final android.app.Activity act, Class<?> provider, int withWeather) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(act);
            android.app.PendingIntent cb = null;
            if (withWeather >= 0) {
                // nach dem Ablegen meldet das System die neue Widget-Nummer: dort die gewaehlte Variante einstellen
                Intent done = new Intent(act, WidgetProvider.class).setAction(WidgetProvider.ACTION_PINNED)
                        .putExtra("wx", withWeather == 1);
                int fl = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        | (android.os.Build.VERSION.SDK_INT >= 31 ? android.app.PendingIntent.FLAG_MUTABLE : 0);
                cb = android.app.PendingIntent.getBroadcast(act, 40 + withWeather, done, fl);
            }
            if (m.isRequestPinAppWidgetSupported()
                    && m.requestPinAppWidget(new ComponentName(act, provider), null, cb)) {
                return;
            }
        } catch (Exception ignored) {
        }
        new android.app.AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Widget manuell hinzufügen")
                .setMessage("Dein Startbildschirm nimmt das Widget nicht direkt an. So geht es von Hand:\n\n"
                        + "1. Leere Stelle auf dem Startbildschirm gedrückt halten\n"
                        + "2. „Widgets“ wählen\n"
                        + "3. „BGL Wetter & News“ suchen und auf den Startbildschirm ziehen\n\n"
                        + "Tipp: Unter App-Info > Berechtigungen kann bei manchen Geräten „Verknüpfungen auf dem Startbildschirm erstellen“ "
                        + "erlaubt werden, dann klappt der Knopf direkt.")
                .setPositiveButton("OK", null)
                .setNeutralButton("App-Info", (dlg, w) -> {
                    try {
                        act.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + act.getPackageName())));
                    } catch (Exception e) {
                        android.widget.Toast.makeText(act, "App-Info nicht erreichbar", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
