package tv.dumbtv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Every setting is a row: left/right or OK cycles it, and the change is saved at once. */
public final class Options extends Activity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final List<Runnable> refreshers = new ArrayList<>();
    private Config cfg;
    private int u;
    private boolean keyMove;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        u = Ui.px(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(u * 24, u * 14, u * 24, u * 14);
        scroll.addView(list);

        TextView title = Ui.pixelText(this, 12, Ui.YELLOW);
        title.setText("OPTIONS");
        title.setPadding(0, 0, 0, u * 10);
        list.addView(title);

        for (Config.Choice c : Config.CHOICES) list.addView(choice(c));
        list.addView(gap());
        Intent store = Catalog.appstore(this);
        if (store != null) {
            list.addView(action("Open Appstore (updates)", () -> {
                try {
                    startActivity(store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException e) {
                    Sound.play(this, Sound.ERROR);
                }
            }));
        }
        list.addView(action("Apps on the shelf", () -> pick(Catalog.APPS)));
        list.addView(action("Ports shown", () -> pick(Catalog.INPUTS)));
        list.addView(action("Reset order, names, hidden", this::reset));
        list.addView(action("Reset arcade high scores", () -> RetroMenu.confirm(this, "Reset high scores?",
                "Best scores in all three arcade games go back to zero.", "RESET", () -> Scores.reset(cfg))));
        list.addView(action("Open Amazon's home screen", () -> {
            Guard.amazon(this);
            finish();
        }));

        TextView about = Ui.pixelText(this, 4, Ui.GREY);
        about.setPadding(0, u * 12, 0, 0);
        about.setText(("Settings file: " + cfg.dir + "/" + Config.FILE
                + "\nParent mode: hold MENU for 3 seconds on the home screen"
                + "\nUpdate one app: in parent mode hold OK on its cart, then Appstore page"
                + "\nNo internet permission. No tracking, no ads, no accounts.").toUpperCase(Locale.ROOT));
        list.addView(about);

        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        keyMove = e.getAction() == KeyEvent.ACTION_DOWN
                && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN);
        if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) Sound.play(this, Sound.BACK);
        try {
            return super.dispatchKeyEvent(e);
        } finally {
            keyMove = false;
        }
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private View choice(Config.Choice c) {
        LinearLayout row = row();
        TextView name = Ui.pixelText(this, 6, Ui.WHITE);
        TextView value = Ui.pixelText(this, 6, Ui.YELLOW);
        value.setGravity(Gravity.END);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        row.setOnClickListener(v -> step(c, 1));
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return step(c, -1);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return step(c, 1);
            return false;
        });
        Runnable show = () -> {
            String text = c.name(cfg.get(c)).toUpperCase(Locale.ROOT);
            value.setText(row.isFocused() ? "◀ " + text + " ▶" : text);
            boolean off = c == Config.SWITCH && "off".equals(cfg.get(Config.INPUTS));
            row.setAlpha(off ? 0.4f : 1f);
        };
        refreshers.add(show);
        watchFocus(row, name, c.title, show);
        return row;
    }

    private boolean step(Config.Choice c, int by) {
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        int n = c.values.length;
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        Sound.play(this, Sound.SELECT);
        refresh();
        return true;
    }

    private View action(String text, Runnable run) {
        LinearLayout row = row();
        TextView name = Ui.pixelText(this, 6, Ui.WHITE);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.setOnClickListener(v -> {
            Sound.play(this, Sound.SELECT);
            run.run();
        });
        watchFocus(row, name, text, null);
        return row;
    }

    private void watchFocus(View row, TextView name, String text, Runnable also) {
        String upper = text.toUpperCase(Locale.ROOT);
        row.setOnFocusChangeListener((v, has) -> {
            name.setText((has ? "▶ " : "  ") + upper);
            name.setTextColor(has ? Ui.YELLOW : Ui.WHITE);
            if (has && keyMove) Sound.play(this, Sound.MOVE);
            if (also != null) also.run();
        });
        name.setText("  " + upper);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(0, u * 4, 0, u * 4);
        row.setLayoutParams(new LinearLayout.LayoutParams(u * 300, WRAP));
        return row;
    }

    private View gap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, u * 8));
        return v;
    }

    private void pick(String kind) {
        List<Item> items = Catalog.APPS.equals(kind)
                ? Catalog.apps(this)
                : Catalog.inputs(this, (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE));
        String[] names = new String[items.size()];
        boolean[] on = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = Catalog.label(cfg, items.get(i)).toString();
            on[i] = Catalog.shown(cfg, kind, items.get(i));
        }
        RetroMenu.toggles(this, Catalog.APPS.equals(kind) ? "Apps on the shelf" : "Ports shown", names, on,
                (which, checked) -> Catalog.setShown(cfg, kind, items.get(which), checked));
    }

    private void reset() {
        RetroMenu.confirm(this, "Reset the shelf?", "Order, names and hidden carts go back to default.",
                "RESET", () -> cfg.removePrefix("name.", "apps.", "inputs."));
    }
}
