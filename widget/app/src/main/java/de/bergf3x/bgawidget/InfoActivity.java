package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Bundle;

/** Info: Name, Version und Datum der letzten Aktualisierung. */
public class InfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bubble b = new Bubble(this, 320, 12);
        b.high();
        b.header("Über die App", b::close);
        String ver = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
        } catch (Exception ignored) {
        }
        b.text("BGL Wetter & News", 18, b.title, true).setPadding(b.px(4), b.px(2), 0, b.px(2));
        b.text("Version " + ver + " · " + getString(R.string.build_date), 13, b.soft, false).setPadding(b.px(4), 0, 0, b.px(2));
        b.text("Entwickelt von bergf3x", 13, b.soft, false).setPadding(b.px(4), 0, 0, b.px(8));

        b.divider();
        android.widget.LinearLayout log = section(b, "Neuigkeiten", 0.45f);
        for (String[] v : Changelog.LOG) {
            log.addView(line(b, "Version " + v[0], 14, b.title, true, 8));
            for (int i = 1; i < v.length; i++) log.addView(line(b, v[i], 13, b.tx, false, 1));
        }

        b.divider();
        android.widget.LinearLayout src = section(b, "Quellen", 0.35f);
        src.addView(line(b, "Die App zeigt Überschriften und kurze Anrisse und verlinkt immer auf den Originalartikel. "
                + "Alle Rechte an den Inhalten liegen bei den jeweiligen Verlagen und Stellen.", 12, b.soft, false, 2));
        for (Catalog.Feed f : Catalog.load(this)) src.addView(line(b, "• " + f.name, 13, b.tx, false, 3));
        src.addView(line(b, "Wetter: Open-Meteo · Warnungen: Deutscher Wetterdienst · Webcams: jeweilige Betreiber", 12, b.soft, false, 8));

        b.divider();
        android.widget.LinearLayout cb = section(b, "Bildnachweise", 0.35f);
        cb.addView(line(b, "Wetter-Hintergründe: Fotos von Wikimedia Commons, bearbeitet (zugeschnitten, schwarzweiß, abgedunkelt).", 12, b.soft, false, 2));
        for (final String[] c : Credits.PHOTOS) {
            cb.addView(line(b, c[0], 13, b.tx, true, 8));
            android.widget.TextView t = line(b, "Foto: " + c[1] + " · " + c[2] + " · Quelle öffnen", 12, b.soft, false, 1);
            t.setOnClickListener(x -> {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(c[3])));
                } catch (Exception ignored) {
                }
            });
            cb.addView(t);
        }

        if (Direct.enabled(this)) {
            b.divider();
            android.widget.LinearLayout tech = section(b, "Technisches Protokoll", 0.35f);
            tech.addView(line(b, Direct.logText(this), 11, b.soft, false, 2));
        }
        b.space(4);
        b.pill("Schließen", false, true, () -> { });
    }

    private final java.util.List<Runnable> sections = new java.util.ArrayList<>();

    /** Aufklappbarer Abschnitt; liefert den Inhaltsbereich. */
    private android.widget.LinearLayout section(Bubble b, final String title, float frac) {
        final android.widget.TextView head = b.text(title + "  ▾", 15, b.title, true);
        head.setPadding(b.px(4), b.px(8), b.px(4), b.px(8));
        final android.widget.LinearLayout body = b.scrollBox(frac);
        final android.view.View sv = (android.view.View) body.getParent();
        sv.setVisibility(android.view.View.GONE);
        final Runnable close = () -> {
            sv.setVisibility(android.view.View.GONE);
            head.setText(title + "  ▾");
        };
        sections.add(close);
        head.setOnClickListener(x -> {
            boolean open = sv.getVisibility() != android.view.View.VISIBLE;
            // immer nur ein Abschnitt offen, damit "Schließen" sichtbar bleibt
            for (Runnable r : sections) r.run();
            sv.setVisibility(open ? android.view.View.VISIBLE : android.view.View.GONE);
            head.setText(title + (open ? "  ▴" : "  ▾"));
            Haptic.play(x, Haptic.TICK);
        });
        return body;
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
