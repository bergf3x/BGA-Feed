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

        Cam(String id, String name, String source, String page, boolean small, String... urls) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.page = page;
            this.small = small;
            this.urls = urls;
        }
    }

    private static Cam foto(String slug, String name) {
        String base = "https://www.foto-webcam.eu/webcam/" + slug + "/current/";
        return new Cam("fw_" + slug, name, "foto-webcam.eu", "https://www.foto-webcam.eu/webcam/" + slug + "/", false,
                base + "1920.jpg", base + "1200.jpg");
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

    private static File file(Context c, Cam cam) {
        File dir = new File(c.getCacheDir(), "cams");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, cam.id + ".jpg");
    }

    /** Zuletzt gespeichertes Bild (ohne Netz); bmp ist null, wenn es noch keins gibt. */
    static Shot cached(Context c, Cam cam, int maxW) {
        Shot s = new Shot();
        File f = file(c, cam);
        synchronized (LOCK) {
            if (f.exists()) s.bmp = decode(f, maxW);
        }
        if (f.exists()) {
            s.taken = Prefs.sp(c).getLong("cam_t_" + cam.id, 0);
            s.fetched = Prefs.sp(c).getLong("cam_f_" + cam.id, f.lastModified());
        }
        return s;
    }

    /** Aktuelles Bild holen (Hintergrund-Thread). Bei Fehler bleibt das gespeicherte Bild, offline = true. */
    static Shot refresh(Context c, Cam cam, int maxW) {
        Shot cur = cached(c, cam, maxW);
        File f = file(c, cam);
        String err = "";
        for (String url : cam.urls) {
            try {
                Net.Img im = Net.image(url, f.exists() ? cur.taken : 0);
                long now = System.currentTimeMillis();
                if (im.notModified && f.exists()) {
                    Prefs.sp(c).edit().putLong("cam_f_" + cam.id, now).apply();
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
                Prefs.sp(c).edit().putLong("cam_t_" + cam.id, taken).putLong("cam_f_" + cam.id, now).apply();
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
