package org.tontos.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * First-Startup Out-of-Box-Experience (OOBE) Install Wizard.
 * Implements State Pattern via WizardStep enum.
 */
public class SetupWizardActivity extends Activity {

    private static final String PREFS_NAME = "tontos_prefs";
    private static final String KEY_SETUP_COMPLETED = "setup_completed";
    private static final String KEY_SYSTEM_LANGUAGE = "system_language";
    private static final String KEY_SYSTEM_REGION = "system_region";
    private static final String KEY_SELECTED_THEME = "selected_theme";
    private static final String KEY_DAILY_BUDGET = "daily_budget_mins";
    private static final String KEY_OTA_CHANNEL = "ota_update_channel";

    private static final int DEFAULT_BUDGET_MINUTES = 120;
    private static final int MIN_TOUCH_TARGET_DP = 48;

    private SharedPreferences preferences;
    private LinearLayout rootLayout;
    private TextView titleView;
    private TextView subtitleView;
    private View dividerView;
    private LinearLayout contentContainer;
    private LinearLayout bottomNavDock;
    private TextView prevButton;
    private TextView nextButton;
    private WizardStep currentStep = WizardStep.WELCOME;

    private LocaleItem selectedLocale = LocaleItem.DEFAULT;
    private RegionItem selectedRegion = LocaleManager.DEFAULT_REGION;
    private LanguageLocale selectedLanguage = LanguageLocale.EN_US;
    private ThemePreset selectedTheme = ThemePreset.AMOLED;
    private int selectedBudgetMinutes = DEFAULT_BUDGET_MINUTES;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int scaleInt = prefs.getInt("pref_font_scale", 100);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = scaleInt / 100.0f;
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedLang = preferences.getString(KEY_SYSTEM_LANGUAGE, "en");
        String savedRegion = preferences.getString(KEY_SYSTEM_REGION, "US");
        selectedLocale = LocaleManager.getLanguage(savedLang);
        selectedRegion = LocaleManager.getRegion(savedRegion);
        selectedLanguage = LanguageLocale.fromCode(selectedLocale.getTag());
        I18n.applyLocale(this, selectedLocale.getTag(), selectedRegion.getCode());

        ThemeTokens initTokens = ThemeTokens.get(selectedTheme);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(initTokens.bg);
        rootLayout.setPadding(50, 60, 50, 40);

        titleView = new TextView(this);
        titleView.setText("TontOS SETUP WIZARD");
        titleView.setTextColor(initTokens.accent);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        rootLayout.addView(titleView);

        subtitleView = new TextView(this);
        subtitleView.setText("Initial Device & Detox Configuration");
        subtitleView.setTextColor(initTokens.textMuted);
        subtitleView.setTextSize(13);
        subtitleView.setTypeface(Typeface.MONOSPACE);
        subtitleView.setPadding(0, 4, 0, 16);
        rootLayout.addView(subtitleView);

