package org.tontos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Minimalist AMOLED Post-Setup Quick Guide Dialog for TontOS.
 * Provides a skippable, 100% monospace overview of the phone's 5 essential features.
 * Built with AmoledDialog template.
 */
public final class QuickStartGuideDialog {

    public static final String PREF_GUIDE_SHOWN = "pref_quick_guide_shown";

    private QuickStartGuideDialog() {}

    public static void show(final Context context, final ThemeTokens tokens, final String langCode) {
        if (context == null) return;

        final AmoledDialog dlg = new AmoledDialog(context, tokens, I18n.get(I18n.KEY_GUIDE_HEADER, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        TextView subtitle = new TextView(context);
        subtitle.setText(I18n.get(I18n.KEY_GUIDE_SUBTITLE, langCode));
        subtitle.setTextColor(themeTokens.textMuted);
        subtitle.setTextSize(12f * fontScale);
        subtitle.setTypeface(Typeface.MONOSPACE);
        subtitle.setPadding(0, 0, 0, (int) (8 * density));
        dlg.addHeaderCustomView(subtitle);

        LinearLayout cardsContainer = dlg.getContentContainer();

        // Feature 1: Home & Drawer
        addCard(context, cardsContainer, themeTokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD1_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD1_DESC, langCode));

        // Feature 2: Background Task Termination
        addCard(context, cardsContainer, themeTokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD2_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD2_DESC, langCode));

        // Feature 3: Mindful Pause
        addCard(context, cardsContainer, themeTokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD3_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD3_DESC, langCode));

        // Feature 4: Detox Budget & Monochrome
        addCard(context, cardsContainer, themeTokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD4_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD4_DESC, langCode));

        // Feature 5: Ultra Shade
        addCard(context, cardsContainer, themeTokens, density, fontScale,
            I18n.get(I18n.KEY_GUIDE_CARD5_TITLE, langCode),
            I18n.get(I18n.KEY_GUIDE_CARD5_DESC, langCode));

        // Done / Start Button
        dlg.addActionButton(
            I18n.get(I18n.KEY_GUIDE_START, langCode),
            themeTokens.navAccent,
            themeTokens.border,
            true,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    markGuideCompleted(context);
                    dlg.dismiss();
                }
            }
        );

        dlg.show();
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
}
