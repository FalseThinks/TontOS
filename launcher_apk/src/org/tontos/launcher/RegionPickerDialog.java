package org.tontos.launcher;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AMOLED modal dialog for selecting regional formatting / country.
 * Features real-time search filtering, active region indicator, and minimum 48dp touch targets.
 * Built with AmoledDialog template.
 */
public final class RegionPickerDialog {

    private static final int MAX_DISPLAY_COUNT = 120;

    public interface OnRegionSelectedListener {
        void onRegionSelected(RegionItem selected);
    }

    private RegionPickerDialog() {}

    public static void show(
        final Context context,
        final ThemeTokens tokens,
        final RegionItem currentRegion,
        final OnRegionSelectedListener listener
    ) {
        if (context == null) return;

        String tempLang = "en";
        try {
            tempLang = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}
        final String langCode = tempLang;

        final AmoledDialog dlg = new AmoledDialog(context, tokens, I18n.get(I18n.KEY_SELECT_REGION, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();
        final int minTouchHeight = (int) (48 * density + 0.5f);

        // Search Input Bar
        final EditText searchBar = new EditText(context);
        searchBar.setHint(I18n.get(I18n.KEY_SEARCH_REGION, langCode));
        searchBar.setHintTextColor(themeTokens.textMuted);
        searchBar.setTextColor(themeTokens.textPrimary);
        searchBar.setTextSize(14 * fontScale);
        searchBar.setTypeface(Typeface.MONOSPACE);
        searchBar.setSingleLine(true);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setMinHeight(minTouchHeight);
        searchBar.setMinimumHeight(minTouchHeight);
        searchBar.setPadding((int) (12 * density), 0, (int) (12 * density), 0);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke((int) (1 * density), themeTokens.border);
        searchBg.setCornerRadius((int) (4 * density));
        searchBar.setBackground(searchBg);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(0, 0, 0, (int) (8 * density));
        searchBar.setLayoutParams(searchLp);
        dlg.addFixedSubHeaderView(searchBar);

        // Badge
        final TextView countBadge = new TextView(context);
        countBadge.setTextColor(themeTokens.textMuted);
        countBadge.setTextSize(11 * fontScale);
        countBadge.setTypeface(Typeface.MONOSPACE);
        countBadge.setPadding(0, 0, 0, (int) (8 * density));
        dlg.addFixedSubHeaderView(countBadge);

        final LinearLayout listContainer = dlg.getContentContainer();
        final List<RegionItem> allRegions = LocaleManager.getAvailableRegions();

        final Runnable renderList = new Runnable() {
            @Override
            public void run() {
                listContainer.removeAllViews();
                String query = searchBar.getText().toString().trim().toLowerCase(Locale.ROOT);

                List<RegionItem> matches = new ArrayList<>();
                for (RegionItem item : allRegions) {
                    if (query.isEmpty()) {
                        matches.add(item);
                    } else if (item.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                            || item.getCode().toLowerCase(Locale.ROOT).contains(query)) {
                        matches.add(item);
                    }
                }

                int total = matches.size();
                int renderCap = query.isEmpty() ? Math.min(total, MAX_DISPLAY_COUNT) : total;
                countBadge.setText(I18n.format(I18n.KEY_REGIONS_FOUND_FMT, langCode, total));

                // Render current selected region first if on default view
                if (query.isEmpty() && currentRegion != null) {
                    View currentView = createRegionRow(context, themeTokens, currentRegion, true, density, minTouchHeight, fontScale, dlg, listener);
                    listContainer.addView(currentView);

                    TextView sep = new TextView(context);
                    sep.setText("--- [ " + I18n.get(I18n.KEY_REGION, langCode).toUpperCase(Locale.ROOT) + " ] ---");
                    sep.setTextColor(Color.parseColor("#444444"));
                    sep.setTextSize(11 * fontScale);
                    sep.setTypeface(Typeface.MONOSPACE);
                    sep.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
                    listContainer.addView(sep);
                }

                for (int i = 0; i < renderCap; i++) {
                    RegionItem item = matches.get(i);
                    boolean isCurrent = currentRegion != null && currentRegion.getCode().equalsIgnoreCase(item.getCode());
                    if (query.isEmpty() && isCurrent) continue;

                    View row = createRegionRow(context, themeTokens, item, isCurrent, density, minTouchHeight, fontScale, dlg, listener);
                    listContainer.addView(row);
                }
            }
        };

        renderList.run();

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderList.run();
            }
            @Override public void afterTextChanged(Editable s) {}
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
    }

    private static View createRegionRow(
        final Context context,
        final ThemeTokens tokens,
        final RegionItem item,
        final boolean isActive,
        float density,
        int minTouchHeight,
        float fontScale,
        final AmoledDialog dlg,
        final OnRegionSelectedListener listener
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(minTouchHeight);
        row.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(TactileUi.createSelectionRowBg(tokens, isActive, density));

        TextView indicator = new TextView(context);
        indicator.setText(isActive ? "[x] " : "[ ] ");
        indicator.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        indicator.setTextSize(13 * fontScale);
        indicator.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        row.addView(indicator);

        TextView nameView = new TextView(context);
        nameView.setText(item.getDisplayName());
        nameView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textPrimary);
        nameView.setTextSize(13 * fontScale);
        nameView.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        nameView.setLayoutParams(nameLp);
        row.addView(nameView);

        TextView codeView = new TextView(context);
        codeView.setText("[" + item.getCode() + "]");
        codeView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        codeView.setTextSize(11 * fontScale);
        codeView.setTypeface(Typeface.MONOSPACE);
        row.addView(codeView);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                if (listener != null) {
                    listener.onRegionSelected(item);
                }
            }
        });

        return row;
    }
}
