package tv.dumbtv.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;

/**
 * The Secret Arcade behind the egg: a menu of three small games drawn on a 320×180 canvas that is
 * scaled up with hard pixel edges. One {@link Scene} runs at a time on a fixed 60 Hz step.
 */
public final class Arcade extends Activity {
    /** Keys the games read; the remote's ring and a gamepad's buttons both map onto these. */
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3, OK = 4;

    private static Arcade current;

    private Screen screen;
    private Scene scene;
    private Menu menu;
    private final boolean[] held = new boolean[5];
    private boolean paused;
    private int pauseRow;
    Config cfg;

    interface Scene {
        void update();

        void draw(Canvas c);

        /** A key went down ({@code repeat} for the remote's auto-repeat); true when used. */
        boolean press(int key, boolean repeat);
    }

    /** Pauses a running game, e.g. for the sleep timer's one-minute warning. */
    static void interrupt() {
        Arcade a = current;
        if (a != null) a.pause();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        Pix.init(this);
        Sound.init(this);
        screen = new Screen(this);
        setContentView(screen);
        menu = new Menu(this);
        scene = menu;
    }

    @Override
    protected void onResume() {
        super.onResume();
        current = this;
        screen.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (current == this) current = null;
        pause();
        screen.stop();
        java.util.Arrays.fill(held, false);
    }

    boolean held(int key) {
        return held[key];
    }

    void play(Scene game) {
        if (game instanceof Game) ((Game) game).restart();
        paused = false;
        scene = game;
    }

    void toMenu() {
        paused = false;
        scene = menu;
    }

    void pause() {
        if (scene != menu && !paused) {
            paused = true;
            pauseRow = 0;
        }
    }

    void sound(int which) {
        Sound.play(this, which);
    }

    private static int map(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP:
                return UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return DOWN;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return OK;
            default:
                return -1;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B;
        if (back) {
            if (down && e.getRepeatCount() == 0) back();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MENU
                || code == KeyEvent.KEYCODE_BUTTON_START) {
            if (down && e.getRepeatCount() == 0) {
                if (paused) paused = false;
                else pause();
            }
            return true;
        }
        int key = map(code);
        if (key < 0) return super.dispatchKeyEvent(e);
        if (!down) {
            held[key] = false;
            return true;
        }
        held[key] = true;
        boolean repeat = e.getRepeatCount() > 0;
        if (paused) {
            if (repeat) return true;
            if (key == UP || key == DOWN) {
                pauseRow = 1 - pauseRow;
                sound(Sound.MOVE);
            } else if (key == OK) {
                sound(Sound.SELECT);
                if (pauseRow == 0) paused = false;
                else toMenu();
            }
            return true;
        }
        scene.press(key, repeat);
        return true;
    }

    private void back() {
        sound(Sound.BACK);
        if (scene == menu) finish();
        else if (paused || scene instanceof Game && ((Game) scene).state == Game.OVER) toMenu();
        else pause();
    }

    private void step() {
        if (!paused) scene.update();
    }

    private void render(Canvas c) {
        scene.draw(c);
        if (paused) {
            Pix.rect(c, 0, 0, Pix.W, Pix.H, 0x99000000);
            Pix.window(c, 104, 56, 112, 68);
            Pix.center(c, "PAUSED", 66, Pix.YELLOW);
            Pix.text(c, (pauseRow == 0 ? "▶ " : "  ") + "RESUME", 124, 86, pauseRow == 0 ? Pix.YELLOW : Pix.WHITE);
            Pix.text(c, (pauseRow == 1 ? "▶ " : "  ") + "QUIT", 124, 102, pauseRow == 1 ? Pix.YELLOW : Pix.WHITE);
        }
    }

    /** Fixed-step loop on the display's vsync; draws into a 320×180 bitmap, scaled with no filtering. */
    private final class Screen extends View implements Choreographer.FrameCallback {
        private static final long STEP_NS = 1_000_000_000L / 60;
        private final Bitmap frame = Bitmap.createBitmap(Pix.W, Pix.H, Bitmap.Config.ARGB_8888);
        private final Canvas canvas = new Canvas(frame);
        private final Rect src = new Rect(0, 0, Pix.W, Pix.H), dst = new Rect();
        private final Paint blit = new Paint();
        private long last;
        private long debt;
        private boolean running;

        Screen(Context c) {
            super(c);
            blit.setFilterBitmap(false);
            blit.setAntiAlias(false);
            setBackgroundColor(Pix.BLACK);
        }

