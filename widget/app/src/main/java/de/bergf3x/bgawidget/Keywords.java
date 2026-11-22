/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Eigene Stichwoerter: passende Artikel werden hervorgehoben und melden sich auf Wunsch per Benachrichtigung. */
final class Keywords {
    private Keywords() {}

    static List<String> list(Context c) {
        List<String> out = new ArrayList<>();
        for (String k : Prefs.keywords(c).split("[,;\\n]")) {
            String t = k.trim().toLowerCase(Locale.GERMANY);
            if (t.length() >= 3) out.add(t);
        }
        return out;
    }

    static boolean matches(List<String> kw, FeedItem it) {
        if (kw.isEmpty()) return false;
        String h = (it.title + " " + it.description).toLowerCase(Locale.GERMANY);
        for (String k : kw) if (h.contains(k)) return true;
        return false;
    }
}
