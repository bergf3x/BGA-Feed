package de.bergf3x.bgawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

/** Eigenes, reines Wetter-Widget (gleiche Wetterzeile wie im Nachrichten-Widget). */
public class WeatherWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "de.bergf3x.bgawidget.WX_REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
        loadAndUpdate(ctx, ids, goAsync());
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            Prefs.invalidateWeather(ctx);
            Prefs.invalidateWarn(ctx);
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WeatherWidgetProvider.class));
            loadAndUpdate(ctx, ids, goAsync());
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) Prefs.remove(ctx, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, android.os.Bundle opts) {
        update(ctx, mgr, id);
    }

    private static void loadAndUpdate(final Context ctx, final int[] ids, final PendingResult pr) {
        new Thread(() -> {
            try {
                Warnings.refreshIfStale(ctx);
                Avalanche.refreshIfStale(ctx);
                for (int id : ids) Weather.refreshIfStale(ctx, Prefs.place(ctx, id));
                AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
                for (int id : ids) update(ctx, mgr, id);
            } finally {
                if (pr != null) pr.finish();
            }
        }).start();
    }

    private static void apply(Context ctx, RemoteViews rv, int id) {
        int tx = Prefs.text(ctx, id);
        int rgb = Prefs.color(ctx, id);
        int solid = 0xFF000000 | rgb;
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        WidgetProvider.applyGlass(ctx, rv, id);
        WidgetProvider.applyWeather(ctx, rv, id, Prefs.argb(rgb, tx, 1f), Prefs.argb(rgb, tx, 0.7f), true);
        rv.setInt(R.id.btn_refresh, "setColorFilter", solid);
        rv.setInt(R.id.btn_settings, "setColorFilter", solid);
        rv.setInt(R.id.btn_refresh, "setImageAlpha", Prefs.alpha(tx));
        rv.setInt(R.id.btn_settings, "setImageAlpha", Prefs.alpha(tx));
        Intent refresh = new Intent(ctx, WeatherWidgetProvider.class).setAction(ACTION_REFRESH);
        rv.setOnClickPendingIntent(R.id.btn_refresh,
                PendingIntent.getBroadcast(ctx, 7, refresh, PendingIntent.FLAG_UPDATE_CURRENT | immutable));
        Intent cfg = new Intent(ctx, ConfigActivity.class);
        cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        cfg.setData(Uri.parse("bgawidget://config/" + id));
        rv.setOnClickPendingIntent(R.id.btn_settings,
                PendingIntent.getActivity(ctx, id, cfg, PendingIntent.FLAG_UPDATE_CURRENT | immutable));
    }

    static void update(Context ctx, AppWidgetManager mgr, int id) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_weather);
        apply(ctx, rv, id);
        mgr.updateAppWidget(id, rv);
    }

    /** Kurz neu zeichnen; fehlt fuer den (neu gewaehlten) Ort das Wetter, wird es nachgeladen. */
    static void refresh(final Context ctx, final int id) {
        update(ctx, AppWidgetManager.getInstance(ctx), id);
        new Thread(() -> {
            if (Weather.refreshIfStale(ctx, Prefs.place(ctx, id))) update(ctx, AppWidgetManager.getInstance(ctx), id);
        }).start();
    }
}
