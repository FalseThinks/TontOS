package org.tontos.launcher;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Mindful Pause / Intentional Friction Dialog.
 * Enforces a deliberate countdown (3s - 15s) with mindful breathing cue
 * before launching an app.
 * Built with AmoledDialog template.
 */
public final class FrictionDialog {

    private FrictionDialog() {}

    public static void show(
        final Context context,
        final ThemeTokens tokens,
        final String appLabel,
        final int durationSecs,
        final Runnable onProceed
    ) {
        if (context == null || durationSecs <= 0) {
            if (onProceed != null) onProceed.run();
            return;
        }

        final String cleanLabel = (appLabel != null && !appLabel.trim().isEmpty()) ? appLabel.trim() : "App";

        String langCode = "en";
        try {
            langCode = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}

        final AmoledDialog dlg = new AmoledDialog(context, tokens, I18n.get(I18n.KEY_FRICTION_HEADER, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        // Center mindful breathing container
        LinearLayout centerContainer = new LinearLayout(context);
        centerContainer.setOrientation(LinearLayout.VERTICAL);
        centerContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        centerLp.setMargins(0, (int) (32 * density), 0, (int) (32 * density));
        centerContainer.setLayoutParams(centerLp);

        // Breathing Focus Indicator
        final TextView breathingGraphic = new TextView(context);
        breathingGraphic.setText("(  *  )");
        breathingGraphic.setTextColor(themeTokens.accentMuted);
        breathingGraphic.setTextSize(26 * fontScale);
        breathingGraphic.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        breathingGraphic.setGravity(Gravity.CENTER);
        breathingGraphic.setPadding(0, 0, 0, (int) (24 * density));
        centerContainer.addView(breathingGraphic);

        // Monospace Breathing Text: "Respiración consciente... Abriendo [appLabel] en %ds..."
        final TextView breathingText = new TextView(context);
        breathingText.setTextColor(themeTokens.textPrimary);
        breathingText.setTextSize(15 * fontScale);
        breathingText.setTypeface(Typeface.MONOSPACE);
        breathingText.setGravity(Gravity.CENTER);
        breathingText.setPadding((int) (16 * density), 0, (int) (16 * density), 0);
        centerContainer.addView(breathingText);

        // Large Countdown Number
        final TextView counterView = new TextView(context);
        counterView.setTextColor(themeTokens.accent);
        counterView.setTextSize(40 * fontScale);
        counterView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        counterView.setGravity(Gravity.CENTER);
        counterView.setPadding(0, (int) (18 * density), 0, 0);
        centerContainer.addView(counterView);

        dlg.getContentContainer().addView(centerContainer);

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
                        if (dlg.getDialog().isShowing()) {
                            dlg.dismiss();
                        }
                    } catch (Throwable ignored) {}
                    if (onProceed != null) {
                        onProceed.run();
                    }
                }
            }
        };

        // Action Buttons: [ CANCELAR ] and [ ABRIR AHORA ]
        dlg.addActionButton(
            I18n.get(I18n.KEY_FRICTION_CANCEL, langCode),
            themeTokens.navAccent,
            themeTokens.border,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isProceeding[0] = false;
                    timer.cancel();
                    try {
                        dlg.dismiss();
                    } catch (Throwable ignored) {}
                }
            }
        );

        dlg.addActionButton(
            I18n.get(I18n.KEY_FRICTION_OPEN_NOW, langCode),
            themeTokens.accent,
            themeTokens.accent,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isProceeding[0]) {
                        isProceeding[0] = true;
                        timer.cancel();
                        try {
                            dlg.dismiss();
                        } catch (Throwable ignored) {}
                        if (onProceed != null) {
                            onProceed.run();
                        }
                    }
                }
            }
        );

        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                timer.cancel();
            }
        });

        dlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface d) {
                isProceeding[0] = false;
                timer.cancel();
            }
        });

        breathingText.setText(I18n.format(I18n.KEY_FRICTION_BREATHE_FMT, finalLangCode, cleanLabel, durationSecs));
        counterView.setText(durationSecs + "s");

        dlg.show();
        timer.start();
    }
}
