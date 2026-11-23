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

    private Bubble b;
    private LinearLayout body;
    private String[][] stations = new String[0][];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_REGION);
        String[] reg = WeatherCards.region(id);
        String[][] st = id == null ? null : WeatherCards.BERG_STATIONS.get(id);
        if (reg == null || st == null) {
            finish();
            return;
        }
        stations = st;
        final String regionUrl = reg[4];
        int orte = 0, gipfel = 0;
        for (String[] s : st) {
            if (s[2].equals("O")) orte++;
            else gipfel++;
        }
        b = new Bubble(this, 330, 12);
        b.header(reg[1], b::close);
        b.text(reg[2], 12, b.soft, false).setPadding(b.px(4), 0, b.px(4), b.px(8));
        b.segment(b.box, new String[]{"Orte · " + orte, "Gipfel · " + gipfel}, 0, this::fill);
        b.space(6);
        body = b.scrollBox(0.5f);
        fill(0);
        b.pillKeep("Ganze Region auf alpenverein.de  ↗", false, () -> open(regionUrl));
        b.pill("Schließen", false, true, () -> { });
    }

    private void fill(int tab) {
        Bubble.soft(body);
        body.removeAllViews();
        String kind = tab == 0 ? "O" : "G";
        for (final String[] s : stations) {
            if (!s[2].equals(kind)) continue;
            final String url = WeatherCards.stationUrl(getIntent().getStringExtra(EXTRA_REGION), s[1]);
            b.row(body, s[0] + "  ↗", false, () -> open(url), false);
        }
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }
}
