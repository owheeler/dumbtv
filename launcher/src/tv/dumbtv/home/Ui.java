package tv.dumbtv.home;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** tvOS-style tokens: near-black page, white text at three strengths, white-fill focus. */
final class Ui {
    static final int BG = 0xFF0B0B0D;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xB8FFFFFF;
    static final int FAINT = 0x73FFFFFF;
    static final int SURFACE = 0xFF1C1C1F;
    /** Translucent dark panel for dialogs and the sleep warning. */
    static final int PANEL = 0xEB1C1C1F;
    /** Unfocused fill of text tiles and pills. */
    static final int GLASS = 0x33FFFFFF;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    /** A light spring: a quick rise with one small overshoot, like the tvOS focus lift. */
    static final TimeInterpolator SPRING = t -> {
        float x = t - 1f;
        return x * x * ((1.6f + 1f) * x + 1.6f) + 1f;
    };

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
}