        void start() {
            if (running) return;
            running = true;
            last = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void doFrame(long now) {
            if (!running) return;
            if (last == 0) {
                last = now;
                step();
            } else {
                debt += Math.min(now - last, STEP_NS * 5);
                last = now;
                // Within half a step of a whole frame counts as one, so a 60 Hz panel never stutters.
                while (debt >= STEP_NS / 2) {
                    step();
                    debt -= STEP_NS;
                }
            }
            frame.eraseColor(Pix.BLACK);
            render(canvas);
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int scale = Math.max(1, Math.min(w / Pix.W, h / Pix.H));
            int sw = Pix.W * scale, sh = Pix.H * scale;
            dst.set((w - sw) / 2, (h - sh) / 2, (w + sw) / 2, (h + sh) / 2);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawBitmap(frame, src, dst, blit);
        }
    }

    /** The cartridge picker shown when the egg hatches. */
    private static final class Menu implements Scene {
        private static final String[] NAMES = {"SNAKE BYTE", "BRICK BOP", "STAR SWARM"};
        private static final String[] IDS = {"snake", "bricks", "swarm"};
        private static final int[] COLORS = {Pix.GREEN, Pix.ORANGE, Pix.BLUE};
        private final Arcade a;
        private int pick, tick;

        Menu(Arcade a) {
            this.a = a;
        }

        @Override
        public void update() {
            tick++;
        }

        @Override
        public boolean press(int key, boolean repeat) {
            if (key == LEFT || key == RIGHT) {
                pick = (pick + (key == LEFT ? 2 : 1)) % 3;
                a.sound(Sound.MOVE);
            } else if (key == OK && !repeat) {
                a.sound(Sound.SELECT);
                a.play(pick == 0 ? new Snake(a) : pick == 1 ? new Bricks(a) : new Swarm(a));
            }
            return true;
        }

        @Override
        public void draw(Canvas c) {
            Pix.rect(c, 0, 0, Pix.W, Pix.H, 0xFF1D1A2B);
            for (int i = 0; i < 40; i++) {
                int x = (i * 73 + tick / 4) % Pix.W, y = (i * 37) % 60;
                Pix.rect(c, x, y, 1, 1, i % 3 == 0 ? Pix.GREY : Pix.DARK);
            }
            Pix.center(c, "SECRET ARCADE", 16, Pix.YELLOW);
            Pix.center(c, "YOU FOUND IT!", 30, Pix.GREY);
            for (int i = 0; i < 3; i++) {
                int x = 28 + i * 96, y = 52 - (i == pick ? 4 : 0);
                if (i == pick) Pix.rect(c, x - 2, y - 2, 76, 74, Pix.YELLOW);
                Pix.rect(c, x, y, 72, 70, 0xFF55535E);
                Pix.rect(c, x, y, 72, 4, 0xFF6A6873);
                for (int g = 0; g < 4; g++) Pix.rect(c, x + 8 + g * 16, y + 6, 8, 2, 0xFF3C3A44);
                Pix.rect(c, x + 6, y + 12, 60, 40, COLORS[i]);
                icon(c, i, x + 6, y + 12);
                for (int pin = 0; pin < 8; pin++) Pix.rect(c, x + 8 + pin * 8, y + 60, 4, 6, Pix.YELLOW);
                Pix.centerAt(c, NAMES[i], x + 36, 130, i == pick ? Pix.YELLOW : Pix.WHITE);
                long best = Scores.best(a.cfg, IDS[i]);
                Pix.centerAt(c, best > 0 ? "HI " + best : "HI ---", x + 36, 142, Pix.GREY);
            }
            if ((tick / 30) % 2 == 0) Pix.center(c, "◀ ▶ PICK   OK PLAY", 162, Pix.WHITE);
        }

        /** A tiny picture on each cartridge label. */
        private static void icon(Canvas c, int which, int x, int y) {
            int ink = 0xFF14121F;
            if (which == 0) {
                int[][] body = {{14, 22}, {20, 22}, {26, 22}, {26, 16}, {32, 16}, {38, 16}, {38, 22}};
                for (int[] p : body) Pix.rect(c, x + p[0], y + p[1], 5, 5, ink);
                Pix.rect(c, x + 44, y + 22, 5, 5, Pix.RED);
            } else if (which == 1) {
                for (int r = 0; r < 3; r++) {
                    for (int k = 0; k < 5; k++) Pix.rect(c, x + 6 + k * 10, y + 6 + r * 6, 9, 5, ink);
                }
                Pix.rect(c, x + 28, y + 26, 3, 3, Pix.WHITE);
                Pix.rect(c, x + 20, y + 34, 18, 3, ink);
            } else {
                Pix.rect(c, x + 27, y + 28, 6, 6, ink);
                Pix.rect(c, x + 24, y + 31, 12, 3, ink);
                Pix.rect(c, x + 29, y + 22, 2, 4, Pix.YELLOW);
                for (int k = 0; k < 4; k++) Pix.rect(c, x + 8 + k * 12, y + 6, 7, 5, ink);
            }
        }
    }
}
