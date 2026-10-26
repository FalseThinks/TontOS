package org.tontos.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
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

    private PatternSetupDialog() {}

    public static void show(final Activity activity, final ThemeTokens tokens, final String langCode, final OnPatternSetupListener listener) {
        if (activity == null || activity.isFinishing()) return;

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_LOCK_SETUP_PATTERN, langCode));
        dlg.setCancelable(true);

        final float density = activity.getResources().getDisplayMetrics().density;
        final int dp260 = (int) (260 * density + 0.5f);

        // Instruction / Step Prompt
        final TextView promptView = new TextView(activity);
        promptView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        promptView.setTextSize(13f);
        promptView.setTextColor(tokens.textMuted);
        promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_DRAW_HINT, langCode));
        promptView.setGravity(Gravity.CENTER);
        promptView.setPadding(0, (int) (8 * density), 0, (int) (12 * density));
        dlg.getContentContainer().addView(promptView);

        // Pattern Canvas View Container (centered)
        LinearLayout patternHolder = new LinearLayout(activity);
        patternHolder.setGravity(Gravity.CENTER);
        final PatternView patternView = new PatternView(activity, tokens);
        LinearLayout.LayoutParams patternLp = new LinearLayout.LayoutParams(dp260, dp260);
        patternHolder.addView(patternView, patternLp);
        dlg.getContentContainer().addView(patternHolder);

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
                                dlg.dismiss();
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

        dlg.addActionButton(I18n.get(I18n.KEY_RESET, langCode), tokens.textPrimary, tokens.border, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                firstPattern.clear();
                inConfirmStep[0] = false;
                promptView.setText(I18n.get(I18n.KEY_LOCK_PATTERN_DRAW_HINT, langCode));
                promptView.setTextColor(tokens.textMuted);
                patternView.clearPattern();
            }
        });

        dlg.addActionButton(I18n.get(I18n.KEY_CANCEL, langCode), tokens.textMuted, tokens.border, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });

        dlg.show();
    }
}
