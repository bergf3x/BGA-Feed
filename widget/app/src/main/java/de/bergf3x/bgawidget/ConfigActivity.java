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
    private int bgMode, bgC1, bgC2, bgTint, bgSrc;
    private int accent = -1;
    private boolean bgRound;
    private LinearLayout bgBox;
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
    private final java.util.Map<String, Boolean> plusF = new java.util.HashMap<>();
    private EditText etKw;
    private boolean kwNotifyOn;
    private boolean compact;
    private boolean notify;
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
        compact = Prefs.compact(this, widgetId);
        notify = Prefs.warnNotify(this);
        bgMode = BgStyle.mode(this, widgetId);
        bgC1 = BgStyle.c1(this, widgetId);
        bgC2 = BgStyle.c2(this, widgetId);
        bgTint = BgStyle.tint(this, widgetId);
        bgSrc = BgStyle.src(this, widgetId);
        accent = Prefs.accent(this, widgetId);
        bgRound = BgStyle.sysCorner(this, widgetId);
        kwNotifyOn = Prefs.kwNotify(this);
        selected.addAll(Prefs.feeds(this, widgetId));
        muted.addAll(Prefs.muted(this, widgetId));

        b = new Bubble(this, 360, 16);
        kwText = Prefs.keywords(this);
        showMain();

        // Katalog im Hintergrund aktualisieren (neue Feeds erscheinen ohne App-Update)
        new Thread(() -> {
            String st = Catalog.refreshStatus(getApplicationContext());
            runOnUiThread(() -> {
                if (catStatus != null) catStatus.setText(st);
                buildFeeds();
                buildMutes();
            });
        }).start();
    }

    // ---------- Seiten ----------
    private static final int P_MAIN = 0, P_SOURCES = 1, P_DESIGN = 2, P_LIST = 3, P_WEATHER = 4, P_COLORS = 5;
    private int page = P_MAIN;
    private String kwText = "";

    private boolean firstResume = true;

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (b != null && page == P_WEATHER) showWeather();
        else if (b != null && page == P_MAIN) showMain();
    }

    @Override
    public void onBackPressed() {
        if (page == P_COLORS) showDesign();
        else if (page != P_MAIN) showMain();
        else super.onBackPressed();
    }

    private void clear() {
        if (etKw != null) kwText = etKw.getText().toString();
        etKw = null;
        feedBox = null;
        muteBox = null;
        catStatus = null;
        prevFill = null;
        lblBg = null;
        lblTx = null;
        bgBox = null;
        b.box.removeAllViews();
    }

    private LinearLayout subPage(String title, int pg, float frac, boolean preview) {
        return subPage(title, pg, frac, preview, this::showMain);
    }

    private LinearLayout subPage(String title, int pg, float frac, boolean preview, Runnable back) {
        clear();
        page = pg;
        b.header(title, back);
        if (preview) buildPreview();
        return b.scrollBox(frac);
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String ivName(int idx) {
        return Prefs.INTERVAL_NAMES[idx];
    }

    private void showMain() {
        clear();
        page = P_MAIN;
        b.text("Einstellungen", 20, b.title, true).setPadding(b.px(4), 0, 0, b.px(4));
        if (widgetId == Prefs.APP) {
            b.tile("Widget hinzufügen", "Nachrichten- oder Wetter-Widget auf den Startbildschirm", () ->
                    startActivity(new Intent(this, AddWidgetActivity.class)));
        }
        b.tile("Quellen & Themen", selected.size() + (selected.size() == 1 ? " Quelle" : " Quellen") + " · Stichwörter", this::showSources);
        b.tile("Design", (theme == 1 ? "Hell" : "Dunkel") + " · "
                + Style.FONT_NAMES[Math.max(0, Math.min(Style.FONT_NAMES.length - 1, font))] + " · Farben", this::showDesign);
        b.tile("Liste", (density == 1 ? "Luftig" : "Kompakt") + " · Themen ausblenden · Gemerkte", this::showList);
        b.tile("Wetter", Weather.PLACES[Prefs.place(this, widgetId)] + (wxOn ? "" : " · ausgeblendet"), this::showWeather);
        b.tile("Aktualisierung", "Wetter " + ivName(Prefs.wxIdx(this)) + " · Nachrichten " + ivName(Prefs.newsIdx(this)), () -> {
            Intent in = new Intent(this, UpdateActivity.class);
            in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            startActivity(in);
        });
        b.tile("Info", "bergf3x · Version " + version() + " · " + getString(R.string.build_date), () ->
                startActivity(new Intent(this, InfoActivity.class)));
        b.space(10);
        b.pill("Speichern", true, true, this::save);
        b.pill("Abbrechen", false, true, () -> { });
    }

    private void showSources() {
        LinearLayout body = subPage("Quellen & Themen", P_SOURCES, 0.62f, false);
        catStatus = b.label(body, "Katalog wird geladen ...");
        feedBox = new LinearLayout(this);
        feedBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(feedBox);
        buildFeeds();

        b.label(body, "Stichwörter (mit Komma trennen, z. B. Sperrung, Vermisst, Lawine)");
        etKw = new EditText(this);
        etKw.setSingleLine(false);
        etKw.setText(kwText);
        etKw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        etKw.setTextColor(b.tx);
        etKw.setBackgroundTintList(ColorStateList.valueOf(b.soft));
        body.addView(etKw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.toggle(body, "Treffer als Benachrichtigung melden", kwNotifyOn, on -> {
            kwNotifyOn = on;
            askNotifPermission(on);
        });
        b.pillKeep("Fertig", true, this::showMain);
    }

    private void askNotifPermission(boolean on) {
        if (on && android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void showList() {
        LinearLayout body = subPage("Liste", P_LIST, 0.62f, false);
        b.label(body, "Dichte der Liste");
        b.segment(body, new String[]{"Kompakt", "Luftig"}, density, i -> density = i);
        b.label(body, "Themen ausblenden (markiert = versteckt)");
        muteBox = new LinearLayout(this);
        muteBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(muteBox);
        buildMutes();
        b.pillKeep("Gemerkte Artikel ansehen", false, () -> startActivity(new Intent(this, SavedActivity.class)));
        b.pillKeep("Fertig", true, this::showMain);
    }

    private void showWeather() {
        LinearLayout body = subPage("Wetter", P_WEATHER, 0.62f, false);
        b.label(body, "Ort");
        b.pillKeep(Weather.PLACES[Prefs.place(this, widgetId)] + "  ▾", false, () -> {
            Intent in = new Intent(this, PlaceActivity.class);
            in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            startActivity(in);
        });
        b.label(body, "Anzeige");
        b.toggle(body, "Wetterbereich anzeigen", wxOn, on -> wxOn = on);
        b.toggle(body, "Wetter kompakt (ohne Vorschau und Zusatzzeilen)", compact, on -> compact = on);
        b.label(body, "Warnungen");
        b.toggle(body, "Unwetterwarnungen anzeigen", warnOn, on -> warnOn = on);
        b.toggle(body, "Unwetterwarnung als Benachrichtigung (ab Stufe 3)", notify, on -> {
            notify = on;
            askNotifPermission(on);
        });
        b.pillKeep("Fertig", true, this::showMain);
    }

    private void showDesign() {
        LinearLayout body = subPage("Design", P_DESIGN, 0.40f, true, this::showMain);
        b.label(body, "Modus");
        b.segment(body, new String[]{"Dunkel", "Hell"}, theme, i -> {
            theme = i;
            if (i == 1 && color == 0xFFFFFF) color = 0x1B1F22;
            if (i == 0 && color == 0x1B1F22) color = 0xFFFFFF;
            refreshPreview();
        });
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
        refreshPreview();
        b.pillKeep("Farben des Widgets …", false, this::showColors);
        b.pillKeep("Fertig", true, this::showMain);
    }

    /** Alle Farben des Widgets an einem Ort: Hintergrund, Schrift, Nebenzeilen, Wetter, Akzent. */
    private void showColors() {
        LinearLayout body = subPage("Farben", P_COLORS, 0.40f, true, this::showDesign);
        b.label(body, "Hintergrund");
        b.segment(body, new String[]{"Standard", "Farbe", "Dynamisch"}, bgMode, i -> {
            bgMode = i;
            buildBgBox();
            refreshPreview();
        });
        bgBox = new LinearLayout(this);
        bgBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(bgBox);
        buildBgBox();

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

        b.label(body, "Akzent (aktiver Chip), erstes Feld = Standard");
        final LinearLayout accBox = new LinearLayout(this);
        body.addView(accBox);
        buildAccent(accBox);

        b.pillKeep("Nebenzeilen, Wetter und Verlauf …", false, () -> {
            Intent in = new Intent(this, ColorsActivity.class);
            in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            startActivity(in);
        });
        refreshPreview();
        b.pillKeep("Fertig", true, this::showDesign);
    }

    private void buildAccent(final LinearLayout box) {
        box.removeAllViews();
        box.addView(swatches(accent, true, c -> {
            accent = c;
            buildAccent(box);
        }));
    }

    private static final int[] BG_COLORS = {
            0x3B6EA5, 0x118AB2, 0x1F8A70, 0x2A9D8F, 0x8E5BA8, 0xC8553D, 0xE63946, 0xD9A441, 0xF4A261,
            0xFF8FB1, 0x7DF0C0, 0x2B2D42, 0x5C677D, 0xFFFFFF, 0x000000
    };

    private void buildBgBox() {
        if (bgBox == null) return;
        bgBox.removeAllViews();
        if (bgMode == BgStyle.FIXED) {
            b.label(bgBox, "Farbe");
            bgBox.addView(swatches(bgC1, false, c -> {
                bgC1 = c;
                buildBgBox();
                refreshPreview();
            }));
            b.label(bgBox, "Zweite Farbe für Verlauf (links → rechts)");
            bgBox.addView(swatches(bgC2, true, c -> {
                bgC2 = c;
                buildBgBox();
                refreshPreview();
            }));
        } else if (bgMode == BgStyle.DYNAMIC) {
            b.label(bgBox, "Farbquelle");
            b.segment(bgBox, new String[]{"Systempalette", "Hintergrundbild"}, bgSrc, i -> {
                bgSrc = i;
                buildBgBox();
                refreshPreview();
            });
            final int[] det = bgSrc == 1 ? BgStyle.wallpaper(this) : BgStyle.palette(this, lightNow());
            b.label(bgBox, "Erkannte Farben");
            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            for (int c : det) strip.addView(swatch(c, false, b.px(34), x -> { }));
            bgBox.addView(strip);
            final TextView lt = b.label(bgBox, "Tönung: " + bgTint + " %");
            bgBox.addView(seek(Math.max(1, bgTint), p -> {
                bgTint = p;
                lt.setText("Tönung: " + p + " %");
                refreshPreview();
            }));
            if (det.length == 0) {
                b.label(bgBox, "Das System liefert hier keine Farben, es bleibt beim Standard.");
            }
            b.toggle(bgBox, "Ecken wie bei Systemwidgets", bgRound, on -> bgRound = on);
        }
    }

    private interface RgbCb {
        void on(int rgb);
    }

    /** Reihe runder Farbfelder; allowNone = erstes Feld "aus" (-1). */
    private View swatches(int current, boolean allowNone, RgbCb cb) {
        android.widget.HorizontalScrollView sv = new android.widget.HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int size = b.px(34);
        if (allowNone) row.addView(swatch(-1, current == -1, size, cb));
        for (int c : BG_COLORS) row.addView(swatch(c, c == (current & 0xFFFFFF) && current != -1, size, cb));
        sv.addView(row);
        return sv;
    }

    private View swatch(int c, boolean on, int size, RgbCb cb) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(c < 0 ? 0x00000000 : 0xFF000000 | c);
        g.setStroke(b.px(on ? 3 : 1), on ? b.title : b.soft);
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, b.px(7), 0);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> cb.on(c));
        return v;
    }

    /** Live-Vorschau auf einem bunten Hintergrund, damit man die Transparenz sieht. */
    private void buildPreview() {
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
        Prefs.setColors(this, widgetId, Prefs.colorSub(this, widgetId), Prefs.colorWx(this, widgetId), compact, notify);
        BgStyle.set(this, widgetId, bgMode, bgC1, bgC2, bgTint, bgRound, bgSrc);
        Prefs.setAccent(this, widgetId, accent);
        Prefs.setKeywords(this, etKw != null ? etKw.getText().toString() : kwText, kwNotifyOn);
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

    private void buildFeeds() {
        if (feedBox == null) return;
        feedBox.removeAllViews();
        for (Catalog.Feed f : Catalog.load(this)) {
            b.toggle(feedBox, f.name, selected.contains(f.id), on -> {
                if (on) selected.add(f.id);
                else selected.remove(f.id);
            });
            if (!Catalog.hasPaywall(f.id)) continue;
            if (!plusF.containsKey(f.id)) plusF.put(f.id, Prefs.showPlusFeed(this, widgetId, f.id));
            b.toggle(feedBox, "      Bezahlartikel anzeigen (🔒)", plusF.get(f.id), on -> plusF.put(f.id, on));
        }
    }

    private void buildMutes() {
        if (muteBox == null) return;
        muteBox.removeAllViews();
        Set<String> seen = new HashSet<>();
        for (Catalog.Feed f : Catalog.load(this)) {
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

    private boolean lightNow() {
        return theme == 1;
    }

    private void refreshPreview() {
        if (prevFill == null) return;
        if (lblBg != null) lblBg.setText("Hintergrund-Deckkraft: " + bgPct + " %");
        if (lblTx != null) lblTx.setText("Schrift-Deckkraft: " + txPct + " %");
        int[] fc = BgStyle.colors(this, bgMode, bgC1, bgC2, bgTint, lightNow(), bgSrc);
        int a = Prefs.alpha(bgPct);
        int[] cs = new int[Math.max(2, fc.length)];
        for (int i = 0; i < cs.length; i++) cs[i] = (a << 24) | (fc[Math.min(i, fc.length - 1)] & 0xFFFFFF);
        prevFill.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        prevFill.setColors(cs);
        int shown = color;
        prevTitle.setTextColor(Prefs.argb(shown, txPct, 1f));
        prevMeta.setTextColor(Prefs.argb(shown, txPct, 0.7f));
        prevTitle.setTypeface(Style.typeface(font), android.graphics.Typeface.BOLD);
        prevMeta.setTypeface(Style.typeface(font));
        prevTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.titleSp(size));
        prevMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.metaSp(size));
    }
}
