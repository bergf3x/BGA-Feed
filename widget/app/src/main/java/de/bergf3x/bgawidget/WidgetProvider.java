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
    private static final int[] CHIPS = {R.id.chip0, R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4};

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
        if (ACTION_SET_TOPIC.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            if (id != 0) {
                Prefs.setTopic(ctx, id, intent.getStringExtra("topic") == null ? "" : intent.getStringExtra("topic"));
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
        for (int i = 0; i < CHIPS.length; i++) {
            int cid = CHIPS[i];
            String value = i == 0 ? "" : (i - 1 < parents.size() ? parents.get(i - 1) : null);
            if (value == null || topics.isEmpty()) {
                rv.setViewVisibility(cid, android.view.View.GONE);
                continue;
            }
            boolean on = value.equals(curParent);
            rv.setViewVisibility(cid, android.view.View.VISIBLE);
            rv.setTextViewText(cid, i == 0 ? "Alle" : value);
            rv.setInt(cid, "setBackgroundResource", on ? R.drawable.chip_on : R.drawable.chip_bg);
            rv.setTextColor(cid, on ? dark : fg);
            Intent ci = new Intent(ctx, WidgetProvider.class).setAction(ACTION_SET_TOPIC);
            ci.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            ci.putExtra("topic", value);
            ci.setData(Uri.parse("bgawidget://chip/" + id + "/" + i));
            rv.setOnClickPendingIntent(cid,
                    PendingIntent.getBroadcast(ctx, id * 10 + i, ci, PendingIntent.FLAG_UPDATE_CURRENT | immutable));
        }
        boolean hasSub = topic.indexOf('/') >= 0;
        rv.setTextViewText(R.id.chip5, (hasSub ? topic.substring(topic.indexOf('/') + 1) : "Mehr") + " ▾");
        rv.setInt(R.id.chip5, "setBackgroundResource", hasSub ? R.drawable.chip_on : R.drawable.chip_bg);
        rv.setTextColor(R.id.chip5, hasSub ? dark : fg);
        rv.setViewVisibility(R.id.chip5, topics.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
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

        Intent topicIntent = new Intent(ctx, TopicActivity.class);
        topicIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        topicIntent.setData(Uri.parse("bgawidget://topic/" + id));
        rv.setOnClickPendingIntent(R.id.chip5,
                PendingIntent.getActivity(ctx, id, topicIntent, PendingIntent.FLAG_UPDATE_CURRENT | immutable));

        Intent cfg = new Intent(ctx, ConfigActivity.class);
        cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        rv.setOnClickPendingIntent(R.id.btn_settings,
                PendingIntent.getActivity(ctx, id, cfg, PendingIntent.FLAG_UPDATE_CURRENT | immutable));

        mgr.updateAppWidget(id, rv);
        mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
    }
}
