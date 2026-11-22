/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Locale;

/**
 * Berechtigungen und Systemeinstellungen, die das Widget fuer zuverlaessiges Arbeiten braucht.
 * Erkennt den Hersteller und springt direkt zur passenden Seite in dessen Einstellungen.
 */
final class Perms {
    private Perms() { }

    static final int REQ_NOTIFY = 71;

    /** Hersteller in Kleinbuchstaben (xiaomi, samsung, huawei, oppo …). */
    static String vendor() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) return "xiaomi";
        if (m.contains("samsung")) return "samsung";
        if (m.contains("huawei") || m.contains("honor")) return "huawei";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) return "oppo";
        if (m.contains("vivo") || m.contains("iqoo")) return "vivo";
        if (m.contains("asus")) return "asus";
        return "other";
    }

    /** Anzeigename von Hersteller und Oberflaeche, z. B. "Xiaomi HyperOS". */
    static String vendorLabel() {
        switch (vendor()) {
            case "xiaomi": return hyperOs() ? "Xiaomi HyperOS" : "Xiaomi MIUI";
            case "samsung": return "Samsung One UI";
            case "huawei": return "Huawei/Honor";
            case "oppo": return "Oppo/OnePlus/Realme";
            case "vivo": return "Vivo";
            case "asus": return "Asus";
            default: return Build.MANUFACTURER == null ? "Android" : cap(Build.MANUFACTURER);
        }
    }

    private static boolean hyperOs() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class).invoke(null, "ro.mi.os.version.name");
            return v != null && !v.isEmpty();
        } catch (Exception e) {
            return Build.VERSION.SDK_INT >= 34;
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- Benachrichtigungen ----

    static boolean notifyOk(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            return ((android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    /** Erst die Systemabfrage; wurde sie schon abgelehnt, direkt die Benachrichtigungs-Seite der App. */
    static void askNotify(Activity a) {
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
                && !Prefs.flag(a, "perm_notify_asked")) {
            Prefs.setFlag(a, "perm_notify_asked", true);
            a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
            return;
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
        } catch (Exception e) {
            appInfo(a);
        }
    }

    // ---- Akku-Optimierung ----

    static boolean batteryOk(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static void askBattery(Activity a) {
        try {
            if (batteryOk(a)) {
                a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } else {
                a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + a.getPackageName())));
            }
        } catch (Exception e) {
            appInfo(a);
        }
    }

    // ---- Autostart / Hintergrund (herstellerabhaengig, nicht auslesbar) ----

    /** Braucht dieses Handy eine eigene Autostart-Freigabe? Reines Android nicht. */
    static boolean needsAutostart() {
        return !"other".equals(vendor());
    }

    static String autostartTitle() {
        switch (vendor()) {
            case "samsung": return "Nie schlafende App";
            case "huawei": return "App-Start manuell";
            default: return "Autostart";
        }
    }

    static String autostartHint() {
        switch (vendor()) {
            case "xiaomi": return "Nach Neustart weiter aktualisieren";
            case "samsung": return "Akku → Nicht eingeschränkt";
            case "huawei": return "Alle drei Schalter einschalten";
            default: return "Im Hintergrund weiter aktualisieren";
        }
    }

    /** Vom Nutzer bestaetigt, da keine App den Autostart-Schalter auslesen kann. */
    static boolean autostartDone(Context c) {
        return !needsAutostart() || Prefs.flag(c, "perm_autostart_done");
    }

    static void setAutostartDone(Context c, boolean done) {
        Prefs.setFlag(c, "perm_autostart_done", done);
    }

    static void openAutostart(Activity a) {
        String[][] targets;
        switch (vendor()) {
            case "xiaomi":
                targets = new String[][]{
                        {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                        {"com.miui.securitycenter", "com.miui.powercenter.PowerSettings"}};
                break;
            case "samsung":
                targets = new String[0][];
                break;
            case "huawei":
                targets = new String[][]{
                        {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                        {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                        {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"}};
                break;
            case "oppo":
                targets = new String[][]{
                        {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                        {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                        {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
                        {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"}};
                break;
            case "vivo":
                targets = new String[][]{
                        {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                        {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"}};
                break;
            case "asus":
                targets = new String[][]{
                        {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"}};
                break;
            default:
                targets = new String[0][];
        }
        for (String[] t : targets) {
            try {
                Intent in = new Intent().setComponent(new ComponentName(t[0], t[1]))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (a.getPackageManager().resolveActivity(in, 0) == null) continue;
                a.startActivity(in);
                Toast.makeText(a, "„BGL" + (a.getPackageName().endsWith(".test") ? " Test" : " Wetter & News")
                        + "“ suchen und einschalten", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) {
            }
        }
        // Samsung und alle anderen: App-Info, dort Akku -> Nicht eingeschraenkt
        appInfo(a);
        Toast.makeText(a, "Dort: Akku → „Nicht eingeschränkt“ bzw. Autostart erlauben", Toast.LENGTH_LONG).show();
    }

    static void appInfo(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
        } catch (Exception ignored) {
        }
    }

    /** Anzahl offener Punkte (0 = alles eingerichtet). */
    static int open(Context c) {
        int n = 0;
        if (!notifyOk(c)) n++;
        if (!batteryOk(c)) n++;
        if (!autostartDone(c)) n++;
        return n;
    }
}
