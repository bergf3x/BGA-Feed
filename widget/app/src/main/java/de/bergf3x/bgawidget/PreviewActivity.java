package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

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

        // als gelesen markieren; Liste nur aus dem Cache neu zeichnen (kein Netz, kein Flackern)
        if (!link.isEmpty()) {
            Prefs.markRead(this, link);
            Prefs.setLocalOnly(this);
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(this);
            int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(this, WidgetProvider.class));
            for (int id : ids) mgr.notifyAppWidgetViewDataChanged(id, R.id.list);
        }

        final Bubble b = new Bubble(this, 330, 16);
        b.text(meta, 12, 0xB3B5D4F4, false);
        b.text(title, 18, 0xFFFFFFFF, true).setPadding(0, b.px(6), 0, b.px(10));
        b.scrollText(text.isEmpty() ? "Für diesen Artikel gibt es keinen Anrisstext." : text, 14, Bubble.TEXT);
        b.text("Nur der Anrisstext – der ganze Artikel steht auf der Webseite.", 11, 0x80FFFFFF, false)
                .setPadding(0, b.px(10), 0, b.px(4));
        b.pill("Artikel öffnen", true, isWeb(link), () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
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
