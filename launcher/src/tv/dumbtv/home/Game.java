package tv.dumbtv.home;

import android.graphics.Canvas;

import java.util.Random;

/**
 * What the three arcade games share: a GET READY pause, the score bar, lives, and the GAME OVER
 * card that records the best score. Subclasses fill in {@link #reset}, {@link #tick} and
 * {@link #paint}.
 */
abstract class Game implements Arcade.Scene {
    static final int READY = 0, PLAY = 1, OVER = 2;
    private static final int READY_FRAMES = 90;

    final Arcade a;
    final String id;
    final Random rnd = new Random();
    int state, frames, lives;
    long score;
    private boolean newBest;
    private int overFrames;

    Game(Arcade a, String id) {
        this.a = a;
        this.id = id;
    }

    final void restart() {
        score = 0;
        lives = 3;
        reset();
        ready();
    }

    /** Back to the GET READY banner, e.g. after a lost life. */
    final void ready() {
        state = READY;
        frames = 0;
    }

    final void gameOver() {
        state = OVER;
        overFrames = 0;
        newBest = Scores.submit(a.cfg, id, score);
        a.sound(newBest ? Sound.LEVEL : Sound.OVER);
    }

    /** New game from scratch. */
    abstract void reset();

    /** One 60 Hz step of play. */
    abstract void tick();

    /** The playfield, under the score bar and any banners. */
    abstract void paint(Canvas c);

    /** A key while playing; the default ignores it. */
    void key(int key, boolean repeat) {}

    /** Text for the GET READY banner's second line. */
    String hint() {
        return "";
    }

    @Override
    public final void update() {
        frames++;
        if (state == READY && frames >= READY_FRAMES) {
            state = PLAY;
            frames = 0;
        } else if (state == PLAY) {
            tick();
        } else if (state == OVER) {
            overFrames++;
        }
    }

    @Override
    public final boolean press(int key, boolean repeat) {
        if (state == OVER) {
            // A short grace period so a key still held from play doesn't skip the card.
            if (key == Arcade.OK && !repeat && overFrames > 40) {
                a.sound(Sound.SELECT);
                restart();
            }
            return true;
        }
        key(key, repeat);
        return true;
    }

    @Override
    public final void draw(Canvas c) {
        paint(c);
        Pix.rect(c, 0, 0, Pix.W, 12, 0xFF0E0C16);
        Pix.text(c, "SCORE " + score, 4, 2, Pix.WHITE);
        long best = Math.max(Scores.best(a.cfg, id), state == OVER ? 0 : score);
        Pix.centerAt(c, "HI " + best, 186, 2, Pix.YELLOW);
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < Math.min(lives, 5); i++) hearts.append('♥');
        Pix.right(c, hearts.toString(), Pix.W - 4, 2, Pix.RED);
        if (state == READY) {
            Pix.window(c, 92, 70, 136, hint().isEmpty() ? 26 : 40);
            Pix.center(c, "GET READY", 79, Pix.YELLOW);
            if (!hint().isEmpty()) Pix.center(c, hint(), 93, Pix.WHITE);
        } else if (state == OVER) {
            Pix.rect(c, 0, 12, Pix.W, Pix.H - 12, 0x88000000);
            Pix.window(c, 72, 56, 176, 76);
            Pix.center(c, "GAME OVER", 64, Pix.RED);
            Pix.center(c, "SCORE " + score, 82, Pix.WHITE);
            if (newBest && (overFrames / 15) % 2 == 0) Pix.center(c, "NEW HIGH SCORE!", 96, Pix.YELLOW);
            else if (!newBest) Pix.center(c, "BEST " + Scores.best(a.cfg, id), 96, Pix.GREY);
            if (overFrames > 40) Pix.center(c, "OK AGAIN  BACK MENU", 114, Pix.GREY);
        }
    }
}
