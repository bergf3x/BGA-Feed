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
        private Set<String> readSet = new java.util.HashSet<>();
        private boolean airy;
        private int sizeIdx = 1;
        private boolean lightTheme;

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
            if (Prefs.takeLocalOnly(ctx)) {
                items = load(false);
                return;
            }
            Catalog.refresh(ctx);
            Weather.refreshIfStale(ctx, Prefs.place(ctx, widgetId));
            Warnings.refreshIfStale(ctx);
            items = load(true);
            WidgetProvider.refreshHeader(ctx, widgetId);
        }

        /** Gewaehlte Feeds laden (live oder aus dem Cache), mischen, nach Thema filtern. */
        private List<FeedItem> load(boolean online) {
            readSet = Prefs.readSet(ctx);
            airy = Prefs.density(ctx, widgetId) == 1;
            sizeIdx = Prefs.size(ctx, widgetId);
            lightTheme = Prefs.theme(ctx, widgetId) == 1;
            List<Catalog.Feed> all = Catalog.load(ctx);
            Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, widgetId));
            String topic = Prefs.topic(ctx, widgetId);
            boolean plusOn = Prefs.showPlus(ctx, widgetId);
            Set<String> muted = Prefs.muted(ctx, widgetId);
            boolean anyOk = false;
            boolean anyFail = false;
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
                        if (xml != null) {
                            Prefs.setFeedCache(ctx, f.id, xml);
                            anyOk = true;
                        } else {
                            anyFail = true;
                        }
                    } catch (Exception e) {
                        xml = null;
                        anyFail = true;
                        Prefs.setStatus(ctx, f.id, String.valueOf(e.getMessage() != null ? e.getMessage() : e));
                    }
                }
                if (xml == null) xml = Prefs.feedCache(ctx, f.id);
                for (FeedItem it : FeedItem.parse(xml, f.name)) {
                    if (!topic.isEmpty() && !it.inTopic(topic)) continue;
                    if (it.plus && !plusOn) continue;
                    if (isMuted(it, muted, topic)) continue;
                    merged.add(it);
                }
            }
            if (online) Prefs.setNews(ctx, anyOk || !anyFail);
            if (online && merged.isEmpty()) showEmptyReason(all, selected, topic);
            // Gleiche Zeit (z. B. erster Lauf): Reihenfolge innerhalb des Feeds bleibt erhalten
            Collections.sort(merged, (a, b) -> Long.compare(b.time, a.time));
            return merged;
        }

        /** Ausgeblendetes Thema – es sei denn, genau dieses Thema ist gerade gewaehlt. */
        private boolean isMuted(FeedItem it, Set<String> muted, String selTopic) {
            for (String m : muted) {
                if (!it.inTopic(m)) continue;
                boolean selected = !selTopic.isEmpty() && (selTopic.equals(m) || selTopic.startsWith(m + "/"));
                if (!selected) return true;
            }
            return false;
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
            boolean read = Prefs.isRead(readSet, it.link);
            float dim = read ? 0.55f : 1f;
            String meta = it.meta();
            rv.setTextViewText(R.id.item_title, Style.font(ctx, widgetId, it.title));
            rv.setTextViewText(R.id.item_meta, Style.font(ctx, widgetId, meta));
            rv.setTextViewTextSize(R.id.item_title, android.util.TypedValue.COMPLEX_UNIT_SP, Style.titleSp(sizeIdx));
            rv.setTextViewTextSize(R.id.item_meta, android.util.TypedValue.COMPLEX_UNIT_SP, Style.metaSp(sizeIdx));
            rv.setInt(R.id.item_card, "setBackgroundResource", lightTheme ? R.drawable.card_bg_light : R.drawable.card_bg);
            rv.setTextColor(R.id.item_title, Prefs.argb(rgb, tx, dim));
            rv.setTextColor(R.id.item_meta, Prefs.argb(rgb, tx, 0.7f * dim));
            rv.setInt(R.id.item_accent, "setColorFilter", it.accentColor());
            rv.setInt(R.id.item_accent, "setImageAlpha", read ? 90 : 255);
            rv.setViewVisibility(R.id.item_plus, it.plus ? android.view.View.VISIBLE : android.view.View.GONE);
            boolean dot = !read && it.isFresh();
            rv.setTextColor(R.id.item_dot, it.accentColor());
            rv.setViewVisibility(R.id.item_dot, dot ? android.view.View.VISIBLE : android.view.View.GONE);
            float dp = ctx.getResources().getDisplayMetrics().density;
            int cardPad = Math.round((airy ? 13 : 8) * dp);
            int rowPad = Math.round((airy ? 5 : 3) * dp);
            rv.setViewPadding(R.id.item_card, cardPad, cardPad, cardPad, cardPad);
            rv.setViewPadding(R.id.item_root, 0, rowPad, 0, rowPad);
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
