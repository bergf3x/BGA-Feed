/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Liste der gemerkten Artikel als getrennte Karten (Titel fett, Zeile mit Ressort/Zeit, Quelle in Klammern); ein Tipp oeffnet die Vorschau. */
public class SavedActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bubble b = new Bubble(this, 340, 8);
        b.header("Gemerkte Artikel", b::close);
        Prefs.pruneSaved(this);
        JSONArray a = Prefs.saved(this);
        final int[] keep = {Prefs.savedKeepDays(this) == 0 ? 0 : 1};
        final int keep0 = keep[0];
        if (a.length() == 0) {
            b.text("Noch nichts gemerkt. In der Artikel-Vorschau gibt es den Knopf „Merken“.", 14, b.tx, false)
                    .setPadding(b.px(10), b.px(4), b.px(10), b.px(10));
        } else {
            LinearLayout body = b.scrollBox(0.6f);
            b.label(body, "Aufbewahrung");
            b.segment(body, new String[]{"Dauerhaft", "7 Tage"}, keep[0], i -> keep[0] = i);
            for (int i = 0; i < a.length(); i++) {
                final JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(b.px(12), b.px(10), b.px(12), b.px(10));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(b.light ? 0x14000000 : 0x1FFFFFFF);
                bg.setCornerRadius(b.px(14));
                card.setBackground(bg);
                card.addView(line(b, o.optString("t"), 16, b.title, true, 0));
                String meta = o.optString("m");
                if (o.optBoolean("g")) meta = "Artikel nicht mehr verfügbar" + (meta.isEmpty() ? "" : " · " + meta);
                if (!meta.isEmpty()) card.addView(line(b, meta, 12, b.soft, false, 4));
                String src = FeedItem.source(o.optString("l"));
                if (!src.isEmpty()) card.addView(line(b, src, 11, b.soft, false, 1));
                card.setOnClickListener(v -> {
                    Intent in = new Intent(this, PreviewActivity.class);
                    in.putExtra(PreviewActivity.EXTRA_TITLE, o.optString("t"));
                    in.putExtra(PreviewActivity.EXTRA_META, o.optString("m"));
                    in.putExtra(PreviewActivity.EXTRA_TEXT, o.optString("x"));
                    in.putExtra(PreviewActivity.EXTRA_LINK, o.optString("l"));
                    in.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, Bubble.widgetId(this));
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(in);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = b.px(10);
                body.addView(card, lp);
            }
        }
        if (a.length() == 0) {
            b.pill("Schließen", false, true, () -> { });
        } else {
            // Aufbewahrung gilt erst mit "Uebernehmen"; zu alte Artikel werden erst dann entfernt
            TextView ok = b.footer(true, () -> { }, () -> {
                Prefs.setSavedKeepDays(this, keep[0] == 0 ? 0 : 7);
                Prefs.pruneSaved(this);
            });
            b.watch(ok, () -> keep[0] != keep0);
        }
    }

    private TextView line(Bubble b, String s, float sp, int color, boolean bold, int topDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, b.px(topDp), 0, 0);
        return t;
    }
}
