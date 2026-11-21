package de.bergf3x.bgawidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

/** Benachrichtigung bei neuer amtlicher Unwetterwarnung (Stufe 3 oder hoeher). */
final class Notifier {
    private static final String CHANNEL = "warn";

    private Notifier() {}

    static void warn(Context c, int level, String text) {
        if (!Prefs.warnNotify(c) || level < 3 || text.isEmpty()) return;
        String key = level + "|" + text;
        if (key.equals(Prefs.lastNotified(c))) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Unwetterwarnungen",
                        NotificationManager.IMPORTANCE_HIGH));
            }
            Intent open = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.dwd.de/DE/wetter/warnungen/warnWetter_node.html"));
            int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent pi = PendingIntent.getActivity(c, 77, open, PendingIntent.FLAG_UPDATE_CURRENT | imm);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_warn)
                    .setContentTitle("Berchtesgadener Land: Stufe " + level)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(7001, b.build());
            Prefs.setLastNotified(c, key);
        } catch (Exception ignored) {
        }
    }
}
