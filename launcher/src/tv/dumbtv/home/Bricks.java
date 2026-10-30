package tv.dumbtv.home;

import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * BRICK BOP: keep the ball up with the paddle and clear each picture made of bricks. Some bricks
 * take two hits, steel ones never break, and some drop capsules: W wide paddle, M three balls,
 * S slow ball, and a rare 1UP.
 */
final class Bricks extends Game {
    private static final int BW = 16, BH = 8, COLS = 18, LEFT = 16, TOP = 22;
    private static final int PADDLE_Y = 168, PADDLE_H = 4, BALL = 3;
    /** . empty, lower case one hit, upper case two hits, # steel. r o y g c b p w are colours. */
    private static final String[][] LEVELS = {
        { // smiley
            "......yyyyyy......",
            "....yyyyyyyyyy....",
            "...yyyBBByyBBByy..",
            "...yyyyyyyyyyyyy..",
            "...yyRyyyyyyyRyy..",
            "....yyRRRRRRRyy...",
            "......yyyyyyy.....",
        },
        { // heart
            "...rrrr....rrrr...",
            "..rrrrrr..rrrrrr..",
            "..rrRRrrrrrrRRrr..",
            "...rrrrrrrrrrrr...",
            "....rrrrrrrrrr....",
            "......rrrrrr......",
            "........rr........",
        },
        { // game controller
            "..pppppppppppppp..",
            ".ppwpppppppppcpp..",
            ".pwwwppppppprpcp..",
            ".ppwppp##ppppcpp..",
            ".pppppppppppppppp.",
            ".ppPPP......PPPpp.",
        },
        { // rocket
            "........ww........",
            ".......wwww.......",
            "......wwbbww......",
            "......wwbbww......",
            "......wwwwww......",
            ".....#wwwwww#.....",
            "....##wwwwww##....",
            "......OOyyOO......",
            ".......rOOr.......",
        },
        { // castle
            ".g.g.g......g.g.g.",
            ".ggggg......ggggg.",
            ".gGGGg.c.c..gGGGg.",
            ".ggggggccccggggg..",
            ".gggggg#cc#ggggg..",
            ".gggggg#..#ggggg..",
        },
        { // rainbow
            "....rrrrrrrrrr....",
            "...rooooooooooo...",
            "..royyyyyyyyyyor..",
            ".roygggggggggyor..",
            ".roygbbbbbbbbgyor.",
            "royGB........BGyor",
            "royg##......##gyor",
        },
    };

    private static final class Ball {
        float x, y, vx, vy;
        boolean stuck = true;
    }

    private static final class Drop {
        float x, y;
        char kind;
    }

    private final char[][] bricks = new char[12][COLS];
    private final List<Ball> balls = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private int level, hits, wideLeft, slowLeft, stuckFrames, clearFrames;
    private float px, pv, speed;

    Bricks(Arcade a) {
        super(a, "bricks");
    }

    @Override
    void reset() {
        level = 0;
        load();
    }

    @Override
    String hint() {
        return "LEVEL " + (level + 1);
    }

    private void load() {
        for (char[] row : bricks) java.util.Arrays.fill(row, '.');
        String[] map = LEVELS[level % LEVELS.length];
        for (int r = 0; r < map.length; r++) map[r].getChars(0, COLS, bricks[r], 0);
        speed = 1.7f + 0.2f * (level / LEVELS.length);
        hits = 0;
        clearFrames = 0;
        serve();
    }

    /** A fresh ball on the paddle, and no power-ups. */
    private void serve() {
        balls.clear();
        drops.clear();
        wideLeft = 0;
        slowLeft = 0;
        stuckFrames = 0;
        px = Pix.W / 2f - paddleW() / 2f;
        pv = 0;
        Ball b = new Ball();
        b.x = px + paddleW() / 2f - 1;
        b.y = PADDLE_Y - BALL;
        balls.add(b);
    }

    private int paddleW() {
        return wideLeft > 0 ? 48 : 32;
    }

    @Override
    void key(int key, boolean repeat) {
        if (key == Arcade.OK && !repeat) launch();
    }

    private void launch() {
        for (Ball b : balls) {
            if (!b.stuck) continue;
            b.stuck = false;
            double angle = Math.toRadians(rnd.nextBoolean() ? 25 : -25);
            b.vx = (float) Math.sin(angle) * ballSpeed();
            b.vy = (float) -Math.cos(angle) * ballSpeed();
        }
    }

    private float ballSpeed() {
        return speed * (slowLeft > 0 ? 0.7f : 1f);
    }

