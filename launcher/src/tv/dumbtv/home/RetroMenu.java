package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** RPG-style dialog boxes with a ▶ cursor, in place of Android's AlertDialog. */
final class RetroMenu {
    private static final String CURSOR = "▶ ", BLANK = "  ";

    private RetroMenu() {}

    interface Toggle {
        void set(int which, boolean on);
    }

    interface Text {
        void done(String value);
    }

    /** A pick-one list; {@code pick} gets the index. */
    static Dialog list(Activity a, String title, String[] items, IntConsumer pick) {
        Dialog d = dialog(a);
        LinearLayout box = box(a, title);
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            rows.addView(row(a, () -> items[which], v -> {
                Sound.play(a, Sound.SELECT);
                d.dismiss();
                pick.accept(which);
            }));
        }
        box.addView(scroll(a, rows));
        show(d, box);
        return d;
    }

    /** A list of on/off lines; each OK flips one and reports it at once. */
    static void toggles(Activity a, String title, String[] names, boolean[] on, Toggle toggle) {
        Dialog d = dialog(a);
        LinearLayout box = box(a, title);
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            List<TextView> self = new ArrayList<>(1);
            TextView row = row(a, () -> (on[which] ? "[X] " : "[ ] ") + names[which], v -> {
                Sound.play(a, Sound.SELECT);
                on[which] = !on[which];
                toggle.set(which, on[which]);
                self.get(0).getOnFocusChangeListener().onFocusChange(self.get(0), true);
            });
            self.add(row);
            rows.addView(row);
        }
        rows.addView(row(a, () -> "DONE", v -> {
            Sound.play(a, Sound.BACK);
            d.dismiss();
        }));
        box.addView(scroll(a, rows));
        show(d, box);
    }

    /** A yes/no question; {@code yes} runs only on the first choice. */
    static void confirm(Activity a, String title, String message, String yesLabel, Runnable yes) {
        Dialog d = dialog(a);
        LinearLayout box = box(a, title);
        TextView body = Ui.pixelText(a, 4, Ui.WHITE);
        body.setText(message.toUpperCase(Locale.ROOT));
        body.setPadding(0, 0, 0, Ui.px(a) * 6);
        box.addView(body);
        box.addView(row(a, () -> yesLabel, v -> {
            Sound.play(a, Sound.SELECT);
            d.dismiss();
            yes.run();
        }));
        box.addView(row(a, () -> "CANCEL", v -> {
            Sound.play(a, Sound.BACK);
            d.dismiss();
        }));
        show(d, box);
    }

    /** A one-line text field with SAVE, ORIGINAL and CANCEL. */
    static void input(Activity a, String title, String value, Text save, Runnable original) {
        Dialog d = dialog(a);
        LinearLayout box = box(a, title);
        EditText field = new EditText(a);
        field.setSingleLine();
        field.setText(value);
        field.setSelectAllOnFocus(true);
        field.setTextColor(Ui.WHITE);
        field.setTypeface(Ui.pixel(a));
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(a) * 6);
        field.setBackground(new Ui.RetroButton(a));
        box.addView(field, new LinearLayout.LayoutParams(Ui.px(a) * 150, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(spacer(a));
        box.addView(row(a, () -> "SAVE", v -> {
            Sound.play(a, Sound.SELECT);
            d.dismiss();
            save.done(field.getText().toString().trim());
        }));
        box.addView(row(a, () -> "ORIGINAL NAME", v -> {
            Sound.play(a, Sound.SELECT);
            d.dismiss();
            original.run();
        }));
        box.addView(row(a, () -> "CANCEL", v -> {
            Sound.play(a, Sound.BACK);
            d.dismiss();
        }));
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        show(d, box);
    }

    private static Dialog dialog(Activity a) {
        Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        d.setOnKeyListener((dialog, code, e) -> {
            if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) Sound.play(a, Sound.BACK);
            return false;
        });
        return d;
    }

    private static LinearLayout box(Activity a, String title) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(new Ui.RetroWindow(a));
        box.setMinimumWidth(Ui.px(a) * 130);
        TextView t = Ui.pixelText(a, 6, Ui.YELLOW);
        t.setText(title.toUpperCase(Locale.ROOT));
        t.setPadding(0, 0, 0, Ui.px(a) * 6);
        box.addView(t);
        return box;
    }

    private interface Label {
        String text();
    }

    private static TextView row(Activity a, Label label, View.OnClickListener click) {
        TextView v = Ui.pixelText(a, 6, Ui.WHITE);
        int p = Ui.px(a) * 3;
        v.setPadding(0, p, p, p);
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(click);
        View.OnFocusChangeListener f = (view, has) -> {
            ((TextView) view).setText((has ? CURSOR : BLANK) + label.text().toUpperCase(Locale.ROOT));
            ((TextView) view).setTextColor(has ? Ui.YELLOW : Ui.WHITE);
        };
        v.setOnFocusChangeListener(f);
        f.onFocusChange(v, false);
        return v;
    }

    private static View scroll(Activity a, LinearLayout rows) {
        ScrollView s = new ScrollView(a) {
            @Override
            protected void onMeasure(int w, int h) {
                int max = getResources().getDisplayMetrics().heightPixels * 6 / 10;
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        s.setVerticalScrollBarEnabled(false);
        s.setFocusable(false);
        s.addView(rows);
        return s;
    }

    private static View spacer(Activity a) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.px(a) * 4));
        return v;
    }

    private static void show(Dialog d, View content) {
        d.setContentView(content);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0));
        w.setDimAmount(0.6f);
        w.setGravity(Gravity.CENTER);
        w.setWindowAnimations(0);
        d.show();
    }
}
