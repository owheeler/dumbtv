package tv.dumbtv.home;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fire OS keeps HOME bound to Amazon's protected launcher and handles the HOME key before any
 * accessibility filter sees it, so this service waits for Amazon's home window and replaces it.
 *
 * A plain startActivity right after a HOME press is held back by Android's five-second
 * app-switch delay. An alarm-fired PendingIntent is sent by system_server, which is exempt,
 * so {@link #home} launches through an alarm. That alarm only fires at once when the global
 * {@code alarm_manager_constants} has {@code min_futurity=0}; install.sh sets it.
 *
 * Amazon's home is covered with a plain overlay until ours has drawn, so it never shows.
 *
 * Android 11 refuses an activity start from an app with no visible window, even one sent
 * through system_server by an alarm ("Background activity start ... callingUidHasAnyVisibleWindow:
 * false"). The cover is such a window, so the launch waits until the cover has reached the
 * window manager: {@link #launchWhenCovered} fires it from the cover's first draw pass.
 */
public final class Guard extends AccessibilityService {
    static final String AMAZON = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final long COVER_MAX_MS = 1500;
    private static final long REPEAT_MS = 1000;
    private static final long LAUNCH_FALLBACK_MS = 150;
    private static final long WARN_SHOW_MS = 10_000;
    /** Fire OS brings its home forward about 2 s after the screen goes off; check after that. */
    private static final long AFTER_OFF_MS = 4000, AFTER_ON_MS = 700;
    /** AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN (API 28); the build compiles against API 25. */
    private static final int GLOBAL_ACTION_LOCK_SCREEN = 8;

    private static volatile boolean paused;
    private static Guard running;

    /** elapsedRealtime of the last boot or screen-on; the shelf reshuffles when it changes. */
    static volatile long wokeAt;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            handler.removeCallbacks(reclaim);
            AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarms.cancel(reclaimAlarm);
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                // The CPU suspends soon after the screen goes off, which would hold a plain
                // postDelayed until the next wake; a wakeup alarm runs it while still asleep.
                alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + AFTER_OFF_MS,
                        "dumbtv:reclaim", reclaimAlarm, handler);
                return;
            }
            wokeAt = SystemClock.elapsedRealtime();
            Sleep.cancel(c);
            Home.woke();
            handler.postDelayed(reclaim, AFTER_ON_MS);
        }
    };
    private final Runnable reclaim = this::reclaim;
    private final AlarmManager.OnAlarmListener reclaimAlarm = this::reclaim;
    private final Runnable unwarn = this::unwarn;
    private View warning;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uncover = this::uncover;
    private final Runnable launch = () -> home(this);
    private View cover;
    private long lastLaunch;

    /** Lets Amazon's home stay on screen until the user leaves it. */
    static void pause() {
        paused = true;
        Keeper.pause();
    }

    static void home(Context c) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        alarms.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), launch(c));
    }

    /** Drops a queued launch, so it cannot pull the user back after they opened an app. */
    static void arrived(Context c) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        alarms.cancel(launch(c));
    }

    /** Called once our home window is on screen. */
    static void shown() {
        if (running != null) running.uncover();
    }

    private static PendingIntent launch(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(c, Home.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT,
                ActivityOptions.makeCustomAnimation(c, 0, 0).toBundle());
    }

    static void amazon(Context c) {
        pause();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(AMAZON).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {
            paused = false;
        }
    }

    /** Puts the TV to sleep; false when this service is not bound or the build is older than Android 9. */
    static boolean sleep(Context c) {
        Guard g = running;
        if (g == null || Build.VERSION.SDK_INT < 28) return false;
        g.unwarn();
        g.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        return true;
    }

    /** A small panel over whatever is playing, one minute before the sleep timer ends. */
    static void warn(Context c) {
        Guard g = running;
        if (g != null) g.showWarning();
    }

    private void showWarning() {
        unwarn();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 22);
        box.setPadding(p + p / 2, p, p + p / 2, p);
        box.setBackground(Ui.round(Ui.PANEL, Ui.dp(this, 18)));
        TextView title = Ui.text(this, 20, Ui.TEXT, Ui.MEDIUM);
        title.setText("The TV will sleep in 1 minute");
        TextView hint = Ui.text(this, 15, Ui.MUTED, Ui.REGULAR);
        hint.setText("Press Home to change the sleep timer");
        hint.setPadding(0, Ui.dp(this, 8), 0, 0);
        box.addView(title);
        box.addView(hint);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = Ui.dp(this, 36);
        ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(box, lp);
        warning = box;
        handler.postDelayed(unwarn, WARN_SHOW_MS);
    }

    private void unwarn() {
        handler.removeCallbacks(unwarn);
        if (warning == null) return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(warning);
        warning = null;
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        wokeAt = SystemClock.elapsedRealtime();
        IntentFilter power = new IntentFilter(Intent.ACTION_SCREEN_ON);
        power.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, power);
        View view = new View(this);
        view.setVisibility(View.GONE);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.windowAnimations = 0;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(view, lp);
        cover = view;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(uncover);
        handler.removeCallbacks(launch);
        handler.removeCallbacks(reclaim);
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(reclaimAlarm);
        unregisterReceiver(screen);
        unwarn();
        if (cover != null) ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        if (running == this) running = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName(), cls = e.getClassName();
        if (pkg == null) return;
        if (!AMAZON.contentEquals(pkg)) {
            if ("com.android.systemui".contentEquals(pkg) || "android".contentEquals(pkg)) return;
            paused = false;
            if (!getPackageName().contentEquals(pkg)) arrived(this);
            return;
        }
        if (paused || cls == null || !AMAZON_HOME.contentEquals(cls)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < REPEAT_MS) return;
        lastLaunch = now;
        launchWhenCovered();
    }

    /**
     * Shows the cover, then starts the launch once that window is visible to the window manager.
     * A cover that is already on screen (a repeat within {@link #COVER_MAX_MS}) gets no new draw
     * pass, so the launch goes out at once; otherwise it waits for the first pre-draw, which runs
     * after the window's relayout. A short fallback covers a window that never draws.
     */
    private void launchWhenCovered() {
        handler.removeCallbacks(launch);
        if (cover == null) {
            home(this);
            return;
        }
        boolean shown = cover.getVisibility() == View.VISIBLE;
        cover();
        if (shown) {
            home(this);
            return;
        }
        final ViewTreeObserver observer = cover.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                cover.getViewTreeObserver().removeOnPreDrawListener(this);
                handler.removeCallbacks(launch);
                home(Guard.this);
                return true;
            }
        });
        handler.postDelayed(launch, LAUNCH_FALLBACK_MS);
    }

    /**
     * When the TV goes to sleep during playback, Fire OS moves its own home to the front while the
     * screen is dark. No window event reaches this service then, and on wake that home simply
     * resumes, so it stayed until the next key press. UsageStats says what is in front instead
     * (install.sh grants GET_USAGE_STATS through appops; without it this finds nothing).
     */
    private void reclaim() {
        if (!paused && AMAZON_HOME.equals(frontClass())) launchWhenCovered();
    }

    private String frontClass() {
        UsageStatsManager usage = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usage == null) return null;
        long now = System.currentTimeMillis();
        try {
            // A day back: on wake, the move to the front happened when the screen went off.
            UsageEvents events = usage.queryEvents(now - 86_400_000L, now);
            if (events == null) return null;
            UsageEvents.Event e = new UsageEvents.Event();
            String front = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) front = e.getClassName();
            }
            return front;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The overlay window exists from service start; a GONE root keeps its surface hidden. */
    private void cover() {
        if (cover == null) return;
        cover.setBackgroundColor(Config.get(this).background());
        cover.setVisibility(View.VISIBLE);
        handler.removeCallbacks(uncover);
        handler.postDelayed(uncover, COVER_MAX_MS);
    }

    private void uncover() {
        handler.removeCallbacks(uncover);
        if (cover != null) cover.setVisibility(View.GONE);
    }

    @Override
    public void onInterrupt() {}
}
