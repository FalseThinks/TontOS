package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Pure AMOLED (#000000) All-Languages Selector Dialog.
 * Features real-time search filtering, 48dp minimum touch targets,
 * monospace typography, and active language [x] indicator.
 */
public final class LanguagePickerDialog {

    public interface OnLocaleSelectedListener {
        void onLocaleSelected(LocaleItem item);
    }

    private static final int MIN_TOUCH_TARGET_DP = 48;
    private static final int MAX_DISPLAY_COUNT = 150;

    private LanguagePickerDialog() {}

    public static Dialog show(
        final Activity activity,
        final ThemeTokens tokens,
        final LocaleItem currentItem,
        final OnLocaleSelectedListener listener
    ) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && dialog.getWindow().getInsetsController() != null) {
                    dialog.getWindow().getInsetsController().setSystemBarsAppearance(
                        tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } catch (Throwable ignored) {}
            GrayscaleManager.applyGrayscaleToWindow(dialog.getWindow(), GrayscaleManager.isGrayscaleEnabled(activity));
        }

        int screenWidthPx = activity.getResources().getDisplayMetrics().widthPixels;
        float density = activity.getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (screenWidthPx / density);
        int padH = screenWidthDp < 360 ? dpToPx(activity, 14) : dpToPx(activity, 20);
        int padV = dpToPx(activity, 20);

        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(tokens.bg);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(padH, padV, padH, padV);

        if (screenWidthDp > 520) {
            root.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(activity, 480), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        final String langCode = currentItem != null ? currentItem.getTag() : "en";
        final float fontScale = GlobalSettingsBridge.getFontScale(activity);

        // 1. Header: SELECT SYSTEM LANGUAGE
        TextView header = new TextView(activity);
        header.setText(I18n.get(I18n.KEY_SELECT_LANGUAGE, langCode).toUpperCase(Locale.ROOT));
        header.setTextColor(tokens.accent);
        header.setTextSize((screenWidthDp < 360 ? 15 : 16) * fontScale);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setSingleLine(true);
        header.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.setPadding(0, 0, 0, dpToPx(activity, 4));
        root.addView(header);

        // Tactile divider line
        View divider = new View(activity);
        divider.setBackgroundColor(tokens.border);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 1));
        divLp.setMargins(0, 0, 0, dpToPx(activity, 10));
        divider.setLayoutParams(divLp);
        root.addView(divider);

        // 2. Real-time search filter EditText [ Search language... ]
        final EditText searchBar = new EditText(activity);
        searchBar.setHint(I18n.get(I18n.KEY_SEARCH_LANGUAGE, langCode));
        searchBar.setHintTextColor(tokens.textMuted);
        searchBar.setTextColor(tokens.textPrimary);
        searchBar.setTextSize(13 * fontScale);
        searchBar.setTypeface(Typeface.MONOSPACE);
        searchBar.setSingleLine(true);
        searchBar.setMinHeight(dpToPx(activity, 44));
        searchBar.setMinimumHeight(dpToPx(activity, 44));
        searchBar.setPadding(dpToPx(activity, 12), dpToPx(activity, 8), dpToPx(activity, 12), dpToPx(activity, 8));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setShape(GradientDrawable.RECTANGLE);
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke(dpToPx(activity, 1), tokens.border);
        searchBg.setCornerRadius(dpToPx(activity, 4));
        searchBar.setBackground(searchBg);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.setMargins(0, 0, 0, dpToPx(activity, 6));
        searchBar.setLayoutParams(searchLp);
        root.addView(searchBar);

        // Count badge
        final TextView countBadge = new TextView(activity);
        countBadge.setTextColor(tokens.textMuted);
        countBadge.setTextSize(11 * fontScale);
        countBadge.setTypeface(Typeface.MONOSPACE);
        countBadge.setSingleLine(true);
        countBadge.setEllipsize(android.text.TextUtils.TruncateAt.END);
        countBadge.setPadding(0, 0, 0, dpToPx(activity, 6));
        root.addView(countBadge);

        // 3. Scrollable LinearLayout (in ScrollView)
        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scroll.setLayoutParams(scrollLp);

        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        root.addView(scroll);

        // 4. Bottom Cancel / Back Button (Flat invisible action button in navAccent)
        TextView closeBtn = new TextView(activity);
        closeBtn.setText(I18n.get(I18n.KEY_CANCEL, langCode));
        closeBtn.setTextColor(tokens.navAccent);
        closeBtn.setTextSize(13 * fontScale);
        closeBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setMinHeight(dpToPx(activity, MIN_TOUCH_TARGET_DP));
        closeBtn.setMinimumHeight(dpToPx(activity, MIN_TOUCH_TARGET_DP));
        closeBtn.setPadding(0, dpToPx(activity, 10), 0, dpToPx(activity, 10));
        closeBtn.setBackground(createFlatActionFeedbackDrawable(activity, tokens.controlBgPressed));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        closeLp.setMargins(0, dpToPx(activity, 10), 0, 0);
        closeBtn.setLayoutParams(closeLp);
        root.addView(closeBtn);

        // Populate initial list of unique languages
        final List<LocaleItem> allLocales = LocaleManager.getAvailableLanguages();
        populateList(activity, listContainer, countBadge, "", allLocales, currentItem, dialog, tokens, langCode, listener);

        // Real-time TextWatcher
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = (s != null) ? s.toString() : "";
                populateList(activity, listContainer, countBadge, query, allLocales, currentItem, dialog, tokens, langCode, listener);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        outer.addView(root);
        dialog.setContentView(outer);
        dialog.show();
        return dialog;
    }

    private static String stripAccents(String s) {
        if (s == null) return "";
        String normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static void populateList(
        final Activity activity,
        final LinearLayout container,
        final TextView countBadge,
        final String query,
        final List<LocaleItem> allLocales,
        final LocaleItem currentItem,
        final Dialog dialog,
        final ThemeTokens tokens,
        final String langCode,
        final OnLocaleSelectedListener listener
    ) {
        container.removeAllViews();
        String q = stripAccents(query);

        // If query is empty, pin the active language at the top for immediate access
        if (q.isEmpty() && currentItem != null) {
            View activeTopRow = createLanguageRow(activity, currentItem, true, tokens, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onLocaleSelected(currentItem);
                    }
                }
            });
            container.addView(activeTopRow);

            View pinDivider = new View(activity);
            pinDivider.setBackgroundColor(tokens.border);
            LinearLayout.LayoutParams pdLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 1));
            pdLp.setMargins(0, dpToPx(activity, 4), 0, dpToPx(activity, 6));
            pinDivider.setLayoutParams(pdLp);
            container.addView(pinDivider);
        }

        int count = 0;
        int rendered = 0;
        int maxResults = q.isEmpty() ? 100 : MAX_DISPLAY_COUNT;

        for (final LocaleItem item : allLocales) {
            boolean match = q.isEmpty()
                || stripAccents(item.getDisplayName()).contains(q)
                || stripAccents(item.getTag()).contains(q)
                || stripAccents(item.getNativeName()).contains(q)
                || stripAccents(item.getCode()).contains(q);

            if (match) {
                count++;
                if (rendered < maxResults) {
                    final boolean isActive = currentItem != null && (
                        currentItem.getTag().equalsIgnoreCase(item.getTag())
                        || currentItem.getCode().equalsIgnoreCase(item.getCode())
                    );
                    View row = createLanguageRow(activity, item, isActive, tokens, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            if (listener != null) {
                                listener.onLocaleSelected(item);
                            }
                        }
                    });
                    container.addView(row);
                    rendered++;
                }
            }
        }

        if (countBadge != null) {
            countBadge.setText(I18n.format(I18n.KEY_LANGUAGES_FOUND_FMT, langCode, count));
        }
    }

    private static View createLanguageRow(
        final Context context,
        final LocaleItem item,
        final boolean isActive,
        final ThemeTokens tokens,
        final View.OnClickListener onClick
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dpToPx(context, MIN_TOUCH_TARGET_DP));
        row.setPadding(dpToPx(context, 12), dpToPx(context, 6), dpToPx(context, 12), dpToPx(context, 6));
        row.setClickable(true);
        row.setFocusable(true);

        int normalBg = isActive ? tokens.activeSelectionBg : Color.TRANSPARENT;
        int strokeColor = isActive ? tokens.accent : Color.TRANSPARENT;
        row.setBackground(createStateFeedbackDrawable(context, normalBg, tokens.controlBgPressed, strokeColor));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.setMargins(0, dpToPx(context, 2), 0, dpToPx(context, 2));
        row.setLayoutParams(rowLp);

        final float fontScale = GlobalSettingsBridge.getFontScale(context);

        // Active indicator: [x] or [ ]
        TextView indicator = new TextView(context);
        indicator.setText(isActive ? "[x] " : "[ ] ");
        indicator.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        indicator.setTextSize(13 * fontScale);
        indicator.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        row.addView(indicator);

        // Language display name
        TextView nameView = new TextView(context);
        nameView.setText(item.getDisplayName());
        nameView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textPrimary);
        nameView.setTextSize(13 * fontScale);
        nameView.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        nameView.setLayoutParams(nameLp);
        row.addView(nameView);

        // Language tag on right e.g. [en-US]
        TextView tagView = new TextView(context);
        tagView.setText("[" + item.getTag() + "]");
        tagView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        tagView.setTextSize(11 * fontScale);
        tagView.setTypeface(Typeface.MONOSPACE);
        tagView.setSingleLine(true);
        tagView.setPadding(dpToPx(context, 8), 0, 0, 0);
        row.addView(tagView);

        row.setOnClickListener(onClick);
        return row;
    }

    private static Drawable createFlatActionFeedbackDrawable(Context context, int pressedColor) {
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setCornerRadius(dpToPx(context, 4));
        android.graphics.drawable.ColorDrawable normal = new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    private static Drawable createStateFeedbackDrawable(Context context, int normalColor, int pressedColor, int strokeColor) {
        StateListDrawable sld = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(pressedColor); // #222222
        if (strokeColor != Color.TRANSPARENT) {
            pressed.setStroke(dpToPx(context, 1), strokeColor);
        }
        pressed.setCornerRadius(dpToPx(context, 4));

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(normalColor);
        if (strokeColor != Color.TRANSPARENT) {
            normal.setStroke(dpToPx(context, 1), strokeColor);
        }
        normal.setCornerRadius(dpToPx(context, 4));

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