        float density = getResources().getDisplayMetrics().density;
        dividerView = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (16 * density + 0.5f));
        dividerView.setLayoutParams(divParams);
        dividerView.setBackgroundColor(initTokens.border);
        rootLayout.addView(dividerView);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        ));

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(contentContainer);
        rootLayout.addView(scroll);

        // Explicit bottom navigation dual-button dock
        bottomNavDock = new LinearLayout(this);
        bottomNavDock.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavDock.setGravity(Gravity.CENTER_VERTICAL);
        bottomNavDock.setPadding(0, dpToPx(12), 0, dpToPx(8));

        prevButton = new TextView(this);
        prevButton.setTextSize(13);
        prevButton.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        prevButton.setGravity(Gravity.CENTER);
        prevButton.setMinHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        prevButton.setMinimumHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        prevButton.setClickable(true);
        prevButton.setFocusable(true);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        prevLp.setMargins(0, 0, dpToPx(6), 0);
        prevButton.setLayoutParams(prevLp);
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigatePrevious();
            }
        });

        nextButton = new TextView(this);
        nextButton.setTextSize(13);
        nextButton.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setMinHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        nextButton.setMinimumHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        nextButton.setClickable(true);
        nextButton.setFocusable(true);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        nextLp.setMargins(dpToPx(6), 0, 0, 0);
        nextButton.setLayoutParams(nextLp);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateNext();
            }
        });

        bottomNavDock.addView(prevButton);
        bottomNavDock.addView(nextButton);
        rootLayout.addView(bottomNavDock);

        setContentView(rootLayout);
        hideSystemUI();
        renderStep();
    }

    private void hideSystemUI() {
        // No-op: compositor owns chrome.
    }

    private void navigatePrevious() {
        if (currentStep.getIndex() > 0) {
            currentStep = WizardStep.fromIndex(currentStep.getIndex() - 1);
            renderStep();
        }
    }

    private void navigateNext() {
        if (currentStep == WizardStep.COMPLETE) {
            completeSetup();
        } else {
            currentStep = WizardStep.fromIndex(currentStep.getIndex() + 1);
            renderStep();
        }
    }

    private void completeSetup() {
        // Seed home defaults: full pinned set + order + limit = visible size.
        // Ensures first boot from 0 shows all defaults; background can reuse.
        // Seed bg_allowed_apps = DEFAULT_PINNED + bg_policy whitelist so the
        // background policy never kills Phone/Messages on first boot.
        String pinnedOrder = android.text.TextUtils.join(",", MainActivity.DEFAULT_PINNED_PACKAGES);
        preferences.edit()
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .putString(KEY_SYSTEM_LANGUAGE, selectedLocale.getTag())
            .putString(KEY_SYSTEM_REGION, selectedRegion.getCode())
            .putString(KEY_SELECTED_THEME, selectedTheme.getId())
            .putInt(KEY_DAILY_BUDGET, selectedBudgetMinutes)
            .putString(KEY_OTA_CHANNEL, UpdateChannel.BETA.getCode())
            .putStringSet(MainActivity.KEY_PINNED_APPS, new java.util.HashSet<String>(MainActivity.DEFAULT_PINNED_PACKAGES))
            .putString(MainActivity.KEY_PINNED_APPS_ORDER, pinnedOrder)
            .putInt(MainActivity.KEY_HOME_APP_LIMIT, MainActivity.DEFAULT_PINNED_PACKAGES.size())
            .putString("bg_policy", "whitelist")
            .putStringSet("bg_allowed_apps", new java.util.HashSet<String>(MainActivity.DEFAULT_PINNED_PACKAGES))
            .apply();
        I18n.applyLocale(this, selectedLocale.getTag(), selectedRegion.getCode());

        Toast.makeText(SetupWizardActivity.this, "TontOS Initialized", Toast.LENGTH_SHORT).show();
        Intent homeIntent = new Intent(SetupWizardActivity.this, MainActivity.class);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(homeIntent);
        finish();
    }

    private void renderStep() {
        contentContainer.removeAllViews();
        ThemeTokens tokens = ThemeTokens.get(selectedTheme);
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";

        rootLayout.setBackgroundColor(tokens.bg);
        titleView.setText(I18n.get(I18n.KEY_WIZARD_TITLE, langCode));
        titleView.setTextColor(tokens.accent);
        subtitleView.setText(I18n.get(I18n.KEY_WIZARD_SUBTITLE, langCode));
        subtitleView.setTextColor(tokens.textMuted);
        dividerView.setBackgroundColor(tokens.border);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().setSystemBarsAppearance(
                    tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } catch (Throwable ignored) {}

        switch (currentStep) {
            case WELCOME:
                renderWelcome(tokens);
                break;
            case LANGUAGE_REGION:
                renderLanguageRegion(tokens);
                break;
            case THEME_PRESET:
                renderThemeSelection(tokens);
                break;
            case DETOX_BUDGET:
                renderBudgetSelection(tokens);
                break;
            case COMPLETE:
                renderSummary(tokens);
                break;
        }

        updateNavDock(tokens);
    }

    private CharSequence formatBulletText(String text, int accentColor) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        int idx = 0;
        while ((idx = text.indexOf('•', idx)) != -1) {
            ssb.setSpan(new ForegroundColorSpan(accentColor), idx, idx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            idx++;
        }
        if (text.startsWith("Configuration Summary:")) {
            ssb.setSpan(new ForegroundColorSpan(accentColor), 0, "Configuration Summary:".length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    private void updateNavDock(ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        if (currentStep == WizardStep.WELCOME) {
            prevButton.setVisibility(View.INVISIBLE);
            prevButton.setEnabled(false);
        } else {
            prevButton.setVisibility(View.VISIBLE);
            prevButton.setEnabled(true);
            prevButton.setText(I18n.get(I18n.KEY_PREVIOUS, langCode));
            prevButton.setTextColor(tokens.navAccent);
            prevButton.setBackground(createTactileButtonDrawable(tokens.navAccent, tokens.navBg, tokens.controlBgPressed));
        }

        if (currentStep == WizardStep.COMPLETE) {
            nextButton.setText(I18n.get(I18n.KEY_ENTER_HOME, langCode));
        } else if (currentStep == WizardStep.WELCOME) {
            nextButton.setText(I18n.get(I18n.KEY_BEGIN_SETUP, langCode));
        } else {
            String nextTitle;
            switch (currentStep) {
                case LANGUAGE_REGION:
                    nextTitle = I18n.get(I18n.KEY_THEME_PRESET, langCode).toUpperCase(Locale.ROOT);
                    break;
                case THEME_PRESET:
                    nextTitle = I18n.get(I18n.KEY_DAILY_BUDGET, langCode).toUpperCase(Locale.ROOT);
                    break;
                case DETOX_BUDGET:
                    nextTitle = I18n.get(I18n.KEY_WIZARD_FINALIZE, langCode);
                    break;
                default:
                    nextTitle = I18n.get(I18n.KEY_WIZARD_NEXT, langCode);
                    break;
            }
            nextButton.setText(I18n.get(I18n.KEY_NEXT, langCode) + nextTitle + " > ]");
        }
        nextButton.setTextColor(tokens.isLight ? Color.WHITE : tokens.navAccent);
        nextButton.setBackground(createTactileButtonDrawable(tokens.navAccent, tokens.isLight ? tokens.navAccent : tokens.navBg, tokens.controlBgPressed));
    }

    private void renderWelcome(ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        addHeader("TontOS v1.0-beta", tokens);

        TextView welcome = new TextView(this);
        welcome.setText(formatBulletText(I18n.get(I18n.KEY_WELCOME_BODY, langCode), tokens.accent));
        welcome.setTextColor(tokens.textPrimary);
        welcome.setTextSize(14);
        welcome.setTypeface(Typeface.MONOSPACE);
        welcome.setLineSpacing(4, 1.2f);
        contentContainer.addView(welcome);
    }

    private void renderLanguageRegion(final ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        addHeader(I18n.get(I18n.KEY_STEP1_TITLE, langCode), tokens);

        TextView info = new TextView(this);
        info.setText(I18n.get(I18n.KEY_STEP1_DESC, langCode));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(13);
        info.setTypeface(Typeface.MONOSPACE);
        info.setPadding(0, 0, 0, dpToPx(16));
        contentContainer.addView(info);

        addSelectorRow(I18n.get(I18n.KEY_LANGUAGE, langCode), selectedLocale.getDisplayName(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LanguagePickerDialog.show(
                    SetupWizardActivity.this,
                    tokens,
                    selectedLocale,
                    new LanguagePickerDialog.OnLocaleSelectedListener() {
                        @Override
                        public void onLocaleSelected(LocaleItem selected) {
                            selectedLocale = selected;
                            selectedLanguage = LanguageLocale.fromCode(selected.getTag());
                            preferences.edit().putString(KEY_SYSTEM_LANGUAGE, selected.getTag()).apply();
                            I18n.applyLocale(SetupWizardActivity.this, selected.getTag(), selectedRegion != null ? selectedRegion.getCode() : "US");
                            renderStep();
                            Toast.makeText(SetupWizardActivity.this, "Language: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        // Region selector deactivated per user requirement: "desactiva el selector de region, no lo requerimos. Dejalo comentado en el codigo"
        /*
        addSelectorRow(I18n.get(I18n.KEY_REGION, langCode), selectedRegion.getDisplayName() + " [" + selectedRegion.getCode() + "]", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RegionPickerDialog.show(
                    SetupWizardActivity.this,
                    tokens,
                    selectedRegion,
                    new RegionPickerDialog.OnRegionSelectedListener() {
                        @Override
                        public void onRegionSelected(RegionItem selected) {
                            selectedRegion = selected;
                            preferences.edit().putString(KEY_SYSTEM_REGION, selected.getCode()).apply();
                            I18n.applyLocale(SetupWizardActivity.this, selectedLocale.getTag(), selected.getCode());
                            renderStep();
                            Toast.makeText(SetupWizardActivity.this, "Region: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);
        */
    }

    private void renderThemeSelection(final ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        addHeader(I18n.get(I18n.KEY_STEP2_TITLE, langCode), tokens);

        TextView info = new TextView(this);
        String themeDesc = I18n.get(I18n.KEY_STEP2_DESC, langCode) + "\n\n"
            + "• AMOLED: " + I18n.get(I18n.KEY_THEME_AMOLED_DESC, langCode) + "\n"
            + "• AMBER: " + I18n.get(I18n.KEY_THEME_AMBER_DESC, langCode) + "\n"
            + "• E-INK: " + I18n.get(I18n.KEY_THEME_E_INK_DESC, langCode) + "\n"
            + "• SLATE: " + I18n.get(I18n.KEY_THEME_SLATE_DESC, langCode);
        info.setText(formatBulletText(themeDesc, tokens.accent));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(12);
        info.setTypeface(Typeface.MONOSPACE);
        info.setPadding(0, 0, 0, dpToPx(16));
        contentContainer.addView(info);

        addSelectorRow(I18n.get(I18n.KEY_THEME_PRESET, langCode), selectedTheme.getDisplayName(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String curLang = selectedLocale != null ? selectedLocale.getTag() : "en";
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.AMOLED.getId(), "AMOLED"));
                options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.AMBER.getId(), "AMBER"));
                options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.E_INK.getId(), "E-INK"));
                options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.SLATE.getId(), "SLATE"));

                SingleChoiceSelectorDialog.show(
                    SetupWizardActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_THEME_SELECT_TITLE, curLang),
                    options,
                    selectedTheme.getId(),
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            selectedTheme = ThemePreset.fromId(selected.id);
                            preferences.edit().putString(KEY_SELECTED_THEME, selectedTheme.getId()).apply();
                            renderStep();
                        }
                    }
                );
            }
        }, tokens);
    }

    private void renderBudgetSelection(final ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        addHeader(I18n.get(I18n.KEY_STEP3_TITLE, langCode), tokens);

        TextView info = new TextView(this);
        info.setText(I18n.get(I18n.KEY_STEP3_DESC, langCode));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(12);
        info.setTypeface(Typeface.MONOSPACE);
        info.setPadding(0, 0, 0, dpToPx(16));
        contentContainer.addView(info);

        String currentBudgetDisplay;
        if (selectedBudgetMinutes <= 0) {
            currentBudgetDisplay = I18n.get(I18n.KEY_BUDGET_OFF, langCode);
        } else if (selectedBudgetMinutes % 60 == 0) {
            currentBudgetDisplay = (selectedBudgetMinutes / 60) + "h";
        } else {
            currentBudgetDisplay = selectedBudgetMinutes + "m";
        }

        addSelectorRow(I18n.get(I18n.KEY_DAILY_BUDGET, langCode), currentBudgetDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("none", I18n.get(I18n.KEY_BUDGET_OFF, langCode)));
                options.add(new SingleChoiceSelectorDialog.Option("30m", "30m"));
                options.add(new SingleChoiceSelectorDialog.Option("1h", "1h"));
                options.add(new SingleChoiceSelectorDialog.Option("2h", "2h"));
                options.add(new SingleChoiceSelectorDialog.Option("3h", "3h"));
                options.add(new SingleChoiceSelectorDialog.Option("4h", "4h"));
                options.add(new SingleChoiceSelectorDialog.Option("6h", "6h"));
                options.add(new SingleChoiceSelectorDialog.Option("8h", "8h"));
                options.add(new SingleChoiceSelectorDialog.Option("12h", "12h"));
                options.add(new SingleChoiceSelectorDialog.Option("24h", "24h"));
                options.add(new SingleChoiceSelectorDialog.Option("custom", I18n.get(I18n.KEY_BUDGET_CUSTOM, langCode)));

                String curId = (selectedBudgetMinutes <= 0) ? "none" : (selectedBudgetMinutes % 60 == 0 ? (selectedBudgetMinutes / 60) + "h" : selectedBudgetMinutes + "m");
                SingleChoiceSelectorDialog.show(
                    SetupWizardActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_DAILY_BUDGET, langCode),
                    options,
                    curId,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int minutes = 0;
                            if ("none".equals(selected.id) || "disabled".equals(selected.id)) minutes = 0;
                            else if ("30m".equals(selected.id)) minutes = 30;
                            else if ("1h".equals(selected.id)) minutes = 60;
                            else if ("2h".equals(selected.id)) minutes = 120;
                            else if ("3h".equals(selected.id)) minutes = 180;
                            else if ("4h".equals(selected.id)) minutes = 240;
                            else if ("6h".equals(selected.id)) minutes = 360;
                            else if ("8h".equals(selected.id)) minutes = 480;
                            else if ("12h".equals(selected.id)) minutes = 720;
                            else if ("24h".equals(selected.id)) minutes = 1440;
                            else if ("custom".equals(selected.id)) { showCustomBudgetDialog(tokens); return; }

                            selectedBudgetMinutes = minutes;
                            preferences.edit().putInt(KEY_DAILY_BUDGET, minutes).apply();
                            renderStep();
                        }
                    }
                );
            }
        }, tokens);
    }

    private void showCustomBudgetDialog(final ThemeTokens tokens) {
        final String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(I18n.get(I18n.KEY_BUDGET_CUSTOM_HINT, langCode));
        if (selectedBudgetMinutes > 0) input.setText(String.valueOf(selectedBudgetMinutes));
        try { input.setSelection(input.getText() != null ? input.getText().length() : 0); } catch (Throwable ignored) {}
        new android.app.AlertDialog.Builder(this)
            .setTitle(I18n.get(I18n.KEY_DAILY_BUDGET, langCode))
            .setView(input)
            .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    try {
                        int v = Integer.parseInt(input.getText().toString().trim());
                        v = Math.max(1, Math.min(1440, v));
                        selectedBudgetMinutes = v;
                        preferences.edit().putInt(KEY_DAILY_BUDGET, v).apply();
                    } catch (Throwable ignored) {}
                    renderStep();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void renderSummary(ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        addHeader(I18n.get(I18n.KEY_STEP4_TITLE, langCode), tokens);

        String budgetLabel = (selectedBudgetMinutes <= 0)
            ? I18n.get(I18n.KEY_BUDGET_OFF, langCode)
            : (selectedBudgetMinutes < 60 ? selectedBudgetMinutes + "m" : (selectedBudgetMinutes / 60) + "h");

        TextView summary = new TextView(this);
        String text = I18n.format(
            I18n.KEY_SUMMARY_BODY_FMT,
            langCode,
            selectedLocale.getDisplayName(),
            selectedRegion.getDisplayName(),
            selectedRegion.getCode(),
            selectedTheme.getDisplayName(),
            budgetLabel,
            UpdateChannel.BETA.getCode()
        );
        summary.setText(formatBulletText(text, tokens.accent));
        summary.setTextColor(tokens.textPrimary);
        summary.setTextSize(14);
        summary.setTypeface(Typeface.MONOSPACE);
        summary.setLineSpacing(4, 1.2f);
        contentContainer.addView(summary);
    }

    private void addHeader(String text, ThemeTokens tokens) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextColor(tokens.accent);
        header.setTextSize(15);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 8, 0, 14);
        contentContainer.addView(header);
    }

    private void addSelectorRow(String label, String valueText, View.OnClickListener listener, ThemeTokens tokens) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(tokens.textMuted);
        labelView.setTextSize(13);
        labelView.setTypeface(Typeface.MONOSPACE);
        labelView.setSingleLine(true);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labelView.setPadding(0, dpToPx(8), 0, dpToPx(4));
        contentContainer.addView(labelView);

        String cleanVal = (valueText != null ? valueText.trim() : "");
        while (cleanVal.startsWith("[") && cleanVal.endsWith("]")) {
            cleanVal = cleanVal.substring(1, cleanVal.length() - 1).trim();
        }

        TextView triggerBtn = new TextView(this);
        triggerBtn.setText("[ " + cleanVal + "  ▼ ]");
        triggerBtn.setTextColor(tokens.textPrimary);
        triggerBtn.setTextSize(14);
        triggerBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        triggerBtn.setSingleLine(true);
        triggerBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        triggerBtn.setGravity(Gravity.CENTER_VERTICAL);
        triggerBtn.setMinHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        triggerBtn.setMinimumHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        triggerBtn.setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8));

        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(tokens.controlBgPressed);
        pressed.setCornerRadius(dpToPx(4));

        ColorDrawable normal = new ColorDrawable(Color.TRANSPARENT);

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        triggerBtn.setBackground(sld);

        triggerBtn.setClickable(true);
        triggerBtn.setFocusable(true);
        triggerBtn.setOnClickListener(listener);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnLp.setMargins(0, 0, 0, dpToPx(12));
        triggerBtn.setLayoutParams(btnLp);

        contentContainer.addView(triggerBtn);
    }

    private Drawable createTactileButtonDrawable(int strokeColor, int bgColor, int pressedBgColor) {
        StateListDrawable sld = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(pressedBgColor);
        pressed.setStroke(dpToPx(2), strokeColor);
        pressed.setCornerRadius(dpToPx(4));

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(bgColor);
        normal.setStroke(dpToPx(1), strokeColor);
        normal.setCornerRadius(dpToPx(4));

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (currentStep.getIndex() > 0) {
            navigatePrevious();
        } else {
            super.onBackPressed();
        }
    }
}
