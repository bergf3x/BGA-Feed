/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Webcams mit festem Standbild: Liste, Abruf, Zwischenspeicher und Aufnahmezeit. */
final class Webcams {
    static final class Cam {
        final String id;
        final String name;
        final String source;
        final String page;
        final String[] urls;
        final boolean small;
        /** Kleines Rasterbild (nur wo die Quelle es anbietet), sonst null: dann dient das grosse Bild. */
        String thumb;

        Cam(String id, String name, String source, String page, boolean small, String... urls) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.page = page;
            this.small = small;
            this.urls = urls;
        }
    }

    /** Ordner (Regionen) in der Auswahl; ids verweisen auf Cam.id. */
    static final class Folder {
        final String name;
        final String[] ids;

        Folder(String name, String... ids) {
            this.name = name;
            this.ids = ids;
        }
    }

    private static Cam foto(String slug, String name) {
        String base = "https://www.foto-webcam.eu/webcam/" + slug + "/current/";
        Cam c = new Cam("fw_" + slug, name, "foto-webcam.eu", "https://www.foto-webcam.eu/webcam/" + slug + "/", false,
                base + "1920.jpg", base + "1200.jpg");
        c.thumb = base + "400.jpg";
        return c;
    }

    private static Cam terra(String slug, String name) {
        String base = "https://www.terra-hd.de/" + slug + "/img/current/";
        return new Cam("th_" + slug, name, "terra-hd.de", "https://www.terra-hd.de/" + slug + "/", false,
                base + "1920.jpg", base + "1200.jpg");
    }

    private static Cam feratel(int n, String name) {
        return new Cam("fe_" + n, name + " (Vorschaubild)", "Feratel", "https://www.berchtesgaden.de/webcams", true,
                "https://wtvthmb.feratel.com/thumbnails/" + n + ".jpeg?t=38&design=v5");
    }

    static final Cam[] CAMS = {
            foto("jenner-sued", "Jenner Süd"),
            foto("jenner-ost", "Jenner Ost"),
            foto("funtenseetauern", "Funtenseetauern"),
            foto("funtensee", "Kärlingerhaus am Funtensee"),
            foto("stoehrhaus", "Stöhrhaus"),
            foto("stahlhaus", "Carl-von-Stahl-Haus"),
            foto("windbeutelbaron", "Windbeutelbaron"),
            terra("hoegl", "Högl"),
            terra("hintersee", "Hintersee"),
            terra("stoisser-alm", "Stoißer Alm"),
            terra("neue-traunsteiner-huette", "Neue Traunsteiner Hütte"),
            new Cam("ls_koenigssee", "Königssee", "livespotting.com / wetter.com", "https://www.berchtesgaden.de/webcams/webcam-koenigssee", false,
                    "https://wettercom-c1.livespotting.com/memfs/c61cba4f-68a3-488e-b2e4-ffb8e182794f/ee9cf6ad-1e23-47f2-ae2e-91aed7e21352/live/main.jpg"),
            new Cam("px_rossfeld", "Rossfeld Panoramastraße", "Panomax", "https://rossfeld.panomax.com/", true,
                    "https://panodata.panomax.com/cams/1028/preview_og.jpg"),
            feratel(3004, "Jennerbahn"),
            feratel(3009, "Jenneralm"),
            feratel(3005, "Högl"),
            feratel(3006, "Hochschwarzeck Ramsau"),
            feratel(3007, "Lockstein"),
            feratel(3345, "Götschen Talstation"),
    };

    /** Zuordnung der Kameras zu Ordnern (Vorschlag, ohne Pruefung der Lage jeder Kamera). Reihenfolge = Anzeige. */
    static final Folder[] FOLDERS = {
            new Folder("Jenner und Königssee", "fw_jenner-sued", "fw_jenner-ost", "ls_koenigssee", "fe_3004", "fe_3009", "fe_3345"),
            new Folder("Berchtesgaden und Obersalzberg", "th_hoegl", "fe_3005", "px_rossfeld", "fw_windbeutelbaron", "fe_3007"),
            new Folder("Ramsau und Hintersee", "th_hintersee", "fe_3006", "th_stoisser-alm"),
            new Folder("Nationalpark und Hütten", "fw_funtenseetauern", "fw_funtensee", "fw_stoehrhaus", "fw_stahlhaus", "th_neue-traunsteiner-huette"),
    };

    static int indexOf(String id) {
        for (int i = 0; i < CAMS.length; i++) if (CAMS[i].id.equals(id)) return i;
        return -1;
    }

    /** Kamera-Indizes eines Ordners (in Anzeigereihenfolge). */
    static int[] folderCams(Folder f) {
        int[] r = new int[f.ids.length];
        for (int i = 0; i < r.length; i++) r[i] = indexOf(f.ids[i]);
        return r;
    }

    static Folder folderOf(Cam c) {
        for (Folder f : FOLDERS) for (String id : f.ids) if (id.equals(c.id)) return f;
        return null;
    }

    // ------------------------------------------------------------ Einstellungen und Favoriten

    static final long FRESH_MS = 5 * 60000L;
    static final long LIMIT_BYTES = 50L * 1024 * 1024;

    /** Aktualisierung bei offener Ansicht in ms; 0 = nur von Hand. */
    static long intervalMs(Context c) {
        return Prefs.sp(c).getLong("cam_int", 60000L);
    }

    static void setIntervalMs(Context c, long ms) {
        Prefs.sp(c).edit().putLong("cam_int", ms).apply();
    }

    /** Tage, nach denen nicht angesehene Bilder geloescht werden. */
    static int keepDays(Context c) {
        return Prefs.sp(c).getInt("cam_keep", 14);
    }

    static void setKeepDays(Context c, int d) {
        Prefs.sp(c).edit().putInt("cam_keep", d).apply();
    }

    static boolean wifiOnly(Context c) {
        return Prefs.sp(c).getBoolean("cam_wifi", false);
    }

    static void setWifiOnly(Context c, boolean on) {
        Prefs.sp(c).edit().putBoolean("cam_wifi", on).apply();
    }

    /** false, wenn "nur im WLAN" gilt und gerade kein WLAN da ist. */
    static boolean mayFetch(Context c) {
        if (!wifiOnly(c)) return true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    || nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            return true;
        }
    }

    static java.util.Set<String> favs(Context c) {
        java.util.Set<String> r = new java.util.LinkedHashSet<>();
        String raw = Prefs.sp(c).getString("cam_fav", "");
        for (String p : raw.split(",")) if (!p.isEmpty()) r.add(p);
        return r;
    }

    static boolean isFav(Context c, Cam cam) {
        return favs(c).contains(cam.id);
    }

    static boolean toggleFav(Context c, Cam cam) {
        java.util.Set<String> f = favs(c);
        boolean on = !f.remove(cam.id);
        if (on) f.add(cam.id);
        Prefs.sp(c).edit().putString("cam_fav", android.text.TextUtils.join(",", f)).apply();
        return on;
    }

    /** Kamera wurde angesehen (fuers Aufraeumen). */
    static void viewed(Context c, Cam cam) {
        Prefs.sp(c).edit().putLong("cam_v_" + cam.id, System.currentTimeMillis()).apply();
    }

    // ------------------------------------------------------------ Speicher

    private static File dir(Context c) {
        File dir = new File(c.getCacheDir(), "cams");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static long cacheSize(Context c) {
        long n = 0;
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) n += f.length();
        return n;
    }

    static void clearCache(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private static String idOfFile(String name) {
        String n = name.endsWith(".jpg") ? name.substring(0, name.length() - 4) : name;
        return n.endsWith("_s") ? n.substring(0, n.length() - 2) : n;
    }

    /**
     * Aufraeumen: Favoriten und die fest eingebauten Kameras bleiben; alles andere wird nach keepDays ohne Ansehen
     * geloescht, danach (aelteste zuerst) bis das Limit eingehalten ist. Tempdateien werden immer entfernt.
     */
    static void cleanup(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs == null) return;
        java.util.Set<String> keep = favs(c);
        for (Cam cam : CAMS) keep.add(cam.id);
        long now = System.currentTimeMillis();
        long maxAge = keepDays(c) * 86400000L;
        java.util.List<File> rest = new java.util.ArrayList<>();
        long total = 0;
        for (File f : fs) {
            if (f.getName().endsWith(".tmp")) {
                f.delete();
                continue;
            }
            String id = idOfFile(f.getName());
            if (!keep.contains(id)) {
                long seen = Math.max(Prefs.sp(c).getLong("cam_v_" + id, 0), 0);
                if (seen == 0) seen = f.lastModified();
                if (now - seen > maxAge) {
                    f.delete();
                    continue;
                }
                rest.add(f);
            }
            total += f.length();
        }
        if (total > LIMIT_BYTES) {
            java.util.Collections.sort(rest, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : rest) {
                if (total <= LIMIT_BYTES) break;
                total -= f.length();
                f.delete();
            }
        }
    }

    /** Ergebnis eines Abrufs: Bild, Aufnahmezeit (0 = unbekannt), Abrufzeit, Fehlertext. */
    static final class Shot {
        Bitmap bmp;
        long taken;
        long fetched;
        boolean offline;
        String err = "";
    }

    private static final Object LOCK = new Object();

    private Webcams() {}

    /** Schluessel der Bildvariante: Rasterbild (nur wo vorhanden) oder grosses Bild. */
    private static String key(Cam cam, boolean thumb) {
        return thumb && cam.thumb != null ? cam.id + "_s" : cam.id;
    }

    private static File file(Context c, Cam cam, boolean thumb) {
        return new File(dir(c), key(cam, thumb) + ".jpg");
    }

    /** Alter des gespeicherten Bildes in ms (Long.MAX_VALUE = nie geladen). */
    static long age(Context c, Cam cam, boolean thumb) {
        File f = file(c, cam, thumb);
        if (!f.exists()) return Long.MAX_VALUE;
        long t = Prefs.sp(c).getLong("cam_f_" + key(cam, thumb), f.lastModified());
        return Math.max(0, System.currentTimeMillis() - t);
    }

    /** Zuletzt gespeichertes Bild (ohne Netz); bmp ist null, wenn es noch keins gibt. */
    static Shot cached(Context c, Cam cam, int maxW) {
        return cached(c, cam, maxW, false);
    }

    static Shot cached(Context c, Cam cam, int maxW, boolean thumb) {
        Shot s = new Shot();
        File f = file(c, cam, thumb);
        String k = key(cam, thumb);
        synchronized (LOCK) {
            if (f.exists()) s.bmp = decode(f, maxW);
        }
        if (f.exists()) {
            s.taken = Prefs.sp(c).getLong("cam_t_" + k, 0);
            s.fetched = Prefs.sp(c).getLong("cam_f_" + k, f.lastModified());
        }
        return s;
    }

    /** Aktuelles Bild holen (Hintergrund-Thread). Bei Fehler bleibt das gespeicherte Bild, offline = true. */
    static Shot refresh(Context c, Cam cam, int maxW) {
        return refresh(c, cam, maxW, false);
    }

    static Shot refresh(Context c, Cam cam, int maxW, boolean thumb) {
        Shot cur = cached(c, cam, maxW, thumb);
        if (!mayFetch(c)) {
            cur.offline = true;
            cur.err = "nur im WLAN";
            return cur;
        }
        File f = file(c, cam, thumb);
        String k = key(cam, thumb);
        String err = "";
        String[] urls = thumb && cam.thumb != null ? new String[]{cam.thumb} : cam.urls;
        for (String url : urls) {
            try {
                Net.Img im = Net.image(url, f.exists() ? cur.taken : 0);
                long now = System.currentTimeMillis();
                if (im.notModified && f.exists()) {
                    Prefs.sp(c).edit().putLong("cam_f_" + k, now).apply();
                    cur.fetched = now;
                    return cur;
                }
                if (!looksLikeImage(im.data)) {
                    err = "Kein Bild erhalten";
                    continue;
                }
                File tmp = new File(f.getPath() + ".tmp");
                synchronized (LOCK) {
                    try (FileOutputStream out = new FileOutputStream(tmp)) {
                        out.write(im.data);
                    }
                    if (!tmp.renameTo(f)) {
                        f.delete();
                        if (!tmp.renameTo(f)) throw new IOException("Speichern fehlgeschlagen");
                    }
                }
                long taken = im.modified > 0 && im.modified <= now + 60000 ? im.modified : 0;
                Prefs.sp(c).edit().putLong("cam_t_" + k, taken).putLong("cam_f_" + k, now).apply();
                Shot s = new Shot();
                synchronized (LOCK) {
                    s.bmp = decode(f, maxW);
                }
                if (s.bmp == null) {
                    err = "Bild nicht lesbar";
                    f.delete();
                    continue;
                }
                s.taken = taken;
                s.fetched = now;
                return s;
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
        }
        cur.offline = true;
        cur.err = err;
        return cur;
    }

    /** JPEG (FF D8) oder PNG (89 50) – schuetzt vor Fehlerseiten, die als "Bild" ankommen. */
    private static boolean looksLikeImage(byte[] d) {
        return d != null && d.length > 8
                && ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 || (d[0] & 0xFF) == 0x89 && d[1] == 0x50);
    }

    private static Bitmap decode(File f, int maxW) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxW && maxW > 0) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (Throwable t) {
            return null;
        }
    }

    /** "14:20 Uhr · vor 12 Min." bzw. "gestern 18:05 Uhr". */
    static String when(long t) {
        if (t <= 0) return "";
        long diff = System.currentTimeMillis() - t;
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        String clock = hm.format(new Date(t)) + " Uhr";
        if (diff < 0) return clock;
        if (diff < 3600000L) return clock + " · vor " + Math.max(1, diff / 60000) + " Min.";
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(t);
        a.set(Calendar.HOUR_OF_DAY, 0); a.set(Calendar.MINUTE, 0); a.set(Calendar.SECOND, 0); a.set(Calendar.MILLISECOND, 0);
        b.set(Calendar.HOUR_OF_DAY, 0); b.set(Calendar.MINUTE, 0); b.set(Calendar.SECOND, 0); b.set(Calendar.MILLISECOND, 0);
        long days = Math.round((a.getTimeInMillis() - b.getTimeInMillis()) / 86400000.0);
        if (days <= 0) return "heute " + clock;
        if (days == 1) return "gestern " + clock;
        return new SimpleDateFormat("dd.MM.", Locale.GERMANY).format(new Date(t)) + " " + clock;
    }

    /** Statuszeile unter dem Bild. */
    static String status(Shot s) {
        if (s.bmp == null) return s.offline ? "Kein Bild verfügbar" + (s.err.isEmpty() ? "" : " (" + s.err + ")") : "Lade …";
        String base = s.taken > 0 ? "Aufnahme " + when(s.taken) : "Abgerufen " + when(s.fetched);
        return s.offline ? base + "  ·  offline (gespeichertes Bild)" : base;
    }
}
