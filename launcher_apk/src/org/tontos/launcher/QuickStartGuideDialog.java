package org.tontos.launcher;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
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

/**
 * Minimalist AMOLED Post-Setup Quick Guide Dialog for TontOS.
 * Provides a skippable, 100% monospace overview of the phone's 5 essential features.
 */
public final class QuickStartGuideDialog {

    public static final String PREF_GUIDE_SHOWN = "pref_quick_guide_shown";

    private QuickStartGuideDialog() {}

    public static void show(final Context context, final ThemeTokens tokens, final String langCode) {
        if (context == null) return;

        final Dialog dialog = new Dialog(context, android.R.style.Theme_NoTitleBar_Fullscreen);
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
        }

        float density = context.getResources().getDisplayMetrics().density;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        float fontScale = prefs.getInt("pref_font_scale", 100) / 100.0f;
        int pHoriz = (int) (24 * density);
        int pVert = (int) (32 * density);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(pHoriz, pVert, pHoriz, (int) (20 * density));

        // Header Title
        TextView header = new TextView(context);
        header.setText(I18n.get(I18n.KEY_GUIDE_HEADER, langCode));
        header.setTextColor(tokens.accent);
        header.setTextSize(16f * fontScale);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 0, 0, (int) (6 * density));
        root.addView(header);

        TextView subtitle = new TextView(context);
        subtitle.setText(I18n.get(I18n.KEY_GUIDE_SUBTITLE, langCode));
        subtitle.setTextColor(tokens.textMuted);
        subtitle.setTextSize(12f * fontScale);
        subtitle.setTypeface(Typeface.MONOSPACE);
        subtitle.setPadding(0, 0, 0, (int) (16 * density));
        root.addView(subtitle);

        View div = new View(context);
        div.setBackgroundColor(tokens.border);
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)));
        root.addView(div);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scrollLp.setMargins(0, (int) (12 * density), 0, (int) (12 * density));
        scroll.setLayoutParams(scrollLp);

        LinearLayout cardsContainer = new LinearLayout(context);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);

        // Feature 1: Home & Drawer
        addCard(context, cardsContainer, tokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD1_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD1_DESC, langCode));

        // Feature 2: Background Task Termination
        addCard(context, cardsContainer, tokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD2_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD2_DESC, langCode));

        // Feature 3: Mindful Pause
        addCard(context, cardsContainer, tokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD3_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD3_DESC, langCode));

        // Feature 4: Detox Budget & Monochrome
        addCard(context, cardsContainer, tokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD4_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD4_DESC, langCode));

        // Feature 5: Ultra Shade
        addCard(context, cardsContainer, tokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD5_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD5_DESC, langCode));

        scroll.addView(cardsContainer);
        root.addView(scroll);

        // Bottom Actions (Single Start Button)
        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER_VERTICAL);

        // Done / Start Button
        TextView doneBtn = new TextView(context);
        doneBtn.setText(I18n.get(I18n.KEY_GUIDE_START, langCode));
        doneBtn.setTextColor(tokens.isLight ? Color.WHITE : tokens.navAccent);
        doneBtn.setTextSize(14f * fontScale);
        doneBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        doneBtn.setGravity(Gravity.CENTER);
        doneBtn.setMinHeight((int) (48 * density));
        doneBtn.setMinimumHeight((int) (48 * density));
        doneBtn.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        doneBtn.setBackground(createButtonBg(tokens.navAccent, tokens.isLight ? tokens.navAccent : tokens.navBg, tokens.controlBgPressed, density));
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markGuideCompleted(context);
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneBtn.setLayoutParams(doneLp);
        buttonsRow.addView(doneBtn);

        root.addView(buttonsRow);

        dialog.setContentView(root);
        dialog.show();
    }

    private static void markGuideCompleted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_GUIDE_SHOWN, true).apply();
    }

    private static void addCard(Context context, LinearLayout parent, ThemeTokens tokens, float density, float fontScale, String title, String body) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(tokens.surface);
        card.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(tokens.surface);
        bg.setStroke((int) (1 * density), tokens.border);
        bg.setCornerRadius((int) (4 * density));
        card.setBackground(bg);

        TextView cardTitle = new TextView(context);
        cardTitle.setText(title);
        cardTitle.setTextColor(tokens.accent);
        cardTitle.setTextSize(13f * fontScale);
        cardTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cardTitle.setPadding(0, 0, 0, (int) (4 * density));
        card.addView(cardTitle);

        TextView cardBody = new TextView(context);
        cardBody.setText(body);
        cardBody.setTextColor(tokens.textPrimary);
        cardBody.setTextSize(12f * fontScale);
        cardBody.setTypeface(Typeface.MONOSPACE);
        cardBody.setLineSpacing(2, 1.15f);
        card.addView(cardBody);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, (int) (10 * density));
        card.setLayoutParams(cardLp);

        parent.addView(card);
    }

    private static StateListDrawable createButtonBg(int strokeColor, int bgColor, int pressedColor, float density) {
        StateListDrawable sld = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setStroke((int) (1 * density), strokeColor);
        pressed.setCornerRadius((int) (4 * density));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(bgColor);
        normal.setStroke((int) (1 * density), strokeColor);
        normal.setCornerRadius((int) (4 * density));

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}
