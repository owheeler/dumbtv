package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * STAR SWARM: the ship fires on its own; move left and right. Aliens swoop in along curved paths,
 * settle into a swaying formation, then break off in ones, twos and threes to dive and shoot.
 * A UFO sometimes crosses the top (twin shot or a shield), and every fourth wave is a meteor
 * shower for bonus points.
 */
final class Swarm extends Game {
    private static final int SHIP_Y = 164, SHIP_W = 9, COLS = 8;
    private static final int BLIP = 0, ZAPPER = 1, RINGER = 2, BOSS = 3;
    private static final int[] POINTS = {50, 80, 100, 150};
    private static final int WAIT = 0, ENTER = 1, HOME = 2, IN = 3, DIVE = 4, DEAD = 5;

    private static final Bitmap SHIP = Pix.sprite(new String[] {
        "....w....",
        "...www...",
        "...wbw...",
        "..wwwww..",
        ".wwwwwww.",
        "wwrwwwrww",
        "w..w.w..w",
        "...y.y...",
    }, "wFFF0F0F0", "bFF4888F0", "rFFE84848", "yFFF8D030");
    private static final Bitmap[][] ENEMY = {
        {
            Pix.sprite(new String[] {".ggggg.", "gwgggwg", "ggggggg", ".g.g.g.", "g.....g"}, "gFF58C858", "wFF14121F"),
            Pix.sprite(new String[] {".ggggg.", "gwgggwg", "ggggggg", ".g.g.g.", ".g...g."}, "gFF58C858", "wFF14121F"),
        },
        {
            Pix.sprite(new String[] {"o.......o", ".o.ooo.o.", "..ooooo..", ".oowowoo.", "ooooooooo", "o.o...o.o", "..o...o.."},
                    "oFFF09030", "wFF14121F"),
            Pix.sprite(new String[] {".o.....o.", "o..ooo..o", "..ooooo..", ".oowowoo.", "ooooooooo", ".o.o.o.o.", "o.......o"},
                    "oFFF09030", "wFF14121F"),
        },
        {
            Pix.sprite(new String[] {"...ccc...", "..cwcwc..", "ccccccccc", "c.ppppp.c", "..c.c.c.."},
                    "cFF48C8E8", "wFFFFFFFF", "pFF4888F0"),
            Pix.sprite(new String[] {"...ccc...", "..cwcwc..", "ccccccccc", "c.ppppp.c", ".c.c.c.c."},
                    "cFF48C8E8", "wFFFFFFFF", "pFF4888F0"),
        },
        {
            Pix.sprite(new String[] {"...p...p...", "..ppp.ppp..", ".ppwpppwpp.", "ppppppppppp", "pp.ppppp.pp",
                    "p..p...p..p", "...pp.pp..."}, "pFFC060E0", "wFFF8D030"),
            Pix.sprite(new String[] {"...p...p...", "..ppp.ppp..", ".ppwpppwpp.", "ppppppppppp", "pp.ppppp.pp",
                    ".p.p...p.p.", "...pp.pp..."}, "pFFC060E0", "wFFF8D030"),
        },
    };
    private static final Bitmap BOSS_HURT = Pix.sprite(new String[] {"...p...p...", "..ppp.ppp..",
            ".ppwpppwpp.", "ppppppppppp", "pp.ppppp.pp", "p..p...p..p", "...pp.pp..."}, "pFFF070B0", "wFFF8D030");
    private static final Bitmap UFO = Pix.sprite(new String[] {
        "....rrrrr....",
        "..rrwrwrwrr..",
        "rrrrrrrrrrrrr",
        "..r.r...r.r..",
    }, "rFFE84848", "wFFFFFFFF");
    private static final Bitmap ROCK = Pix.sprite(new String[] {
        ".gggg.", "ggdggg", "gggggd", "gdgggg", "ggggdg", ".gggg.",
    }, "gFF8A86A0", "dFF5A5670");

    private static final class Enemy {
        int type, row, col, hp, state, delay;
        float x, y, t, speed;
        final float[] bx = new float[4], by = new float[4];
        boolean zig;
        int shots;
    }

