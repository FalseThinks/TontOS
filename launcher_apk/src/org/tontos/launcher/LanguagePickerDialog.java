package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * AMOLED modal dialog for selecting system language.
 * Features real-time search filtering, accent-insensitive search, and minimum 48dp touch targets.
 * Built with AmoledDialog template.
 */
public final class LanguagePickerDialog {

    private static final int MIN_TOUCH_TARGET_DP = 48;
    private static final int MAX_DISPLAY_COUNT = 150;

    public interface OnLocaleSelectedListener {
        void onLocaleSelected(LocaleItem selected);
    }

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

        final String langCode = currentItem != null ? currentItem.getTag() : "en";
        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_SELECT_LANGUAGE, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        // 1. Real-time search filter EditText [ Search language... ]
        final EditText searchBar = new EditText(activity);
        searchBar.setHint(I18n.get(I18n.KEY_SEARCH_LANGUAGE, langCode));
        searchBar.setHintTextColor(themeTokens.textMuted);
        searchBar.setTextColor(themeTokens.textPrimary);
        searchBar.setTextSize(13 * fontScale);
        searchBar.setTypeface(Typeface.MONOSPACE);
        searchBar.setSingleLine(true);
        searchBar.setMinHeight((int) (44 * density + 0.5f));
        searchBar.setMinimumHeight((int) (44 * density + 0.5f));
        searchBar.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setShape(GradientDrawable.RECTANGLE);
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke((int) (1 * density + 0.5f), themeTokens.border);
        searchBg.setCornerRadius((int) (4 * density));
        searchBar.setBackground(searchBg);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.setMargins(0, 0, 0, (int) (6 * density));
        searchBar.setLayoutParams(searchLp);
        dlg.addFixedSubHeaderView(searchBar);

        // Count badge
        final TextView countBadge = new TextView(activity);
        countBadge.setTextColor(themeTokens.textMuted);
        countBadge.setTextSize(11 * fontScale);
        countBadge.setTypeface(Typeface.MONOSPACE);
        countBadge.setSingleLine(true);
        countBadge.setEllipsize(android.text.TextUtils.TruncateAt.END);
        countBadge.setPadding(0, 0, 0, (int) (6 * density));
        dlg.addFixedSubHeaderView(countBadge);

        final LinearLayout listContainer = dlg.getContentContainer();

        // Populate initial list of unique languages
        final List<LocaleItem> allLocales = LocaleManager.getAvailableLanguages();
        populateList(activity, listContainer, countBadge, "", allLocales, currentItem, dlg, themeTokens, langCode, density, fontScale, listener);

        // Real-time TextWatcher
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = (s != null) ? s.toString() : "";
                populateList(activity, listContainer, countBadge, query, allLocales, currentItem, dlg, themeTokens, langCode, density, fontScale, listener);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Bottom Cancel Button
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
        return dlg.getDialog();
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
        final AmoledDialog dlg,
        final ThemeTokens tokens,
        final String langCode,
        final float density,
        final float fontScale,
        final OnLocaleSelectedListener listener
    ) {
        container.removeAllViews();
        String q = stripAccents(query);

        // If query is empty, pin the active language at the top for immediate access
        if (q.isEmpty() && currentItem != null) {
            View activeTopRow = createLanguageRow(activity, currentItem, true, tokens, density, fontScale, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                    if (listener != null) {
                        listener.onLocaleSelected(currentItem);
                    }
                }
            });
            container.addView(activeTopRow);

            View pinDivider = new View(activity);
            pinDivider.setBackgroundColor(tokens.border);
            LinearLayout.LayoutParams pdLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density + 0.5f));
            pdLp.setMargins(0, (int) (4 * density), 0, (int) (6 * density));
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
                    View row = createLanguageRow(activity, item, isActive, tokens, density, fontScale, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dlg.dismiss();
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

        countBadge.setText(I18n.format(I18n.KEY_LANGUAGES_FOUND_FMT, langCode, count));
    }

    private static View createLanguageRow(
        final Context context,
        final LocaleItem item,
        final boolean isActive,
        final ThemeTokens tokens,
        final float density,
        final float fontScale,
        final View.OnClickListener onClick
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (MIN_TOUCH_TARGET_DP * density + 0.5f));
        row.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(TactileUi.createSelectionRowBg(tokens, isActive, density));

        // Indicator [x] or [ ]
        TextView indicator = new TextView(context);
        indicator.setText(isActive ? "[x] " : "[ ] ");
        indicator.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        indicator.setTextSize(13 * fontScale);
        indicator.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        row.addView(indicator);

        // Main text container: Title (English) + Native Name
        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textContainer.setLayoutParams(textLp);

        TextView titleView = new TextView(context);
        titleView.setText(item.getDisplayName());
        titleView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textPrimary);
        titleView.setTextSize(13 * fontScale);
        titleView.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textContainer.addView(titleView);

        if (item.getNativeName() != null && !item.getNativeName().isEmpty() && !item.getNativeName().equalsIgnoreCase(item.getDisplayName())) {
            TextView nativeView = new TextView(context);
            nativeView.setText(item.getNativeName());
            nativeView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
            nativeView.setTextSize(11 * fontScale);
            nativeView.setTypeface(Typeface.MONOSPACE);
            nativeView.setSingleLine(true);
            nativeView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textContainer.addView(nativeView);
        }
        row.addView(textContainer);

        // Tag badge [en-US]
        TextView tagView = new TextView(context);
        tagView.setText("[" + item.getTag() + "]");
        tagView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        tagView.setTextSize(11 * fontScale);
        tagView.setTypeface(Typeface.MONOSPACE);
        tagView.setSingleLine(true);
        tagView.setPadding((int) (8 * density), 0, 0, 0);
        row.addView(tagView);

        row.setOnClickListener(onClick);
        return row;
    }
}
