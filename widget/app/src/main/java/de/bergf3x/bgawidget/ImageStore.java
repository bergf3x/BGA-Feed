/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

/**
 * Vorschaubilder der Meldungen: Laden, verkleinert auf der Platte merken, schnell anzeigen.
 * Einstellung "Vorschaubilder": 0 aus, 1 nur im WLAN (Standard), 2 immer. Gemerkte Bilder werden auch ohne WLAN gezeigt.
 */
final class ImageStore {
    private ImageStore() { }

    static final int OFF = 0, WIFI = 1, ALWAYS = 2;
    private static final int STORE_W = 480;          // gespeicherte Breite (Pixel)
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final long MAX_AGE = 7L * 24 * 3600000;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    /** Adressen, die zuletzt nicht klappten: bis zu dieser Zeit nicht erneut versuchen. */
    private static final ConcurrentHashMap<String, Long> FAILED = new ConcurrentHashMap<>();

    static int mode(Context c) {
        return Prefs.imgMode(c);
    }

    /** Darf jetzt aus dem Netz geladen werden? */
    static boolean mayDownload(Context c) {
        int m = mode(c);
        if (m == OFF) return false;
        if (m == ALWAYS) return Net.online(c);
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected() && !cm.isActiveNetworkMetered();
        } catch (Exception e) {
            return false;
        }
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "img");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static File file(Context c, String url) {
        CRC32 crc = new CRC32();
        crc.update(url.getBytes());
        return new File(dir(c), Long.toHexString(crc.getValue()) + "_" + url.length() + ".jpg");
    }

    /** Liegt das Bild schon auf der Platte? */
    static boolean has(Context c, String url) {
        return url != null && !url.isEmpty() && file(c, url).exists();
    }

    /** Nur aus Speicher/Platte, nie aus dem Netz; null, wenn nicht vorhanden. Breite in Pixel. */
    static Bitmap cached(Context c, String url, int w) {
        if (url == null || url.isEmpty() || mode(c) == OFF) return null;
        String key = url + "@" + w;
        Bitmap b = MEM.get(key);
        if (b != null) return b;
        File f = file(c, url);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= w) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            b = BitmapFactory.decodeFile(f.getPath(), d);
            if (b != null) {
                //noinspection ResultOfMethodCallIgnored
                f.setLastModified(System.currentTimeMillis());
                MEM.put(key, b);
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Laden (blockiert, nicht im Hauptthread): zuerst Platte, sonst Netz, wenn erlaubt. Ergebnis ist die gemerkte Datei. */
    static boolean fetch(Context c, String url) {
        if (url == null || url.isEmpty() || mode(c) == OFF) return false;
        File f = file(c, url);
        if (f.exists()) return true;
        Long bad = FAILED.get(url);
        if (bad != null && System.currentTimeMillis() < bad) return false;
        if (!mayDownload(c)) return false;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "BGAWidget/2.3 (privates Nachrichten-Widget, Android)");
            con.setRequestProperty("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8");
            if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
            String type = con.getContentType();
            if (type != null && !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) throw new java.io.IOException("kein Bild");
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 6_000_000) throw new java.io.IOException("Bild zu gross");
            }
            byte[] data = out.toByteArray();
            Net.countBytes(data.length);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth < 40 || o.outHeight < 40) throw new java.io.IOException("Bild zu klein");
            int s = 1;
            while (o.outWidth / (s * 2) >= STORE_W) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, d);
            if (bm == null) throw new java.io.IOException("nicht lesbar");
            File tmp = new File(f.getPath() + "." + Thread.currentThread().getId() + ".tmp");
            FileOutputStream fo = new FileOutputStream(tmp);
            bm.compress(Bitmap.CompressFormat.JPEG, 82, fo);
            fo.close();
            bm.recycle();
            if (!tmp.renameTo(f)) {
                tmp.delete();
                if (!f.exists()) throw new java.io.IOException("Speichern fehlgeschlagen");
            }
            android.util.Log.i("BGAimg", "Bild ok " + data.length / 1024 + " KB " + o.outWidth + "x" + o.outHeight + " " + url);
            if (f.getName().hashCode() % 7 == 0) prune(c);
            return true;
        } catch (Throwable e) {
            FAILED.put(url, System.currentTimeMillis() + 30 * 60000L);
            android.util.Log.i("BGAimg", "Bild nicht geladen: " + e + " " + url);
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /**
     * Bild in eine ImageView laden. Ohne Bild-Adresse, bei "aus" oder wenn nichts gemerkt ist und gerade nicht geladen
     * werden darf, wird die View ausgeblendet (kein leerer Platz). Klappt das Laden nicht, verschwindet sie ebenfalls.
     */
    static void into(final ImageView iv, final String url, final int w, final boolean reserve) {
        final Context c = iv.getContext().getApplicationContext();
        iv.setTag(url);
        Bitmap b = cached(c, url, w);
        if (b != null) {
            iv.setImageBitmap(b);
            iv.setVisibility(View.VISIBLE);
            return;
        }
        if (url == null || url.isEmpty() || !mayDownload(c)) {
            iv.setVisibility(View.GONE);
            return;
        }
        iv.setImageDrawable(null);
        iv.setVisibility(reserve ? View.VISIBLE : View.GONE); // reserve: Platz bleibt frei, solange das Bild kommt
        POOL.execute(() -> {
            final boolean had = has(c, url);
            final boolean ok = fetch(c, url);
            if (ok && !had) widgetsChanged(c);
            final Bitmap bm = ok ? cached(c, url, w) : null;
            UI.post(() -> {
                if (!url.equals(iv.getTag())) return; // View wurde inzwischen fuer eine andere Meldung benutzt
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    iv.setVisibility(View.VISIBLE);
                } else {
                    iv.setVisibility(View.GONE);
                }
            });
        });
    }

    private static volatile long lastWidgetPing;

    /**
     * Ein Bild kam neu auf die Platte (z. B. durch die App): Widget-Listen neu zeichnen, damit die Vorschaubilder
     * dort auch erscheinen. Hoechstens einmal pro Sekunde, damit viele Bilder nicht viele Neuzeichnungen ausloesen.
     */
    private static void widgetsChanged(final Context c) {
        long now = System.currentTimeMillis();
        if (now - lastWidgetPing < 1000) return;
        lastWidgetPing = now;
        UI.postDelayed(() -> {
            try {
                android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(c);
                for (int wid : mgr.getAppWidgetIds(new android.content.ComponentName(c, WidgetProvider.class))) {
                    mgr.notifyAppWidgetViewDataChanged(wid, R.id.list);
                }
            } catch (Exception ignored) {
            }
        }, 1500);
    }

    /** Abgerundete Ecken per Umriss (App-Ansichten). */
    static void round(View v, final float radiusPx) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
    }

    /** Fuer RemoteViews (Widget): zugeschnittenes, abgerundetes Quadrat. */
    static Bitmap square(Bitmap src, int px, float radiusPx) {
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int side = Math.min(src.getWidth(), src.getHeight());
        android.graphics.Rect from = new android.graphics.Rect((src.getWidth() - side) / 2, (src.getHeight() - side) / 2,
                (src.getWidth() + side) / 2, (src.getHeight() + side) / 2);
        RectF to = new RectF(0, 0, px, px);
        cv.drawRoundRect(to, radiusPx, radiusPx, p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        cv.drawBitmap(src, from, to, p);
        return out;
    }

    /** Die obersten Meldungen im Hintergrund vorladen (nur wenn erlaubt, also im WLAN). Meldet true, wenn etwas neu kam. */
    static void prefetch(final Context ctx, final List<FeedItem> items, final int max, final Runnable whenNew) {
        final Context c = ctx.getApplicationContext();
        if (!mayDownload(c)) return;
        final java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        for (FeedItem it : items) {
            if (urls.size() >= max) break;
            if (!it.header && !it.image.isEmpty() && !has(c, it.image) && !urls.contains(it.image)) urls.add(it.image);
        }
        if (urls.isEmpty()) return;
        POOL.execute(() -> {
            int fresh = 0;
            for (String u : urls) if (fetch(c, u)) fresh++;
            if (fresh > 0 && whenNew != null) whenNew.run();
        });
    }

    /** Platte begrenzen: alte Dateien (7 Tage) weg, bei Ueberschreitung die aeltesten zuerst. */
    private static void prune(Context c) {
        try {
            File[] fs = dir(c).listFiles();
            if (fs == null) return;
            long now = System.currentTimeMillis(), total = 0;
            java.util.Arrays.sort(fs, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
            java.util.ArrayList<File> keep = new java.util.ArrayList<>();
            for (File f : fs) {
                if (now - f.lastModified() > MAX_AGE) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                } else {
                    keep.add(f);
                    total += f.length();
                }
            }
            for (File f : keep) {
                if (total <= MAX_BYTES * 8 / 10) break;
                long len = f.length();
                if (f.delete()) total -= len;
            }
        } catch (Throwable ignored) {
        }
    }
}