    private static final class Shot {
        float x, y, vx, vy;
    }

    private static final class Spark {
        float x, y, vx, vy;
        int life, color;
    }

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Shot> mine = new ArrayList<>(), theirs = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final float[] starX = new float[60], starY = new float[60];
    private int wave, tick, cooldown, diveTimer, deadFrames, invuln, twinLeft, bannerFrames;
    private int rocksSpawned, rocksHit;
    private boolean shield, bonusStage;
    private long nextLife;
    private float shipX;
    private Enemy ufo;
    private String banner = "";

    Swarm(Arcade a) {
        super(a, "swarm");
        for (int i = 0; i < starX.length; i++) {
            starX[i] = rnd.nextInt(Pix.W);
            starY[i] = rnd.nextInt(Pix.H);
        }
    }

    @Override
    void reset() {
        wave = 0;
        nextLife = 20_000;
        shield = false;
        twinLeft = 0;
        nextWave();
    }

    @Override
    String hint() {
        return bonusStage ? "BONUS STAGE" : "WAVE " + wave;
    }

    private void nextWave() {
        wave++;
        bonusStage = wave % 4 == 0;
        enemies.clear();
        mine.clear();
        theirs.clear();
        ufo = null;
        shipX = Pix.W / 2f - SHIP_W / 2f;
        deadFrames = 0;
        invuln = 0;
        bannerFrames = 0;
        rocksSpawned = 0;
        rocksHit = 0;
        diveTimer = 150;
        if (!bonusStage) {
            int[][] groups = {{3, BLIP, 0}, {2, ZAPPER, 1}, {1, RINGER, 0}, {0, BOSS, 1}};
            for (int g = 0; g < groups.length; g++) {
                int row = groups[g][0], type = groups[g][1];
                boolean fromRight = groups[g][2] == 1;
                int n = 0;
                for (int col = 0; col < COLS; col++) {
                    if (type == BOSS && (col < 2 || col > 5)) continue;
                    Enemy e = new Enemy();
                    e.type = type;
                    e.row = row;
                    e.col = col;
                    e.hp = type == BOSS ? 2 : 1;
                    e.state = WAIT;
                    e.delay = g * 100 + n++ * 9;
                    entryPath(e, fromRight);
                    enemies.add(e);
                }
            }
        }
        ready();
    }

    /** A swoop in from low on one side, looping up through the middle of the screen. */
    private void entryPath(Enemy e, boolean fromRight) {
        float[] xs = {-12, 120, 230, 160}, ys = {150, 40, 150, 70};
        for (int i = 0; i < 4; i++) {
            e.bx[i] = fromRight ? Pix.W - xs[i] : xs[i];
            e.by[i] = ys[i];
        }
        e.speed = 1 / 110f;
        e.x = e.bx[0];
        e.y = e.by[0];
    }

    private float slotX(Enemy e) {
        float sway = (float) Math.sin(tick * 0.02) * 22;
        return 80 + sway + e.col * 20 + (e.type == BOSS ? -1 : 0);
    }

    private static float slotY(Enemy e) {
        return 24 + e.row * 13;
    }

    private static float bez(float[] p, float t) {
        float u = 1 - t;
        return u * u * u * p[0] + 3 * u * u * t * p[1] + 3 * u * t * t * p[2] + t * t * t * p[3];
    }

    @Override
    void tick() {
        tick++;
        scrollStars(1f);
        if (bannerFrames > 0) {
            if (--bannerFrames == 0) nextWave();
            updateSparks();
            return;
        }
        moveShip();
        if (bonusStage) rocks();
        else aliens();
        moveShots();
        collide();
        updateSparks();
        if (twinLeft > 0) twinLeft--;
        if (invuln > 0) invuln--;
        if (deadFrames > 0 && ++deadFrames > 100) {
            if (lives <= 0) {
                gameOver();
                return;
            }
            deadFrames = 0;
            invuln = 120;
            theirs.clear();
            shipX = Pix.W / 2f - SHIP_W / 2f;
        }
        if (score >= nextLife) {
            nextLife += 20_000;
            lives++;
            a.sound(Sound.POWER);
        }
        if (!bonusStage && enemies.isEmpty() && deadFrames == 0) {
            banner = "WAVE CLEAR!";
            bannerFrames = 90;
            a.sound(Sound.LEVEL);
        }
    }

