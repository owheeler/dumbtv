package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** SNAKE BYTE: steer with the arrows, eat berries, grow, and don't hit a wall or yourself. */
final class Snake extends Game {
    private static final int CELL = 8, COLS = 40, ROWS = 21, TOP = 12;
    private static final int[] DX = {-1, 1, 0, 0}, DY = {0, 0, -1, 1};
    private static final Bitmap BERRY = Pix.sprite(new String[] {
        "...g...",
        "..g....",
        ".rrrrr.",
        "rrwrrrr",
        "rrrrrrr",
        "rrrrrrr",
        ".rrrrr.",
    }, "gFF58C858", "rFFE84848", "wFFFFFFFF");
    private static final Bitmap STAR = Pix.sprite(new String[] {
        "...y...",
        "...y...",
        "yyyyyyy",
        ".yyyyy.",
        "..yyy..",
        ".yy.yy.",
        "y.....y",
    }, "yFFF8D030");

    private final List<Integer> body = new ArrayList<>();
    private final ArrayDeque<Integer> turns = new ArrayDeque<>();
    private int dir, grow, food, star, starLeft, eaten, dying;
    private float speed, acc;

    Snake(Arcade a) {
        super(a, "snake");
    }

    @Override
    void reset() {
        lives = 0;
        body.clear();
        for (int i = 0; i < 4; i++) body.add(10 * COLS + 9 - i);
        turns.clear();
        dir = Arcade.RIGHT;
        grow = 0;
        eaten = 0;
        star = -1;
        dying = 0;
        speed = 7;
        acc = 0;
        food = free();
    }

    @Override
    String hint() {
        return "ARROWS STEER";
    }

    @Override
    void key(int key, boolean repeat) {
        if (key > Arcade.DOWN || turns.size() >= 2) return;
        int last = turns.isEmpty() ? dir : turns.peekLast();
        // The same way, or straight back into your own neck, does nothing.
        if (key == last || (key ^ 1) == last) return;
        turns.add(key);
    }

    @Override
    void tick() {
        if (dying > 0) {
            dying++;
            if (dying > 30 && dying % 3 == 0) {
                if (!body.isEmpty()) body.remove(body.size() - 1);
                if (body.isEmpty()) gameOver();
            }
            return;
        }
        if (star >= 0 && --starLeft <= 0) star = -1;
        acc += speed / 60f;
        while (acc >= 1 && dying == 0) {
            acc -= 1;
            move();
        }
    }

    private void move() {
        if (!turns.isEmpty()) dir = turns.poll();
        int head = body.get(0);
        int x = head % COLS + DX[dir], y = head / COLS + DY[dir];
        int next = y * COLS + x;
        // The tail moves out of the way this step unless the snake is growing.
        boolean bites = body.subList(0, grow > 0 ? body.size() : body.size() - 1).contains(next);
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS || bites) {
            dying = 1;
            a.sound(Sound.BOOM);
            return;
        }
        body.add(0, next);
        if (next == food) {
            score += 10;
            grow++;
            eaten++;
            speed = Math.min(15, speed + 0.25f);
            a.sound(Sound.EAT);
            food = free();
            if (eaten % 8 == 0) {
                star = free();
                starLeft = 300;
            }
        } else if (next == star) {
            score += 50;
            grow += 3;
            star = -1;
            a.sound(Sound.POWER);
        }
        if (grow > 0) grow--;
        else body.remove(body.size() - 1);
    }

    private int free() {
        int cell;
        do {
            cell = rnd.nextInt(COLS * ROWS);
        } while (body.contains(cell) || cell == food || cell == star);
        return cell;
    }

    @Override
    void paint(Canvas c) {
        boolean flash = dying > 0 && dying < 24 && (dying / 4) % 2 == 0;
        Pix.rect(c, 0, TOP, Pix.W, Pix.H - TOP, flash ? 0xFF4A1820 : 0xFF14121F);
        for (int y = 0; y < ROWS; y += 2) {
            for (int x = 0; x < COLS; x += 2) Pix.rect(c, x * CELL + 3, TOP + y * CELL + 3, 1, 1, 0xFF221F33);
        }
        Pix.blit(c, BERRY, food % COLS * CELL, TOP + food / COLS * CELL);
        if (star >= 0 && (starLeft > 90 || (starLeft / 6) % 2 == 0)) {
            Pix.blit(c, STAR, star % COLS * CELL, TOP + star / COLS * CELL);
        }
        for (int i = body.size() - 1; i >= 0; i--) {
            int cell = body.get(i);
            int x = cell % COLS * CELL, y = TOP + cell / COLS * CELL;
            boolean head = i == 0 && dying == 0;
            Pix.rect(c, x, y, 7, 7, head ? 0xFFB8F070 : (i % 2 == 0 ? Pix.GREEN : 0xFF48B048));
            if (head) {
                int ex = DX[dir], ey = DY[dir];
                // two eyes, on the side facing the way it's going
                int cx = x + 3 + ex * 2, cy = y + 3 + ey * 2;
                Pix.rect(c, cx - Math.abs(ey) * 2, cy - Math.abs(ex) * 2, 1, 1, Pix.BLACK);
                Pix.rect(c, cx + Math.abs(ey) * 2, cy + Math.abs(ex) * 2, 1, 1, Pix.BLACK);
            }
        }
    }
}
