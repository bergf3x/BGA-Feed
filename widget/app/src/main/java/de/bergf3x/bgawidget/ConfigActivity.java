/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
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
    private int showsIdx;
    private boolean wcRegionShow;
    private java.util.Set<String> wcBerg;
    private boolean wcRegion;
    private java.util.Set<String> wcWarn;
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
        showsIdx = Prefs.widgetShows(this, widgetId);
        wcRegionShow = Prefs.topic(this, widgetId).equals(WeatherCards.REGION);
        wcBerg = Prefs.bergRegions(this);
        wcRegion = Prefs.regionCards(this);
        wcWarn = Prefs.warnRegions(this);
        bgMode = BgStyle.mode(this, widgetId);
        bgC1 = BgStyle.c1(this, widgetId);
        bgC2 = BgStyle.c2(this, widgetId);
        bgTint = BgStyle.tint(this, widgetId);
        bgSrc = BgStyle.src(this, widgetId);
        accent = Prefs.accent(this, widgetId);
        bgRound = BgStyle.sysCorner(this, widgetId);
        kwNotifyOn = Prefs.kwNotify(this);
        haptic = Haptic.enabled(this);
        pageSwipe = Motion.swipe(this);
        reduced = Motion.reduced(this);
        imgMode = Prefs.imgMode(this);
        selected.addAll(Prefs.feeds(this, widgetId));
        muted.addAll(Prefs.muted(this, widgetId));

        b = new Bubble(this, 360, 16);
        b.high();
        kwText = Prefs.keywords(this);
        baseSnap = snap();
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
    private static final int P_MAIN = 0, P_SOURCES = 1, P_DESIGN = 2, P_LIST = 3, P_WEATHER = 4, P_COLORS = 5, P_NOTIFY = 6, P_SHOWS = 7;
    private boolean haptic, pageSwipe, reduced;
    private int imgMode;
    private int page = P_MAIN;
    private String kwText = "";

    private String baseSnap = "";

    /** Alle Entwurfswerte als Text: Aenderung = Knopf "OK" wird hell. */
    private String snap() {
        StringBuilder sb = new StringBuilder();
        sb.append(bgPct).append('|').append(txPct).append('|').append(color).append('|').append(plus).append('|')
                .append(density).append('|').append(font).append('|').append(size).append('|').append(theme).append('|')
                .append(wxOn).append('|').append(warnOn).append('|').append(bgMode).append('|').append(bgC1).append('|')
                .append(bgC2).append('|').append(bgTint).append('|').append(bgSrc).append('|').append(accent).append('|')
                .append(bgRound).append('|').append(kwNotifyOn).append('|').append(compact).append('|')
                .append(notify).append('|').append(haptic).append('|').append(pageSwipe).append('|').append(reduced).append('|').append(imgMode).append('|')
                .append(etKw != null ? etKw.getText().toString() : kwText).append('|')
                .append(new java.util.TreeSet<>(selected)).append('|').append(new java.util.TreeSet<>(muted)).append('|')
                .append(new java.util.TreeMap<>(plusF)).append(new java.util.TreeSet<>(wcBerg)).append(wcRegion)
                .append(new java.util.TreeSet<>(wcWarn)).append(showsIdx).append(wcRegionShow);
        return sb.toString();
    }

    /** Stand aller Entwurfswerte beim Betreten einer Unterseite, damit "Abbrechen" sie zuruecksetzen kann. */
    private static final class St {
        int bgPct, txPct, color, density, font, size, theme, bgMode, bgC1, bgC2, bgTint, bgSrc, accent, imgMode, showsIdx;
        boolean plus, wxOn, warnOn, bgRound, kwNotifyOn, compact, notify, haptic, pageSwipe, reduced, wcRegion, wcRegionShow;
        String kw;
        Set<String> selected, muted, wcBerg, wcWarn;
        java.util.Map<String, Boolean> plusF;
    }

    private final java.util.HashMap<Integer, St> entries = new java.util.HashMap<>();

    private St capture() {
        St t = new St();
        t.bgPct = bgPct; t.txPct = txPct; t.color = color; t.density = density; t.font = font; t.size = size;
        t.theme = theme; t.bgMode = bgMode; t.bgC1 = bgC1; t.bgC2 = bgC2; t.bgTint = bgTint; t.bgSrc = bgSrc;
        t.accent = accent; t.imgMode = imgMode; t.showsIdx = showsIdx; t.plus = plus; t.wxOn = wxOn;
        t.warnOn = warnOn; t.bgRound = bgRound; t.kwNotifyOn = kwNotifyOn; t.compact = compact; t.notify = notify;
        t.haptic = haptic; t.pageSwipe = pageSwipe; t.reduced = reduced; t.wcRegion = wcRegion; t.wcRegionShow = wcRegionShow;
        t.kw = etKw != null ? etKw.getText().toString() : kwText;
        t.selected = new HashSet<>(selected);
        t.muted = new HashSet<>(muted);
        t.wcBerg = new HashSet<>(wcBerg);
        t.wcWarn = new HashSet<>(wcWarn);
        t.plusF = new java.util.HashMap<>(plusF);
        return t;
    }

    private void restore(St t) {
        if (t == null) return;
        bgPct = t.bgPct; txPct = t.txPct; color = t.color; density = t.density; font = t.font; size = t.size;
        theme = t.theme; bgMode = t.bgMode; bgC1 = t.bgC1; bgC2 = t.bgC2; bgTint = t.bgTint; bgSrc = t.bgSrc;
        accent = t.accent; imgMode = t.imgMode; showsIdx = t.showsIdx; plus = t.plus; wxOn = t.wxOn;
        warnOn = t.warnOn; bgRound = t.bgRound; kwNotifyOn = t.kwNotifyOn; compact = t.compact; notify = t.notify;
        wcRegion = t.wcRegion; wcRegionShow = t.wcRegionShow;
        if (haptic != t.haptic) {
            haptic = t.haptic;
            Haptic.set(this, haptic);
        }
        if (pageSwipe != t.pageSwipe) {
            pageSwipe = t.pageSwipe;
            Motion.setSwipe(this, pageSwipe);
        }
        if (reduced != t.reduced) {
            reduced = t.reduced;
            Motion.setReduced(this, reduced);
        }
        kwText = t.kw;
        if (etKw != null) etKw.setText(t.kw);
        selected.clear(); selected.addAll(t.selected);
        muted.clear(); muted.addAll(t.muted);
        wcBerg = new HashSet<>(t.wcBerg);
        wcWarn = new HashSet<>(t.wcWarn);
        plusF.clear(); plusF.putAll(t.plusF);
    }

    /** Eine Ebene zurueck: Farben -> Darstellung, Themen ausblenden -> Nachrichten, sonst Hauptmenue. */
    private void goBack(int pg) {
        if (pg == P_COLORS) showDesign();
        else if (pg == P_LIST) showSources();
        else showMain();
    }

    /** Einheitliche Fusszeile einer Unterseite: "Abbrechen" setzt die Seite zurueck, "OK" speichert sofort. */
    private void done(final Runnable back) {
        final String base = snap();
        final int pg = page;
        TextView ok = b.footer(false, () -> cancelPage(pg), () -> {
            save();
            baseSnap = snap();
            entries.remove(pg);
            back.run();
        });
        b.watch(ok, () -> !snap().equals(base));
    }

    /** Aenderungen dieser Seite verwerfen (auch schon gespeicherte, falls eine untere Ebene sie uebernommen hatte). */
    private void cancelPage(int pg) {
        St t = entries.remove(pg);
        restore(t);
        if (t != null && !snap().equals(baseSnap)) {
            save();
            baseSnap = snap();
        }
        goBack(pg);
    }

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
        if (page != P_MAIN) cancelPage(page);
        else b.close();
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
        Bubble.soft(b.box);
        b.box.removeAllViews();
    }

    private LinearLayout subPage(String title, int pg, float frac, boolean preview) {
        return subPage(title, pg, frac, preview, this::showMain);
    }

    private LinearLayout subPage(String title, int pg, float frac, boolean preview, Runnable back) {
        clear();
        page = pg;
        if (!entries.containsKey(pg)) entries.put(pg, capture());
        b.header(title, () -> cancelPage(pg));
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

    /** Untertitel der Kachel "Aktualisierung": im Widget die Intervalle (oder manuell), in der App nur die Werkzeuge. */
    private String ivSummary() {
        if (widgetId == Prefs.APP) return "Jetzt aktualisieren · Akku · Berechtigungen";
        String w = "Wetter " + (Prefs.wxAuto(this) ? ivName(Prefs.wxIdx(this)) : "manuell");
        if (WidgetProvider.isWeatherWidget(this, widgetId)) return w;
        return "Nachrichten " + (Prefs.newsAuto(this) ? ivName(Prefs.newsIdx(this)) : "manuell") + " · " + w;
    }

    private int savedCount() {
        try {
            return Prefs.saved(this).length();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Hauptmenue: Inhalte, Aussehen, System – wie in etablierten Nachrichten-Apps. */
    private void showMain() {
        clear();
        entries.clear();
        page = P_MAIN;
        b.text("Einstellungen", 20, b.title, true).setPadding(b.px(4), 0, 0, b.px(2));
        LinearLayout body = b.scrollBox(0.9f);
        LinearLayout keep = b.box;
        // Kacheln in den scrollbaren Bereich legen
        java.util.function.Consumer<Runnable> into = r -> {
            int before = keep.getChildCount();
            r.run();
            while (keep.getChildCount() > before) {
                View v = keep.getChildAt(before);
                keep.removeViewAt(before);
                body.addView(v);
            }
        };
        int n = selected.size();
        into.accept(() -> {
            b.group("Inhalte");
            b.tile(R.drawable.ic_news, "Nachrichten", n + (n == 1 ? " Quelle" : " Quellen") + " · Abo-Artikel · Themen ausblenden", this::showSources);
            b.tile(R.drawable.ic_cloud, "Wetter", Weather.PLACES[Prefs.place(this, widgetId)]
                    + (wxOn ? "" : " · ausgeblendet") + " · " + wcBerg.size() + " Bergregionen"
                    + (notify ? " · Warnungen an" : ""), this::showWeather);
            int sc = savedCount();
            b.tile(R.drawable.ic_bookmark, "Gemerkt", sc < 0 ? "Später lesen" : sc + (sc == 1 ? " Artikel" : " Artikel"),
                    () -> startActivity(new Intent(this, SavedActivity.class)));
            b.group("Benachrichtigungen");
            b.tile(R.drawable.ic_bell, "Stichwörter", kwNotifyOn ? "An" : "Aus", this::showNotify);
            if (widgetId != Prefs.APP) {
                b.group("Dieses Widget");
                b.tile(R.drawable.ic_widgets, "Inhalt", SHOWS[showsIdx] + (showsIdx == 1 ? " · " + (wcRegionShow ? "Region" : "Bergwetter") : ""),
                        this::showShows);
            }
            b.group("Aussehen");
            b.tile(R.drawable.ic_palette, "Darstellung", (theme == 1 ? "Hell" : "Dunkel") + " · "
                    + Style.FONT_NAMES[Math.max(0, Math.min(Style.FONT_NAMES.length - 1, font))] + " · "
                    + (density == 1 ? "Luftig" : "Kompakt"), this::showDesign);
            if (widgetId == Prefs.APP) {
                b.tile(R.drawable.ic_widgets, "Widgets", "Nachrichten, Wetter oder beides auf den Startbildschirm",
                        () -> startActivity(new Intent(this, AddWidgetActivity.class)));
            }
            b.group("System");
            b.tile(R.drawable.ic_refresh, "Aktualisierung", ivSummary(), () -> {
                Intent in = new Intent(this, UpdateActivity.class);
                in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                startActivity(in);
            });
            b.tile(R.drawable.ic_info, "Über die App", "Version " + version() + " · Neuigkeiten · Quellen · Bildnachweise",
                    () -> startActivity(new Intent(this, InfoActivity.class)));
        });
        b.space(6);
        TextView ok = b.footer(true, () -> { }, this::save);
        b.watch(ok, () -> !snap().equals(baseSnap));
    }

    /** Nachrichten: Quellen (mit Abo-Schalter je Zeitung) und Themen ausblenden. */
    private void showSources() {
        LinearLayout body = subPage("Nachrichten", P_SOURCES, 0.6f, false);
        b.box.removeView((View) body.getParent());
        b.hint("Welche Zeitungen und Themen in der Liste erscheinen.");
        b.box.addView((View) body.getParent());
        b.label(body, "Quellen");
        feedBox = new LinearLayout(this);
        feedBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(feedBox);
        buildFeeds();
        catStatus = b.label(body, "");
        catStatus.setVisibility(View.GONE);
        b.label(body, "Themen");
        final int before = b.box.getChildCount();
        b.tile(R.drawable.ic_tune, "Themen ausblenden", muted.isEmpty() ? "Alle Themen sichtbar" : muted.size() + " ausgeblendet", this::showMutes);
        View tile = b.box.getChildAt(before);
        b.box.removeView(tile);
        body.addView(tile);
        done(this::showMain);
    }

    /** Themen ausblenden: BGL-Regionen und Rubriken von Deutschland & Welt. */
    private void showMutes() {
        LinearLayout body = subPage("Themen ausblenden", P_LIST, 0.6f, false, this::showSources);
        b.box.removeView((View) body.getParent());
        b.hint("Eingeschaltete Themen werden in der Liste versteckt. Ein gewählter Reiter zeigt sie trotzdem.");
        b.box.addView((View) body.getParent());
        muteBox = new LinearLayout(this);
        muteBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(muteBox);
        buildMutes();
        done(this::showSources);
    }

    /** Benachrichtigungen: Stichwoerter und Unwetter. */
    private void showNotify() {
        LinearLayout body = subPage("Benachrichtigungen", P_NOTIFY, 0.6f, false);
        b.box.removeView((View) body.getParent());
        b.hint("Wann dich die App von sich aus benachrichtigt.");
        b.box.addView((View) body.getParent());
        b.label(body, "Stichwörter");
        etKw = new EditText(this);
        etKw.setSingleLine(false);
        etKw.setText(kwText);
        etKw.setHint("z. B. Sperrung, Vermisst, Lawine");
        etKw.setHintTextColor(b.soft);
        etKw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        etKw.setTextColor(b.tx);
        etKw.setBackground(Depth.sunken(b.light ? 0xFFE2E5EC : 0xFF17191E, b.px(10), b.d, b.light));
        etKw.setPadding(b.px(10), b.px(8), b.px(10), b.px(8));
        body.addView(etKw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.toggle(body, "Treffer als Benachrichtigung melden", kwNotifyOn, on -> {
            kwNotifyOn = on;
            askNotifPermission(on);
        });
        done(this::showMain);
    }

    private void askNotifPermission(boolean on) {
        if (on && android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void wcardsInto(LinearLayout body) {
        b.label(body, "Bergwetter (DAV)");
        for (final String[] r : WeatherCards.BERG_REGIONS) {
            b.toggle(body, r[1], wcBerg.contains(r[0]), on -> {
                if (on) wcBerg.add(r[0]); else wcBerg.remove(r[0]);
            });
        }
        b.label(body, "Region");
        b.toggle(body, "Vorhersage, Radar und Warnlage", wcRegion, on -> wcRegion = on);
        b.label(body, "Wetterwarnungen");
        b.toggle(body, "Warnung melden (ab Stufe 3)", notify, on -> {
            notify = on;
            askNotifPermission(on);
        });
        b.hint("Warnungen für diese Regionen:");
        for (final String[] r : WeatherCards.WARN_REGIONS) {
            b.toggle(body, "   " + r[1], wcWarn.contains(r[0]), on -> {
                if (on) wcWarn.add(r[0]); else wcWarn.remove(r[0]);
            });
        }
    }

    private static final String[] SHOWS = {"Nachrichten", "Wetter", "Gemerkt"};

    private void showShows() {
        LinearLayout body = subPage("Inhalt", P_SHOWS, 0.6f, false);
        b.label(body, "Dieses Widget zeigt");
        b.segment(body, SHOWS, showsIdx, i -> showsIdx = i);
        b.label(body, "Wenn Wetter");
        b.segment(body, new String[]{"Bergwetter", "Region"}, wcRegionShow ? 1 : 0, i -> wcRegionShow = i == 1);
        done(this::showMain);
    }

    private void showWeather() {
        LinearLayout body = subPage("Wetter", P_WEATHER, 0.6f, false);
        b.box.removeView((View) body.getParent());
        b.hint("Ort und was die Wetterkarte zeigt.");
        b.box.addView((View) body.getParent());
        b.label(body, "Ort");
        b.pillKeep(Weather.PLACES[Prefs.place(this, widgetId)] + "  ▾", false, () -> {
            Intent in = new Intent(this, PlaceActivity.class);
            in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            startActivity(in);
        });
        if (widgetId != Prefs.APP && WidgetProvider.isWeatherWidget(this, widgetId)) {
            int p2 = Prefs.place2(this, widgetId);
            b.label(body, "Zweiter Ort (nebeneinander, ab ca. " + WeatherWidgetProvider.DUAL_MIN_DP + " dp Höhe)");
            b.pillKeep((p2 < 0 ? "Keiner" : Weather.PLACES[p2]) + "  ▾", false, () -> {
                Intent in = new Intent(this, PlaceActivity.class);
                in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                in.putExtra("second", true);
                startActivity(in);
            });
        }
        b.label(body, "Anzeige");
        b.toggle(body, "Wetterbereich anzeigen", wxOn, on -> wxOn = on);
        b.toggle(body, "Wetter kompakt (ohne Vorschau und Zusatzzeilen)", compact, on -> compact = on);
        b.label(body, "Warnungen");
        b.toggle(body, "Unwetterwarnungen anzeigen", warnOn, on -> warnOn = on);
        wcardsInto(body);
        done(this::showMain);
    }

    private void showDesign() {
        LinearLayout body = subPage("Darstellung", P_DESIGN, widgetId == Prefs.APP ? 0.55f : 0.40f, true, this::showMain);
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
        if (widgetId != Prefs.APP) {
            // Deckkraft wirkt nur im Widget auf dem Startbildschirm; in der App ist der Hintergrund immer deckend
            lblBg = b.label(body, "");
            body.addView(seek(bgPct, 0, p -> {
                bgPct = p;
                refreshPreview();
            }));
            lblTx = b.label(body, "");
            body.addView(seek(txPct, p -> {
                txPct = p;
                refreshPreview();
            }));
        }
        b.label(body, "Abstand in der Liste");
        b.segment(body, new String[]{"Kompakt", "Luftig"}, density, i -> density = i);
        b.label(body, "Vorschaubilder (Nur WLAN schont dein Datenvolumen)");
        b.segment(body, new String[]{"Aus", "Nur WLAN", "Immer"}, imgMode, i -> imgMode = i);
        b.label(body, "Bedienung");
        b.toggle(body, "Vibration bei Aktionen", haptic, on -> {
            haptic = on;
            Haptic.set(this, on);
        });
        if (widgetId == Prefs.APP) {
            b.toggle(body, "Seiten durch Wischen wechseln", pageSwipe, on -> {
                pageSwipe = on;
                Motion.setSwipe(this, on);
            });
        }
        b.toggle(body, "Animationen reduzieren", reduced, on -> {
            reduced = on;
            Motion.setReduced(this, on);
        });
        refreshPreview();
        b.pillKeep("Farben …", false, this::showColors);
        done(this::showMain);
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
        etColor.setBackground(Depth.sunken(b.light ? 0xFFE2E5EC : 0xFF17191E, b.px(10), b.d, b.light));
        etColor.setPadding(b.px(10), b.px(8), b.px(10), b.px(8));
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
        done(this::showDesign);
    }

    private void buildAccent(final LinearLayout box) {
        Bubble.soft(box);
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
        Bubble.soft(bgBox);
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
        return seek(pct, 1, cb);
    }

    /** Regler von min bis 100 %. */
    private SeekBar seek(int pct, final int min, IntCb cb) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(100 - min);
        sb.setProgress(pct - min);
        sb.setProgressTintList(ColorStateList.valueOf(b.hi));
        sb.setThumbTintList(ColorStateList.valueOf(b.hi));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                cb.on(p + min);
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
        Prefs.setImgMode(this, imgMode);
        Prefs.setWeatherCards(this, wcBerg, wcRegion, wcWarn);
        if (widgetId != Prefs.APP) Prefs.setWidgetShows(this, widgetId, showsIdx, wcRegionShow);
        Prefs.setKeywords(this, etKw != null ? etKw.getText().toString() : kwText, kwNotifyOn);
        for (java.util.Map.Entry<String, Boolean> e : plusF.entrySet()) Prefs.setPlusFeed(this, widgetId, e.getKey(), e.getValue());
        WidgetProvider.updateAny(this, widgetId);
        Scheduler.schedule(this);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
    }

    private void buildSwatches() {
        Bubble.soft(swatchBox);
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
        Bubble.soft(feedBox);
        feedBox.removeAllViews();
        int paid = 0, paidOn = 0;
        for (Catalog.Feed f : Catalog.load(this)) {
            if (!Catalog.hasPaywall(f.id)) continue;
            if (!plusF.containsKey(f.id)) plusF.put(f.id, Prefs.showPlusFeed(this, widgetId, f.id));
            paid++;
            if (plusF.get(f.id)) paidOn++;
        }
        if (paid > 0) {
            final boolean allOn = paidOn == paid;
            b.toggle(feedBox, paidOn == 0 || allOn ? "Abo-Artikel anzeigen (alle Quellen)" : "Abo-Artikel anzeigen (teilweise)", allOn, on -> {
                for (Catalog.Feed g : Catalog.load(this)) if (Catalog.hasPaywall(g.id)) plusF.put(g.id, on);
                feedBox.post(this::buildFeeds);
            });
        }
        for (Catalog.Feed f : Catalog.load(this)) {
            b.toggle(feedBox, f.name, selected.contains(f.id), on -> {
                if (on) selected.add(f.id);
                else selected.remove(f.id);
            });
            if (!Catalog.hasPaywall(f.id)) continue;
            if (!plusF.containsKey(f.id)) plusF.put(f.id, Prefs.showPlusFeed(this, widgetId, f.id));
            b.toggle(feedBox, "  Abo-Artikel anzeigen", plusF.get(f.id), on -> {
                plusF.put(f.id, on);
                feedBox.post(this::buildFeeds);
            });
        }
    }

    private void buildMutes() {
        if (muteBox == null) return;
        Bubble.soft(muteBox);
        muteBox.removeAllViews();
        for (String t : Taxonomy.topics(this, widgetId)) {
            String[] p = t.split("/");
            if (!p[0].equals(Taxonomy.BGL) && !p[0].equals(Taxonomy.WORLD)) continue; // Zeitungen: ueber die Quellen
            if (p.length > 2) continue;
            if (p.length == 1) {
                b.label(muteBox, t);
                b.toggle(muteBox, "Alles aus „" + t + "“", muted.contains(t), on -> {
                    if (on) muted.add(t);
                    else muted.remove(t);
                });
                continue;
            }
            b.toggle(muteBox, "  " + p[1], muted.contains(t), on -> {
                if (on) muted.add(t);
                else muted.remove(t);
            });
        }
    }

    private boolean lightNow() {
        return theme == 1;
    }

    private void refreshPreview() {
        if (prevFill == null) return;
        if (lblBg != null) lblBg.setText("Hintergrund-Deckkraft: " + bgPct + " %" + (bgPct == 0 ? " (durchsichtig)" : ""));
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
