/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
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

    static void warn(Context c, int level, String text, String region, String ags) {
        if (!Prefs.warnNotify(c) || level < 3 || text.isEmpty()) return;
        String key = level + "|" + text;
        String pk = "wc_last_" + ags;
        if (key.equals(Prefs.sp(c).getString(pk, ""))) return;
        Prefs.sp(c).edit().putString(pk, key).apply();
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Unwetterwarnungen",
                        NotificationManager.IMPORTANCE_HIGH));
            Intent open = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.dwd.de/DE/wetter/warnungen/warnWetter_node.html"));
            int imm = PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(c, 77, open, PendingIntent.FLAG_UPDATE_CURRENT | imm);
            Notification.Builder b = new Notification.Builder(c, CHANNEL);
            b.setSmallIcon(R.drawable.ic_warn)
                    .setContentTitle("Warnung " + region)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(7001 + Math.abs(ags.hashCode() % 500), b.build());
            Prefs.setLastNotified(c, key);
        } catch (Exception ignored) {
        }
    }

    /** Neuer Artikel mit einem deiner Stichwoerter (nur frische Artikel, je Artikel einmal). */
    static void keyword(Context c, FeedItem it) {
        if (!Prefs.kwNotify(c) || it.link.isEmpty() || !it.isFresh() || Prefs.kwWasNotified(c, it.link)) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel("kw", "Stichwort-Treffer",
                        NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(it.link));
            int imm = PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(c, 78 + (it.link.hashCode() & 0xFFFF), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | imm);
            Notification.Builder b = new Notification.Builder(c, "kw");
            b.setSmallIcon(R.drawable.ic_warn)
                    .setContentTitle(it.title)
                    .setContentText(it.meta())
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(8000 + (it.link.hashCode() & 0xFFF), b.build());
            Prefs.kwMarkNotified(c, it.link);
        } catch (Exception ignored) {
        }
    }
}
