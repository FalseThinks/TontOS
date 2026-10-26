package org.tontos.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * AMOLED Monospace Modal Dialog for configuring lock screen PIN.
 * Guides user through 2-step setup: Enter PIN -> Confirm PIN.
 */
public final class PinSetupDialog {

    public interface OnPinSetupListener {
        void onPinConfigured();
    }

    private PinSetupDialog() {}

    public static void show(final Activity activity, final ThemeTokens tokens, final String langCode, final OnPinSetupListener listener) {
        if (activity == null || activity.isFinishing()) return;

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_LOCK_SETUP_PIN, langCode));
        dlg.setCancelable(true);

        final float density = activity.getResources().getDisplayMetrics().density;

        // Instruction / Step Prompt
        final TextView promptView = new TextView(activity);
        promptView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        promptView.setTextSize(13f);
        promptView.setTextColor(tokens.textMuted);
        promptView.setText(I18n.get(I18n.KEY_LOCK_PIN_ENTER_HINT, langCode));
        promptView.setGravity(Gravity.CENTER);
        promptView.setPadding(0, (int) (8 * density), 0, (int) (12 * density));
        dlg.getContentContainer().addView(promptView);

        // PIN Keypad View
        final PinUnlockView pinView = new PinUnlockView(activity, tokens);
        pinView.setRequiredLength(4);
        dlg.getContentContainer().addView(pinView);

        // Logic state
        final String[] firstPin = new String[]{null};
        final boolean[] inConfirmStep = new boolean[]{false};

        pinView.setOnPinListener(new PinUnlockView.OnPinListener() {
            @Override
            public void onPinEntered(String pin) {
                if (pin.length() < 4) {
                    promptView.setText(I18n.get(I18n.KEY_LOCK_PIN_TOO_SHORT, langCode));
                    promptView.setTextColor(Color.parseColor("#FF5252"));
                    LockSecurityManager.vibrateError(activity);
                    return;
                }

                if (!inConfirmStep[0]) {
                    firstPin[0] = pin;
                    inConfirmStep[0] = true;
                    promptView.setText(I18n.get(I18n.KEY_LOCK_PIN_CONFIRM_HINT, langCode));
                    promptView.setTextColor(tokens.accent);
                    pinView.clearPin();
                } else {
                    if (pin.equals(firstPin[0])) {
                        LockSecurityManager.vibrateSuccess(activity);
                        LockSecurityManager.setPin(activity, pin);
                        promptView.setText(I18n.get(I18n.KEY_DONE, langCode));
                        promptView.setTextColor(tokens.accent);

                        promptView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                dlg.dismiss();
                                if (listener != null) {
                                    listener.onPinConfigured();
                                }
                            }
                        }, 400L);
                    } else {
                        LockSecurityManager.vibrateError(activity);
                        pinView.showError(I18n.get(I18n.KEY_LOCK_PIN_MISMATCH, langCode));
                        promptView.setText(I18n.get(I18n.KEY_LOCK_PIN_MISMATCH, langCode));
                        promptView.setTextColor(Color.parseColor("#FF5252"));

                        promptView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                firstPin[0] = null;
                                inConfirmStep[0] = false;
                                promptView.setText(I18n.get(I18n.KEY_LOCK_PIN_ENTER_HINT, langCode));
                                promptView.setTextColor(tokens.textMuted);
                                pinView.clearPin();
                            }
                        }, 900L);
                    }
                }
            }

            @Override
            public void onPinCleared() {}
        });

        dlg.addActionButton(I18n.get(I18n.KEY_RESET, langCode), tokens.textPrimary, tokens.border, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                firstPin[0] = null;
                inConfirmStep[0] = false;
                promptView.setText(I18n.get(I18n.KEY_LOCK_PIN_ENTER_HINT, langCode));
                promptView.setTextColor(tokens.textMuted);
                pinView.clearPin();
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
