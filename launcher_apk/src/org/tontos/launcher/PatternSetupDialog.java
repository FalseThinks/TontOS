package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * AMOLED Monospace Modal Dialog for configuring lock screen pattern.
 * Guides user through 2-step setup: Draw -> Confirm.
 */
public final class PatternSetupDialog {

    public interface OnPatternSetupListener {
        void onPatternConfigured();
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public static void show(final Activity activity, final ThemeTokens tokens, final String langCode, final OnPatternSetupListener listener) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(tokens.bg);
        layout.setPadding(dpToPx(activity, 20), dpToPx(activity, 24), dpToPx(activity, 20), dpToPx(activity, 20));

        // Tactile border card
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(tokens.bg);
        cardBg.setStroke(dpToPx(activity, 1), tokens.border);
        cardBg.setCornerRadius(dpToPx(activity, 8));
        layout.setBackground(cardBg);

        // Title
        TextView titleView = new TextView(activity);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleView.setTextSize(14f);
        titleView.setTextColor(tokens.accent);
        titleView.setText(I18n.get(I18n.KEY_LOCK_SETUP_PATTERN, langCode));
        titleView.setGravity(Gravity.CENTER);
        layout.addView(titleView);

        // Instruction / Step Prompt
        final TextView promptView = new TextView(activity);
        promptView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        promptView.setTextSize(12f);
        promptView.setTextColor(tokens.textMuted);
        promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_DRAW_HINT, langCode));
        promptView.setGravity(Gravity.CENTER);
        promptView.setPadding(0, dpToPx(activity, 10), 0, dpToPx(activity, 8));
        layout.addView(promptView);

        // Pattern Canvas View
        final PatternView patternView = new PatternView(activity, tokens);
        LinearLayout.LayoutParams patternLp = new LinearLayout.LayoutParams(
            dpToPx(activity, 260), dpToPx(activity, 260));
        patternLp.setMargins(0, dpToPx(activity, 6), 0, dpToPx(activity, 12));
        layout.addView(patternView, patternLp);

        // Bottom Button Row
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        TextView resetBtn = createButton(activity, tokens, I18n.get(I18n.KEY_RESET, langCode));
        TextView cancelBtn = createButton(activity, tokens, I18n.get(I18n.KEY_CANCEL, langCode));

        btnRow.addView(resetBtn);
        btnRow.addView(cancelBtn);
        layout.addView(btnRow);

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // Logic state
        final List<Integer> firstPattern = new ArrayList<>();
        final boolean[] inConfirmStep = new boolean[]{false};

        patternView.setOnPatternListener(new PatternView.OnPatternListener() {
            @Override
            public void onPatternDetected(List<Integer> pattern) {
                if (pattern.size() < 4) {
                    promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_TOO_SHORT, langCode));
                    promptView.setTextColor(Color.parseColor("#FF5252"));
                    patternView.setDisplayMode(PatternView.DisplayMode.ERROR);
                    LockSecurityManager.vibrateError(activity);
                    return;
                }

                if (!inConfirmStep[0]) {
                    firstPattern.clear();
                    firstPattern.addAll(pattern);
                    inConfirmStep[0] = true;
                    promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_CONFIRM_HINT, langCode));
                    promptView.setTextColor(tokens.accent);
                    patternView.clearPattern();
                } else {
                    if (pattern.equals(firstPattern)) {
                        patternView.setDisplayMode(PatternView.DisplayMode.CORRECT);
                        LockSecurityManager.vibrateSuccess(activity);
                        LockSecurityManager.setPattern(activity, pattern);
                        promptView.setText(I18n.get(I18n.KEY_DONE, langCode));
                        promptView.setTextColor(tokens.accent);

                        promptView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                                if (listener != null) {
                                    listener.onPatternConfigured();
                                }
                            }
                        }, 400L);
                    } else {
                        patternView.setDisplayMode(PatternView.DisplayMode.ERROR);
                        LockSecurityManager.vibrateError(activity);
                        promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_MISMATCH, langCode));
                        promptView.setTextColor(Color.parseColor("#FF5252"));

                        promptView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                firstPattern.clear();
                                inConfirmStep[0] = false;
                                promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_DRAW_HINT, langCode));
                                promptView.setTextColor(tokens.textMuted);
                                patternView.clearPattern();
                            }
                        }, 900L);
                    }
                }
            }

            @Override
            public void onPatternCleared() {}
        });

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                firstPattern.clear();
                inConfirmStep[0] = false;
                promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_DRAW_HINT, langCode));
                promptView.setTextColor(tokens.textMuted);
                patternView.clearPattern();
            }
        });

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private static TextView createButton(Context context, ThemeTokens tokens, String text) {
        TextView btn = new TextView(context);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setTextSize(12f);
        btn.setTextColor(tokens.textPrimary);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dpToPx(context, 16), dpToPx(context, 10), dpToPx(context, 16), dpToPx(context, 10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(tokens.controlBgNormal);
        bg.setStroke(dpToPx(context, 1), tokens.border);
        bg.setCornerRadius(dpToPx(context, 4));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.setMargins(dpToPx(context, 6), 0, dpToPx(context, 6), 0);
        btn.setLayoutParams(lp);

        return btn;
    }
}
