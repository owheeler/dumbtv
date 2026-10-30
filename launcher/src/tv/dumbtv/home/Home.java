package tv.dumbtv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * An Apple TV–style home: a photo slideshow fills the top shelf, the first five apps sit in a
 * dock over it, and scrolling down slides the shelf away over a blurred copy of the photo to
 * show every other app, the built-in Settings, Options and Sleep cards, and the TV inputs.
 */
public final class Home extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int COLUMNS = 5, DOCK = 5;
    private static final String SETTINGS = "builtin:settings", OPTIONS = "builtin:options", SLEEP = "builtin:sleep";

    private static Home current;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService shelfIo = Executors.newSingleThreadExecutor();
    private Config cfg;
    private TvInputManager tv;
    private int applied = -1, appsGeneration;
    private boolean resumed;
    private int tileW, tileH, edge, gap;
    private FrameLayout root, shelfLayer;
    private ImageView backdrop;
    private ScrollView page;
    private LinearLayout column, dock, inputStrip;
    private Grid grid;
    private View spacer, topShade;
    private TextView clock, sleepText, inputsTitle;
    private Shelf shelf;
    private List<Item> apps = new ArrayList<>(), inputs = new ArrayList<>(), shownApps = new ArrayList<>(),
            shownInputs = new ArrayList<>();
    private String movingId, movingKind;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            loadApps(data == null ? null : data.getSchemeSpecificPart());
        }
    };

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    /** Once a second while a sleep timer runs: the countdown in the corner and on the Sleep card. */
    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            showSleep();
            if (resumed && Sleep.endsAt(Home.this) != 0) ui.postDelayed(this, 1000);
        }
    };

    private final TvInputManager.TvInputCallback inputWatch = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String id, int state) {
            loadInputs();
        }

        @Override
        public void onInputAdded(String id) {
            loadInputs();
        }

        @Override
        public void onInputRemoved(String id) {
            loadInputs();
        }

        @Override
        public void onInputUpdated(String id) {
            loadInputs();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Future<List<Item>> catalog = io.submit(() -> Catalog.apps(this));
        cfg = Config.get(this);
        tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        setContentView(build());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        Keeper.start(this);
        if (tv != null) tv.registerCallback(inputWatch, ui);
        firstApps(catalog);
        loadInputs();
    }

    /**
     * Waits for the app list and loads the dock's art, so the first frame is complete. Loading it
     * in the background drew empty cards for a moment, then popped the banners in.
     */
    private void firstApps(Future<List<Item>> catalog) {
        try {
            apps = catalog.get();
        } catch (ExecutionException e) {
            loadApps(null);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loadApps(null);
            return;
        }
        int fit = DOCK + COLUMNS;
        for (Item it : Catalog.visible(cfg, Catalog.APPS, apps)) {
            if (fit-- <= 0) break;
            Catalog.art(getPackageManager(), it);
            it.artRequested = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        current = this;
        Guard.arrived(this);
        cfg.refresh();
        if (cfg.version() != applied) apply();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        tick();
        shelf.start(Guard.wokeAt);
        ui.removeCallbacks(countdown);
        countdown.run();
        if (!cfg.get(Config.SLEEP_FAILED, "").isEmpty()) {
            cfg.set(Config.SLEEP_FAILED, null);
            Toast.makeText(this, "The sleep timer could not turn the TV off", Toast.LENGTH_LONG).show();
        }
        // HOME during the one-minute warning lands here: offer to change the timer.
        long at = Sleep.endsAt(this);
        if (at != 0 && at - System.currentTimeMillis() <= Sleep.WARN_MS) ui.post(this::sleepMenu);
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (current == this) current = null;
        unregisterReceiver(time);
        ui.removeCallbacks(countdown);
        shelf.stop();
        if (movingId != null) stopMoving();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (tv != null) tv.unregisterCallback(inputWatch);
        io.shutdownNow();
        shelfIo.shutdownNow();
        super.onDestroy();
    }

    /** Called by Guard when the screen turns on: a fresh shuffle of the shelf. */
    static void woke() {
        Home h = current;
        if (h != null && h.resumed) h.shelf.start(Guard.wokeAt);
    }

    /**
     * Android 9 keeps a hidden window's GPU context (about 35 MB on this TV) unless memory is
     * critical. Dropping it while an app plays costs one context rebuild on the way back. The
     * shelf's photos go too; they repaint in well under a second.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_COMPLETE) return;
        shelf.release();
        backdrop.setImageDrawable(null);
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object global = wmg.getMethod("getInstance").invoke(null);
            wmg.getMethod("trimMemory", int.class).invoke(global, TRIM_MEMORY_COMPLETE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not reachable on this build: the context simply stays
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (movingId != null) stopMoving();
        if (resumed) focusFirst();
    }

    @Override
    public void onBackPressed() {
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (movingId == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            boolean app = Catalog.APPS.equals(movingKind);
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shift(-1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shift(1);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (app) shift(-COLUMNS);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (app) shift(COLUMNS);
                    break;
                default:
                    stopMoving();
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Tile && !Tile.BUILTIN.equals(((Tile) f).kind)) menu((Tile) f);
            else startActivity(new Intent(this, Options.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private View build() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        edge = dp(48);
        gap = dp(26);
        tileW = (w - 2 * edge - (COLUMNS - 1) * gap) / COLUMNS;
        tileH = tileW * 9 / 16;

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(MATCH, MATCH));

        shelfLayer = new FrameLayout(this);
        ImageView a = new ImageView(this), b = new ImageView(this);
        for (ImageView v : new ImageView[] {a, b}) {
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shelfLayer.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x00000000, 0x66000000, 0xD9000000}));
        shelfLayer.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(shelfLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
        // Photos are painted at 1280x720: plenty for a soft backdrop, a third of the memory of 1080p.
        shelf = new Shelf(a, b, ui, shelfIo, cfg, Math.min(w, 1280), Math.min(h, 720),
                blurred -> backdrop.setImageBitmap(blurred));

        page = new ScrollView(this) {
            /** The dock keeps the page at the top; any lower row scrolls up to just under the clock. */
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                int target = r.top < dock.getBottom() ? 0 : r.top - dp(118);
                int range = Math.max(0, getChildAt(0).getHeight() - getHeight());
                return Math.max(0, Math.min(range, target)) - getScrollY();
            }

            /** Up and down only move the focus; scrolling follows it. */
            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_PAGE_UP:
                    case KeyEvent.KEYCODE_PAGE_DOWN:
                    case KeyEvent.KEYCODE_SPACE:
                        return false;
                    default:
                        return super.executeKeyEvent(e);
                }
            }
        };
        page.setFocusable(false);
        page.setVerticalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.setClipChildren(false);
        page.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            float shelfH = spacer.getHeight();
            float t = shelfH == 0 ? 0 : Math.min(1f, y / (shelfH * 0.55f));
            backdrop.setAlpha(t);
            topShade.setAlpha(t);
            shelfLayer.setAlpha(1f - t);
            shelfLayer.setTranslationY(-y * 0.3f);
        });
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, 0, 0, h / 2);
        page.addView(column, new FrameLayout.LayoutParams(MATCH, WRAP));

        spacer = new View(this);
        column.addView(spacer, new LinearLayout.LayoutParams(MATCH, Math.round(h * 0.6f)));
        dock = new LinearLayout(this);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dock.setPadding(edge, 0, edge, 0);
        column.addView(dock, new LinearLayout.LayoutParams(MATCH, WRAP));
        grid = new Grid(this, gap, dp(22));
        grid.setPadding(edge, dp(52), edge, 0);
        column.addView(grid, new LinearLayout.LayoutParams(MATCH, WRAP));
        inputsTitle = Ui.text(this, 15, Ui.MUTED, Ui.MEDIUM);
        inputsTitle.setText("Inputs");
        inputsTitle.setPadding(edge + dp(4), dp(30), edge, dp(14));
        column.addView(inputsTitle, new LinearLayout.LayoutParams(MATCH, WRAP));
        inputStrip = new LinearLayout(this);
        inputStrip.setClipChildren(false);
        inputStrip.setClipToPadding(false);
        inputStrip.setPadding(edge, 0, edge, 0);
        column.addView(inputStrip, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Rows scrolling up pass under this band, so they never collide with the clock.
        topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xF20B0B0D, 0xCC0B0B0D, 0x000B0B0D}));
        topShade.setAlpha(0f);
        root.addView(topShade, new FrameLayout.LayoutParams(MATCH, dp(104)));

        LinearLayout corner = new LinearLayout(this);
        corner.setGravity(Gravity.CENTER_VERTICAL);
        sleepText = Ui.text(this, 15, Ui.MUTED, Ui.MEDIUM);
        sleepText.setShadowLayer(dp(6), 0, dp(1), 0xAA000000);
        sleepText.setPadding(0, 0, dp(18), 0);
        corner.addView(sleepText);
        clock = Ui.text(this, 22, Ui.TEXT, Ui.MEDIUM);
        clock.setShadowLayer(dp(6), 0, dp(1), 0xAA000000);
        corner.addView(clock);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        lp.topMargin = dp(30);
        lp.rightMargin = edge;
        root.addView(corner, lp);
        return root;
    }

    private void apply() {
        applied = cfg.version();
        renderApps();
        renderInputs();
        tick();
    }

    /** Rebuilds the app list, keeping loaded art for every package except {@code changed}. */
    private void loadApps(String changed) {
        int generation = ++appsGeneration;
        io.execute(() -> {
            List<Item> list = Catalog.apps(this);
            ui.post(() -> {
                if (generation != appsGeneration || isDestroyed()) return;
                for (Item fresh : list) {
                    for (Item old : apps) {
                        if (old.id.equals(fresh.id) && !old.id.equals(changed) && old.artRequested) {
                            fresh.banner = old.banner;
                            fresh.icon = old.icon;
                            fresh.artRequested = true;
                        }
                    }
                }
                apps = list;
                renderApps();
            });
        });
    }

    private void loadInputs() {
        inputs = Catalog.inputs(this, tv);
        renderInputs();
    }

    private void renderApps() {
        String keep = focusedId();
        shownApps = Catalog.visible(cfg, Catalog.APPS, apps);
        dock.removeAllViews();
        grid.removeAllViews();
        View restore = null;
        for (int i = 0; i < shownApps.size(); i++) {
            Item it = shownApps.get(i);
            Tile t = tile(it, Catalog.APPS, tileW, tileH);
            if (i < DOCK) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
                if (i < DOCK - 1) lp.rightMargin = gap;
                dock.addView(t, lp);
            } else {
                grid.addView(t, new ViewGroup.LayoutParams(WRAP, WRAP));
            }
            if (it.id.equals(keep)) restore = t;
            if (!it.artRequested) loadArt(it);
        }
        for (String id : new String[] {SETTINGS, OPTIONS, SLEEP}) {
            String name = SETTINGS.equals(id) ? "Settings" : OPTIONS.equals(id) ? "Options" : "Sleep";
            Tile t = tile(new Item(id, name, null), Tile.BUILTIN, tileW, tileH);
            if (SLEEP.equals(id)) {
                t.setSubtitle(() -> {
                    String left = Sleep.left(this);
                    return left.isEmpty() ? "Timer off" : "Sleeps in " + left;
                });
            }
            grid.addView(t, new ViewGroup.LayoutParams(WRAP, WRAP));
            if (id.equals(keep)) restore = t;
        }
        if (restore != null) restore.requestFocus();
        else if (!(getCurrentFocus() instanceof Tile)) focusFirst();
    }

    private void renderInputs() {
        String keep = focusedId();
        boolean enabled = "on".equals(cfg.get(Config.INPUTS));
        shownInputs = enabled ? Catalog.visible(cfg, Catalog.INPUTS, inputs) : new ArrayList<>();
        inputStrip.removeAllViews();
        View restore = null;
        for (Item it : shownInputs) {
            Tile t = tile(it, Catalog.INPUTS, dp(160), dp(48));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(18);
            inputStrip.addView(t, lp);
            if (it.id.equals(keep)) restore = t;
        }
        int v = shownInputs.isEmpty() ? View.GONE : View.VISIBLE;
        inputsTitle.setVisibility(v);
        inputStrip.setVisibility(v);
        if (restore != null) restore.requestFocus();
        else if (!(getCurrentFocus() instanceof Tile)) focusFirst();
    }

    private Tile tile(Item it, String kind, int w, int h) {
        Tile t = new Tile(this, it, kind, Tile.BUILTIN.equals(kind) ? it.label : Catalog.label(cfg, it), w, h);
        t.setOnClickListener(v -> open((Tile) v));
        t.setOnLongClickListener(v -> {
            Tile tile = (Tile) v;
            if (Tile.BUILTIN.equals(tile.kind)) return false;
            menu(tile);
            return true;
        });
        return t;
    }

    private String focusedId() {
        View f = getCurrentFocus();
        return f instanceof Tile ? ((Tile) f).item.id : null;
    }

    private void loadArt(Item it) {
        it.artRequested = true;
        io.execute(() -> {
            Catalog.art(getPackageManager(), it);
            ui.post(() -> {
                for (ViewGroup g : new ViewGroup[] {dock, grid}) {
                    for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).invalidate();
                }
            });
        });
    }

    private void focusFirst() {
        page.scrollTo(0, 0);
        View first = dock.getChildAt(0);
        if (first == null) first = grid.getChildAt(0);
        if (first != null) first.requestFocus();
    }

    private void tick() {
        String mode = cfg.get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", new Date()));
    }

    private void showSleep() {
        String left = Sleep.left(this);
        sleepText.setText(left.isEmpty() ? "" : "Sleep in " + left);
        sleepText.setVisibility(left.isEmpty() ? View.GONE : View.VISIBLE);
        View sleepTile = grid.getChildAt(grid.getChildCount() - 1);
        if (sleepTile != null) sleepTile.invalidate();
    }

    private void sleepMenu() {
        if (!resumed) return;
        boolean running = Sleep.endsAt(this) != 0;
        List<String> items = new ArrayList<>();
        if (running) {
            items.add("Add 15 minutes");
            items.add("Turn off the timer");
        }
        List<Integer> minutes = new ArrayList<>();
        for (int m : Sleep.MINUTES) minutes.add(m);
        // The same debug file Keeper logs with: lets the timer be tested without a 15-minute wait.
        if (new File(cfg.dir, "debug").exists()) minutes.add(0, 1);
        for (int m : minutes) {
            items.add(m == 1 ? "1 minute (test)" : m % 60 == 0 ? m / 60 + (m == 60 ? " hour" : " hours")
                    : m > 60 ? m / 60 + " hour " + m % 60 + " minutes" : m + " minutes");
        }
        int offset = running ? 2 : 0;
        TvMenu.list(this, running ? "Sleep in " + Sleep.left(this) : "Sleep timer",
                items.toArray(new String[0]), which -> {
                    if (running && which == 0) Sleep.extend(this, 15);
                    else if (running && which == 1) Sleep.cancel(this);
                    else Sleep.start(this, minutes.get(which - offset));
                    applied = cfg.version();
                    ui.removeCallbacks(countdown);
                    countdown.run();
                });
    }

    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, which
     * Guard then replaces with ours. The TV settings entry point opens the real settings screen.
     */
    private void openSettings() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS");
        boolean found = tvSettings.resolveActivity(getPackageManager()) != null;
        launch(found ? tvSettings : new Intent(Settings.ACTION_SETTINGS), null);
    }

    private void open(Tile t) {
        if (Tile.BUILTIN.equals(t.kind)) {
            if (SETTINGS.equals(t.item.id)) openSettings();
            else if (OPTIONS.equals(t.item.id)) startActivity(new Intent(this, Options.class));
            else sleepMenu();
            return;
        }
        if (Catalog.APPS.equals(t.kind)) {
            launch(new Intent(t.item.intent), t.item.id);
            return;
        }
        Intent own = Input.intent(this, cfg, t.item, Catalog.label(cfg, t.item));
        // The built-in screen goes on top of this one in the same task, so BACK returns here and
        // the HOME takeover's singleTask relaunch clears it. NEW_TASK | RESET_TASK_IF_NEEDED on an
        // activity of the task that is already in front only brings that task forward and starts
        // nothing (ActivityStarter.complyActivityFlags), so it is started plainly.
        if (own != null) startActivity(own);
        else launch(new Intent(t.item.intent), null);
    }

    private void launch(Intent i, String pkg) {
        if (Guard.AMAZON.equals(pkg)) Guard.pause();
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(Tile t) {
        boolean app = Catalog.APPS.equals(t.kind);
        List<String> actions = new ArrayList<>();
        actions.add("Move");
        actions.add("Rename");
        actions.add("Hide");
        if (app && Catalog.fromAppstore(this, t.item.id)) actions.add("Check for updates");
        if (app) actions.add("App info");
        TvMenu.list(this, Catalog.label(cfg, t.item).toString(), actions.toArray(new String[0]), which -> {
            switch (actions.get(which)) {
                case "Move":
                    movingId = t.item.id;
                    movingKind = t.kind;
                    t.setMoving(true);
                    break;
                case "Rename":
                    rename(t);
                    break;
                case "Hide":
                    Catalog.setShown(cfg, t.kind, t.item, false);
                    applied = cfg.version();
                    if (app) renderApps();
                    else renderInputs();
                    Toast.makeText(this, "Hidden. Bring it back from Options.", Toast.LENGTH_SHORT).show();
                    break;
                case "Check for updates":
                    launch(Catalog.appstorePage(t.item.id), null);
                    break;
                default:
                    launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", t.item.id, null)), null);
            }
        });
    }

    private void rename(Tile t) {
        String key = "name." + t.item.id;
        TvMenu.input(this, "Rename", Catalog.label(cfg, t.item).toString(), name -> {
            cfg.set(key, name.isEmpty() || name.equals(t.item.label.toString()) ? null : name);
            apply();
        }, () -> {
            cfg.set(key, null);
            apply();
        });
    }

    /** Moves the tile being moved {@code by} places in the saved order, then redraws its row. */
    private void shift(int by) {
        boolean app = Catalog.APPS.equals(movingKind);
        List<Item> list = app ? shownApps : shownInputs;
        int from = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(movingId)) from = i;
        int to = from + by;
        if (from < 0 || to < 0 || to >= list.size()) return;
        List<String> ids = new ArrayList<>();
        for (Item it : list) ids.add(it.id);
        ids.add(to, ids.remove(from));
        Catalog.saveOrder(cfg, movingKind, ids);
        applied = cfg.version();
        if (app) renderApps();
        else renderInputs();
        Tile moved = find(movingId);
        if (moved != null) {
            moved.setMoving(true);
            moved.requestFocus();
        }
    }

    private void stopMoving() {
        Tile t = find(movingId);
        movingId = null;
        movingKind = null;
        if (t != null) t.setMoving(false);
    }

    private Tile find(String id) {
        for (ViewGroup g : new ViewGroup[] {dock, grid, inputStrip}) {
            for (int i = 0; i < g.getChildCount(); i++) {
                Tile t = (Tile) g.getChildAt(i);
                if (t.item.id.equals(id)) return t;
            }
        }
        return null;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
