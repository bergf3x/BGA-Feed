/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;

/** Bewegung in der App: Seitenwischen an/aus, Animationen reduzieren (Standard: wie in den Systemeinstellungen). */
final class Motion {
    private Motion() { }

    private static android.content.SharedPreferences sp(Context c) {
        return c.getSharedPreferences("bga_widget", Context.MODE_PRIVATE);
    }

    static boolean swipe(Context c) {
        return sp(c).getBoolean("page_swipe", true);
    }

    static void setSwipe(Context c, boolean on) {
        sp(c).edit().putBoolean("page_swipe", on).apply();
    }

    /** Animationen reduzieren: eigene Wahl, sonst Systemeinstellung (Animationsfaktor 0 = aus). */
    static boolean reduced(Context c) {
        if (sp(c).contains("reduce_motion")) return sp(c).getBoolean("reduce_motion", false);
        return system(c);
    }

    static boolean system(Context c) {
        try {
            return android.provider.Settings.Global.getFloat(c.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    static void setReduced(Context c, boolean on) {
        // gleich wie das System: keine eigene Wahl speichern, damit spaetere Systemaenderungen wirken
        if (on == system(c)) sp(c).edit().remove("reduce_motion").apply();
        else sp(c).edit().putBoolean("reduce_motion", on).apply();
    }
}
