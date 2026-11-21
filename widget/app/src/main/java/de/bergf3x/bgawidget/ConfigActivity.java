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
    /** Grosse Palette fuer Nebenzeilen und Wetterbereich. */
    private static final int[] PAL = {
            0xFFFFFF, 0xD9D9D9, 0x9CC9FF, 0x4DA3FF, 0x7CE3BE, 0x2ED47A, 0xFFE08A,
            0xFFB020, 0xFF8A5B, 0xFF5C5C, 0xFF8AD1, 0xB58CFF, 0x1B1F22
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
    private TextView prevWx;
    private Pick pickSub;
    private Pick pickWx;
    private TextView lblBg;
    private TextView lblTx;
    private EditText etColor;
    private LinearLayout feedBox;
    private TextView catStatus;
    private final java.util.Map<String, Boolean> plusF = new java.util.HashMap<>();
    private EditText etKw;
    private boolean kwNotifyOn;
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
        kwNotifyOn = Prefs.kwNotify(this);
        selected.addAll(Prefs.feeds(this, widgetId));
        muted.addAll(Prefs.muted(this, widgetId));

        b = new Bubble(this, 360, 16);
        b.text("Einstellungen", 18, b.title, true).setPadding(0, 0, 0, b.px(10));

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
        prevWx = new TextView(this);
        prevWx.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        prevWx.setPadding(0, b.px(6), 0, 0);
        card.addView(prevWx);
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
        etColor.setTextColor(b.tx);
        etColor.setBackgroundTintList(ColorStateList.valueOf(b.soft));
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
            if (pickSub != null) pickSub.paint();
            if (pickWx != null) pickWx.paint();
        });

        pickSub = new Pick(body, "Farbe der Nebenzeilen", colSub, Prefs.colorSub2(this, widgetId));
        pickWx = new Pick(body, "Farbe des Wetterbereichs", colWx, Prefs.colorWx2(this, widgetId));

        b.label(body, "Stichwörter (mit Komma trennen, z. B. Sperrung, Vermisst, Lawine)");
        etKw = new EditText(this);
        etKw.setSingleLine(false);
        etKw.setText(Prefs.keywords(this));
        etKw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        etKw.setTextColor(b.tx);
        etKw.setBackgroundTintList(ColorStateList.valueOf(b.soft));
        body.addView(etKw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.toggle(body, "Treffer als Benachrichtigung melden", kwNotifyOn, on -> {
            kwNotifyOn = on;
            if (on && android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            }
        });

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
        sb.setProgressTintList(ColorStateList.valueOf(b.hi));
        sb.setThumbTintList(ColorStateList.valueOf(b.hi));
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
        Prefs.setColors(this, widgetId, pickSub.c1, pickWx.c1, compact, notify);
        Prefs.setColors2(this, widgetId, pickSub.c1 >= 0 ? pickSub.c2 : -1, pickWx.c1 >= 0 ? pickWx.c2 : -1);
        Prefs.setKeywords(this, etKw.getText().toString(), kwNotifyOn);
        for (java.util.Map.Entry<String, Boolean> e : plusF.entrySet()) Prefs.setPlusFeed(this, widgetId, e.getKey(), e.getValue());
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
            g.setStroke(b.px(on ? 3 : 1), on ? b.title : b.soft);
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
            if (!plusF.containsKey(f.id)) plusF.put(f.id, Prefs.showPlusFeed(this, widgetId, f.id));
            b.toggle(feedBox, "      Bezahlartikel anzeigen (🔒)", plusF.get(f.id), on -> plusF.put(f.id, on));
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

    private void tint(TextView t, String text, int c1, int c2, int base, float f) {
        if (c1 >= 0 && c2 >= 0) {
            t.setText(Pal.gradient(text, c1, c2, txPct, f));
        } else {
            t.setText(text);
            t.setTextColor(Prefs.argb(c1 >= 0 ? c1 : base, txPct, f));
        }
    }

    private static boolean tooBright(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0 > 0.75;
    }

    private static boolean tooDark(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0 < 0.25;
    }

    private View swatch(int c, int cur, String lbl, IntCb pick) {
        TextView v = new TextView(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        boolean on = c == cur;
        g.setColor(c < 0 ? 0x00000000 : 0xFF000000 | c);
        g.setStroke(b.px(on ? 3 : 1), on ? b.title : b.soft);
        v.setBackground(g);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTextColor(b.tx);
        if (c < 0) v.setText(lbl);
        v.setOnClickListener(x -> pick.on(c));
        return v;
    }

    private void fillRows(LinearLayout host, int cur, String firstLbl, IntCb pick) {
        host.removeAllViews();
        int[] all = new int[PAL.length + 1];
        all[0] = -1;
        System.arraycopy(PAL, 0, all, 1, PAL.length);
        int sz = b.px(34);
        LinearLayout row = null;
        for (int i = 0; i < all.length; i++) {
            if (i % 7 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = b.px(i == 0 ? 0 : 6);
                host.addView(row, rl);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.rightMargin = b.px(7);
            row.addView(swatch(all[i], cur, firstLbl, pick), lp);
        }
    }

    /** Farbwahl mit Palette, Hex-Feld und optionalem zweiten Farbton fuer einen Verlauf. */
    private final class Pick {
        int c1;
        int c2;
        private final LinearLayout h1;
        private final LinearLayout h2;
        private final EditText e1;
        private final EditText e2;
        private final TextView warn;
        private boolean setting;

        Pick(LinearLayout body, String title, int c1, int c2) {
            this.c1 = c1;
            this.c2 = c2;
            b.label(body, title);
            h1 = new LinearLayout(ConfigActivity.this);
            h1.setOrientation(LinearLayout.VERTICAL);
            body.addView(h1);
            e1 = hex(body, v -> {
                this.c1 = v;
                paint();
            });
            b.label(body, "Verlauf bis (optional, nur mit eigener Startfarbe)");
            h2 = new LinearLayout(ConfigActivity.this);
            h2.setOrientation(LinearLayout.VERTICAL);
            body.addView(h2);
            e2 = hex(body, v -> {
                this.c2 = v;
                paint();
            });
            warn = b.label(body, "");
            warn.setTextColor(0xFFFFB74D);
            setTexts();
            paint();
        }

        private EditText hex(LinearLayout body, IntCb cb) {
            EditText e = new EditText(ConfigActivity.this);
            e.setSingleLine(true);
            e.setHint("Hex, z. B. 4DA3FF");
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            e.setTextColor(b.tx);
            e.setHintTextColor(b.soft);
            e.setBackgroundTintList(ColorStateList.valueOf(b.soft));
            e.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

                @Override
                public void onTextChanged(CharSequence s, int a, int c, int d) {}

                @Override
                public void afterTextChanged(Editable ed) {
                    if (setting) return;
                    String t = ed.toString().trim();
                    if (t.startsWith("#")) t = t.substring(1);
                    if (t.length() == 6) {
                        try {
                            cb.on(Integer.parseInt(t, 16) & 0xFFFFFF);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            });
            body.addView(e, new LinearLayout.LayoutParams(b.px(150), ViewGroup.LayoutParams.WRAP_CONTENT));
            return e;
        }

        void paint() {
            fillRows(h1, c1, "T", v -> {
                c1 = v;
                setTexts();
                paint();
            });
            fillRows(h2, c2, "–", v -> {
                c2 = v;
                setTexts();
                paint();
            });
            boolean bright = (c1 >= 0 && tooBright(c1)) || (c1 >= 0 && c2 >= 0 && tooBright(c2));
            boolean dark = (c1 >= 0 && tooDark(c1)) || (c1 >= 0 && c2 >= 0 && tooDark(c2));
            String w = "";
            if (bright && (theme == 1 || theme == 2)) w = "Achtung: Diese Farbe ist auf hellem Design schlecht lesbar.";
            else if (dark && (theme == 0 || theme == 2)) w = "Achtung: Diese Farbe ist auf dunklem Design schlecht lesbar.";
            warn.setText(w);
            warn.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
            refreshPreview();
        }

        private void setTexts() {
            setting = true;
            e1.setText(c1 >= 0 ? String.format("%06X", c1) : "");
            e2.setText(c2 >= 0 ? String.format("%06X", c2) : "");
            setting = false;
        }
    }

    private void refreshPreview() {
        lblBg.setText("Hintergrund-Deckkraft: " + bgPct + " %");
        lblTx.setText("Schrift-Deckkraft: " + txPct + " %");
        prevFill.setColor(Prefs.argb(lightNow() ? 0xEEF1F6 : 0x1E1E26, bgPct, 1f));
        int shown = theme == 2 ? (lightNow() ? 0x1B1F22 : 0xFFFFFF) : color;
        prevTitle.setTextColor(Prefs.argb(shown, txPct, 1f));
        if (pickSub != null) tint(prevMeta, "Region · vor 12 Min.", pickSub.c1, pickSub.c2, shown, 0.7f);
        if (pickWx != null) tint(prevWx, "Regen 100 %   ·   Wind 8 km/h\nSonne 07:21 – 16:25\nNullgrad 1900 m   ·   Schnee ab ca. 1500 m", pickWx.c1, pickWx.c2, shown, 0.7f);
        prevWx.setTypeface(Style.typeface(font));
        prevWx.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.metaSp(size));
        prevTitle.setTypeface(Style.typeface(font), android.graphics.Typeface.BOLD);
        prevMeta.setTypeface(Style.typeface(font));
        prevTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.titleSp(size));
        prevMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.metaSp(size));
    }
}
