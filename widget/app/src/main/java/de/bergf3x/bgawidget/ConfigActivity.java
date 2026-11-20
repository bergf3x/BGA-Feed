package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class ConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private SeekBar sbBg;
    private SeekBar sbTx;
    private TextView lblBg;
    private TextView lblTx;
    private ImageView prevFill;
    private ImageView prevBorder;
    private TextView prevText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.config);
        sbBg = findViewById(R.id.sb_bg);
        sbTx = findViewById(R.id.sb_tx);
        lblBg = findViewById(R.id.lbl_bg);
        lblTx = findViewById(R.id.lbl_tx);
        prevFill = findViewById(R.id.prev_fill);
        prevBorder = findViewById(R.id.prev_border);
        prevText = findViewById(R.id.prev_text);
        Button save = findViewById(R.id.btn_save);

        sbBg.setProgress(Prefs.bg(this, widgetId) - 1);
        sbTx.setProgress(Prefs.text(this, widgetId) - 1);
        refreshPreview();

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                refreshPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        };
        sbBg.setOnSeekBarChangeListener(l);
        sbTx.setOnSeekBarChangeListener(l);

        save.setOnClickListener(v -> {
            Prefs.set(this, widgetId, sbBg.getProgress() + 1, sbTx.getProgress() + 1);
            WidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void refreshPreview() {
        int bg = sbBg.getProgress() + 1;
        int tx = sbTx.getProgress() + 1;
        lblBg.setText("Hintergrund-Deckkraft: " + bg + " %");
        lblTx.setText("Schrift-Deckkraft: " + tx + " %");
        prevFill.setImageAlpha(Prefs.alpha(bg));
        prevBorder.setImageAlpha(Math.round(Prefs.alpha(Math.max(bg, 20)) * 0.45f));
        prevText.setTextColor((Prefs.alpha(tx) << 24) | 0x00FFFFFF);
    }
}
