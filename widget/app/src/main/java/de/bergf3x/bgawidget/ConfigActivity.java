package de.bergf3x.bgawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Einstellungen im Stil der Glas-Blase, mit Live-Vorschau. */
public class ConfigActivity extends Activity {
    private static final int[] COLORS = {
            0xFFFFFF, 0xD0D0D0, 0xFFE066, 0xFFA94D, 0xFF8FB1, 0x8CCBFF, 0x7DF0C0, 0x000000
    };

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Bubble b;
    private int bgPct;
    private int txPct;
    private int color;
    private boolean plus;
    private int density;
    private int font;
    private int size;
    private int theme;
    private boolean wxOn;
    private boolean warnOn;
    private boolean settingHex = false;
    private final Set<String> selected = new HashSet<>();
    private final Set<String> muted = new HashSet<>();

    private GradientDrawable prevFill;
    private TextView prevTitle;
    private TextView prevMeta;
    private TextView lblBg;
    private TextView lblTx;
    private EditText etColor;
    private LinearLayout feedBox;
    private TextView catStatus;
    private int colSub = -1;
    private int colWx = -1;
    private boolean compact;
    private boolean notify;
    private static final int[] TONES = {-1, 0xFFFFFF, 0x9CC9FF, 0x7CE3BE, 0xFFE08A};
    private static final String[] TONE_NAMES = {"Wie Titel", "Weiß", "Hellblau", "Mint", "Gelb"};
    private LinearLayout muteBox;
    private LinearLayout swatchBox;

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
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        bgPct = Prefs.bg(this, widgetId);
        txPct = Prefs.text(this, widgetId);
        color = Prefs.color(this, widgetId);
        plus = Prefs.showPlus(this, widgetId);
        density = Prefs.density(this, widgetId);
        font = Prefs.font(this, widgetId);
        size = Prefs.size(this, widgetId);
        theme = Prefs.theme(this, widgetId);
        wxOn = Prefs.showWeather(this, widgetId);
        warnOn = Prefs.showWarn(this, widgetId);
        colSub = Prefs.colorSub(this, widgetId);
        colWx = Prefs.colorWx(this, widgetId);
        compact = Prefs.compact(this, widgetId);
        notify = Prefs.warnNotify(this);
        selected.addAll(Prefs.feeds(this, widgetId));
        muted.addAll(Prefs.muted(this, widgetId));

        b = new Bubble(this, 360, 16);
        b.text("Einstellungen", 18, 0xFFFFFFFF, true).setPadding(0, 0, 0, b.px(10));

