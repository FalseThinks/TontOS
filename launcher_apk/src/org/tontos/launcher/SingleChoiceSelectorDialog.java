package org.tontos.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Clean AMOLED modal dialog for selecting from a discrete list of options
 * (e.g. Theme Presets, Daily Detox Budgets).
 * Uses AmoledDialog builder for uniform styling and responsive layouts.
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

    private SingleChoiceSelectorDialog() {}

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
        if (context == null) return;

        String langCode = "en";
        try {
            langCode = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}

        String dlgTitle = title != null ? title.toUpperCase(java.util.Locale.ROOT) : I18n.get(I18n.KEY_SELECT_OPTION, langCode);
        final AmoledDialog dlg = new AmoledDialog(context, tokens, dlgTitle);

        if (extraActionView != null) {
            dlg.addFixedSubHeaderView(extraActionView);
        }

        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();
        int minHeightPx = (int) (44 * density + 0.5f);
        int itemPadV = (int) (8 * density);
        int itemPadH = (int) (10 * density);

        if (options != null) {
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
                itemRow.setBackground(TactileUi.createSelectionRowBg(themeTokens, isActive, density));

                TextView indicator = new TextView(context);
                indicator.setText(isActive ? "[x] " : "[ ] ");
                indicator.setTextColor(isActive ? themeTokens.activeSelectionText : themeTokens.textMuted);
                indicator.setTextSize(13 * fontScale);
                indicator.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
                itemRow.addView(indicator);

                TextView label = new TextView(context);
                label.setText(opt.label);
                label.setTextColor(isActive ? themeTokens.activeSelectionText : themeTokens.textPrimary);
                label.setTextSize(13 * fontScale);
                label.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                itemRow.addView(label);

                itemRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dlg.dismiss();
                        if (listener != null) {
                            listener.onOptionSelected(opt);
                        }
                    }
                });

                dlg.getContentContainer().addView(itemRow);

                View space = new View(context);
                space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (3 * density)));
                dlg.getContentContainer().addView(space);
            }
        }

        // Cancel / Back Button
        dlg.addActionButton(
            I18n.get(I18n.KEY_CANCEL, langCode),
            themeTokens.navAccent,
            themeTokens.border,
            true,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                }
            }
        );

        dlg.show();
    }
}
