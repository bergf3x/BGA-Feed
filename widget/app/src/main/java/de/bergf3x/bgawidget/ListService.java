package de.bergf3x.bgawidget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
            items = FeedItem.parse(Prefs.cache(ctx));
        }

        @Override
        public void onDataSetChanged() {
            try {
                String xml = download(Prefs.FEED_URL + "?t=" + (System.currentTimeMillis() / 60000L));
                List<FeedItem> fresh = FeedItem.parse(xml);
                if (!fresh.isEmpty()) {
                    Prefs.setCache(ctx, xml);
                    items = fresh;
                    return;
                }
            } catch (Exception ignored) {
            }
            if (items.isEmpty()) items = FeedItem.parse(Prefs.cache(ctx));
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return Math.min(items.size(), 40);
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            if (position < 0 || position >= items.size()) return rv;
            FeedItem it = items.get(position);
            int tx = Prefs.text(ctx, widgetId);
            int a = Prefs.alpha(tx);
            rv.setTextViewText(R.id.item_title, it.title);
            rv.setTextViewText(R.id.item_meta, it.meta());
            rv.setTextColor(R.id.item_title, (a << 24) | 0x00FFFFFF);
            rv.setTextColor(R.id.item_meta, (Math.round(a * 0.7f) << 24) | 0x00FFFFFF);
            Intent fill = new Intent();
            fill.setData(Uri.parse(it.link));
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

        private static String download(String url) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "bga-widget/1.0");
            try {
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                InputStream in = c.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            } finally {
                c.disconnect();
            }
        }
    }
}
