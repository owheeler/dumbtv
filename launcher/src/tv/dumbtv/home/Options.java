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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** tvOS Settings style: every setting is a row; left/right or OK cycles it, saved at once. */
public final class Options extends Activity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final List<Runnable> refreshers = new ArrayList<>();
    private Config cfg;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setClipChildren(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setGravity(Gravity.CENTER_HORIZONTAL);
        list.setClipChildren(false);
        list.setPadding(dp(56), dp(44), dp(56), dp(44));
        scroll.addView(list);

        TextView title = Ui.text(this, 30, Ui.TEXT, Ui.MEDIUM);
        title.setText("Options");
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(28));
        list.addView(title);

        for (Config.Choice c : Config.CHOICES) list.addView(choice(c));
        list.addView(gap());
        Intent store = Catalog.appstore(this);
        if (store != null) {
            list.addView(action("Appstore (app updates)", () -> {
                try {
                    startActivity(store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(this, "Could not open the Appstore", Toast.LENGTH_SHORT).show();
                }
            }));
        }
        list.addView(action("Apps on home screen", () -> pick(Catalog.APPS)));
        list.addView(action("Inputs on home screen", () -> pick(Catalog.INPUTS)));
        list.addView(action("Reset order, names and hidden tiles", this::reset));
        list.addView(action("Open Amazon's home screen", () -> {
            Guard.amazon(this);
            finish();
        }));

        TextView about = Ui.text(this, 13, Ui.FAINT, Ui.REGULAR);
        about.setLineSpacing(0, 1.4f);
        about.setPadding(dp(18), dp(28), dp(18), 0);
        about.setText("Your own photos: copy .jpg or .png files into " + cfg.dir + "/" + Config.WALLPAPERS
                + "/ and they replace the built-in scenery. They shuffle every time the TV wakes."
                + "\nRearrange: hold OK on an app (or press Menu), then Move. The first five apps sit in the top row."
                + "\nSettings file: " + cfg.dir + "/" + Config.FILE
                + "\nNo internet permission. No tracking, no ads, no accounts.");
        list.addView(about, new LinearLayout.LayoutParams(dp(640), WRAP));

        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private View choice(Config.Choice c) {
        LinearLayout row = row();
        TextView name = label(c.title);
        TextView value = Ui.text(this, 17, Ui.MUTED, Ui.REGULAR);
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
            boolean focused = row.isFocused();
            String text = c.name(cfg.get(c));
            value.setText(focused ? "‹  " + text + "  ›" : text);
            value.setTextColor(focused ? 0x99000000 : Ui.MUTED);
            name.setTextColor(focused ? 0xFF000000 : Ui.TEXT);
            boolean off = c == Config.SWITCH && "off".equals(cfg.get(Config.INPUTS));
            row.setAlpha(off ? 0.4f : 1f);
        };
        focus(row, show);
        refreshers.add(show);
        return row;
    }

    private boolean step(Config.Choice c, int by) {
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        int n = c.values.length;
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        refresh();
        return true;
    }

    private View action(String text, Runnable run) {
        LinearLayout row = row();
        TextView name = label(text);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView chevron = Ui.text(this, 17, Ui.MUTED, Ui.REGULAR);
        chevron.setText("›");
        row.addView(chevron);
        row.setOnClickListener(v -> run.run());
        focus(row, () -> {
            boolean focused = row.isFocused();
            name.setTextColor(focused ? 0xFF000000 : Ui.TEXT);
            chevron.setTextColor(focused ? 0x99000000 : Ui.MUTED);
        });
        return row;
    }

    /** White fill and a small lift on focus, like a tvOS Settings row. */
    private void focus(View row, Runnable also) {
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.round(has ? 0xFFFFFFFF : 0, dp(12)));
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                    .setInterpolator(has ? Ui.SPRING : null).setDuration(has ? 240 : 140).start();
            also.run();
        });
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(20), dp(15), dp(20), dp(15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(640), WRAP);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView label(String text) {
        TextView v = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        v.setText(text);
        return v;
    }

    private View gap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(22)));
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
        TvMenu.toggles(this, Catalog.APPS.equals(kind) ? "Apps on home screen" : "Inputs on home screen",
                names, on, (which, checked) -> Catalog.setShown(cfg, kind, items.get(which), checked));
    }

    private void reset() {
        TvMenu.confirm(this, "Reset the home screen?", "Order, names and hidden tiles go back to default.",
                "Reset", () -> cfg.removePrefix("name.", "apps.", "inputs."));
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