    private void moveShip() {
        if (deadFrames > 0) return;
        int dir = (a.held(Arcade.RIGHT) ? 1 : 0) - (a.held(Arcade.LEFT) ? 1 : 0);
        shipX = Math.max(2, Math.min(Pix.W - SHIP_W - 2, shipX + dir * 2.4f));
        if (--cooldown > 0) return;
        int limit = twinLeft > 0 ? 4 : 2;
        if (mine.size() + (twinLeft > 0 ? 2 : 1) > limit) return;
        cooldown = 12;
        if (twinLeft > 0) {
            shoot(shipX + 1);
            shoot(shipX + SHIP_W - 2);
        } else {
            shoot(shipX + SHIP_W / 2f);
        }
    }

    private void shoot(float x) {
        Shot s = new Shot();
        s.x = x;
        s.y = SHIP_Y - 3;
        s.vy = -4.5f;
        mine.add(s);
    }

    private void aliens() {
        boolean allIn = true;
        for (Enemy e : enemies) {
            switch (e.state) {
                case WAIT:
                    allIn = false;
                    if (--e.delay <= 0) e.state = ENTER;
                    break;
                case ENTER:
                    allIn = false;
                    e.t += e.speed;
                    if (e.t >= 1) e.state = HOME;
                    else {
                        e.x = bez(e.bx, e.t);
                        e.y = bez(e.by, e.t);
                    }
                    break;
                case HOME:
                    allIn = false;
                    seek(e);
                    break;
                case IN:
                    e.x = slotX(e);
                    e.y = slotY(e);
                    break;
                case DIVE:
                    dive(e);
                    break;
                default:
                    break;
            }
        }
        if (allIn && deadFrames == 0 && --diveTimer <= 0) {
            int count = 1 + rnd.nextInt(Math.min(3, 1 + wave / 2));
            for (int k = 0; k < count; k++) startDive();
            diveTimer = Math.max(45, 140 - wave * 10) + rnd.nextInt(40);
        }
        if (ufo == null && allIn && rnd.nextInt(1500) == 0) {
            ufo = new Enemy();
            boolean left = rnd.nextBoolean();
            ufo.x = left ? -13 : Pix.W;
            ufo.y = 14;
            ufo.speed = left ? 1 : -1;
        }
        if (ufo != null) {
            ufo.x += ufo.speed;
            if (ufo.x < -14 || ufo.x > Pix.W + 1) ufo = null;
        }
    }

    /** Flies straight to the moving formation slot, then locks into it. */
    private void seek(Enemy e) {
        float dx = slotX(e) - e.x, dy = slotY(e) - e.y;
        float d = (float) Math.hypot(dx, dy);
        if (d < 3) {
            e.state = IN;
            return;
        }
        e.x += dx / d * 2.5f;
        e.y += dy / d * 2.5f;
    }

    private void startDive() {
        List<Enemy> home = new ArrayList<>();
        for (Enemy e : enemies) if (e.state == IN) home.add(e);
        if (home.isEmpty()) return;
        Enemy e = home.get(rnd.nextInt(home.size()));
        float side = e.x < Pix.W / 2f ? -1 : 1;
        float target = shipX + rnd.nextInt(61) - 30;
        e.bx[0] = e.x;
        e.by[0] = e.y;
        e.bx[1] = e.x + side * 50;
        e.by[1] = e.y - 34;
        e.bx[2] = target;
        e.by[2] = 120;
        e.bx[3] = target + rnd.nextInt(121) - 60;
        e.by[3] = 200;
        e.t = 0;
        e.speed = 1f / Math.max(70, 120 - wave * 6);
        e.zig = e.type == RINGER;
        e.shots = e.type == ZAPPER || e.type == BOSS ? 2 : 1;
        e.state = DIVE;
    }

