package de.bergf3x.bgawidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Hintergrundbild der Wetterzeile: Watzmann-Foto, je nach Wetter und Tageszeit eingefaerbt, mit Sonne, Mond, Wolken, Regen usw. */
final class WeatherBg {
    /** Zeichenflaeche des aktuellen Laufs (Widget 720x130, App-Karte hoeher). */
    static int W = 720;
    static int H = 130;
    private static final java.util.Map<String, Bitmap> CACHE = new java.util.LinkedHashMap<>();

    /** Motiv je Ort (Reihenfolge wie Weather.PLACES). Fotos: Wikimedia Commons, Nachweise in Credits. */
    private static final int[] PLACE_BG = {
            R.drawable.bg_watzmann,        // Berchtesgaden
            R.drawable.bg_hintersee,       // Ramsau
            R.drawable.bg_watzmann,        // Bischofswiesen
            R.drawable.bg_koenigssee,      // Schoenau am Koenigssee
            R.drawable.bg_hochstaufen,     // Bad Reichenhall
            R.drawable.bg_hochstaufen,     // Bayerisch Gmain
            R.drawable.bg_hochstaufen,     // Piding
            R.drawable.bg_hochstaufen_sued, // Anger
            R.drawable.bg_thumsee,         // Schneizlreuth
            R.drawable.bg_marktschellenberg, // Marktschellenberg
            R.drawable.bg_untersberg,      // Ainring
            R.drawable.bg_untersberg,      // Freilassing
            R.drawable.bg_untersberg,      // Saaldorf-Surheim
            R.drawable.bg_teisendorf,      // Teisendorf
            R.drawable.bg_laufen,          // Laufen
            R.drawable.bg_koenigssee,      // Koenigssee
            R.drawable.bg_kehlstein,       // Obersalzberg
    };

    static int photo(int place) {
        return place >= 0 && place < PLACE_BG.length ? PLACE_BG[place] : R.drawable.bg_watzmann;
    }

    private WeatherBg() {}

