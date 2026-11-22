/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Feed-Daten als Dateien (statt in den Einstellungen) und einmal geparst im Speicher. */
final class FeedCache {
    private static final class Entry {
        long stamp;
        List<FeedItem> items;
    }

    private static final Map<String, Entry> MEM = new HashMap<>();

    private FeedCache() {}

    private static File file(Context c, String id) {
        File d = new File(c.getFilesDir(), "feeds");
        d.mkdirs();
        return new File(d, id.replaceAll("[^A-Za-z0-9_-]", "_") + ".xml");
    }

    private static String readFile(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** Gespeicherte Feed-XML; null, wenn es noch keine gibt (alte Einstellungs-Ablage wird mitgelesen). */
    static synchronized String read(Context c, String id) {
        File f = file(c, id);
        if (f.exists()) return readFile(f);
        return Prefs.sp(c).getString("cache_" + id, null);
    }

    static synchronized void put(Context c, String id, String xml, List<FeedItem> items) {
        File f = file(c, id);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(xml.getBytes("UTF-8"));
            out.getFD().sync();
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        Entry e = new Entry();
        e.stamp = f.lastModified() + f.length();
        e.items = items;
        MEM.put(id, e);
        if (Prefs.sp(c).contains("cache_" + id)) Prefs.sp(c).edit().remove("cache_" + id).apply();
    }

    /** Geparste Artikel des Feeds; leer, wenn es nichts gibt. */
    static synchronized List<FeedItem> items(Context c, String id, String name) {
        File f = file(c, id);
        long stamp = f.exists() ? f.lastModified() + f.length() : -1;
        Entry e = MEM.get(id);
        if (e != null && e.stamp == stamp) return e.items;
        String xml = read(c, id);
        List<FeedItem> list = xml == null ? new ArrayList<FeedItem>() : FeedItem.parse(xml, name);
        e = new Entry();
        e.stamp = stamp;
        e.items = list;
        MEM.put(id, e);
        return list;
    }
}