        // Live-Vorschau auf einem bunten Hintergrund, damit man die Transparenz sieht
        FrameLayout backdrop = new FrameLayout(this);
        GradientDrawable bd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3B6EA5, 0xFF8E5BA8});
        bd.setCornerRadius(b.px(16));
        backdrop.setBackground(bd);
        backdrop.setPadding(b.px(10), b.px(10), b.px(10), b.px(10));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(b.px(14), b.px(10), b.px(14), b.px(10));
        prevFill = new GradientDrawable();
        prevFill.setCornerRadius(b.px(16));
        card.setBackground(prevFill);
        prevTitle = new TextView(this);
        prevTitle.setText("Beispiel: Neue Schlagzeile");
        prevTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        prevTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        prevMeta = new TextView(this);
        prevMeta.setText("Region · vor 12 Min.");
        prevMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        card.addView(prevTitle);
        card.addView(prevMeta);
        backdrop.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.box.addView(backdrop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = b.scrollBox(0.40f);

        lblBg = b.label(body, "");
        body.addView(seek(bgPct, p -> {
            bgPct = p;
            refreshPreview();
        }));
        lblTx = b.label(body, "");
        body.addView(seek(txPct, p -> {
            txPct = p;
            refreshPreview();
        }));

        b.label(body, "Schriftfarbe");
        swatchBox = new LinearLayout(this);
        swatchBox.setOrientation(LinearLayout.HORIZONTAL);
        swatchBox.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(swatchBox);
        etColor = new EditText(this);
        etColor.setSingleLine(true);
        etColor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        etColor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        etColor.setTextColor(Bubble.TEXT);
        etColor.setBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        body.addView(etColor, new LinearLayout.LayoutParams(b.px(120), ViewGroup.LayoutParams.WRAP_CONTENT));
        buildSwatches();
        setHex(color);
        etColor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {}

            @Override
            public void afterTextChanged(Editable e) {
                if (settingHex) return;
                String t = e.toString().trim();
                if (t.startsWith("#")) t = t.substring(1);
                if (t.length() == 6) {
                    try {
                        color = Integer.parseInt(t, 16) & 0xFFFFFF;
                        buildSwatches();
                        refreshPreview();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });

        b.label(body, "Artikel");
        b.toggle(body, "PLUS-Artikel anzeigen", plus, on -> plus = on);

        b.label(body, "Dichte der Liste");
        b.segment(body, new String[]{"Kompakt", "Luftig"}, density, i -> density = i);

        b.label(body, "Schriftart");
        b.segment(body, Style.FONT_NAMES, font, i -> {
            font = i;
            refreshPreview();
        });
        b.label(body, "Schriftgröße");
        b.segment(body, new String[]{"Klein", "Normal", "Groß"}, size, i -> {
            size = i;
            refreshPreview();
        });
        b.label(body, "Design");
        b.segment(body, new String[]{"Dunkel", "Hell", "Auto"}, theme, i -> {
            theme = i;
            if (i == 1 && color == 0xFFFFFF) color = 0x1B1F22;
            if (i == 0 && color == 0x1B1F22) color = 0xFFFFFF;
            setHex(color);
            buildSwatches();
            refreshPreview();
        });

        b.label(body, "Auto folgt dem System (Hell/Dunkel); die Textfarben unten gelten dann nicht.");
        b.label(body, "Farbe der Nebenzeilen");
        b.segment(body, TONE_NAMES, toneIndex(colSub), i -> colSub = TONES[i]);
        b.label(body, "Farbe des Wetterbereichs");
        b.segment(body, TONE_NAMES, toneIndex(colWx), i -> colWx = TONES[i]);

        b.label(body, "Anzeige");
        b.toggle(body, "Wetter kompakt (ohne Vorschau und Zusatzzeilen)", compact, on -> compact = on);
        b.toggle(body, "Unwetterwarnung als Benachrichtigung (ab Stufe 3)", notify, on -> {
            notify = on;
            if (on && android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            }
        });
        b.toggle(body, "Wetterbereich anzeigen", wxOn, on -> wxOn = on);
        b.toggle(body, "Unwetterwarnungen anzeigen", warnOn, on -> warnOn = on);
        b.pillKeep("Gemerkte Artikel ansehen", false,
                () -> startActivity(new Intent(this, SavedActivity.class)));

        b.label(body, "Quellen");
        catStatus = b.label(body, "Katalog wird geladen ...");
        feedBox = new LinearLayout(this);
        feedBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(feedBox);

        b.label(body, "Themen ausblenden (markiert = versteckt)");
        muteBox = new LinearLayout(this);
        muteBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(muteBox);

        buildLists();
        refreshPreview();

        // Katalog im Hintergrund aktualisieren (neue Feeds erscheinen ohne App-Update)
        new Thread(() -> {
            String st = Catalog.refreshStatus(getApplicationContext());
            runOnUiThread(() -> {
                catStatus.setText(st);
                buildLists();
            });
        }).start();

        b.space(6);
        b.pill("Speichern", true, true, this::save);
        b.pill("Abbrechen", false, true, () -> { });
    }

    private interface IntCb {
        void on(int v);
    }

    private SeekBar seek(int pct, IntCb cb) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(99);
        sb.setProgress(pct - 1);
        sb.setProgressTintList(ColorStateList.valueOf(Bubble.LIGHT));
        sb.setThumbTintList(ColorStateList.valueOf(Bubble.LIGHT));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                cb.on(p + 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }

    private void save() {
        if (selected.isEmpty()) selected.add("bga");
        Prefs.set(this, widgetId, bgPct, txPct, color, selected);
        Prefs.setExtras(this, widgetId, plus, muted, density);
        Prefs.setLook(this, widgetId, font, size, theme, wxOn, warnOn);
        Prefs.setColors(this, widgetId, colSub, colWx, compact, notify);
        WidgetProvider.updateAny(this, widgetId);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
    }

    private void buildSwatches() {
        swatchBox.removeAllViews();
        int size = b.px(34);
        for (int c : COLORS) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | c);
            boolean on = c == color;
            g.setStroke(b.px(on ? 3 : 1), on ? 0xFFFFFFFF : 0x66FFFFFF);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, b.px(7), 0);
            v.setOnClickListener(x -> {
                color = c;
                setHex(c);
                buildSwatches();
                refreshPreview();
            });
            swatchBox.addView(v, lp);
        }
    }

    private void setHex(int rgb) {
        settingHex = true;
        etColor.setText(String.format("%06X", rgb & 0xFFFFFF));
        settingHex = false;
    }

    private void buildLists() {
        feedBox.removeAllViews();
        muteBox.removeAllViews();
        List<Catalog.Feed> all = Catalog.load(this);
        Set<String> seen = new HashSet<>();
        for (Catalog.Feed f : all) {
            b.toggle(feedBox, f.name, selected.contains(f.id), on -> {
                if (on) selected.add(f.id);
                else selected.remove(f.id);
            });
            for (String t : f.topics) {
                if (!seen.add(t)) continue;
                int slash = t.indexOf('/');
                String label = slash < 0 ? t : "      " + t.substring(t.lastIndexOf('/') + 1);
                b.toggle(muteBox, label, muted.contains(t), on -> {
                    if (on) muted.add(t);
                    else muted.remove(t);
                });
            }
        }
    }

    private static int toneIndex(int rgb) {
        for (int i = 0; i < TONES.length; i++) if (TONES[i] == rgb) return i;
        return 0;
    }

    private boolean lightNow() {
        if (theme == 1) return true;
        if (theme != 2) return false;
        int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode != android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void refreshPreview() {
        lblBg.setText("Hintergrund-Deckkraft: " + bgPct + " %");
        lblTx.setText("Schrift-Deckkraft: " + txPct + " %");
        prevFill.setColor(Prefs.argb(lightNow() ? 0xEEF1F6 : 0x1E1E26, bgPct, 1f));
        int shown = theme == 2 ? (lightNow() ? 0x1B1F22 : 0xFFFFFF) : color;
        prevTitle.setTextColor(Prefs.argb(shown, txPct, 1f));
        prevMeta.setTextColor(Prefs.argb(theme != 2 && colSub >= 0 ? colSub : shown, txPct, 0.7f));
        prevTitle.setTypeface(Style.typeface(font), android.graphics.Typeface.BOLD);
        prevMeta.setTypeface(Style.typeface(font));
        prevTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.titleSp(size));
        prevMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.metaSp(size));
    }
}
