package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Bundle;

/** Info: Name, Version und Datum der letzten Aktualisierung. */
public class InfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bubble b = new Bubble(this, 300, 12);
        b.header("Info", b::close);
        String ver = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
        } catch (Exception ignored) {
        }
        b.text("BGL Wetter & News", 18, b.title, true).setPadding(b.px(4), b.px(2), 0, b.px(10));
        b.text("Entwickler", 12, b.soft, false).setPadding(b.px(4), 0, 0, 0);
        b.text("bergf3x", 15, b.tx, false).setPadding(b.px(4), 0, 0, b.px(8));
        b.text("Version", 12, b.soft, false).setPadding(b.px(4), 0, 0, 0);
        b.text(ver, 15, b.tx, false).setPadding(b.px(4), 0, 0, b.px(8));
        b.text("Letzte Aktualisierung", 12, b.soft, false).setPadding(b.px(4), 0, 0, 0);
        b.text(getString(R.string.build_date), 15, b.tx, false).setPadding(b.px(4), 0, 0, b.px(8));
        if (Direct.enabled(this)) {
            b.text("TESTVERSION: direkter Abruf", 13, b.title, true).setPadding(b.px(4), b.px(4), 0, b.px(2));
            b.text(Direct.logText(this), 11, b.soft, false).setPadding(b.px(4), 0, 0, b.px(6));
        }
        b.divider();
        final android.widget.TextView head = b.text("Änderungen  ▾", 15, b.title, true);
        head.setPadding(b.px(4), b.px(8), b.px(4), b.px(8));
        final android.widget.LinearLayout body = b.scrollBox(0.4f);
        final android.view.View sv = (android.view.View) body.getParent();
        sv.setVisibility(android.view.View.GONE);
        for (String[] v : Changelog.LOG) {
            body.addView(line(b, "Version " + v[0], 14, b.title, true, 8));
            for (int i = 1; i < v.length; i++) body.addView(line(b, v[i], 13, b.tx, false, 1));
        }
        head.setOnClickListener(x -> {
            boolean open = sv.getVisibility() != android.view.View.VISIBLE;
            sv.setVisibility(open ? android.view.View.VISIBLE : android.view.View.GONE);
            head.setText(open ? "Änderungen  ▴" : "Änderungen  ▾");
        });
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
    }

    private android.widget.TextView line(Bubble b, String s, float sp, int color, boolean bold, int topDp) {
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(s);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(b.px(4), b.px(topDp), b.px(4), b.px(1));
        return t;
    }
}
