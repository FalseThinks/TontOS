package org.tontos.launcher;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AMOLED modal dialog for selecting regional formatting / country.
 * Features real-time search filtering, active region indicator, and minimum 48dp touch targets.
 */
public final class RegionPickerDialog {

    private static final int MAX_DISPLAY_COUNT = 120;

    public interface OnRegionSelectedListener {
        void onRegionSelected(RegionItem selected);
    }

    public static void show(
        final Context context,
        final ThemeTokens tokens,
        final RegionItem currentRegion,
        final OnRegionSelectedListener listener
    ) {
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

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

        final float density = context.getResources().getDisplayMetrics().density;
        final int padH = (int) (20 * density);
        final int padV = (int) (20 * density);
        final int minTouchHeight = (int) (48 * density + 0.5f);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(padH, padV, padH, padV);

        String tempLang = "en";
        try {
            tempLang = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}
        final String langCode = tempLang;

        // Header Title
        TextView header = new TextView(context);
        header.setText(I18n.get(I18n.KEY_SELECT_REGION, langCode).toUpperCase(Locale.ROOT));
        header.setTextColor(tokens.accent);
        header.setTextSize(18);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(header);

        View divider = new View(context);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (8 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        // Search Input Bar
        final EditText searchBar = new EditText(context);
        searchBar.setHint(I18n.get(I18n.KEY_SEARCH_REGION, langCode));
        searchBar.setHintTextColor(tokens.textMuted);
        searchBar.setTextColor(tokens.textPrimary);
        searchBar.setTextSize(14);
        searchBar.setTypeface(Typeface.MONOSPACE);
        searchBar.setSingleLine(true);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setMinHeight(minTouchHeight);
        searchBar.setMinimumHeight(minTouchHeight);
        searchBar.setPadding((int) (12 * density), 0, (int) (12 * density), 0);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke((int) (1 * density), tokens.border);
        searchBg.setCornerRadius((int) (4 * density));
        searchBar.setBackground(searchBg);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(0, 0, 0, (int) (10 * density));
        searchBar.setLayoutParams(searchLp);
        root.addView(searchBar);

        // Badge
        final TextView countBadge = new TextView(context);
        countBadge.setTextColor(tokens.textMuted);
        countBadge.setTextSize(11);
        countBadge.setTypeface(Typeface.MONOSPACE);
        countBadge.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(countBadge);

        // Scrollable Container
        final ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scroll.setLayoutParams(scrollLp);

        final LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        root.addView(scroll);

        // Bottom Cancel Button
        final TextView closeBtn = new TextView(context);
        closeBtn.setText(I18n.get(I18n.KEY_CANCEL, langCode));
        closeBtn.setTextColor(tokens.navAccent);
        closeBtn.setTextSize(13);
        closeBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setMinHeight(minTouchHeight);
        closeBtn.setMinimumHeight(minTouchHeight);
        closeBtn.setPadding(0, (int) (12 * density), 0, (int) (12 * density));
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
                    View currentView = createRegionRow(context, tokens, currentRegion, true, density, minTouchHeight, dialog, listener);
                    listContainer.addView(currentView);

                    TextView sep = new TextView(context);
                    sep.setText("--- [ " + I18n.get(I18n.KEY_REGION, langCode).toUpperCase(Locale.ROOT) + " ] ---");
                    sep.setTextColor(Color.parseColor("#444444"));
                    sep.setTextSize(11);
                    sep.setTypeface(Typeface.MONOSPACE);
                    sep.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
                    listContainer.addView(sep);
                }

                for (int i = 0; i < renderCap; i++) {
                    RegionItem item = matches.get(i);
                    boolean isCurrent = currentRegion != null && currentRegion.getCode().equalsIgnoreCase(item.getCode());
                    if (query.isEmpty() && isCurrent) continue;

                    View row = createRegionRow(context, tokens, item, isCurrent, density, minTouchHeight, dialog, listener);
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

        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private static View createRegionRow(
        final Context context,
        final ThemeTokens tokens,
        final RegionItem item,
        final boolean isActive,
        float density,
        int minTouchHeight,
        final Dialog dialog,
        final OnRegionSelectedListener listener
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(minTouchHeight);
        row.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        row.setClickable(true);
        row.setFocusable(true);

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
        row.setBackground(sld);

        TextView indicator = new TextView(context);
        indicator.setText(isActive ? "[x] " : "[ ] ");
        indicator.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        indicator.setTextSize(13);
        indicator.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        row.addView(indicator);

        TextView nameView = new TextView(context);
        nameView.setText(item.getDisplayName());
        nameView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textPrimary);
        nameView.setTextSize(13);
        nameView.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        nameView.setLayoutParams(nameLp);
        row.addView(nameView);

        TextView codeView = new TextView(context);
        codeView.setText("[" + item.getCode() + "]");
        codeView.setTextColor(isActive ? tokens.activeSelectionText : tokens.textMuted);
        codeView.setTextSize(11);
        codeView.setTypeface(Typeface.MONOSPACE);
        row.addView(codeView);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (listener != null) {
                    listener.onRegionSelected(item);
                }
            }
        });

        return row;
    }
}
