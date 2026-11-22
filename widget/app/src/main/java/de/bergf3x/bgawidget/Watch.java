/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Quellen-Waechter: merkt sich bei jedem Direktabruf, ob eine Quelle noch sauber liefert
 * (Abruf klappt, Zahl der Meldungen normal, Bilder da, regelmaessig Neues) und meldet es in "Info > Quellen".
 */
final class Watch {
    private Watch() { }

    static final int OK = 0, WARN = 1, BAD = 2, IDLE = 3;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    private static JSONObject get(Context c, String id) {
        try {
            return new JSONObject(sp(c).getString(id, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void put(Context c, String id, JSONObject o) {
        sp(c).edit().putString(id, o.toString()).apply();
    }

    /** Abruf gelungen: Zahl der Meldungen, wie viele davon ein Bild haben, wie viele neu sind. */
    static synchronized void ok(Context c, String id, int count, int withImg, int fresh) {
        try {
            JSONObject o = get(c, id);
            long now = System.currentTimeMillis();
            int n = o.optInt("n");
            double avg = o.optDouble("avg", count);
            double imgAvg = o.optDouble("imgAvg", count == 0 ? 0 : 100.0 * withImg / count);
            double pct = count == 0 ? 0 : 100.0 * withImg / count;
            o.put("ok", now);
            o.put("try", now);
            o.put("count", count);
            o.put("img", withImg);
            o.put("fails", 0);
            o.put("err", "");
            o.put("n", n + 1);
            o.put("avg", n == 0 ? count : avg * 0.8 + count * 0.2);
            o.put("imgAvg", n == 0 ? pct : imgAvg * 0.8 + pct * 0.2);
            if (fresh > 0 || !o.has("new")) o.put("new", now);
            put(c, id, o);
        } catch (Exception ignored) {
        }
    }

    /** Direktabruf misslungen (GitHub-Feed springt ein). */
    static synchronized void fail(Context c, String id, String err) {
        try {
            JSONObject o = get(c, id);
            o.put("try", System.currentTimeMillis());
            o.put("fails", o.optInt("fails") + 1);
            o.put("err", err == null ? "" : err);
            put(c, id, o);
        } catch (Exception ignored) {
        }
    }

    /** Zustand der Quelle: {Stufe als Text, Beschreibung}. */
    static String[] status(Context c, Catalog.Feed f) {
        if (!Direct.handles(f)) return new String[]{String.valueOf(IDLE), "Wird über den GitHub-Feed geholt"};
        JSONObject o = get(c, f.id);
        if (!o.has("try")) return new String[]{String.valueOf(IDLE), "Noch nicht abgerufen"};
        long now = System.currentTimeMillis();
        int fails = o.optInt("fails");
        int count = o.optInt("count");
        double avg = o.optDouble("avg", count);
        int n = o.optInt("n");
        if (fails >= 3) {
            return new String[]{String.valueOf(BAD), "Direktabruf klappt seit " + fails + " Versuchen nicht" + errText(o)
                    + ". Der GitHub-Feed springt ein, kann aber älter sein."};
        }
        if (fails >= 1) {
            return new String[]{String.valueOf(WARN), "Letzter Direktabruf fehlgeschlagen" + errText(o) + ". GitHub-Feed übernimmt."};
        }
        if (n >= 3 && count < avg * 0.4) {
            return new String[]{String.valueOf(WARN), "Nur " + count + " Meldungen statt etwa " + Math.round(avg)
                    + ". Hat die Seite ihren Aufbau geändert?"};
        }
        if (n >= 3 && o.optDouble("imgAvg", 0) >= 50 && o.optInt("img") * 100.0 / Math.max(1, count) < 20) {
            return new String[]{String.valueOf(WARN), "Fast keine Vorschaubilder mehr gefunden. Aufbau der Seite geändert?"};
        }
        long fresh = o.optLong("new", 0);
        if (fresh > 0 && !Catalog.lowVolume(f.id) && now - fresh > 48L * 3600000) {
            return new String[]{String.valueOf(WARN), "Seit " + (now - fresh) / 3600000L + " Std. keine neue Meldung."};
        }
        String when = new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(new Date(o.optLong("ok")));
        return new String[]{String.valueOf(OK), "Läuft · " + count + " Meldungen"
                + (o.optInt("img") > 0 ? ", " + o.optInt("img") + " mit Bild" : "") + " · zuletzt " + when};
    }

    private static String errText(JSONObject o) {
        String e = o.optString("err");
        if (e.isEmpty()) return "";
        if (e.length() > 50) e = e.substring(0, 50);
        return " (" + e + ")";
    }

    /** Kurzer Hinweis fuer die Kopfzeile, wenn mindestens eine Quelle rot ist; sonst leer. */
    static String badSummary(Context c) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Catalog.Feed f : Catalog.load(c)) {
                if (!Direct.handles(f)) continue;
                String[] s = status(c, f);
                if (Integer.parseInt(s[0]) == BAD) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(f.name);
                }
            }
        } catch (Exception ignored) {
        }
        return sb.length() == 0 ? "" : "Quelle gestört: " + sb + " (Info > Quellen)";
    }
}
