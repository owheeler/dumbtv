package tv.dumbtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The launcher's own sleep timer. Amazon's timer only accepts Amazon-signed callers, so this one
 * schedules two alarms (a warning a minute before, then sleep) and asks {@link Guard} to put the
 * TV to sleep. The end time lives in home.conf so the countdown survives the process dying.
 */
public final class Sleep extends BroadcastReceiver {
    static final int[] MINUTES = {15, 30, 45, 60, 90, 120};
    static final long WARN_MS = 60_000;
    private static final String WARN = "tv.dumbtv.home.SLEEP_WARN", NOW = "tv.dumbtv.home.SLEEP_NOW";
    private static final long SLACK_MS = 5_000;

    /** Wall-clock end of the running timer, or 0. */
    static long endsAt(Context c) {
        long at = Config.get(c).getLong(Config.SLEEP_AT);
        return at > System.currentTimeMillis() ? at : 0;
    }

    static void start(Context c, int minutes) {
        schedule(c, System.currentTimeMillis() + minutes * 60_000L);
    }

    /** Adds {@code minutes} to the running timer, or starts one. */
    static void extend(Context c, int minutes) {
        long at = endsAt(c);
        schedule(c, (at == 0 ? System.currentTimeMillis() : at) + minutes * 60_000L);
    }

    static void cancel(Context c) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        alarms.cancel(pending(c, WARN));
        alarms.cancel(pending(c, NOW));
        if (Config.get(c).getLong(Config.SLEEP_AT) != 0) Config.get(c).set(Config.SLEEP_AT, null);
    }

    private static void schedule(Context c, long at) {
        Config.get(c).set(Config.SLEEP_AT, String.valueOf(at));
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - WARN_MS, pending(c, WARN));
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, NOW));
    }

    private static PendingIntent pending(Context c, String action) {
        Intent i = new Intent(action).setClass(c, Sleep.class);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** "29:05" for the time left, or "" when no timer runs. */
    static String left(Context c) {
        long at = endsAt(c);
        if (at == 0) return "";
        long s = (at - System.currentTimeMillis() + 999) / 1000;
        if (s >= 3600) return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        Config cfg = Config.get(c);
        cfg.refresh();
        long at = cfg.getLong(Config.SLEEP_AT);
        long now = System.currentTimeMillis();
        // A late alarm still counts; an early one belongs to a timer that was moved since.
        if (WARN.equals(intent.getAction())) {
            if (at != 0 && now >= at - WARN_MS - SLACK_MS && now < at) Guard.warn(c);
            return;
        }
        if (!NOW.equals(intent.getAction()) || at == 0 || now < at - SLACK_MS) return;
        cfg.set(Config.SLEEP_AT, null);
        if (!Guard.sleep(c)) cfg.set(Config.SLEEP_FAILED, "1");
    }
}
