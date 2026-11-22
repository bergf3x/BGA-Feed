/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.content.Context;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Kurze Vibrations-Rueckmeldung an wichtigen Stellen. Haelt sich an die Systemeinstellung
 * "Beruehrungsfeedback" (performHapticFeedback) und an den eigenen Schalter in der App.
 */
final class Haptic {
    private Haptic() { }

    /** sehr leicht: Reiter/Thema waehlen */
    static final int TICK = 0;
    /** Schalter an/aus */
    static final int TOGGLE_ON = 1;
    static final int TOGGLE_OFF = 2;
    /** Bestaetigung: Aktualisieren ausgeloest/fertig, gemerkt */
    static final int CONFIRM = 3;
    /** Fehler, z. B. kein Netz */
    static final int REJECT = 4;
    /** kurzer, klarer Klick (Ausloese-Stelle beim Ziehen) */
    static final int CLICK = 5;

    static boolean enabled(Context c) {
        return c.getSharedPreferences("bga_widget", Context.MODE_PRIVATE).getBoolean("haptic", true);
    }

    static void set(Context c, boolean on) {
        c.getSharedPreferences("bga_widget", Context.MODE_PRIVATE).edit().putBoolean("haptic", on).apply();
    }

    static void play(View v, int kind) {
        if (v == null || !enabled(v.getContext())) return;
        int c;
        switch (kind) {
            case TOGGLE_ON:
                c = Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.KEYBOARD_TAP;
                break;
            case TOGGLE_OFF:
                c = Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.TOGGLE_OFF : HapticFeedbackConstants.KEYBOARD_TAP;
                break;
            case CONFIRM:
                c = Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY;
                break;
            case REJECT:
                c = Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS;
                break;
            case CLICK:
                c = HapticFeedbackConstants.VIRTUAL_KEY;
                break;
            default:
                c = HapticFeedbackConstants.KEYBOARD_TAP;
        }
        v.performHapticFeedback(c);
    }
}
