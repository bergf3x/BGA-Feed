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
    private static final int[] P = {R.id.p0, R.id.p1, R.id.p2, R.id.p3, R.id.p4};
    private static final int[] P_ON = {R.id.p0_on, R.id.p1_on, R.id.p2_on, R.id.p3_on, R.id.p4_on};
    private static final int[] P_T = {R.id.p0_t, R.id.p1_t, R.id.p2_t, R.id.p3_t, R.id.p4_t};
    private static final int[] S = {R.id.s0, R.id.s1, R.id.s2, R.id.s3, R.id.s4, R.id.s5};
    private static final int[] S_ON = {R.id.s0_on, R.id.s1_on, R.id.s2_on, R.id.s3_on, R.id.s4_on, R.id.s5_on};
    private static final int[] S_T = {R.id.s0_t, R.id.s1_t, R.id.s2_t, R.id.s3_t, R.id.s4_t, R.id.s5_t};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
            for (int id : ids) mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) Prefs.remove(ctx, id);
    }

    static void update(Context ctx, AppWidgetManager mgr, int id) {
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
        String title = "Nachrichten";
        if (selected.size() == 1) {
            Catalog.Feed f = Catalog.find(all, selected.iterator().next());
            if (f != null) title = f.name;
        }

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setInt(R.id.bg_fill, "setImageAlpha", Prefs.alpha(bg));
        rv.setInt(R.id.bg_border, "setImageAlpha", Math.round(Prefs.alpha(Math.max(bg, 20)) * 0.45f));
        rv.setTextViewText(R.id.title, title);
        rv.setTextColor(R.id.title, Prefs.argb(rgb, tx, 1f));
        rv.setTextColor(R.id.empty, Prefs.argb(rgb, tx, 1f));
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        int fg = Prefs.argb(rgb, tx, 1f);
        int dark = 0xFF1B1F22;
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
            rv.setViewVisibility(P[i], android.view.View.VISIBLE);
            rv.setTextViewText(P_T[i], i == 0 ? "Alle" : value);
            rv.setViewVisibility(P_ON[i], on ? android.view.View.VISIBLE : android.view.View.GONE);
            rv.setTextColor(P_T[i], on ? dark : fg);
            rv.setOnClickPendingIntent(P[i], topicPending(ctx, id, 100 + i, value, immutable));
        }
        // Unterthemen des gewaehlten Oberthemas: "Alle <Thema>" plus die Unterthemen (max. 5)
        List<String> subs = new java.util.ArrayList<>();
        if (!curParent.isEmpty()) {
            for (String t : topics) if (t.startsWith(curParent + "/")) subs.add(t);
            if (!subs.isEmpty()) subs.add(0, curParent);
        }
        for (int i = 0; i < S.length; i++) {
            if (i >= subs.size()) {
                rv.setViewVisibility(S[i], android.view.View.GONE);
                continue;
            }
            String value = subs.get(i);
            boolean on = value.equals(topic);
            rv.setViewVisibility(S[i], android.view.View.VISIBLE);
            rv.setTextViewText(S_T[i], i == 0 ? "Alle" : value.substring(value.indexOf('/') + 1));
            rv.setViewVisibility(S_ON[i], on ? android.view.View.VISIBLE : android.view.View.GONE);
            rv.setTextColor(S_T[i], on ? dark : fg);
            rv.setOnClickPendingIntent(S[i], topicPending(ctx, id, 200 + i, value, immutable));
        }
        rv.setViewVisibility(R.id.row_s1, subs.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        rv.setViewVisibility(R.id.row_s2, subs.size() > 3 ? android.view.View.VISIBLE : android.view.View.GONE);
        rv.setInt(R.id.btn_refresh, "setColorFilter", solid);
        rv.setInt(R.id.btn_settings, "setColorFilter", solid);
        rv.setInt(R.id.btn_refresh, "setImageAlpha", textAlpha);
        rv.setInt(R.id.btn_settings, "setImageAlpha", textAlpha);

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

    private static PendingIntent topicPending(Context ctx, int id, int slot, String topic, int immutable) {
        Intent i = new Intent(ctx, TopicActivity.class);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra("topic", topic);
        i.setData(Uri.parse("bgawidget://topic/" + id + "/" + slot));
        return PendingIntent.getActivity(ctx, id * 1000 + slot, i, PendingIntent.FLAG_UPDATE_CURRENT | immutable);
    }
}
