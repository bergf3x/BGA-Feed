/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Net {
    private Net() {}

    static String download(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "BGAWidget/2.3 (privates Nachrichten-Widget, Android)");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 4_000_000) throw new IOException("Antwort zu gross");
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Ergebnis eines Bildabrufs: Daten, Aufnahmezeit laut Server (0 = unbekannt), notModified = Bild unveraendert. */
    static final class Img {
        byte[] data;
        long modified;
        boolean notModified;
    }

    /** Bild laden (max. 8 MB). ifModifiedSince > 0 spart Daten, wenn sich nichts geaendert hat (HTTP 304). */
    static Img image(String url, long ifModifiedSince) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "BGAWidget/2.3 (privates Nachrichten-Widget, Android)");
        c.setRequestProperty("Accept", "image/jpeg,image/png,image/*;q=0.8");
        c.setRequestProperty("Cache-Control", "no-cache");
        if (ifModifiedSince > 0) c.setIfModifiedSince(ifModifiedSince);
        try {
            int code = c.getResponseCode();
            Img r = new Img();
            if (code == 304) {
                r.notModified = true;
                return r;
            }
            if (code != 200) throw new IOException("HTTP " + code);
            r.modified = c.getLastModified();
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 8_000_000) throw new IOException("Bild zu gross");
            }
            r.data = out.toByteArray();
            return r;
        } finally {
            c.disconnect();
        }
    }

    /** Cache-Buster, damit raw.githubusercontent.com nicht minutenlang Altes liefert. */
    static String fresh(String url) {
        return url + "?t=" + (System.currentTimeMillis() / 60000L);
    }

    private static final String RAW = "https://raw.githubusercontent.com/bergf3x/BGA-Feed/main/";
    private static final String MIRROR = "https://cdn.jsdelivr.net/gh/bergf3x/BGA-Feed@main/";

    /** Spiegel der GitHub-Datei bei jsDelivr (eigenes Netz, gleiche Inhalte); leer, wenn es keinen gibt. */
    static String mirror(String url) {
        return url != null && url.startsWith(RAW) ? MIRROR + url.substring(RAW.length()) : "";
    }

    /** Ersatz-Feed: erst GitHub, faellt das aus, der jsDelivr-Spiegel. */
    static String downloadFeed(String url) throws IOException {
        try {
            return download(fresh(url));
        } catch (IOException e) {
            String m = mirror(url);
            if (m.isEmpty()) throw e;
            lastMirror = true;
            return download(m);
        }
    }

    /** true, wenn zuletzt der Spiegel einspringen musste (fuer das Protokoll). */
    static volatile boolean lastMirror;
}
