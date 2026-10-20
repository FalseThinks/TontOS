package org.tontos.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TontOS Settings Management Activity.
 * Implements State Pattern via SettingsSection enum and Command Pattern for UI section builders.
 */
public class SettingsActivity extends Activity {

    public static class CachedApp {
        public final String pkg;
        public final String label;
        public final ResolveInfo ri;

        public CachedApp(String pkg, String label, ResolveInfo ri) {
            this.pkg = pkg;
            this.label = label != null ? label : pkg;
            this.ri = ri;
        }
    }

    private static final String PREFS_NAME = MainActivity.PREFS_NAME;
    private static final String KEY_THEME = MainActivity.KEY_SELECTED_THEME;
    private static final String KEY_LANGUAGE = MainActivity.KEY_SYSTEM_LANGUAGE;
    private static final String KEY_REGION = MainActivity.KEY_SYSTEM_REGION;
    private static final String KEY_OTA_CHANNEL = "ota_update_channel";
    private static final String KEY_BRIGHTNESS = "screen_brightness_percent";
    private static final String KEY_TIMEOUT = "screen_timeout_secs";
    private static final String KEY_BUDGET = MainActivity.KEY_DAILY_BUDGET_MINS;
    private static final String KEY_APP_LIMIT = MainActivity.KEY_HOME_APP_LIMIT;
    private static final String KEY_PINNED = MainActivity.KEY_PINNED_APPS;
    private static final String KEY_PINNED_ORDER = MainActivity.KEY_PINNED_APPS_ORDER;
    private static final String KEY_DATA_STATE = "mobile_data_state";

    private static final int MIN_TOUCH_TARGET_HEIGHT_DP = 48;
    private static final int MIN_TOUCH_TARGET_WIDTH_DP = 44;
    private static final int BUTTON_MARGIN_DP = 4;

    public interface OnSliderChangedListener {
        void onProgress(int progress, boolean fromUser);
        void onStop(int progress);
    }

    private LinearLayout rootLayout;
    private TextView headerTitle;
    private View headerDivider;
    private LinearLayout contentContainer;
    private ScrollView scrollView;
    private SharedPreferences preferences;
    private AudioManager audioManager;
    private Equalizer systemEqualizer;
    private SettingsSection currentSection = SettingsSection.MENU;
    private SettingsSubSection currentSubSection = SettingsSubSection.NONE;
    private int pendingRestoreScrollY = -1;

    public static final String PREF_LAST_SETTINGS_SECTION = "pref_last_settings_section";
    public static final String PREF_LAST_SETTINGS_SUB_SECTION = "pref_last_settings_sub_section";
    public static final String PREF_LAST_SETTINGS_SCROLL_Y = "pref_last_settings_scroll_y";

