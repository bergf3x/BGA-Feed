package de.bergf3x.bgawidget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ListService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int id = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        return new Factory(getApplicationContext(), id);
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final int widgetId;
        private List<FeedItem> items = new ArrayList<>();

        Factory(Context ctx, int widgetId) {
            this.ctx = ctx;
            this.widgetId = widgetId;
        }

        @Override
        public void onCreate() {
            items = load(false);
        }

        @Override
        public void onDataSetChanged() {
            Catalog.refresh(ctx);
            if (Weather.refreshIfStale(ctx, Prefs.place(ctx, widgetId))) WidgetProvider.refreshHeader(ctx, widgetId);
            items = load(true);
        }

        /** Gewaehlte Feeds laden (live oder aus dem Cache), mischen, nach Thema filtern. */
        private List<FeedItem> load(boolean online) {
            List<Catalog.Feed> all = Catalog.load(ctx);
            Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, widgetId));
            String topic = Prefs.topic(ctx, widgetId);
            List<FeedItem> merged = new ArrayList<>();
            for (Catalog.Feed f : all) {
                if (!selected.contains(f.id)) continue;
                String xml = null;
                if (online) {
                    try {
                        xml = Net.download(Net.fresh(f.url));
                        if (FeedItem.parse(xml, f.name).isEmpty()) {
                            xml = null;
                            Prefs.setStatus(ctx, f.id, "Feed ist leer");
                        } else {
                            Prefs.setStatus(ctx, f.id, "");
                        }
                        if (xml != null) Prefs.setFeedCache(ctx, f.id, xml);
                    } catch (Exception e) {
                        xml = null;
                        Prefs.setStatus(ctx, f.id, String.valueOf(e.getMessage() != null ? e.getMessage() : e));
                    }
                }
                if (xml == null) xml = Prefs.feedCache(ctx, f.id);
                for (FeedItem it : FeedItem.parse(xml, f.name)) {
                    String t = it.topic.isEmpty() ? "Region" : it.topic;
                    if (topic.isEmpty() || t.equals(topic) || t.startsWith(topic + "/")) merged.add(it);
                }
            }
            if (online && merged.isEmpty()) showEmptyReason(all, selected, topic);
            // Gleiche Zeit (z. B. erster Lauf): Reihenfolge innerhalb des Feeds bleibt erhalten
            Collections.sort(merged, (a, b) -> Long.compare(b.time, a.time));
            return merged;
        }

        private void showEmptyReason(List<Catalog.Feed> all, Set<String> selected, String topic) {
            StringBuilder sb = new StringBuilder();
            for (Catalog.Feed f : all) {
                if (!selected.contains(f.id)) continue;
                String st = Prefs.status(ctx, f.id);
                if (!st.isEmpty()) sb.append(f.name).append(": ").append(st).append("\n");
            }
            if (sb.length() == 0) {
                sb.append(topic.isEmpty() ? "Keine Artikel gefunden"
                        : "Keine Artikel im Thema „" + topic.substring(topic.indexOf('/') + 1) + "“");
            }
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.empty, sb.toString().trim());
            android.appwidget.AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(widgetId, rv);
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return Math.min(items.size(), 60);
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            if (position < 0 || position >= items.size()) return rv;
            FeedItem it = items.get(position);
            int tx = Prefs.text(ctx, widgetId);
            int rgb = Prefs.color(ctx, widgetId);
            String meta = it.meta();
            rv.setTextViewText(R.id.item_title, it.title);
            rv.setTextViewText(R.id.item_meta, meta);
            rv.setTextColor(R.id.item_title, Prefs.argb(rgb, tx, 1f));
            rv.setTextColor(R.id.item_meta, Prefs.argb(rgb, tx, 0.7f));
            rv.setInt(R.id.item_accent, "setColorFilter", it.accentColor());
            rv.setViewVisibility(R.id.item_plus, it.plus ? android.view.View.VISIBLE : android.view.View.GONE);
            Intent fill = new Intent();
            fill.putExtra(PreviewActivity.EXTRA_TITLE, it.title);
            fill.putExtra(PreviewActivity.EXTRA_META, meta);
            fill.putExtra(PreviewActivity.EXTRA_TEXT, it.description);
            fill.putExtra(PreviewActivity.EXTRA_LINK, it.link);
            rv.setOnClickFillInIntent(R.id.item_root, fill);
            return rv;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
