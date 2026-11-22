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

import java.util.List;
import java.util.Set;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACTION_SET_SHOWS = "de.bergf3x.bgawidget.SET_SHOWS";
    static final String ACTION_REFRESH = "de.bergf3x.bgawidget.REFRESH";
    static final String ACTION_SET_TOPIC = "de.bergf3x.bgawidget.SET_TOPIC";
    static final String ACTION_PINNED = "de.bergf3x.bgawidget.PINNED";
    static final String ACTION_TOGGLE_WX = "de.bergf3x.bgawidget.TOGGLE_WX";
    static final String ACTION_TOGGLE_ABO = "de.bergf3x.bgawidget.TOGGLE_ABO";
    private static final int[] P = {R.id.p0, R.id.p1, R.id.p2, R.id.p3, R.id.p4};
    private static final int[] P_ON = {R.id.p0_on, R.id.p1_on, R.id.p2_on, R.id.p3_on, R.id.p4_on};
    private static final int[] P_DOT = {R.id.p0_dot, R.id.p1_dot, R.id.p2_dot, R.id.p3_dot, R.id.p4_dot};
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
        if (ACTION_PINNED.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            if (id != 0) {
                Prefs.setShowWeather(ctx, id, intent.getBooleanExtra("wx", true));
                refresh(ctx, id);
            }
            return;
        }
        if (ACTION_TOGGLE_WX.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            if (id != 0) {
                Prefs.setFlag(ctx, "wxopen_" + id, !Prefs.flag(ctx, "wxopen_" + id));
                refreshHeader(ctx, id);
            }
            return;
        }
        if (ACTION_TOGGLE_ABO.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            if (id != 0) {
                boolean on = Prefs.plusState(ctx, id) != 1;
                Prefs.setPlusAll(ctx, id, on);
                android.widget.Toast.makeText(ctx, on ? "Abo-Artikel werden angezeigt" : "Abo-Artikel sind ausgeblendet",
                        android.widget.Toast.LENGTH_SHORT).show();
                refresh(ctx, id);
            }
            return;
        }
        if (ACTION_SET_SHOWS.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            int k = intent.getIntExtra("shows", 0);
            if (id != 0) {
                boolean region = Prefs.topic(ctx, id).equals(WeatherCards.REGION);
                Prefs.setWidgetShows(ctx, id, k, region);
                refresh(ctx, id);
            }
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

        final boolean wxm = topic.startsWith(Taxonomy.WETTER);
        final int shows = Prefs.widgetShows(ctx, id);
        List<String> parents = new java.util.ArrayList<>();
        if (wxm) {
            parents.add(WeatherCards.BERG);
            parents.add(WeatherCards.REGION);
        } else {
            for (String t : topics) if (t.indexOf('/') < 0 && !t.equals(Taxonomy.OUT) && !t.equals(Taxonomy.WETTER)) parents.add(t);
        }
        String curParent = topic.isEmpty() ? "" : topic.split("/")[0];
        java.util.Set<String> newTop = wxm ? new java.util.HashSet<String>() : ListService.newTopics(ctx, id);
        for (int i = 0; i < P.length; i++) {
            String value = wxm ? (i < parents.size() ? parents.get(i) : null) : (i == 0 ? "" : (i - 1 < parents.size() ? parents.get(i - 1) : null));
            if (value == null || topics.isEmpty()) {
                rv.setViewVisibility(P[i], android.view.View.GONE);
                continue;
            }
            boolean on = wxm ? value.equals(topic) : value.equals(curParent);
            boolean hasSubs = false;
            if (!wxm) for (String t : topics) if (t.startsWith(value + "/") && !value.isEmpty()) hasSubs = true;
            int fresh = !wxm && i == 0 ? ListService.freshUnread(ctx, id) : 0;
            String label = wxm ? (i == 0 ? "Bergwetter" : "Region") : (i == 0 ? (fresh > 0 ? "Alle (" + Math.min(fresh, 99) + ")" : "Alle") : value);
            if (!wxm && on && (topic.indexOf('/') >= 0 || Prefs.topicSel(ctx, id).size() > 1)) label = Prefs.topicLabel(ctx, id);
            if (hasSubs) label = label + " ▾";
            rv.setViewVisibility(P[i], android.view.View.VISIBLE);
            rv.setViewVisibility(P_DOT[i], !wxm && i > 0 && newTop.contains(value) ? android.view.View.VISIBLE : android.view.View.GONE);
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
        if (shows == 2) rv.setViewVisibility(R.id.row_p, android.view.View.GONE);
        rv.setViewVisibility(R.id.btn_pabo, shows == 2 || !pl.chips ? android.view.View.GONE : android.view.View.VISIBLE);
        // Untere Leiste ist immer da (das Widget ist mindestens 400 dp hoch): News | Wetter | Gemerkt mit Zaehler
        rv.setViewVisibility(R.id.nav_row, android.view.View.VISIBLE);
        int savedN = 0;
        try {
            savedN = Prefs.saved(ctx).length();
        } catch (Exception ignored) {
        }
        rv.setTextViewText(R.id.nav_saved, "Gemerkt");
        rv.setViewVisibility(R.id.nav_badge, savedN > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        rv.setTextViewText(R.id.nav_badge, savedN > 99 ? "99+" : String.valueOf(savedN));
        // rot = noch Ungelesenes im Gemerkt, grau = alles gelesen (nur noch Erinnerung)
        rv.setInt(R.id.nav_badge, "setBackgroundResource", Prefs.savedUnread(ctx) > 0 ? R.drawable.badge_red : R.drawable.badge_gray);
        {
            int[] nv = {R.id.nav_news, R.id.nav_wx, R.id.nav_saved};
            for (int k = 0; k < 3; k++) {
                Pal.text(ctx, id, rv, nv[k], -1, k == shows ? 1f : 0.55f);
                // gewaehlter Reiter: erhabene Pille
                rv.setInt(nv[k], "setBackgroundResource", k == shows ? (Prefs.isLight(ctx, id) ? R.drawable.nav_on_light : R.drawable.nav_on) : 0);
                Intent ni = new Intent(ctx, WidgetProvider.class).setAction(ACTION_SET_SHOWS);
                ni.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                ni.putExtra("shows", k);
                ni.setData(Uri.parse("bgawidget://shows/" + id + "/" + k));
                rv.setOnClickPendingIntent(nv[k], PendingIntent.getBroadcast(ctx, 0, ni, PendingIntent.FLAG_UPDATE_CURRENT | immutable));
            }
        }
        Pal.filter(ctx, id, rv, R.id.btn_refresh, -1);
        Pal.filter(ctx, id, rv, R.id.btn_settings, -1);
        rv.setInt(R.id.btn_refresh, "setImageAlpha", textAlpha);
        rv.setInt(R.id.btn_settings, "setImageAlpha", textAlpha);
        Pal.text(ctx, id, rv, R.id.w_title, -1, 1f);
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
            rv.setInt(R.id.bg_border, "setImageAlpha", bg <= 0 ? 0 : Math.round(Prefs.alpha(Math.max(bg, 20)) * 0.45f));
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
        rv.setOnClickPendingIntent(R.id.btn_saved, activity(ctx, SavedActivity.class, id, 905, "saved"));
        for (int tb : new int[]{R.id.btn_cam, R.id.btn_saved}) {
            Pal.filter(ctx, id, rv, tb, cw);
            rv.setInt(tb, "setImageAlpha", Prefs.alpha(Prefs.text(ctx, id)));
        }
        rv.setViewVisibility(R.id.btn_saved, android.view.View.GONE);
        rv.setViewVisibility(R.id.btn_abo, android.view.View.GONE);
        // reines Wetter-Widget hat einen eigenen, immer sichtbaren Webcam-Knopf oben rechts
        rv.setViewVisibility(R.id.btn_cam, android.view.View.GONE);
        Pal.text(ctx, id, rv, R.id.w_desc, cw, 0.7f);
        Pal.text(ctx, id, rv, R.id.w_temp, cw, 1f);
        Pal.text(ctx, id, rv, R.id.w_hi, cw, 1f);
        Pal.text(ctx, id, rv, R.id.w_lo, cw, 0.7f);
        if (w == null) {
            rv.setInt(R.id.w_tint, "setImageAlpha", 0);
            rv.setInt(R.id.w_tint_l, "setImageAlpha", 0);
            rv.setImageViewResource(R.id.w_icon, R.drawable.w_cloud);
            rv.setTextViewText(R.id.w_desc, "Noch keine Wetterdaten · Tippen zum Aktualisieren");
            rv.setOnClickPendingIntent(R.id.w_desc, PendingIntent.getBroadcast(ctx, 0,
                    new Intent(ctx, WidgetProvider.class).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
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
        if (weatherWidget) return;

        // Nachrichten-Widget: Groessenstufe. Klein/mittel: Knoepfe und Stand in die Kopfzeile, Pfeil zum Aufklappen.
        // Kopfzeile: Name immer voll lesbar, rechts nur Aktualisieren und Einstellungen
        String sub = ok ? (stand.isEmpty() ? "" : "Aktualisiert " + stand) : "Offline" + (stand.isEmpty() ? "" : " · Stand " + stand);
        rv.setTextViewText(R.id.w_sub, sub);
        if (ok) Pal.text(ctx, id, rv, R.id.w_sub, -1, 0.7f); else rv.setTextColor(R.id.w_sub, 0xFFFFB74D);
        rv.setViewVisibility(R.id.w_sub, !sub.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        rv.setViewVisibility(R.id.btn_habo, android.view.View.GONE);
        rv.setViewVisibility(R.id.btn_hsaved, android.view.View.GONE);
        rv.setViewVisibility(R.id.btn_hcam, android.view.View.GONE);
        rv.setViewVisibility(R.id.w_stand, android.view.View.GONE);
        // Abo-Schloss links vor den Themen, Webcams rechts in der unteren Leiste
        aboButton(ctx, rv, id, R.id.btn_pabo, 0xA0, -1);
        rv.setOnClickPendingIntent(R.id.nav_cam, activity(ctx, WebcamActivity.class, id, 903, "webcam"));
        Pal.filter(ctx, id, rv, R.id.nav_cam, -1);
        rv.setInt(R.id.nav_cam, "setImageAlpha", Prefs.alpha(Prefs.text(ctx, id)));
        rv.setTextColor(R.id.w_warn_ic, Warnings.color(wl));
        rv.setViewVisibility(R.id.w_warn_ic, on && plan.warnIcon ? android.view.View.VISIBLE : android.view.View.GONE);
        boolean canToggle = on && plan.level < 2;
        rv.setViewVisibility(R.id.w_more, canToggle ? android.view.View.VISIBLE : android.view.View.GONE);
        if (canToggle) {
            rv.setImageViewResource(R.id.w_more, plan.open ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
            Pal.filter(ctx, id, rv, R.id.w_more, cw);
            Intent tg = new Intent(ctx, WidgetProvider.class).setAction(ACTION_TOGGLE_WX)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("bgawidget://togglewx/" + id));
            PendingIntent tp = PendingIntent.getBroadcast(ctx, id * 1000 + 906, tg,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            rv.setOnClickPendingIntent(R.id.w_more, tp);
            rv.setOnClickPendingIntent(R.id.w_icon, tp);
            rv.setOnClickPendingIntent(R.id.w_warn_ic, tp);
        } else {
            rv.setOnClickPendingIntent(R.id.w_icon, hourly);
        }
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

    /** Abo-Schalter im Widget: geschlossenes Schloss = Abo-Artikel sichtbar, offenes (blasser) = ausgeblendet. */
    private static void aboButton(Context ctx, RemoteViews rv, int id, int viewId, int dimAlpha, int override) {
        int ps = Prefs.plusState(ctx, id);
        rv.setImageViewResource(viewId, ps == 0 ? R.drawable.ic_lock_open : R.drawable.ic_lock);
        Pal.filter(ctx, id, rv, viewId, override);
        int full = Prefs.alpha(Prefs.text(ctx, id));
        rv.setInt(viewId, "setImageAlpha", ps == 0 ? Math.min(full, dimAlpha) : full);
        rv.setContentDescription(viewId, ps == 0 ? "Abo-Artikel ausgeblendet, antippen zum Anzeigen"
                : ps == 1 ? "Abo-Artikel sichtbar, antippen zum Ausblenden" : "Abo-Artikel teilweise sichtbar, antippen zum Anzeigen");
        Intent tg = new Intent(ctx, WidgetProvider.class).setAction(ACTION_TOGGLE_ABO)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("bgawidget://toggleabo/" + id + "/" + viewId));
        rv.setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(ctx, id * 1000 + 907 + (viewId & 7), tg,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
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
