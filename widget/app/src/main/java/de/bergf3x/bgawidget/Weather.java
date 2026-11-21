package de.bergf3x.bgawidget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Wetter fuer Berchtesgaden von Open-Meteo (kostenlos, ohne Schluessel). */
final class Weather {
    /** Orte zur Auswahl: Name, Breite, Laenge. */
    static final String[] PLACES = {"Berchtesgaden", "Ramsau bei Berchtesgaden", "Bischofswiesen", "Schönau am Königssee"};
    private static final double[][] COORDS = {{47.6297, 13.0036}, {47.6333, 12.8833}, {47.6417, 12.9833}, {47.6050, 12.9870}};

    private static String url(int place) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + COORDS[place][0]
                + "&longitude=" + COORDS[place][1]
                + "&current=temperature_2m,weather_code,is_day,wind_speed_10m,wind_gusts_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                + "&hourly=freezing_level_height,temperature_2m,precipitation_probability,weather_code"
                + "&timezone=Europe%2FBerlin&forecast_days=4";
    }

    /** Notfall-Abruf ohne Zusatzfelder. */
    private static String basicUrl(int place) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + COORDS[place][0]
                + "&longitude=" + COORDS[place][1]
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=Europe%2FBerlin&forecast_days=4";
    }

    /** Temperatur auf Berghoehe (Open-Meteo rechnet mit "elevation" auf diese Hoehe um). */
    static final int MOUNTAIN_M = 1800;
    /** Hausberg je Ort: Name und Hoehe (Temperatur wird auf diese Hoehe umgerechnet). */
    static final String[] PEAK_N = {"Watzmann", "Hochkalter", "Untersberg", "Jenner"};
    static final int[] PEAK_M = {2713, 2607, 1972, 1874};

    private static String mountainUrl(int place) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + COORDS[place][0]
                + "&longitude=" + COORDS[place][1]
                + "&current=temperature_2m&elevation=" + PEAK_M[place]
                + "&timezone=Europe%2FBerlin&forecast_days=1";
    }

    static final int NONE = Integer.MIN_VALUE;

    private static final long MAX_AGE = 15 * 60 * 1000L;

    int temp;
    int code;
    boolean day = true;
    int days;
    int place = 0;
    String avalanche = "";
    int mtn = NONE;      // Temperatur auf MOUNTAIN_M
    int freeze = NONE;   // Nullgradgrenze in m
    int precip = NONE;   // Niederschlagswahrscheinlichkeit heute in %
    int wind = NONE;     // km/h
    int gust = NONE;     // km/h
    String sunrise = "";
    String sunset = "";
    int hourStart = 0;
    int[] hrTemp = new int[0];
    int[] hrProb = new int[0];
    int[] hrCode = new int[0];
    final int[] hi = new int[4];
    final int[] lo = new int[4];
    final int[] dayCode = new int[4];
    final String[] dayName = new String[4];

    private Weather() {}

    /** Gespeicherten Stand lesen; null, wenn es noch keinen gibt. */
    static Weather load(Context c, int place) {
        Weather w = parse(Prefs.weather(c, place));
        if (w != null && System.currentTimeMillis() - Prefs.avalTime(c) < 12L * 3600000) w.avalanche = Prefs.avalText(c);
        return w;
    }

    /** Im Hintergrund-Thread aufrufen. true, wenn neue Daten gespeichert wurden. */
    static boolean refreshIfStale(Context c, int place) {
        String cached = Prefs.weather(c, place);
        // Alter Stand ohne die neuen Felder (Wind/Regenrisiko) gilt als veraltet
        boolean fresh = cached != null && cached.contains("sunrise") && cached.contains("\"peak\"")
                && System.currentTimeMillis() - Prefs.weatherTime(c, place) < MAX_AGE;
        if (fresh) return false;
        try {
            String json;
            try {
                json = Net.download(url(place));
            } catch (Exception ex) {
                // Notfall: Grundversion ohne Zusatzfelder
                json = Net.download(basicUrl(place));
            }
            if (parse(json) == null) return false;
            try {
                JSONObject m = new JSONObject(Net.download(mountainUrl(place)));
                double t = m.getJSONObject("current").getDouble("temperature_2m");
                JSONObject o = new JSONObject(json);
                o.put("mtn", t);
                o.put("peak", place);
                json = o.toString();
            } catch (Exception ignored) {
                // Bergwetter ist optional; Tal-Wetter wird trotzdem gespeichert
            }
            Prefs.setWeather(c, place, json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Weather parse(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject cur = o.getJSONObject("current");
            Weather w = new Weather();
            w.temp = (int) Math.round(cur.getDouble("temperature_2m"));
            w.code = cur.getInt("weather_code");
            w.day = cur.optInt("is_day", 1) == 1;
            w.place = Math.max(0, Math.min(PEAK_M.length - 1, o.optInt("peak", 0)));
            if (o.has("mtn")) w.mtn = (int) Math.round(o.optDouble("mtn", NONE));
            if (cur.has("wind_speed_10m")) w.wind = (int) Math.round(cur.optDouble("wind_speed_10m", NONE));
            if (cur.has("wind_gusts_10m")) w.gust = (int) Math.round(cur.optDouble("wind_gusts_10m", NONE));
            try {
                String ct = cur.optString("time", "");
                int hour = ct.length() >= 13 ? Integer.parseInt(ct.substring(11, 13)) : 12;
                JSONArray fz = o.getJSONObject("hourly").getJSONArray("freezing_level_height");
                if (hour < fz.length() && !fz.isNull(hour)) w.freeze = (int) Math.round(fz.getDouble(hour));
            } catch (Exception ignored) {
            }
            JSONObject d = o.getJSONObject("daily");
            try {
                String sr = d.getJSONArray("sunrise").optString(0, "");
                String ss = d.getJSONArray("sunset").optString(0, "");
                if (sr.length() >= 16) w.sunrise = sr.substring(11, 16);
                if (ss.length() >= 16) w.sunset = ss.substring(11, 16);
            } catch (Exception ignored) {
            }
            try {
                String ct2 = cur.optString("time", "");
                w.hourStart = ct2.length() >= 13 ? Integer.parseInt(ct2.substring(11, 13)) : 0;
                JSONObject h = o.getJSONObject("hourly");
                JSONArray ht = h.getJSONArray("temperature_2m");
                JSONArray hp = h.optJSONArray("precipitation_probability");
                JSONArray hc = h.optJSONArray("weather_code");
                int n2 = ht.length();
                w.hrTemp = new int[n2];
                w.hrProb = new int[n2];
                w.hrCode = new int[n2];
                for (int i = 0; i < n2; i++) {
                    w.hrTemp[i] = (int) Math.round(ht.optDouble(i, 0));
                    w.hrProb[i] = hp != null && !hp.isNull(i) ? hp.optInt(i, NONE) : NONE;
                    w.hrCode[i] = hc != null && !hc.isNull(i) ? hc.optInt(i, 3) : 3;
                }
            } catch (Exception ignored) {
                w.hrTemp = new int[0];
            }
            try {
                JSONArray pp = d.getJSONArray("precipitation_probability_max");
                if (pp.length() > 0 && !pp.isNull(0)) w.precip = pp.getInt(0);
            } catch (Exception ignored) {
            }
            JSONArray time = d.getJSONArray("time");
            JSONArray codes = d.getJSONArray("weather_code");
            JSONArray max = d.getJSONArray("temperature_2m_max");
            JSONArray min = d.getJSONArray("temperature_2m_min");
            int n = Math.min(Math.min(time.length(), codes.length()), Math.min(max.length(), min.length()));
            w.days = Math.min(n, 4);
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
            SimpleDateFormat out = new SimpleDateFormat("EE", Locale.GERMANY);
            for (int i = 0; i < w.days; i++) {
                w.hi[i] = (int) Math.round(max.getDouble(i));
                w.lo[i] = (int) Math.round(min.getDouble(i));
                w.dayCode[i] = codes.getInt(i);
                Date dt = in.parse(time.getString(i));
                w.dayName[i] = dt == null ? "" : out.format(dt).replace(".", "");
            }
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    /** Zeile mit Niederschlag, Wind, Bergtemperatur und Nullgradgrenze; leer, wenn nichts da ist. */
    String info() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder l1 = new StringBuilder();
        if (precip != NONE) l1.append("Regen ").append(precip).append(" %");
        if (wind != NONE) {
            if (l1.length() > 0) l1.append("   ·   ");
            l1.append("Wind ").append(wind);
            if (gust != NONE && gust > wind + 5) l1.append("–").append(gust);
            l1.append(" km/h");
        }
        if (l1.length() > 0) lines.add(l1.toString());
        if (!sunrise.isEmpty() && !sunset.isEmpty()) lines.add("Sonne " + sunrise + " – " + sunset);
        if (mtn != NONE) lines.add(PEAK_N[place] + " (" + PEAK_M[place] + " m): " + deg(mtn));
        if (freeze != NONE) {
            // grobe Faustregel: Schneefallgrenze etwa 300 m unter der Nullgradgrenze
            int snow = Math.max(0, Math.round((freeze - 300) / 50f) * 50);
            lines.add("Nullgrad " + freeze + " m   ·   Schnee ab ca. " + snow + " m");
        }
        if (avalanche != null && !avalanche.isEmpty()) lines.add(avalanche);
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(l);
        }
        return sb.toString();
    }

    /** Zeilen "14 Uhr · 5° · Regen 40 % · Bewölkt" fuer die naechsten Stunden. */
    java.util.List<String> hourlyLines(int count) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = hourStart; i < hrTemp.length && out.size() < count; i++) {
            int h = i % 24;
            boolean dayTime = h >= 7 && h <= 18;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(java.util.Locale.GERMANY, "%02d Uhr", h)).append("   ")
                    .append(deg(hrTemp[i]));
            if (hrProb[i] != NONE && hrProb[i] > 0) sb.append("   Regen ").append(hrProb[i]).append(" %");
            sb.append("   ").append(text(hrCode[i], dayTime));
            out.add(sb.toString());
        }
        return out;
    }

    /** Hintergrundfarbe (opak) fuer die Wetter-Zeile. */
    static int tint(int code, boolean day) {
        if (code >= 95) return 0xFF7A5BC0;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return 0xFFBFD9F2;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return 0xFF4A7FC1;
        if (code == 45 || code == 48) return 0xFF9AA5A8;
        if (code == 3) return 0xFF8A9BB0;
        if (!day) return 0xFF3A4A8C;
        if (code == 0 || code == 1) return 0xFFFFB84D;
        return 0xFF7FA6D6;
    }

    static String deg(int v) {
        return v + "°";
    }

    static int icon(int code, boolean day) {
        if (code == 0 || code == 1) return day ? R.drawable.w_sun : R.drawable.w_moon;
        if (code == 2) return day ? R.drawable.w_partly : R.drawable.w_cloud;
        if (code == 45 || code == 48) return R.drawable.w_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.w_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.w_snow;
        if (code >= 95) return R.drawable.w_storm;
        return R.drawable.w_cloud;
    }

    static String text(int code, boolean day) {
        if (code == 0) return day ? "Sonnig" : "Klar";
        if (code == 1) return "Heiter";
        if (code == 2) return "Teils bewölkt";
        if (code == 3) return "Bedeckt";
        if (code == 45 || code == 48) return "Nebel";
        if (code >= 51 && code <= 57) return "Nieselregen";
        if (code >= 61 && code <= 67) return "Regen";
        if (code >= 71 && code <= 77) return "Schnee";
        if (code >= 80 && code <= 82) return "Regenschauer";
        if (code == 85 || code == 86) return "Schneeschauer";
        if (code >= 95) return "Gewitter";
        return "Wolkig";
    }
}
