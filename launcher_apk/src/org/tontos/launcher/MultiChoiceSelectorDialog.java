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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reusable AMOLED Fullscreen Multi-Choice Selector Modal Dialog.
 * Supports real-time filtering, individual item checkboxes, batch selection, and apply callback.
 */
public final class MultiChoiceSelectorDialog {

    public static class Item {
        public final String id;
        public final String label;
        public final String subtitle;
        final String lowerLabel;

        public Item(String id, String label) {
            this(id, label, null);
        }

        public Item(String id, String label, String subtitle) {
            this.id = id;
            this.label = label;
            this.subtitle = subtitle;
            this.lowerLabel = (label != null) ? label.toLowerCase(Locale.ROOT) : "";
        }
    }

    public interface OnMultiChoiceSelectedListener {
        void onSelected(Set<String> selectedIds);
    }

    private MultiChoiceSelectorDialog() {}

    public static void show(
        final Activity activity,
        final ThemeTokens tokens,
        final String title,
        final List<Item> items,
        final Set<String> initialSelectedIds,
        final String langCode,
        final OnMultiChoiceSelectedListener listener
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

        // Working copy of selections
        final Set<String> workingSelections = new HashSet<>(initialSelectedIds != null ? initialSelectedIds : Collections.<String>emptySet());

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
        titleView.setPadding(0, 0, 0, (int) (4 * density));
        root.addView(titleView);

        // Summary Counter
        final TextView summaryView = new TextView(activity);
        summaryView.setTextColor(tokens.textMuted);
        summaryView.setTextSize(12);
        summaryView.setTypeface(Typeface.MONOSPACE);
        summaryView.setPadding(0, 0, 0, (int) (12 * density));
        updateSummary(summaryView, workingSelections.size(), items != null ? items.size() : 0);
        root.addView(summaryView);

        View divider = new View(activity);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (12 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        // Search Filter Input
        final EditText searchBox = new EditText(activity);
        searchBox.setHint(I18n.get(I18n.KEY_SEARCH_APPS, langCode));
        searchBox.setHintTextColor(tokens.textMuted);
        searchBox.setTextColor(tokens.textPrimary);
        searchBox.setTextSize(13);
        searchBox.setTypeface(Typeface.MONOSPACE);
        searchBox.setSingleLine(true);
        searchBox.setMinimumHeight((int) (44 * density));
        searchBox.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));

        final GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke((int) (1 * density), tokens.border);
        searchBg.setCornerRadius((int) (4 * density));
        searchBox.setBackground(searchBg);

        searchBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                searchBg.setStroke((int) (1 * density), hasFocus ? tokens.accent : tokens.border);
            }
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.setMargins(0, 0, 0, (int) (10 * density));
        searchBox.setLayoutParams(searchLp);
        root.addView(searchBox);

        // Batch Action Row: [ SELECT ALL ] and [ DESELECT ALL ]
        LinearLayout batchRow = new LinearLayout(activity);
        batchRow.setOrientation(LinearLayout.HORIZONTAL);
        batchRow.setGravity(Gravity.CENTER_VERTICAL);
        batchRow.setPadding(0, 0, 0, (int) (10 * density));

        TextView selectAllBtn = new TextView(activity);
        selectAllBtn.setText(I18n.get(I18n.KEY_SELECT_ALL, langCode));
        selectAllBtn.setTextColor(tokens.navAccent);
        selectAllBtn.setTextSize(11);
        selectAllBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        selectAllBtn.setPadding(0, (int) (4 * density), (int) (16 * density), (int) (4 * density));
        selectAllBtn.setClickable(true);
        selectAllBtn.setFocusable(true);
        batchRow.addView(selectAllBtn);

        TextView deselectAllBtn = new TextView(activity);
        deselectAllBtn.setText(I18n.get(I18n.KEY_DESELECT_ALL, langCode));
        deselectAllBtn.setTextColor(tokens.textMuted);
        deselectAllBtn.setTextSize(11);
        deselectAllBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        deselectAllBtn.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
        deselectAllBtn.setClickable(true);
        deselectAllBtn.setFocusable(true);
        batchRow.addView(deselectAllBtn);

        root.addView(batchRow);

        // Scrollable List Container
        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        root.addView(scroll);

        final List<Item> safeItems = (items != null) ? items : Collections.<Item>emptyList();

        // Render helper
        final Runnable renderList = new Runnable() {
            @Override
            public void run() {
                String query = searchBox.getText() != null ? searchBox.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
                renderItems(activity, listContainer, safeItems, workingSelections, query, tokens, summaryView, density);
            }
        };

        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (Item item : safeItems) {
                    workingSelections.add(item.id);
                }
                updateSummary(summaryView, workingSelections.size(), safeItems.size());
                renderList.run();
            }
        });

        deselectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                workingSelections.clear();
                updateSummary(summaryView, 0, safeItems.size());
                renderList.run();
            }
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderList.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Initial render
        renderList.run();

        // Bottom Action Bar: [ CANCEL ] and [ APPLY ]
        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, (int) (16 * density), 0, 0);

        TextView cancelBtn = new TextView(activity);
        cancelBtn.setText(I18n.get(I18n.KEY_CANCEL, langCode));
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
            }
        });

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cancelLp.setMargins(0, 0, (int) (8 * density), 0);
        cancelBtn.setLayoutParams(cancelLp);
        bottomBar.addView(cancelBtn);

        TextView applyBtn = new TextView(activity);
        applyBtn.setText(I18n.get(I18n.KEY_APPLY, langCode));
        applyBtn.setTextColor(tokens.accent);
        applyBtn.setTextSize(13);
        applyBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        applyBtn.setGravity(Gravity.CENTER);
        applyBtn.setMinHeight((int) (48 * density));
        applyBtn.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));
        applyBtn.setClickable(true);
        applyBtn.setFocusable(true);
        applyBtn.setBackground(createButtonBg(tokens.controlBgPressed, tokens.accent, density));

        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                if (listener != null) {
                    listener.onSelected(workingSelections);
                }
            }
        });

        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        applyLp.setMargins((int) (8 * density), 0, 0, 0);
        applyBtn.setLayoutParams(applyLp);
        bottomBar.addView(applyBtn);

        root.addView(bottomBar);

        dlg.setContentView(root);
        dlg.show();
    }

    private static void updateSummary(TextView summaryView, int selectedCount, int totalCount) {
        summaryView.setText(String.format(Locale.ROOT, "[ %d / %d ]", selectedCount, totalCount));
    }

    private static void renderItems(
        final Context context,
        LinearLayout container,
        List<Item> items,
        final Set<String> workingSelections,
        String query,
        final ThemeTokens tokens,
        final TextView summaryView,
        final float density
    ) {
        container.removeAllViews();
        final int totalCount = items.size();

        for (final Item item : items) {
            if (!query.isEmpty() && !item.lowerLabel.contains(query) && !item.id.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            final boolean isSelected = workingSelections.contains(item.id);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (48 * density));
            row.setPadding((int) (4 * density), (int) (8 * density), (int) (4 * density), (int) (8 * density));
            row.setClickable(true);
            row.setFocusable(true);

            StateListDrawable rowBg = new StateListDrawable();
            GradientDrawable pressed = new GradientDrawable();
            pressed.setColor(tokens.controlBgPressed);
            pressed.setCornerRadius((int) (4 * density));
            rowBg.addState(new int[]{android.R.attr.state_pressed}, pressed);
            rowBg.addState(new int[]{android.R.attr.state_focused}, pressed);
            rowBg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            row.setBackground(rowBg);

            // Checkbox tag: [x] or [ ]
            TextView checkView = new TextView(context);
            checkView.setText(isSelected ? "[x] " : "[ ] ");
            checkView.setTextColor(isSelected ? tokens.accent : tokens.textMuted);
            checkView.setTextSize(14);
            checkView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            checkView.setPadding(0, 0, (int) (8 * density), 0);
            row.addView(checkView);

            // Label container
            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            textCol.setLayoutParams(textLp);

            TextView labelView = new TextView(context);
            labelView.setText(item.label != null ? item.label : item.id);
            labelView.setTextColor(isSelected ? tokens.textPrimary : tokens.textMuted);
            labelView.setTextSize(14);
            labelView.setTypeface(Typeface.MONOSPACE, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            textCol.addView(labelView);

            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                TextView subView = new TextView(context);
                subView.setText(item.subtitle);
                subView.setTextColor(tokens.textMuted);
                subView.setTextSize(11);
                subView.setTypeface(Typeface.MONOSPACE);
                textCol.addView(subView);
            }

            row.addView(textCol);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (workingSelections.contains(item.id)) {
                        workingSelections.remove(item.id);
                    } else {
                        workingSelections.add(item.id);
                    }
                    updateSummary(summaryView, workingSelections.size(), totalCount);
                    // Rerender row visually
                    boolean newSel = workingSelections.contains(item.id);
                    checkView.setText(newSel ? "[x] " : "[ ] ");
                    checkView.setTextColor(newSel ? tokens.accent : tokens.textMuted);
                    labelView.setTextColor(newSel ? tokens.textPrimary : tokens.textMuted);
                    labelView.setTypeface(Typeface.MONOSPACE, newSel ? Typeface.BOLD : Typeface.NORMAL);
                }
            });

            container.addView(row);
        }
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
