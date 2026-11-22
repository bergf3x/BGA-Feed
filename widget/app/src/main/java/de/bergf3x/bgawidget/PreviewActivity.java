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
import android.widget.TextView;

/** Vorschau eines Artikels (Titel, Rubrik, Zeit, Anrisstext) als Glas-Blase mit Knopf zur Webseite. */
public class PreviewActivity extends Activity {
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_META = "meta";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_LINK = "link";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        String title = str(in, EXTRA_TITLE);
        String meta = str(in, EXTRA_META);
        String text = str(in, EXTRA_TEXT);
        final String link = str(in, EXTRA_LINK);
        if (title.isEmpty() && link.isEmpty()) {
            // Tipp auf eine Medien-Ueberschrift im Widget: nichts anzuzeigen
            finish();
            return;
        }

        // als gelesen markieren; Liste nur aus dem Cache neu zeichnen (kein Netz, kein Flackern)
        if (!link.isEmpty()) {
            Prefs.markRead(this, link);
            Prefs.setLocalOnly(this);
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(this);
            int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(this, WidgetProvider.class));
            for (int id : ids) mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
        }

        final Bubble b = new Bubble(this, 330, 16);
        b.text(meta, 12, b.soft, false);
        b.text(title, 18, b.title, true).setPadding(0, b.px(6), 0, b.px(10));
        b.scrollText(text.isEmpty() ? "Für diesen Artikel gibt es keinen Anrisstext." : text, 14, b.tx);
        String src = FeedItem.source(link);
        b.text("Nur der Anrisstext – der ganze Artikel steht auf der Webseite." + (src.isEmpty() ? "" : "\nQuelle: " + src), 11, b.soft, false)
                .setPadding(0, b.px(10), 0, b.px(4));
        b.pill("Artikel öffnen", true, isWeb(link), () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            } catch (Exception ignored) {
            }
        });
        final TextView save = b.pillKeep(Prefs.isSaved(this, link) ? "Gemerkt ✓ (tippen zum Entfernen)" : "Merken", false, () -> { });
        save.setOnClickListener(v -> {
            boolean now = Prefs.toggleSaved(this, title, meta, text, link);
            save.setText(now ? "Gemerkt ✓ (tippen zum Entfernen)" : "Merken");
            Haptic.play(save, now ? Haptic.CONFIRM : Haptic.TICK);
        });
        b.pillKeep("Teilen", false, () -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.putExtra(Intent.EXTRA_TEXT, title + "\n" + link);
                startActivity(Intent.createChooser(send, "Teilen"));
            } catch (Exception ignored) {
            }
        });
        b.pill("Schließen", false, true, () -> { });
    }

    private static boolean isWeb(String link) {
        return link.startsWith("https://") || link.startsWith("http://");
    }

    private static String str(Intent i, String k) {
        String s = i.getStringExtra(k);
        return s == null ? "" : s;
    }
}
