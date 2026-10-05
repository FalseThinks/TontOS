package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Minimalist pure-AMOLED friction modal dialog (LightOS / Blloc Ratio pattern).
 * Provides an intentional delay / mindful breathing pause before opening distracting apps.
 */
public final class FrictionDialog {

    private FrictionDialog() {}

    /**
     * Shows a mindful pause dialog with default 5-second countdown.
     */
    public static void show(final Context context, final ThemeTokens tokens, final String appLabel, final Runnable onProceed) {
        show(context, tokens, appLabel, 5, onProceed);
    }

    /**
     * Shows a mindful pause dialog with specified countdown seconds.
     */
    public static void show(
        final Context context,
        final ThemeTokens tokens,
        final String appLabel,
        final int seconds,
        final Runnable onProceed
    ) {
        if (context == null) return;
        if (context instanceof Activity) {
            Activity act = (Activity) context;
            if (act.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && act.isDestroyed())) {
                return;
            }
        }

        final int durationSecs = (seconds > 0) ? seconds : 5;
        final String cleanLabel = (appLabel != null && !appLabel.trim().isEmpty()) ? appLabel.trim() : "App";

        String langCode = "en";
        try {
            langCode = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}

        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(tokens.bg));
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
                    window.getInsetsController().setSystemBarsAppearance(
                        tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } catch (Throwable ignored) {}
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            GrayscaleManager.applyGrayscaleToWindow(window, GrayscaleManager.isGrayscaleEnabled(context));
        }

        float density = context.getResources().getDisplayMetrics().density;
        int padH = (int) (24 * density);
        int padV = (int) (24 * density);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(padH, padV, padH, padV);

        // Header Title: PAUSA CONSCIENTE / MINDFUL PAUSE
        TextView header = new TextView(context);
        header.setText(I18n.get(I18n.KEY_FRICTION_HEADER, langCode));
        header.setTextColor(tokens.accent);
        header.setTextSize(18);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 0, 0, (int) (6 * density));
        root.addView(header);

        // Header Divider
        View divider = new View(context);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (14 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        // Center mindful breathing container
        LinearLayout centerContainer = new LinearLayout(context);
        centerContainer.setOrientation(LinearLayout.VERTICAL);
        centerContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        centerContainer.setLayoutParams(centerLp);

        // Breathing Focus Indicator
        final TextView breathingGraphic = new TextView(context);
        breathingGraphic.setText("(  *  )");
        breathingGraphic.setTextColor(tokens.accentMuted);
        breathingGraphic.setTextSize(26);
        breathingGraphic.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        breathingGraphic.setGravity(Gravity.CENTER);
        breathingGraphic.setPadding(0, 0, 0, (int) (24 * density));
        centerContainer.addView(breathingGraphic);

        // Monospace Breathing Text: "Respiración consciente... Abriendo [appLabel] en %ds..."
        final TextView breathingText = new TextView(context);
        breathingText.setTextColor(tokens.textPrimary);
        breathingText.setTextSize(15);
        breathingText.setTypeface(Typeface.MONOSPACE);
        breathingText.setGravity(Gravity.CENTER);
        breathingText.setPadding((int) (16 * density), 0, (int) (16 * density), 0);
        centerContainer.addView(breathingText);

        // Large Countdown Number
        final TextView counterView = new TextView(context);
        counterView.setTextColor(tokens.accent);
        counterView.setTextSize(40);
        counterView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        counterView.setGravity(Gravity.CENTER);
        counterView.setPadding(0, (int) (18 * density), 0, 0);
        centerContainer.addView(counterView);

        root.addView(centerContainer);

        // Bottom Action Buttons
        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnRowLp.setMargins(0, (int) (16 * density), 0, 0);
        buttonRow.setLayoutParams(btnRowLp);

        int minHeightPx = (int) (48 * density + 0.5f);

        // [ CANCELAR ]
        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(I18n.get(I18n.KEY_FRICTION_CANCEL, langCode));
        cancelBtn.setTextColor(tokens.navAccent);
        cancelBtn.setTextSize(13);
        cancelBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setMinHeight(minHeightPx);
        cancelBtn.setMinimumHeight(minHeightPx);
        cancelBtn.setPadding((int) (8 * density), (int) (12 * density), (int) (8 * density), (int) (12 * density));
        cancelBtn.setClickable(true);
        cancelBtn.setFocusable(true);
        cancelBtn.setBackground(createButtonFeedback(tokens, false, density));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cancelLp.setMargins(0, 0, (int) (6 * density), 0);
        cancelBtn.setLayoutParams(cancelLp);
        buttonRow.addView(cancelBtn);

        // [ ABRIR AHORA ]
        TextView openNowBtn = new TextView(context);
        openNowBtn.setText(I18n.get(I18n.KEY_FRICTION_OPEN_NOW, langCode));
        openNowBtn.setTextColor(tokens.accent);
        openNowBtn.setTextSize(13);
        openNowBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        openNowBtn.setGravity(Gravity.CENTER);
        openNowBtn.setMinHeight(minHeightPx);
        openNowBtn.setMinimumHeight(minHeightPx);
        openNowBtn.setPadding((int) (8 * density), (int) (12 * density), (int) (8 * density), (int) (12 * density));
        openNowBtn.setClickable(true);
        openNowBtn.setFocusable(true);
        openNowBtn.setBackground(createButtonFeedback(tokens, true, density));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        openLp.setMargins((int) (6 * density), 0, 0, 0);
        openNowBtn.setLayoutParams(openLp);
        buttonRow.addView(openNowBtn);

        root.addView(buttonRow);

        final boolean[] isProceeding = new boolean[]{false};
        final String finalLangCode = langCode;

        final CountDownTimer timer = new CountDownTimer(durationSecs * 1000L + 250L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remSecs = (int) Math.round(millisUntilFinished / 1000.0);
                if (remSecs > durationSecs) remSecs = durationSecs;
                if (remSecs < 1) remSecs = 1;

                breathingText.setText(I18n.format(I18n.KEY_FRICTION_BREATHE_FMT, finalLangCode, cleanLabel, remSecs));
                counterView.setText(remSecs + "s");

                if (remSecs % 2 == 0) {
                    breathingGraphic.setText("(  *  )");
                } else {
                    breathingGraphic.setText("[ ( * ) ]");
                }
            }

            @Override
            public void onFinish() {
                if (!isProceeding[0]) {
                    isProceeding[0] = true;
                    try {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                    } catch (Throwable ignored) {}
                    if (onProceed != null) {
                        onProceed.run();
                    }
                }
            }
        };

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isProceeding[0] = false;
                timer.cancel();
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {}
            }
        });

        openNowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isProceeding[0]) {
                    isProceeding[0] = true;
                    timer.cancel();
                    try {
                        dialog.dismiss();
                    } catch (Throwable ignored) {}
                    if (onProceed != null) {
                        onProceed.run();
                    }
                }
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                timer.cancel();
            }
        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface d) {
                isProceeding[0] = false;
                timer.cancel();
            }
        });

        breathingText.setText(I18n.format(I18n.KEY_FRICTION_BREATHE_FMT, finalLangCode, cleanLabel, durationSecs));
        counterView.setText(durationSecs + "s");

        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        timer.start();
    }

    private static StateListDrawable createButtonFeedback(ThemeTokens tokens, boolean isAccent, float density) {
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(tokens.controlBgPressed);
        pressed.setCornerRadius((int) (4 * density));
        pressed.setStroke((int) (1 * density), isAccent ? tokens.accent : tokens.border);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setCornerRadius((int) (4 * density));
        normal.setStroke((int) (1 * density), isAccent ? tokens.accentMuted : tokens.border);

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}
