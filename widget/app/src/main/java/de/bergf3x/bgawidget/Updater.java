package de.bergf3x.bgawidget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;

/** Zentraler Abruf im Hintergrund: Wetter, Warnungen, Nachrichten – je nach Intervall faellig oder erzwungen. */
final class Updater {
    private Updater() {}

    static void runAsync(final Context ctx, final boolean force, final BroadcastReceiver.PendingResult pr) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                run(app, force);
            } catch (Exception ignored) {
            } finally {
                if (pr != null) pr.finish();
            }
        }).start();
    }

    static synchronized void run(Context ctx, boolean force) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] news = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
        int[] wx = mgr.getAppWidgetIds(new ComponentName(ctx, WeatherWidgetProvider.class));
        if (news.length == 0 && wx.length == 0) return;
        Prefs.takeLocalOnly(ctx);
        if (force) {
            Prefs.invalidateWeather(ctx);
            Prefs.invalidateWarn(ctx);
        }
        long now = System.currentTimeMillis();
        boolean newsDue = force || now - Prefs.newsTry(ctx) >= Prefs.newsMs(ctx);
        if (news.length > 0 && newsDue) {
            Prefs.setNewsTry(ctx, now);
            // erstes Widget laedt Feeds, Wetter und Warnungen; die Feeds liegen danach im gemeinsamen Cache
            new ListService.Factory(ctx, news[0]).onDataSetChanged();
            for (int i = 1; i < news.length; i++) WidgetProvider.refreshHeader(ctx, news[i]);
        } else {
            Warnings.refreshIfStale(ctx);
            Avalanche.refreshIfStale(ctx);
            for (int id : news) {
                Weather.refreshIfStale(ctx, Prefs.place(ctx, id));
                WidgetProvider.refreshHeader(ctx, id);
            }
        }
        for (int id : wx) Weather.refreshIfStale(ctx, Prefs.place(ctx, id));
        for (int id : wx) WeatherWidgetProvider.update(ctx, mgr, id);
        if (news.length > 0 && newsDue) {
            Prefs.markFetched(ctx);
            for (int id : news) mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
        }
    }
}