    // Algorithmic optimization: Cached ResolveInfo and resolved label index to prevent repeated PackageManager IPC scans
    private final Map<String, CachedApp> cachedActivityMap = new HashMap<>();

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int scaleInt = prefs.getInt("pref_font_scale", 100);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.fontScale = scaleInt / 100.0f;
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.BLACK);
        rootLayout.setPadding(50, 40, 50, 20);

        headerTitle = new TextView(this);
        headerTitle.setText(SettingsSection.MENU.getTitle());
        headerTitle.setTextColor(Color.parseColor("#44DD88"));
        headerTitle.setTextSize(18);
        headerTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        rootLayout.addView(headerTitle);

        float density = getResources().getDisplayMetrics().density;
        headerDivider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, (int) (8 * density + 0.5f), 0, (int) (12 * density + 0.5f));
        headerDivider.setLayoutParams(divParams);
        headerDivider.setBackgroundColor(Color.parseColor("#333333"));
        rootLayout.addView(headerDivider);

        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(contentContainer);
        rootLayout.addView(scrollView);
        setContentView(rootLayout);

        if (savedInstanceState != null) {
            String secName = savedInstanceState.getString("saved_section");
            if (secName != null) {
                try {
                    currentSection = SettingsSection.valueOf(secName);
                } catch (Exception ignored) {}
            }
            String subSecName = savedInstanceState.getString("saved_sub_section");
            if (subSecName != null) {
                try {
                    currentSubSection = SettingsSubSection.valueOf(subSecName);
                } catch (Exception ignored) {}
            }
            pendingRestoreScrollY = savedInstanceState.getInt("saved_scroll_y", -1);
        } else {
            String lastSec = preferences.getString(PREF_LAST_SETTINGS_SECTION, null);
            if (lastSec != null) {
                try {
                    currentSection = SettingsSection.valueOf(lastSec);
                } catch (Exception ignored) {}
            }
            String lastSubSec = preferences.getString(PREF_LAST_SETTINGS_SUB_SECTION, null);
            if (lastSubSec != null) {
                try {
                    currentSubSection = SettingsSubSection.valueOf(lastSubSec);
                } catch (Exception ignored) {}
            }
            pendingRestoreScrollY = preferences.getInt(PREF_LAST_SETTINGS_SCROLL_Y, -1);
        }
        if (getIntent() != null && getIntent().hasExtra("section")) {
            String extraSec = getIntent().getStringExtra("section");
            if (extraSec != null) {
                try {
                    currentSection = SettingsSection.valueOf(extraSec);
                    currentSubSection = SettingsSubSection.NONE;
                } catch (Exception ignored) {}
            }
        }
        preferences.edit().remove(PREF_LAST_SETTINGS_SCROLL_Y).apply();

        refreshActivitiesCache();
        buildSettingsUI();

        disableStatusBarExpansion();
        hideSystemUI();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentSection != null) {
            outState.putString("saved_section", currentSection.name());
        }
        if (currentSubSection != null) {
            outState.putString("saved_sub_section", currentSubSection.name());
        }
        if (scrollView != null) {
            outState.putInt("saved_scroll_y", scrollView.getScrollY());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        disableStatusBarExpansion();
        hideSystemUI();
        refreshActivitiesCache();
        buildSettingsUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            disableStatusBarExpansion();
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        // No-op: compositor owns chrome, avoid Pixel WindowInsets dependency.
    }

    private void disableStatusBarExpansion() {
        // TontOS owns its shade; no Pixel 'cmd statusbar' dependency.
    }

    private ThemeTokens currentTokens() {
        ThemePreset preset = ThemePreset.fromId(preferences.getString(KEY_THEME, ThemePreset.AMOLED.getId()));
        ThemeTokens tokens = ThemeTokens.get(preset);
        if (GrayscaleManager.isGrayscaleEnabled(this)) {
            return tokens.toMonochrome();
        }
        return tokens;
    }

    private void refreshActivitiesCache() {
        refreshActivitiesCache(false);
    }

    private void refreshActivitiesCache(boolean force) {
        if (!force && !cachedActivityMap.isEmpty()) return;
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

        cachedActivityMap.clear();
        for (ResolveInfo ri : activities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals("com.android.settings") || pkg.equals("com.android.stk")) {
                continue;
            }
            String label;
            try {
                CharSequence cs = ri.loadLabel(pm);
                label = cs != null ? cs.toString() : pkg;
            } catch (Throwable t) {
                label = pkg;
            }
            cachedActivityMap.put(pkg, new CachedApp(pkg, label, ri));
        }
    }

    void buildSettingsUI() {
        ThemeTokens tokens = currentTokens();
        GrayscaleManager.applyGrayscaleToWindow(this);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().setSystemBarsAppearance(
                    tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } catch (Throwable ignored) {}
        rootLayout.setBackgroundColor(tokens.bg);
        headerTitle.setTextColor(tokens.accent);
        headerDivider.setBackgroundColor(tokens.border);

        final int preservedScrollY = (scrollView != null && pendingRestoreScrollY < 0) ? scrollView.getScrollY() : -1;
        contentContainer.removeAllViews();

        switch (currentSection) {
            case MENU:
                buildCategoryMenu(tokens);
                break;
            case DISPLAY:
                buildDisplayThemeSection(tokens);
                break;
            case SOUND:
                buildSoundSection(tokens);
                break;
            case HOME_DRAWER:
                buildHomeDrawerSection(tokens);
                break;
            case CONNECTIVITY:
                buildConnectivitySection(tokens);
                break;
            case SYSTEM:
                buildSystemSection(tokens);
                break;
        }

        final int targetScrollY = pendingRestoreScrollY >= 0 ? pendingRestoreScrollY : preservedScrollY;
        pendingRestoreScrollY = -1;
        if (targetScrollY > 0 && scrollView != null) {
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    if (scrollView != null) {
                        scrollView.scrollTo(0, targetScrollY);
                    }
                }
            });
        }
    }

    // ==========================================
    // SECTION 0: CATEGORY MENU
    // ==========================================
    private void buildCategoryMenu(ThemeTokens tokens) {
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");

        TextView sub = new TextView(this);
        sub.setText(SettingsSection.MENU.getSubtitle(curLangCode));
        sub.setTextColor(tokens.textMuted);
        sub.setTextSize(13);
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setPadding(0, 0, 0, 16);
        contentContainer.addView(sub);

        addCategoryCard(SettingsSection.DISPLAY.getTitle(curLangCode), SettingsSection.DISPLAY.getSubtitle(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSection = SettingsSection.DISPLAY;
                currentSubSection = SettingsSubSection.NONE;
                preferences.edit().putString(PREF_LAST_SETTINGS_SECTION, currentSection.name()).apply();
                if (scrollView != null) scrollView.scrollTo(0, 0);
                buildSettingsUI();
            }
        }, tokens);

        addCategoryCard(SettingsSection.SOUND.getTitle(curLangCode), SettingsSection.SOUND.getSubtitle(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSection = SettingsSection.SOUND;
                currentSubSection = SettingsSubSection.NONE;
                preferences.edit().putString(PREF_LAST_SETTINGS_SECTION, currentSection.name()).apply();
                if (scrollView != null) scrollView.scrollTo(0, 0);
                buildSettingsUI();
            }
        }, tokens);

        addCategoryCard(SettingsSection.HOME_DRAWER.getTitle(curLangCode), SettingsSection.HOME_DRAWER.getSubtitle(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSection = SettingsSection.HOME_DRAWER;
                currentSubSection = SettingsSubSection.NONE;
                preferences.edit().putString(PREF_LAST_SETTINGS_SECTION, currentSection.name()).apply();
                if (scrollView != null) scrollView.scrollTo(0, 0);
                buildSettingsUI();
            }
        }, tokens);

        addCategoryCard(SettingsSection.CONNECTIVITY.getTitle(curLangCode), SettingsSection.CONNECTIVITY.getSubtitle(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSection = SettingsSection.CONNECTIVITY;
                currentSubSection = SettingsSubSection.NONE;
                preferences.edit().putString(PREF_LAST_SETTINGS_SECTION, currentSection.name()).apply();
                if (scrollView != null) scrollView.scrollTo(0, 0);
                buildSettingsUI();
            }
        }, tokens);

        addCategoryCard(SettingsSection.SYSTEM.getTitle(curLangCode), SettingsSection.SYSTEM.getSubtitle(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSection = SettingsSection.SYSTEM;
                currentSubSection = SettingsSubSection.NONE;
                preferences.edit().putString(PREF_LAST_SETTINGS_SECTION, currentSection.name()).apply();
                if (scrollView != null) scrollView.scrollTo(0, 0);
                buildSettingsUI();
            }
        }, tokens);

        float density = getResources().getDisplayMetrics().density;
        int minTargetPx = (int) (MIN_TOUCH_TARGET_HEIGHT_DP * density + 0.5f);

        TextView backBtn = new TextView(this);
        backBtn.setText(I18n.get(I18n.KEY_BACK, curLangCode));
        backBtn.setTextColor(tokens.navAccent);
        backBtn.setTextSize(14);
        backBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        backBtn.setMinHeight(minTargetPx);
        backBtn.setMinimumHeight(minTargetPx);
        backBtn.setGravity(Gravity.CENTER_VERTICAL);
        backBtn.setPadding(0, (int) (14 * density), 0, (int) (14 * density));
        backBtn.setClickable(true);
        backBtn.setFocusable(true);
        backBtn.setBackground(createTouchFeedbackDrawable(tokens));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preferences.edit().remove(PREF_LAST_SETTINGS_SECTION).remove(PREF_LAST_SETTINGS_SCROLL_Y).apply();
                finish();
            }
        });
        contentContainer.addView(backBtn);
    }

    // ==========================================
    // SECTION 1: DISPLAY, THEMES & LANGUAGE
    // ==========================================
    private void buildDisplayThemeSection(final ThemeTokens tokens) {
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        if (currentSubSection == SettingsSubSection.NONE) {
            addBackHeader(I18n.get(I18n.KEY_SEC_DISPLAY, curLangCode), tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_DISPLAY_STYLE, curLangCode), I18n.get(I18n.KEY_SUB_DISPLAY_STYLE_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.DISPLAY_STYLE;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_DISPLAY_LOCALE, curLangCode), I18n.get(I18n.KEY_SUB_DISPLAY_LOCALE_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.DISPLAY_LOCALE;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_DISPLAY_LOCKSCREEN, curLangCode), I18n.get(I18n.KEY_SUB_DISPLAY_LOCKSCREEN_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.DISPLAY_LOCKSCREEN;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_DISPLAY_DETOX, curLangCode), I18n.get(I18n.KEY_SUB_DISPLAY_DETOX_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.DISPLAY_DETOX;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            return;
        }

        switch (currentSubSection) {
            case DISPLAY_STYLE:
                buildDisplayStyleSubSection(tokens, curLangCode);
                break;
            case DISPLAY_LOCALE:
                buildDisplayLocaleSubSection(tokens, curLangCode);
                break;
            case DISPLAY_LOCKSCREEN:
                buildDisplayLockscreenSubSection(tokens, curLangCode);
                break;
            case DISPLAY_DETOX:
                buildDisplayDetoxSubSection(tokens, curLangCode);
                break;
            default:
                break;
        }
    }

    private void buildDisplayStyleSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_DISPLAY_STYLE, curLangCode), tokens);

        final boolean isGray = GrayscaleManager.isGrayscaleEnabled(this);
        final ThemePreset curPreset = ThemePreset.fromId(preferences.getString(KEY_THEME, ThemePreset.AMOLED.getId()));
        if (isGray) {
            String lockedLabel = curPreset.getDisplayName() + " [ " + I18n.get(I18n.KEY_DISABLED, curLangCode) + " ]";
            addSelectorRow(I18n.get(I18n.KEY_THEME_PRESET, curLangCode), lockedLabel, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(SettingsActivity.this, I18n.get(I18n.KEY_THEME_DISABLED_BY_GRAYSCALE, curLangCode), Toast.LENGTH_SHORT).show();
                }
            }, tokens);
        } else {
            addSelectorRow(I18n.get(I18n.KEY_THEME_PRESET, curLangCode), curPreset.getDisplayName(), true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.AMOLED.getId(), "AMOLED"));
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.AMBER.getId(), "Amber CRT"));
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.E_INK.getId(), "E-Ink Paper"));
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.SLATE.getId(), "Slate Midnight"));

                    SingleChoiceSelectorDialog.show(
                        SettingsActivity.this,
                        tokens,
                        I18n.get(I18n.KEY_THEME_SELECT_TITLE, curLangCode),
                        options,
                        curPreset.getId(),
                        new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                            @Override
                            public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                                ThemePreset chosen = ThemePreset.fromId(selected.id);
                                preferences.edit().putString(KEY_THEME, chosen.getId()).apply();
                                buildSettingsUI();
                                Toast.makeText(SettingsActivity.this, "Theme: " + chosen.getDisplayName(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    );
                }
            }, tokens);
        }

        addToggleRow(I18n.get(I18n.KEY_GRAYSCALE_MODE, curLangCode), isGray ? "[ ON ]" : "[ OFF ]", isGray, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nextState = !isGray;
                GrayscaleManager.setGrayscaleEnabled(SettingsActivity.this, nextState);
                GrayscaleManager.applyGrayscaleToWindow(SettingsActivity.this);
                buildSettingsUI();
            }
        }, tokens);

        int currentBrightness = preferences.getInt(KEY_BRIGHTNESS, 50);
        addSliderRow(I18n.get(I18n.KEY_BRIGHTNESS, curLangCode), currentBrightness, 10, 100, new OnSliderChangedListener() {
            @Override
            public void onProgress(int progress, boolean fromUser) {
                applyBrightness(progress);
            }
            @Override
            public void onStop(int progress) {
                preferences.edit().putInt(KEY_BRIGHTNESS, progress).apply();
                applySystemBrightness(progress);
            }
        }, tokens);

        final int curFontScale = preferences.getInt("pref_font_scale", 100);
        String curFontScaleDisplay;
        if (curFontScale == 85) curFontScaleDisplay = I18n.get(I18n.KEY_FONT_SMALL, curLangCode);
        else if (curFontScale == 115) curFontScaleDisplay = I18n.get(I18n.KEY_FONT_LARGE, curLangCode);
        else if (curFontScale == 130) curFontScaleDisplay = I18n.get(I18n.KEY_FONT_XL, curLangCode);
        else curFontScaleDisplay = I18n.get(I18n.KEY_FONT_NORMAL, curLangCode);

        addSelectorRow(I18n.get(I18n.KEY_FONT_SIZE, curLangCode), curFontScaleDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("85", I18n.get(I18n.KEY_FONT_SMALL, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("100", I18n.get(I18n.KEY_FONT_NORMAL, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("115", I18n.get(I18n.KEY_FONT_LARGE, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("130", I18n.get(I18n.KEY_FONT_XL, curLangCode)));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_FONT_SIZE, curLangCode),
                    options,
                    String.valueOf(curFontScale),
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int val = Integer.parseInt(selected.id);
                            int currentScroll = scrollView != null ? scrollView.getScrollY() : 0;
                            preferences.edit()
                                .putInt("pref_font_scale", val)
                                .putString(PREF_LAST_SETTINGS_SECTION, currentSection.name())
                                .putInt(PREF_LAST_SETTINGS_SCROLL_Y, currentScroll)
                                .apply();
                            Toast.makeText(SettingsActivity.this, I18n.get(I18n.KEY_FONT_SIZE, curLangCode) + ": " + selected.label, Toast.LENGTH_SHORT).show();
                            recreate();
                        }
                    }
                );
            }
        }, tokens);

        final int currentTimeout = preferences.getInt(KEY_TIMEOUT, 30);
        String currentTimeoutDisplay;
        if (currentTimeout <= 0) {
            currentTimeoutDisplay = I18n.get(I18n.KEY_TIMEOUT_NONE, curLangCode);
        } else if (currentTimeout >= 60) {
            currentTimeoutDisplay = (currentTimeout / 60) + "m";
        } else {
            currentTimeoutDisplay = currentTimeout + "s";
        }

        addSelectorRow(I18n.get(I18n.KEY_TIMEOUT, curLangCode), currentTimeoutDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("0", I18n.get(I18n.KEY_TIMEOUT_NONE, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("15", "15s"));
                options.add(new SingleChoiceSelectorDialog.Option("30", "30s"));
                options.add(new SingleChoiceSelectorDialog.Option("60", "1m"));
                options.add(new SingleChoiceSelectorDialog.Option("120", "2m"));
                options.add(new SingleChoiceSelectorDialog.Option("300", "5m"));
                options.add(new SingleChoiceSelectorDialog.Option("600", "10m"));

                String curId = String.valueOf(Math.max(0, currentTimeout));
                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_TIMEOUT, curLangCode),
                    options,
                    curId,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int secs = Integer.parseInt(selected.id);
                            preferences.edit().putInt(KEY_TIMEOUT, secs).apply();
                            applyScreenTimeout(secs);
                            buildSettingsUI();
                        }
                    }
                );
            }
        }, tokens);
    }

    private void buildDisplayLocaleSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_DISPLAY_LOCALE, curLangCode), tokens);

        final LocaleItem curLang = LocaleManager.getLanguage(curLangCode);
        addSelectorRow(I18n.get(I18n.KEY_LANGUAGE, curLangCode), curLang.getDisplayName(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LanguagePickerDialog.show(
                    SettingsActivity.this,
                    tokens,
                    curLang,
                    new LanguagePickerDialog.OnLocaleSelectedListener() {
                        @Override
                        public void onLocaleSelected(LocaleItem selected) {
                            String defaultRegion = LocaleManager.getDefaultRegionForLanguage(selected.getTag());
                            preferences.edit()
                                .putString(KEY_LANGUAGE, selected.getTag())
                                .putString(MainActivity.KEY_SYSTEM_LANGUAGE, selected.getTag())
                                .putString(KEY_REGION, defaultRegion)
                                .putString(MainActivity.KEY_SYSTEM_REGION, defaultRegion)
                                .apply();
                            I18n.applyLocale(SettingsActivity.this, selected.getTag(), defaultRegion);
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, "Language: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        final String curRegionCode = preferences.getString(KEY_REGION, "US");
        final RegionItem curRegion = LocaleManager.getRegion(curRegionCode);
        addSelectorRow(I18n.get(I18n.KEY_REGION, curLangCode), curRegion.getDisplayName() + " [" + curRegion.getCode() + "]", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RegionPickerDialog.show(
                    SettingsActivity.this,
                    tokens,
                    curRegion,
                    new RegionPickerDialog.OnRegionSelectedListener() {
                        @Override
                        public void onRegionSelected(RegionItem selected) {
                            preferences.edit()
                                .putString(KEY_REGION, selected.getCode())
                                .putString(MainActivity.KEY_SYSTEM_REGION, selected.getCode())
                                .apply();
                            I18n.applyLocale(SettingsActivity.this, preferences.getString(KEY_LANGUAGE, "en"), selected.getCode());
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, "Region: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);
    }

    private void buildDisplayLockscreenSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_DISPLAY_LOCKSCREEN, curLangCode), tokens);

        final boolean lockEnabled = LockScreenManager.isLockScreenEnabled(this);
        addToggleRow(I18n.get(I18n.KEY_LOCKSCREEN_ENABLE, curLangCode), lockEnabled ? "[ ON ]" : "[ OFF ]", lockEnabled, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LockScreenManager.setLockScreenEnabled(SettingsActivity.this, !lockEnabled);
                buildSettingsUI();
            }
        }, tokens);

        if (lockEnabled) {
            final LockClockStyle curClockStyle = LockScreenManager.getClockStyle(this);
            String clockStyleDisplay;
            switch (curClockStyle) {
                case BIG_MONOSPACE: clockStyleDisplay = I18n.get(I18n.KEY_LOCK_CLOCK_BIG, curLangCode); break;
                case VERTICAL_STACKED: clockStyleDisplay = I18n.get(I18n.KEY_LOCK_CLOCK_STACKED, curLangCode); break;
                case TERMINAL_RETRO: clockStyleDisplay = I18n.get(I18n.KEY_LOCK_CLOCK_TERMINAL, curLangCode); break;
                case COMPACT_SINGLE: clockStyleDisplay = I18n.get(I18n.KEY_LOCK_CLOCK_COMPACT, curLangCode); break;
                case THEME_DEFAULT:
                default: clockStyleDisplay = I18n.get(I18n.KEY_LOCK_CLOCK_DEFAULT, curLangCode); break;
            }

            addSelectorRow(I18n.get(I18n.KEY_LOCK_CLOCK_STYLE, curLangCode), clockStyleDisplay, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                    options.add(new SingleChoiceSelectorDialog.Option(LockClockStyle.THEME_DEFAULT.getId(), I18n.get(I18n.KEY_LOCK_CLOCK_DEFAULT, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option(LockClockStyle.BIG_MONOSPACE.getId(), I18n.get(I18n.KEY_LOCK_CLOCK_BIG, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option(LockClockStyle.VERTICAL_STACKED.getId(), I18n.get(I18n.KEY_LOCK_CLOCK_STACKED, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option(LockClockStyle.TERMINAL_RETRO.getId(), I18n.get(I18n.KEY_LOCK_CLOCK_TERMINAL, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option(LockClockStyle.COMPACT_SINGLE.getId(), I18n.get(I18n.KEY_LOCK_CLOCK_COMPACT, curLangCode)));

                    SingleChoiceSelectorDialog.show(
                        SettingsActivity.this,
                        tokens,
                        I18n.get(I18n.KEY_LOCK_CLOCK_STYLE, curLangCode),
                        options,
                        curClockStyle.getId(),
                        new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                            @Override
                            public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                                LockScreenManager.setClockStyle(SettingsActivity.this, LockClockStyle.fromId(selected.id));
                                buildSettingsUI();
                            }
                        }
                    );
                }
            }, tokens);

            final String curThemeOverride = LockScreenManager.getThemeOverride(this);
            String themeOverrideDisplay;
            if (LockScreenManager.THEME_MATCH_ACTIVE.equalsIgnoreCase(curThemeOverride)) {
                themeOverrideDisplay = I18n.get(I18n.KEY_LOCK_THEME_MATCH, curLangCode);
            } else {
                ThemePreset p = ThemePreset.fromId(curThemeOverride);
                themeOverrideDisplay = p != null ? p.getDisplayName() : curThemeOverride;
            }

            addSelectorRow(I18n.get(I18n.KEY_LOCK_THEME_OVERRIDE, curLangCode), themeOverrideDisplay, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                    options.add(new SingleChoiceSelectorDialog.Option(LockScreenManager.THEME_MATCH_ACTIVE, I18n.get(I18n.KEY_LOCK_THEME_MATCH, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.AMOLED.getId(), "AMOLED Black"));
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.AMBER.getId(), "Amber CRT"));
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.E_INK.getId(), "E-Ink Paper"));
                    options.add(new SingleChoiceSelectorDialog.Option(ThemePreset.SLATE.getId(), "Slate Midnight"));

                    SingleChoiceSelectorDialog.show(
                        SettingsActivity.this,
                        tokens,
                        I18n.get(I18n.KEY_LOCK_THEME_OVERRIDE, curLangCode),
                        options,
                        curThemeOverride,
                        new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                            @Override
                            public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                                LockScreenManager.setThemeOverride(SettingsActivity.this, selected.id);
                                buildSettingsUI();
                            }
                        }
                    );
                }
            }, tokens);

            final boolean showDetox = LockScreenManager.isShowDetox(this);
            addToggleRow(I18n.get(I18n.KEY_LOCK_SHOW_DETOX, curLangCode), showDetox ? "[ ON ]" : "[ OFF ]", showDetox, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LockScreenManager.setShowDetox(SettingsActivity.this, !showDetox);
                    buildSettingsUI();
                }
            }, tokens);

            final boolean showBattery = LockScreenManager.isShowBattery(this);
            addToggleRow(I18n.get(I18n.KEY_LOCK_SHOW_BATTERY, curLangCode), showBattery ? "[ ON ]" : "[ OFF ]", showBattery, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LockScreenManager.setShowBattery(SettingsActivity.this, !showBattery);
                    buildSettingsUI();
                }
            }, tokens);

            String ownerInfo = LockScreenManager.getOwnerInfo(this);
            String ownerDisplay = TextUtils.isEmpty(ownerInfo) ? "[ NONE ]" : (ownerInfo.length() > 20 ? ownerInfo.substring(0, 18) + "..." : ownerInfo);
            addSelectorRow(I18n.get(I18n.KEY_LOCK_OWNER_INFO, curLangCode), ownerDisplay, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLockOwnerDialog(tokens, curLangCode);
                }
            }, tokens);

            final LockShortcut curLeft = LockScreenManager.getLeftShortcut(this);
            addSelectorRow(I18n.get(I18n.KEY_LOCK_SHORTCUT_LEFT, curLangCode), getLockShortcutDisplay(curLeft, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLockShortcutPicker(true, curLeft, tokens, curLangCode);
                }
            }, tokens);

            final LockShortcut curRight = LockScreenManager.getRightShortcut(this);
            addSelectorRow(I18n.get(I18n.KEY_LOCK_SHORTCUT_RIGHT, curLangCode), getLockShortcutDisplay(curRight, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLockShortcutPicker(false, curRight, tokens, curLangCode);
                }
            }, tokens);

            addButtonRow(I18n.get(I18n.KEY_LOCK_TEST_BTN, curLangCode), "TEST", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent testIntent = new Intent(SettingsActivity.this, LockScreenActivity.class);
                    testIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(testIntent);
                }
            }, tokens);
        }
    }

    private void buildDisplayDetoxSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_DISPLAY_DETOX, curLangCode), tokens);

        final int currentBudgetMins = preferences.getInt(KEY_BUDGET, MainActivity.DEFAULT_BUDGET_MINUTES);
        String currentBudgetDisplay;
        if (currentBudgetMins <= 0) {
            currentBudgetDisplay = I18n.get(I18n.KEY_BUDGET_OFF, curLangCode);
        } else if (currentBudgetMins % 60 == 0) {
            currentBudgetDisplay = (currentBudgetMins / 60) + "h";
        } else {
            currentBudgetDisplay = currentBudgetMins + "m";
        }

        addSelectorRow(I18n.get(I18n.KEY_DAILY_BUDGET, curLangCode), currentBudgetDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("none", I18n.get(I18n.KEY_BUDGET_OFF, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("1h", "1h"));
                options.add(new SingleChoiceSelectorDialog.Option("2h", "2h"));
                options.add(new SingleChoiceSelectorDialog.Option("3h", "3h"));
                options.add(new SingleChoiceSelectorDialog.Option("4h", "4h"));

                String curId = (currentBudgetMins <= 0) ? "none" : (currentBudgetMins % 60 == 0 ? (currentBudgetMins / 60) + "h" : currentBudgetMins + "m");
                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_DAILY_BUDGET, curLangCode),
                    options,
                    curId,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int mins = 0;
                            if ("none".equals(selected.id) || "disabled".equals(selected.id)) mins = 0;
                            else if ("1h".equals(selected.id)) mins = 60;
                            else if ("2h".equals(selected.id)) mins = 120;
                            else if ("3h".equals(selected.id)) mins = 180;
                            else if ("4h".equals(selected.id)) mins = 240;

                            preferences.edit().putInt(KEY_BUDGET, mins).apply();
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, "Budget: " + selected.label, Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        final boolean curClock24 = preferences.getBoolean("pref_clock_24h", android.text.format.DateFormat.is24HourFormat(this));
        String clockLabel = curClock24 ? I18n.get(I18n.KEY_CLOCK_24H, curLangCode) : I18n.get(I18n.KEY_CLOCK_12H, curLangCode);
        addSelectorRow(I18n.get(I18n.KEY_CLOCK_FORMAT, curLangCode), clockLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("24", I18n.get(I18n.KEY_CLOCK_24H, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("12", I18n.get(I18n.KEY_CLOCK_12H, curLangCode)));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_CLOCK_FORMAT, curLangCode),
                    options,
                    curClock24 ? "24" : "12",
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            boolean is24 = "24".equals(selected.id);
                            preferences.edit().putBoolean("pref_clock_24h", is24).apply();
                            try {
                                android.provider.Settings.System.putString(getContentResolver(), android.provider.Settings.System.TIME_12_24, is24 ? "24" : "12");
                                SafeCommandExecutor.executePrivileged("settings", "put", "system", "time_12_24", is24 ? "24" : "12");
                            } catch (Throwable ignored) {}
                            buildSettingsUI();
                        }
                    }
                );
            }
        }, tokens);

        final int curBatteryMode = BatteryProtectionManager.getMode(this);
        addSelectorRow(I18n.get(I18n.KEY_BATTERY_PROTECTION, curLangCode), BatteryProtectionManager.getSettingsDisplay(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option(
                    "100", BatteryProtectionManager.getOptionDisplay(BatteryProtectionManager.MODE_100, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option(
                    "80", BatteryProtectionManager.getOptionDisplay(BatteryProtectionManager.MODE_80, curLangCode)));

                String curId = curBatteryMode == BatteryProtectionManager.MODE_80 ? "80" : "100";
                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_BATTERY_PROTECTION, curLangCode),
                    options,
                    curId,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int targetMode = "80".equals(selected.id) ? BatteryProtectionManager.MODE_80 : BatteryProtectionManager.MODE_100;
                            BatteryProtectionManager.ModeResult result =
                                BatteryProtectionManager.setMode(SettingsActivity.this, targetMode);
                            if (result == BatteryProtectionManager.ModeResult.APPLIED) {
                                buildSettingsUI();
                            }
                            Toast.makeText(
                                SettingsActivity.this,
                                BatteryProtectionManager.getModeResultDisplay(result, curLangCode),
                                Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                );
            }
        }, tokens);

        final boolean glyphSupported = NothingGlyphManager.isGlyphHardwarePresent();
        if (glyphSupported) {
            final boolean glyphMaster = NothingGlyphManager.isMasterEnabled(this);
            addToggleRow(I18n.get(I18n.KEY_GLYPH_INTERFACE, curLangCode), glyphMaster ? "[ ON ]" : "[ OFF ]", glyphMaster, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean nextState = !glyphMaster;
                    NothingGlyphManager.setMasterEnabled(SettingsActivity.this, nextState);
                    buildSettingsUI();
                }
            }, tokens);

            if (glyphMaster) {
                final boolean flipActive = NothingGlyphManager.isFlipToGlyphEnabled(this);
                addToggleRow("  " + I18n.get(I18n.KEY_FLIP_TO_GLYPH, curLangCode), flipActive ? "[ ON ]" : "[ OFF ]", flipActive, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NothingGlyphManager.setFlipToGlyphEnabled(SettingsActivity.this, !flipActive);
                        buildSettingsUI();
                    }
                }, tokens);

                final boolean essentialActive = NothingGlyphManager.isEssentialGlyphEnabled(this);
                addToggleRow("  " + I18n.get(I18n.KEY_ESSENTIAL_GLYPH, curLangCode), essentialActive ? "[ ON ]" : "[ OFF ]", essentialActive, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NothingGlyphManager.setEssentialGlyphEnabled(SettingsActivity.this, !essentialActive);
                        buildSettingsUI();
                    }
                }, tokens);
            }
        }
    }

    private String getLockShortcutDisplay(LockShortcut shortcut, String curLangCode) {
        if (shortcut == null) return I18n.get(I18n.KEY_LOCK_ACTION_NONE, curLangCode);
        switch (shortcut) {
            case TORCH: return I18n.get(I18n.KEY_LOCK_ACTION_TORCH, curLangCode);
            case CAMERA: return I18n.get(I18n.KEY_LOCK_ACTION_CAMERA, curLangCode);
            case PHONE: return I18n.get(I18n.KEY_LOCK_ACTION_PHONE, curLangCode);
            case BROWSER: return I18n.get(I18n.KEY_LOCK_ACTION_BROWSER, curLangCode);
            case NONE:
            default: return I18n.get(I18n.KEY_LOCK_ACTION_NONE, curLangCode);
        }
    }

    private void showLockShortcutPicker(final boolean isLeft, LockShortcut current, final ThemeTokens tokens, final String curLangCode) {
        List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
        options.add(new SingleChoiceSelectorDialog.Option(LockShortcut.NONE.getId(), I18n.get(I18n.KEY_LOCK_ACTION_NONE, curLangCode)));
        options.add(new SingleChoiceSelectorDialog.Option(LockShortcut.TORCH.getId(), I18n.get(I18n.KEY_LOCK_ACTION_TORCH, curLangCode)));
        options.add(new SingleChoiceSelectorDialog.Option(LockShortcut.CAMERA.getId(), I18n.get(I18n.KEY_LOCK_ACTION_CAMERA, curLangCode)));
        options.add(new SingleChoiceSelectorDialog.Option(LockShortcut.PHONE.getId(), I18n.get(I18n.KEY_LOCK_ACTION_PHONE, curLangCode)));
        options.add(new SingleChoiceSelectorDialog.Option(LockShortcut.BROWSER.getId(), I18n.get(I18n.KEY_LOCK_ACTION_BROWSER, curLangCode)));

        String title = isLeft ? I18n.get(I18n.KEY_LOCK_SHORTCUT_LEFT, curLangCode) : I18n.get(I18n.KEY_LOCK_SHORTCUT_RIGHT, curLangCode);
        SingleChoiceSelectorDialog.show(
            this,
            tokens,
            title,
            options,
            current.getId(),
            new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                @Override
                public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                    LockShortcut chosen = LockShortcut.fromId(selected.id);
                    if (isLeft) {
                        LockScreenManager.setLeftShortcut(SettingsActivity.this, chosen);
                    } else {
                        LockScreenManager.setRightShortcut(SettingsActivity.this, chosen);
                    }
                    buildSettingsUI();
                }
            }
        );
    }

    private void showLockOwnerDialog(final ThemeTokens tokens, final String curLangCode) {
        float density = getResources().getDisplayMetrics().density;
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(14);
        input.setTextColor(tokens.textPrimary);
        input.setBackgroundColor(tokens.controlBgNormal);
        input.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        input.setText(LockScreenManager.getOwnerInfo(this));
        input.setHint(I18n.get(I18n.KEY_LOCK_OWNER_HINT, curLangCode));
        input.setHintTextColor(tokens.textMuted);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (20 * density), (int) (12 * density), (int) (20 * density), (int) (8 * density));
        layout.addView(input);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(I18n.get(I18n.KEY_LOCK_OWNER_INFO, curLangCode));
        builder.setView(layout);
        builder.setPositiveButton(I18n.get(I18n.KEY_CONFIRM, curLangCode), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String text = input.getText().toString();
                LockScreenManager.setOwnerInfo(SettingsActivity.this, text);
                buildSettingsUI();
            }
        });
        builder.setNegativeButton(I18n.get(I18n.KEY_CANCEL, curLangCode), null);
        builder.show();
    }

    // ==========================================
    // SECTION 2: SOUND & VOLUME
    // ==========================================
    private void buildSoundSection(ThemeTokens tokens) {
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        if (currentSubSection == SettingsSubSection.NONE) {
            addBackHeader(SettingsSection.SOUND.getTitle(curLangCode), tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_SOUND_VOLUMES, curLangCode), I18n.get(I18n.KEY_SUB_SOUND_VOLUMES_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.SOUND_VOLUMES;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_SOUND_EQUALIZER, curLangCode), I18n.get(I18n.KEY_SUB_SOUND_EQUALIZER_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.SOUND_EQUALIZER;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            return;
        }

        switch (currentSubSection) {
            case SOUND_VOLUMES:
                buildSoundVolumesSubSection(tokens, curLangCode);
                break;
            case SOUND_EQUALIZER:
                buildSoundEqualizerSubSection(tokens, curLangCode);
                break;
            default:
                break;
        }
    }

    private void buildSoundVolumesSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_SOUND_VOLUMES, curLangCode), tokens);

        int maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curMedia = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int mediaPercent = (int) Math.round(((double) curMedia / (double) Math.max(1, maxMedia)) * 100);
        addSliderRow(I18n.get(I18n.KEY_MEDIA_VOL, curLangCode), mediaPercent, 0, 100, new OnSliderChangedListener() {
            @Override
            public void onProgress(int progress, boolean fromUser) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int target = (int) Math.round((progress / 100.0) * max);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            }
            @Override public void onStop(int progress) {}
        }, tokens);

        int maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
        int curRing = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        int ringPercent = (int) Math.round(((double) curRing / (double) Math.max(1, maxRing)) * 100);
        addSliderRow(I18n.get(I18n.KEY_RING_VOL, curLangCode), ringPercent, 0, 100, new OnSliderChangedListener() {
            @Override
            public void onProgress(int progress, boolean fromUser) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                int target = (int) Math.round((progress / 100.0) * max);
                audioManager.setStreamVolume(AudioManager.STREAM_RING, target, AudioManager.FLAG_SHOW_UI);
            }
            @Override public void onStop(int progress) {}
        }, tokens);

        int maxNotif = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
        int curNotif = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        int notifPercent = (int) Math.round(((double) curNotif / (double) Math.max(1, maxNotif)) * 100);
        addSliderRow(I18n.get(I18n.KEY_NOTIFICATION_VOL, curLangCode), notifPercent, 0, 100, new OnSliderChangedListener() {
            @Override
            public void onProgress(int progress, boolean fromUser) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
                int target = (int) Math.round((progress / 100.0) * max);
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, target, AudioManager.FLAG_SHOW_UI);
                if (target > 0) {
                    preferences.edit().putInt("pref_user_notif_vol", target).apply();
                }
            }
            @Override public void onStop(int progress) {}
        }, tokens);

        int maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        int curAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int alarmPercent = (int) Math.round(((double) curAlarm / (double) Math.max(1, maxAlarm)) * 100);
        addSliderRow(I18n.get(I18n.KEY_ALARM_VOL, curLangCode), alarmPercent, 0, 100, new OnSliderChangedListener() {
            @Override
            public void onProgress(int progress, boolean fromUser) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int target = (int) Math.round((progress / 100.0) * max);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, AudioManager.FLAG_SHOW_UI);
            }
            @Override public void onStop(int progress) {}
        }, tokens);

        int ringerMode = audioManager.getRingerMode();
        final String currentMode;
        final String currentModeLabel;
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            currentMode = "VIBRATE";
            currentModeLabel = I18n.get(I18n.KEY_RINGER_VIBRATE, curLangCode);
        } else if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            currentMode = "SILENT";
            currentModeLabel = I18n.get(I18n.KEY_RINGER_SILENT, curLangCode);
        } else {
            currentMode = "NORMAL";
            currentModeLabel = I18n.get(I18n.KEY_RINGER_NORMAL, curLangCode);
        }

        addSelectorRow(I18n.get(I18n.KEY_RINGER_PROFILE, curLangCode), currentModeLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("NORMAL", I18n.get(I18n.KEY_RINGER_NORMAL, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("VIBRATE", I18n.get(I18n.KEY_RINGER_VIBRATE, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("SILENT", I18n.get(I18n.KEY_RINGER_SILENT, curLangCode)));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_RINGER_PROFILE, curLangCode),
                    options,
                    currentMode,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            if ("VIBRATE".equals(selected.id)) {
                                audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                            } else if ("SILENT".equals(selected.id)) {
                                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                            } else {
                                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                            }
                            buildSettingsUI();
                        }
                    }
                );
            }
        }, tokens);

        final NotificationDigestManager.DeliveryMode curMode = NotificationDigestManager.getDeliveryMode(this);
        String notifModeLabel;
        switch (curMode) {
            case SCHEDULED_DIGEST:
                notifModeLabel = I18n.get(I18n.KEY_NOTIF_DIGEST, curLangCode);
                break;
            case STARRED_ONLY:
                notifModeLabel = I18n.get(I18n.KEY_NOTIF_STARRED, curLangCode);
                break;
            case IMMEDIATE:
            default:
                notifModeLabel = I18n.get(I18n.KEY_NOTIF_IMMEDIATE, curLangCode);
                break;
        }

        addSelectorRow(I18n.get(I18n.KEY_NOTIFICATION_DELIVERY_TITLE, curLangCode), notifModeLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option(NotificationDigestManager.DeliveryMode.IMMEDIATE.name(), I18n.get(I18n.KEY_NOTIF_IMMEDIATE, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option(NotificationDigestManager.DeliveryMode.SCHEDULED_DIGEST.name(), I18n.get(I18n.KEY_NOTIF_DIGEST, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option(NotificationDigestManager.DeliveryMode.STARRED_ONLY.name(), I18n.get(I18n.KEY_NOTIF_STARRED, curLangCode)));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_NOTIFICATION_DELIVERY_TITLE, curLangCode),
                    options,
                    curMode.name(),
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            try {
                                NotificationDigestManager.DeliveryMode newMode = NotificationDigestManager.DeliveryMode.valueOf(selected.id);
                                NotificationDigestManager.setDeliveryMode(SettingsActivity.this, newMode);
                                NotificationDigestManager.syncDoNotDisturb(SettingsActivity.this, newMode);
                                buildSettingsUI();
                            } catch (Exception ignored) {}
                        }
                    }
                );
            }
        }, tokens);

        String ringToneLabel = getToneLabel(RingtoneManager.TYPE_RINGTONE, curLangCode);
        addSelectorRow(I18n.get(I18n.KEY_RINGTONE_TITLE, curLangCode), ringToneLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTonePickerModal(RingtoneManager.TYPE_RINGTONE, I18n.get(I18n.KEY_RINGTONE_TITLE, curLangCode), tokens, curLangCode);
            }
        }, tokens);

        String notifToneLabel = getToneLabel(RingtoneManager.TYPE_NOTIFICATION, curLangCode);
        addSelectorRow(I18n.get(I18n.KEY_NOTIFICATION_SOUND_TITLE, curLangCode), notifToneLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTonePickerModal(RingtoneManager.TYPE_NOTIFICATION, I18n.get(I18n.KEY_NOTIFICATION_SOUND_TITLE, curLangCode), tokens, curLangCode);
            }
        }, tokens);

        String alarmToneLabel = getToneLabel(RingtoneManager.TYPE_ALARM, curLangCode);
        addSelectorRow(I18n.get(I18n.KEY_ALARM_SOUND_TITLE, curLangCode), alarmToneLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTonePickerModal(RingtoneManager.TYPE_ALARM, I18n.get(I18n.KEY_ALARM_SOUND_TITLE, curLangCode), tokens, curLangCode);
            }
        }, tokens);
    }

    private void buildSoundEqualizerSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_SOUND_EQUALIZER, curLangCode), tokens);

        final boolean eqEnabled = preferences.getBoolean("pref_eq_enabled", true);
        addToggleRow(I18n.get(I18n.KEY_EQUALIZER, curLangCode), eqEnabled ? "[ ON ]" : "[ OFF ]", eqEnabled, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nextState = !eqEnabled;
                preferences.edit().putBoolean("pref_eq_enabled", nextState).apply();
                setEqualizerEnabled(nextState);
                buildSettingsUI();
            }
        }, tokens);

        if (eqEnabled) {
            final String curEqPreset = preferences.getString("pref_eq_preset", "flat");
            String eqPresetLabel;
            if ("bass".equals(curEqPreset)) eqPresetLabel = I18n.get(I18n.KEY_EQ_BASS, curLangCode);
            else if ("vocal".equals(curEqPreset)) eqPresetLabel = I18n.get(I18n.KEY_EQ_VOCAL, curLangCode);
            else if ("treble".equals(curEqPreset)) eqPresetLabel = I18n.get(I18n.KEY_EQ_TREBLE, curLangCode);
            else if ("rock".equals(curEqPreset)) eqPresetLabel = I18n.get(I18n.KEY_EQ_ROCK, curLangCode);
            else if ("custom".equals(curEqPreset)) eqPresetLabel = I18n.get(I18n.KEY_EQ_CUSTOM, curLangCode);
            else eqPresetLabel = I18n.get(I18n.KEY_EQ_FLAT, curLangCode);

            addSelectorRow(I18n.get(I18n.KEY_EQ_PRESET, curLangCode), eqPresetLabel, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                    options.add(new SingleChoiceSelectorDialog.Option("flat", I18n.get(I18n.KEY_EQ_FLAT, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option("bass", I18n.get(I18n.KEY_EQ_BASS, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option("vocal", I18n.get(I18n.KEY_EQ_VOCAL, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option("treble", I18n.get(I18n.KEY_EQ_TREBLE, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option("rock", I18n.get(I18n.KEY_EQ_ROCK, curLangCode)));
                    options.add(new SingleChoiceSelectorDialog.Option("custom", I18n.get(I18n.KEY_EQ_CUSTOM, curLangCode)));

                    SingleChoiceSelectorDialog.show(
                        SettingsActivity.this,
                        tokens,
                        I18n.get(I18n.KEY_EQ_PRESET, curLangCode),
                        options,
                        curEqPreset,
                        new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                            @Override
                            public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                                preferences.edit().putString("pref_eq_preset", selected.id).apply();
                                int[] presetBands;
                                if ("bass".equals(selected.id)) presetBands = new int[]{5, 3, 0, 0, 0};
                                else if ("vocal".equals(selected.id)) presetBands = new int[]{-2, 0, 4, 2, 0};
                                else if ("treble".equals(selected.id)) presetBands = new int[]{0, 0, 0, 3, 5};
                                else if ("rock".equals(selected.id)) presetBands = new int[]{4, 2, -1, 2, 4};
                                else presetBands = new int[]{0, 0, 0, 0, 0};

                                if (!"custom".equals(selected.id)) {
                                    SharedPreferences.Editor ed = preferences.edit();
                                    for (int b = 0; b < 5; b++) {
                                        ed.putInt("pref_eq_band_" + b, presetBands[b]);
                                        applyEqualizerBand(b, presetBands[b]);
                                    }
                                    ed.apply();
                                }
                                buildSettingsUI();
                            }
                        }
                    );
                }
            }, tokens);

            String[] bandLabels = {"60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"};
            for (int b = 0; b < 5; b++) {
                final int bandIndex = b;
                final String bandLabel = bandLabels[b];
                final int currentDb = preferences.getInt("pref_eq_band_" + b, 0);
                applyEqualizerBand(bandIndex, currentDb);
                addEqBandSliderRow(bandLabel, bandIndex, currentDb, tokens);
            }
        }
    }

    // ==========================================
    // SECTION 3: HOME SCREEN & APP DRAWER
    // ==========================================
    private void buildHomeDrawerSection(final ThemeTokens tokens) {
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        if (currentSubSection == SettingsSubSection.NONE) {
            addBackHeader(SettingsSection.HOME_DRAWER.getTitle(curLangCode), tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_HOME_CONFIG, curLangCode), I18n.get(I18n.KEY_SUB_HOME_CONFIG_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.HOME_CONFIG;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_HOME_PINNED, curLangCode), I18n.get(I18n.KEY_SUB_HOME_PINNED_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.HOME_PINNED;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_HOME_DRAWER_ALL, curLangCode), I18n.get(I18n.KEY_SUB_HOME_DRAWER_ALL_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.HOME_DRAWER_ALL;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            return;
        }

        switch (currentSubSection) {
            case HOME_CONFIG:
                buildHomeConfigSubSection(tokens, curLangCode);
                break;
            case HOME_PINNED:
                buildHomePinnedSubSection(tokens, curLangCode);
                break;
            case HOME_DRAWER_ALL:
                buildHomeDrawerAllSubSection(tokens, curLangCode);
                break;
            default:
                break;
        }
    }

    private void buildHomeConfigSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_HOME_CONFIG, curLangCode), tokens);

        final int currentLimit = preferences.getInt(KEY_APP_LIMIT, MainActivity.DEFAULT_HOME_APP_LIMIT);
        final String limitDisplay = (currentLimit >= 999)
            ? I18n.get(I18n.KEY_APP_LIMIT_UNLIMITED, curLangCode)
            : String.valueOf(currentLimit);

        addSelectorRow(I18n.get(I18n.KEY_APP_LIMIT_TITLE, curLangCode), limitDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("3", "3"));
                options.add(new SingleChoiceSelectorDialog.Option("4", "4"));
                options.add(new SingleChoiceSelectorDialog.Option("5", "5"));
                options.add(new SingleChoiceSelectorDialog.Option("6", "6"));
                options.add(new SingleChoiceSelectorDialog.Option("8", "8"));
                options.add(new SingleChoiceSelectorDialog.Option("10", "10"));
                options.add(new SingleChoiceSelectorDialog.Option("999", I18n.get(I18n.KEY_APP_LIMIT_UNLIMITED, curLangCode)));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_APP_LIMIT_TITLE, curLangCode),
                    options,
                    String.valueOf(currentLimit),
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int val = Integer.parseInt(selected.id);
                            preferences.edit().putInt(KEY_APP_LIMIT, val).apply();
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, I18n.get(I18n.KEY_APP_LIMIT_TITLE, curLangCode) + ": " + selected.label, Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        final int frictionSecs = preferences.getInt("pref_friction_seconds", 0);
        final String frictionDisplay = frictionSecs > 0 ? (frictionSecs + "s") : "Off";

        addSelectorRow(I18n.get(I18n.KEY_FRICTION_TITLE, curLangCode), frictionDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("0", "Off"));
                options.add(new SingleChoiceSelectorDialog.Option("3", "3s"));
                options.add(new SingleChoiceSelectorDialog.Option("5", "5s"));
                options.add(new SingleChoiceSelectorDialog.Option("10", "10s"));

                float density = getResources().getDisplayMetrics().density;
                TextView helpBox = new TextView(SettingsActivity.this);
                helpBox.setText(I18n.get(I18n.KEY_FRICTION_HELP, curLangCode));
                helpBox.setTextColor(tokens.textMuted);
                helpBox.setTextSize(12);
                helpBox.setTypeface(Typeface.MONOSPACE);
                helpBox.setPadding(0, 0, 0, (int) (12 * density));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_FRICTION_TITLE, curLangCode),
                    options,
                    String.valueOf(frictionSecs),
                    helpBox,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int val = Integer.parseInt(selected.id);
                            preferences.edit().putInt("pref_friction_seconds", val).apply();
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, I18n.get(I18n.KEY_FRICTION_TITLE, curLangCode) + ": " + selected.label, Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        addSelectorRow(I18n.get(I18n.KEY_APP_PRIVACY_GUARD, curLangCode), I18n.get(I18n.KEY_OPEN, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppPrivacyGuard.showAllAppsPrivacyDialog(SettingsActivity.this, tokens, curLangCode);
            }
        }, tokens);

        final String curBgPolicy = preferences.getString("bg_policy", "whitelist");
        String bgPolicyDisplay;
        if ("strict".equals(curBgPolicy)) bgPolicyDisplay = I18n.get(I18n.KEY_BG_STRICT, curLangCode);
        else if ("allowed".equals(curBgPolicy)) bgPolicyDisplay = I18n.get(I18n.KEY_BG_ALLOWED_ALL, curLangCode);
        else bgPolicyDisplay = I18n.get(I18n.KEY_BG_WHITELIST, curLangCode);

        addSelectorRow(I18n.get(I18n.KEY_BG_POLICY, curLangCode), bgPolicyDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("whitelist", I18n.get(I18n.KEY_BG_WHITELIST, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("strict", I18n.get(I18n.KEY_BG_STRICT, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("allowed", I18n.get(I18n.KEY_BG_ALLOWED_ALL, curLangCode)));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_BG_POLICY, curLangCode),
                    options,
                    curBgPolicy,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            preferences.edit().putString("bg_policy", selected.id).apply();
                            MainActivity.applyBackgroundPolicy(SettingsActivity.this);
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, I18n.get(I18n.KEY_BG_POLICY, curLangCode) + ": " + selected.label, Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        if ("whitelist".equals(curBgPolicy)) {
            addButtonRow(I18n.get(I18n.KEY_BG_APPS_CONFIG, curLangCode), I18n.get(I18n.KEY_OPEN, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBackgroundAppsConfigDialog(tokens);
                }
            }, tokens);
        }

        addSelectorRow(I18n.get(I18n.KEY_SHADE_TILES_TITLE, curLangCode), getShadeTilesSummary(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showShadeTilesDialog(tokens, curLangCode);
            }
        }, tokens);
    }

    private void buildHomePinnedSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_HOME_PINNED, curLangCode), tokens);

        final int currentLimit = preferences.getInt(KEY_APP_LIMIT, MainActivity.DEFAULT_HOME_APP_LIMIT);
        final Set<String> pinned = new HashSet<>(preferences.getStringSet(KEY_PINNED, MainActivity.DEFAULT_PINNED_PACKAGES));

        String orderCsv = preferences.getString(KEY_PINNED_ORDER, null);
        final List<String> orderedPinned = new ArrayList<>();
        if (orderCsv != null && !orderCsv.trim().isEmpty()) {
            for (String p : orderCsv.split(",")) {
                String clean = p.trim();
                if (pinned.contains(clean) && cachedActivityMap.containsKey(clean) && !orderedPinned.contains(clean)) {
                    orderedPinned.add(clean);
                }
            }
        }
        for (String p : pinned) {
            if (cachedActivityMap.containsKey(p) && !orderedPinned.contains(p)) {
                orderedPinned.add(p);
            }
        }

        if (currentLimit < 999 && orderedPinned.size() > currentLimit) {
            while (orderedPinned.size() > currentLimit) {
                String removed = orderedPinned.remove(orderedPinned.size() - 1);
                pinned.remove(removed);
            }
            saveOrderAndRefresh(orderedPinned, pinned);
            return;
        }

        TextView countView = new TextView(this);
        String limitStr = (currentLimit >= 999) ? I18n.get(I18n.KEY_APP_LIMIT_UNLIMITED, curLangCode) : String.valueOf(currentLimit);
        countView.setText(orderedPinned.size() + " / " + limitStr + " pinned apps");
        countView.setTextColor(tokens.textMuted);
        countView.setTextSize(12);
        countView.setTypeface(Typeface.MONOSPACE);
        countView.setPadding(0, 0, 0, 12);
        contentContainer.addView(countView);

        for (int i = 0; i < orderedPinned.size(); i++) {
            final int index = i;
            final String pkg = orderedPinned.get(i);
            CachedApp app = cachedActivityMap.get(pkg);
            if (app == null) continue;

            final String label = app.label;

            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(0, 6, 0, 6);

            TextView title = new TextView(this);
            title.setText((index + 1) + ". " + label);
            title.setTextColor(tokens.textPrimary);
            title.setTextSize(13);
            title.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            title.setLayoutParams(tLp);
            title.setClickable(true);
            title.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppPrivacyGuard.showAppPrivacySheet(SettingsActivity.this, tokens, pkg, label, curLangCode, new Runnable() {
                        @Override
                        public void run() {
                            buildSettingsUI();
                        }
                    });
                }
            });
            itemRow.addView(title);

            if (index > 0) {
                TextView upBtn = createReorderButton("[^]", tokens.textPrimary, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Collections.swap(orderedPinned, index, index - 1);
                        saveOrderAndRefresh(orderedPinned, pinned);
                    }
                }, tokens);
                itemRow.addView(upBtn);
            }

            if (index < orderedPinned.size() - 1) {
                TextView downBtn = createReorderButton("[v]", tokens.textPrimary, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Collections.swap(orderedPinned, index, index + 1);
                        saveOrderAndRefresh(orderedPinned, pinned);
                    }
                }, tokens);
                itemRow.addView(downBtn);
            }

            TextView removeBtn = createReorderButton("[x]", tokens.isLight ? Color.parseColor("#CC0000") : Color.parseColor("#FF5555"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pinned.remove(pkg);
                    orderedPinned.remove(index);
                    saveOrderAndRefresh(orderedPinned, pinned);
                }
            }, tokens);
            itemRow.addView(removeBtn);

            contentContainer.addView(itemRow);
        }
        if (orderedPinned.isEmpty()) {
            addInfoRow("Pinned Apps", "No apps currently pinned", tokens);
        }
    }

    private void buildHomeDrawerAllSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_HOME_DRAWER_ALL, curLangCode), tokens);

        final int currentLimit = preferences.getInt(KEY_APP_LIMIT, MainActivity.DEFAULT_HOME_APP_LIMIT);
        final Set<String> pinned = new HashSet<>(preferences.getStringSet(KEY_PINNED, MainActivity.DEFAULT_PINNED_PACKAGES));

        String orderCsv = preferences.getString(KEY_PINNED_ORDER, null);
        final List<String> orderedPinned = new ArrayList<>();
        if (orderCsv != null && !orderCsv.trim().isEmpty()) {
            for (String p : orderCsv.split(",")) {
                String clean = p.trim();
                if (pinned.contains(clean) && cachedActivityMap.containsKey(clean) && !orderedPinned.contains(clean)) {
                    orderedPinned.add(clean);
                }
            }
        }
        for (String p : pinned) {
            if (cachedActivityMap.containsKey(p) && !orderedPinned.contains(p)) {
                orderedPinned.add(p);
            }
        }

        List<Map.Entry<String, CachedApp>> sortedEntries = new ArrayList<>(cachedActivityMap.entrySet());
        Collections.sort(sortedEntries, new Comparator<Map.Entry<String, CachedApp>>() {
            @Override
            public int compare(Map.Entry<String, CachedApp> e1, Map.Entry<String, CachedApp> e2) {
                return e1.getValue().label.compareToIgnoreCase(e2.getValue().label);
            }
        });

        int drawerCount = 0;
        for (Map.Entry<String, CachedApp> entry : sortedEntries) {
            final String pkg = entry.getKey();
            if (!pinned.contains(pkg)) {
                drawerCount++;
                String label = entry.getValue().label;
                if (currentLimit < 999 && orderedPinned.size() >= currentLimit) {
                    addToggleRow(label, I18n.format(I18n.KEY_LIMIT_REACHED_FMT, curLangCode, currentLimit), false, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Toast.makeText(SettingsActivity.this, I18n.format(I18n.KEY_LIMIT_REACHED_FMT, curLangCode, currentLimit), Toast.LENGTH_SHORT).show();
                        }
                    }, tokens);
                } else {
                    addToggleRow(label, I18n.get(I18n.KEY_PIN_TO_MAIN, curLangCode), true, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pinned.add(pkg);
                            orderedPinned.add(pkg);
                            saveOrderAndRefresh(orderedPinned, pinned);
                        }
                    }, tokens);
                }
            }
        }
        if (drawerCount == 0) {
            addInfoRow("Drawer Only", "All installed apps are pinned", tokens);
        }
    }

    private void saveOrderAndRefresh(List<String> order, Set<String> pinned) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(order.get(i));
        }
        preferences.edit()
            .putString(KEY_PINNED_ORDER, sb.toString())
            .putStringSet(KEY_PINNED, pinned)
            .apply();
        buildSettingsUI();
    }

    // ==========================================
    // SECTION 4: CONNECTIVITY & RADIOS
    // ==========================================
    private void buildConnectivitySection(ThemeTokens tokens) {
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        if (currentSubSection == SettingsSubSection.NONE) {
            addBackHeader(SettingsSection.CONNECTIVITY.getTitle(curLangCode), tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_CONN_FIREWALL, curLangCode), I18n.get(I18n.KEY_SUB_CONN_FIREWALL_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.CONN_FIREWALL;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_CONN_RADIOS, curLangCode), I18n.get(I18n.KEY_SUB_CONN_RADIOS_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.CONN_RADIOS;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            return;
        }

        switch (currentSubSection) {
            case CONN_FIREWALL:
                buildConnFirewallSubSection(tokens, curLangCode);
                break;
            case CONN_RADIOS:
                buildConnRadiosSubSection(tokens, curLangCode);
                break;
            default:
                break;
        }
    }

    private void buildConnFirewallSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_CONN_FIREWALL, curLangCode), tokens);

        addSelectorRow(I18n.get(I18n.KEY_APP_NETWORK_FIREWALL, curLangCode), getBlockedCountSummary(this, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppNetworkFirewall.showFirewallDialog(SettingsActivity.this, tokens, curLangCode);
            }
        }, tokens);

        final String curBgPolicy = preferences.getString("bg_policy", "whitelist");
        String bgPolicyDisplay;
        if ("strict".equals(curBgPolicy)) bgPolicyDisplay = I18n.get(I18n.KEY_BG_STRICT, curLangCode);
        else if ("allowed".equals(curBgPolicy)) bgPolicyDisplay = I18n.get(I18n.KEY_BG_ALLOWED_ALL, curLangCode);
        else bgPolicyDisplay = I18n.get(I18n.KEY_BG_WHITELIST, curLangCode);

        addSelectorRow(I18n.get(I18n.KEY_BG_POLICY, curLangCode), bgPolicyDisplay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("whitelist", I18n.get(I18n.KEY_BG_WHITELIST, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("strict", I18n.get(I18n.KEY_BG_STRICT, curLangCode)));
                options.add(new SingleChoiceSelectorDialog.Option("allowed", I18n.get(I18n.KEY_BG_ALLOWED_ALL, curLangCode)));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_BG_POLICY, curLangCode),
                    options,
                    curBgPolicy,
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            preferences.edit().putString("bg_policy", selected.id).apply();
                            MainActivity.applyBackgroundPolicy(SettingsActivity.this);
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, I18n.get(I18n.KEY_BG_POLICY, curLangCode) + ": " + selected.label, Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        if ("whitelist".equals(curBgPolicy)) {
            addButtonRow(I18n.get(I18n.KEY_BG_APPS_CONFIG, curLangCode), I18n.get(I18n.KEY_OPEN, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBackgroundAppsConfigDialog(tokens);
                }
            }, tokens);
        }
    }

    private void buildConnRadiosSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_CONN_RADIOS, curLangCode), tokens);

        final boolean wifiState = getWifiHardwareState();
        addToggleRow(I18n.get(I18n.KEY_WIFI_RADIO, curLangCode), wifiState ? I18n.get(I18n.KEY_ENABLED, curLangCode) : I18n.get(I18n.KEY_DISABLED, curLangCode), wifiState, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRadioState("wifi", !wifiState);
                buildSettingsUI();
            }
        }, tokens);

        final boolean btState = getBluetoothHardwareState();
        addToggleRow(I18n.get(I18n.KEY_BT_RADIO, curLangCode), btState ? I18n.get(I18n.KEY_ENABLED, curLangCode) : I18n.get(I18n.KEY_DISABLED, curLangCode), btState, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRadioState("bluetooth", !btState);
                buildSettingsUI();
            }
        }, tokens);

        final boolean dataState = preferences.getBoolean(KEY_DATA_STATE, true);
        addToggleRow(I18n.get(I18n.KEY_CELLULAR_DATA, curLangCode), dataState ? I18n.get(I18n.KEY_ENABLED, curLangCode) : I18n.get(I18n.KEY_DISABLED, curLangCode), dataState, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRadioState("data", !dataState);
                preferences.edit().putBoolean(KEY_DATA_STATE, !dataState).apply();
                buildSettingsUI();
            }
        }, tokens);

        final boolean nfcState = getNfcHardwareState();
        addToggleRow(I18n.get(I18n.KEY_NFC_RADIO, curLangCode), nfcState ? I18n.get(I18n.KEY_ENABLED, curLangCode) : I18n.get(I18n.KEY_DISABLED, curLangCode), nfcState, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setNfcState(!nfcState);
                buildSettingsUI();
            }
        }, tokens);

        final boolean nfcSupported = getNfcHardwareState();
        if (nfcSupported) {
            addButtonRow(I18n.get(I18n.KEY_NFC_PAYMENTS, curLangCode), I18n.get(I18n.KEY_OPEN, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NfcPaymentSandboxManager.showDiagnosticsDialog(SettingsActivity.this, tokens, curLangCode);
                }
            }, tokens);
        }

        final int currentBatch = preferences.getInt("sync_batching_mins", 30);
        String batchDisplay;
        if (currentBatch == 15) batchDisplay = I18n.get(I18n.KEY_BATCH_15M, curLangCode);
        else if (currentBatch == 60) batchDisplay = I18n.get(I18n.KEY_BATCH_60M, curLangCode);
        else batchDisplay = I18n.get(I18n.KEY_BATCH_30M, curLangCode);

        addSelectorRow(I18n.get(I18n.KEY_SYNC_BATCHING, curLangCode), currentBatch + "m", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option("15", "15m"));
                options.add(new SingleChoiceSelectorDialog.Option("30", "30m"));
                options.add(new SingleChoiceSelectorDialog.Option("60", "60m"));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_SYNC_BATCHING, curLangCode),
                    options,
                    String.valueOf(currentBatch),
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            int mins = Integer.parseInt(selected.id);
                            preferences.edit().putInt("sync_batching_mins", mins).apply();
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, "Sync: " + selected.label, Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        addSelectorRow(I18n.get(I18n.KEY_SHADE_TILES_TITLE, curLangCode), getShadeTilesSummary(curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showShadeTilesDialog(tokens, curLangCode);
            }
        }, tokens);
    }

    private String getBlockedCountSummary(Context context, String curLangCode) {
        return AppNetworkFirewall.getBlockedCountSummary(context, curLangCode);
    }

    private String formatCleanKernelVersion(String raw) {
        if (raw == null || raw.isEmpty()) return "Linux";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)").matcher(raw);
        String semver = m.find() ? m.group(1) : raw;
        String arch = System.getProperty("os.arch");
        if (arch != null && !arch.isEmpty()) {
            return "Linux " + semver + " (" + arch + ")";
        }
        return "Linux " + semver;
    }

    // ==========================================
    // SECTION 5: ABOUT DEVICE, RELEASE LINKS & BACKUP
    // ==========================================
    private void buildSystemSection(ThemeTokens tokens) {
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        if (currentSubSection == SettingsSubSection.NONE) {
            addBackHeader(SettingsSection.SYSTEM.getTitle(curLangCode), tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_SYS_INFO, curLangCode), I18n.get(I18n.KEY_SUB_SYS_INFO_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.SYS_INFO;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_SYS_UPDATES, curLangCode), I18n.get(I18n.KEY_SUB_SYS_UPDATES_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.SYS_UPDATES;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_SYS_BACKUP, curLangCode), I18n.get(I18n.KEY_SUB_SYS_BACKUP_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.SYS_BACKUP;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            addCategoryCard(I18n.get(I18n.KEY_SUB_SYS_ADVANCED, curLangCode), I18n.get(I18n.KEY_SUB_SYS_ADVANCED_DESC, curLangCode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSubSection = SettingsSubSection.SYS_ADVANCED;
                    preferences.edit().putString(PREF_LAST_SETTINGS_SUB_SECTION, currentSubSection.name()).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                }
            }, tokens);
            return;
        }

        switch (currentSubSection) {
            case SYS_INFO:
                buildSysInfoSubSection(tokens, curLangCode);
                break;
            case SYS_UPDATES:
                buildSysUpdatesSubSection(tokens, curLangCode);
                break;
            case SYS_BACKUP:
                buildSysBackupSubSection(tokens, curLangCode);
                break;
            case SYS_ADVANCED:
                buildSysAdvancedSubSection(tokens, curLangCode);
                break;
            default:
                break;
        }
    }

    private void buildSysInfoSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_SYS_INFO, curLangCode), tokens);

        addInfoRow(I18n.get(I18n.KEY_DEVICE_MODEL, curLangCode), Build.MANUFACTURER + " " + Build.MODEL, tokens);
        addInfoRow(I18n.get(I18n.KEY_ANDROID_VERSION, curLangCode), Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", tokens);
        addInfoRow(I18n.get(I18n.KEY_RUNNING_OS, curLangCode), MainActivity.OS_VERSION_STRING, tokens);
        addInfoRow(I18n.get(I18n.KEY_CHIPSET_PLATFORM, curLangCode), Build.HARDWARE + " (" + Build.BOARD + ")", tokens);
        addInfoRow(I18n.get(I18n.KEY_KERNEL_VERSION, curLangCode), formatCleanKernelVersion(System.getProperty("os.version")), tokens);
        addInfoRow(I18n.get(I18n.KEY_BUILD_ID, curLangCode), Build.DISPLAY != null ? Build.DISPLAY : Build.ID, tokens);
    }

    private void buildSysUpdatesSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_SYS_UPDATES, curLangCode), tokens);

        final UpdateChannel curChannel = UpdateChannel.fromCode(preferences.getString(KEY_OTA_CHANNEL, UpdateChannel.BETA.getCode()));

        addSelectorRow(I18n.get(I18n.KEY_UPDATE_CHANNEL, curLangCode), curChannel.getCode(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
                options.add(new SingleChoiceSelectorDialog.Option(UpdateChannel.RELEASE.getCode(), "Release"));
                options.add(new SingleChoiceSelectorDialog.Option(UpdateChannel.BETA.getCode(), "Beta"));

                SingleChoiceSelectorDialog.show(
                    SettingsActivity.this,
                    tokens,
                    I18n.get(I18n.KEY_UPDATE_CHANNEL, curLangCode),
                    options,
                    curChannel.getCode(),
                    new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                        @Override
                        public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                            UpdateChannel chosen = UpdateChannel.fromCode(selected.id);
                            preferences.edit().putString(KEY_OTA_CHANNEL, chosen.getCode()).apply();
                            buildSettingsUI();
                            Toast.makeText(SettingsActivity.this, "Channel: " + chosen.getCode(), Toast.LENGTH_SHORT).show();
                        }
                    }
                );
            }
        }, tokens);

        addButtonRow("GitHub Releases", I18n.get(I18n.KEY_CHECK_UPDATES, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OtaUpdateManager.checkForUpdates(SettingsActivity.this, tokens, curChannel, curLangCode);
            }
        }, tokens);

        addButtonRow("APK", "Install package", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                File[] apks = dir.listFiles(new java.io.FilenameFilter() {
                    @Override public boolean accept(File d, String name) { return name.endsWith(".apk"); }
                });
                if (apks == null || apks.length == 0) {
                    Toast.makeText(SettingsActivity.this, "No .apk in " + dir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    return;
                }
                File apk = apks[0];
                if (apk.length() > 300L * 1024L * 1024L) {
                    Toast.makeText(SettingsActivity.this, "APK too large (>300MB)", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                    Toast.makeText(SettingsActivity.this, "Allow install, retry", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Throwable e) {
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{"pm", "install", "-r", apk.getAbsolutePath()});
                        int rc = p.waitFor();
                        Toast.makeText(SettingsActivity.this, rc == 0 ? "Installed" : "Install rc=" + rc, Toast.LENGTH_SHORT).show();
                    } catch (Throwable e2) {
                        Toast.makeText(SettingsActivity.this, "Install error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }, tokens);
    }

    private void buildSysBackupSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_SYS_BACKUP, curLangCode), tokens);

        addButtonRow(I18n.get(I18n.KEY_CREATE_SNAPSHOT, curLangCode), I18n.get(I18n.KEY_CREATE_BACKUP, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BackupRestoreManager.showCreateBackupDialog(SettingsActivity.this, tokens, curLangCode, preferences);
            }
        }, tokens);

        addButtonRow(I18n.get(I18n.KEY_RESTORE_SNAPSHOT, curLangCode), I18n.get(I18n.KEY_RESTORE_BACKUP, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BackupRestoreManager.showRestoreBackupDialog(SettingsActivity.this, tokens, curLangCode, preferences, new Runnable() {
                    @Override
                    public void run() {
                        buildSettingsUI();
                    }
                });
            }
        }, tokens);
    }

    private void buildSysAdvancedSubSection(final ThemeTokens tokens, final String curLangCode) {
        addBackHeader(I18n.get(I18n.KEY_SUB_SYS_ADVANCED, curLangCode), tokens);

        addButtonRow("Keyboard", "Input method", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showInputMethodPicker();
                } catch (Throwable e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
                    } catch (Throwable ignored) {}
                }
            }
        }, tokens);

        addButtonRow(I18n.get(I18n.KEY_RUN_WIZARD_ROW, curLangCode), I18n.get(I18n.KEY_RUN_WIZARD, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmDialog(
                    I18n.get(I18n.KEY_CONFIRM_RUN_TITLE, curLangCode),
                    I18n.get(I18n.KEY_CONFIRM_WIZARD_MSG, curLangCode),
                    I18n.get(I18n.KEY_ACTION_RUN, curLangCode),
                    new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(SettingsActivity.this, SetupWizardActivity.class));
                        }
                    },
                    tokens
                );
            }
        }, tokens);

        addButtonRow(I18n.get(I18n.KEY_GUIDE_SETTINGS_ROW, curLangCode), I18n.get(I18n.KEY_OPEN, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                QuickStartGuideDialog.show(SettingsActivity.this, tokens, curLangCode);
            }
        }, tokens);

        addButtonRow(I18n.get(I18n.KEY_DEV_SETTINGS, curLangCode), I18n.get(I18n.KEY_OPEN, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    try {
                        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
                    } catch (Throwable ignored) {}
                    try {
                        SafeCommandExecutor.executePrivileged("settings", "put", "global", "development_settings_enabled", "1");
                    } catch (Throwable ignored) {}

                    Intent devIntent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    devIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(devIntent);
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "Could not open Dev Settings", Toast.LENGTH_SHORT).show();
                }
            }
        }, tokens);

        addButtonRow(I18n.get(I18n.KEY_HARDWARE_TEST, curLangCode), I18n.get(I18n.KEY_OPEN, curLangCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTestingSettingsMenu();
            }
        }, tokens);
    }

    private void openTestingSettingsMenu() {
        // 1. Direct launch to AOSP TestingSettingsActivity
        try {
            Intent directIntent = new Intent(Intent.ACTION_MAIN);
            directIntent.setClassName("com.android.settings", "com.android.settings.Settings$TestingSettingsActivity");
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(directIntent);
            return;
        } catch (Throwable ignored) {}

        // 2. Broadcast secret code to TestingSettingsBroadcastReceiver
        try {
            Intent secretIntent = new Intent("android.telephony.action.SECRET_CODE", Uri.parse("android_secret_code://4636"));
            secretIntent.setPackage("com.android.settings");
            sendBroadcast(secretIntent);
            return;
        } catch (Throwable ignored) {}

        // 3. Direct launch to RadioInfo
        try {
            Intent radioIntent = new Intent(Intent.ACTION_MAIN);
            radioIntent.setClassName("com.android.settings", "com.android.settings.RadioInfo");
            radioIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(radioIntent);
            return;
        } catch (Throwable ignored) {}

        // 4. Privileged am start via shell/su
        try {
            SafeCommandExecutor.executePrivileged("am", "start", "-n", "com.android.settings/.Settings$TestingSettingsActivity");
            return;
        } catch (Throwable ignored) {}

        // 5. Fallback: dialer
        try {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:*#*#4636#*#*"));
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(dialIntent);
        } catch (Throwable e) {
            Toast.makeText(this, "Could not open Testing Menu", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // HELPER UI BUILDERS (Delegated to SettingsRowFactory)
    // ==========================================
    private void addBackHeader(String title, ThemeTokens tokens) {
        String langCode = preferences.getString(KEY_LANGUAGE, "en");
        SettingsRowFactory.addBackHeader(this, contentContainer, tokens, langCode, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSubSection != SettingsSubSection.NONE) {
                    currentSubSection = SettingsSubSection.NONE;
                    preferences.edit().remove(PREF_LAST_SETTINGS_SUB_SECTION).apply();
                    if (scrollView != null) scrollView.scrollTo(0, 0);
                    buildSettingsUI();
                    return;
                }
                currentSection = SettingsSection.MENU;
                preferences.edit().remove(PREF_LAST_SETTINGS_SECTION).remove(PREF_LAST_SETTINGS_SUB_SECTION).remove(PREF_LAST_SETTINGS_SCROLL_Y).apply();
                if (scrollView != null) scrollView.scrollTo(0, 0);
                buildSettingsUI();
            }
        });
        if (title != null && !title.isEmpty()) {
            TextView subTitleView = new TextView(this);
            subTitleView.setText("=== " + title + " ===");
            subTitleView.setTextColor(tokens.accent);
            subTitleView.setTextSize(14);
            subTitleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            subTitleView.setPadding(0, dpToPx(4), 0, dpToPx(12));
            contentContainer.addView(subTitleView);
        }
    }

    private void addCategoryCard(String title, String subtitle, View.OnClickListener listener, ThemeTokens tokens) {
        SettingsRowFactory.addCategoryCard(this, contentContainer, title, subtitle, listener, tokens);
    }

    private void addSliderRow(final String label, int currentVal, final int min, final int max, final OnSliderChangedListener listener, ThemeTokens tokens) {
        SettingsRowFactory.addSliderRow(this, contentContainer, label, currentVal, min, max, listener, tokens);
    }

    private void addEqBandSliderRow(final String bandName, final int bandIndex, int currentDb, ThemeTokens tokens) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dpToPx(8), 0, dpToPx(8));

        final TextView labelView = new TextView(this);
        labelView.setText(SettingsRowFactory.formatBandDbText(bandName, currentDb));
        labelView.setTextColor(tokens.textPrimary);
        labelView.setTextSize(13);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView);

        SeekBar seekBar = new SeekBar(this);
        SettingsRowFactory.styleMinimalSeekBar(this, seekBar, tokens);
        seekBar.setMax(20);
        seekBar.setProgress(currentDb + 10);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int db = progress - 10;
                labelView.setText(SettingsRowFactory.formatBandDbText(bandName, db));
                if (fromUser) {
                    preferences.edit()
                        .putInt("pref_eq_band_" + bandIndex, db)
                        .putString("pref_eq_preset", "custom")
                        .apply();
                    applyEqualizerBand(bandIndex, db);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        row.addView(seekBar);
        contentContainer.addView(row);
    }

    private void addToggleRow(String label, String actionText, boolean isPositive, View.OnClickListener listener, ThemeTokens tokens) {
        SettingsRowFactory.addToggleRow(this, contentContainer, label, actionText, isPositive, listener, tokens);
    }

    private void addButtonRow(String label, String buttonText, View.OnClickListener listener, ThemeTokens tokens) {
        SettingsRowFactory.addButtonRow(this, contentContainer, label, buttonText, tokens.navAccent, listener, tokens);
    }

    private void addButtonRow(String label, String buttonText, int buttonTextColor, View.OnClickListener listener, ThemeTokens tokens) {
        SettingsRowFactory.addButtonRow(this, contentContainer, label, buttonText, buttonTextColor, listener, tokens);
    }

    private void addSelectorRow(String label, String valueText, View.OnClickListener listener, ThemeTokens tokens) {
        SettingsRowFactory.addSelectorRow(this, contentContainer, label, valueText, true, listener, tokens);
    }

    private void addSelectorRow(String label, String valueText, boolean enabled, View.OnClickListener listener, ThemeTokens tokens) {
        SettingsRowFactory.addSelectorRow(this, contentContainer, label, valueText, enabled, listener, tokens);
    }

    private void addInfoRow(String key, String value, ThemeTokens tokens) {
        SettingsRowFactory.addInfoRow(this, contentContainer, key, value, tokens);
    }

    private Drawable createTouchFeedbackDrawable(ThemeTokens tokens) {
        return SettingsRowFactory.createTouchFeedbackDrawable(this, tokens);
    }

    private Drawable createTouchFeedbackDrawable() {
        return createTouchFeedbackDrawable(currentTokens());
    }

    private TextView createReorderButton(String text, int textColor, View.OnClickListener listener, ThemeTokens tokens) {
        return SettingsRowFactory.createReorderButton(this, text, textColor, listener, tokens);
    }

    private TextView createReorderButton(String text, int textColor, View.OnClickListener listener) {
        return SettingsRowFactory.createReorderButton(this, text, textColor, listener, currentTokens());
    }

    private void showConfirmDialog(String title, String message, String positiveLabel, final Runnable onConfirm, ThemeTokens tokens) {
        String cancelText = I18n.get(I18n.KEY_CANCEL, preferences.getString(KEY_LANGUAGE, "en"));
        BooleanSelectorDialog.show(
            this,
            tokens,
            title,
            message,
            positiveLabel,
            cancelText,
            new BooleanSelectorDialog.OnBooleanSelectedListener() {
                @Override
                public void onSelected(boolean confirmed) {
                    if (confirmed && onConfirm != null) {
                        onConfirm.run();
                    }
                }
            }
        );
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ==========================================
    // HARDWARE ADAPTERS & BACKUP
    // ==========================================
    private void applyBrightness(int percent) {
        try {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = (float) percent / 100.0f;
            getWindow().setAttributes(layout);
        } catch (Exception ignored) {}
    }

    private void applySystemBrightness(final int percent) {
        applyBrightness(percent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            return;
        }
        try {
            int sysVal = Math.max(1, Math.min(255, Math.round((percent / 100.0f) * 255.0f)));
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, sysVal);
        } catch (Throwable ignored) {
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        int sysVal2 = Math.max(1, Math.min(255, Math.round((percent / 100.0f) * 255.0f)));
                        Runtime.getRuntime().exec(new String[]{"settings", "put", "system", "screen_brightness_mode", "0"});
                        Runtime.getRuntime().exec(new String[]{"settings", "put", "system", "screen_brightness", String.valueOf(sysVal2)});
                    } catch (Throwable ignored2) {}
                }
            }).start();
        }
    }

    private boolean getNfcHardwareState() {
        try {
            android.nfc.NfcAdapter nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter != null) return nfcAdapter.isEnabled();
        } catch (Throwable ignored) {}
        return preferences.getBoolean("nfc_enabled", false);
    }

    private void setNfcState(boolean enable) {
        String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        preferences.edit().putBoolean("nfc_enabled", enable).apply();
        boolean toggled = false;
        try {
            android.nfc.NfcAdapter adapter = android.nfc.NfcAdapter.getDefaultAdapter(this);
            if (adapter != null) {
                try {
                    java.lang.reflect.Method m = enable ? adapter.getClass().getMethod("enable") : adapter.getClass().getMethod("disable");
                    Boolean res = (Boolean) m.invoke(adapter);
                    if (res != null && res) toggled = true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        if (!toggled) {
            toggled = SafeCommandExecutor.execute("svc", "nfc", enable ? "enable" : "disable");
        }
        if (!toggled) {
            toggled = SafeCommandExecutor.execute("su", "-c", "svc nfc " + (enable ? "enable" : "disable"));
        }

        String statusStr = I18n.get(enable ? I18n.KEY_ENABLED : I18n.KEY_DISABLED, curLangCode);
        Toast.makeText(this, "NFC: " + statusStr, Toast.LENGTH_SHORT).show();
    }

    private void showBackgroundAppsConfigDialog(final ThemeTokens tokens) {
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        final Set<String> allowedSet = new HashSet<>(preferences.getStringSet("bg_allowed_apps", Collections.<String>emptySet()));

        List<CachedApp> appsList = new ArrayList<>(cachedActivityMap.values());
        Collections.sort(appsList, new Comparator<CachedApp>() {
            @Override
            public int compare(CachedApp o1, CachedApp o2) {
                return o1.label.compareToIgnoreCase(o2.label);
            }
        });

        List<MultiChoiceSelectorDialog.Item> items = new ArrayList<>();
        for (CachedApp app : appsList) {
            items.add(new MultiChoiceSelectorDialog.Item(app.pkg, app.label, app.pkg));
        }

        MultiChoiceSelectorDialog.show(
            this,
            tokens,
            I18n.get(I18n.KEY_BG_APPS_CONFIG, curLangCode),
            items,
            allowedSet,
            curLangCode,
            new MultiChoiceSelectorDialog.OnMultiChoiceSelectedListener() {
                @Override
                public void onSelected(Set<String> selectedIds) {
                    preferences.edit().putStringSet("bg_allowed_apps", selectedIds).apply();
                    MainActivity.applyBackgroundPolicy(SettingsActivity.this);
                    Toast.makeText(SettingsActivity.this, "Background apps: " + selectedIds.size(), Toast.LENGTH_SHORT).show();
                    buildSettingsUI();
                }
            }
        );
    }

    private static final String[] ALL_SHADE_TILES = {"airplane", "dnd", "wifi", "data", "bluetooth", "torch", "nfc"};

    private String getShadeTilesSummary(String lang) {
        java.util.Set<String> s = preferences.getStringSet("shade_tiles", null);
        if (s == null || s.isEmpty()) return "7/7";
        return s.size() + "/7";
    }

    private String shadeTileLabel(String id, String lang) {
        if ("airplane".equals(id)) return I18n.get(I18n.KEY_TOGGLE_AIRPLANE, lang);
        if ("dnd".equals(id)) return I18n.get(I18n.KEY_TOGGLE_DND, lang);
        if ("wifi".equals(id)) return I18n.get(I18n.KEY_TOGGLE_WIFI, lang);
        if ("data".equals(id)) return I18n.get(I18n.KEY_TOGGLE_DATA, lang);
        if ("bluetooth".equals(id)) return I18n.get(I18n.KEY_TOGGLE_BLUETOOTH, lang);
        if ("torch".equals(id)) return I18n.get(I18n.KEY_TOGGLE_TORCH, lang);
        if ("nfc".equals(id)) return I18n.get(I18n.KEY_TOGGLE_NFC, lang);
        return id;
    }

    private void showShadeTilesDialog(final ThemeTokens tokens, final String lang) {
        java.util.Set<String> cur = preferences.getStringSet("shade_tiles", null);
        final java.util.Set<String> sel = (cur == null || cur.isEmpty())
            ? new java.util.HashSet<>(java.util.Arrays.asList(ALL_SHADE_TILES))
            : new java.util.HashSet<>(cur);
        java.util.List<MultiChoiceSelectorDialog.Item> items = new java.util.ArrayList<>();
        for (String id : ALL_SHADE_TILES) items.add(new MultiChoiceSelectorDialog.Item(id, shadeTileLabel(id, lang)));
        MultiChoiceSelectorDialog.show(this, tokens, I18n.get(I18n.KEY_SHADE_TILES_TITLE, lang), items, sel, lang,
            new MultiChoiceSelectorDialog.OnMultiChoiceSelectedListener() {
                @Override public void onSelected(java.util.Set<String> selected) {
                    java.util.Set<String> save = (selected == null || selected.isEmpty())
                        ? new java.util.HashSet<>(java.util.Arrays.asList(ALL_SHADE_TILES)) : selected;
                    preferences.edit().putStringSet("shade_tiles", save).apply();
                    buildSettingsUI();
                }
            });
    }

    private void applyScreenTimeout(int secs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            return;
        }
        long ms = (secs <= 0) ? 2147483647L : ((long) secs * 1000L);
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, (int) Math.min(ms, Integer.MAX_VALUE));
        } catch (Throwable ignored) {
            try {
                Runtime.getRuntime().exec(new String[]{"settings", "put", "system", "screen_off_timeout", String.valueOf(ms)});
            } catch (Exception ignored2) {}
        }
    }

    private boolean getWifiHardwareState() {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"cmd", "wifi", "status"});
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("wifi is enabled")) return true;
                if (line.toLowerCase().contains("wifi is disabled")) return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    private boolean getBluetoothHardwareState() {
        try {
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled();
        } catch (Exception ignored) {}
        return false;
    }

    private void setRadioState(String radio, boolean enable) {
        if (!"wifi".equals(radio) && !"bluetooth".equals(radio) && !"nfc".equals(radio) && !"data".equals(radio)) {
            return;
        }
        if ("wifi".equals(radio)) {
            try {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.setWifiEnabled(enable)) {
                    Toast.makeText(this, "WIFI " + (enable ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Throwable ignored) {}
        }
        if ("bluetooth".equals(radio)) {
            try {
                android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                boolean ok = false;
                if (adapter != null) ok = enable ? adapter.enable() : adapter.disable();
                if (ok) {
                    Toast.makeText(this, "BLUETOOTH " + (enable ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                return;
            } catch (Throwable ignored) {}
        }
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"svc", radio, enable ? "enable" : "disable"});
            if (p.waitFor() != 0) {
                Toast.makeText(this, "Failed to toggle " + radio + " (needs system privilege)", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, radio.toUpperCase() + (enable ? " enabled" : " disabled"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to toggle " + radio + " (needs system privilege)", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureEqualizer() {
        if (systemEqualizer == null) {
            try {
                systemEqualizer = new Equalizer(0, 0);
                boolean enabled = preferences.getBoolean("pref_eq_enabled", true);
                systemEqualizer.setEnabled(enabled);
            } catch (Throwable ignored) {}
        }
    }

    private void setEqualizerEnabled(boolean enabled) {
        try {
            ensureEqualizer();
            if (systemEqualizer != null) {
                systemEqualizer.setEnabled(enabled);
            }
        } catch (Throwable ignored) {}
    }

    private void applyEqualizerBand(int bandIndex, int db) {
        try {
            ensureEqualizer();
            if (systemEqualizer != null) {
                short numBands = systemEqualizer.getNumberOfBands();
                if (bandIndex < numBands) {
                    short[] range = systemEqualizer.getBandLevelRange();
                    short mb = (short) (db * 100);
                    if (range != null && range.length >= 2) {
                        if (mb < range[0]) mb = range[0];
                        if (mb > range[1]) mb = range[1];
                    }
                    systemEqualizer.setBandLevel((short) bandIndex, mb);
                }
            }
        } catch (Throwable ignored) {}
    }



    private Ringtone currentPlayingPreview = null;

    private Uri getEffectiveToneUri(int type) {
        try {
            Uri currentUri = RingtoneManager.getActualDefaultRingtoneUri(this, type);
            if (currentUri != null) return currentUri;
            // Fall back to first available ringtone in system database if not set to explicit silent
            String explicitSilent = preferences.getString("pref_explicit_silent_" + type, null);
            if ("true".equals(explicitSilent)) return null;

            RingtoneManager rm = new RingtoneManager(this);
            rm.setType(type);
            Cursor cursor = rm.getCursor();
            if (cursor != null && cursor.moveToFirst()) {
                Uri fallbackUri = rm.getRingtoneUri(0);
                if (fallbackUri != null) {
                    RingtoneManager.setActualDefaultRingtoneUri(this, type, fallbackUri);
                    return fallbackUri;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String getToneLabel(int type, String langCode) {
        try {
            Uri currentUri = getEffectiveToneUri(type);
            if (currentUri == null) {
                return I18n.get(I18n.KEY_SILENT_TONE, langCode);
            }
            Ringtone r = RingtoneManager.getRingtone(this, currentUri);
            if (r != null) {
                String title = r.getTitle(this);
                if (title != null && !title.isEmpty()) {
                    return title;
                }
            }
            return I18n.get(I18n.KEY_DEFAULT_TONE, langCode);
        } catch (Throwable t) {
            return I18n.get(I18n.KEY_DEFAULT_TONE, langCode);
        }
    }

    private void showTonePickerModal(final int type, final String title, final ThemeTokens tokens, final String curLangCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Enable 'Modify system settings' to set tones", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            return;
        }

        List<SingleChoiceSelectorDialog.Option> options = new ArrayList<>();
        options.add(new SingleChoiceSelectorDialog.Option("silent", I18n.get(I18n.KEY_SILENT_TONE, curLangCode)));

        try {
            RingtoneManager rm = new RingtoneManager(this);
            rm.setType(type);
            Cursor cursor = rm.getCursor();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String toneTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                    Uri uri = rm.getRingtoneUri(cursor.getPosition());
                    if (toneTitle != null && uri != null) {
                        options.add(new SingleChoiceSelectorDialog.Option(uri.toString(), toneTitle));
                    }
                }
            }
        } catch (Throwable ignored) {}

        Uri curUri = getEffectiveToneUri(type);
        String curId = (curUri == null) ? "silent" : curUri.toString();

        SingleChoiceSelectorDialog.show(
            this,
            tokens,
            title,
            options,
            curId,
            new SingleChoiceSelectorDialog.OnOptionSelectedListener() {
                @Override
                public void onOptionSelected(SingleChoiceSelectorDialog.Option selected) {
                    try {
                        if (currentPlayingPreview != null) {
                            if (currentPlayingPreview.isPlaying()) currentPlayingPreview.stop();
                            currentPlayingPreview = null;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        if ("silent".equals(selected.id)) {
                            preferences.edit().putString("pref_explicit_silent_" + type, "true").apply();
                            RingtoneManager.setActualDefaultRingtoneUri(SettingsActivity.this, type, null);
                        } else {
                            preferences.edit().remove("pref_explicit_silent_" + type).apply();
                            Uri chosenUri = Uri.parse(selected.id);
                            RingtoneManager.setActualDefaultRingtoneUri(SettingsActivity.this, type, chosenUri);
                            if (type == RingtoneManager.TYPE_NOTIFICATION) {
                                int curN = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
                                if (curN == 0 && audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                                    int maxN = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
                                    int targetN = (int)(maxN * 0.7);
                                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetN, 0);
                                    preferences.edit().putInt("pref_user_notif_vol", targetN).apply();
                                }
                            }
                            try {
                                currentPlayingPreview = RingtoneManager.getRingtone(SettingsActivity.this, chosenUri);
                                if (currentPlayingPreview != null) {
                                    currentPlayingPreview.play();
                                    contentContainer.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                if (currentPlayingPreview != null && currentPlayingPreview.isPlaying()) {
                                                    currentPlayingPreview.stop();
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                    }, 2500);
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (SecurityException se) {
                        Toast.makeText(SettingsActivity.this, "Permission required: write system settings", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {}

                    buildSettingsUI();
                }
            }
        );
    }

    @Override
    public void onBackPressed() {
        if (currentSubSection != SettingsSubSection.NONE) {
            currentSubSection = SettingsSubSection.NONE;
            preferences.edit().remove(PREF_LAST_SETTINGS_SUB_SECTION).apply();
            if (scrollView != null) scrollView.scrollTo(0, 0);
            buildSettingsUI();
            return;
        }
        if (currentSection != SettingsSection.MENU) {
            currentSection = SettingsSection.MENU;
            preferences.edit().remove(PREF_LAST_SETTINGS_SECTION).remove(PREF_LAST_SETTINGS_SUB_SECTION).remove(PREF_LAST_SETTINGS_SCROLL_Y).apply();
            if (scrollView != null) scrollView.scrollTo(0, 0);
            buildSettingsUI();
        } else {
            preferences.edit().remove(PREF_LAST_SETTINGS_SECTION).remove(PREF_LAST_SETTINGS_SUB_SECTION).remove(PREF_LAST_SETTINGS_SCROLL_Y).apply();
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final String curLangCode = preferences.getString(KEY_LANGUAGE, "en");
        ThemeTokens tokens = currentTokens();
        if (requestCode == BackupRestoreManager.REQUEST_CODE_SAF_CREATE && resultCode == RESULT_OK && data != null) {
            BackupRestoreManager.handleSafCreateResult(this, data.getData(), tokens, curLangCode);
        } else if (requestCode == BackupRestoreManager.REQUEST_CODE_SAF_RESTORE && resultCode == RESULT_OK && data != null) {
            BackupRestoreManager.handleSafRestoreResult(this, data.getData(), tokens, curLangCode, preferences, new Runnable() {
                @Override
                public void run() {
                    buildSettingsUI();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentPlayingPreview != null) {
            try {
                if (currentPlayingPreview.isPlaying()) currentPlayingPreview.stop();
            } catch (Throwable ignored) {}
            currentPlayingPreview = null;
        }
        if (systemEqualizer != null) {
            try {
                systemEqualizer.release();
            } catch (Throwable ignored) {}
            systemEqualizer = null;
        }
    }
}
