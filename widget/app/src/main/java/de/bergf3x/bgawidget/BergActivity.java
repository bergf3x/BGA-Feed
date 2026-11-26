/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;

/**
 * Bergwetter-Region als Fenster: Reiter "Orte" und "Gipfel" mit Direktlinks zu den Seiten des Alpenvereins.
 * Titel, Reiter und die unteren Knoepfe stehen fest, nur die Liste scrollt.
 */
public class BergActivity extends Activity {
    static final String EXTRA_REGION = "region";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!show(this, getIntent() == null ? null : getIntent().getStringExtra(EXTRA_REGION))) finish();
    }

    /**
     * Baut das Regionsfenster in der gegebenen Activity auf. Wird auch direkt aus PreviewActivity genutzt:
     * Vom Widget aus darf keine zweite Activity nachgestartet werden (Android 14+ und manche Hersteller
     * blockieren solche Starts aus dem Hintergrund), daher zeigt die erste Activity das Fenster selbst.
     */
    static boolean show(final Activity a, final String id) {
        String[] reg = WeatherCards.region(id);
        final String[][] st = id == null ? null : WeatherCards.BERG_STATIONS.get(id);
        if (reg == null || st == null) return false;
        final String regionUrl = reg[4];
        int orte = 0, gipfel = 0;
        for (String[] s : st) {
            if (s[2].equals("O")) orte++;
            else gipfel++;
        }
        final Bubble b = new Bubble(a, 330, 12);
        b.header(reg[1], b::close);
        b.text(reg[2], 12, b.soft, false).setPadding(b.px(4), 0, b.px(4), b.px(8));
        final LinearLayout[] body = new LinearLayout[1];
        b.segment(b.box, new String[]{"Orte · " + orte, "Gipfel · " + gipfel}, 0, tab -> fill(a, b, body[0], st, id, tab));
        b.space(6);
        body[0] = b.scrollBox(0.5f);
        fill(a, b, body[0], st, id, 0);
        b.pillKeep("Ganze Region auf alpenverein.de  ↗", false, () -> open(a, regionUrl));
        b.pill("Schließen", false, true, () -> { });
        return true;
    }

    private static void fill(Activity a, Bubble b, LinearLayout body, String[][] stations, String id, int tab) {
        if (body == null) return;
        Bubble.soft(body);
        body.removeAllViews();
        String kind = tab == 0 ? "O" : "G";
        for (final String[] s : stations) {
            if (!s[2].equals(kind)) continue;
            final String url = WeatherCards.stationUrl(id, s[1]);
            b.row(body, s[0] + "  ↗", false, () -> open(a, url), false);
        }
    }

    private static void open(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }
}
