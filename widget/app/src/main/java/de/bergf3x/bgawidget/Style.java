package de.bergf3x.bgawidget;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;

/** Schriftart und -groesse aus den Einstellungen (nur System-Schriften, Widgets koennen keine eigenen laden). */
final class Style {
    static final String[] FAMILIES = {null, "serif", "sans-serif-condensed", "monospace", "casual"};
    static final String[] FONT_NAMES = {"Standard", "Serif", "Schmal", "Mono", "Hand"};

    private Style() {}

    /** Text mit der gewaehlten Schriftart; bei "Standard" der Text unveraendert. */
    static CharSequence font(Context c, int id, String text) {
        int f = Prefs.font(c, id);
        if (f == 0 || FAMILIES[f] == null || text == null || text.isEmpty()) return text;
        SpannableString s = new SpannableString(text);
        s.setSpan(new TypefaceSpan(FAMILIES[f]), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    static Typeface typeface(int font) {
        if (font <= 0 || font >= FAMILIES.length) return Typeface.DEFAULT;
        return Typeface.create(FAMILIES[font], Typeface.NORMAL);
    }

    static float titleSp(int size) {
        return size == 0 ? 13f : (size == 2 ? 16f : 14f);
    }

    static float metaSp(int size) {
        return size == 0 ? 10f : (size == 2 ? 12.5f : 11f);
    }
}
