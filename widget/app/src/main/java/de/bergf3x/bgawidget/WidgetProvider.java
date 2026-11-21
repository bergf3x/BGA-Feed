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
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            android.widget.Toast.makeText(ctx, "Aktualisiere …", android.widget.Toast.LENGTH_SHORT).show();
            Updater.runAsync(ctx, true, goAsync());
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
        List<String> topics = Taxonomy.topics(ctx, id);
        String topic = Prefs.topic(ctx, id);
        if (!topic.isEmpty() && !topics.contains(topic)) {
            topic = "";
            Prefs.setTopic(ctx, id, "");
        }
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        int fg = Prefs.argb(rgb, tx, 1f);
        int dark = 0xFF1B1F22;

        applyGlass(ctx, rv, id);
        Plan pl = Plan.of(ctx, id, false);
        rv.setViewVisibility(R.id.row_p, pl.chips ? android.view.View.VISIBLE : android.view.View.GONE);
        rv.setViewVisibility(R.id.list_box, pl.list ? android.view.View.VISIBLE : android.view.View.GONE);
        Pal.text(ctx, id, rv, R.id.empty, -1, 1f);
        boolean light = Prefs.isLight(ctx, id);

        applyWeather(ctx, rv, id, fg, Prefs.argb(rgb, tx, 0.7f), false);

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
            int fresh = i == 0 ? ListService.freshUnread(ctx, id) : 0;
            String label = i == 0 ? (fresh > 0 ? "Alle (" + Math.min(fresh, 99) + ")" : "Alle") : value;
            if (on && (topic.indexOf('/') >= 0 || Prefs.topicSel(ctx, id).size() > 1)) label = Prefs.topicLabel(ctx, id);
            if (hasSubs) label = label + " ▾";
            rv.setViewVisibility(P[i], android.view.View.VISIBLE);
            rv.setTextViewText(P_T[i], Style.font(ctx, id, label));
            rv.setInt(P[i], "setBackgroundResource", Pal.chipBg(ctx, id));
            int acc = Prefs.accent(ctx, id);
            if (acc >= 0) rv.setInt(P_ON[i], "setColorFilter", 0xFF000000 | acc);
            else Pal.pair(ctx, id, rv, P_ON[i], "setColorFilter", 0xFF2A2F36, 0xFFF2F2F2);
            rv.setViewVisibility(P_ON[i], on ? android.view.View.VISIBLE : android.view.View.GONE);
            if (on && acc >= 0) rv.setTextColor(P_T[i], Pal.onColor(acc));
            else if (on) Pal.textPair(ctx, id, rv, P_T[i], 0xFFFFFFFF, dark);
            else Pal.text(ctx, id, rv, P_T[i], -1, 1f);
            rv.setOnClickPendingIntent(P[i], hasSubs
                    ? topicPending(ctx, id, 100 + i, value, true, immutable)
                    : topicBroadcast(ctx, id, 100 + i, value, immutable));
        }
        Pal.filter(ctx, id, rv, R.id.btn_refresh, -1);
        Pal.filter(ctx, id, rv, R.id.btn_settings, -1);
        rv.setInt(R.id.btn_refresh, "setImageAlpha", textAlpha);
        rv.setInt(R.id.btn_settings, "setImageAlpha", textAlpha);
    }

    static final int[] FC_D = {R.id.fc1_d, R.id.fc2_d, R.id.fc3_d};
    static final int[] FC_I = {R.id.fc1_i, R.id.fc2_i, R.id.fc3_i};
    static final int[] FC_T = {R.id.fc1_t, R.id.fc2_t, R.id.fc3_t};
    static final int[] FC_BAR = {R.id.fc1_bar, R.id.fc2_bar, R.id.fc3_bar};
    static final int BAR_DP = 56;

    /** Glas-Hintergrund passend zur aktuellen Widget-Groesse zeichnen. */
    static void applyGlass(Context ctx, RemoteViews rv, int id) {
        int bg = Prefs.bg(ctx, id);
        boolean light = Prefs.isLight(ctx, id);
        android.os.Bundle o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id);
        float d = ctx.getResources().getDisplayMetrics().density;
        float sc = 0.55f;
        int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        int w = Math.round(wDp * d * sc), h = Math.round(hDp * d * sc);
        android.graphics.Bitmap bmp = GlassBg.get(w, h, bg, light, d * sc, BgStyle.colors(ctx, id, light), BgStyle.radiusDp(ctx, id));
        // zwei Flaechen im Layout: dunkle und helle; nur die zum Design passende ist sichtbar
        int on = light ? R.id.bg_fill_l : R.id.bg_fill;
        int off = light ? R.id.bg_fill : R.id.bg_fill_l;
        rv.setFloat(R.id.bg_fill, "setAlpha", light ? 0f : 1f);
        rv.setFloat(R.id.bg_fill_l, "setAlpha", light ? 1f : 0f);
        rv.setInt(off, "setImageAlpha", 0);
        if (bmp != null) {
            rv.setImageViewBitmap(on, bmp);
            rv.setInt(on, "setImageAlpha", 255);
            rv.setInt(R.id.bg_border, "setImageAlpha", 0);
        } else {
            rv.setInt(on, "setImageAlpha", Prefs.alpha(bg));
            rv.setInt(R.id.bg_border, "setImageAlpha", Math.round(Prefs.alpha(Math.max(bg, 20)) * 0.45f));
        }
    }

    private static PendingIntent activity(Context ctx, Class<?> cls, int id, int slot, String path) {
        Intent i = new Intent(ctx, cls);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.setData(Uri.parse("bgawidget://" + path + "/" + id));
        return PendingIntent.getActivity(ctx, id * 1000 + slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    /** Wetterbereich: aktueller Stand, Hoechst/Tiefst, Zusatzzeile, Warnung, Vorschau. weatherWidget = eigenes Wetter-Widget. */
    static void applyWeather(Context ctx, RemoteViews rv, int id, int fg, int fgSoft, boolean weatherWidget) {
        int cw = Prefs.colorWx(ctx, id);
        int cw2 = Prefs.colorWx2(ctx, id);
        if (cw >= 0) {
            int txw = Prefs.text(ctx, id);
            fg = Prefs.argb(cw, txw, 1f);
            fgSoft = Prefs.argb(cw, txw, 0.7f);
        }
        int place = Prefs.place(ctx, id);
        Weather w = Weather.load(ctx, place);
        boolean light = Prefs.isLight(ctx, id);
        boolean on = weatherWidget || Prefs.showWeather(ctx, id);
        int visOn = on ? android.view.View.VISIBLE : android.view.View.GONE;
        rv.setViewVisibility(R.id.w_icon, visOn);
        rv.setViewVisibility(R.id.w_temp, visOn);
        rv.setViewVisibility(R.id.w_hilo, visOn);
        rv.setViewVisibility(R.id.w_box, on ? android.view.View.VISIBLE : android.view.View.INVISIBLE);

        Pal.set(ctx, id, rv, R.id.w_place, Style.font(ctx, id, Weather.PLACES[place] + " ▾"), cw, cw2, 1f);
                rv.setOnClickPendingIntent(R.id.w_box, activity(ctx, PlaceActivity.class, id, 900, "place"));
        PendingIntent hourly = activity(ctx, HourlyActivity.class, id, 902, "hourly");
        rv.setOnClickPendingIntent(R.id.w_temp, hourly);
        rv.setOnClickPendingIntent(R.id.w_hi, hourly);
        rv.setOnClickPendingIntent(R.id.w_lo, hourly);
        rv.setOnClickPendingIntent(R.id.btn_cam, activity(ctx, WebcamActivity.class, id, 903, "webcam"));
        rv.setOnClickPendingIntent(R.id.btn_search, activity(ctx, SearchActivity.class, id, 904, "search"));
        rv.setOnClickPendingIntent(R.id.btn_saved, activity(ctx, SavedActivity.class, id, 905, "saved"));
        for (int tb : new int[]{R.id.btn_cam, R.id.btn_search, R.id.btn_saved}) {
            Pal.filter(ctx, id, rv, tb, cw);
            rv.setInt(tb, "setImageAlpha", Prefs.alpha(Prefs.text(ctx, id)));
        }
        rv.setViewVisibility(R.id.btn_search, weatherWidget ? android.view.View.GONE : android.view.View.VISIBLE);
        rv.setViewVisibility(R.id.btn_saved, weatherWidget ? android.view.View.GONE : android.view.View.VISIBLE);
        Pal.text(ctx, id, rv, R.id.w_desc, cw, 0.7f);
        Pal.text(ctx, id, rv, R.id.w_temp, cw, 1f);
        Pal.text(ctx, id, rv, R.id.w_hi, cw, 1f);
        Pal.text(ctx, id, rv, R.id.w_lo, cw, 0.7f);
        if (w == null) {
            rv.setInt(R.id.w_tint, "setImageAlpha", 0);
            rv.setInt(R.id.w_tint_l, "setImageAlpha", 0);
            rv.setImageViewResource(R.id.w_icon, R.drawable.w_cloud);
            rv.setTextViewText(R.id.w_desc, "Wetter lädt …");
            rv.setTextViewText(R.id.w_temp, "–");
            rv.setTextViewText(R.id.w_hi, "");
            rv.setTextViewText(R.id.w_lo, "");
        } else {
            int th = Prefs.theme(ctx, id);
            android.graphics.Bitmap bgD = on && th != 1 ? WeatherBg.get(ctx, w.code, w.day, false, Prefs.place(ctx, id)) : null;
            android.graphics.Bitmap bgL = on && th != 0 ? WeatherBg.get(ctx, w.code, w.day, true, Prefs.place(ctx, id)) : null;
            rv.setInt(R.id.w_tint, "setColorFilter", 0);
            rv.setInt(R.id.w_tint_l, "setColorFilter", 0);
            rv.setFloat(R.id.w_tint, "setAlpha", th == 1 ? 0f : 1f);
            rv.setFloat(R.id.w_tint_l, "setAlpha", th == 1 ? 1f : 0f);
            if (bgD != null) {
                rv.setImageViewBitmap(R.id.w_tint, bgD);
                rv.setInt(R.id.w_tint, "setImageAlpha", 255);
            } else {
                rv.setInt(R.id.w_tint, "setImageAlpha", 0);
            }
            if (bgL != null) {
                rv.setImageViewBitmap(R.id.w_tint_l, bgL);
                rv.setInt(R.id.w_tint_l, "setImageAlpha", 255);
            } else {
                rv.setInt(R.id.w_tint_l, "setImageAlpha", 0);
            }
            rv.setImageViewResource(R.id.w_icon, Weather.icon(w.code, w.day));
            Pal.set(ctx, id, rv, R.id.w_desc, Style.font(ctx, id, Weather.text(w.code, w.day)), cw, cw2, 0.7f);
            Pal.set(ctx, id, rv, R.id.w_temp, Weather.deg(w.temp), cw, cw2, 1f);
            Pal.set(ctx, id, rv, R.id.w_hi, w.days > 0 ? "↑ " + Weather.deg(w.hi[0]) : "", cw, cw2, 1f);
            Pal.set(ctx, id, rv, R.id.w_lo, w.days > 0 ? "↓ " + Weather.deg(w.lo[0]) : "", cw, cw2, 0.7f);
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
            Pal.text(ctx, id, rv, FC_D[i], cw, 0.7f);
            Pal.text(ctx, id, rv, FC_T[i], cw, 1f);
            Pal.set(ctx, id, rv, FC_D[i], ok ? w.dayName[d] : "", cw, cw2, 0.7f);
            Pal.set(ctx, id, rv, FC_T[i], ok ? Weather.deg(w.hi[d]) + " / " + Weather.deg(w.lo[d]) : "", cw, cw2, 1f);
            rv.setImageViewResource(FC_I[i], ok ? Weather.icon(w.dayCode[d], true) : R.drawable.w_cloud);
            rv.setViewVisibility(FC_I[i], ok ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        }
        Plan plan = Plan.of(ctx, id, weatherWidget);
        rv.setViewVisibility(R.id.fc_box, on && plan.fc ? android.view.View.VISIBLE : android.view.View.GONE);
        String info = w == null ? "" : w.info();
        Pal.set(ctx, id, rv, R.id.w_info, Style.font(ctx, id, info), cw, cw2, 0.7f);
        boolean infoOn = on && plan.infoLines > 0 && !info.isEmpty();
        if (infoOn) rv.setInt(R.id.w_info, "setMaxLines", plan.infoLines);
        rv.setViewVisibility(R.id.w_info, infoOn ? android.view.View.VISIBLE : android.view.View.GONE);
        rv.setViewVisibility(R.id.tools, on && plan.tools ? android.view.View.VISIBLE : android.view.View.GONE);

        // amtliche DWD-Warnung (nur wenn aktuell)
        int wl = Prefs.warnLevel(ctx);
        String wt = Prefs.warnText(ctx);
        boolean showWarn = on && plan.warn;
        rv.setTextViewText(R.id.w_warn, "⚠ " + wt);
        rv.setTextColor(R.id.w_warn, Warnings.color(wl));
        rv.setViewVisibility(R.id.w_warn, showWarn ? android.view.View.VISIBLE : android.view.View.GONE);

        // Stand + Offline-Hinweis
        long t = weatherWidget ? Prefs.weatherTime(ctx, place) : Prefs.newsTime(ctx);
        String stand = t == 0 ? "" : new java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(t));
        boolean ok = weatherWidget || Prefs.newsOk(ctx);
        String line;
        String sw = weatherWidget ? "" : Prefs.srcWarn(ctx);
        if (ok) line = stand.isEmpty() ? "" : "Letzte Aktualisierung " + stand + (sw.isEmpty() ? "" : "  ·  " + sw);
        else line = "Offline – " + (stand.isEmpty() ? "keine Daten" : "Letzte Aktualisierung " + stand);
        rv.setTextViewText(R.id.w_stand, line);
        if (ok) Pal.text(ctx, id, rv, R.id.w_stand, cw, 0.7f); else rv.setTextColor(R.id.w_stand, 0xFFFFB74D);
        rv.setViewVisibility(R.id.w_stand, line.isEmpty() || !plan.tools ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, android.os.Bundle opts) {
        refreshHeader(ctx, id);
    }

    /** Nur die Kopfzeile (Wetter, Chips, Farben) neu zeichnen, ohne die Liste neu zu laden. */
    static void refreshHeader(Context ctx, int id) {
        if (id <= 0) return;
        if (isWeatherWidget(ctx, id)) {
            WeatherWidgetProvider.refresh(ctx, id);
            return;
        }
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        applyHeader(ctx, rv, id);
        AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, rv);
    }

    static boolean isWeatherWidget(Context ctx, int id) {
        try {
            android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id);
            return info != null && WeatherWidgetProvider.class.getName().equals(info.provider.getClassName());
        } catch (Exception e) {
            return false;
        }
    }

    /** Nach Aenderung der Einstellungen: das richtige Widget komplett neu zeichnen. */
    static void updateAny(Context ctx, int id) {
        if (id <= 0) return;
        if (isWeatherWidget(ctx, id)) {
            WeatherWidgetProvider.update(ctx, AppWidgetManager.getInstance(ctx), id);
        } else {
            update(ctx, AppWidgetManager.getInstance(ctx), id);
            refresh(ctx, id);
        }
    }

    /** Weiche Aktualisierung nur der Kopfzeile + Liste, ohne das ganze Widget neu aufzubauen. */
    static void refresh(Context ctx, int id) {
        if (id <= 0) return;
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
