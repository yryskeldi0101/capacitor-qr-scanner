// ===================== QRScanLineOverlayView.java =====================
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
    private ValueAnimator animator;
    private boolean isPaused = false;

    private float lineWidthFactor = 0.90f;
    private float bottomFactor = 0.73f;
    private float lineHeightPx;

    private float statusBarFactor = 1.20f;
    private float extraTopDp = 100f;

    private long durationMs = 2000;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float currentY;

    private float leftX, rightX;
    private float yTop, yBottom;

    private static final int IOS_SYSTEM_BLUE = 0xFF007AFF;

    private float pausedY = Float.NaN;

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

        lineHeightPx = 2f * d;

        linePaint.setStyle(Paint.Style.FILL);
        glowPaint1.setStyle(Paint.Style.FILL);
        glowPaint2.setStyle(Paint.Style.FILL);

        setLayerType(LAYER_TYPE_SOFTWARE, null);

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

        LinearGradient g = new LinearGradient(
                leftX, 0, rightX, 0,
                new int[]{0x00000000, IOS_SYSTEM_BLUE, 0x00000000},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        linePaint.setShader(g);

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

        if (isPaused) {
            if (Float.isNaN(pausedY)) pausedY = yTop;
            currentY = clamp(pausedY, yTop, yBottom);
            postInvalidateOnAnimation();
            return;
        }

        restartAnimatorFrom(Float.NaN);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float half = lineHeightPx / 2f;
        float top = currentY - half;
        float bottom = currentY + half;

        float glowExtra1 = lineHeightPx * 1.2f;
        float glowExtra2 = lineHeightPx * 2.2f;

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

    private void restartAnimatorFrom(float startY) {
        stopInternal(false);

        if (getWidth() == 0 || getHeight() == 0) return;
        if (yBottom <= yTop + 20) return;

        float begin = Float.isNaN(startY) ? yTop : clamp(startY, yTop, yBottom);
        currentY = begin;
        postInvalidateOnAnimation();

        animator = ValueAnimator.ofFloat(begin, yBottom);
        animator.setDuration(durationMs);

        if (Build.VERSION.SDK_INT >= 21) {
            animator.setInterpolator(new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f));
        } else {
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
        }

        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);

        animator.addUpdateListener(a -> {
            if (isPaused) return;
            currentY = (float) a.getAnimatedValue();
            postInvalidateOnAnimation();
        });

        animator.start();
    }

    public void start() {
        isPaused = false;
        pausedY = Float.NaN;
        post(() -> {
            if (getWidth() > 0 && getHeight() > 0) restartAnimatorFrom(Float.NaN);
            else post(this::start);
        });
    }

    public void pause() {
        isPaused = true;
        pausedY = currentY;

        if (animator != null) {
            if (Build.VERSION.SDK_INT >= 19) {
                animator.pause();
            } else {
                animator.cancel();
            }
        }
        postInvalidateOnAnimation();
    }

    public void resume() {
        if (!isPaused) return;

        isPaused = false;

        if (animator != null) {
            if (Build.VERSION.SDK_INT >= 19) {
                animator.resume();
                return;
            }
        }

        float startY = Float.isNaN(pausedY) ? currentY : pausedY;
        pausedY = Float.NaN;
        restartAnimatorFrom(startY);
    }

    public void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean resetPos) {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator = null;
        }
        if (resetPos) {
            isPaused = false;
            pausedY = Float.NaN;
        }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
