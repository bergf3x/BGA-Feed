package de.bergf3x.bgawidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;

/** Widget auf den Startbildschirm legen (oder Anleitung zeigen, wenn der Launcher das nicht direkt annimmt). */
final class WidgetPin {
    private WidgetPin() { }

    static void pin(final android.app.Activity act, Class<?> provider) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(act);
            if (m.isRequestPinAppWidgetSupported()
                    && m.requestPinAppWidget(new ComponentName(act, provider), null, null)) {
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
                        + "Tipp Xiaomi: Unter App-Info > Weitere Berechtigungen kann „Verknüpfungen auf dem Startbildschirm erstellen“ "
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
