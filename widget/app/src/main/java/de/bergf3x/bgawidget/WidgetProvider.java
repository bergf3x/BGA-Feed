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

import java.util.List;
import java.util.Set;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "de.bergf3x.bgawidget.REFRESH";
    static final String ACTION_SET_TOPIC = "de.bergf3x.bgawidget.SET_TOPIC";
    private static final int[] P = {R.id.p0, R.id.p1, R.id.p2, R.id.p3, R.id.p4};
    private static final int[] P_ON = {R.id.p0_on, R.id.p1_on, R.id.p2_on, R.id.p3_on, R.id.p4_on};
    private static final int[] P_T = {R.id.p0_t, R.id.p1_t, R.id.p2_t, R.id.p3_t, R.id.p4_t};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            Prefs.invalidateWeather(ctx);
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
            for (int id : ids) mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
            return;
        }
        if (ACTION_SET_TOPIC.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            String t = intent.getStringExtra("topic");
            if (id != 0) {
                Prefs.setTopic(ctx, id, t == null ? "" : t);
                refresh(ctx, id);
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) Prefs.remove(ctx, id);
    }

    /** Kopfzeile (Farben, Titel, Chips) in ein RemoteViews schreiben. */
    private static void applyHeader(Context ctx, RemoteViews rv, int id) {
        int bg = Prefs.bg(ctx, id);
        int tx = Prefs.text(ctx, id);
        int rgb = Prefs.color(ctx, id);
        int textAlpha = Prefs.alpha(tx);
        int solid = 0xFF000000 | rgb;

        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, id));
        List<String> topics = Catalog.topicsFor(all, selected);
        String topic = Prefs.topic(ctx, id);
        if (!topic.isEmpty() && !topics.contains(topic)) {
            topic = "";
            Prefs.setTopic(ctx, id, "");
        }
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        int fg = Prefs.argb(rgb, tx, 1f);
        int dark = 0xFF1B1F22;

        rv.setInt(R.id.bg_fill, "setImageAlpha", Prefs.alpha(bg));
        rv.setInt(R.id.bg_border, "setImageAlpha", Math.round(Prefs.alpha(Math.max(bg, 20)) * 0.45f));
        rv.setTextColor(R.id.empty, fg);

        applyWeather(ctx, rv, id, fg, Prefs.argb(rgb, tx, 0.7f));

        List<String> parents = new java.util.ArrayList<>();
        for (String t : topics) if (t.indexOf('/') < 0) parents.add(t);
        String curParent = topic.isEmpty() ? "" : topic.split("/")[0];
        for (int i = 0; i < P.length; i++) {
            String value = i == 0 ? "" : (i - 1 < parents.size() ? parents.get(i - 1) : null);
            if (value == null || topics.isEmpty()) {
                rv.setViewVisibility(P[i], android.view.View.GONE);
                continue;
            }
            boolean on = value.equals(curParent);
            boolean hasSubs = false;
            for (String t : topics) if (t.startsWith(value + "/") && !value.isEmpty()) hasSubs = true;
            String label = i == 0 ? "Alle" : value;
            if (on && topic.indexOf('/') >= 0) label = topic.substring(topic.indexOf('/') + 1);
            else if (hasSubs) label = label + " ▾";
            rv.setViewVisibility(P[i], android.view.View.VISIBLE);
            rv.setTextViewText(P_T[i], label);
            rv.setViewVisibility(P_ON[i], on ? android.view.View.VISIBLE : android.view.View.GONE);
            rv.setTextColor(P_T[i], on ? dark : fg);
            rv.setOnClickPendingIntent(P[i], hasSubs
                    ? topicPending(ctx, id, 100 + i, value, true, immutable)
                    : topicBroadcast(ctx, id, 100 + i, value, immutable));
        }
        rv.setInt(R.id.btn_refresh, "setColorFilter", solid);
        rv.setInt(R.id.btn_settings, "setColorFilter", solid);
        rv.setInt(R.id.btn_refresh, "setImageAlpha", textAlpha);
        rv.setInt(R.id.btn_settings, "setImageAlpha", textAlpha);
    }

    private static final int[] FC_D = {R.id.fc1_d, R.id.fc2_d, R.id.fc3_d};
    private static final int[] FC_I = {R.id.fc1_i, R.id.fc2_i, R.id.fc3_i};
    private static final int[] FC_T = {R.id.fc1_t, R.id.fc2_t, R.id.fc3_t};
    private static final int[] FC_BAR = {R.id.fc1_bar, R.id.fc2_bar, R.id.fc3_bar};
    private static final int BAR_DP = 56;

    /** Wetterbereich oben: aktueller Stand, Hoechst/Tiefst, Vorhersage (nur bei genug Hoehe). */
    private static void applyWeather(Context ctx, RemoteViews rv, int id, int fg, int fgSoft) {
        int place = Prefs.place(ctx, id);
        Weather w = Weather.load(ctx, place);
        rv.setTextViewText(R.id.w_place, Weather.PLACES[place] + " ▾");
        rv.setTextColor(R.id.w_place, fg);
        Intent pi = new Intent(ctx, PlaceActivity.class);
        pi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        pi.setData(Uri.parse("bgawidget://place/" + id));
        rv.setOnClickPendingIntent(R.id.w_box, PendingIntent.getActivity(ctx, id * 1000 + 900, pi,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
        rv.setTextColor(R.id.w_desc, fgSoft);
        rv.setTextColor(R.id.w_temp, fg);
        rv.setTextColor(R.id.w_hi, fg);
        rv.setTextColor(R.id.w_lo, fgSoft);
        if (w == null) {
            rv.setInt(R.id.w_tint, "setImageAlpha", 0);
            rv.setImageViewResource(R.id.w_icon, R.drawable.w_cloud);
            rv.setTextViewText(R.id.w_desc, "Wetter lädt …");
            rv.setTextViewText(R.id.w_temp, "–");
            rv.setTextViewText(R.id.w_hi, "");
            rv.setTextViewText(R.id.w_lo, "");
        } else {
            rv.setInt(R.id.w_tint, "setColorFilter", Weather.tint(w.code, w.day));
            rv.setInt(R.id.w_tint, "setImageAlpha", 58);
            rv.setImageViewResource(R.id.w_icon, Weather.icon(w.code, w.day));
            rv.setTextViewText(R.id.w_desc, Weather.text(w.code, w.day));
            rv.setTextViewText(R.id.w_temp, Weather.deg(w.temp));
            rv.setTextViewText(R.id.w_hi, w.days > 0 ? "↑ " + Weather.deg(w.hi[0]) : "");
            rv.setTextViewText(R.id.w_lo, w.days > 0 ? "↓ " + Weather.deg(w.lo[0]) : "");
        }
        // gemeinsame Skala fuer die Temperaturbalken (heute + 3 Tage)
        int gMin = 100, gMax = -100;
        if (w != null) {
            for (int d = 0; d < w.days; d++) {
                gMin = Math.min(gMin, w.lo[d]);
                gMax = Math.max(gMax, w.hi[d]);
            }
        }
        float dens = ctx.getResources().getDisplayMetrics().density;
        int barPx = Math.round(BAR_DP * dens);
        int minBar = Math.round(6 * dens);
        for (int i = 0; i < 3; i++) {
            int d = i + 1;
            boolean ok = w != null && d < w.days;
            if (ok && gMax > gMin) {
                float range = gMax - gMin;
                int left = Math.round((w.lo[d] - gMin) / range * barPx);
                int right = Math.round((gMax - w.hi[d]) / range * barPx);
                if (barPx - left - right < minBar) right = Math.max(0, barPx - left - minBar);
                rv.setViewPadding(FC_BAR[i], left, 0, right, 0);
            } else {
                rv.setViewPadding(FC_BAR[i], 0, 0, 0, 0);
            }
            rv.setViewVisibility(FC_BAR[i], ok ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
            rv.setTextColor(FC_D[i], fgSoft);
            rv.setTextColor(FC_T[i], fg);
            rv.setTextViewText(FC_D[i], ok ? w.dayName[d] : "");
            rv.setTextViewText(FC_T[i], ok ? Weather.deg(w.hi[d]) + " / " + Weather.deg(w.lo[d]) : "");
            rv.setImageViewResource(FC_I[i], ok ? Weather.icon(w.dayCode[d], true) : R.drawable.w_cloud);
            rv.setViewVisibility(FC_I[i], ok ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        }
        int minH = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300);
        rv.setViewVisibility(R.id.fc_box, minH >= 260 ? android.view.View.VISIBLE : android.view.View.GONE);
        String info = w == null ? "" : w.info();
        rv.setTextViewText(R.id.w_info, info);
        rv.setTextColor(R.id.w_info, fgSoft);
        rv.setViewVisibility(R.id.w_info, minH >= 190 && !info.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        // Stand der Nachrichten + Offline-Hinweis
        long t = Prefs.newsTime(ctx);
        String stand = t == 0 ? "" : new java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(t));
        boolean ok = Prefs.newsOk(ctx);
        String line;
        if (ok) line = stand.isEmpty() ? "" : "Stand " + stand;
        else line = "Offline – " + (stand.isEmpty() ? "keine Daten" : "Stand " + stand);
        rv.setTextViewText(R.id.w_stand, line);
        rv.setTextColor(R.id.w_stand, ok ? fgSoft : 0xFFFFB74D);
        rv.setViewVisibility(R.id.w_stand, line.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, android.os.Bundle opts) {
        refreshHeader(ctx, id);
    }

    /** Nur die Kopfzeile (Wetter, Chips, Farben) neu zeichnen, ohne die Liste neu zu laden. */
    static void refreshHeader(Context ctx, int id) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        applyHeader(ctx, rv, id);
        AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, rv);
    }

    /** Weiche Aktualisierung nur der Kopfzeile + Liste, ohne das ganze Widget neu aufzubauen. */
    static void refresh(Context ctx, int id) {
        refreshHeader(ctx, id);
        AppWidgetManager.getInstance(ctx).notifyAppWidgetViewDataChanged(id, R.id.list);
    }

    static void update(Context ctx, AppWidgetManager mgr, int id) {
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        applyHeader(ctx, rv, id);

        Intent svc = new Intent(ctx, ListService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.list, svc);
        rv.setEmptyView(R.id.list, R.id.empty);

        Intent open = new Intent(ctx, PreviewActivity.class);
        rv.setPendingIntentTemplate(R.id.list,
                PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT | mutable));

        Intent refresh = new Intent(ctx, WidgetProvider.class).setAction(ACTION_REFRESH);
        rv.setOnClickPendingIntent(R.id.btn_refresh,
                PendingIntent.getBroadcast(ctx, 0, refresh, PendingIntent.FLAG_UPDATE_CURRENT | immutable));

        Intent cfg = new Intent(ctx, ConfigActivity.class);
        cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        rv.setOnClickPendingIntent(R.id.btn_settings,
                PendingIntent.getActivity(ctx, id, cfg, PendingIntent.FLAG_UPDATE_CURRENT | immutable));

        mgr.updateAppWidget(id, rv);
        mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
    }

    /** Direkt wechselndes Thema ohne Fenster (kein Start-Effekt des Launchers). */
    private static PendingIntent topicBroadcast(Context ctx, int id, int slot, String topic, int immutable) {
        Intent i = new Intent(ctx, WidgetProvider.class).setAction(ACTION_SET_TOPIC);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra("topic", topic);
        i.setData(Uri.parse("bgawidget://topicset/" + id + "/" + slot));
        return PendingIntent.getBroadcast(ctx, id * 1000 + slot, i, PendingIntent.FLAG_UPDATE_CURRENT | immutable);
    }

    private static PendingIntent topicPending(Context ctx, int id, int slot, String topic, boolean menu, int immutable) {
        Intent i = new Intent(ctx, TopicActivity.class);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra("topic", topic);
        i.putExtra("menu", menu);
        i.setData(Uri.parse("bgawidget://topic/" + id + "/" + slot));
        return PendingIntent.getActivity(ctx, id * 1000 + slot, i, PendingIntent.FLAG_UPDATE_CURRENT | immutable);
    }
}
