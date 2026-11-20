package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Vorschau eines Artikels (Titel, Rubrik, Zeit, Anrisstext) mit Button zur Webseite. */
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
        String link = str(in, EXTRA_LINK);

        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(meta);
        tvMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvMeta.setAlpha(0.7f);
        root.addView(tvMeta);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, Math.round(6 * d), 0, Math.round(12 * d));
        root.addView(tvTitle);

        TextView tvText = new TextView(this);
        tvText.setText(text.isEmpty() ? "Für diesen Artikel gibt es keinen Anrisstext." : text);
        tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvText.setLineSpacing(0, 1.15f);
        ScrollView sv = new ScrollView(this);
        sv.addView(tvText);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Nur der Anrisstext – der ganze Artikel steht auf der Webseite.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setAlpha(0.55f);
        hint.setPadding(0, Math.round(10 * d), 0, Math.round(6 * d));
        root.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        Button close = new Button(this);
        close.setText("Schließen");
        close.setOnClickListener(v -> finish());
        Button open = new Button(this);
        open.setText("Artikel öffnen");
        open.setEnabled(isWeb(link));
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            } catch (Exception ignored) {
            }
            finish();
        });
        row.addView(close);
        row.addView(open);
        root.addView(row);

        setContentView(root);
    }

    private static boolean isWeb(String link) {
        return link.startsWith("https://") || link.startsWith("http://");
    }

    private static String str(Intent i, String k) {
        String s = i.getStringExtra(k);
        return s == null ? "" : s;
    }
}
