package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

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

        final Dialog dlg = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dlg.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(tokens.bg));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
                    window.getInsetsController().setSystemBarsAppearance(
                        tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } catch (Throwable ignored) {}
            GrayscaleManager.applyGrayscaleToWindow(window, GrayscaleManager.isGrayscaleEnabled(activity));
        }

        final float density = activity.getResources().getDisplayMetrics().density;
        final int pad = (int) (24 * density);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(pad, pad, pad, pad);

        // Header Title
        TextView titleView = new TextView(activity);
        titleView.setText(title != null ? title.toUpperCase(Locale.ROOT) : "");
        titleView.setTextColor(tokens.accent);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(titleView);

        // Header Divider
        View divider = new View(activity);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (16 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        // Message Body (scrollable)
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        TextView messageView = new TextView(activity);
        messageView.setText(message != null ? message : "");
        messageView.setTextColor(tokens.textPrimary);
        messageView.setTextSize(14);
        messageView.setTypeface(Typeface.MONOSPACE);
        messageView.setLineSpacing(0, 1.3f);
        messageView.setPadding(0, (int) (8 * density), 0, (int) (16 * density));

        scrollView.addView(messageView);
        root.addView(scrollView);

        // Action Buttons Row
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        btnRow.setPadding(0, (int) (16 * density), 0, 0);

        // Negative / Cancel Button
        TextView cancelBtn = new TextView(activity);
        cancelBtn.setText(cancelLabel != null ? cancelLabel : "[ CANCEL ]");
        cancelBtn.setTextColor(tokens.textMuted);
        cancelBtn.setTextSize(13);
        cancelBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setMinHeight((int) (48 * density));
        cancelBtn.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));
        cancelBtn.setClickable(true);
        cancelBtn.setFocusable(true);
        cancelBtn.setBackground(createButtonBg(tokens.controlBgPressed, tokens.border, density));

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                if (listener != null) listener.onSelected(false);
            }
        });

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cancelLp.setMargins(0, 0, (int) (8 * density), 0);
        cancelBtn.setLayoutParams(cancelLp);
        btnRow.addView(cancelBtn);

        // Positive / Confirm Button
        TextView confirmBtn = new TextView(activity);
        confirmBtn.setText(confirmLabel != null ? confirmLabel : "[ CONFIRM ]");
        confirmBtn.setTextColor(tokens.accent);
        confirmBtn.setTextSize(13);
        confirmBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        confirmBtn.setGravity(Gravity.CENTER);
        confirmBtn.setMinHeight((int) (48 * density));
        confirmBtn.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));
        confirmBtn.setClickable(true);
        confirmBtn.setFocusable(true);
        confirmBtn.setBackground(createButtonBg(tokens.controlBgPressed, tokens.accent, density));

        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                if (listener != null) listener.onSelected(true);
            }
        });

        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        confirmLp.setMargins((int) (8 * density), 0, 0, 0);
        confirmBtn.setLayoutParams(confirmLp);
        btnRow.addView(confirmBtn);

        root.addView(btnRow);

        dlg.setContentView(root);
        dlg.show();
    }

    private static StateListDrawable createButtonBg(int pressedColor, int strokeColor, float density) {
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setStroke((int) (1 * density), strokeColor);
        pressed.setCornerRadius((int) (4 * density));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setStroke((int) (1 * density), strokeColor);
        normal.setCornerRadius((int) (4 * density));

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}