    @Override
    void tick() {
        if (clearFrames > 0) {
            if (++clearFrames > 90) {
                level++;
                load();
                ready();
            }
            return;
        }
        // Holding a direction speeds the paddle up over about 150 ms; a tap nudges it.
        int dir = (a.held(Arcade.RIGHT) ? 1 : 0) - (a.held(Arcade.LEFT) ? 1 : 0);
        if (dir == 0) pv = 0;
        else pv = Math.max(-4.5f, Math.min(4.5f, (Math.signum(pv) == dir ? pv : dir) + dir * 0.4f));
        px = Math.max(0, Math.min(Pix.W - paddleW(), px + pv));
        if (wideLeft > 0) wideLeft--;
        if (slowLeft > 0 && --slowLeft == 0) rescale();

        if (++stuckFrames > 180) launch();
        for (Iterator<Ball> it = balls.iterator(); it.hasNext(); ) {
            Ball b = it.next();
            if (b.stuck) {
                b.x = px + paddleW() / 2f - 1;
                b.y = PADDLE_Y - BALL;
                continue;
            }
            for (int s = 0; s < 4; s++) {
                if (move(b, b.vx / 4, 0)) b.vx = -b.vx;
                if (move(b, 0, b.vy / 4)) b.vy = -b.vy;
            }
            if (b.y > Pix.H) it.remove();
        }
        if (balls.isEmpty()) {
            a.sound(Sound.BOOM);
            if (--lives <= 0) {
                gameOver();
            } else {
                serve();
                ready();
            }
            return;
        }
        for (Iterator<Drop> it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            d.y += 0.8f;
            if (Pix.overlap(d.x, d.y, 12, 6, px, PADDLE_Y, paddleW(), PADDLE_H)) {
                power(d.kind);
                it.remove();
            } else if (d.y > Pix.H) {
                it.remove();
            }
        }
        if (cleared()) {
            clearFrames = 1;
            score += 500;
            a.sound(Sound.LEVEL);
        }
    }

    /** Moves the ball by one sub-step; true when it bounced off something on this axis. */
    private boolean move(Ball b, float dx, float dy) {
        b.x += dx;
        b.y += dy;
        if (b.x < 0 || b.x + BALL > Pix.W) {
            b.x = Math.max(0, Math.min(Pix.W - BALL, b.x));
            a.sound(Sound.BOUNCE);
            return true;
        }
        if (b.y < 12) {
            b.y = 12;
            a.sound(Sound.BOUNCE);
            return true;
        }
        if (dy > 0 && Pix.overlap(b.x, b.y, BALL, BALL, px, PADDLE_Y, paddleW(), PADDLE_H)) {
            paddleBounce(b);
            return false;
        }
        int c0 = (int) Math.floor((b.x - LEFT) / BW), c1 = (int) Math.floor((b.x + BALL - 1 - LEFT) / BW);
        int r0 = (int) Math.floor((b.y - TOP) / BH), r1 = (int) Math.floor((b.y + BALL - 1 - TOP) / BH);
        boolean hit = false;
        for (int r = Math.max(0, r0); r <= Math.min(bricks.length - 1, r1); r++) {
            for (int c = Math.max(0, c0); c <= Math.min(COLS - 1, c1); c++) {
                if (bricks[r][c] == '.') continue;
                hit = true;
                strike(r, c);
            }
        }
        if (hit) {
            b.x -= dx;
            b.y -= dy;
        }
        return hit;
    }

    /** Where the ball lands on the paddle sets its angle: five zones from steep left to steep right. */
    private void paddleBounce(Ball b) {
        float t = (b.x + BALL / 2f - (px + paddleW() / 2f)) / (paddleW() / 2f);
        int zone = Math.max(0, Math.min(4, Math.round((t + 1) * 2)));
        float[] angles = {-60, -35, b.vx < 0 ? -15 : 15, 35, 60};
        if (++hits % 8 == 0) speed = Math.min(3.4f, speed + 0.15f);
        double angle = Math.toRadians(angles[zone]);
        b.vx = (float) Math.sin(angle) * ballSpeed();
        b.vy = (float) -Math.cos(angle) * ballSpeed();
        b.y = PADDLE_Y - BALL;
        a.sound(Sound.BOUNCE);
    }

