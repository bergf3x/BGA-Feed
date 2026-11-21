package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Volltextsuche (Titel und Anrisstext) in den zuletzt geladenen Artikeln und in den gemerkten. */
public class SearchActivity extends Activity {
    private Bubble b;
    private int fixed;
    private final List<FeedItem> pool = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        b = new Bubble(this, 340, 8);
        b.text("Suche", 11, b.soft, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(2));
        for (Catalog.Feed f : Catalog.load(this)) {
            pool.addAll(FeedCache.items(this, f.id, f.name));
        }
        Collections.sort(pool, (x, y) -> Long.compare(y.time, x.time));

        EditText et = new EditText(this);
        et.setHint("Stichwort eingeben");
        et.setSingleLine(true);
        et.setTextColor(b.title);
        et.setHintTextColor(b.soft);
        et.setPadding(b.px(12), b.px(8), b.px(12), b.px(8));
        b.box.addView(et);
        fixed = b.box.getChildCount();
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) { show(s.toString().trim()); }
        });
        show("");
    }

    private void show(String q) {
        b.box.removeViews(fixed, b.box.getChildCount() - fixed);
        if (q.length() < 2) {
            b.text("Mindestens 2 Buchstaben. Gesucht wird in den zuletzt geladenen und in den gemerkten Artikeln.",
                    13, b.soft, false).setPadding(b.px(10), b.px(6), b.px(10), b.px(8));
            return;
        }
        String[] words = q.toLowerCase(Locale.GERMANY).split("\\s+");
        int n = 0;
        for (FeedItem it : pool) {
            if (n >= 25) break;
            if (!matches(words, it.title + " " + it.description)) continue;
            n++;
            final FeedItem x = it;
            b.row(it.title, false, () -> open(x.title, x.meta(), x.description, x.link));
        }
        JSONArray a = Prefs.saved(this);
        for (int i = 0; i < a.length() && n < 30; i++) {
            final JSONObject o = a.optJSONObject(i);
            if (o == null || !matches(words, o.optString("t") + " " + o.optString("x"))) continue;
            n++;
            b.row("★ " + o.optString("t"), false, () -> open(o.optString("t"), o.optString("m"), o.optString("x"), o.optString("l")));
        }
        if (n == 0) b.text("Nichts gefunden.", 14, b.tx, false).setPadding(b.px(10), b.px(6), b.px(10), b.px(8));
    }

    private static boolean matches(String[] words, String hay) {
        String h = hay.toLowerCase(Locale.GERMANY);
        for (String w : words) if (!h.contains(w)) return false;
        return true;
    }

    private void open(String title, String meta, String text, String link) {
        Intent in = new Intent(this, PreviewActivity.class);
        in.putExtra(PreviewActivity.EXTRA_TITLE, title);
        in.putExtra(PreviewActivity.EXTRA_META, meta);
        in.putExtra(PreviewActivity.EXTRA_TEXT, text);
        in.putExtra(PreviewActivity.EXTRA_LINK, link);
        in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(in);
    }
}
