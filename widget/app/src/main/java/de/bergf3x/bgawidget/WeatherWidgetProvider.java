/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
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
        Scheduler.schedule(ctx);
        Updater.runAsync(ctx, false, goAsync());
    }

    @Override
    public void onEnabled(Context ctx) {
        Scheduler.schedule(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        Scheduler.schedule(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            android.widget.Toast.makeText(ctx, "Aktualisiere …", android.widget.Toast.LENGTH_SHORT).show();
            Updater.runAsync(ctx, true, goAsync());
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

    private static void apply(Context ctx, RemoteViews rv, int id) {
        int tx = Prefs.text(ctx, id);
        int rgb = Prefs.color(ctx, id);
        int solid = 0xFF000000 | rgb;
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        WidgetProvider.applyGlass(ctx, rv, id);
        WidgetProvider.applyWeather(ctx, rv, id, Prefs.argb(rgb, tx, 1f), Prefs.argb(rgb, tx, 0.7f), true);
        applyDual(ctx, rv, id);
        Pal.filter(ctx, id, rv, R.id.btn_refresh, -1);
        Pal.filter(ctx, id, rv, R.id.btn_settings, -1);
        Pal.filter(ctx, id, rv, R.id.btn_wcam, -1);
        rv.setTextViewText(R.id.w_title, "BGL Wetter");
        Pal.text(ctx, id, rv, R.id.w_title, -1, 1f);
        rv.setInt(R.id.btn_wcam, "setImageAlpha", Prefs.alpha(tx));
        Intent cam = new Intent(ctx, WebcamActivity.class);
        cam.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        cam.setData(Uri.parse("bgawidget://webcam/" + id));
        rv.setOnClickPendingIntent(R.id.btn_wcam,
                PendingIntent.getActivity(ctx, 900000 + id, cam, PendingIntent.FLAG_UPDATE_CURRENT | immutable));
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


    /** Ab dieser Hoehe (dp) passen zwei Orte nebeneinander sauber ins Widget. */
    static final int DUAL_MIN_DP = 290;

    static boolean dual(Context ctx, int id) {
        return Prefs.place2(ctx, id) >= 0 && Plan.heightDp(ctx, id) >= DUAL_MIN_DP;
    }

    /** Zwei Orte als getrennte Karten nebeneinander; Warnung gemeinsam darueber. */
    private static void applyDual(Context ctx, RemoteViews rv, int id) {
        boolean on = dual(ctx, id);
        rv.setViewVisibility(R.id.w_dual, on ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!on) {
            rv.setViewVisibility(R.id.w_warn2, android.view.View.GONE);
            return;
        }
        rv.setViewVisibility(R.id.w_frame, android.view.View.GONE);
        rv.setViewVisibility(R.id.w_info, android.view.View.GONE);
        rv.setViewVisibility(R.id.fc_box, android.view.View.GONE);
        boolean warn = Prefs.showWarn(ctx, id) && Prefs.warnLevel(ctx) >= 2 && Prefs.warnText(ctx).length() > 0
                && System.currentTimeMillis() - Prefs.warnTime(ctx) < 6L * 3600000;
        rv.setViewVisibility(R.id.w_warn, android.view.View.GONE);
        rv.setViewVisibility(R.id.w_warn2, warn ? android.view.View.VISIBLE : android.view.View.GONE);
        if (warn) {
            rv.setTextViewText(R.id.w_warn2, "\u26A0 " + Prefs.warnText(ctx));
            rv.setTextColor(R.id.w_warn2, Warnings.color(Prefs.warnLevel(ctx)));
        }
        int cw = Prefs.colorWx(ctx, id), cw2 = Prefs.colorWx2(ctx, id);
        int tx = Prefs.text(ctx, id);
        int rgb = Prefs.color(ctx, id);
        int c1 = cw >= 0 ? Prefs.argb(cw, tx, 1f) : Prefs.argb(rgb, tx, 1f);
        int cx = cw >= 0 ? cw : -1;
        boolean tall = Plan.heightDp(ctx, id) >= 330;
        int[][] ids = {
            {R.id.c1_place, R.id.c1_desc, R.id.c1_icon, R.id.c1_temp, R.id.c1_hi, R.id.c1_lo, R.id.c1_info,
                R.id.c1_f1_d, R.id.c1_f1_i, R.id.c1_f1_t, R.id.c1_f2_d, R.id.c1_f2_i, R.id.c1_f2_t,
                R.id.c1_f3_d, R.id.c1_f3_i, R.id.c1_f3_t, R.id.c1_box, R.id.c1_main},
            {R.id.c2_place, R.id.c2_desc, R.id.c2_icon, R.id.c2_temp, R.id.c2_hi, R.id.c2_lo, R.id.c2_info,
                R.id.c2_f1_d, R.id.c2_f1_i, R.id.c2_f1_t, R.id.c2_f2_d, R.id.c2_f2_i, R.id.c2_f2_t,
                R.id.c2_f3_d, R.id.c2_f3_i, R.id.c2_f3_t, R.id.c2_box, R.id.c2_main}};
        int[] places = {Prefs.place(ctx, id), Prefs.place2(ctx, id)};
        for (int n = 0; n < 2; n++) {
            int[] v = ids[n];
            Weather w = Weather.load(ctx, places[n]);
            Pal.set(ctx, id, rv, v[0], Style.font(ctx, id, Weather.PLACES[places[n]] + " \u25BE"), cw, cw2, 1f);
            Pal.text(ctx, id, rv, v[1], cw, 0.7f);
            Pal.text(ctx, id, rv, v[3], cw, 1f);
            Pal.text(ctx, id, rv, v[4], cw, 1f);
            Pal.text(ctx, id, rv, v[5], cw, 0.7f);
            Pal.text(ctx, id, rv, v[6], cw, 0.7f);
            if (w == null) {
                rv.setTextViewText(v[1], "Noch keine Daten");
                rv.setTextViewText(v[3], "\u2013");
                rv.setTextViewText(v[4], "");
                rv.setTextViewText(v[5], "");
                rv.setTextViewText(v[6], "");
                rv.setImageViewResource(v[2], R.drawable.w_cloud);
                for (int k = 0; k < 3; k++) {
                    rv.setTextViewText(v[7 + k * 3], "");
                    rv.setTextViewText(v[9 + k * 3], "");
                    rv.setViewVisibility(v[8 + k * 3], android.view.View.INVISIBLE);
                }
            } else {
                rv.setTextViewText(v[1], Style.font(ctx, id, Weather.text(w.code, w.day)));
                rv.setImageViewResource(v[2], Weather.icon(w.code, w.day));
                rv.setTextViewText(v[3], Weather.deg(w.temp));
                rv.setTextViewText(v[4], w.days > 0 ? "\u2191 " + Weather.deg(w.hi[0]) : "");
                rv.setTextViewText(v[5], w.days > 0 ? "\u2193 " + Weather.deg(w.lo[0]) : "");
                rv.setTextViewText(v[6], Style.font(ctx, id, w.info()));
                for (int k = 0; k < 3; k++) {
                    int d = k + 1;
                    boolean ok = d < w.days;
                    Pal.text(ctx, id, rv, v[7 + k * 3], cw, 0.7f);
                    Pal.text(ctx, id, rv, v[9 + k * 3], cw, 1f);
                    rv.setTextViewText(v[7 + k * 3], ok ? w.dayName[d] : "");
                    rv.setTextViewText(v[9 + k * 3], ok ? Weather.deg(w.hi[d]) + " / " + Weather.deg(w.lo[d]) : "");
                    rv.setImageViewResource(v[8 + k * 3], ok ? Weather.icon(w.dayCode[d], true) : R.drawable.w_cloud);
                    rv.setViewVisibility(v[8 + k * 3], ok ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
                }
            }
            rv.setInt(v[6], "setMaxLines", tall ? 4 : 2);
            rv.setImageViewResource(v[2], w == null ? R.drawable.w_cloud : Weather.icon(w.code, w.day));
            Intent pi = new Intent(ctx, PlaceActivity.class);
            pi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            pi.putExtra("second", n == 1);
            pi.setData(Uri.parse("bgawidget://place" + n + "/" + id));
            int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
            rv.setOnClickPendingIntent(v[16], PendingIntent.getActivity(ctx, 910000 + id * 2 + n, pi,
                    PendingIntent.FLAG_UPDATE_CURRENT | imm));
        }
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
            boolean a = Weather.refreshIfStale(ctx, Prefs.place(ctx, id));
            boolean b = Prefs.place2(ctx, id) >= 0 && Weather.refreshIfStale(ctx, Prefs.place2(ctx, id));
            if (a || b) update(ctx, AppWidgetManager.getInstance(ctx), id);
        }).start();
    }
}
