package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/**
 * The easter egg in the bottom-left corner: a small speckled pixel egg that looks like part of
 * the background until it has focus. Three presses of OK crack it open into the Secret Arcade.
 * Cracks heal if the egg is left alone for a while.
 */
final class Egg extends View {
    private static final String[] SHAPE = {
        "...##...",
        "..####..",
        ".######.",
        ".##o###.",
        "########",
        "#####o##",
        "#o######",
        "########",
        ".###o##.",
        "..####..",
    };
    /** Crack lines per stage, as cells (column, row) drawn in the page colour. */
    private static final int[][][] CRACKS = {
        {},
        {{2, 4}, {3, 5}, {4, 4}},
        {{0, 5}, {1, 4}, {2, 5}, {3, 4}, {4, 5}, {5, 4}, {6, 5}, {7, 4}},
    };
    private static final int COLS = 8, ROWS = 10;
    private static final int SHELL = 0xFFF4E8C8, SPOT = 0xFF5EC8A0;
    private static final long HEAL_MS = 10_000, WOBBLE_MS = 1500;

    private final Paint paint = new Paint();
    private final int cell;
    private final Runnable hatched;
    private int cracks, frame = -1;
    private long lastCrack;

    private final Runnable wobble = new Runnable() {
        @Override
        public void run() {
            if (!isFocused() || frame >= 0) return;
            animate().rotation(-12f).setDuration(90).setInterpolator(Ui.stepped(2)).withEndAction(() ->
                    animate().rotation(12f).setDuration(120).withEndAction(() ->
                            animate().rotation(0f).setDuration(90).start()).start()).start();
            postDelayed(this, WOBBLE_MS);
        }
    };

    private final Runnable hatch = new Runnable() {
        @Override
        public void run() {
            frame++;
            invalidate();
            if (frame < 6) {
                postDelayed(this, 70);
            } else {
                frame = -1;
                cracks = 0;
                invalidate();
                hatched.run();
            }
        }
    };

    Egg(Context c, Runnable hatched) {
        super(c);
        this.hatched = hatched;
        cell = Ui.px(c);
        setFocusable(true);
        setClickable(true);
        setAlpha(0.45f);
        setContentDescription("Egg");
        setOnClickListener(v -> crack());
    }

    @Override
    protected void onMeasure(int w, int h) {
        // Extra room above for the shell halves flying off when it hatches.
        setMeasuredDimension(COLS * cell, (ROWS + 4) * cell);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        setAlpha(focused ? 1f : 0.45f);
        removeCallbacks(wobble);
        if (focused) {
            if (SystemClock.uptimeMillis() - lastCrack > HEAL_MS) cracks = 0;
            postDelayed(wobble, 400);
        } else {
            setRotation(0);
        }
        invalidate();
    }

    private void crack() {
        if (frame >= 0) return;
        lastCrack = SystemClock.uptimeMillis();
        cracks++;
        if (cracks >= 3) {
            Sound.play(getContext(), Sound.HATCH);
            frame = 0;
            post(hatch);
        } else {
            Sound.play(getContext(), Sound.CRACK);
            animate().translationX(cell).setDuration(40).withEndAction(() ->
                    animate().translationX(-cell).setDuration(40).withEndAction(() ->
                            animate().translationX(0).setDuration(40).start()).start()).start();
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(wobble);
        removeCallbacks(hatch);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean lit = isFocused() || frame >= 0;
        int shell = lit ? SHELL : Ui.PLASTIC, spot = lit ? SPOT : Ui.PLASTIC_DARK;
        int top = 4 * cell;
        int open = frame < 0 ? 0 : frame;
        for (int r = 0; r < ROWS; r++) {
            // While hatching, the top half lifts away a cell per frame and the halves part.
            boolean upper = r < 5;
            int dy = frame >= 0 && upper ? -open : 0;
            int dx = frame >= 0 ? (upper ? -open / 2 : open / 3) : 0;
            for (int c = 0; c < COLS; c++) {
                char ch = SHAPE[r].charAt(c);
                if (ch == '.') continue;
                paint.setColor(ch == 'o' ? spot : shell);
                int x = (c + dx) * cell, y = top + (r + dy) * cell;
                canvas.drawRect(x, y, x + cell, y + cell, paint);
            }
        }
        int stage = Math.min(cracks, CRACKS.length - 1);
        if (frame < 0) {
            paint.setColor(Ui.BG);
            for (int[] p : CRACKS[stage]) {
                canvas.drawRect(p[0] * cell, top + p[1] * cell, (p[0] + 1) * cell, top + (p[1] + 1) * cell, paint);
            }
        } else if (frame >= 2) {
            // sparkles in the gap
            paint.setColor(Ui.YELLOW);
            int y = top + 4 * cell;
            canvas.drawRect(3 * cell, y - frame * cell, 4 * cell, y - (frame - 1) * cell, paint);
            canvas.drawRect(5 * cell, y - (frame - 1) * cell, 6 * cell, y - (frame - 2) * cell, paint);
            canvas.drawRect(1 * cell, y - (frame - 2) * cell, 2 * cell, y - (frame - 3) * cell, paint);
        }
    }
}
