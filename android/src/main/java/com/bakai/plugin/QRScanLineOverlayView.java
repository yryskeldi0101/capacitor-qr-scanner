package com.bakai.plugin;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

public final class QRScanLineOverlayView extends View {

    private float lineWidthFactor = 0.90f;
    private float bottomFactor = 0.73f;
    private float lineHeightPx;

    private float statusBarFactor = 1.20f;
    private float extraTopDp = 100f;

    private long durationMs = 2000;

    // base paint (градиент линии)
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // glow paints (аура как на iOS)
    private final Paint glowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ValueAnimator animator;
    private float currentY;

    private float leftX, rightX;
    private float yTop, yBottom;

    // ✅ iOS systemBlue = #007AFF
    private static final int IOS_SYSTEM_BLUE = 0xFF007AFF;

    public QRScanLineOverlayView(Context context) {
        super(context);
        init();
    }

    public QRScanLineOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        float d = getResources().getDisplayMetrics().density;

        // толщина линии (3dp)
        lineHeightPx = 2f * d;

        linePaint.setStyle(Paint.Style.FILL);
        glowPaint1.setStyle(Paint.Style.FILL);
        glowPaint2.setStyle(Paint.Style.FILL);

        // BlurMaskFilter требует software layer
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // ✅ shadow чуть меньше
        glowPaint1.setMaskFilter(new BlurMaskFilter(2f * d, BlurMaskFilter.Blur.NORMAL));
        glowPaint2.setMaskFilter(new BlurMaskFilter(4f * d, BlurMaskFilter.Blur.NORMAL));
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float d = getResources().getDisplayMetrics().density;

        float lineW = w * lineWidthFactor;
        leftX = (w - lineW) / 2f;
        rightX = leftX + lineW;

        int sb = getStatusBarHeight();
        yTop = (sb * statusBarFactor) + (extraTopDp * d);
        yBottom = h * bottomFactor;

        // градиент: прозрачный -> iOS systemBlue -> прозрачный
        LinearGradient g = new LinearGradient(
                leftX, 0, rightX, 0,
                new int[]{0x00000000, IOS_SYSTEM_BLUE, 0x00000000},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        linePaint.setShader(g);

        // glow градиенты (мягче)
        LinearGradient gGlow1 = new LinearGradient(
                leftX, 0, rightX, 0,
                new int[]{0x00000000, 0xCC007AFF, 0x00000000},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        glowPaint1.setShader(gGlow1);

        LinearGradient gGlow2 = new LinearGradient(
                leftX, 0, rightX, 0,
                new int[]{0x00000000, 0x88007AFF, 0x00000000},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        glowPaint2.setShader(gGlow2);

        restartAnimator();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float half = lineHeightPx / 2f;
        float top = currentY - half;
        float bottom = currentY + half;

        // ✅ glow: чуть меньше расползание
        float glowExtra1 = lineHeightPx * 1.2f;
        float glowExtra2 = lineHeightPx * 2.2f;

        // roundRect “капсула”
        float r = 999f;

        canvas.drawRoundRect(
                leftX, top - glowExtra2, rightX, bottom + glowExtra2,
                r, r, glowPaint2
        );
        canvas.drawRoundRect(
                leftX, top - glowExtra1, rightX, bottom + glowExtra1,
                r, r, glowPaint1
        );
        canvas.drawRoundRect(
                leftX, top, rightX, bottom,
                r, r, linePaint
        );
    }

    private void restartAnimator() {
        stop();

        if (getWidth() == 0 || getHeight() == 0) return;
        if (yBottom <= yTop + 20) return;

        currentY = yTop;

        animator = ValueAnimator.ofFloat(yTop, yBottom);
        animator.setDuration(durationMs);

        // iOS-like easeInOut
        if (Build.VERSION.SDK_INT >= 21) {
            animator.setInterpolator(new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f));
        } else {
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
        }

        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);

        animator.addUpdateListener(a -> {
            currentY = (float) a.getAnimatedValue();
            postInvalidateOnAnimation();
        });

        animator.start();
    }

    public void start() {
        post(() -> {
            if (getWidth() > 0 && getHeight() > 0) restartAnimator();
            else post(this::start);
        });
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator = null;
        }
    }
}
