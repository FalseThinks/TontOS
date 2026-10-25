package org.tontos.launcher;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

/**
 * Reusable AMOLED Fullscreen Boolean / Confirmation Modal Dialog.
 * Provides unified monospace styling, tactile touch feedback, and tokens integration.
 */
public final class BooleanSelectorDialog {

    public interface OnBooleanSelectedListener {
        void onSelected(boolean confirmed);
    }

    private BooleanSelectorDialog() {}

    public static void show(
        final Activity activity,
        final ThemeTokens tokens,
        final String title,
        final String message,
        final String confirmLabel,
        final String cancelLabel,
        final OnBooleanSelectedListener listener
    ) {
        if (activity == null || activity.isFinishing()) return;

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, title);

        TextView messageView = new TextView(activity);
        messageView.setText(message != null ? message : "");
        messageView.setTextColor(tokens.textPrimary);
        messageView.setTextSize(14 * dlg.getFontScale());
        messageView.setTypeface(Typeface.MONOSPACE);
        messageView.setLineSpacing(0, 1.3f);
        messageView.setPadding(0, (int) (8 * dlg.getDensity()), 0, (int) (16 * dlg.getDensity()));
        dlg.getContentContainer().addView(messageView);

        boolean hasCancel = (cancelLabel != null && !cancelLabel.trim().isEmpty());
        if (hasCancel) {
            dlg.addActionButton(
                TactileUi.ensureBrackets(cancelLabel, "[ CANCEL ]"),
                tokens.textMuted,
                tokens.border,
                false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dlg.dismiss();
                        if (listener != null) listener.onSelected(false);
                    }
                }
            );
        }

        dlg.addActionButton(
            TactileUi.ensureBrackets(confirmLabel, "[ CONFIRM ]"),
            tokens.accent,
            tokens.accent,
            !hasCancel,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                    if (listener != null) listener.onSelected(true);
                }
            }
        );

        dlg.show();
    }
}
