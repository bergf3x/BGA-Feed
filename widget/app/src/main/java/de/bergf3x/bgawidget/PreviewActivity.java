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
    static final String EXTRA_IMAGE = "image";

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
        final boolean wx = WeatherCards.isCardLink(this, link);
        if (!link.isEmpty() && !wx) {
            Prefs.markRead(this, link);
            Prefs.setLocalOnly(this);
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(this);
            int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(this, WidgetProvider.class));
            for (int id : ids) mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
        }

        final Bubble b = new Bubble(this, 330, 16);
        final String image = str(in, EXTRA_IMAGE);
        // Bild nur, wenn es wirklich da ist (gemerkt oder jetzt ladbar); sonst kein leerer Rahmen
        if (!image.isEmpty() && ImageStore.mode(this) != ImageStore.OFF) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            ImageStore.round(iv, b.px(12));
            android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, b.px(170));
            ilp.bottomMargin = b.px(10);
            iv.setVisibility(android.view.View.GONE);
            b.flex(iv, 70);
            b.box.addView(iv, ilp);
            ImageStore.into(iv, image, 480, false);
        }
        b.text(meta, 12, b.soft, false);
        b.text(title, 18, b.title, true).setPadding(0, b.px(6), 0, b.px(10));
        b.scrollText(text.isEmpty() ? "Für diesen Artikel gibt es keinen Anrisstext." : text, 14, b.tx);
        String src = FeedItem.source(link);
        b.text(wx ? "Wird dort laufend aktualisiert." + (src.isEmpty() ? "" : "\nQuelle: " + src)
                        : "Nur der Anrisstext – der ganze Artikel steht auf der Webseite." + (src.isEmpty() ? "" : "\nQuelle: " + src), 11, b.soft, false)
                .setPadding(0, b.px(10), 0, b.px(4));
        b.pill(wx ? "Seite öffnen" : "Artikel öffnen", true, isWeb(link), () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            } catch (Exception ignored) {
            }
        });
        final Runnable share = () -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.putExtra(Intent.EXTRA_TEXT, title + "\n" + link);
                startActivity(Intent.createChooser(send, "Teilen"));
            } catch (Exception ignored) {
            }
        };
        if (!wx) {
            // Merken und Teilen nebeneinander: spart eine Zeile, damit alle Knoepfe auf den Bildschirm passen
            b.pillRow(Prefs.isSaved(this, link) ? "Gemerkt ✓" : "Merken", () -> { }, "Teilen", share);
            final TextView save = (TextView) ((android.view.ViewGroup) b.box.getChildAt(b.box.getChildCount() - 1)).getChildAt(0);
            save.setOnClickListener(v -> {
                boolean now = Prefs.toggleSaved(this, title, meta, text, link, image);
                save.setText(now ? "Gemerkt ✓" : "Merken");
                Haptic.play(save, now ? Haptic.CONFIRM : Haptic.TICK);
            });
        } else {
            b.pillKeep("Teilen", false, share);
        }
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
