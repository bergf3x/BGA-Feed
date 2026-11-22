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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = new Bubble(this, 340, 12);
        b.header("Aktualisierung", b::close);
        LinearLayout body = b.scrollBox(0.5f);
        final int[] iv = {Prefs.wxIdx(this), Prefs.newsIdx(this)};
        b.label(body, "Wetter alle …");
        b.segment(body, Prefs.INTERVAL_NAMES, iv[0], i -> {
            iv[0] = i;
            Prefs.setIntervals(this, iv[0], iv[1]);
            Scheduler.schedule(this);
        });
        b.label(body, "Nachrichten alle …");
        b.segment(body, Prefs.INTERVAL_NAMES, iv[1], i -> {
            iv[1] = i;
            Prefs.setIntervals(this, iv[0], iv[1]);
            Scheduler.schedule(this);
        });
        stamp = b.label(body, "");
        TextView hint = b.label(body, "Kurze Intervalle verbrauchen mehr Akku. Im Ruhemodus des Handys ist der Takt nur ungefähr, "
                + "Android und Xiaomi dürfen ihn verschieben.");
        hint.setPadding(b.px(4), b.px(10), b.px(4), b.px(4));

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