    private void dive(Enemy e) {
        e.t += e.speed;
        if (e.t >= 1) {
            // Off the bottom: come back in from the top and rejoin the formation.
            e.state = HOME;
            e.x = slotX(e);
            e.y = -10;
            return;
        }
        e.x = bez(e.bx, e.t) + (e.zig ? (float) Math.sin(e.t * Math.PI * 6) * 14 : 0);
        e.y = bez(e.by, e.t);
        if (e.shots > 0 && e.t > (e.shots == 2 ? 0.38f : 0.48f) && e.y < SHIP_Y - 30 && deadFrames == 0) {
            e.shots--;
            Shot s = new Shot();
            s.x = e.x + 4;
            s.y = e.y + 6;
            s.vy = Math.min(3f, 2f + wave * 0.1f);
            s.vx = Math.max(-0.8f, Math.min(0.8f, (shipX - e.x) * 0.012f));
            theirs.add(s);
        }
    }

    private void rocks() {
        if (rocksSpawned < 30 && tick % 20 == 0) {
            Enemy r = new Enemy();
            r.x = 10 + rnd.nextInt(Pix.W - 26);
            r.y = -8;
            r.speed = 0.8f + rnd.nextFloat() * 0.8f;
            r.t = (rnd.nextFloat() - 0.5f) * 0.6f;
            r.state = DIVE;
            r.hp = 1;
            enemies.add(r);
            rocksSpawned++;
        }
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy r = it.next();
            r.y += r.speed;
            r.x += r.t;
            if (r.y > Pix.H) it.remove();
        }
        if (rocksSpawned == 30 && enemies.isEmpty()) {
            long bonus = rocksHit == 30 ? 5000 : rocksHit * 100L;
            score += bonus;
            banner = rocksHit == 30 ? "PERFECT! +5000" : "HITS " + rocksHit + "/30";
            bannerFrames = 120;
            a.sound(Sound.LEVEL);
        }
    }

    private void moveShots() {
        for (Iterator<Shot> it = mine.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            s.y += s.vy;
            if (s.y < 10) it.remove();
        }
        for (Iterator<Shot> it = theirs.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            s.x += s.vx;
            s.y += s.vy;
            if (s.y > Pix.H) it.remove();
        }
    }

    private void collide() {
        for (Iterator<Shot> it = mine.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            boolean used = false;
            if (ufo != null && Pix.overlap(s.x, s.y, 1, 4, ufo.x, ufo.y, 13, 4)) {
                used = true;
                score += 300;
                burst(ufo.x + 6, ufo.y + 2, Pix.RED, 14);
                ufo = null;
                a.sound(Sound.POWER);
                if (rnd.nextBoolean()) twinLeft = 20 * 60;
                else shield = true;
            }
            for (Iterator<Enemy> et = enemies.iterator(); !used && et.hasNext(); ) {
                Enemy e = et.next();
                if (e.state == WAIT) continue;
                int w = bonusStage ? 6 : ENEMY[e.type][0].getWidth(), h = bonusStage ? 6 : ENEMY[e.type][0].getHeight();
                if (!Pix.overlap(s.x, s.y, 1, 4, e.x, e.y, w, h)) continue;
                used = true;
                if (--e.hp > 0) {
                    a.sound(Sound.BOUNCE);
                    continue;
                }
                et.remove();
                if (bonusStage) {
                    rocksHit++;
                    score += 100;
                    burst(e.x + 3, e.y + 3, Pix.GREY, 6);
                    a.sound(Sound.BRICK);
                } else {
                    score += POINTS[e.type] * (e.state == DIVE ? 2 : 1);
                    burst(e.x + w / 2f, e.y + h / 2f, e.type == BLIP ? Pix.GREEN : e.type == ZAPPER ? Pix.ORANGE
                            : e.type == RINGER ? Pix.CYAN : Pix.PURPLE, 10);
                    a.sound(e.type == BOSS ? Sound.BOOM : Sound.BRICK);
                }
            }
            if (used) it.remove();
        }
        if (deadFrames > 0 || invuln > 0) return;
        for (Iterator<Shot> it = theirs.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            if (Pix.overlap(s.x, s.y, 2, 3, shipX, SHIP_Y, SHIP_W, 8)) {
                it.remove();
                hit();
                return;
            }
        }
        if (bonusStage) return;
        for (Enemy e : enemies) {
            if (e.state == DIVE && Pix.overlap(e.x, e.y, 8, 6, shipX + 1, SHIP_Y + 1, SHIP_W - 2, 6)) {
                hit();
                return;
            }
        }
    }

    private void hit() {
        if (shield) {
            shield = false;
            invuln = 60;
            a.sound(Sound.BOUNCE);
            return;
        }
        lives--;
        deadFrames = 1;
        mine.clear();
        burst(shipX + SHIP_W / 2f, SHIP_Y + 4, Pix.YELLOW, 24);
        burst(shipX + SHIP_W / 2f, SHIP_Y + 4, Pix.WHITE, 12);
        a.sound(Sound.BOOM);
    }

    private void burst(float x, float y, int color, int n) {
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            s.x = x;
            s.y = y;
            double ang = rnd.nextDouble() * Math.PI * 2, sp = 0.4 + rnd.nextDouble() * 1.6;
            s.vx = (float) (Math.cos(ang) * sp);
            s.vy = (float) (Math.sin(ang) * sp);
            s.life = 20 + rnd.nextInt(20);
            s.color = color;
            sparks.add(s);
        }
    }

    private void updateSparks() {
        for (Iterator<Spark> it = sparks.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            s.x += s.vx;
            s.y += s.vy;
            if (--s.life <= 0) it.remove();
        }
    }

    private void scrollStars(float speed) {
        for (int i = 0; i < starX.length; i++) {
            starY[i] += (i % 2 == 0 ? 0.3f : 0.8f) * speed;
            if (starY[i] > Pix.H) {
                starY[i] = 12;
                starX[i] = rnd.nextInt(Pix.W);
            }
        }
    }

    @Override
    void paint(Canvas c) {
        Pix.rect(c, 0, 12, Pix.W, Pix.H - 12, 0xFF05040C);
        for (int i = 0; i < starX.length; i++) {
            Pix.rect(c, starX[i], starY[i], 1, 1, i % 2 == 0 ? 0xFF3A3650 : (i % 3 == 0 ? Pix.CYAN : Pix.GREY));
        }
        int flap = (tick / 20) % 2;
        for (Enemy e : enemies) {
            if (e.state == WAIT) continue;
            if (bonusStage) Pix.blit(c, ROCK, e.x, e.y);
            else Pix.blit(c, e.type == BOSS && e.hp == 1 ? BOSS_HURT : ENEMY[e.type][flap], e.x, e.y);
        }
        if (ufo != null) Pix.blit(c, UFO, ufo.x, ufo.y);
        for (Shot s : mine) Pix.rect(c, s.x, s.y, 1, 4, Pix.YELLOW);
        for (Shot s : theirs) {
            Pix.rect(c, s.x, s.y, 2, 3, Pix.RED);
            Pix.rect(c, s.x, s.y, 2, 1, Pix.WHITE);
        }
        if (deadFrames == 0 && (invuln == 0 || (invuln / 4) % 2 == 0)) {
            Pix.blit(c, SHIP, shipX, SHIP_Y);
            if (shield) {
                Pix.rect(c, shipX - 2, SHIP_Y - 3, SHIP_W + 4, 1, Pix.CYAN);
                Pix.rect(c, shipX - 3, SHIP_Y - 2, 1, 9, Pix.CYAN);
                Pix.rect(c, shipX + SHIP_W + 2, SHIP_Y - 2, 1, 9, Pix.CYAN);
            }
        }
        for (Spark s : sparks) Pix.rect(c, s.x, s.y, 1, 1, s.color);
        if (twinLeft > 0) Pix.text(c, "TWIN", 4, 170, twinLeft > 180 || (twinLeft / 8) % 2 == 0 ? Pix.YELLOW : Pix.DARK);
        if (bannerFrames > 0) {
            Pix.window(c, 84, 74, 152, 26);
            Pix.center(c, banner, 83, Pix.YELLOW);
        }
    }
}
