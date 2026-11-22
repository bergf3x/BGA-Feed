/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Einstellungen zur Aktualisierung: Intervalle, manueller Abruf, Akku-Ausnahme, Autostart (Xiaomi). */
public class UpdateActivity extends Activity {
    private TextView battery, notify;
    private TextView stamp;
    private Bubble b;

    private int wid;
    private TextView newsNote, wxNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wid = getIntent() == null ? 0 : getIntent().getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        boolean fromWidget = wid > 0;
        boolean weatherOnly = fromWidget && WidgetProvider.isWeatherWidget(this, wid);
        b = new Bubble(this, 340, 12);
        b.header("Aktualisierung", b::close);
        LinearLayout body = b.scrollBox(fromWidget ? 0.62f : 0.5f);
        if (fromWidget) {
            // Intervall oder manuell: gilt fuer alle Widgets. Die Vollbild-App bleibt davon unberuehrt.
            if (!weatherOnly) {
                b.label(body, "Nachrichten holen");
                b.segment(body, MODE_NAMES, seg(Prefs.newsAuto(this), Prefs.newsIdx(this)), i -> {
                    Prefs.setNewsMode(this, i == 0, i == 0 ? -1 : i - 1);
                    changed(i != 0);
                    paintNotes();
                });
                newsNote = note(body);
            }
            b.label(body, "Wetter holen");
            b.segment(body, MODE_NAMES, seg(Prefs.wxAuto(this), Prefs.wxIdx(this)), i -> {
                Prefs.setWxMode(this, i == 0, i == 0 ? -1 : i - 1);
                changed(i != 0);
                paintNotes();
            });
            wxNote = note(body);
            paintNotes();
        } else {
            TextView t = b.label(body, "In der App aktualisierst du durch Herunterziehen der Liste. "
                    + "Intervalle oder „Manuell“ stellst du in den Einstellungen eines Widgets ein.");
            t.setPadding(b.px(4), b.px(10), b.px(4), b.px(4));
        }
        stamp = b.label(body, "");

        b.pillKeep("Jetzt aktualisieren", true, () -> {
            Toast.makeText(this, "Aktualisiere …", Toast.LENGTH_SHORT).show();
            Updater.runAsync(this, true, null);
        });
        battery = b.pillKeep("", false, this::askBattery);
        notify = b.pillKeep("", false, () -> Perms.askNotify(this));
        if (Perms.needsAutostart()) {
            b.pillKeep(Perms.autostartTitle() + " öffnen (" + Perms.vendorLabel() + ")", false, () -> {
                Prefs.setFlag(this, "perm_autostart_opened", true);
                Perms.openAutostart(this);
            });
        }
        b.pillKeep("Berechtigungs-Hinweis wieder anzeigen", false, () -> {
            Prefs.setFlag(this, "perm_card_hidden", false);
            Perms.setAutostartDone(this, false);
            Prefs.setFlag(this, "perm_autostart_opened", false);
            Toast.makeText(this, "Der Hinweis erscheint wieder oben in der App, solange etwas offen ist.", Toast.LENGTH_LONG).show();
        });
        b.pill("Schließen", false, true, () -> { });
    }

    private static final String[] MODE_NAMES = {"Manuell", "15 Min", "30 Min", "1 Std", "3 Std"};

    /** Auswahlfeld zu gespeichertem Stand: 0 = Manuell, sonst Intervall (6 und 12 Std zeigen "3 Std"). */
    private static int seg(boolean auto, int idx) {
        return auto ? Math.min(3, idx) + 1 : 0;
    }

    private void changed(boolean nowAuto) {
        Scheduler.schedule(this);
        if (nowAuto) Updater.runAsync(this, false, null);
    }

    /** Hinweisfeld unter einer Auswahl: Warnung (gelb) bei Automatik, Sparhinweis bei Manuell. */
    private TextView note(LinearLayout parent) {
        TextView t = new TextView(this);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f);
        t.setLineSpacing(0, 1.1f);
        t.setPadding(b.px(12), b.px(9), b.px(12), b.px(9));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = b.px(8);
        parent.addView(t, lp);
        return t;
    }

    private void styleNote(TextView t, boolean warn, String text) {
        int amber = b.light ? 0xFF8A5A00 : 0xFFE8C37A;
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(b.px(12));
        g.setColor(warn ? (b.light ? 0x26E0A400 : 0x24E8C37A) : (b.light ? 0x121B1F22 : 0x14FFFFFF));
        t.setBackground(g);
        t.setTextColor(warn ? amber : b.soft);
        t.setText(text);
    }

    private void paintNotes() {
        boolean notif = Prefs.kwNotify(this) || Prefs.warnNotify(this);
        if (newsNote != null) {
            if (Prefs.newsAuto(this)) {
                styleNote(newsNote, true, "⚠ Automatisch weckt das Handy regelmäßig auf und lädt alle Quellen neu. "
                        + "Das kostet Akku und Datenvolumen, je kürzer das Intervall, desto mehr. Manuell spart am meisten.");
            } else {
                styleNote(newsNote, false, "Manuell spart am meisten Akku und Daten: Das Widget lädt nur neu, wenn du auf „Aktualisieren“ tippst."
                        + (notif ? "\nBenachrichtigungen (Stichwörter, Unwetter) kommen nur bei Automatik." : ""));
            }
        }
        if (wxNote != null) {
            if (Prefs.wxAuto(this)) {
                styleNote(wxNote, true, "⚠ Automatisch kostet Akku und Daten. Das Wetter ändert sich langsam, "
                        + "meist reicht 1 Std.");
            } else {
                styleNote(wxNote, false, "Manuell: Das Wetter bleibt auf dem letzten Stand, bis du auf „Aktualisieren“ tippst.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = isIgnoring();
        battery.setText(ok ? "Akku-Optimierung: aufgehoben ✓" : "Akku-Optimierung für das Widget aufheben");
        notify.setText(Perms.notifyOk(this) ? "Benachrichtigungen: erlaubt ✓" : "Benachrichtigungen erlauben");
        stamp.setText("Letzter Abruf: Nachrichten " + time(Prefs.newsTime(this)) + "  ·  Wetter "
                + time(Prefs.weatherTime(this, 0)));
    }

    private String time(long t) {
        return t == 0 ? "–" : new java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(t));
    }

    private boolean isIgnoring() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void askBattery() {
        try {
            if (isIgnoring()) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } else {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            }
        } catch (Exception e) {
            appInfo();
        }
    }

    private void openAutostart() {
        try {
            Intent in = new Intent();
            in.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(in);
        } catch (Exception e) {
            appInfo();
            Toast.makeText(this, "Dort: Autostart erlauben, Akkusparer auf „Keine Einschränkungen“", Toast.LENGTH_LONG).show();
        }
    }

    private void appInfo() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }
}
