package de.bergf3x.bgawidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.Map;

/** Glas-Hintergrund des Widgets: Fuellung mit Lichtreflex oben links und feinem hellen Rand. */
final class GlassBg {
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private GlassBg() {}

    static synchronized Bitmap get(int wPx, int hPx, int bgPct, boolean light, float density) {
        if (wPx < 40 || hPx < 40) return null;
        String key = wPx + "x" + hPx + "_" + bgPct + "_" + light;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        if (CACHE.size() > 4) CACHE.clear();
        try {
            b = render(wPx, hPx, bgPct, light, density);
        } catch (Throwable t) {
            return null;
        }
        CACHE.put(key, b);
        return b;
    }

    private static Bitmap render(int w, int h, int bgPct, boolean light, float density) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = 28 * density;
        float stroke = Math.max(1.2f, 1.2f * density);
        RectF rect = new RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(light ? 0xFFEEF1F6 : 0xFF1E1E26);
        fill.setAlpha(Prefs.alpha(bgPct));
        c.drawRoundRect(rect, r, r, fill);

        // Lichtreflex: von links oben ausklingend
        Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        int top = light ? 0x70FFFFFF : 0x2EFFFFFF;
        gloss.setShader(new LinearGradient(0, 0, w * .75f, h * .45f, top, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, gloss);

        // feiner Rand: hell oben links, dunkler unten rechts
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(stroke);
        int a = Math.round(Math.max(bgPct, 25) * 255f / 100f);
        int hi = ((int) (a * (light ? .9f : .7f)) << 24) | 0xFFFFFF;
        int lo = ((int) (a * (light ? .35f : .22f)) << 24) | (light ? 0x8890A0 : 0xFFFFFF);
        edge.setShader(new LinearGradient(0, 0, w, h, new int[]{hi, lo, lo},
                new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, edge);
        return bmp;
    }
}
