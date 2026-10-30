package tv.dumbtv.home;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFF2F2F3;
    static final int MUTED = 0x99FFFFFF;
    static final int FAINT = 0x66FFFFFF;
    static final int SURFACE = 0xFF1C1D21;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static final int BG = 0xFF1D1A2B;
    static final int SLOT = 0xFF0E0C16;
    static final int PLASTIC = 0xFF55535E;
    static final int PLASTIC_DARK = 0xFF3C3A44;
    static final int PLASTIC_LIGHT = 0xFF6A6873;
    static final int YELLOW = 0xFFF8D030;
    static final int WHITE = 0xFFFFFFFF;
    static final int GREY = 0xFF9A97AD;
    static final int GREEN = 0xFF5EE07A;
    static final int WINDOW = 0xFF1B2A6B;

    private static Typeface pixel;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Black on light accents, white on dark ones. */
    static int ink(int color) {
        return Color.luminance(color) > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Accent fill when focused, a faint fill otherwise. */
    static void focusStyle(TextView v, int accent, int idle, float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(accent, radius));
        bg.addState(new int[0], round(idle, radius));
        v.setBackground(bg);
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {ink(accent), 0xD9FFFFFF}));
    }

    static Typeface pixel(Context c) {
        if (pixel == null) {
            pixel = Typeface.createFromAsset(c.getApplicationContext().getAssets(), "fonts/PressStart2P-Regular.ttf");
        }
        return pixel;
    }

    /**
     * One "pixel" of the retro grid in screen pixels: 1/270 of the screen height, so a 1080p
     * screen draws everything at 4x and sizes stay whole multiples of the font's grid.
     */
    static int px(Context c) {
        int h = Math.min(c.getResources().getDisplayMetrics().heightPixels,
                c.getResources().getDisplayMetrics().widthPixels);
        return Math.max(1, Math.round(h / 270f));
    }

    /** Pixel-font text whose glyphs are {@code cells} grid pixels tall (the font is 8 cells). */
    static TextView pixelText(Context c, int cells, int color) {
        TextView v = new TextView(c);
        v.setTypeface(pixel(c));
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, cells * px(c));
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setLineSpacing(px(c) * 4f, 1f);
        return v;
    }

    /** Moves in {@code steps} hard jumps, like sprites in an 8-bit game. */
    static TimeInterpolator stepped(int steps) {
        return t -> t >= 1f ? 1f : (float) Math.ceil(t * steps) / steps;
    }

    /** Classic RPG dialog box: dark blue fill, white border with notched corners. */
    static final class RetroWindow extends Drawable {
        private final Paint fill = new Paint(), line = new Paint();
        private final int unit;

        RetroWindow(Context c) {
            unit = px(c);
            fill.setColor(WINDOW);
            line.setColor(WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int u = unit, l = b.left, t = b.top, r = b.right, bo = b.bottom;
            canvas.drawRect(l + u, t + u, r - u, bo - u, line);
            canvas.drawRect(l + 2 * u, t, r - 2 * u, bo, line);
            canvas.drawRect(l, t + 2 * u, r, bo - 2 * u, line);
            canvas.drawRect(l + 2 * u, t + 2 * u, r - 2 * u, bo - 2 * u, fill);
        }

        @Override
        public boolean getPadding(Rect padding) {
            int p = unit * 8;
            padding.set(p, p, p, p);
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            line.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            line.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A button face: thick yellow notched outline when focused, a thin plastic one when idle. */
    static final class RetroButton extends Drawable {
        private final Paint paint = new Paint();
        private final int unit;
        private boolean focused;

        RetroButton(Context c) {
            unit = px(c);
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean f = false;
            for (int s : state) if (s == android.R.attr.state_focused) f = true;
            if (f == focused) return false;
            focused = f;
            invalidateSelf();
            return true;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int u = unit;
            paint.setColor(focused ? YELLOW : PLASTIC);
            canvas.drawRect(b.left + u, b.top, b.right - u, b.bottom, paint);
            canvas.drawRect(b.left, b.top + u, b.right, b.bottom - u, paint);
            paint.setColor(SLOT);
            int i = focused ? u * 2 : u;
            canvas.drawRect(b.left + i, b.top + i, b.right - i, b.bottom - i, paint);
        }

        @Override
        public boolean getPadding(Rect padding) {
            padding.set(unit * 5, unit * 4, unit * 5, unit * 4);
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
