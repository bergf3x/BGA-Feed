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

    /** Cache-Buster, damit raw.githubusercontent.com nicht minutenlang Altes liefert. */
    static String fresh(String url) {
        return url + "?t=" + (System.currentTimeMillis() / 60000L);
    }
}
