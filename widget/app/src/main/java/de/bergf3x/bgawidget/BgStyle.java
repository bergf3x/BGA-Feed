package de.bergf3x.bgawidget;

import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.content.Context;
import android.os.Build;

/** Hintergrundfarbe des Widgets: Standard (Grau), feste Farbe/Verlauf oder dynamisch aus dem Hintergrundbild. */
final class BgStyle {
    static final int STANDARD = 0, FIXED = 1, DYNAMIC = 2;
    static final int DARK_BASE = 0x1E1E26, LIGHT_BASE = 0xEEF1F6;

    private BgStyle() {}

    static int mode(Context c, int id) {
        int m = Prefs.sp(c).getInt("bgm_" + id, STANDARD);
        return m == FIXED || m == DYNAMIC ? m : STANDARD;
    }

    static int c1(Context c, int id) {
        return Prefs.sp(c).getInt("bgc1_" + id, 0x3B6EA5) & 0xFFFFFF;
    }

    /** Zweite Farbe fuer den Verlauf; -1 = keine. */
    static int c2(Context c, int id) {
        return Prefs.sp(c).getInt("bgc2_" + id, -1);
    }

    static int tint(Context c, int id) {
        return Math.max(0, Math.min(100, Prefs.sp(c).getInt("bgt_" + id, 55)));
    }

    static boolean sysCorner(Context c, int id) {
        return Prefs.sp(c).getBoolean("bgr_" + id, true);
    }

    static void set(Context c, int id, int mode, int c1, int c2, int tint, boolean sysCorner) {
        Prefs.sp(c).edit().putInt("bgm_" + id, mode).putInt("bgc1_" + id, c1).putInt("bgc2_" + id, c2)
                .putInt("bgt_" + id, tint).putBoolean("bgr_" + id, sysCorner).apply();
    }

    static void remove(Context c, int id) {
        Prefs.sp(c).edit().remove("bgm_" + id).remove("bgc1_" + id).remove("bgc2_" + id)
                .remove("bgt_" + id).remove("bgr_" + id).apply();
    }

    static int mix(int base, int color, int pct) {
        float f = pct / 100f;
        int r = Math.round(((base >> 16) & 0xFF) * (1 - f) + ((color >> 16) & 0xFF) * f);
        int g = Math.round(((base >> 8) & 0xFF) * (1 - f) + ((color >> 8) & 0xFF) * f);
        int b = Math.round((base & 0xFF) * (1 - f) + (color & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Hauptfarben des Hintergrundbilds (Haupt-, Zweit-, Drittfarbe); leer, wenn das System keine liefert. */
    static int[] wallpaper(Context c) {
        if (Build.VERSION.SDK_INT < 27) return new int[0];
        try {
            WallpaperManager wm = WallpaperManager.getInstance(c);
            WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return new int[0];
            int[] out = new int[3];
            int n = 0;
            out[n++] = wc.getPrimaryColor().toArgb() & 0xFFFFFF;
            if (wc.getSecondaryColor() != null) out[n++] = wc.getSecondaryColor().toArgb() & 0xFFFFFF;
            if (wc.getTertiaryColor() != null) out[n++] = wc.getTertiaryColor().toArgb() & 0xFFFFFF;
            return java.util.Arrays.copyOf(out, n);
        } catch (Throwable t) {
            return new int[0];
        }
    }

    /** Fuellfarben von links nach rechts (ohne Alpha); eine Farbe = einfarbig. */
    static int[] colors(Context c, int id, boolean light) {
        return colors(c, mode(c, id), c1(c, id), c2(c, id), tint(c, id), light);
    }

    static int[] colors(Context c, int mode, int c1, int c2, int tint, boolean light) {
        int base = light ? LIGHT_BASE : DARK_BASE;
        if (mode == FIXED) return c2 < 0 ? new int[]{c1} : new int[]{c1, c2 & 0xFFFFFF};
        if (mode == DYNAMIC) {
            int[] w = wallpaper(c);
            if (w.length > 0) {
                int[] out = new int[w.length];
                for (int i = 0; i < w.length; i++) out[i] = mix(base, w[i], tint);
                return out;
            }
        }
        return new int[]{base};
    }

    /** Eckenradius in dp: Systemwert fuer Widgets oder der eigene Standard. */
    static float radiusDp(Context c, int id) {
        if (mode(c, id) != DYNAMIC || !sysCorner(c, id) || Build.VERSION.SDK_INT < 31) return 28f;
        try {
            float px = c.getResources().getDimension(android.R.dimen.system_app_widget_background_radius);
            float dp = px / c.getResources().getDisplayMetrics().density;
            return dp >= 8 && dp <= 44 ? dp : 28f;
        } catch (Throwable t) {
            return 28f;
        }
    }
}
