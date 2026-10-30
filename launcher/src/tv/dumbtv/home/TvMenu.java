package tv.dumbtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** tvOS-style menus: a rounded dark panel, rows that turn white with black text on focus. */
final class TvMenu {
    private TvMenu() {}

    interface Toggle {
        void set(int which, boolean on);
    }

    interface Text {
        void done(String value);
    }

    /** A pick-one list; {@code pick} gets the index. */
    static Dialog list(Activity a, String title, String[] items, IntConsumer pick) {
        Dialog d = dialog(a);
        LinearLayout box = box(a, title, null);
        LinearLayout rows = column(a);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            rows.addView(row(a, items[i], null, v -> {
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
        LinearLayout box = box(a, title, null);
        LinearLayout rows = column(a);
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            TextView mark = Ui.text(a, 17, Ui.TEXT, Ui.MEDIUM);
            mark.setText(on[i] ? "✓" : "");
            rows.addView(row(a, names[i], mark, v -> {
                on[which] = !on[which];
                mark.setText(on[which] ? "✓" : "");
                toggle.set(which, on[which]);
            }));
        }
        rows.addView(row(a, "Done", null, v -> d.dismiss()));
        box.addView(scroll(a, rows));
        show(d, box);
    }

    /** A yes/no question; {@code yes} runs only on the first choice. */
    static void confirm(Activity a, String title, String message, String yesLabel, Runnable yes) {
        Dialog d = dialog(a);
        LinearLayout box = box(a, title, message);
        box.addView(row(a, yesLabel, null, v -> {
            d.dismiss();
            yes.run();
        }));
        box.addView(row(a, "Cancel", null, v -> d.dismiss()));
        show(d, box);
    }

    /** A one-line text field with Save, Original name and Cancel. */
    static void input(Activity a, String title, String value, Text save, Runnable original) {
        Dialog d = dialog(a);
        LinearLayout box = box(a, title, null);
        EditText field = new EditText(a);
        field.setSingleLine();
        field.setText(value);
        field.setSelectAllOnFocus(true);
        field.setTextColor(Ui.TEXT);
        field.setTextSize(18);
        int p = Ui.dp(a, 12);
        field.setPadding(p, p, p, p);
        field.setBackground(Ui.round(Ui.GLASS, Ui.dp(a, 10)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 12);
        box.addView(field, lp);
        box.addView(row(a, "Save", null, v -> {
            d.dismiss();
            save.done(field.getText().toString().trim());
        }));
        box.addView(row(a, "Original name", null, v -> {
            d.dismiss();
            original.run();
        }));
        box.addView(row(a, "Cancel", null, v -> d.dismiss()));
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        show(d, box);
    }

    private static Dialog dialog(Activity a) {
        return new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
    }

    private static LinearLayout column(Activity a) {
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        return rows;
    }

    private static LinearLayout box(Activity a, String title, String message) {
        LinearLayout box = column(a);
        int p = Ui.dp(a, 24);
        box.setPadding(p, p, p, Ui.dp(a, 16));
        box.setBackground(Ui.round(Ui.PANEL, Ui.dp(a, 18)));
        box.setMinimumWidth(Ui.dp(a, 420));
        TextView t = Ui.text(a, 22, Ui.TEXT, Ui.MEDIUM);
        t.setText(title);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, 0, 0, Ui.dp(a, message == null ? 16 : 8));
        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (message != null) {
            TextView m = Ui.text(a, 15, Ui.MUTED, Ui.REGULAR);
            m.setText(message);
            m.setGravity(Gravity.CENTER_HORIZONTAL);
            m.setLineSpacing(0, 1.3f);
            m.setPadding(0, 0, 0, Ui.dp(a, 18));
            box.addView(m, new LinearLayout.LayoutParams(Ui.dp(a, 420), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return box;
    }

    private static View row(Activity a, String label, TextView trailing, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(a, 18);
        row.setPadding(h, Ui.dp(a, 12), h, Ui.dp(a, 12));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(click);
        TextView name = Ui.text(a, 17, Ui.TEXT, Ui.MEDIUM);
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) row.addView(trailing);
        row.setBackground(Ui.round(0, Ui.dp(a, 10)));
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.round(has ? 0xFFFFFFFF : 0, Ui.dp(a, 10)));
            name.setTextColor(has ? 0xFF000000 : Ui.TEXT);
            if (trailing != null) trailing.setTextColor(has ? 0xFF000000 : Ui.TEXT);
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(120).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 4);
        row.setLayoutParams(lp);
        return row;
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
        s.setClipToPadding(false);
        s.addView(rows);
        return s;
    }

    private static void show(Dialog d, View content) {
        d.setContentView(content);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0));
        w.setDimAmount(0.55f);
        w.setGravity(Gravity.CENTER);
        d.show();
    }
}
