package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Oeffnet einen Artikel-Link im Browser (nur http und https). */
public class OpenActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri u = getIntent().getData();
        if (u != null && ("https".equals(u.getScheme()) || "http".equals(u.getScheme()))) {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
            } catch (Exception ignored) {
            }
        }
        finish();
    }
}
