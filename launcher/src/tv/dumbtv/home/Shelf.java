package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The top-shelf slideshow: two stacked ImageViews that cross-fade. Photos come from the
 * {@code wallpapers/} folder next to home.conf; with none there, the built-in {@link Scenery}.
 * The order is shuffled again every time the TV wakes. It only runs while the home screen is
 * in front, and lets go of its bitmaps when the screen is hidden.
 */
final class Shelf {
    interface Listener {
        /** A small blurred copy of the photo now showing, for the backdrop behind the grid. */
        void photo(Bitmap blurred);
    }

    private static final long FADE_MS = 1500;

    private final ImageView[] views;
    private final Handler ui;
    private final ExecutorService io;
    private final Config cfg;
    private final int width, height;
    private final Listener listener;
    private final Runnable next = this::next;
    private final List<Object> order = new ArrayList<>();
    private int front, at = -1, generation;
    private long shuffledFor = -1;
    private String sourceKey;
    private boolean running, empty = true;

    Shelf(ImageView a, ImageView b, Handler ui, ExecutorService io, Config cfg, int width, int height,
            Listener listener) {
        views = new ImageView[] {a, b};
        this.ui = ui;
        this.io = io;
        this.cfg = cfg;
        this.width = width;
        this.height = height;
        this.listener = listener;
        b.setAlpha(0f);
    }

    /** Starts or resumes; reshuffles after a wake and when the photo folder changed. */
    void start(long wokeAt) {
        running = true;
        List<Object> sources = sources();
        String key = sources.toString();
        if (wokeAt != shuffledFor || !key.equals(sourceKey)) {
            shuffledFor = wokeAt;
            sourceKey = key;
            order.clear();
            order.addAll(sources);
            Collections.shuffle(order);
            at = -1;
            empty = true;
        }
        ui.removeCallbacks(next);
        if (empty) next();
        else schedule();
    }

    void stop() {
        running = false;
        ui.removeCallbacks(next);
    }

    /** Drops both bitmaps; the next {@link #start} paints the current photo again. */
    void release() {
        stop();
        generation++;
        for (ImageView v : views) v.setImageDrawable(null);
        if (at >= 0) at--;
        empty = true;
    }

    /** Moves on at once, e.g. from Options. */
    void skip() {
        ui.removeCallbacks(next);
        next();
    }

    private List<Object> sources() {
        List<Object> out = new ArrayList<>(cfg.wallpapers());
        if (out.isEmpty()) {
            for (int i = 0; i < Scenery.count(); i++) out.add(i);
        }
        return out;
    }

    private void schedule() {
        ui.removeCallbacks(next);
        long seconds = Long.parseLong(cfg.get(Config.SHELF));
        if (!running || order.size() < 2 || seconds == 0) return;
        ui.postDelayed(next, seconds * 1000L);
    }

    private void next() {
        if (order.isEmpty()) return;
        at = (at + 1) % order.size();
        Object source = order.get(at);
        int gen = ++generation;
        io.execute(() -> {
            Bitmap photo = load(source);
            Bitmap blurred = photo == null ? null : Scenery.blur(photo);
            ui.post(() -> {
                if (gen != generation || !running) return;
                if (photo == null) {
                    schedule();
                    return;
                }
                show(photo, blurred);
            });
        });
    }

    private void show(Bitmap photo, Bitmap blurred) {
        ImageView old = views[front], fresh = views[1 - front];
        fresh.setImageBitmap(photo);
        fresh.animate().cancel();
        old.animate().cancel();
        if (empty) {
            fresh.setAlpha(1f);
            old.setAlpha(0f);
            old.setImageDrawable(null);
        } else if (fresh == views[1]) {
            // views[1] sits on top: fade it in over the old photo
            fresh.setAlpha(0f);
            fresh.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
                old.setAlpha(0f);
                old.setImageDrawable(null);
            }).start();
        } else {
            // the new photo is underneath: show it, then fade the top one away
            fresh.setAlpha(1f);
            old.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> old.setImageDrawable(null)).start();
        }
        front = 1 - front;
        empty = false;
        listener.photo(blurred);
        schedule();
    }

    private Bitmap load(Object source) {
        try {
            if (source instanceof Integer) return Scenery.paint((Integer) source, width, height);
            return decode((File) source, width, height);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
