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
    private ThemePreset selectedTheme = ThemePreset.AMOLED;
    private int selectedBudgetMinutes = DEFAULT_BUDGET_MINUTES;
    private int selectedFontScale = 100;
    private boolean selectedClock24h = true;

    @Override
    protected void attachBaseContext(Context newBase) {
        int scaleInt = 100;
        try {
            SharedPreferences prefs = MainActivity.getSafeSharedPreferences(newBase, PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs != null) {
                scaleInt = prefs.getInt("pref_font_scale", 100);
            }
        } catch (Throwable ignored) {}
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = scaleInt / 100.0f;
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = MainActivity.getSafeSharedPreferences(this, PREFS_NAME, Context.MODE_PRIVATE);
        String savedLang = preferences.getString(KEY_SYSTEM_LANGUAGE, "en");
        String savedRegion = preferences.getString(KEY_SYSTEM_REGION, "US");
        selectedLocale = LocaleManager.getLanguage(savedLang);
        selectedRegion = LocaleManager.getRegion(savedRegion);
        selectedFontScale = preferences.getInt("pref_font_scale", 100);
        selectedClock24h = preferences.getBoolean("pref_clock_24h", android.text.format.DateFormat.is24HourFormat(this));
        I18n.applyLocaleAndScale(this, selectedLocale.getTag(), selectedRegion.getCode(), selectedFontScale / 100.0f);

        ThemeTokens initTokens = ThemeTokens.get(selectedTheme);

        // Ensure system debloat, launcher lock, and shade hardening execute on setup start
        SystemProvisioningManager.executeInitialProvisioning(this);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(initTokens.bg);
        rootLayout.setPadding(50, 60, 50, 40);

        titleView = new TextView(this);
        titleView.setText(I18n.get(I18n.KEY_WIZARD_TITLE, "en"));
        titleView.setTextColor(initTokens.accent);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        rootLayout.addView(titleView);

        subtitleView = new TextView(this);
        subtitleView.setText(I18n.get(I18n.KEY_WIZARD_SUBTITLE, "en"));
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
            .putInt("pref_font_scale", selectedFontScale)
            .putBoolean("pref_clock_24h", selectedClock24h)
            .putString(KEY_OTA_CHANNEL, UpdateChannel.BETA.getCode())
            .putStringSet(MainActivity.KEY_PINNED_APPS, new java.util.HashSet<String>(MainActivity.DEFAULT_PINNED_PACKAGES))
            .putString(MainActivity.KEY_PINNED_APPS_ORDER, pinnedOrder)
            .putInt(MainActivity.KEY_HOME_APP_LIMIT, MainActivity.DEFAULT_PINNED_PACKAGES.size())
            .putString("bg_policy", "whitelist")
            .putStringSet("bg_allowed_apps", new java.util.HashSet<String>(MainActivity.DEFAULT_PINNED_PACKAGES))
            .commit();
        I18n.applyLocaleAndScale(this, selectedLocale.getTag(), selectedRegion.getCode(), selectedFontScale / 100.0f);
        try {
            android.provider.Settings.System.putString(getContentResolver(), android.provider.Settings.System.TIME_12_24, selectedClock24h ? "24" : "12");
            SafeCommandExecutor.executePrivileged("settings", "put", "system", "time_12_24", selectedClock24h ? "24" : "12");
        } catch (Throwable ignored) {}

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
        float fontScale = selectedFontScale / 100.0f;
        titleView.setText(I18n.get(I18n.KEY_WIZARD_TITLE, langCode));
        titleView.setTextSize(20f * fontScale);
        titleView.setTextColor(tokens.accent);
        subtitleView.setText(I18n.get(I18n.KEY_WIZARD_SUBTITLE, langCode));
        subtitleView.setTextSize(13f * fontScale);
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
        float fontScale = selectedFontScale / 100.0f;
        prevButton.setTextSize(13f * fontScale);
        nextButton.setTextSize(13f * fontScale);

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
        } else if (currentStep == WizardStep.DETOX_BUDGET) {
            nextButton.setText("[ " + I18n.get(I18n.KEY_WIZARD_FINALIZE, langCode) + " > ]");
        } else {
            nextButton.setText("[ " + I18n.get(I18n.KEY_WIZARD_NEXT, langCode) + " > ]");
        }
        nextButton.setTextColor(tokens.isLight ? Color.WHITE : tokens.navAccent);
        nextButton.setBackground(createTactileButtonDrawable(tokens.navAccent, tokens.isLight ? tokens.navAccent : tokens.navBg, tokens.controlBgPressed));
    }

    private void renderWelcome(ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        float fontScale = selectedFontScale / 100.0f;
        addHeader("TontOS v1.0-beta", tokens);

        TextView welcome = new TextView(this);
        welcome.setText(formatBulletText(I18n.get(I18n.KEY_WELCOME_BODY, langCode), tokens.accent));
        welcome.setTextColor(tokens.textPrimary);
        welcome.setTextSize(14f * fontScale);
        welcome.setTypeface(Typeface.MONOSPACE);
        welcome.setLineSpacing(4, 1.2f);
        contentContainer.addView(welcome);
    }

    private void renderLanguageRegion(final ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        float fontScale = selectedFontScale / 100.0f;
        addHeader(I18n.get(I18n.KEY_STEP1_TITLE, langCode), tokens);

        TextView info = new TextView(this);
        info.setText(I18n.get(I18n.KEY_STEP1_DESC, langCode));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(13f * fontScale);
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
                            String defRegion = LocaleManager.getDefaultRegionForLanguage(selected.getTag());
                            selectedRegion = LocaleManager.getRegion(defRegion);
                            preferences.edit()
                                .putString(KEY_SYSTEM_LANGUAGE, selected.getTag())
                                .putString(KEY_SYSTEM_REGION, selectedRegion.getCode())
                                .apply();
                            I18n.applyLocaleAndScale(SetupWizardActivity.this, selected.getTag(), selectedRegion.getCode(), selectedFontScale / 100.0f);
                            renderStep();
                            Toast.makeText(SetupWizardActivity.this, "Language: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

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
                            I18n.applyLocaleAndScale(SetupWizardActivity.this, selectedLocale.getTag(), selected.getCode(), selectedFontScale / 100.0f);
                            renderStep();
                            Toast.makeText(SetupWizardActivity.this, "Region: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        String curFontScaleDisplay;
        if (selectedFontScale == 85) curFontScaleDisplay = I18n.get(I18n.KEY_FONT_SMALL, langCode);
        else if (selectedFontScale == 115) curFontScaleDisplay = I18n.get(I18n.KEY_FONT_LARGE, langCode);
        else if (selectedFontScale == 130) curFontScaleDisplay = I18n.get(I18n.KEY_FONT_XL, langCode);
        else curFontScaleDisplay = I18n.get(I18n.KEY_FONT_NORMAL, langCode);

        addSelectorRow(I18n.get(I18n.KEY_FONT_SIZE, langCode), curFontScaleDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("85", I18n.get(I18n.KEY_FONT_SMALL, langCode)));
                options.add(new SingleChoiceSelectorDialog.Option("100", I18n.get(I18n.KEY_FONT_NORMAL, langCode)));
                options.add(new SingleChoiceSelectorDialog.Option("115", I18n.get(I18n.KEY_FONT_LARGE, langCode)));
                options.add(new SingleChoiceSelectorDialog.Option("130", I18n.get(I18n.KEY_FONT_XL, langCode)));

                SingleChoiceSelectorDialog.show(
                    SetupWizardActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_FONT_SIZE, langCode),
                    options,
                    String.valueOf(selectedFontScale),
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            selectedFontScale = Integer.parseInt(selected.id);
                            preferences.edit().putInt("pref_font_scale", selectedFontScale).apply();
                            I18n.applyLocaleAndScale(SetupWizardActivity.this, selectedLocale.getTag(), selectedRegion != null ? selectedRegion.getCode() : "US", selectedFontScale / 100.0f);
                            renderStep();
                        }
                    }
                );
            }
        }, tokens);

        String clockLabel = selectedClock24h ? I18n.get(I18n.KEY_CLOCK_24H, langCode) : I18n.get(I18n.KEY_CLOCK_12H, langCode);
        addSelectorRow(I18n.get(I18n.KEY_CLOCK_FORMAT, langCode), clockLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("24", I18n.get(I18n.KEY_CLOCK_24H, langCode)));
                options.add(new SingleChoiceSelectorDialog.Option("12", I18n.get(I18n.KEY_CLOCK_12H, langCode)));

                SingleChoiceSelectorDialog.show(
                    SetupWizardActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_CLOCK_FORMAT, langCode),
                    options,
                    selectedClock24h ? "24" : "12",
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            selectedClock24h = "24".equals(selected.id);
                            preferences.edit().putBoolean("pref_clock_24h", selectedClock24h).apply();
                            try {
                                android.provider.Settings.System.putString(getContentResolver(), android.provider.Settings.System.TIME_12_24, selectedClock24h ? "24" : "12");
                                SafeCommandExecutor.executePrivileged("settings", "put", "system", "time_12_24", selectedClock24h ? "24" : "12");
                            } catch (Throwable ignored) {}
                            renderStep();
                        }
                    }
                );
            }
        }, tokens);
    }

    private void renderThemeSelection(final ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        float fontScale = selectedFontScale / 100.0f;
        addHeader(I18n.get(I18n.KEY_STEP2_TITLE, langCode), tokens);

        TextView info = new TextView(this);
        String themeDesc = I18n.get(I18n.KEY_STEP2_DESC, langCode) + "\n\n"
            + "• AMOLED: " + I18n.get(I18n.KEY_THEME_AMOLED_DESC, langCode) + "\n"
            + "• AMBER: " + I18n.get(I18n.KEY_THEME_AMBER_DESC, langCode) + "\n"
            + "• E-INK: " + I18n.get(I18n.KEY_THEME_E_INK_DESC, langCode) + "\n"
            + "• SLATE: " + I18n.get(I18n.KEY_THEME_SLATE_DESC, langCode);
        info.setText(formatBulletText(themeDesc, tokens.accent));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(12f * fontScale);
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
        float fontScale = selectedFontScale / 100.0f;
        addHeader(I18n.get(I18n.KEY_STEP3_TITLE, langCode), tokens);

        TextView info = new TextView(this);
        info.setText(I18n.get(I18n.KEY_STEP3_DESC, langCode));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(12f * fontScale);
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

                            selectedBudgetMinutes = minutes;
                            preferences.edit().putInt(KEY_DAILY_BUDGET, minutes).apply();
                            renderStep();
                        }
                    }
                );
            }
        }, tokens);
    }

    private void renderSummary(ThemeTokens tokens) {
        String langCode = selectedLocale != null ? selectedLocale.getTag() : "en";
        float fontScale = selectedFontScale / 100.0f;
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
        summary.setTextSize(14f * fontScale);
        summary.setTypeface(Typeface.MONOSPACE);
        summary.setLineSpacing(4, 1.2f);
        contentContainer.addView(summary);
    }

    private void addHeader(String text, ThemeTokens tokens) {
        float fontScale = selectedFontScale / 100.0f;
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextColor(tokens.accent);
        header.setTextSize(15f * fontScale);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 8, 0, 14);
        contentContainer.addView(header);
    }

    private void addSelectorRow(String label, String valueText, View.OnClickListener listener, ThemeTokens tokens) {
        float fontScale = selectedFontScale / 100.0f;
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(tokens.textMuted);
        labelView.setTextSize(13f * fontScale);
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
        triggerBtn.setTextSize(14f * fontScale);
        triggerBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        triggerBtn.setSingleLine(true);
        triggerBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        triggerBtn.setGravity(Gravity.CENTER_VERTICAL);
        triggerBtn.setMinHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        triggerBtn.setMinimumHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        triggerBtn.setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8));

        triggerBtn.setBackground(TactileUi.createTouchFeedbackDrawable(this, tokens));

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
        return TactileUi.createButtonDrawable(bgColor, pressedBgColor, strokeColor, dpToPx(1), dpToPx(4));
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
