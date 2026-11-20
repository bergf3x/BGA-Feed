package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigActivity extends Activity {
    private static final int[] COLORS = {
            0xFFFFFF, 0xD0D0D0, 0xFFE066, 0xFFA94D, 0xFF8FB1, 0x8CCBFF, 0x7DF0C0, 0x000000
    };

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private SeekBar sbBg;
    private SeekBar sbTx;
    private TextView lblBg;
    private TextView lblTx;
    private ImageView prevFill;
    private ImageView prevBorder;
    private TextView prevText;
    private EditText etColor;
    private LinearLayout feedList;
    private int color;
    private boolean settingHex = false;
    private final Set<String> selected = new HashSet<>();

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
        etColor = findViewById(R.id.et_color);
        feedList = findViewById(R.id.feed_list);
        Button save = findViewById(R.id.btn_save);

        color = Prefs.color(this, widgetId);
        selected.addAll(Prefs.feeds(this, widgetId));
        sbBg.setProgress(Prefs.bg(this, widgetId) - 1);
        sbTx.setProgress(Prefs.text(this, widgetId) - 1);
        buildSwatches();
        setHex(color);
        buildFeedList();
        refreshPreview();

        // Katalog im Hintergrund aktualisieren (neue Feeds erscheinen ohne App-Update)
        new Thread(() -> {
            Catalog.refresh(getApplicationContext());
            runOnUiThread(this::buildFeedList);
        }).start();

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

        etColor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                if (settingHex) return;
                String t = e.toString().trim();
                if (t.startsWith("#")) t = t.substring(1);
                if (t.length() == 6) {
                    try {
                        color = Integer.parseInt(t, 16) & 0xFFFFFF;
                        refreshPreview();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });

        save.setOnClickListener(v -> {
            if (selected.isEmpty()) selected.add("bga");
            Prefs.set(this, widgetId, sbBg.getProgress() + 1, sbTx.getProgress() + 1, color, selected);
            WidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
            WidgetProvider.refresh(this, widgetId);
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void buildSwatches() {
        LinearLayout box = findViewById(R.id.swatches);
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(40 * d);
        for (int c : COLORS) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | c);
            g.setStroke(Math.round(2 * d), 0x99888888);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, Math.round(10 * d), 0);
            v.setLayoutParams(lp);
            v.setOnClickListener(x -> {
                color = c;
                setHex(c);
                refreshPreview();
            });
            box.addView(v);
        }
    }

    private void setHex(int rgb) {
        settingHex = true;
        etColor.setText(String.format("%06X", rgb & 0xFFFFFF));
        settingHex = false;
    }

    private void buildFeedList() {
        feedList.removeAllViews();
        List<Catalog.Feed> all = Catalog.load(this);
        for (Catalog.Feed f : all) {
            CheckBox cb = new CheckBox(this);
            cb.setText(f.name);
            cb.setTextSize(16);
            cb.setChecked(selected.contains(f.id));
            cb.setOnCheckedChangeListener((b, on) -> {
                if (on) selected.add(f.id);
                else selected.remove(f.id);
            });
            feedList.addView(cb);
        }
    }

    private void refreshPreview() {
        int bg = sbBg.getProgress() + 1;
        int tx = sbTx.getProgress() + 1;
        lblBg.setText("Hintergrund-Deckkraft: " + bg + " %");
        lblTx.setText("Schrift-Deckkraft: " + tx + " %");
        prevFill.setImageAlpha(Prefs.alpha(bg));
        prevBorder.setImageAlpha(Math.round(Prefs.alpha(Math.max(bg, 20)) * 0.45f));
        prevText.setTextColor(Prefs.argb(color, tx, 1f));
    }
}