    private void strike(int r, int c) {
        char k = bricks[r][c];
        if (k == '#') {
            a.sound(Sound.BOUNCE);
            return;
        }
        if (Character.isUpperCase(k)) {
            bricks[r][c] = Character.toLowerCase(k);
            score += 10;
            a.sound(Sound.BOUNCE);
            return;
        }
        bricks[r][c] = '.';
        score += 10;
        a.sound(Sound.BRICK);
        if (rnd.nextInt(8) == 0) {
            Drop d = new Drop();
            d.x = LEFT + c * BW + 2;
            d.y = TOP + r * BH;
            int roll = rnd.nextInt(12);
            d.kind = roll < 4 ? 'W' : roll < 8 ? 'M' : roll < 11 ? 'S' : '1';
            drops.add(d);
        }
    }

    private void power(char kind) {
        a.sound(Sound.POWER);
        score += 50;
        switch (kind) {
            case 'W':
                wideLeft = 15 * 60;
                break;
            case 'S':
                slowLeft = 10 * 60;
                rescale();
                break;
            case '1':
                lives++;
                break;
            default:
                List<Ball> extra = new ArrayList<>();
                for (Ball b : balls) {
                    if (b.stuck || extra.size() >= 2) continue;
                    for (int side = -1; side <= 1; side += 2) {
                        Ball n = new Ball();
                        n.stuck = false;
                        n.x = b.x;
                        n.y = b.y;
                        n.vx = -b.vx + side * 0.6f;
                        n.vy = b.vy;
                        extra.add(n);
                    }
                }
                balls.addAll(extra);
                rescale();
        }
    }

    /** Puts every moving ball back on the current speed, keeping its direction. */
    private void rescale() {
        for (Ball b : balls) {
            float len = (float) Math.hypot(b.vx, b.vy);
            if (b.stuck || len == 0) continue;
            b.vx = b.vx / len * ballSpeed();
            b.vy = b.vy / len * ballSpeed();
        }
    }

    private boolean cleared() {
        for (char[] row : bricks) {
            for (char k : row) if (k != '.' && k != '#') return false;
        }
        return true;
    }

    private static int color(char k) {
        switch (Character.toLowerCase(k)) {
            case 'r': return Pix.RED;
            case 'o': return Pix.ORANGE;
            case 'y': return Pix.YELLOW;
            case 'g': return Pix.GREEN;
            case 'c': return Pix.CYAN;
            case 'b': return Pix.BLUE;
            case 'p': return Pix.PURPLE;
            case '#': return 0xFF9A9AA8;
            default: return Pix.WHITE;
        }
    }

    @Override
    void paint(Canvas c) {
        Pix.rect(c, 0, 12, Pix.W, Pix.H - 12, 0xFF14121F);
        for (int r = 0; r < bricks.length; r++) {
            for (int col = 0; col < COLS; col++) {
                char k = bricks[r][col];
                if (k == '.') continue;
                int x = LEFT + col * BW, y = TOP + r * BH;
                Pix.rect(c, x, y, BW - 1, BH - 1, color(k));
                Pix.rect(c, x, y, BW - 1, 1, 0x55FFFFFF);
                Pix.rect(c, x, y + BH - 2, BW - 1, 1, 0x55000000);
                if (Character.isUpperCase(k)) Pix.rect(c, x + 3, y + 3, BW - 7, 1, 0x88FFFFFF);
                if (k == '#') Pix.rect(c, x + 7, y + 2, 1, 3, 0xFF6A6A78);
            }
        }
        for (Drop d : drops) {
            int col = d.kind == 'W' ? Pix.BLUE : d.kind == 'M' ? Pix.PURPLE : d.kind == 'S' ? Pix.GREEN : Pix.RED;
            Pix.rect(c, d.x, d.y, 12, 6, col);
            Pix.rect(c, d.x + 1, d.y + 1, 10, 1, 0x66FFFFFF);
            Pix.centerAt(c, d.kind == '1' ? "+" : String.valueOf(d.kind), d.x + 6.5f, d.y - 1, Pix.WHITE);
        }
        int pw = paddleW();
        Pix.rect(c, px, PADDLE_Y, pw, PADDLE_H, Pix.WHITE);
        Pix.rect(c, px, PADDLE_Y, 3, PADDLE_H, Pix.RED);
        Pix.rect(c, px + pw - 3, PADDLE_Y, 3, PADDLE_H, Pix.RED);
        for (Ball b : balls) Pix.rect(c, b.x, b.y, BALL, BALL, Pix.YELLOW);
        if (clearFrames > 0) {
            Pix.window(c, 92, 74, 136, 26);
            Pix.center(c, "LEVEL CLEAR!", 83, Pix.YELLOW);
        } else if (state == PLAY && balls.size() == 1 && balls.get(0).stuck) {
            Pix.center(c, "OK TO LAUNCH", 140, Pix.GREY);
        }
    }
}
