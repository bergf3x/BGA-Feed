/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
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
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        final Runnable finish = () -> {
            if (pr != null && done.compareAndSet(false, true)) pr.finish();
        };
        // Sicherung: der Empfaenger darf dem System nicht ewig "laufend" melden
        if (pr != null) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(finish, 40000);
        new Thread(() -> {
            try {
                run(app, force);
            } catch (Exception ignored) {
            } finally {
                finish.run();
            }
        }).start();
    }

    static synchronized void run(Context ctx, boolean force) {
        Net.init(ctx);
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] news = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
        int[] wx = mgr.getAppWidgetIds(new ComponentName(ctx, WeatherWidgetProvider.class));
        if (news.length == 0 && wx.length == 0) {
            // ohne Widgets nur die Vollbild-App bedienen (Knopf "Jetzt aktualisieren")
            if (force) ListService.syncApp(ctx, true);
            return;
        }
        // Ohne Netz im Hintergrund gar nicht erst versuchen: spart Funk und Zeitueberschreitungen
        if (!force && !Net.online(ctx)) {
            android.util.Log.i("BGAstat", "Abruf uebersprungen: kein Netz");
            return;
        }
        Prefs.takeLocalOnly(ctx);
        if (force) {
            Prefs.invalidateWeather(ctx);
            Prefs.invalidateWarn(ctx);
        }
        long t0 = System.currentTimeMillis();
        Net.resetStats();
        long now = t0;
        // Nachrichten: auf Knopfdruck, nach Intervall (Automatik) oder einmalig, solange noch gar nichts geladen wurde
        boolean newsDue = news.length > 0 && (force || Prefs.newsTime(ctx) == 0
                || (Prefs.newsAuto(ctx) && now - Prefs.newsTry(ctx) >= Prefs.newsMs(ctx)));
        if (newsDue) {
            Prefs.setNewsTry(ctx, now);
            ListService.syncWidgets(ctx, news);
            // weitere Orte: Wetter nachziehen (nach Einstellung; manuell nur, wenn noch nichts da ist)
            for (int i = 1; i < news.length; i++) Weather.refreshIfStale(ctx, Prefs.place(ctx, news[i]));
        } else {
            Warnings.refreshIfStale(ctx);
            Avalanche.refreshIfStale(ctx);
            for (int id : news) Weather.refreshIfStale(ctx, Prefs.place(ctx, id));
        }
        for (int id : news) WidgetProvider.refreshHeader(ctx, id);
        for (int id : wx) {
            Weather.refreshIfStale(ctx, Prefs.place(ctx, id));
            if (Prefs.place2(ctx, id) >= 0) Weather.refreshIfStale(ctx, Prefs.place2(ctx, id));
        }
        for (int id : wx) WeatherWidgetProvider.update(ctx, mgr, id);
        if (newsDue) {
            for (int id : news) mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
        }
        android.util.Log.i("BGAstat", "Abruf " + (force ? "manuell" : "automatisch") + ": Nachrichten=" + newsDue
                + " " + Net.stats() + " in " + (System.currentTimeMillis() - t0) + " ms");
    }
}