    private static String category(int code, boolean day) {
        String c;
        if (code >= 95) c = "storm";
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) c = "snow";
        else if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) c = "rain";
        else if (code == 45 || code == 48) c = "fog";
        else if (code == 2 || code == 3) c = "cloudy";
        else c = "clear";
        return c + (day ? "_d" : "_n");
    }

    /** Widget-Streifen (720x130). */
    static Bitmap get(Context ctx, int code, boolean day, boolean light, int place) {
        return get(ctx, code, day, light, place, 720, 130);
    }

    /** Hintergrund in beliebigem Format (App-Karte z. B. 900x500); Foto des Orts, schwarzweiss und abgedunkelt. */
    static synchronized Bitmap get(Context ctx, int code, boolean day, boolean light, int place, int w, int h) {
        String key = category(code, day) + (light ? "L" : "");
        String ck = key + "|" + place + "|" + w + "x" + h;
        Bitmap hit = CACHE.get(ck);
        if (hit != null) return hit;
        Bitmap b = null;
        try {
            W = w;
            H = h;
            b = render(ctx, key, photo(place));
        } catch (Throwable t) {
            // zu wenig Speicher o. ae.: dann eben ohne Hintergrund
        }
        if (b != null) {
            if (CACHE.size() >= 4) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(ck, b);
        }
        return b;
    }

    private static Paint p() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        return p;
    }

    private static Bitmap render(Context ctx, String key0, int res) {
        boolean light = key0.endsWith("L");
        String key = light ? key0.substring(0, key0.length() - 1) : key0;
        boolean day = key.endsWith("_d");
        String cat = key.substring(0, key.indexOf('_'));
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap src = BitmapFactory.decodeResource(ctx.getResources(), res, op);
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, W, H), 42f * W / 720f, 42f * W / 720f, Path.Direction.CW);
        c.clipPath(clip);

        // Foto einfaerben: Saettigung, Farbfaktoren, Helligkeit
        float sat, r, g, b, br, alpha = 0.62f;
        boolean nightLook = !day;
        switch (cat) {
            case "cloudy": sat = .3f; r = .85f; g = .9f; b = .98f; br = .82f; break;
            case "rain": sat = .25f; r = .55f; g = .62f; b = .76f; br = .5f; break;
            case "snow": sat = .35f; r = .95f; g = 1f; b = 1.1f; br = 1.05f; break;
            case "storm": sat = .2f; r = .45f; g = .45f; b = .62f; br = .4f; break;
            case "fog": sat = .2f; r = .8f; g = .84f; b = .9f; br = .9f; break;
            default:
                if (day) { sat = 1.25f; r = 1.05f; g = 1.02f; b = .98f; br = 1.12f; }
                else { sat = .5f; r = .28f; g = .38f; b = .9f; br = .42f; alpha = .66f; }
        }
        if (nightLook && !cat.equals("clear")) { r *= .6f; g *= .62f; b *= .8f; br *= .8f; alpha = .66f; }
        // Schwarzweiss, dunkel: keine Farbe, Helligkeit je Wetter/Tageszeit, insgesamt gedaempft
        sat = 0f;
        float lum = (r + g + b) / 3f;
        r = g = b = lum;
        br *= 0.62f;
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(sat);
        cm.postConcat(new ColorMatrix(new float[]{
                r * br, 0, 0, 0, 0,
                0, g * br, 0, 0, 0,
                0, 0, b * br, 0, 0,
                0, 0, 0, 1, 0}));
        Paint ph = p();
        ph.setColorFilter(new ColorMatrixColorFilter(cm));
        // mittig zuschneiden statt verzerren
        float sr = src.getWidth() / (float) src.getHeight(), dr = W / (float) H;
        android.graphics.Rect crop;
        if (sr > dr) {
            int cw = Math.round(src.getHeight() * dr);
            int x0 = (src.getWidth() - cw) / 2;
            crop = new android.graphics.Rect(x0, 0, x0 + cw, src.getHeight());
        } else {
            int ch = Math.round(src.getWidth() / dr);
            int y0 = Math.max(0, Math.round((src.getHeight() - ch) * 0.4f));
            crop = new android.graphics.Rect(0, y0, src.getWidth(), y0 + ch);
        }
        c.drawBitmap(src, crop, new RectF(0, 0, W, H), ph);
        src.recycle();

        Random rnd = new Random(5);
        if (cat.equals("clear")) {
            if (day) sun(c, rnd);
            else night(c, rnd);
        } else if (cat.equals("cloudy")) {
            if (!day) stars(c, rnd, 30);
            clouds(c, rnd, day ? 0xFFD2D8E4 : 0xFF3A4660, 235, 7, 8, 30);
            clouds(c, rnd, day ? 0xFF96A0B2 : 0xFF2A3350, 200, 40, 5, 34);
        } else if (cat.equals("rain")) {
            clouds(c, rnd, 0xFF46506A, 245, 4, 9, 22);
            rain(c, rnd, 300);
        } else if (cat.equals("snow")) {
            clouds(c, rnd, day ? 0xFFE2E8F2 : 0xFF8794AC, 230, 6, 8, 26);
            snow(c, rnd, 170);
        } else if (cat.equals("storm")) {
            clouds(c, rnd, 0xFF32344A, 250, 4, 10, 20);
            rain(c, rnd, 220);
            lightning(c);
        } else if (cat.equals("fog")) {
            clouds(c, rnd, 0xFFE6EAF0, 220, 30, 9, 40);
            clouds(c, rnd, 0xFFE6EAF0, 200, 80, 7, 40);
        }

        // Weich ausblenden: links hinter dem Text fast weg, unten auslaufend; insgesamt dezent
        if (light) alpha *= 0.55f;
        Paint m = new Paint();
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        int a0 = Math.round(255 * alpha * .3f);
        int a1 = Math.round(255 * alpha);
        m.setShader(new LinearGradient(0, 0, W * .4f, 0, (a0 << 24) | 0xFFFFFF, (a1 << 24) | 0xFFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, m);
        Paint v = new Paint();
        v.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        v.setShader(new LinearGradient(0, H * .6f, 0, H, 0xFFFFFFFF, 0x66FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, H * .6f, W, H, v);
        return out;
    }

    private static void sun(Canvas c, Random rnd) {
        float cx = 137, cy = 26;
        Paint g = p();
        g.setShader(new RadialGradient(cx, cy, 190,
                new int[]{0xDDFFE6A0, 0x66FFD478, 0x00FFD478}, new float[]{0f, .35f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 190, g);
        Paint ray = p();
        ray.setColor(0x22FFEBAA);
        ray.setStrokeWidth(8);
        ray.setMaskFilter(new BlurMaskFilter(6, BlurMaskFilter.Blur.NORMAL));
        for (int k = 0; k < 14; k++) {
            double a = k * Math.PI / 7;
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * 170, cy + (float) Math.sin(a) * 170, ray);
        }
        Paint core = p();
        core.setColor(0xFFFFFAE1);
        c.drawCircle(cx, cy, 19, core);
    }

    private static void stars(Canvas c, Random rnd, int n) {
        Paint s = p();
        for (int i = 0; i < n; i++) {
            float x = rnd.nextFloat() * W, y = rnd.nextFloat() * H * .6f, r = 0.7f + rnd.nextFloat() * 1.5f;
            s.setColor(((150 + rnd.nextInt(105)) << 24) | 0xE6EBFF);
            c.drawCircle(x, y, r, s);
        }
    }

    private static void night(Canvas c, Random rnd) {
        stars(c, rnd, 90);
        float cx = 137, cy = 36;
        Paint g = p();
        g.setShader(new RadialGradient(cx, cy, 70, new int[]{0x66DCE4FF, 0x00DCE4FF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 70, g);
        Paint mo = p();
        mo.setColor(0xFFFAF7E6);
        c.drawCircle(cx, cy, 16, mo);
        Paint cut = p();
        cut.setColor(0xFF1E2A5A);
        c.drawCircle(cx + 11, cy - 5, 13, cut);
    }

    private static void clouds(Canvas c, Random rnd, int rgb, int alpha, float y0, int n, float blur) {
        Paint cp = p();
        cp.setColor((alpha << 24) | (rgb & 0xFFFFFF));
        cp.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        for (int i = 0; i < n; i++) {
            float cx = -30 + rnd.nextFloat() * (W + 60);
            float cy = y0 + rnd.nextFloat() * 30;
            float rx = 60 + rnd.nextFloat() * 110, ry = 14 + rnd.nextFloat() * 20;
            c.drawOval(new RectF(cx - rx, cy - ry, cx + rx, cy + ry), cp);
        }
    }

    private static void rain(Canvas c, Random rnd, int n) {
        Paint r = p();
        r.setStrokeWidth(1.5f);
        r.setColor(0x96D7E8FF);
        for (int i = 0; i < n; i++) {
            float x = rnd.nextFloat() * (W + 30), y = -10 + rnd.nextFloat() * H, l = 16 + rnd.nextFloat() * 14;
            c.drawLine(x, y, x - l * .32f, y + l, r);
        }
    }

    private static void snow(Canvas c, Random rnd, int n) {
        Paint s = p();
        s.setMaskFilter(new BlurMaskFilter(1.2f, BlurMaskFilter.Blur.NORMAL));
        for (int i = 0; i < n; i++) {
            float x = rnd.nextFloat() * W, y = rnd.nextFloat() * H, r = 1.4f + rnd.nextFloat() * 2.6f;
            s.setColor(((170 + rnd.nextInt(85)) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, r, s);
        }
    }

    private static void lightning(Canvas c) {
        Path bolt = new Path();
        bolt.moveTo(400, 0);
        bolt.lineTo(386, 36);
        bolt.lineTo(410, 36);
        bolt.lineTo(372, 95);
        Paint glow = p();
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(10);
        glow.setColor(0x8CDCC8FF);
        glow.setMaskFilter(new BlurMaskFilter(7, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(bolt, glow);
        Paint core = p();
        core.setStyle(Paint.Style.STROKE);
        core.setStrokeWidth(2.8f);
        core.setColor(0xFFFFFAC8);
        c.drawPath(bolt, core);
    }
}
