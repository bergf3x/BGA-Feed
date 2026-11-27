/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

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
        /** true, wenn page nur eine gemeinsame Seite fuer mehrere Kameras ist (Link nicht kamera-genau). */
        boolean generic;
        /** 360-Grad-Panorama (sehr breites Bild). */
        boolean pano;
        /** Mitte des Vorschau-Ausschnitts im Panorama (Anteil der Bildbreite). */
        float panX = 0.5f;
        /** Hoehe in m (0 = unbekannt) und Beschreibung/Schlagwoerter fuer die Suche (aus der Kameraliste). */
        int elev;
        String desc = "";
        /** Nur Webseite, kein Bild in der App. */
        final boolean link;

        Cam(String id, String name, String source, String page, boolean small, String... urls) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.page = page;
            this.small = small;
            this.urls = urls;
            this.link = urls.length == 0;
        }
    }

    /** Region in der Liste; ids verweisen auf Cam.id. chip = Kurzname fuer den Reiter (null = kein Reiter). */
    static final class Folder {
        final String name;
        final String chip;
        final String[] ids;

        Folder(String name, String chip, String... ids) {
            this.name = name;
            this.chip = chip;
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
        Cam c = new Cam("fe_" + n, name + " (Vorschaubild)", "Feratel", "https://www.berchtesgaden.de/webcams", true,
                "https://wtvthmb.feratel.com/thumbnails/" + n + ".jpeg?t=38&design=v5");
        c.generic = true;
        return c;
    }

    private static Cam panomax(int id, String sub, String name) {
        return new Cam("px_" + id, name, "Panomax", "https://" + sub + ".panomax.com/", false,
                "https://panodata.panomax.com/cams/" + id + "/preview_og.jpg");
    }

    /** Roundshot-Kamera: Kennung aus der Kamera-Seite; Vorschau = /thumbnail, Bild = /medium (je per Weiterleitung). */
    private static Cam roundshot(String host, String slug, int id, String name, float panX) {
        String base = "https://" + host + ".roundshot.com/";
        Cam c = new Cam("rs_" + slug, name, "Roundshot", base + slug + "/", false, base + "cams/" + id + "/medium");
        c.thumb = base + "cams/" + id + "/thumbnail";
        c.pano = panX >= 0;
        c.panX = panX;
        return c;
    }

    /** Panomax-Kamera der Region Cervinia (Seite cervinia.panomax.com/<slug>). */
    private static Cam cervinia(String slug, int id, String name) {
        return new Cam("px_" + id, name, "Panomax", "https://cervinia.panomax.com/" + slug, false,
                "https://panodata.panomax.com/cams/" + id + "/preview_og.jpg");
    }

    private static Cam link(String id, String name, String source, String page) {
        return new Cam("lk_" + id, name, source, page, false);
    }

    private static final Cam[] BASE = {
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
            panomax(7, "grossglockner", "Kaiser-Franz-Josefs-Höhe"),
            panomax(16, "fuschertoerl", "Fuschertörl"),
            panomax(17, "edelweissspitze", "Edelweißspitze"),
            foto("freiwandeck", "Freiwandeck – Blick zum Großglockner"),
            foto("wallackhaus", "Wallackhaus Süd"),
            foto("wallackhaus-nord", "Wallackhaus Nord"),
            roundshot("zbag", "rothorn", 1045, "Rothorn", 0.56f),
            roundshot("zbag", "matterhornglacierparadise", 964, "Matterhorn Glacier Paradise", 0.3f),
            roundshot("zbag", "trockener-steg", 1044, "Trockener Steg", 0.17f),
            roundshot("zbag", "hohtaelli", 1043, "Hohtälli", 0.63f),
            roundshot("zbag", "hirli", 1047, "Hirli", 0.14f),
            roundshot("zbag", "sunnegga", 1049, "Sunnegga", -1f),
            roundshot("zbag", "riffelberg", 1048, "Riffelberg", -1f),
            roundshot("zbag", "schwarzsee", 2090, "Schwarzsee", 0.27f),
            roundshot("zermatt", "gornergrat", 1042, "Gornergrat", 0.48f),
            roundshot("zermatt", "monterosa", 1551, "Monte Rosa", 0.4f),
            roundshot("zbag", "blauherd", 2091, "Blauherd", 0.74f),
            cervinia("plateau-rosa", 931, "Plateau Rosa (Cervinia)"),
            cervinia("bec-carre", 1256, "Bec Carré (Cervinia)"),
            cervinia("matterhorn", 867, "Matterhorn (Cervinia)"),
            // nur Webseite (kein Standbild abrufbar)
            link("avisec_kfjh", "Kaiser-Franz-Josefs-Haus", "avisec", "https://avisec.com/feed/kfjh"),
            link("avisec_glocknerhaus", "Glocknerhaus", "avisec", "https://avisec.com/feed/glocknerhaus"),
            link("jenner_berg", "Jenner Bergstation", "Nur Webseite · bergfex", "https://www.bergfex.com/jenner-schoenau-am-koenigssee/webcams/c23475/"),
            link("obersalzberg", "Obersalzberg – Watzmann im Blick", "Nur Webseite · blickfix", "https://www.blickfix.com/webcams/webcam-obersalzberg-watzmann-im-blick?type=17023"),
            link("hochlenzer", "Hochlenzer – Schönau und Watzmann", "Nur Webseite · bergfex", "https://www.bergfex.com/berchtesgaden-gutshof-obersalzberg/webcams/c2341/"),
            link("kehlstein", "Kehlstein", "Nur Webseite · bergfex", "https://www.bergfex.com/jenner-schoenau-am-koenigssee/webcams/c18989/"),
            link("nationalpark", "Nationalpark: Jenner, Funtensee, Ramsau", "Nur Webseite · Nationalpark", "https://nationalpark-berchtesgaden.bayern.de/english/service/media/webcam/index.htm"),
            link("dir_schoenau", "Alle Webcams Schönau", "Nur Webseite · bergfex", "https://www.bergfex.com/bayern/langlaufen/schoenau-am-koenigssee/webcams/"),
            link("dir_bgd", "Alle Webcams Berchtesgaden", "Nur Webseite · berchtesgaden.de", "https://www.berchtesgaden.de/webcams"),
    };

    /** Alle Kameras (eingebaute + aus der Kameraliste geladene) und die Regionen daraus. */
    static volatile Cam[] CAMS = BASE;
    static volatile Folder[] FOLDERS = new Folder[0];
    /** true, wenn eine neue Kameraliste geladen wurde, aber noch nicht in die Anzeige uebernommen ist. */
    static volatile boolean dirty;
    private static volatile java.util.Map<String, Cam> EXTRA = new java.util.LinkedHashMap<>();

    /**
     * Regionen in Anzeigereihenfolge: {Name, Reiter (leer = keiner), eingebaute Kameras, Kameras aus der Liste (Kennungen von foto-webcam.eu)}.
     * Zuordnung nach Lage; die Eintraege der Liste stammen aus einer von Hand getroffenen Bergauswahl.
     */
    private static final String[][] REGIONS = {
            {"Jenner und Königssee", "Jenner", "fw_jenner-sued fw_jenner-ost ls_koenigssee fe_3004 fe_3009 fe_3345 lk_jenner_berg", ""},
            {"Berchtesgaden und Obersalzberg", "Berchtesgaden", "th_hoegl fe_3005 px_rossfeld fw_windbeutelbaron fe_3007 lk_obersalzberg lk_hochlenzer lk_kehlstein", ""},
            {"Ramsau und Hintersee", "Ramsau", "th_hintersee fe_3006 th_stoisser-alm", "vorderloiplsau"},
            {"Nationalpark und Hütten", "Nationalpark", "fw_funtenseetauern fw_funtensee fw_stoehrhaus fw_stahlhaus th_neue-traunsteiner-huette lk_nationalpark", ""},
            {"Bad Reichenhall", "Bad Reichenhall", "", "hochstaufen salzburg"},
            {"Chiemgau", "Chiemgau", "", "wendelstein-ost wendelstein-west hochfelln hochfelln-alpen traunstein-sued traunstein rauschberg hochries-sued hochries-nord hochries hochries-ost kampenwand schleching samerberg"},
            {"Tegernsee und Voralpen", "Tegernsee", "", "buchstein kochelsee wallberg pendling-ost pendling-west tegernseewest bruennsteinhaus gsteig"},
            {"Garmisch und Zugspitze", "Zugspitze", "", "bzb-gipfel zugspitze-sued zugspitze zugspitze-nord zugspitze-ost sonnalpin meilerhuette-haus meilerhuette wank kandahar-express kreuzeck eibsee-nord hausberg bzb-tal eibsee-hotel"},
            {"Allgäu", "Allgäu", "", "nebelhorn fellhorn tegelberg"},
            {"Salzburger Land", "Salzburg", "px_16 px_17", "sonnblick kleinfleisskees hochkoenig rauris wasserfallboden rudolfshuette thomaseck obertauern2 mooserboden asitz obertauern1 hundsfeldsee gernkogel sonnblickbasis hackelhuette hackelhuette-nord zellamsee schwaigerhaus"},
            {"Salzkammergut", "Salzkammergut", "", "gjaidstein simonyhuette hochleckenhaus attersee attersee2"},
            {"Zillertal", "Zillertal", "", "tuxertal tristner gamshuette schlegeis"},
            {"Nordtirol", "Nordtirol", "", "hintereisferner2 hintereisferner1 tisenjoch jamtalferner bockkogel braunschweigerhuette russkopf sulzkogel wannenkogel bremerhuette st-anton innsbruck toelzer-huette trins wilder-kaiser st-johann-in-tirol-west st-johann-in-tirol"},
            {"Osttirol", "Osttirol", "", "kreuzspitze lucknerhaus grossglockner-zoom glocknerwinkel lienz dolomitenhuette kalsertal virgen-nord"},
            {"Vorarlberg", "Vorarlberg", "", "ruefikopf gargellen-sued gargellen-west diedamskopf koerbersee lindauerhuette furkajoch dornbirn"},
            {"Kärnten", "Kärnten", "px_7 fw_freiwandeck fw_wallackhaus fw_wallackhaus-nord lk_avisec_kfjh lk_avisec_glocknerhaus",
                    "adlersruhe oberwalderhuette gletscher-sued ankogel-sued gletscher-nord feldkopf feldkopf-west ankogel-nord hagener-huette polinik liesgelespitz dobratsch falkert-nord ederplan falkert zechneralm turrach-ost turrach-west heidi-chalets lorenzalm schulterkogel poertschach-a-w"},
            {"Steiermark", "Steiermark", "", "hochkar sarstein krakau hotel-lindenhof grimming"},
            {"Österreich (übrige)", "Österreich", "", "pfaelzerhuette passlueg"},
            {"Südtirol und Italien", "Italien", "", "langenferner becherhaus ridnaun weissbrunnferner rieserferner chavalatsch kronplatz rittnerhorn trafoi zallinger dreizinnenblick hochmuth gummer", "px_931 px_1256 px_867"},
            {"Schweiz", "Schweiz", "rs_rothorn rs_matterhornglacierparadise rs_trockener-steg rs_hohtaelli rs_hirli rs_sunnegga rs_riffelberg rs_schwarzsee rs_gornergrat rs_monterosa rs_blauherd", "konkordiahuette glecksteinhuette pizol wildhaus hoherkasten hochhamm brunnen"},
            {"Webcam-Verzeichnisse", "", "lk_dir_schoenau lk_dir_bgd", ""},
    };

    static {
        rebuild();
    }

    private static String[] words(String s) {
        return s.trim().isEmpty() ? new String[0] : s.trim().split("\\s+");
    }

    /** CAMS und FOLDERS aus den eingebauten Kameras und der geladenen Kameraliste neu bilden. */
    static synchronized void rebuild() {
        java.util.Map<String, Cam> ex = EXTRA;
        java.util.List<Cam> all = new java.util.ArrayList<>(java.util.Arrays.asList(BASE));
        all.addAll(ex.values());
        CAMS = all.toArray(new Cam[0]);
        java.util.List<Folder> fs = new java.util.ArrayList<>();
        for (String[] r : REGIONS) {
            java.util.List<String> ids = new java.util.ArrayList<>(java.util.Arrays.asList(words(r[2])));
            java.util.List<Cam> ext = new java.util.ArrayList<>();
            for (String slug : words(r[3])) {
                Cam c = ex.get("fw_" + slug);
                if (c != null) ext.add(c);
            }
            java.util.Collections.sort(ext, (a, b) -> Integer.compare(b.elev, a.elev));
            for (Cam c : ext) ids.add(c.id);
            if (r.length > 4) ids.addAll(java.util.Arrays.asList(words(r[4])));
            fs.add(new Folder(r[0], r[1].isEmpty() ? null : r[1], ids.toArray(new String[0])));
        }
        FOLDERS = fs.toArray(new Folder[0]);
        dirty = false;
    }

    // ------------------------------------------------------------ Kameraliste von foto-webcam.eu

    private static final String INDEX_URL = "https://www.foto-webcam.eu/";
    private static final int INDEX_FMT = 2;
    private static final long INDEX_MAX_AGE = 3 * 86400000L;

    private static java.io.File indexFile(Context c) {
        return new java.io.File(c.getFilesDir(), "cam_index.json");
    }

    private static java.util.Set<String> wantedSlugs() {
        java.util.Set<String> w = new java.util.HashSet<>();
        for (String[] r : REGIONS) java.util.Collections.addAll(w, words(r[3]));
        // auch die eingebauten foto-webcam-Kameras: Titel, Schlagwoerter und Hoehe fuer die Suche
        for (Cam bc : BASE) if (bc.id.startsWith("fw_")) w.add(bc.id.substring(3));
        return w;
    }

    /** true, wenn es noch keine gespeicherte Liste gibt oder sie aelter als 3 Tage ist. */
    static boolean indexStale(Context c) {
        java.io.File f = indexFile(c);
        // Liste ohne die eingebauten Kameras (aelter als Format 2): einmal neu laden
        if (Prefs.sp(c).getInt("cam_idx_fmt", 1) < INDEX_FMT) return true;
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > INDEX_MAX_AGE;
    }

    /**
     * Uebersichtsseite von foto-webcam.eu laden, die eingebettete Kameraliste lesen und nur die ausgewaehlten
     * Kameras lokal speichern (Hintergrund-Thread). Wirft bei Fehler; dann bleibt die alte Liste.
     */
    static void fetchIndex(Context c) throws Exception {
        String h = Net.download(INDEX_URL, false);
        String key = "var metadata= new Object(";
        int i = h.indexOf(key);
        if (i < 0) throw new IOException("Keine Kameraliste gefunden");
        org.json.JSONObject root = (org.json.JSONObject) new org.json.JSONTokener(h.substring(i + key.length())).nextValue();
        org.json.JSONArray arr = root.getJSONArray("cams");
        java.util.Set<String> want = wantedSlugs();
        org.json.JSONArray out = new org.json.JSONArray();
        String[] keep = {"id", "name", "title", "keywords", "offline", "hidden", "imgurl", "link", "elevation", "partner"};
        for (int k = 0; k < arr.length(); k++) {
            org.json.JSONObject j = arr.getJSONObject(k);
            if (!want.contains(j.optString("id"))) continue;
            org.json.JSONObject o = new org.json.JSONObject();
            for (String f : keep) if (j.has(f)) o.put(f, j.get(f));
            out.put(o);
        }
        if (out.length() < 20) throw new IOException("Kameraliste unvollstaendig");
        java.io.File f = indexFile(c);
        java.io.File tmp = new java.io.File(f.getPath() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(out.toString().getBytes("UTF-8"));
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("Speichern fehlgeschlagen");
        }
        Prefs.sp(c).edit().putInt("cam_idx_fmt", INDEX_FMT).apply();
        dirty = true;
    }

    private static String hostOf(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? "" : h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return "";
        }
    }

    /** Gespeicherte Kameraliste lesen und in CAMS/FOLDERS uebernehmen (ohne Netz). */
    static void loadIndex(Context c) {
        java.util.Map<String, Cam> ex = new java.util.LinkedHashMap<>();
        try {
            java.io.File f = indexFile(c);
            if (f.exists()) {
                byte[] b = new byte[(int) f.length()];
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                    int n = 0, r;
                    while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
                }
                org.json.JSONArray arr = new org.json.JSONArray(new String(b, "UTF-8"));
                java.util.Set<String> baseIds = new java.util.HashSet<>();
                java.util.Map<String, Cam> baseById = new java.util.HashMap<>();
                for (Cam bc : BASE) {
                    baseIds.add(bc.id);
                    baseById.put(bc.id, bc);
                }
                for (int k = 0; k < arr.length(); k++) {
                    org.json.JSONObject j = arr.getJSONObject(k);
                    String slug = j.optString("id");
                    if (slug.isEmpty()) continue;
                    Cam own = baseById.get("fw_" + slug);
                    if (own != null) {
                        // eingebaute Kamera: nur Suchtext und Hoehe aus der Liste uebernehmen
                        own.elev = j.optInt("elevation", 0);
                        own.desc = j.optString("title") + " " + j.optString("keywords");
                        continue;
                    }
                    if (j.optBoolean("offline") || j.optBoolean("hidden")) continue;
                    String name = j.optString("name", slug);
                    String img = j.optString("imgurl");
                    String link = j.optString("link");
                    Cam cam;
                    if (!j.optBoolean("partner") && img.startsWith("https://www.foto-webcam.eu/webcam/" + slug + "/current/")) {
                        cam = foto(slug, name);
                    } else {
                        if (img.isEmpty()) continue;
                        String page = link.startsWith("http") ? link : "https://www.foto-webcam.eu/webcam/" + slug + "/";
                        cam = new Cam("fw_" + slug, name, hostOf(page), page, true, img);
                    }
                    cam.elev = j.optInt("elevation", 0);
                    cam.desc = j.optString("title") + " " + j.optString("keywords");
                    ex.put(cam.id, cam);
                }
            }
        } catch (Exception ignored) {
            ex.clear();
        }
        EXTRA = ex;
        rebuild();
    }

    static Cam byId(String id) {
        for (Cam c : CAMS) if (c.id.equals(id)) return c;
        return null;
    }

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
        for (Cam cam : BASE) keep.add(cam.id);
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

    /** "vor 4 Min." / "vor 2 Std." / "vor 3 Tagen". */
    static String agoText(long t) {
        long diff = System.currentTimeMillis() - t;
        if (diff < 60000L) return "gerade eben";
        if (diff < 3600000L) return "vor " + diff / 60000L + " Min.";
        if (diff < 86400000L) return "vor " + diff / 3600000L + " Std.";
        long d = diff / 86400000L;
        return "vor " + d + (d == 1 ? " Tag" : " Tagen");
    }

    /** "Aufnahme vor 4 Min." bzw. "Abgerufen vor 4 Min." aus den gespeicherten Zeiten; leer, wenn es noch kein Bild gibt. */
    static String agoLabel(Context c, Cam cam, boolean thumb) {
        if (cam.link) return "";
        File f = file(c, cam, thumb);
        if (!f.exists()) return "";
        String k = key(cam, thumb);
        long taken = Prefs.sp(c).getLong("cam_t_" + k, 0);
        if (taken > 0) return "Aufnahme " + agoText(taken);
        return "Abgerufen " + agoText(Prefs.sp(c).getLong("cam_f_" + k, f.lastModified()));
    }

    /** Link dieser Kamera ueber das Teilen-Menue weitergeben. */
    static void share(android.app.Activity a, Cam cam) {
        try {
            android.content.Intent s = new android.content.Intent(android.content.Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(android.content.Intent.EXTRA_SUBJECT, cam.name);
            s.putExtra(android.content.Intent.EXTRA_TEXT, cam.name + " (" + cam.source + ")\n" + cam.page);
            a.startActivity(android.content.Intent.createChooser(s, "Webcam teilen"));
        } catch (Exception ignored) {
        }
    }

    /** Statuszeile unter dem Bild. */
    static String status(Shot s) {
        if (s.bmp == null) return s.offline ? "Kein Bild verfügbar" + (s.err.isEmpty() ? "" : " (" + s.err + ")") : "Lade …";
        String base = s.taken > 0 ? "Aufnahme " + when(s.taken) : "Abgerufen " + when(s.fetched);
        return s.offline ? base + "  ·  offline (gespeichertes Bild)" : base;
    }
}
