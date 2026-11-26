/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
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
            boolean plusFeed = Prefs.showPlusFeed(ctx, widgetId, f.id);
            for (FeedItem it : FeedCache.items(ctx, f.id, f.name)) {
                if (!it.isFresh() || Prefs.isRead(read, it.link)) continue;
                if (it.plus && !plusFeed) continue;
                boolean hide = false;
                for (String m : muted) if (it.inTopic(m)) hide = true;
                if (!hide) n++;
            }
        }
        return n;
    }

    /** Alle Zeitungen der gewaehlten Quellen (auch ohne aktuelle Meldungen), Namen wie im Pfad "Zeitungen/<Name>". */
    static List<String> paperNames(Context ctx, int id) {
        List<String> out = new ArrayList<>();
        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> sel = Catalog.effective(all, Prefs.feeds(ctx, id));
        for (Catalog.Feed f : all) {
            String n = f.name.replace('/', '-');
            if (sel.contains(f.id) && !out.contains(n)) out.add(n);
        }
        return out;
    }

    /** {gefilterte Zeitungen, alle Zeitungen}; ohne Zeitungsfilter sind beide Zahlen gleich. */
    static int[] sourceCount(Context ctx, int id) {
        String root = Taxonomy.PAPERS + "/";
        int total = paperNames(ctx, id).size();
        Set<String> picked = new java.util.HashSet<>();
        for (String[] e : Prefs.topicSel(ctx, id)) {
            if (e[0].startsWith(root)) picked.add(e[0].substring(root.length()).split("/")[0]);
        }
        return new int[]{picked.isEmpty() ? total : Math.min(total, picked.size()), total};
    }

    /** Ist ein Quellen-Filter aktiv (nicht alle Zeitungen)? */
    static boolean filtered(Context ctx, int id) {
        int[] q = sourceCount(ctx, id);
        return q[0] < q[1];
    }

    /** Zeile unter den Reitern, z. B. "Gefiltert: 2 von 6 Quellen · 3 ungelesen"; leer, wenn es nichts zu sagen gibt. */
    static String infoLine(Context ctx, int id, int unread) {
        int[] q = sourceCount(ctx, id);
        StringBuilder sb = new StringBuilder();
        if (q[0] < q[1]) sb.append("Gefiltert: ").append(q[0]).append(" von ").append(q[1]).append(q[1] == 1 ? " Quelle" : " Quellen");
        if (unread > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(unread).append(" ungelesen");
        }
        return sb.toString();
    }

    /** Alles Aktuelle als gelesen markieren und App sowie Widgets neu zeichnen. */
    static void markAllRead(Context ctx) {
        markSeen(ctx);
        Prefs.setLocalOnly(ctx);
        Prefs.refreshWidgets(ctx.getApplicationContext());
        try {
            ctx.sendBroadcast(new Intent(Direct.DATA_CHANGED).setPackage(ctx.getPackageName()));
        } catch (Exception ignored) {
        }
    }

    /** Alle Links im Zwischenspeicher aller Quellen. */
    static Set<String> allLinks(Context ctx) {
        Set<String> out = new java.util.HashSet<>();
        for (Catalog.Feed f : Catalog.load(ctx)) {
            for (FeedItem it : FeedCache.items(ctx, f.id, f.name)) if (!it.link.isEmpty()) out.add(it.link);
        }
        return out;
    }

    /** Alles Aktuelle gilt ab jetzt als gesehen (beim Verlassen der App). */
    static void markSeen(Context ctx) {
        Set<String> s = allLinks(ctx);
        if (!s.isEmpty()) Prefs.setSeen(ctx, s);
    }

    /** Artikel ist neu: nicht in der zuletzt gesehenen Menge und noch nicht gelesen. */
    static boolean isNew(boolean hasSeen, Set<String> seen, Set<String> read, FeedItem it) {
        return hasSeen && !it.weather() && !it.link.isEmpty() && !seen.contains(it.link) && !Prefs.isRead(read, it.link);
    }

    /** Alle ungelesenen neuen Artikel, die in diesem Widget bzw. der App sichtbar sind. */
    static List<FeedItem> newItems(Context ctx, int widgetId) {
        List<FeedItem> out = new java.util.ArrayList<>();
        if (!Prefs.hasSeen(ctx)) return out;
        Set<String> seen = Prefs.seenSet(ctx);
        Set<String> read = Prefs.readSet(ctx);
        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, widgetId));
        Set<String> muted = Prefs.muted(ctx, widgetId);
        for (Catalog.Feed f : all) {
            if (!selected.contains(f.id)) continue;
            boolean plusFeed = Prefs.showPlusFeed(ctx, widgetId, f.id);
            for (FeedItem it : FeedCache.items(ctx, f.id, f.name)) {
                if (!isNew(true, seen, read, it)) continue;
                if (it.plus && !plusFeed) continue;
                boolean hide = false;
                for (String m : muted) if (it.inTopic(m)) hide = true;
                if (!hide) out.add(it);
            }
        }
        return out;
    }

    /** Oberthemen (und Zeitungs-Wurzel), in denen es Neues gibt. */
    static Set<String> newTopics(Context ctx, int widgetId) {
        Set<String> out = new java.util.HashSet<>();
        for (FeedItem it : newItems(ctx, widgetId)) {
            out.add((it.topic.isEmpty() ? "Region" : it.topic).split("/")[0]);
            if (!it.paper.isEmpty()) out.add(it.paper.split("/")[0]);
        }
        return out;
    }

    static final Object FETCH_LOCK = new Object();
    /** Grund, warum die letzte Abfrage keine Artikel ergab (fuer die Vollbild-App). */
    static volatile String emptyReason = "";
    private static volatile long lastFetchEnd = 0;

    static boolean recentlyFetched() {
        return System.currentTimeMillis() - lastFetchEnd < 20000;
    }

    /** Ergebnis eines Feed-Abrufs. */
    static final class Fetched {
        String xml;
        List<FeedItem> items;
        String err;
    }

    /** Alle gewaehlten Feeds plus Wetter, Warnungen und Lawinenbericht gleichzeitig, mit gemeinsamem Zeitbudget. */
    static java.util.Map<String, Fetched> fetchAll(final Context ctx, List<Catalog.Feed> all, Set<String> selected, final int place, final boolean app) {
        Net.init(ctx);
        java.util.Map<String, Fetched> out = new java.util.HashMap<>();
        List<java.util.concurrent.Callable<Fetched>> tasks = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        for (final Catalog.Feed f : all) {
            if (!selected.contains(f.id)) continue;
            ids.add(f.id);
            tasks.add(() -> {
                Fetched r = new Fetched();
                try {
                    r.xml = Direct.enabled(ctx) && Direct.handles(f) ? Direct.fetch(ctx, f) : Net.downloadFeed(f.url);
                    r.items = FeedItem.parse(r.xml, f.name);
                } catch (Exception e) {
                    r.err = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
                }
                return r;
            });
        }
        final int feedCount = tasks.size();
        // Vollbild-App: Wetter immer gleich frisch; Widgets: nach ihrer Einstellung (Automatik-Intervall oder manuell)
        final long wxAge = app ? Prefs.APP_WX_MS : Prefs.wxMaxAge(ctx);
        tasks.add(() -> { Weather.refreshIfStale(ctx, place, wxAge); return null; });
        tasks.add(() -> { Warnings.refreshIfStale(ctx, wxAge); return null; });
        tasks.add(() -> { Avalanche.refreshIfStale(ctx, wxAge); return null; });
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(Math.min(5, tasks.size()));
        try {
            List<java.util.concurrent.Future<Fetched>> fs = ex.invokeAll(tasks, Direct.enabled(ctx) ? 50 : 25, java.util.concurrent.TimeUnit.SECONDS);
            for (int i = 0; i < feedCount; i++) {
                java.util.concurrent.Future<Fetched> fu = fs.get(i);
                if (fu.isCancelled()) continue;
                try {
                    Fetched r = fu.get();
                    if (r != null) out.put(ids.get(i), r);
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ex.shutdownNow();
        }
        android.util.Log.i("BGAstat", "fetchAll app=" + app + " " + Net.stats());
        return out;
    }

    /** Gewaehlte Feeds laden (live oder aus dem Cache), mischen, nach Thema filtern. */
    static List<FeedItem> loadItems(Context ctx, int id, boolean online, java.util.Map<String, Fetched> res) {
        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, id));
        String topic = Prefs.topic(ctx, id);
        List<String[]> sel = Prefs.topicSel(ctx, id);
        List<String> kw = Keywords.list(ctx);
        Set<String> muted = Prefs.muted(ctx, id);
        boolean anyOk = false;
        boolean anyFail = false;
        List<FeedItem> merged = new ArrayList<>();
        StringBuilder stale = new StringBuilder();
        long nowMs = System.currentTimeMillis();
        for (Catalog.Feed f : all) {
            if (!selected.contains(f.id)) continue;
            List<FeedItem> parsed = null;
            if (online) {
                Fetched r = res == null ? null : res.get(f.id);
                if (r == null || r.err != null) {
                    anyFail = true;
                    Prefs.setStatus(ctx, f.id, r == null ? "Zeitüberschreitung" : r.err);
                } else if (r.items.isEmpty()) {
                    anyFail = true;
                    Prefs.setStatus(ctx, f.id, "Feed ist leer");
                } else {
                    Prefs.setStatus(ctx, f.id, "");
                    FeedCache.put(ctx, f.id, r.xml, r.items);
                    anyOk = true;
                    parsed = r.items;
                }
            }
            if (parsed == null) parsed = FeedCache.items(ctx, f.id, f.name);
            long newest = 0;
            for (FeedItem it : parsed) newest = Math.max(newest, it.time);
            if (newest > 0 && nowMs - newest > 24L * 3600000 && !Catalog.lowVolume(f.id)) {
                if (stale.length() > 0) stale.append(", ");
                stale.append(f.name).append(" ohne Neues seit ").append((nowMs - newest) / 3600000L).append(" Std.");
            }
            boolean plusFeed = Prefs.showPlusFeed(ctx, id, f.id);
            for (FeedItem it : parsed) {
                if (it.plus && !plusFeed) continue;
                if (isMuted(it, muted, topic)) continue;
                if (online && Keywords.matches(kw, it)) Notifier.keyword(ctx, it);
                if (!Prefs.matchTopic(it, f.name, sel)) continue;
                merged.add(it);
            }
        }
        if (!sel.isEmpty()) {
            for (FeedItem it : WeatherCards.items(ctx)) if (Prefs.matchTopic(it, WeatherCards.TOP, sel)) merged.add(it);
        }
        if (online) Prefs.setNews(ctx, anyOk || !anyFail);
        if (online && merged.isEmpty()) showEmptyReason(ctx, id, all, selected, topic);
        // Gleiche Zeit (z. B. erster Lauf): Reihenfolge innerhalb des Feeds bleibt erhalten
        Collections.sort(merged, (a, b) -> Long.compare(b.time, a.time));
        String bad = Watch.badSummary(ctx);
        if (!bad.isEmpty()) {
            if (stale.length() > 0) stale.append(" · ");
            stale.append(bad);
        }
        Prefs.setSrcWarn(ctx, stale.toString());
        final List<FeedItem> result = dedupe(merged);
        int plusN = 0;
        for (FeedItem it : result) if (it.plus) plusN++;
        PLUS_N.put(id, plusN);
        if (online) {
            try {
                checkSaved(ctx, merged);
            } catch (Exception ignored) {
            }
        }
        if (online && id != Prefs.APP) {
            // Widget: Bilder der obersten Meldungen im WLAN vorladen, danach die Liste neu zeichnen
            final Context app = ctx.getApplicationContext();
            ImageStore.prefetch(app, result, 20, () -> {
                android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(app);
                for (int wid : mgr.getAppWidgetIds(new android.content.ComponentName(app, WidgetProvider.class))) {
                    mgr.notifyAppWidgetViewDataChanged(wid, R.id.list);
                }
            });
        }
        return result;
    }

    /**
     * Bei mehreren Medien: Liste nach Medium gruppieren (Reihenfolge wie im Katalog), je Gruppe eine Ueberschrift,
     * innerhalb der Gruppe weiter nach Zeit. Bei nur einem Medium unveraendert.
     */
    /** Gemerkte Artikel als Listeneintraege (ohne Zeitangabe, die waere veraltet). */
    /**
     * Prueft bei jeder Aktualisierung die gemerkten Artikel: Steht der Link noch im geladenen Feed, ist er da;
     * sonst wird die Seite abgerufen, nur bei HTTP 404/410 gilt er als entfernt (Netzfehler zaehlen nicht).
     * Fehlende Bilder werden aus den geladenen Meldungen nachgetragen.
     */
    static void checkSaved(Context ctx, List<FeedItem> loaded) {
        Prefs.pruneSaved(ctx);
        org.json.JSONArray a = Prefs.saved(ctx);
        if (a.length() == 0) return;
        java.util.Map<String, String> imgs = new java.util.HashMap<>();
        Set<String> live = new java.util.HashSet<>();
        for (FeedItem it : loaded) {
            if (it.link.isEmpty()) continue;
            live.add(it.link);
            if (!it.image.isEmpty()) imgs.put(it.link, it.image);
        }
        Set<String> gone = new java.util.HashSet<>();
        Set<String> checked = new java.util.HashSet<>();
        int fetched = 0;
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String l = o.optString("l");
            if (!l.startsWith("http")) continue;
            if (live.contains(l)) {
                checked.add(l);
                continue;
            }
            if (fetched >= 12) break;
            fetched++;
            try {
                String html = Net.download(l, false);
                checked.add(l);
                if (o.optString("i").isEmpty()) {
                    String img = Direct.ogImage(html, l);
                    if (img != null && !img.isEmpty()) imgs.put(l, img);
                }
            } catch (java.io.IOException e) {
                String m = String.valueOf(e.getMessage());
                if (m.equals("HTTP 404") || m.equals("HTTP 410")) {
                    checked.add(l);
                    gone.add(l);
                }
            }
        }
        Prefs.markSavedGone(ctx, gone, checked, imgs);
    }

    /** Anzahl Abo-Artikel in der zuletzt geladenen Liste je Widget/App. */
    private static final java.util.Map<Integer, Integer> PLUS_N = new java.util.concurrent.ConcurrentHashMap<>();

    static int plusCount(int id) {
        Integer n = PLUS_N.get(id);
        return n == null ? 0 : n;
    }

    static List<FeedItem> savedItems(Context ctx) {
        List<FeedItem> sv = new ArrayList<>();
        Prefs.pruneSaved(ctx);
        org.json.JSONArray a = Prefs.saved(ctx);
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            FeedItem f = new FeedItem();
            f.title = o.optString("t");
            f.description = o.optString("x");
            f.link = o.optString("l");
            f.image = o.optString("i");
            StringBuilder m = new StringBuilder();
            for (String seg : o.optString("m").split(" · ")) {
                String s = seg.trim();
                if (s.isEmpty() || s.startsWith("vor ") || s.startsWith("heute") || s.startsWith("gestern") || Character.isDigit(s.charAt(0)) || s.equals("●")) continue;
                if (m.length() > 0) m.append(" · ");
                m.append(s.replace("● ", ""));
            }
            f.gone = o.optBoolean("g");
            if (f.gone) m.insert(0, "Artikel nicht mehr verfügbar" + (m.length() > 0 ? " · " : ""));
            f.sub = m.toString();
            String src = FeedItem.source(f.link);
            f.feedName = src.isEmpty() ? "Gemerkt" : src;
            sv.add(f);
        }
        return sv;
    }

    static List<FeedItem> grouped(Context ctx, List<FeedItem> in) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (FeedItem it : in) names.add(it.feedName);
        if (names.size() < 2) return in;
        List<String> order = new ArrayList<>();
        for (Catalog.Feed f : Catalog.load(ctx)) if (names.contains(f.name)) order.add(f.name);
        for (String n : names) if (!order.contains(n)) order.add(n);
        List<FeedItem> out = new ArrayList<>();
        for (String n : order) {
            FeedItem h = new FeedItem();
            h.header = true;
            h.title = n;
            h.feedName = n;
            out.add(h);
            for (FeedItem it : in) if (it.feedName.equals(n)) out.add(it);
        }
        return out;
    }

    /** Gleiche Meldung aus zwei Quellen nur einmal zeigen (aehnlicher Titel, ueber 60 % gleiche Woerter). */
    private static List<FeedItem> dedupe(List<FeedItem> in) {
        // Wort-Index statt Jeder-gegen-Jeden: bleibt auch bei 1000+ Meldungen aus vielen Quellen schnell
        List<FeedItem> out = new ArrayList<>();
        List<Set<String>> sets = new ArrayList<>();
        java.util.Map<String, List<Integer>> index = new java.util.HashMap<>();
        for (FeedItem it : in) {
            Set<String> w = words(it.title);
            boolean dup = false;
            if (w.size() >= 3) {
                java.util.Map<Integer, Integer> common = new java.util.HashMap<>();
                for (String x : w) {
                    List<Integer> hits = index.get(x);
                    if (hits == null) continue;
                    for (int i : hits) {
                        Integer c = common.get(i);
                        common.put(i, c == null ? 1 : c + 1);
                    }
                }
                for (java.util.Map.Entry<Integer, Integer> e : common.entrySet()) {
                    int i = e.getKey();
                    if (out.get(i).feedName.equals(it.feedName) && !out.get(i).title.equals(it.title)) continue;
                    Set<String> o = sets.get(i);
                    if (o.size() < 3) continue;
                    int c = e.getValue();
                    int union = w.size() + o.size() - c;
                    if (union > 0 && c / (double) union >= 0.6) {
                        dup = true;
                        break;
                    }
                }
            }
            if (dup) continue;
            int pos = out.size();
            out.add(it);
            sets.add(w);
            for (String x : w) {
                List<Integer> l = index.get(x);
                if (l == null) index.put(x, l = new ArrayList<>());
                l.add(pos);
            }
        }
        return out;
    }

    private static Set<String> words(String t) {
        Set<String> r = new java.util.HashSet<>();
        for (String x : t.toLowerCase(java.util.Locale.GERMANY).split("[^\\p{L}\\p{N}]+")) if (x.length() >= 4) r.add(x);
        return r;
    }

    /** Ausgeblendetes Thema – es sei denn, genau dieses Thema ist gerade gewaehlt. */
    private static boolean isMuted(FeedItem it, Set<String> muted, String selTopic) {
        for (String m : muted) {
            if (!it.inTopic(m)) continue;
            boolean selected = !selTopic.isEmpty() && (selTopic.equals(m) || selTopic.startsWith(m + "/"));
            if (!selected) return true;
        }
        return false;
    }

    /** Abo-Artikel, die im gewaehlten Thema nur wegen "Abo ausblenden" fehlen; ids = betroffene Quellen. */
    static int hiddenPlus(Context ctx, int id, List<String> ids) {
        List<Catalog.Feed> all = Catalog.load(ctx);
        Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, id));
        List<String[]> sel = Prefs.topicSel(ctx, id);
        int n = 0;
        for (Catalog.Feed f : all) {
            if (!selected.contains(f.id) || Prefs.showPlusFeed(ctx, id, f.id)) continue;
            int k = 0;
            for (FeedItem it : FeedCache.items(ctx, f.id, f.name)) {
                if (it.plus && Prefs.matchTopic(it, f.name, sel)) k++;
            }
            if (k > 0 && ids != null) ids.add(f.id);
            n += k;
        }
        return n;
    }

    private static void showEmptyReason(Context ctx, int id, List<Catalog.Feed> all, Set<String> selected, String topic) {
        StringBuilder sb = new StringBuilder();
        for (Catalog.Feed f : all) {
            if (!selected.contains(f.id)) continue;
            String st = Prefs.status(ctx, f.id);
            if (!st.isEmpty()) sb.append(f.name).append(": ").append(st).append("\n");
        }
        if (sb.length() == 0) {
            sb.append(topic.isEmpty() ? "Keine Artikel gefunden"
                    : "Keine Artikel im Thema „" + topic.substring(topic.indexOf('/') + 1) + "“");
            int hp = hiddenPlus(ctx, id, null);
            if (hp > 0) sb.append("\n").append(hp).append(" Abo-Artikel ausgeblendet");
        }
        emptyReason = sb.toString().trim();
        if (id <= 0) return;
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setTextViewText(R.id.empty, emptyReason);
        android.appwidget.AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, rv);
    }

    /** Abruf fuer die Vollbild-App (eigene Einstellungen unter Prefs.APP); nie doppelt zum Widget-Abruf. */
    static void syncApp(Context ctx, boolean force) {
        synchronized (FETCH_LOCK) {
            if (!force && recentlyFetched()) return;
            if (force) {
                Prefs.invalidateWeather(ctx);
                Prefs.invalidateWarn(ctx);
            }
            Catalog.refresh(ctx);
            List<Catalog.Feed> all = Catalog.load(ctx);
            Set<String> selected = Catalog.effective(all, Prefs.feeds(ctx, Prefs.APP));
            java.util.Map<String, Fetched> res = fetchAll(ctx, all, selected, Prefs.place(ctx, Prefs.APP), true);
            loadItems(ctx, Prefs.APP, true, res);
            lastFetchEnd = System.currentTimeMillis();
        }
    }

    /**
     * Ein gemeinsamer Abruf fuer alle Nachrichten-Widgets: die gewaehlten Quellen aller Widgets zusammen,
     * jede Quelle genau einmal; danach schreibt jedes Widget seinen Teil in den Zwischenspeicher.
     */
    static void syncWidgets(Context ctx, int[] ids) {
        if (ids.length == 0) return;
        synchronized (FETCH_LOCK) {
            Catalog.refresh(ctx);
            List<Catalog.Feed> all = Catalog.load(ctx);
            Set<String> union = new java.util.HashSet<>();
            for (int id : ids) union.addAll(Catalog.effective(all, Prefs.feeds(ctx, id)));
            java.util.Map<String, Fetched> res = fetchAll(ctx, all, union, Prefs.place(ctx, ids[0]), false);
            for (int id : ids) loadItems(ctx, id, true, res);
            lastFetchEnd = System.currentTimeMillis();
        }
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final int widgetId;
        private List<FeedItem> items = new ArrayList<>();
        private Set<String> readSet = new java.util.HashSet<>();
        private Set<String> seenSet = new java.util.HashSet<>();
        private boolean hasSeen;
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
            items = load(false, null);
        }

        @Override
        public void onDataSetChanged() {
            // nie ins Netz: Neuabrufe laufen nur ueber den Wecker oder den Aktualisieren-Knopf (Updater).
            // Ein Reiterwechsel, eine Groessenaenderung oder "gelesen" liest nur den Zwischenspeicher.
            Prefs.takeLocalOnly(ctx);
            items = load(false, null);
            WidgetProvider.refreshHeader(ctx, widgetId);
        }

        private List<FeedItem> load(boolean online, java.util.Map<String, Fetched> res) {
            // Gemerkt-Ansicht: gelesen = nach dem Merken geoeffnet (gleich wie in der App)
            readSet = Prefs.widgetShows(ctx, widgetId) == 2 ? Prefs.savedReadSet(ctx) : Prefs.readSet(ctx);
            hasSeen = Prefs.hasSeen(ctx);
            seenSet = Prefs.seenSet(ctx);
            kwList = Keywords.list(ctx);
            airy = Prefs.density(ctx, widgetId) == 1;
            sizeIdx = Prefs.size(ctx, widgetId);
            lightTheme = Prefs.isLight(ctx, widgetId);
            if (Prefs.widgetShows(ctx, widgetId) == 2) return savedItems(ctx);
            List<FeedItem> all = loadItems(ctx, widgetId, online, res);
            List<FeedItem> list = grouped(ctx, new ArrayList<>(all.subList(0, Math.min(all.size(), 60))));
            return list;
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        private RemoteViews headerView(FeedItem it) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            rv.setInt(R.id.item_card, "setBackgroundResource", 0);
            rv.setViewVisibility(R.id.item_accent, android.view.View.GONE);
            rv.setViewVisibility(R.id.item_meta, android.view.View.GONE);
            rv.setViewVisibility(R.id.item_dot, android.view.View.GONE);
            rv.setViewVisibility(R.id.item_plus, android.view.View.GONE);
            // Zeilen werden vom Widget wiederverwendet: Vorschaubild einer frueheren Zeile darf nicht stehen bleiben
            rv.setViewVisibility(R.id.item_thumb, android.view.View.GONE);
            rv.setImageViewBitmap(R.id.item_thumb, null);
            rv.setTextViewText(R.id.item_title, Style.font(ctx, widgetId, it.title.toUpperCase(java.util.Locale.GERMANY)));
            rv.setTextViewTextSize(R.id.item_title, android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
            Pal.text(ctx, widgetId, rv, R.id.item_title, -1, 0.8f);
            float dp = ctx.getResources().getDisplayMetrics().density;
            rv.setViewPadding(R.id.item_card, Math.round(4 * dp), Math.round(8 * dp), Math.round(4 * dp), 0);
            rv.setViewPadding(R.id.item_root, 0, 0, 0, 0);
            rv.setOnClickFillInIntent(R.id.item_root, new Intent());
            return rv;
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
            if (it.header) return headerView(it);
            int tx = Prefs.text(ctx, widgetId);
            int rgb = Prefs.color(ctx, widgetId);
            boolean read = Prefs.isRead(readSet, it.link);
            float dim = read ? 0.55f : 1f;
            String meta = it.meta();
            boolean hit = Keywords.matches(kwList, it);
            // wiederverwendete Zeile kann vorher eine Ueberschrift gewesen sein: alles ausdruecklich zuruecksetzen
            rv.setViewVisibility(R.id.item_accent, android.view.View.VISIBLE);
            rv.setViewVisibility(R.id.item_meta, android.view.View.VISIBLE);
            rv.setTextViewText(R.id.item_title, Style.font(ctx, widgetId, hit ? "★ " + it.title : it.title));
            
            rv.setTextViewTextSize(R.id.item_title, android.util.TypedValue.COMPLEX_UNIT_SP, Style.titleSp(sizeIdx));
            rv.setTextViewTextSize(R.id.item_meta, android.util.TypedValue.COMPLEX_UNIT_SP, Style.metaSp(sizeIdx));
            rv.setInt(R.id.item_card, "setBackgroundResource", Pal.cardBg(ctx, widgetId));
            if (hit) rv.setTextColor(R.id.item_title, Prefs.argb(0xFFD166, tx, dim)); else Pal.text(ctx, widgetId, rv, R.id.item_title, -1, dim);
            int sub = Prefs.colorSub(ctx, widgetId);
            Pal.set(ctx, widgetId, rv, R.id.item_meta, Style.font(ctx, widgetId, meta + " · " + FeedItem.shortSource(it.feedName)), sub, Prefs.colorSub2(ctx, widgetId), 0.7f * dim);
            boolean isNew = Prefs.widgetShows(ctx, widgetId) != 2 && isNew(hasSeen, seenSet, readSet, it);
            rv.setInt(R.id.item_accent, "setColorFilter", isNew ? 0xFFE53935 : it.accentColor());
            rv.setInt(R.id.item_accent, "setImageAlpha", read ? 90 : 255);
            rv.setViewVisibility(R.id.item_plus, it.plus ? android.view.View.VISIBLE : android.view.View.GONE);
            rv.setViewVisibility(R.id.item_dot, isNew ? android.view.View.VISIBLE : android.view.View.GONE);
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
            fill.putExtra(PreviewActivity.EXTRA_IMAGE, it.image);
            rv.setOnClickFillInIntent(R.id.item_root, fill);
            // Vorschaubild nur aus dem Speicher (kein Netz im Widget-Aufbau); ohne Bild bleibt die Zeile wie vorher
            android.graphics.Bitmap th = it.image.isEmpty() ? null : ImageStore.cached(ctx, it.image, 140);
            if (th != null) {
                rv.setImageViewBitmap(R.id.item_thumb, ImageStore.square(th, Math.round(46 * dp), 8 * dp));
                rv.setViewVisibility(R.id.item_thumb, android.view.View.VISIBLE);
                rv.setInt(R.id.item_thumb, "setImageAlpha", read ? 130 : 255);
            } else {
                rv.setViewVisibility(R.id.item_thumb, android.view.View.GONE);
                rv.setImageViewBitmap(R.id.item_thumb, null);
            }
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
