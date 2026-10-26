package org.tontos.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Minimalist AMOLED Monospace Numeric Keypad View for TontOS PIN Unlock.
 * Features tactile hit targets, digit bullet indicators, and haptic feedback.
 */
public class PinUnlockView extends LinearLayout {

    public interface OnPinListener {
        void onPinEntered(String pin);
        void onPinCleared();
    }

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    private static final int MIN_TOUCH_TARGET_DP = 48;

    private final StringBuilder currentPin = new StringBuilder();
    private ThemeTokens tokens;
    private OnPinListener listener;
    private int requiredLength = 4;

    private TextView indicatorView;
    private TextView errorView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    public PinUnlockView(Context context) {
        super(context);
        init();
    }

    public PinUnlockView(Context context, ThemeTokens tokens) {
        super(context);
        this.tokens = tokens;
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. PIN Digit Bullets Indicator
        indicatorView = new TextView(getContext());
        indicatorView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        indicatorView.setTextSize(22f);
        indicatorView.setGravity(Gravity.CENTER);
        indicatorView.setPadding(0, dpToPx(8), 0, dpToPx(4));
        addView(indicatorView);

        // 2. Error / Status Text
        errorView = new TextView(getContext());
        errorView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        errorView.setTextSize(12f);
        errorView.setTextColor(Color.parseColor("#FF5252"));
        errorView.setGravity(Gravity.CENTER);
        errorView.setVisibility(View.GONE);
        addView(errorView);

        // 3. Numeric Keypad (4 rows)
        buildKeypad();
        updateIndicator();
    }

    public void setTokens(ThemeTokens tokens) {
        this.tokens = tokens;
        removeAllViews();
        init();
    }

    public void setRequiredLength(int length) {
        this.requiredLength = Math.max(MIN_PIN_LENGTH, Math.min(length, MAX_PIN_LENGTH));
        updateIndicator();
    }

    public void setOnPinListener(OnPinListener listener) {
        this.listener = listener;
    }

    public void clearPin() {
        currentPin.setLength(0);
        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }
        updateIndicator();
        if (listener != null) {
            listener.onPinCleared();
        }
    }

    public void showError(String message) {
        if (errorView != null) {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        }
        if (indicatorView != null) {
            indicatorView.setTextColor(Color.parseColor("#FF5252"));
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                clearPin();
            }
        }, 800L);
    }

    private void updateIndicator() {
        if (indicatorView == null) return;
        int accent = (tokens != null) ? tokens.accent : Color.parseColor("#44DD88");
        indicatorView.setTextColor(accent);

        StringBuilder sb = new StringBuilder();
        int entered = currentPin.length();
        int total = Math.max(requiredLength, entered);

        for (int i = 0; i < total; i++) {
            if (i > 0) sb.append(' ');
            sb.append(i < entered ? "●" : "○");
        }
        indicatorView.setText(sb.toString());
    }

    private void buildKeypad() {
        String[][] keys = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {"C", "0", "⌫"}
        };

        LinearLayout keypad = new LinearLayout(getContext());
        keypad.setOrientation(VERTICAL);
        keypad.setGravity(Gravity.CENTER);
        keypad.setPadding(0, dpToPx(8), 0, dpToPx(8));

        for (String[] row : keys) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            for (final String key : row) {
                TextView keyBtn = createKeyButton(key);
                keyBtn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        handleKeyPress(key);
                    }
                });
                rowLayout.addView(keyBtn);
            }
            keypad.addView(rowLayout);
        }
        addView(keypad);
    }

    private void handleKeyPress(String key) {
        if ("C".equals(key)) {
            clearPin();
        } else if ("⌫".equals(key)) {
            if (currentPin.length() > 0) {
                currentPin.deleteCharAt(currentPin.length() - 1);
                if (errorView != null) errorView.setVisibility(View.GONE);
                updateIndicator();
            }
        } else {
            if (currentPin.length() < MAX_PIN_LENGTH) {
                currentPin.append(key);
                if (errorView != null) errorView.setVisibility(View.GONE);
                updateIndicator();

                if (currentPin.length() == requiredLength && listener != null) {
                    listener.onPinEntered(currentPin.toString());
                }
            }
        }
    }

    private TextView createKeyButton(String text) {
        TextView btn = new TextView(getContext());
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setTextSize(18f);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);

        int primaryColor = (tokens != null) ? tokens.textPrimary : Color.parseColor("#E5E7EB");
        int bgColor = (tokens != null) ? tokens.controlBgNormal : Color.parseColor("#0D0D0D");
        int borderColor = (tokens != null) ? tokens.border : Color.parseColor("#1B261F");

        btn.setTextColor(primaryColor);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setStroke(dpToPx(1), borderColor);
        gd.setCornerRadius(dpToPx(6));
        btn.setBackground(gd);

        int sizePx = dpToPx(60);
        LayoutParams lp = new LayoutParams(sizePx, sizePx);
        lp.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        btn.setLayoutParams(lp);

        return btn;
    }
}
