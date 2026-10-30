package tv.dumbtv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The retro home screen: apps as cartridges on a console shelf, HDMI inputs as controller
 * ports, a sleep timer that is always reachable, and settings behind parent mode (hold MENU
 * for three seconds).
 */
public final class Home extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final long PARENT_MS = 120_000, PARENT_HOLD_MS = 3_000;
    private static final long BLINK_MS = 500, START_WINDOW_MS = 60_000, START_TIMEOUT_MS = 15_000;

    /** The wake (Guard.wokeAt) the start screen was last shown for; survives activity restarts. */
    private static long startShownFor;
    /** This screen while it is resumed, so a screen-on that lands after onResume still reaches it. */
    private static Home current;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Config cfg;
    private TvInputManager tv;
    private int applied = -1, appsGeneration, u;
    private boolean resumed, landed, keyMove, blinkOn, menuLong;
    private long parentUntil;
    private List<Item> apps = new ArrayList<>(), inputs = new ArrayList<>();
    private Tile moving;

    private FrameLayout root, start;
    private View scan;
    private TextView badge, sleepText, clock, name, prompt, portsLabel, startPrompt;
    private TextView sleepButton, optionsButton, setupButton;
    private Egg egg;
    private HorizontalScrollView shelf, ports;
    private LinearLayout strip, portStrip;
    private int cartW;

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

    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            blinkOn = !blinkOn;
            prompt.setVisibility(blinkOn ? View.VISIBLE : View.INVISIBLE);
            startPrompt.setVisibility(blinkOn ? View.VISIBLE : View.INVISIBLE);
            if (moving != null) moving.blink(blinkOn);
            showSleep();
            if (parentUntil != 0 && !parent()) endParent();
            ui.postDelayed(this, BLINK_MS);
        }
    };

    private final Runnable startTimeout = () -> dismissStart(false);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Future<List<Item>> catalog = io.submit(() -> Catalog.apps(this));
        Sound.init(this);
        cfg = Config.get(this);
        tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        u = Ui.px(this);
        cartW = snap(getResources().getDisplayMetrics().widthPixels * 0.16f);
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

    /** Waits for the app list and the art of the carts that fit on screen, so the first frame is whole. */
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
        int fit = getResources().getDisplayMetrics().widthPixels / (cartW + u * 6) + 1;
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
        ui.removeCallbacks(blink);
        ui.post(blink);
        if (!cfg.get(Config.SLEEP_FAILED, "").isEmpty()) {
            cfg.set(Config.SLEEP_FAILED, null);
            Toast.makeText(this, "Sleep timer could not turn the TV off", Toast.LENGTH_LONG).show();
        }
        maybeStart();
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
        ui.removeCallbacks(blink);
        if (moving != null) stopMoving();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (tv != null) tv.unregisterCallback(inputWatch);
        io.shutdownNow();
        super.onDestroy();
    }

    /**
     * Android 9 keeps a hidden window's GPU context (about 35 MB on this TV) unless memory is
     * critical. Dropping it while an app plays costs one context rebuild on the way back.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_COMPLETE) return;
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
        if (moving != null) stopMoving();
        if (resumed && start.getVisibility() != View.VISIBLE) focusFirst();
    }

    @Override
    public void onBackPressed() {
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (start.getVisibility() == View.VISIBLE && code != KeyEvent.KEYCODE_VOLUME_UP
                && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (down && e.getRepeatCount() == 0) dismissStart(true);
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) return menuKey(e);
        if (parent()) parentUntil = SystemClock.uptimeMillis() + PARENT_MS;
        if (moving != null) return moveKey(e);
        boolean dpad = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        keyMove = dpad && down;
        try {
            return super.dispatchKeyEvent(e);
        } finally {
            keyMove = false;
        }
    }

    /** MENU held 3 s toggles parent mode; a short press opens menus only in parent mode. */
    private boolean menuKey(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) menuLong = false;
            else if (!menuLong && e.getEventTime() - e.getDownTime() >= PARENT_HOLD_MS) {
                menuLong = true;
                if (parent()) endParent();
                else startParent();
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && !menuLong) {
            if (!parent()) {
                Sound.play(this, Sound.ERROR);
            } else if (moving != null) {
                stopMoving();
            } else {
                View f = getCurrentFocus();
                if (f instanceof Tile) menu((Tile) f);
                else startActivity(new Intent(this, Options.class));
            }
        }
        return true;
    }

    private boolean moveKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                shift(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                shift(1);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                break;
            default:
                Sound.play(this, Sound.SELECT);
                stopMoving();
        }
        return true;
    }

    private boolean parent() {
        return SystemClock.uptimeMillis() < parentUntil;
    }

    private void startParent() {
        parentUntil = SystemClock.uptimeMillis() + PARENT_MS;
        Sound.play(this, Sound.PARENT);
        showParent();
    }

    private void endParent() {
        boolean was = parentUntil != 0;
        parentUntil = 0;
        if (moving != null) stopMoving();
        if (was && resumed) Sound.play(this, Sound.BACK);
        showParent();
    }

    private void showParent() {
        boolean on = parent();
        badge.setVisibility(on ? View.VISIBLE : View.GONE);
        optionsButton.setVisibility(on ? View.VISIBLE : View.GONE);
        setupButton.setVisibility(on ? View.VISIBLE : View.GONE);
        View f = getCurrentFocus();
        if (!on && (f == optionsButton || f == setupButton)) sleepButton.requestFocus();
    }

    private View build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        scan = new View(this);
        root.addView(scan, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout column = new LinearLayout(this) {
            @Override
            public View focusSearch(View focused, int direction) {
                if (direction == View.FOCUS_DOWN && focused instanceof Tile && focused.getParent() == strip) {
                    View below = nearest(portStrip, focused);
                    return below != null ? below : sleepButton;
                }
                if (direction == View.FOCUS_UP && focused != null && focused.getParent() != strip) {
                    View above = nearest(strip, focused);
                    if (above != null) return above;
                }
                View next = super.focusSearch(focused, direction);
                // The egg is only a LEFT away from the bottom row, never a diagonal hop from the
                // first cartridge on the shelf.
                if (next == egg && focused != null && focused.getParent() == strip) return null;
                return next;
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        int side = u * 16;
        column.setPadding(0, u * 12, 0, u * 12);
        root.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(side, 0, side, 0);
        TextView player = Ui.pixelText(this, 8, Ui.YELLOW);
        player.setText("PLAYER 1");
        head.addView(player, new LinearLayout.LayoutParams(0, WRAP, 1f));
        badge = Ui.pixelText(this, 6, Ui.GREEN);
        badge.setText("PARENT MODE");
        badge.setPadding(0, 0, u * 10, 0);
        badge.setVisibility(View.GONE);
        head.addView(badge);
        sleepText = Ui.pixelText(this, 6, Ui.WHITE);
        sleepText.setPadding(0, 0, u * 10, 0);
        head.addView(sleepText);
        clock = Ui.pixelText(this, 8, Ui.WHITE);
        head.addView(clock);
        column.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        column.addView(new View(this), new LinearLayout.LayoutParams(MATCH, 0, 1f));

        strip = new LinearLayout(this);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        strip.setGravity(Gravity.BOTTOM);
        strip.setPadding(u * 4, u * 14, u * 4, 0);
        shelf = new HorizontalScrollView(this) {
            /** Keeps the focused cart centred, except near either end of the shelf. */
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                if (getChildCount() == 0) return 0;
                int range = Math.max(0, getChildAt(0).getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
                int target = Math.max(0, Math.min(range, r.centerX() - getWidth() / 2));
                return target - getScrollX();
            }
        };
        shelf.setFocusable(false);
        shelf.setHorizontalScrollBarEnabled(false);
        shelf.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelf.setClipChildren(false);
        shelf.setClipToPadding(false);
        shelf.setPadding(side - u * 4, 0, side - u * 4, 0);
        shelf.addView(strip, new FrameLayout.LayoutParams(WRAP, WRAP));
        column.addView(shelf, new LinearLayout.LayoutParams(MATCH, WRAP));

        View slot = new View(this);
        slot.setBackgroundColor(Ui.SLOT);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(MATCH, u * 6);
        slp.leftMargin = slp.rightMargin = side / 2;
        column.addView(slot, slp);

        name = Ui.pixelText(this, 8, Ui.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setPadding(side, u * 10, side, 0);
        column.addView(name, new LinearLayout.LayoutParams(MATCH, WRAP));
        prompt = Ui.pixelText(this, 6, Ui.YELLOW);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(side, u * 5, side, 0);
        column.addView(prompt, new LinearLayout.LayoutParams(MATCH, WRAP));

        column.addView(new View(this), new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setClipChildren(false);
        // The egg sits in the left margin, so PORTS: stays where it was. LEFT from the first item
        // of the bottom row is the only way onto it.
        egg = new Egg(this, () -> startActivity(new Intent(this, Arcade.class)));
        egg.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            if (keyMove) Sound.play(this, Sound.MOVE);
            say("? ? ?", "");
        });
        bottom.setPadding(u * 4, 0, side, 0);
        LinearLayout.LayoutParams eggLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        eggLp.rightMargin = side - u * 4 - u * 8;
        bottom.addView(egg, eggLp);
        portsLabel = Ui.pixelText(this, 6, Ui.GREY);
        portsLabel.setText("PORTS:");
        portsLabel.setPadding(0, 0, u * 4, 0);
        bottom.addView(portsLabel);
        portStrip = new LinearLayout(this);
        portStrip.setGravity(Gravity.CENTER_VERTICAL);
        ports = new HorizontalScrollView(this);
        ports.setFocusable(false);
        ports.setHorizontalScrollBarEnabled(false);
        ports.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ports.addView(portStrip, new FrameLayout.LayoutParams(WRAP, WRAP));
        bottom.addView(ports, new LinearLayout.LayoutParams(0, WRAP, 1f));
        sleepButton = button("SLEEP", v -> sleepMenu());
        optionsButton = button("OPTIONS", v -> startActivity(new Intent(this, Options.class)));
        setupButton = button("SETUP", v -> openSettings());
        optionsButton.setVisibility(View.GONE);
        setupButton.setVisibility(View.GONE);
        bottom.addView(optionsButton);
        bottom.addView(setupButton);
        bottom.addView(sleepButton);
        column.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));

        start = buildStart();
        root.addView(start, new FrameLayout.LayoutParams(MATCH, MATCH));
        return root;
    }

    private FrameLayout buildStart() {
        FrameLayout s = new FrameLayout(this);
        s.setBackgroundColor(Ui.BG);
        s.setVisibility(View.GONE);
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView logo = Ui.pixelText(this, 24, Ui.YELLOW);
        logo.setText("RETRO-TV");
        logo.setTag("logo");
        middle.addView(logo, new LinearLayout.LayoutParams(WRAP, WRAP));
        TextView sub = Ui.pixelText(this, 6, Ui.GREY);
        sub.setText("HOME CONSOLE");
        sub.setPadding(0, u * 6, 0, u * 24);
        middle.addView(sub, new LinearLayout.LayoutParams(WRAP, WRAP));
        startPrompt = Ui.pixelText(this, 8, Ui.WHITE);
        startPrompt.setText("PRESS START");
        middle.addView(startPrompt, new LinearLayout.LayoutParams(WRAP, WRAP));
        s.addView(middle, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        TextView foot = Ui.pixelText(this, 4, Ui.GREY);
        foot.setText("PLAYER 1  ♥ ♥ ♥");
        s.addView(foot, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        foot.setPadding(0, 0, 0, u * 12);
        return s;
    }

    private TextView button(String label, View.OnClickListener click) {
        TextView v = Ui.pixelText(this, 6, Ui.WHITE);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setBackground(new Ui.RetroButton(this));
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(x -> {
            Sound.play(this, Sound.SELECT);
            click.onClick(x);
        });
        v.setOnFocusChangeListener((view, has) -> {
            ((TextView) view).setTextColor(has ? Ui.YELLOW : Ui.WHITE);
            if (!has) return;
            if (keyMove) Sound.play(this, Sound.MOVE);
            if (view == sleepButton) say("SLEEP TIMER", Sleep.endsAt(this) == 0 ? "▶ PRESS OK TO SET" : "▶ PRESS OK TO CHANGE");
            else if (view == optionsButton) say("OPTIONS", "▶ PRESS OK");
            else say("TV SETTINGS", "▶ PRESS OK");
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = u * 4;
        v.setLayoutParams(lp);
        return v;
    }

    private void say(String title, String hint) {
        name.setText(title.toUpperCase(Locale.ROOT));
        prompt.setText(hint);
    }

    /**
     * The child of {@code row} horizontally closest to {@code from}, or null when the row is
     * empty or hidden.
     */
    private static View nearest(ViewGroup row, View from) {
        int n = row.getChildCount();
        if (n == 0 || !row.isShown()) return null;
        int[] at = new int[2];
        from.getLocationOnScreen(at);
        int x = at[0] + from.getWidth() / 2;
        View best = null;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            View c = row.getChildAt(i);
            c.getLocationOnScreen(at);
            int gap = Math.abs(at[0] + c.getWidth() / 2 - x);
            if (gap < bestGap) {
                bestGap = gap;
                best = c;
            }
        }
        return best;
    }

    private void apply() {
        applied = cfg.version();
        render(true);
        render(false);
        scanlines();
        tick();
        showParent();
        boolean arcade = cfg.on(Config.ARCADE);
        if (!arcade && egg.isFocused()) sleepButton.requestFocus();
        egg.setVisibility(arcade ? View.VISIBLE : View.INVISIBLE);
        egg.setFocusable(arcade);
    }

    private void scanlines() {
        if (!cfg.on(Config.SCANLINES)) {
            scan.setBackground(null);
            return;
        }
        Bitmap line = Bitmap.createBitmap(1, u * 3, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < u; y++) line.setPixel(0, y, 0x38000000);
        BitmapDrawable d = new BitmapDrawable(getResources(), line);
        d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        scan.setBackground(d);
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
                            fresh.pixelated = old.pixelated;
                            fresh.artRequested = true;
                        }
                    }
                }
                apps = list;
                render(true);
            });
        });
    }

    private void loadInputs() {
        inputs = Catalog.inputs(this, tv);
        render(false);
    }

    private void render(boolean appsRow) {
        String kind = appsRow ? Catalog.APPS : Catalog.INPUTS;
        if (moving != null && moving.kind.equals(kind)) stopMoving();
        View focused = getCurrentFocus();
        String keep = focused instanceof Tile && ((Tile) focused).kind.equals(kind)
                ? ((Tile) focused).item.id : null;
        LinearLayout target = appsRow ? strip : portStrip;
        target.removeAllViews();

        boolean enabled = appsRow || "on".equals(cfg.get(Config.INPUTS));
        List<Item> shown = enabled ? Catalog.visible(cfg, kind, appsRow ? apps : inputs) : new ArrayList<>();
        boolean pixel = cfg.on(Config.PIXEL);
        int index = 0, restore = -1;
        for (Item it : shown) {
            Tile t = appsRow
                    ? new Tile(this, it, kind, Catalog.label(cfg, it), cartW, 0, pixel)
                    : new Tile(this, it, kind, Catalog.label(cfg, it), u * 58, u * 13, false);
            t.setOnClickListener(v -> open((Tile) v));
            t.setOnFocusChangeListener((v, has) -> {
                if (!has) return;
                if (keyMove) Sound.play(this, Sound.MOVE);
                Tile tile = (Tile) v;
                say(Catalog.label(cfg, tile.item).toString(),
                        Catalog.APPS.equals(tile.kind) ? "▶ PRESS OK TO PLAY" : "▶ PRESS OK TO SWITCH");
            });
            t.setOnLongClickListener(v -> {
                if (parent()) menu((Tile) v);
                else Sound.play(this, Sound.ERROR);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.leftMargin = appsRow ? u * 3 : 0;
            lp.rightMargin = appsRow ? u * 3 : u * 4;
            target.addView(t, lp);
            if (it.id.equals(keep)) restore = index;
            index++;
            if (appsRow && !it.artRequested) loadArt(it);
        }
        if (!appsRow) {
            portsLabel.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
            ports.setVisibility(shown.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
        if (restore >= 0) {
            target.getChildAt(restore).requestFocus();
        } else if (keep != null && target.getChildCount() > 0) {
            target.getChildAt(0).requestFocus();
        } else if (getCurrentFocus() == null || !landed) {
            focusFirst();
        }
    }

    private void loadArt(Item it) {
        it.artRequested = true;
        io.execute(() -> {
            Catalog.art(getPackageManager(), it);
            ui.post(() -> {
                for (int i = 0; i < strip.getChildCount(); i++) strip.getChildAt(i).invalidate();
            });
        });
    }

    private int snap(float v) {
        return Math.max(u, Math.round(v / u) * u);
    }

    private void focusFirst() {
        shelf.scrollTo(0, 0);
        ports.scrollTo(0, 0);
        View first = strip.getChildAt(0);
        if (first == null) first = portStrip.getChildAt(0);
        landed = strip.getChildCount() > 0;
        if (first == null) first = sleepButton;
        first.requestFocus();
    }

    private void tick() {
        String mode = cfg.get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
        Date now = new Date();
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm a", now).toString().toUpperCase(Locale.ROOT));
        showSleep();
    }

    private void showSleep() {
        String left = Sleep.left(this);
        sleepText.setText(left.isEmpty() ? "" : "SLEEP " + left);
        sleepText.setVisibility(left.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void sleepMenu() {
        boolean running = Sleep.endsAt(this) != 0;
        List<String> items = new ArrayList<>();
        if (running) {
            items.add("+15 MIN");
            items.add("TURN OFF TIMER");
        }
        List<Integer> minutes = new ArrayList<>();
        for (int m : Sleep.MINUTES) minutes.add(m);
        // The same debug file Keeper logs with: lets the timer be tested without a 15-minute wait.
        if (new java.io.File(cfg.dir, "debug").exists()) minutes.add(0, 1);
        for (int m : minutes) items.add(m == 120 ? "2 HOURS" : m + " MIN" + (m == 1 ? " (TEST)" : ""));
        int offset = running ? 2 : 0;
        RetroMenu.list(this, running ? "SLEEP IN " + Sleep.left(this) : "SLEEP TIMER",
                items.toArray(new String[0]), which -> {
                    if (running && which == 0) Sleep.extend(this, 15);
                    else if (running && which == 1) Sleep.cancel(this);
                    else Sleep.start(this, minutes.get(which - offset));
                    showSleep();
                    sleepButton.requestFocus();
                    say("SLEEP TIMER", Sleep.endsAt(this) == 0 ? "TIMER OFF" : "TV SLEEPS IN " + Sleep.left(this));
                });
    }

    /** Called by Guard on screen-on. */
    static void woke() {
        Home h = current;
        if (h != null) h.maybeStart();
    }

    /** The start screen shows once per boot or wake, on the first visit to Home within a minute. */
    private void maybeStart() {
        long woke = Guard.wokeAt;
        if (woke == 0 || woke == startShownFor || !cfg.on(Config.START)) return;
        startShownFor = woke;
        if (SystemClock.elapsedRealtime() - woke > START_WINDOW_MS) return;
        start.setAlpha(1f);
        start.setVisibility(View.VISIBLE);
        View logo = start.findViewWithTag("logo");
        logo.setTranslationY(-getResources().getDisplayMetrics().heightPixels / 2f);
        logo.animate().translationY(0).setInterpolator(Ui.stepped(8)).setDuration(640).start();
        ui.postDelayed(startTimeout, START_TIMEOUT_MS);
    }

    private void dismissStart(boolean pressed) {
        ui.removeCallbacks(startTimeout);
        if (start.getVisibility() != View.VISIBLE) return;
        if (pressed) Sound.play(this, Sound.START);
        start.animate().alpha(0f).setInterpolator(Ui.stepped(4)).setDuration(240)
                .withEndAction(() -> start.setVisibility(View.GONE)).start();
        focusFirst();
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
        Sound.play(this, Sound.SELECT);
        if (Catalog.APPS.equals(t.kind)) {
            Intent i = new Intent(t.item.intent);
            String pkg = t.item.id;
            if (Settings.Global.getFloat(getContentResolver(), "animator_duration_scale", 1f) == 0f) launch(i, pkg);
            else t.insert(() -> launch(i, pkg));
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
        if (pkg != null && parentUntil != 0) {
            parentUntil = 0;
            showParent();
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Sound.play(this, Sound.ERROR);
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(Tile t) {
        boolean app = Catalog.APPS.equals(t.kind);
        List<String> actions = new ArrayList<>();
        actions.add("Move");
        actions.add("Rename");
        actions.add("Hide");
        if (app && Catalog.fromAppstore(this, t.item.id)) actions.add("Appstore page");
        if (app) actions.add("App info");
        RetroMenu.list(this, Catalog.label(cfg, t.item).toString(), actions.toArray(new String[0]), which -> {
            switch (actions.get(which)) {
                case "Move":
                    if (t.getParent() == null) break;
                    moving = t;
                    t.setMoving(true);
                    t.requestFocus();
                    break;
                case "Rename":
                    rename(t);
                    break;
                case "Hide":
                    Catalog.setShown(cfg, t.kind, t.item, false);
                    render(app);
                    Toast.makeText(this, "Hidden. Bring it back from Options.", Toast.LENGTH_SHORT).show();
                    break;
                case "Appstore page":
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
        RetroMenu.input(this, "Rename", Catalog.label(cfg, t.item).toString(), value -> {
            cfg.set(key, value.isEmpty() || value.equals(t.item.label.toString()) ? null : value);
            apply();
        }, () -> {
            cfg.set(key, null);
            apply();
        });
    }

    private void shift(int by) {
        ViewGroup row = (ViewGroup) moving.getParent();
        int from = row.indexOfChild(moving), to = from + by;
        if (to < 0 || to >= row.getChildCount()) {
            Sound.play(this, Sound.ERROR);
            return;
        }
        Sound.play(this, Sound.MOVE);
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        row.removeViewAt(from);
        row.addView(moving, to, lp);
        moving.requestFocus();
    }

    private void stopMoving() {
        Tile t = moving;
        moving = null;
        t.setMoving(false);
        ViewGroup row = (ViewGroup) t.getParent();
        if (row == null) return;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) ids.add(((Tile) row.getChildAt(i)).item.id);
        Catalog.saveOrder(cfg, t.kind, ids);
        applied = cfg.version();
    }
}
