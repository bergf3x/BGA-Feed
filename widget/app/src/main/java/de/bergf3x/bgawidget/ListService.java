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

    /** Ungelesene Artikel der letzten 12 Stunden (fuer die Zahl neben "Alle"). */
    static int freshUnread(Context ctx, int widgetId) {
        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, widgetId));
        Set<String> read = Prefs.readSet(ctx);
        Set<String> muted = Prefs.muted(ctx, widgetId);
        int n = 0;
        for (Catalog.Feed f : all) {
            if (!selected.contains(f.id)) continue;
            String xml = Prefs.feedCache(ctx, f.id);
            if (xml == null) continue;
            boolean plusFeed = Prefs.showPlusFeed(ctx, widgetId, f.id);
            for (FeedItem it : FeedItem.parse(xml, f.name)) {
                if (!it.isFresh() || Prefs.isRead(read, it.link)) continue;
                if (it.plus && !plusFeed) continue;
                boolean hide = false;
                for (String m : muted) if (it.inTopic(m)) hide = true;
                if (!hide) n++;
            }
        }
        return n;
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final int widgetId;
        private List<FeedItem> items = new ArrayList<>();
        private Set<String> readSet = new java.util.HashSet<>();
        private List<String> kwList = new ArrayList<>();
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
            if (Prefs.takeLocalOnly(ctx) || Prefs.justFetched(ctx)) {
                items = load(false);
                WidgetProvider.refreshHeader(ctx, widgetId);
                return;
            }
            Catalog.refresh(ctx);
            Weather.refreshIfStale(ctx, Prefs.place(ctx, widgetId));
            Warnings.refreshIfStale(ctx);
            Avalanche.refreshIfStale(ctx);
            items = load(true);
            WidgetProvider.refreshHeader(ctx, widgetId);
        }

        /** Gewaehlte Feeds laden (live oder aus dem Cache), mischen, nach Thema filtern. */
        private List<FeedItem> load(boolean online) {
            readSet = Prefs.readSet(ctx);
            kwList = Keywords.list(ctx);
            airy = Prefs.density(ctx, widgetId) == 1;
            sizeIdx = Prefs.size(ctx, widgetId);
            lightTheme = Prefs.isLight(ctx, widgetId);
            List<Catalog.Feed> all = Catalog.load(ctx);
            Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, widgetId));
            String topic = Prefs.topic(ctx, widgetId);
            List<String> kw = Keywords.list(ctx);
            Set<String> muted = Prefs.muted(ctx, widgetId);
            boolean anyOk = false;
            boolean anyFail = false;
            List<FeedItem> merged = new ArrayList<>();
            StringBuilder stale = new StringBuilder();
            long nowMs = System.currentTimeMillis();
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
                long newest = 0;
                List<FeedItem> parsed = FeedItem.parse(xml, f.name);
                for (FeedItem it : parsed) newest = Math.max(newest, it.time);
                if (newest > 0 && nowMs - newest > 24L * 3600000) {
                    if (stale.length() > 0) stale.append(", ");
                    stale.append(f.name).append(" ohne Neues seit ").append((nowMs - newest) / 3600000L).append(" Std.");
                }
                boolean plusFeed = Prefs.showPlusFeed(ctx, widgetId, f.id);
                for (FeedItem it : parsed) {
                    if (it.plus && !plusFeed) continue;
                    if (isMuted(it, muted, topic)) continue;
                    if (online && Keywords.matches(kw, it)) Notifier.keyword(ctx, it);
                    if (!topic.isEmpty() && !it.inTopic(topic)) continue;
                    merged.add(it);
                }
            }
            if (online) Prefs.setNews(ctx, anyOk || !anyFail);
            if (online && merged.isEmpty()) showEmptyReason(all, selected, topic);
            // Gleiche Zeit (z. B. erster Lauf): Reihenfolge innerhalb des Feeds bleibt erhalten
            Collections.sort(merged, (a, b) -> Long.compare(b.time, a.time));
            Prefs.setSrcWarn(ctx, stale.toString());
            return dedupe(merged);
        }

        /** Gleiche Meldung aus zwei Quellen nur einmal zeigen (aehnlicher Titel, ueber 60 % gleiche Woerter). */
        private List<FeedItem> dedupe(List<FeedItem> in) {
            List<FeedItem> out = new ArrayList<>();
            List<Set<String>> sets = new ArrayList<>();
            for (FeedItem it : in) {
                Set<String> w = words(it.title);
                boolean dup = false;
                for (int i = 0; i < out.size() && !dup; i++) {
                    if (out.get(i).feedName.equals(it.feedName)) continue;
                    Set<String> o = sets.get(i);
                    if (w.size() < 3 || o.size() < 3) continue;
                    int common = 0;
                    for (String x : w) if (o.contains(x)) common++;
                    int union = w.size() + o.size() - common;
                    if (union > 0 && common / (double) union >= 0.6) dup = true;
                }
                if (dup) continue;
                out.add(it);
                sets.add(w);
            }
            return out;
        }

        private Set<String> words(String t) {
            Set<String> r = new java.util.HashSet<>();
            for (String x : t.toLowerCase(java.util.Locale.GERMANY).split("[^\\p{L}\\p{N}]+")) if (x.length() >= 4) r.add(x);
            return r;
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
            boolean hit = Keywords.matches(kwList, it);
            rv.setTextViewText(R.id.item_title, Style.font(ctx, widgetId, hit ? "★ " + it.title : it.title));
            
            rv.setTextViewTextSize(R.id.item_title, android.util.TypedValue.COMPLEX_UNIT_SP, Style.titleSp(sizeIdx));
            rv.setTextViewTextSize(R.id.item_meta, android.util.TypedValue.COMPLEX_UNIT_SP, Style.metaSp(sizeIdx));
            rv.setInt(R.id.item_card, "setBackgroundResource", Pal.cardBg(ctx, widgetId));
            if (hit) rv.setTextColor(R.id.item_title, Prefs.argb(0xFFD166, tx, dim)); else Pal.text(ctx, widgetId, rv, R.id.item_title, -1, dim);
            int sub = Prefs.colorSub(ctx, widgetId);
            Pal.set(ctx, widgetId, rv, R.id.item_meta, Style.font(ctx, widgetId, meta), sub, Prefs.colorSub2(ctx, widgetId), 0.7f * dim);
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
