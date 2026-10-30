package tv.dumbtv.home;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;

/**
 * A tvOS-style card drawn in one pass. Focus lifts it on a spring, casts a shadow, adds a faint
 * diagonal glare, and fades the name in underneath. Three shapes: app banners (16:9), text cards
 * for the built-in Settings, Options and Sleep, and rounded pills for TV inputs.
 */
final class Tile extends View {
    static final String BUILTIN = "builtin";

    interface Subtitle {
        String text();
    }

    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH, labelH;
    private final float radius, lift;
    private final boolean pill, textCard;
    private final RectF card = new RectF();
    private final Path clip = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glare = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint inner = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint under = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Subtitle subtitle;
    private float labelAlpha;
    private ValueAnimator labelFade;
    private ObjectAnimator jiggle;

    Tile(Context c, Item item, String kind, CharSequence title, int cardW, int cardH) {
        super(c);
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.cardW = cardW;
        this.cardH = cardH;
        pill = Catalog.INPUTS.equals(kind);
        textCard = BUILTIN.equals(kind);
        // Built-in cards reserve the same name strip as apps so every cell of the grid is one size.
        labelH = pill ? 0 : Ui.dp(c, 30);
        radius = pill ? cardH / 2f : Ui.dp(c, 12);
        lift = pill ? 1.08f : 1.12f;

        fill.setColor(pill || textCard ? Ui.GLASS : Ui.SURFACE);
        dot.setColor(0xFF34C759);
        inner.setColor(Ui.TEXT);
        inner.setTypeface(Ui.MEDIUM);
        inner.setTextAlign(Paint.Align.CENTER);
        inner.setTextSize(Ui.dp(c, pill ? 15 : textCard ? 19 : 14));
        small.setColor(Ui.MUTED);
        small.setTypeface(Ui.REGULAR);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(Ui.dp(c, 13));
        under.setColor(Ui.TEXT);
        under.setTypeface(Ui.MEDIUM);
        under.setTextAlign(Paint.Align.CENTER);
        under.setTextSize(Ui.dp(c, 14));
        under.setShadowLayer(Ui.dp(c, 4), 0, Ui.dp(c, 1), 0x99000000);

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, Tile.this.cardW, Tile.this.cardH, radius);
                o.setAlpha(0.45f);
            }
        });
        setElevation(Ui.dp(c, 2));
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(title);
    }

    /** A second line for text cards, read again on every draw (e.g. the sleep countdown). */
    void setSubtitle(Subtitle s) {
        subtitle = s;
        invalidate();
    }

    void setMoving(boolean on) {
        if (jiggle != null) {
            jiggle.cancel();
            jiggle = null;
            setRotation(0);
        }
        if (on) {
            jiggle = ObjectAnimator.ofFloat(this, View.ROTATION, -1.2f, 1.2f);
            jiggle.setDuration(140);
            jiggle.setRepeatCount(ValueAnimator.INFINITE);
            jiggle.setRepeatMode(ValueAnimator.REVERSE);
            jiggle.setInterpolator(new LinearInterpolator());
            jiggle.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (jiggle != null) jiggle.cancel();
        if (labelFade != null) labelFade.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cardW, cardH + labelH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        card.set(0, 0, cardW, cardH);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        glare.setShader(new LinearGradient(0, 0, cardW * 0.7f, cardH, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        setPivotX(cardW / 2f);
        setPivotY(cardH / 2f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        float s = focused ? lift : 1f;
        animate().scaleX(s).scaleY(s).translationZ(focused ? Ui.dp(getContext(), 22) : 0)
                .setDuration(focused ? 260 : 160).setInterpolator(focused ? Ui.SPRING : null).start();
        if (labelFade != null) labelFade.cancel();
        labelFade = ValueAnimator.ofFloat(labelAlpha, focused ? 1f : 0f);
        labelFade.setDuration(focused ? 180 : 100);
        labelFade.addUpdateListener(a -> {
            labelAlpha = (Float) a.getAnimatedValue();
            invalidate();
        });
        labelFade.start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean focused = isFocused();
        Drawable banner = item.banner, icon = item.icon;
        canvas.save();
        canvas.clipPath(clip);
        if (pill || textCard) {
            fill.setColor(focused ? 0xFFFFFFFF : Ui.GLASS);
            canvas.drawRect(card, fill);
            int ink = focused ? 0xFF000000 : Ui.TEXT;
            inner.setColor(ink);
            small.setColor(focused ? 0x99000000 : Ui.MUTED);
            String sub = subtitle == null ? null : subtitle.text();
            float mid = cardH / 2f - (inner.ascent() + inner.descent()) / 2;
            if (sub != null && !sub.isEmpty()) {
                float gap = Ui.dp(getContext(), 12);
                drawLine(canvas, inner, title, cardW / 2f, mid - gap);
                drawLine(canvas, small, sub, cardW / 2f, mid + gap + Ui.dp(getContext(), 4));
            } else {
                drawLine(canvas, inner, title, cardW / 2f, mid);
            }
        } else {
            fill.setColor(Ui.SURFACE);
            canvas.drawRect(card, fill);
            if (banner != null) {
                banner.setBounds(0, 0, cardW, cardH);
                banner.draw(canvas);
            } else if (icon != null) {
                int size = cardH * 46 / 100;
                int left = (cardW - size) / 2, top = cardH * 12 / 100;
                icon.setBounds(left, top, left + size, top + size);
                icon.draw(canvas);
                inner.setColor(Ui.TEXT);
                drawLine(canvas, inner, title, cardW / 2f, cardH - cardH * 13 / 100f);
            }
            if (focused) canvas.drawRect(card, glare);
        }
        canvas.restore();

        if (item.state == TvInputManager.INPUT_STATE_CONNECTED) {
            float r = Ui.dp(getContext(), 3.5f);
            canvas.drawCircle(radius * 0.75f, cardH / 2f, r, dot);
        }
        if (labelH > 0 && labelAlpha > 0.01f && banner != null) {
            under.setAlpha(Math.round(labelAlpha * 255));
            drawLine(canvas, under, title, cardW / 2f, cardH + labelH - Ui.dp(getContext(), 2));
        }
    }

    private void drawLine(Canvas canvas, TextPaint paint, CharSequence text, float x, float baseline) {
        float room = cardW - Ui.dp(getContext(), 20);
        CharSequence s = TextUtils.ellipsize(text, paint, room, TextUtils.TruncateAt.END);
        canvas.drawText(s, 0, s.length(), x, baseline, paint);
    }
}
