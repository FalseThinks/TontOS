package org.tontos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimalist AMOLED 3x3 Tactile Pattern View for TontOS.
 * Renders anti-aliased dot nodes, vector connecting lines, and touch detection.
 */
public class PatternView extends View {

    public enum DisplayMode {
        NORMAL,
        CORRECT,
        ERROR
    }

    public interface OnPatternListener {
        void onPatternDetected(List<Integer> pattern);
        void onPatternCleared();
    }

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();

    private final float[] dotX = new float[9];
    private final float[] dotY = new float[9];
    private final List<Integer> selectedDots = new ArrayList<>(9);

    private ThemeTokens tokens;
    private DisplayMode displayMode = DisplayMode.NORMAL;
    private OnPatternListener listener;
    private boolean isDrawing = false;
    private float currentTouchX = -1f;
    private float currentTouchY = -1f;

    private float dotRadius;
    private float selectedDotRadius;
    private float ringRadius;
    private float hitRadius;

    private final Handler autoClearHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoClearRunnable = new Runnable() {
        @Override
        public void run() {
            clearPattern();
        }
    };

    public PatternView(Context context) {
        super(context);
        init();
    }

    public PatternView(Context context, ThemeTokens tokens) {
        super(context);
        this.tokens = tokens;
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        dotRadius = 6f * density;
        selectedDotRadius = 9f * density;
        ringRadius = 24f * density;
        hitRadius = 36f * density;

        dotPaint.setStyle(Paint.Style.FILL);
        selectedDotPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.FILL);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        updateColors();
    }

    public void setTokens(ThemeTokens tokens) {
        this.tokens = tokens;
        updateColors();
        invalidate();
    }

    public void setOnPatternListener(OnPatternListener listener) {
        this.listener = listener;
    }

    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode;
        updateColors();
        invalidate();

        if (mode == DisplayMode.ERROR) {
            autoClearHandler.removeCallbacks(autoClearRunnable);
            autoClearHandler.postDelayed(autoClearRunnable, 800L);
        }
    }

    public void clearPattern() {
        autoClearHandler.removeCallbacks(autoClearRunnable);
        selectedDots.clear();
        linePath.reset();
        currentTouchX = -1f;
        currentTouchY = -1f;
        displayMode = DisplayMode.NORMAL;
        updateColors();
        invalidate();
        if (listener != null) {
            listener.onPatternCleared();
        }
    }

    private void updateColors() {
        int normalColor = (tokens != null) ? tokens.textMuted : Color.parseColor("#6B7280");
        int accentColor = (tokens != null) ? tokens.accent : Color.parseColor("#44DD88");
        int errorColor = Color.parseColor("#FF5252");

        dotPaint.setColor(normalColor);

        int activeColor = (displayMode == DisplayMode.ERROR) ? errorColor : accentColor;
        selectedDotPaint.setColor(activeColor);

        // Semi-transparent ring
        int ringAlpha = ((activeColor & 0x00FFFFFF) | 0x33000000);
        ringPaint.setColor(ringAlpha);

        linePaint.setColor(activeColor);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = (height > 0) ? Math.min(width, height) : width;
        if (size <= 0) size = (int) (280 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float side = Math.min(w, h);
        float padding = side * 0.16f;
        float step = (side - 2 * padding) / 2.0f;

        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            dotX[i] = padding + col * step;
            dotY[i] = padding + row * step;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                autoClearHandler.removeCallbacks(autoClearRunnable);
                selectedDots.clear();
                displayMode = DisplayMode.NORMAL;
                updateColors();

                int hit = findHitDot(x, y);
                if (hit != -1) {
                    addDot(hit);
                    isDrawing = true;
                    currentTouchX = x;
                    currentTouchY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDrawing) {
                    currentTouchX = x;
                    currentTouchY = y;
                    int candidate = findHitDot(x, y);
                    if (candidate != -1 && !selectedDots.contains(candidate)) {
                        int last = selectedDots.get(selectedDots.size() - 1);
                        int mid = getIntermediateDot(last, candidate);
                        if (mid != -1 && !selectedDots.contains(mid)) {
                            addDot(mid);
                        }
                        addDot(candidate);
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDrawing) {
                    isDrawing = false;
                    currentTouchX = -1f;
                    currentTouchY = -1f;
                    invalidate();

                    if (listener != null && !selectedDots.isEmpty()) {
                        listener.onPatternDetected(new ArrayList<>(selectedDots));
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void addDot(int index) {
        selectedDots.add(index);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private int findHitDot(float x, float y) {
        for (int i = 0; i < 9; i++) {
            float dx = x - dotX[i];
            float dy = y - dotY[i];
            if ((dx * dx + dy * dy) <= (hitRadius * hitRadius)) {
                return i;
            }
        }
        return -1;
    }

    private int getIntermediateDot(int a, int b) {
        if (a > b) {
            int tmp = a; a = b; b = tmp;
        }
        if (a == 0 && b == 2) return 1;
        if (a == 3 && b == 5) return 4;
        if (a == 6 && b == 8) return 7;
        if (a == 0 && b == 6) return 3;
        if (a == 1 && b == 7) return 4;
        if (a == 2 && b == 8) return 5;
        if (a == 0 && b == 8) return 4;
        if (a == 2 && b == 6) return 4;
        return -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Draw connecting lines between selected dots
        if (!selectedDots.isEmpty()) {
            linePath.reset();
            linePath.moveTo(dotX[selectedDots.get(0)], dotY[selectedDots.get(0)]);
            for (int i = 1; i < selectedDots.size(); i++) {
                linePath.lineTo(dotX[selectedDots.get(i)], dotY[selectedDots.get(i)]);
            }
            if (isDrawing && currentTouchX >= 0 && currentTouchY >= 0) {
                linePath.lineTo(currentTouchX, currentTouchY);
            }
            canvas.drawPath(linePath, linePaint);
        }

        // 2. Draw 9 dots (normal or selected with halo ring)
        for (int i = 0; i < 9; i++) {
            float cx = dotX[i];
            float cy = dotY[i];

            if (selectedDots.contains(i)) {
                canvas.drawCircle(cx, cy, ringRadius, ringPaint);
                canvas.drawCircle(cx, cy, selectedDotRadius, selectedDotPaint);
            } else {
                canvas.drawCircle(cx, cy, dotRadius, dotPaint);
            }
        }
    }
}
