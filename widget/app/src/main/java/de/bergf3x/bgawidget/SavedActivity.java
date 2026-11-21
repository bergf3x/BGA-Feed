package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

/** Liste der gemerkten Artikel; ein Tipp oeffnet die Vorschau. */
public class SavedActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bubble b = new Bubble(this, 340, 8);
        b.text("Gemerkte Artikel", 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(6));
        JSONArray a = Prefs.saved(this);
        if (a.length() == 0) {
            b.text("Noch nichts gemerkt. In der Artikel-Vorschau gibt es den Knopf „Merken“.", 14, b.tx, false)
                    .setPadding(b.px(10), b.px(4), b.px(10), b.px(10));
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            final JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            b.row(o.optString("t"), false, () -> {
                Intent in = new Intent(this, PreviewActivity.class);
                in.putExtra(PreviewActivity.EXTRA_TITLE, o.optString("t"));
                in.putExtra(PreviewActivity.EXTRA_META, o.optString("m"));
                in.putExtra(PreviewActivity.EXTRA_TEXT, o.optString("x"));
                in.putExtra(PreviewActivity.EXTRA_LINK, o.optString("l"));
                in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(in);
            });
        }
    }
}
