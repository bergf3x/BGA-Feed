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
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&hourly=freezing_level_height"
                + "&timezone=Europe%2FBerlin&forecast_days=4";
    }

    /** Temperatur auf Berghoehe (Open-Meteo rechnet mit "elevation" auf diese Hoehe um). */
    static final int MOUNTAIN_M = 1800;

    private static String mountainUrl(int place) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + COORDS[place][0]
                + "&longitude=" + COORDS[place][1]
                + "&current=temperature_2m&elevation=" + MOUNTAIN_M
                + "&timezone=Europe%2FBerlin&forecast_days=1";
    }

    static final int NONE = Integer.MIN_VALUE;

    private static final long MAX_AGE = 15 * 60 * 1000L;

    int temp;
    int code;
    boolean day = true;
    int days;
    int mtn = NONE;      // Temperatur auf MOUNTAIN_M
    int freeze = NONE;   // Nullgradgrenze in m
    int precip = NONE;   // Niederschlagswahrscheinlichkeit heute in %
    int wind = NONE;     // km/h
    int gust = NONE;     // km/h
    final int[] hi = new int[4];
    final int[] lo = new int[4];
    final int[] dayCode = new int[4];
    final String[] dayName = new String[4];

    private Weather() {}

    /** Gespeicherten Stand lesen; null, wenn es noch keinen gibt. */
    static Weather load(Context c, int place) {
        return parse(Prefs.weather(c, place));
    }

    /** Im Hintergrund-Thread aufrufen. true, wenn neue Daten gespeichert wurden. */
    static boolean refreshIfStale(Context c, int place) {
        String cached = Prefs.weather(c, place);
        // Alter Stand ohne die neuen Felder (Wind/Regenrisiko) gilt als veraltet
        boolean fresh = cached != null && cached.contains("wind_speed_10m")
                && System.currentTimeMillis() - Prefs.weatherTime(c, place) < MAX_AGE;
        if (fresh) return false;
        try {
            String json;
            try {
                json = Net.download(url(place));
            } catch (Exception ex) {
                // Notfall: Grundversion ohne Zusatzfelder
                json = Net.download(url(place).replace(",wind_speed_10m,wind_gusts_10m", "")
                        .replace(",precipitation_probability_max", "").replace("&hourly=freezing_level_height", ""));
            }
            if (parse(json) == null) return false;
            try {
                JSONObject m = new JSONObject(Net.download(mountainUrl(place)));
                double t = m.getJSONObject("current").getDouble("temperature_2m");
                JSONObject o = new JSONObject(json);
                o.put("mtn", t);
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
        StringBuilder sb = new StringBuilder();
        if (precip != NONE) sb.append("Regenrisiko ").append(precip).append(" %");
        if (wind != NONE) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append("Wind ").append(wind);
            if (gust != NONE && gust > wind + 5) sb.append(" (Böen ").append(gust).append(")");
            sb.append(" km/h");
        }
        if (mtn != NONE) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(MOUNTAIN_M).append(" m: ").append(deg(mtn));
            if (freeze != NONE) sb.append("  ·  0°-Grenze ").append(freeze).append(" m");
        } else if (freeze != NONE) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("0°-Grenze ").append(freeze).append(" m");
        }
        return sb.toString();
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
