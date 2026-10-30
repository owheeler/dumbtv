package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Drawing helpers for the Secret Arcade's 320×180 canvas: sprites written as text maps and baked
 * to bitmaps once, and the pixel font at its native 8 px size with antialiasing off.
 */
final class Pix {
    static final int W = 320, H = 180;
    static final int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF, GREY = 0xFF8A86A0, DARK = 0xFF2A2640,
            YELLOW = 0xFFF8D030, RED = 0xFFE84848, ORANGE = 0xFFF09030, GREEN = 0xFF58C858,
            CYAN = 0xFF48C8E8, BLUE = 0xFF4888F0, PURPLE = 0xFFC060E0, PINK = 0xFFF070B0;

    private static Paint text;
    private static final Paint fill = new Paint();

    private Pix() {}

    static void init(Context c) {
        if (text != null) return;
        text = new Paint();
        text.setAntiAlias(false);
        text.setTypeface(Ui.pixel(c));
        text.setTextSize(8);
    }

    /**
     * A sprite from rows of characters; each character is looked up in {@code palette} as
     * "cRRGGBB" pairs, e.g. {@code "#FFFFFF"} maps '#' to white. '.' and space are transparent.
     */
    static Bitmap sprite(String[] rows, String... palette) {
        int h = rows.length, w = rows[0].length();
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char ch = rows[y].charAt(x);
                for (String p : palette) {
                    if (p.charAt(0) == ch) px[y * w + x] = 0xFF000000 | (int) Long.parseLong(p.substring(1), 16);
                }
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    static void rect(Canvas c, float x, float y, float w, float h, int color) {
        fill.setColor(color);
        c.drawRect(Math.round(x), Math.round(y), Math.round(x) + w, Math.round(y) + h, fill);
    }

    static void blit(Canvas c, Bitmap b, float x, float y) {
        c.drawBitmap(b, Math.round(x), Math.round(y), null);
    }

    /** {@code y} is the top of the 8 px line. */
    static void text(Canvas c, String s, float x, float y, int color) {
        text.setColor(color);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(s, Math.round(x), Math.round(y) + 8, text);
    }

    static void center(Canvas c, String s, float y, int color) {
        centerAt(c, s, W / 2f, y, color);
    }

    static void centerAt(Canvas c, String s, float x, float y, int color) {
        text.setColor(color);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, Math.round(x), Math.round(y) + 8, text);
    }

    static void right(Canvas c, String s, float x, float y, int color) {
        text.setColor(color);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(s, Math.round(x), Math.round(y) + 8, text);
    }

    /** A dark box with a white border, like the launcher's dialog windows. */
    static void window(Canvas c, int x, int y, int w, int h) {
        rect(c, x, y, w, h, WHITE);
        rect(c, x + 1, y + 1, w - 2, h - 2, 0xFF1B2A6B);
    }

    static boolean overlap(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }
}
