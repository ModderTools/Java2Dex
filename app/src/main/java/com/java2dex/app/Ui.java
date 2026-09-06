package com.java2dex.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** UI toolkit : colors, shapes, animations, helpers — all in pure Java */
public class Ui {

    public static final int GREEN       = 0xFF16A34A;
    public static final int GREEN_DARK  = 0xFF15803D;
    public static final int GREEN_DEEP  = 0xFF14532D;
    public static final int GREEN_LIGHT = 0xFFDCFCE7;
    public static final int GREEN_BG    = 0xFFF0FDF4;
    public static final int WHITE       = 0xFFFFFFFF;
    public static final int TEXT        = 0xFF0F172A;
    public static final int TEXT_SUB    = 0xFF64748B;
    public static final int RED         = 0xFFDC2626;
    public static final int RED_LIGHT   = 0xFFFEE2E2;
    public static final int STROKE      = 0xFFE2E8F0;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable fill(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable outline(int fillColor, int strokeColor,
                                           float radiusDp, float strokeDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return g;
    }

    public static GradientDrawable gradient(int from, int to, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{from, to});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static Drawable ripple(Context c, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), content, null);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF94A3B8);
        e.setBackground(outline(WHITE, STROKE, 12, 1.2f, c));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setSingleLine(true);
        return e;
    }

    public static TextView button(Context c, String label, int bg) {
        TextView t = text(c, label, 14.5f, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(c, fill(bg, 14, c)));
        t.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        pressScale(t, 0.97f);
        return t;
    }

    public static void pressScale(final View v, final float down) {
        v.setClickable(true);
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(down).scaleY(down).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    break;
            }
            return false;
        });
    }

    public static void copy(Context c, String label, String value) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            toast(c, "Copied to clipboard");
        }
    }

    public static void toast(Context c, String m) {
        Toast.makeText(c, m, Toast.LENGTH_SHORT).show();
    }

    public static void popIn(View v, long delay) {
        v.setAlpha(0f);
        v.setScaleX(0.85f);
        v.setScaleY(0.85f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.15f)).start();
    }

    public static void riseIn(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 24));
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(360)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public static void shake(View v) {
        float d = dp(v.getContext(), 10);
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, d, -d, d * 0.6f, -d * 0.4f, 0f)
                .setDuration(420).start();
    }

    public static void countUp(final TextView t, int to) {
        ValueAnimator a = ValueAnimator.ofInt(0, to);
        a.setDuration(650);
        a.addUpdateListener(anim -> {
            int val = (Integer) anim.getAnimatedValue();
            t.setText(String.valueOf(val));
        });
        a.start();
    }

    public static String size(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(Locale.US, "%.2f MB", bytes / 1048576f);
    }

    /** Animated success checkmark (custom drawn) */
    public static class SuccessView extends View {

        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path checkPath = new Path();
        private final PathMeasure pm = new PathMeasure();
        private final float box;
        private float progress = 0f;

        public SuccessView(Context c) {
            super(c);
            halo.setColor(GREEN_LIGHT);
            circle.setColor(GREEN);
            check.setColor(WHITE);
            check.setStyle(Paint.Style.STROKE);
            check.setStrokeWidth(dp(c, 5));
            check.setStrokeCap(Paint.Cap.ROUND);
            check.setStrokeJoin(Paint.Join.ROUND);
            box = dp(c, 52);
            checkPath.moveTo(box * 0.30f, box * 0.52f);
            checkPath.lineTo(box * 0.44f, box * 0.66f);
            checkPath.lineTo(box * 0.72f, box * 0.36f);
            pm.setPath(checkPath, false);
        }

        public void start() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(950);
            a.setStartDelay(120);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.addUpdateListener(anim -> {
                progress = (Float) anim.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = Math.min(getWidth(), getHeight()) / 2f - dp(getContext(), 2);
            canvas.drawCircle(cx, cy, maxR, halo);
            float grow = Math.min(1f, progress / 0.45f);
            canvas.drawCircle(cx, cy, maxR * (0.25f + 0.75f * grow), circle);
            float cp = (progress - 0.5f) / 0.5f;
            if (cp > 0f) {
                Path dst = new Path();
                dst.rLineTo(0, 0);
                pm.getSegment(0, pm.getLength() * Math.min(1f, cp), dst, true);
                canvas.save();
                canvas.translate(cx - box / 2f, cy - box / 2f);
                canvas.drawPath(dst, check);
                canvas.restore();
            }
        }
    }
}
