package org.tontos.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reusable AMOLED Fullscreen Multi-Choice Selector Modal Dialog.
 * Supports real-time filtering, individual item checkboxes, batch selection, and apply callback.
 * Built with AmoledDialog template.
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

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, title);
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        // Working copy of selections
        final Set<String> workingSelections = new HashSet<>(initialSelectedIds != null ? initialSelectedIds : Collections.<String>emptySet());
        final List<Item> safeItems = (items != null) ? items : Collections.<Item>emptyList();

        // Summary Counter: [ 3 / 10 ]
        final TextView summaryView = new TextView(activity);
        summaryView.setTextColor(themeTokens.textMuted);
        summaryView.setTextSize(12 * fontScale);
        summaryView.setTypeface(Typeface.MONOSPACE);
        summaryView.setPadding(0, 0, 0, (int) (8 * density));
        updateSummary(summaryView, workingSelections.size(), safeItems.size());
        dlg.addHeaderCustomView(summaryView);

        // Search Filter Input
        final EditText searchBox = new EditText(activity);
        searchBox.setHint(I18n.get(I18n.KEY_SEARCH_APPS, langCode));
        searchBox.setHintTextColor(themeTokens.textMuted);
        searchBox.setTextColor(themeTokens.textPrimary);
        searchBox.setTextSize(13 * fontScale);
        searchBox.setTypeface(Typeface.MONOSPACE);
        searchBox.setSingleLine(true);
        searchBox.setMinimumHeight((int) (44 * density));
        searchBox.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));

        final GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke((int) (1 * density), themeTokens.border);
        searchBg.setCornerRadius((int) (4 * density));
        searchBox.setBackground(searchBg);

        searchBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                searchBg.setStroke((int) (1 * density), hasFocus ? themeTokens.accent : themeTokens.border);
            }
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.setMargins(0, 0, 0, (int) (8 * density));
        searchBox.setLayoutParams(searchLp);
        dlg.addFixedSubHeaderView(searchBox);

        // Batch Action Row: [ SELECT ALL ] and [ DESELECT ALL ]
        LinearLayout batchRow = new LinearLayout(activity);
        batchRow.setOrientation(LinearLayout.HORIZONTAL);
        batchRow.setGravity(Gravity.CENTER_VERTICAL);
        batchRow.setPadding(0, 0, 0, (int) (8 * density));

        TextView selectAllBtn = new TextView(activity);
        selectAllBtn.setText(TactileUi.ensureBrackets(I18n.get(I18n.KEY_SELECT_ALL, langCode)));
        selectAllBtn.setTextColor(themeTokens.navAccent);
        selectAllBtn.setTextSize(11 * fontScale);
        selectAllBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        selectAllBtn.setPadding(0, (int) (4 * density), (int) (16 * density), (int) (4 * density));
        selectAllBtn.setClickable(true);
        selectAllBtn.setFocusable(true);
        batchRow.addView(selectAllBtn);

        TextView deselectAllBtn = new TextView(activity);
        deselectAllBtn.setText(TactileUi.ensureBrackets(I18n.get(I18n.KEY_DESELECT_ALL, langCode)));
        deselectAllBtn.setTextColor(themeTokens.textMuted);
        deselectAllBtn.setTextSize(11 * fontScale);
        deselectAllBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        deselectAllBtn.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
        deselectAllBtn.setClickable(true);
        deselectAllBtn.setFocusable(true);
        batchRow.addView(deselectAllBtn);

        dlg.addFixedSubHeaderView(batchRow);

        // List Container inside AmoledDialog scrollView
        final LinearLayout listContainer = dlg.getContentContainer();

        // Render helper
        final Runnable renderList = new Runnable() {
            @Override
            public void run() {
                String query = searchBox.getText() != null ? searchBox.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
                renderItems(activity, listContainer, safeItems, workingSelections, query, themeTokens, summaryView, density, fontScale);
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

        renderList.run();

        // Action Buttons: [ CANCEL ] and [ APPLY ]
        dlg.addActionButton(
            I18n.get(I18n.KEY_CANCEL, langCode),
            themeTokens.textMuted,
            themeTokens.border,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                }
            }
        );

        dlg.addActionButton(
            I18n.get(I18n.KEY_APPLY, langCode),
            themeTokens.accent,
            themeTokens.accent,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                    if (listener != null) {
                        listener.onSelected(workingSelections);
                    }
                }
            }
        );

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
        final float density,
        final float fontScale
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
            row.setBackground(TactileUi.createTouchFeedbackDrawable(context, tokens));

            // Checkbox tag: [x] or [ ]
            TextView checkView = new TextView(context);
            checkView.setText(isSelected ? "[x] " : "[ ] ");
            checkView.setTextColor(isSelected ? tokens.accent : tokens.textMuted);
            checkView.setTextSize(14 * fontScale);
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
            labelView.setTextSize(14 * fontScale);
            labelView.setTypeface(Typeface.MONOSPACE, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            textCol.addView(labelView);

            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                TextView subView = new TextView(context);
                subView.setText(item.subtitle);
                subView.setTextColor(tokens.textMuted);
                subView.setTextSize(11 * fontScale);
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
}
