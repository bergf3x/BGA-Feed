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

    /**
     * Naechster Wecker nur, wenn mindestens ein Teil auf Automatik steht (Nachrichten fuer Nachrichten-Widgets,
     * Wetter fuer jedes Widget mit Wetter). Alles manuell = gar kein Wecker, das Handy wird nie dafuer geweckt.
     * Ohne Benachrichtigungen reicht ein Wecker, der das Handy nicht aus dem Schlaf holt: er laeuft dann,
     * sobald das Handy ohnehin wach wird (z. B. beim Entsperren). Das spart die naechtlichen Aufwachvorgaenge.
     */
    static void schedule(Context ctx) {
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] news = mgr.getAppWidgetIds(new ComponentName(app, WidgetProvider.class));
        int[] wx = mgr.getAppWidgetIds(new ComponentName(app, WeatherWidgetProvider.class));
        PendingIntent p = pi(app);
        if (news.length + wx.length == 0) {
            am.cancel(p);
            return;
        }
        boolean needWx = wx.length > 0;
        for (int id : news) if (Prefs.showWeather(app, id)) needWx = true;
        int min = Integer.MAX_VALUE;
        if (news.length > 0 && Prefs.newsAuto(app)) min = Math.min(min, Prefs.INTERVALS[Prefs.newsIdx(app)]);
        if (needWx && Prefs.wxAuto(app)) min = Math.min(min, Prefs.INTERVALS[Prefs.wxIdx(app)]);
        if (min == Integer.MAX_VALUE) {
            am.cancel(p);
            return;
        }
        boolean wake = Prefs.warnNotify(app) || Prefs.kwNotify(app);
        try {
            am.setAndAllowWhileIdle(wake ? AlarmManager.ELAPSED_REALTIME_WAKEUP : AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + min * 60000L, p);
        } catch (Exception ignored) {
        }
    }
}
