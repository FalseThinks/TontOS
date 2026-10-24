package org.tontos.launcher;

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
import java.util.List;

/**
 * Clean AMOLED modal dialog for selecting from a discrete list of options
 * (e.g. Theme Presets, Daily Detox Budgets).
 */
public final class SingleChoiceSelectorDialog {

    public static class Option {
        public final String id;
        public final String label;
        public final String sublabel;

        public Option(String id, String label) {
            this(id, label, null);
        }

        public Option(String id, String label, String sublabel) {
            this.id = id;
            this.label = label;
            this.sublabel = sublabel;
        }
    }

    public interface OnOptionSelectedListener {
        void onOptionSelected(Option selected);
    }

    public static void show(
        final Context context,
        final ThemeTokens tokens,
        final String title,
        final List<Option> options,
        final String currentSelectedId,
        final OnOptionSelectedListener listener
    ) {
        show(context, tokens, title, options, currentSelectedId, null, listener);
    }

    public static void show(
        final Context context,
        final ThemeTokens tokens,
        final String title,
        final List<Option> options,
        final String currentSelectedId,
        final View extraActionView,
        final OnOptionSelectedListener listener
    ) {
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        String langCode = "en";
        try {
            langCode = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}

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
        int screenWidthPx = context.getResources().getDisplayMetrics().widthPixels;
        int screenWidthDp = (int) (screenWidthPx / density);

        // Adaptive horizontal padding: 16dp on compact screens, 24dp on standard
        int padH = screenWidthDp < 360 ? (int) (14 * density) : (int) (20 * density);
        int padV = (int) (20 * density);

        // Outer container for centering on wide displays (tablets)
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(tokens.bg);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(padH, padV, padH, padV);

        // Adaptive max width: on tablets (>520dp), cap dialog width to 480dp
        if (screenWidthDp > 520) {
            LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(
                (int) (480 * density), ViewGroup.LayoutParams.MATCH_PARENT
            );
            root.setLayoutParams(rootLp);
        } else {
            LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            );
            root.setLayoutParams(rootLp);
        }

        final float fontScale = GlobalSettingsBridge.getFontScale(context);

        // Header Title
        TextView header = new TextView(context);
        header.setText(title != null ? title.toUpperCase(java.util.Locale.ROOT) : I18n.get(I18n.KEY_SELECT_OPTION, langCode));
        header.setTextColor(tokens.accent);
        header.setTextSize((screenWidthDp < 360 ? 15 : 17) * fontScale);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setSingleLine(true);
        header.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.setPadding(0, 0, 0, (int) (6 * density));
        root.addView(header);

        View divider = new View(context);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (12 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        if (extraActionView != null) {
            root.addView(extraActionView);
        }

        // Scrollable Options List
        ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scroll.setLayoutParams(scrollLp);

        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        int minHeightPx = (int) (44 * density + 0.5f);
        int itemPadV = (int) (8 * density);
        int itemPadH = (int) (10 * density);

        for (final Option opt : options) {
            final boolean isActive = (opt.id == null && currentSelectedId == null)
                || (opt.id != null && opt.id.equalsIgnoreCase(currentSelectedId));

            LinearLayout itemRow = new LinearLayout(context);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setMinimumHeight(minHeightPx);
            itemRow.setPadding(itemPadH, itemPadV, itemPadH, itemPadV);
            itemRow.setClickable(true);
            itemRow.setFocusable(true);

            StateListDrawable sld = new StateListDrawable();
            GradientDrawable pressed = new GradientDrawable();
            pressed.setColor(tokens.controlBgPressed);
            pressed.setCornerRadius((int) (4 * density));

            GradientDrawable normal = new GradientDrawable();
            normal.setColor(isActive ? tokens.activeSelectionBg : Color.TRANSPARENT);
            normal.setCornerRadius((int) (4 * density));
            if (isActive) {
                normal.setStroke((int) (1 * density), tokens.accent);
            }

            sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sld.addState(new int[]{android.R.attr.state_focused}, pressed);
            sld.addState(new int[]{}, normal);
            itemRow.setBackground(sld);

            TextView indicator = new TextView(context);
            indicator.setText(isActive ? "[x] " : "[ ] ");
            indicator.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
            indicator.setTextSize(13 * fontScale);
            indicator.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
            itemRow.addView(indicator);

            TextView label = new TextView(context);
            label.setText(opt.label);
            label.setTextColor(isActive ? tokens.activeSelectionText : tokens.textPrimary);
            label.setTextSize(13 * fontScale);
            label.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            label.setLayoutParams(labelLp);
            itemRow.addView(label);

            itemRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onOptionSelected(opt);
                    }
                }
            });

            listContainer.addView(itemRow);

            View space = new View(context);
            space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (3 * density)));
            listContainer.addView(space);
        }

        scroll.addView(listContainer);
        root.addView(scroll);

        // Cancel / Back Button (Invisible flat action button in navAccent)
        TextView closeBtn = new TextView(context);
        closeBtn.setText(I18n.get(I18n.KEY_CANCEL, langCode));
        closeBtn.setTextColor(tokens.navAccent);
        closeBtn.setTextSize(13 * fontScale);
        closeBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setMinimumHeight(minHeightPx);
        closeBtn.setPadding(0, itemPadV, 0, itemPadV);
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);

        StateListDrawable btnSld = new StateListDrawable();
        GradientDrawable btnPressed = new GradientDrawable();
        btnPressed.setColor(tokens.controlBgPressed);
        btnPressed.setCornerRadius((int) (4 * density));

        ColorDrawable btnNormal = new ColorDrawable(Color.TRANSPARENT);

        btnSld.addState(new int[]{android.R.attr.state_pressed}, btnPressed);
        btnSld.addState(new int[]{android.R.attr.state_focused}, btnPressed);
        btnSld.addState(new int[]{}, btnNormal);
        closeBtn.setBackground(btnSld);

        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(0, (int) (10 * density), 0, 0);
        closeBtn.setLayoutParams(closeLp);
        root.addView(closeBtn);

        outer.addView(root);
        dialog.setContentView(outer);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
}
