package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Auswahl von Webcams der Region; ein Tipp oeffnet die Seite im Browser. */
public class WebcamActivity extends Activity {
    private static final String[][] CAMS = {
            {"Obersalzberg – Watzmann im Blick", "https://www.blickfix.com/webcams/webcam-obersalzberg-watzmann-im-blick?type=17023"},
            {"Hochlenzer – Schönau und Watzmann", "https://www.bergfex.com/berchtesgaden-gutshof-obersalzberg/webcams/c2341/"},
            {"Jenner Bergstation", "https://www.bergfex.com/jenner-schoenau-am-koenigssee/webcams/c23475/"},
            {"Kehlstein", "https://www.bergfex.com/jenner-schoenau-am-koenigssee/webcams/c18989/"},
            {"Alle Webcams Schönau (bergfex)", "https://www.bergfex.com/bayern/langlaufen/schoenau-am-koenigssee/webcams/"},
            {"Königssee", "https://www.berchtesgaden.de/webcams/webcam-koenigssee"},
            {"Alle Webcams Berchtesgaden", "https://www.berchtesgaden.de/webcams"},
            {"Nationalpark: Jenner, Funtensee, Ramsau", "https://nationalpark-berchtesgaden.bayern.de/english/service/media/webcam/index.htm"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bubble b = new Bubble(this, 330, 8);
        b.text("Webcams", 11, 0x99FFFFFF, false).setPadding(b.px(10), b.px(4), b.px(10), b.px(6));
        for (final String[] c : CAMS) {
            b.row(c[0], false, () -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(c[1])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            });
        }
    }
}
