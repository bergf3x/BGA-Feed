/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** Eigene Wecker-Kette: naechster Abruf im kuerzeren der beiden Intervalle, auch im Doze-Modus erlaubt (ungenau). */
final class Scheduler {
    private Scheduler() {}

    private static PendingIntent pi(Context ctx) {
        Intent in = new Intent(ctx, UpdateReceiver.class).setAction(UpdateReceiver.TICK);
        return PendingIntent.getBroadcast(ctx, 91, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context ctx) {
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int n = mgr.getAppWidgetIds(new ComponentName(app, WidgetProvider.class)).length
                + mgr.getAppWidgetIds(new ComponentName(app, WeatherWidgetProvider.class)).length;
        PendingIntent p = pi(app);
        if (n == 0) {
            am.cancel(p);
            return;
        }
        int min = Math.min(Prefs.INTERVALS[Prefs.wxIdx(app)], Prefs.INTERVALS[Prefs.newsIdx(app)]);
        try {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + min * 60000L, p);
        } catch (Exception ignored) {
        }
    }
}
