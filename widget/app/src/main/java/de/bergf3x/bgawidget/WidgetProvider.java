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
    static final String ACTION_TOPIC = "de.bergf3x.bgawidget.TOPIC";

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
        if (ACTION_TOPIC.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            if (id != 0) {
                cycleTopic(ctx, id);
                update(ctx, AppWidgetManager.getInstance(ctx), id);
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) Prefs.remove(ctx, id);
    }

    /** Alle -> erstes Thema -> zweites ... -> wieder Alle. */
    private static void cycleTopic(Context ctx, int id) {
        List<String> topics = Catalog.topicsFor(Catalog.load(ctx), Prefs.feeds(ctx, id));
        String cur = Prefs.topic(ctx, id);
        int idx = cur.isEmpty() ? -1 : topics.indexOf(cur);
        int next = idx + 1;
        Prefs.setTopic(ctx, id, next >= topics.size() ? "" : topics.get(next));
    }

    static void update(Context ctx, AppWidgetManager mgr, int id) {
        int bg = Prefs.bg(ctx, id);
        int tx = Prefs.text(ctx, id);
        int rgb = Prefs.color(ctx, id);
        int textAlpha = Prefs.alpha(tx);
        int solid = 0xFF000000 | rgb;

        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> selected = Prefs.feeds(ctx, id);
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
        rv.setTextViewText(R.id.btn_topic, (topic.isEmpty() ? "Alle Themen" : topic) + " ▾");
        rv.setTextColor(R.id.btn_topic, Prefs.argb(rgb, tx, 1f));
        rv.setViewVisibility(R.id.btn_topic, topics.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        rv.setInt(R.id.btn_refresh, "setColorFilter", solid);
        rv.setInt(R.id.btn_settings, "setColorFilter", solid);
        rv.setInt(R.id.btn_refresh, "setImageAlpha", textAlpha);
        rv.setInt(R.id.btn_settings, "setImageAlpha", textAlpha);

        Intent svc = new Intent(ctx, ListService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.list, svc);
        rv.setEmptyView(R.id.list, R.id.empty);

        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;

        Intent open = new Intent(ctx, PreviewActivity.class);
        rv.setPendingIntentTemplate(R.id.list,
                PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT | mutable));

        Intent refresh = new Intent(ctx, WidgetProvider.class).setAction(ACTION_REFRESH);
        rv.setOnClickPendingIntent(R.id.btn_refresh,
                PendingIntent.getBroadcast(ctx, 0, refresh, PendingIntent.FLAG_UPDATE_CURRENT | immutable));

        Intent topicIntent = new Intent(ctx, WidgetProvider.class).setAction(ACTION_TOPIC);
        topicIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        topicIntent.setData(Uri.parse("bgawidget://topic/" + id));
        rv.setOnClickPendingIntent(R.id.btn_topic,
                PendingIntent.getBroadcast(ctx, id, topicIntent, PendingIntent.FLAG_UPDATE_CURRENT | immutable));

        Intent cfg = new Intent(ctx, ConfigActivity.class);
        cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        rv.setOnClickPendingIntent(R.id.btn_settings,
                PendingIntent.getActivity(ctx, id, cfg, PendingIntent.FLAG_UPDATE_CURRENT | immutable));

        mgr.updateAppWidget(id, rv);
        mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
    }
}
