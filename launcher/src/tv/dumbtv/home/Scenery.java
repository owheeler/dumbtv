package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Built-in top-shelf backdrops for a TV with no photos yet: soft layered mountain ranges under a
 * graded sky, painted at run time so the APK carries no image files. Each scene is seeded, so
 * scene {@code n} looks the same every time.
 */
final class Scenery {
    /** sky top, sky horizon, sun or moon, far ridge, near ridge, stars (1) or not (0). */
    private static final int[][] SCENES = {
        {0xFF2B3A67, 0xFFF6A97A, 0xFFFFE3B0, 0xFF9C8199, 0xFF231E33, 0},
        {0xFF3E7FC0, 0xFFC4E4F2, 0xFFFFFFFF, 0xFF86A9C2, 0xFF1C3836, 0},
        {0xFF3A1C4D, 0xFFF27A54, 0xFFFFC27A, 0xFF9B4F6B, 0xFF190F22, 0},
        {0xFF040A1C, 0xFF223F69, 0xFFE8EEF8, 0xFF2E4468, 0xFF060A14, 1},
        {0xFF03161F, 0xFF2A7E7A, 0xFFDDF5EE, 0xFF1F5A5D, 0xFF031018, 1},
        {0xFF6487C4, 0xFFF6D29C, 0xFFFFF3D6, 0xFFC4906E, 0xFF45281F, 0},
    };

    private Scenery() {}

    static int count() {
        return SCENES.length;
    }

    static Bitmap paint(int n, int w, int h) {
        int[] s = SCENES[n % SCENES.length];
        Random rnd = new Random(7919L * (n + 1));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float horizon = h * (0.52f + rnd.nextFloat() * 0.08f);
        p.setShader(new LinearGradient(0, 0, 0, horizon, s[0], s[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (s[5] == 1) {
            for (int i = 0; i < 260; i++) {
                float x = rnd.nextFloat() * w, y = rnd.nextFloat() * horizon * 0.9f;
                p.setColor(0xFFFFFFFF);
                p.setAlpha(40 + rnd.nextInt(160));
                c.drawCircle(x, y, 0.6f + rnd.nextFloat() * h / 900f, p);
            }
            p.setAlpha(255);
        }

        float sunX = w * (0.18f + rnd.nextFloat() * 0.64f);
        float sunY = horizon * (0.42f + rnd.nextFloat() * 0.3f);
        float sunR = h * 0.05f;
        p.setShader(new RadialGradient(sunX, sunY, sunR * 7, (s[2] & 0x00FFFFFF) | 0x55000000,
                s[2] & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(sunX, sunY, sunR * 7, p);
        p.setShader(null);
        p.setColor(s[2]);
        c.drawCircle(sunX, sunY, sunR, p);

        int ridges = 5;
        Path path = new Path();
        for (int r = 0; r < ridges; r++) {
            float t = r / (float) (ridges - 1);
            float base = horizon + (h - horizon) * (0.02f + t * 0.62f);
            float amp = h * (0.05f + 0.07f * (1 - t) + rnd.nextFloat() * 0.03f);
            float f1 = 1.5f + rnd.nextFloat() * 2f, f2 = 4f + rnd.nextFloat() * 4f, f3 = 11f + rnd.nextFloat() * 8f;
            float p1 = rnd.nextFloat() * 6.28f, p2 = rnd.nextFloat() * 6.28f, p3 = rnd.nextFloat() * 6.28f;
            path.reset();
            path.moveTo(0, h);
            int steps = 160;
            for (int i = 0; i <= steps; i++) {
                float x = w * i / (float) steps, u = i / (float) steps * 6.2832f;
                double y = Math.sin(u * f1 / 2 + p1) * 0.6 + Math.sin(u * f2 / 2 + p2) * 0.28
                        + Math.sin(u * f3 / 2 + p3) * 0.12;
                path.lineTo(x, base - (float) (y * amp) - amp * 0.4f);
            }
            path.lineTo(w, h);
            path.close();
            int top = lerp(lerp(s[1], s[3], 0.55f + 0.45f * t), s[4], t);
            int bottom = lerp(top, s[4], 0.5f + 0.5f * t);
            p.setShader(new LinearGradient(0, base - amp * 1.4f, 0, h, top, bottom, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            // haze between layers, thicker in the distance
            p.setShader(new LinearGradient(0, base - amp, 0, base + h * 0.12f,
                    (s[1] & 0x00FFFFFF) | ((int) (60 * (1 - t)) << 24), s[1] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, base - amp, w, base + h * 0.12f, p);
            p.setShader(null);
        }
        return b;
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8
                | Math.round(ab + (bb - ab) * t);
    }

    /** A tiny, heavily blurred and darkened copy, for the backdrop behind the app grid. */
    static Bitmap blur(Bitmap src) {
        int w = Math.max(8, src.getWidth() / 16), h = Math.max(8, src.getHeight() / 16);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h], tmp = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            box(px, tmp, w, h, 2, true);
            box(tmp, px, w, h, 2, false);
        }
        for (int i = 0; i < px.length; i++) px[i] = lerp(px[i], 0xFF000000, 0.5f);
        Bitmap out = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int sr = 0, sg = 0, sb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int v = in[horizontal ? line * w + j : j * w + line];
                    sr += (v >> 16) & 255;
                    sg += (v >> 8) & 255;
                    sb += v & 255;
                    n++;
                }
                out[horizontal ? line * w + i : i * w + line] = 0xFF000000 | (sr / n) << 16 | (sg / n) << 8 | sb / n;
            }
        }
    }
}
