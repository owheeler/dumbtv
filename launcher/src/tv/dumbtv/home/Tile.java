package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * An app drawn as a game cartridge (the app's banner is the label), or a TV input drawn as a
 * controller port. One onDraw pass; everything snaps to the retro pixel grid ({@link Ui#px}).
 */
final class Tile extends View {
    private static final int GOLD = 0xFFC9A227;
    private static final int LABEL_EMPTY = 0xFF2E2C3A;

    final Item item;
    final String kind;
    private final CharSequence title;
    private final boolean app, pixelLabels;
    private final int u, cardW, cardH, grip, inset, labelW, labelH, teeth;
    private final Paint paint = new Paint();
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint chunky = new Paint();
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect labelRect = new Rect();
    private boolean moving, blinkOn = true;

    Tile(Context c, Item item, String kind, CharSequence title, int width, int height, boolean pixelLabels) {
        super(c);
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.pixelLabels = pixelLabels;
        app = Catalog.APPS.equals(kind);
        u = Ui.px(c);
        cardW = snap(width);
        if (app) {
            grip = snap(cardW * 0.13f);
            inset = snap(cardW * 0.08f);
            labelW = cardW - 2 * inset;
            labelH = labelW * 9 / 16;
            teeth = snap(cardW * 0.11f);
            cardH = grip + labelH + snap(cardW * 0.08f) + teeth;
        } else {
            grip = inset = labelW = labelH = teeth = 0;
            cardH = snap(height);
        }
        labelRect.set(inset, grip, inset + labelW, grip + labelH);
        text.setTypeface(Ui.pixel(c));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(u * 6);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(title);
    }

    private int snap(float v) {
        return Math.max(u, Math.round(v / u) * u);
    }

    int cardHeight() {
        return cardH;
    }

    void setMoving(boolean on) {
        moving = on;
        blinkOn = true;
        invalidate();
    }

    /** Called by Home's blink tick while this tile is being moved. */
    void blink(boolean on) {
        if (!moving || blinkOn == on) return;
        blinkOn = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cardW, cardH);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (app) {
            animate().translationY(focused ? -snap(cardH * 0.14f) : 0)
                    .setInterpolator(Ui.stepped(3)).setDuration(120).start();
        }
        invalidate();
    }

    /** Slides the cart down into the console slot, then runs {@code then}. */
    void insert(Runnable then) {
        animate().translationY(snap(cardH * 0.35f)).setInterpolator(Ui.stepped(4)).setDuration(200)
                .withEndAction(() -> {
                    then.run();
                    postDelayed(() -> setTranslationY(isFocused() ? -snap(cardH * 0.14f) : 0), 600);
                }).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (app) drawCart(canvas);
        else drawPort(canvas);
    }

    private void drawCart(Canvas canvas) {
        int w = cardW, h = cardH;
        boolean ring = isFocused() && (!moving || blinkOn);

        notched(canvas, 0, 0, w, h, ring ? Ui.YELLOW : Ui.SLOT);
        int b = ring ? 2 * u : u;
        notched(canvas, b, b, w - b, h - b, Ui.PLASTIC);
        paint.setColor(Ui.PLASTIC_LIGHT);
        canvas.drawRect(b, b + u, b + u, h - teeth, paint);
        paint.setColor(Ui.PLASTIC_DARK);
        canvas.drawRect(w - b - u, b + u, w - b, h - teeth, paint);

        // grip ridges across the top
        paint.setColor(Ui.PLASTIC_DARK);
        int ridgeL = w / 4, ridgeR = w - w / 4;
        for (int y = b + u * 2; y + u <= grip - u; y += u * 2) canvas.drawRect(ridgeL, y, ridgeR, y + u, paint);

        // label: black frame, then the art
        paint.setColor(Ui.SLOT);
        canvas.drawRect(labelRect.left - u, labelRect.top - u, labelRect.right + u, labelRect.bottom + u, paint);
        drawLabel(canvas);

        // connector: dark band with gold contacts
        int top = h - teeth;
        paint.setColor(Ui.PLASTIC_DARK);
        canvas.drawRect(b, top, w - b, h - b, paint);
        paint.setColor(GOLD);
        int pinTop = top + u, pinBottom = h - b - u;
        for (int x = w / 8; x + u <= w - w / 8; x += u * 2) canvas.drawRect(x, pinTop, x + u, pinBottom, paint);

        if (moving) {
            text.setColor(Ui.YELLOW);
            canvas.drawText("◀ ▶", w / 2f, grip - u, text);
        }
    }

    private void drawLabel(Canvas canvas) {
        Drawable banner = item.banner, icon = item.icon;
        if (banner != null && pixelLabels) {
            Bitmap small = pixelated(banner);
            canvas.drawBitmap(small, null, labelRect, chunky);
            return;
        }
        if (banner != null) {
            banner.setBounds(labelRect);
            banner.draw(canvas);
            return;
        }
        paint.setColor(LABEL_EMPTY);
        canvas.drawRect(labelRect, paint);
        if (icon != null) {
            int size = labelH / 2;
            int left = labelRect.centerX() - size / 2, top = labelRect.top + labelH / 8;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
        }
        text.setColor(Ui.WHITE);
        CharSequence s = TextUtils.ellipsize(title.toString().toUpperCase(java.util.Locale.ROOT), text,
                labelW - u * 4, TextUtils.TruncateAt.END);
        float baseline = icon != null ? labelRect.bottom - u * 3 : labelRect.centerY() + text.getTextSize() / 2;
        canvas.drawText(s, 0, s.length(), labelRect.centerX(), baseline, text);
    }

    private Bitmap pixelated(Drawable banner) {
        if (item.pixelated == null) {
            int sw = Math.max(16, labelW / u), sh = Math.max(9, labelH / u);
            Bitmap bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            banner.setBounds(0, 0, sw, sh);
            banner.draw(c);
            item.pixelated = bmp;
        }
        return item.pixelated;
    }

    private void drawPort(Canvas canvas) {
        int w = cardW, h = cardH;
        boolean focused = isFocused() && (!moving || blinkOn);
        notched(canvas, 0, 0, w, h, focused ? Ui.YELLOW : Ui.PLASTIC);
        int b = focused ? 2 * u : u;
        notched(canvas, b, b, w - b, h - b, Ui.SLOT);
        boolean connected = item.state == TvInputManager.INPUT_STATE_CONNECTED;
        int dot = u * 3, dotX = u * 5, dotY = (h - dot) / 2;
        paint.setColor(connected ? Ui.GREEN : Ui.PLASTIC_DARK);
        canvas.drawRect(dotX, dotY, dotX + dot, dotY + dot, paint);
        text.setColor(focused ? Ui.YELLOW : Ui.WHITE);
        float room = w - dotX - dot - u * 6;
        CharSequence s = TextUtils.ellipsize(title.toString().toUpperCase(java.util.Locale.ROOT), text, room,
                TextUtils.TruncateAt.END);
        float cx = dotX + dot + u * 2 + room / 2f + u;
        canvas.drawText(s, 0, s.length(), cx, h / 2f + text.getTextSize() / 2f, text);
    }

    /** A filled rectangle with its four corners notched by one grid pixel. */
    private void notched(Canvas canvas, int l, int t, int r, int b, int color) {
        paint.setColor(color);
        canvas.drawRect(l + u, t, r - u, b, paint);
        canvas.drawRect(l, t + u, r, b - u, paint);
    }
}
