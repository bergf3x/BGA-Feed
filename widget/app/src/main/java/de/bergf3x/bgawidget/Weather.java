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
    static final String[] PLACES = {"Berchtesgaden", "Ramsau bei Berchtesgaden", "Bischofswiesen"};
    private static final double[][] COORDS = {{47.6297, 13.0036}, {47.6333, 12.8833}, {47.6417, 12.9833}};

    private static String url(int place) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + COORDS[place][0]
                + "&longitude=" + COORDS[place][1]
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=Europe%2FBerlin&forecast_days=4";
    }

    private static final long MAX_AGE = 15 * 60 * 1000L;

    int temp;
    int code;
    boolean day = true;
    int days;
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
        boolean fresh = Prefs.weather(c, place) != null
                && System.currentTimeMillis() - Prefs.weatherTime(c, place) < MAX_AGE;
        if (fresh) return false;
        try {
            String json = Net.download(url(place));
            if (parse(json) == null) return false;
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
            JSONObject d = o.getJSONObject("daily");
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
