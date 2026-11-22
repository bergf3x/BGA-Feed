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

    // ---- Zaehler fuer den Probelauf (Anfragen, unveraenderte Antworten, Bytes) ----
    private static final java.util.concurrent.atomic.AtomicInteger REQ = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger NOT_MOD = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong BYTES = new java.util.concurrent.atomic.AtomicLong();

    /** Fuer die Fehlersuche: adb shell setprop log.tag.BGAnet DEBUG, dann steht jede Anfrage im Logcat. */
    private static void dbg(int code, long bytes, String ne, String nl, boolean cond, String url) {
        if (android.util.Log.isLoggable("BGAnet", android.util.Log.DEBUG)) {
            android.util.Log.d("BGAnet", code + " " + bytes + "B etag=" + (ne != null) + " lm=" + (nl != null)
                    + " cond=" + cond + " " + url);
        }
    }

    static void resetStats() {
        REQ.set(0);
        NOT_MOD.set(0);
        BYTES.set(0);
    }

    static String stats() {
        return "Anfragen=" + REQ.get() + " unveraendert=" + NOT_MOD.get() + " Bytes=" + BYTES.get();
    }

    // ---- Zwischenspeicher fuer Textseiten: Server antwortet bei Unveraendertem nur mit "304", ohne Inhalt ----
    private static volatile java.io.File cacheDir;
    private static volatile boolean pruned;

    static void init(android.content.Context c) {
        if (cacheDir != null) return;
        java.io.File d = new java.io.File(c.getApplicationContext().getCacheDir(), "net");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        cacheDir = d;
        if (!pruned) {
            pruned = true;
            // Eintraege aelter als 3 Tage aufraeumen
            long limit = System.currentTimeMillis() - 3L * 86400000L;
            java.io.File[] fs = d.listFiles();
            if (fs != null) {
                long total = 0;
                for (java.io.File f : fs) {
                    if (f.lastModified() < limit) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    } else {
                        total += f.length();
                    }
                }
                // hoechstens ~12 MB: aelteste zuerst entfernen
                if (total > 12_000_000) {
                    java.util.Arrays.sort(fs, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
                    for (java.io.File f : fs) {
                        if (total <= 8_000_000) break;
                        if (f.exists()) {
                            total -= f.length();
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                }
            }
        }
    }

    /** Hat das Handy gerade Internet? Ohne Berechtigung oder bei Fehlern: ja (dann einfach versuchen). */
    static boolean online(android.content.Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            android.net.Network n = cm.getActiveNetwork();
            if (n == null) return false;
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc == null || nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    /** Schluessel ohne den Minuten-Zusatz "?t=…" (der soll nur Zwischenspeicher im Netz umgehen). */
    private static String key(String url) {
        String k = url.replaceAll("[?&]t=\\d+$", "");
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(k.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(k.hashCode());
        }
    }

    private static String readFile(java.io.File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeFile(java.io.File f, String s) {
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
            o.write(s.getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    static String download(String url) throws IOException {
        return download(url, true);
    }

    /** cache = false fuer Einzelseiten, die nur einmal gelesen werden (Artikel): kein Zwischenspeicher dafuer. */
    static String download(String url, boolean cache) throws IOException {
        java.io.File dir = cache ? cacheDir : null;
        String k = dir == null ? null : key(url);
        java.io.File body = k == null ? null : new java.io.File(dir, k + ".body");
        java.io.File meta = k == null ? null : new java.io.File(dir, k + ".meta");
        String etag = null, lastMod = null;
        if (body != null && body.exists() && meta.exists()) {
            String m = readFile(meta);
            if (m != null) {
                String[] p = m.split("\n", -1);
                etag = p.length > 0 && !p[0].isEmpty() ? p[0] : null;
                lastMod = p.length > 1 && !p[1].isEmpty() ? p[1] : null;
            }
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "BGAWidget/2.3 (privates Nachrichten-Widget, Android)");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
        if (etag != null) c.setRequestProperty("If-None-Match", etag);
        if (lastMod != null) c.setRequestProperty("If-Modified-Since", lastMod);
        REQ.incrementAndGet();
        try {
            int code = c.getResponseCode();
            if (code == 304 && body != null) {
                String cached = readFile(body);
                if (cached != null) {
                    NOT_MOD.incrementAndGet();
                    //noinspection ResultOfMethodCallIgnored
                    body.setLastModified(System.currentTimeMillis());
                    dbg(304, 0, etag, lastMod, true, url);
                    return cached;
                }
            }
            if (code != 200) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 4_000_000) throw new IOException("Antwort zu gross");
            }
            BYTES.addAndGet(out.size());
            String text = out.toString("UTF-8");
            String ne = c.getHeaderField("ETag"), nl = c.getHeaderField("Last-Modified");
            dbg(200, out.size(), ne, nl, etag != null || lastMod != null, url);
            if (body != null && (ne != null || nl != null) && out.size() <= 1_500_000) {
                writeFile(body, text);
                writeFile(meta, (ne == null ? "" : ne) + "\n" + (nl == null ? "" : nl));
            }
            return text;
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
