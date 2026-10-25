package org.tontos.launcher;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
 * Reusable AMOLED fullscreen modal dialog builder.
 * Enforces pure AMOLED black (#000000), monospace typography, system bar insets,
 * WCAG AAA contrast, and daltonizer integration.
 */
public final class AmoledDialog {

    private final Context context;
    private final ThemeTokens tokens;
    private final Dialog dialog;
    private final LinearLayout root;
    private final TextView titleView;
    private final View headerDivider;
    private final ScrollView scrollView;
    private final LinearLayout contentContainer;
    private final LinearLayout buttonRow;
    private final float density;
    private final float fontScale;

    public AmoledDialog(Context context, ThemeTokens tokens, String title) {
        this.context = context;
        this.tokens = tokens != null ? tokens : ThemeTokens.get(ThemePreset.AMOLED);
        this.density = context.getResources().getDisplayMetrics().density;
        this.fontScale = GlobalSettingsBridge.getFontScale(context);

        this.dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.dialog.setCancelable(true);
        this.dialog.setCanceledOnTouchOutside(true);

        configureWindow(dialog.getWindow(), this.tokens, context);

        int screenWidthPx = context.getResources().getDisplayMetrics().widthPixels;
        int screenWidthDp = (int) (screenWidthPx / density);
        int padH = screenWidthDp < 360 ? (int) (14 * density) : (int) (20 * density);
        int padV = (int) (20 * density);

        // Outer container for centering on wide displays (tablets)
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(this.tokens.bg);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        this.root = new LinearLayout(context);
        this.root.setOrientation(LinearLayout.VERTICAL);
        this.root.setBackgroundColor(this.tokens.bg);
        this.root.setPadding(padH, padV, padH, padV);

        if (screenWidthDp > 520) {
            this.root.setLayoutParams(new LinearLayout.LayoutParams((int) (480 * density), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            this.root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Header Title
        this.titleView = new TextView(context);
        this.titleView.setText(title != null ? title.toUpperCase(Locale.ROOT) : "");
        this.titleView.setTextColor(this.tokens.accent);
        this.titleView.setTextSize((screenWidthDp < 360 ? 15 : 16) * fontScale);
        this.titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        this.titleView.setSingleLine(true);
        this.titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        this.titleView.setPadding(0, 0, 0, (int) (6 * density));
        this.root.addView(this.titleView);

        // Header Divider
        this.headerDivider = new View(context);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (12 * density));
        this.headerDivider.setLayoutParams(divParams);
        this.headerDivider.setBackgroundColor(this.tokens.border);
        this.root.addView(this.headerDivider);

        // ScrollView & Content Container
        this.scrollView = new ScrollView(context);
        this.scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        this.contentContainer = new LinearLayout(context);
        this.contentContainer.setOrientation(LinearLayout.VERTICAL);
        this.scrollView.addView(this.contentContainer);
        this.root.addView(this.scrollView);

        // Action Buttons Row
        this.buttonRow = new LinearLayout(context);
        this.buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        this.buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        this.buttonRow.setPadding(0, (int) (16 * density), 0, 0);
        this.root.addView(this.buttonRow);

        outer.addView(this.root);
        this.dialog.setContentView(outer);
    }

    public static void configureWindow(Window window, ThemeTokens tokens, Context context) {
        if (window == null) return;
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
        GrayscaleManager.applyGrayscaleToWindow(window, GrayscaleManager.isGrayscaleEnabled(context));
    }

    public Dialog getDialog() {
        return dialog;
    }

    public LinearLayout getRoot() {
        return root;
    }

    public TextView getTitleView() {
        return titleView;
    }

    public View getHeaderDivider() {
        return headerDivider;
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public LinearLayout getContentContainer() {
        return contentContainer;
    }

    public LinearLayout getButtonRow() {
        return buttonRow;
    }

    public float getDensity() {
        return density;
    }

    public float getFontScale() {
        return fontScale;
    }

    public TextView addActionButton(String label, int textColor, int strokeColor, boolean isFullWidth, final View.OnClickListener listener) {
        TextView btn = new TextView(context);
        btn.setText(TactileUi.ensureBrackets(label));
        btn.setTextColor(textColor);
        btn.setTextSize(13 * fontScale);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight((int) (48 * density));
        btn.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setBackground(TactileUi.createButtonBg(tokens.controlBgPressed, strokeColor, density));
        btn.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = isFullWidth
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        if (!isFullWidth && buttonRow.getChildCount() > 0) {
            lp.setMargins((int) (8 * density), 0, 0, 0);
        }
        btn.setLayoutParams(lp);
        buttonRow.addView(btn);
        return btn;
    }

    public Context getContext() {
        return context;
    }

    public ThemeTokens getTokens() {
        return tokens;
    }

    public void addHeaderCustomView(View view) {
        int divIdx = root.indexOfChild(headerDivider);
        if (divIdx >= 0) {
            root.addView(view, divIdx);
        } else {
            root.addView(view, 0);
        }
    }

    public void addFixedSubHeaderView(View view) {
        int scrollIdx = root.indexOfChild(scrollView);
        if (scrollIdx >= 0) {
            root.addView(view, scrollIdx);
        } else {
            root.addView(view);
        }
    }

    public void setCancelable(boolean cancelable) {
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        dialog.setOnDismissListener(listener);
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        dialog.setOnCancelListener(listener);
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }
}
