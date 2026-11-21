package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

/** Auswahl-Blase fuer den Wetter-Ort (alphabetisch, scrollbar). */
public class PlaceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id == 0) {
            finish();
            return;
        }
        final Bubble b = new Bubble(this, 300, 12);
        b.header("Wetter-Ort", b::close);
        int cur = Prefs.place(this, id);
        Integer[] order = new Integer[Weather.PLACES.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final Collator col = Collator.getInstance(Locale.GERMANY);
        Arrays.sort(order, (x, y) -> col.compare(Weather.PLACES[x], Weather.PLACES[y]));
        LinearLayout body = b.scrollBox(0.6f);
        for (int idx : order) {
            final int place = idx;
            b.row(body, Weather.PLACES[idx], idx == cur, () -> {
                Prefs.setPlace(this, id, place);
                WidgetProvider.refreshHeader(this, id);
                // nur das Wetter des neuen Ortes holen (ohne die Nachrichten) und gleich danach neu zeichnen
                final android.content.Context app = getApplicationContext();
                new Thread(() -> {
                    if (Weather.refreshIfStale(app, place)) WidgetProvider.refreshHeader(app, id);
                }).start();
            }, true);
        }
        b.space(6);
        android.widget.TextView foot = b.text("", 12, b.soft, false);
        foot.setPadding(b.px(8), b.px(4), b.px(8), b.px(4));
        String t1 = "Open-Meteo", t2 = "Deutscher Wetterdienst";
        String full = "Wetterdaten: " + t1 + " · Warnungen: " + t2;
        android.text.SpannableString sp = new android.text.SpannableString(full);
        link(sp, full, t1, "https://open-meteo.com");
        link(sp, full, t2, "https://www.dwd.de");
        foot.setText(sp);
        foot.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        foot.setLinkTextColor(b.tx);
    }

    private void link(android.text.SpannableString sp, String full, String word, String url) {
        int i = full.indexOf(word);
        sp.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(android.view.View v) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                } catch (Exception ignored) {
                }
            }
        }, i, i + word.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
