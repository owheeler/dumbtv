package tv.dumbtv.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** The 8-bit effects in res/raw, made by tools/make_sounds.py. Silent when Options › Sounds is off. */
final class Sound {
    static final int MOVE = 0, SELECT = 1, BACK = 2, ERROR = 3, PARENT = 4, START = 5, WARN = 6, SLEEP = 7;
    /** Secret Arcade effects. */
    static final int CRACK = 8, HATCH = 9, EAT = 10, BOUNCE = 11, BRICK = 12, SHOOT = 13, BOOM = 14,
            POWER = 15, LEVEL = 16, OVER = 17;
    private static final int[] RAW = {
        R.raw.sfx_move, R.raw.sfx_select, R.raw.sfx_back, R.raw.sfx_error,
        R.raw.sfx_parent, R.raw.sfx_start, R.raw.sfx_warn, R.raw.sfx_sleep,
        R.raw.sfx_crack, R.raw.sfx_hatch, R.raw.sfx_eat, R.raw.sfx_bounce, R.raw.sfx_brick,
        R.raw.sfx_shoot, R.raw.sfx_boom, R.raw.sfx_power, R.raw.sfx_level, R.raw.sfx_over,
    };
    private static final float VOLUME = 0.25f;

    private static SoundPool pool;
    private static final int[] ids = new int[RAW.length];

    private Sound() {}

    /** Loads once per process; loading is asynchronous, so the first blip may be silent. */
    static void init(Context c) {
        if (pool != null) return;
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        Context app = c.getApplicationContext();
        for (int i = 0; i < RAW.length; i++) ids[i] = pool.load(app, RAW[i], 1);
    }

    static void play(Context c, int which) {
        if (!Config.get(c).on(Config.SOUNDS)) return;
        init(c);
        pool.play(ids[which], VOLUME, VOLUME, 1, 0, 1f);
    }
}
