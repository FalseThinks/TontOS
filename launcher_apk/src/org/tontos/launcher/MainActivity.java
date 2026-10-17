package org.tontos.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TontOS AMOLED Detox Launcher Home Screen.
 * Demonstrates Strategy Pattern for Themes, Flyweight Color Tokens, and Algorithmic Cache.
 */
public class MainActivity extends Activity {

    public static final String PREFS_NAME = "tontos_prefs";
    public static final String KEY_SETUP_COMPLETED = "setup_completed";
    public static final String KEY_SELECTED_THEME = "selected_theme";
    public static final String KEY_SYSTEM_LANGUAGE = "system_language";
    public static final String KEY_SYSTEM_REGION = "system_region";
    public static final String KEY_DAILY_BUDGET_MINS = "daily_budget_mins";
    public static final String KEY_PINNED_APPS = "pinned_apps";
    public static final String KEY_PINNED_APPS_ORDER = "pinned_apps_order";
    public static final String KEY_HOME_APP_LIMIT = "home_app_limit";

    public static final int DEFAULT_HOME_APP_LIMIT = 5;
    public static final int DEFAULT_BUDGET_MINUTES = 120;
    public static final String OS_VERSION_STRING = "TontOS v1.0-beta";

    public static final String KEY_SCREEN_TIME_SECS = "screen_time_today_secs";

    public static final Set<String> DEFAULT_PINNED_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "org.fossify.phone",
        "org.fossify.messages",
        "de.baumann.browser",
        "org.fossify.camera",
        "org.fossify.gallery",
        "eu.faircode.email"
    )));

    public static class AppItem implements Comparable<AppItem> {
        public final String label;
        public final String lowerLabel;
        public final String packageName;
        public final String activityName;

        public AppItem(String label, String packageName, String activityName) {
            this.label = label;
            this.lowerLabel = (label != null) ? label.toLowerCase(Locale.ROOT) : "";
            this.packageName = packageName;
            this.activityName = activityName;
        }

        @Override
        public int compareTo(AppItem o) {
            return this.label.compareToIgnoreCase(o.label);
        }
    }

    private LinearLayout rootLayout;
    private LinearLayout topSection;
    private LinearLayout bottomBar;
    private TextView timeView;
    private TextView dateView;
    private TextView detoxView;
    private TextView batteryView;
    private TextView osVersionView;
    private View dividerView;
    private TextView drawerBtn;
    private TextView settingsBtn;
    private LinearLayout appListContainer;
    private Handler handler;
    private Runnable tickRunnable;

    private static long screenTimeTodaySecs = 0;
    private static long screenOnStartTime = 0;

    private int batteryLevel = 100;
    private boolean isCharging = false;
    private boolean isDrawerView = false;
    private GestureDetector swipeDetector;

    // Algorithmic optimization: Cached installed applications to avoid repeated PackageManager IPC
    private List<AppItem> cachedInstalledApps = new ArrayList<>();
    private final Map<String, AppItem> cachedAppLookup = new HashMap<>();

    // Performance & scaling optimizations: font scale and cached formatters
    private float fontScale = 1.0f;
    private Locale cachedLocale;
    private SimpleDateFormat cachedTimeFormat;
    private SimpleDateFormat cachedDateFormat;
    private final Date reusableDate = new Date();

    public static void resetScreenTimeToday(Context ctx) {
        screenTimeTodaySecs = 0;
        screenOnStartTime = System.currentTimeMillis();
        if (ctx != null) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_SCREEN_TIME_SECS, 0L).apply();
        }
    }

    public static long getScreenTimeTodayMins() {
        long currentSession = (screenOnStartTime > 0) ? (System.currentTimeMillis() - screenOnStartTime) / 1000 : 0;
        return (screenTimeTodaySecs + currentSession) / 60;
    }

    private static String lastAppliedPolicy = null;
    private static Set<String> lastAppliedAllowed = null;
    private static long lastApplyUptimeMs = 0;

    public static void applyBackgroundPolicy(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final String policy = prefs.getString("bg_policy", "whitelist");
        final Set<String> rawAllowed = prefs.getStringSet("bg_allowed_apps", Collections.<String>emptySet());
        final Set<String> allowed = rawAllowed != null ? new HashSet<>(rawAllowed) : Collections.<String>emptySet();

        synchronized (MainActivity.class) {
            if (policy.equals(lastAppliedPolicy) && allowed.equals(lastAppliedAllowed)) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (lastAppliedPolicy != null && (now - lastApplyUptimeMs) < 500) {
                // debounce rapid toggles from onCreate+onResume
                return;
            }
            lastAppliedPolicy = policy;
            lastAppliedAllowed = new HashSet<>(allowed);
            lastApplyUptimeMs = now;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PackageManager pm = appContext.getPackageManager();
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

                    for (ResolveInfo ri : activities) {
                        String pkg = ri.activityInfo.packageName;
                        if (pkg.equals(appContext.getPackageName())) continue;
                        boolean canRunBg = false;
                        if ("allowed".equals(policy)) {
                            canRunBg = true;
                        } else if ("whitelist".equals(policy)) {
                            canRunBg = allowed.contains(pkg);
                        }
                        try {
                            Runtime.getRuntime().exec(new String[]{"cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", canRunBg ? "allow" : "ignore"});
                            Runtime.getRuntime().exec(new String[]{"am", "set-standby-bucket", pkg, canRunBg ? "active" : "restricted"});
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        }, "TontOS-BgPolicy").start();
    }

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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SETUP_COMPLETED, false)) {
            startActivity(new Intent(this, SetupWizardActivity.class));
            finish();
            return;
        }

        applyBackgroundPolicy(this);
        SystemProvisioningManager.executeInitialProvisioning(this);
        LockScreenManager.registerScreenReceiver(this);

        handler = new Handler(Looper.getMainLooper());
        screenTimeTodaySecs = prefs.getLong(KEY_SCREEN_TIME_SECS, 0L);
        screenOnStartTime = System.currentTimeMillis();

        int fontScaleInt = prefs.getInt("pref_font_scale", 100);
        fontScale = fontScaleInt / 100.0f;

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.BLACK);
        rootLayout.setPadding(50, 30, 50, 20);

        topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topHeaderRow = new LinearLayout(this);
        topHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        topHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        osVersionView = new TextView(this);
        osVersionView.setText(OS_VERSION_STRING);
        osVersionView.setTextColor(Color.parseColor("#44DD88"));
        osVersionView.setTextSize(14 * fontScale);
        osVersionView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams osLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        osVersionView.setLayoutParams(osLp);
        topHeaderRow.addView(osVersionView);

        batteryView = new TextView(this);
        batteryView.setTextColor(Color.parseColor("#888888"));
        batteryView.setTextSize(14 * fontScale);
        batteryView.setTypeface(Typeface.MONOSPACE);
        batteryView.setText("100% ⚡");
        topHeaderRow.addView(batteryView);
        topSection.addView(topHeaderRow);

        timeView = new TextView(this);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(58 * fontScale);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setPadding(0, 4, 0, 0);
        topSection.addView(timeView);

        dateView = new TextView(this);
        dateView.setTextColor(Color.parseColor("#AAAAAA"));
        dateView.setTextSize(14 * fontScale);
        dateView.setTypeface(Typeface.MONOSPACE);
        topSection.addView(dateView);

        detoxView = new TextView(this);
        detoxView.setTextColor(Color.parseColor("#44DD88"));
        detoxView.setTextSize(13 * fontScale);
        detoxView.setTypeface(Typeface.MONOSPACE);
        detoxView.setPadding(0, 6, 0, 6);
        topSection.addView(detoxView);

        float density = getResources().getDisplayMetrics().density;
        dividerView = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, (int) (6 * density + 0.5f), 0, (int) (10 * density + 0.5f));
        dividerView.setLayoutParams(divParams);
        dividerView.setBackgroundColor(Color.parseColor("#333333"));
        topSection.addView(dividerView);

        rootLayout.addView(topSection);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scroll.setLayoutParams(scrollLp);

        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(appListContainer);
        rootLayout.addView(scroll);

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, 14, 0, 10);

        drawerBtn = new TextView(this);
        drawerBtn.setText("[ ALL APPS / DRAWER ]");
        drawerBtn.setTextColor(Color.parseColor("#44DD88"));
        drawerBtn.setTextSize(15 * fontScale);
        drawerBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams drawerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        drawerBtn.setLayoutParams(drawerLp);
        drawerBtn.setClickable(true);
        drawerBtn.setFocusable(true);
        drawerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isDrawerView = true;
                renderApps();
            }
        });
        bottomBar.addView(drawerBtn);

        settingsBtn = new TextView(this);
        settingsBtn.setText("[ SETTINGS ]");
        settingsBtn.setTextColor(Color.parseColor("#CCCCCC"));
        settingsBtn.setTextSize(15 * fontScale);
        settingsBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        settingsBtn.setPadding(10, 0, 0, 0);
        settingsBtn.setClickable(true);
        settingsBtn.setFocusable(true);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        bottomBar.addView(settingsBtn);
        rootLayout.addView(bottomBar);

        setContentView(rootLayout);

        tickRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeDisplay();
                long delayMs = 60000 - (System.currentTimeMillis() % 60000);
                if (delayMs < 1000) delayMs = 60000;
                handler.postDelayed(this, delayMs);
            }
        };

        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null) {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffY) > Math.abs(diffX)) {
                        if (diffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            if (!isDrawerView) {
                                openUltraShade();
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });

        View.OnClickListener shadeOpener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isDrawerView) {
                    openUltraShade();
                }
            }
        };
        topSection.setOnClickListener(shadeOpener);
        osVersionView.setOnClickListener(shadeOpener);
        batteryView.setOnClickListener(shadeOpener);
        timeView.setOnClickListener(shadeOpener);

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        disableStatusBarExpansion();
        hideSystemUI();
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                batteryLevel = (level * 100) / scale;
            }
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL);
            updateBatteryDisplay();
            BatteryProtectionManager.applyHardwareProtection(context, batteryLevel, isCharging);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
        }
        if (screenOnStartTime > 0) {
            screenTimeTodaySecs += (System.currentTimeMillis() - screenOnStartTime) / 1000;
            screenOnStartTime = 0;
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_SCREEN_TIME_SECS, screenTimeTodaySecs).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SETUP_COMPLETED, false)) {
            startActivity(new Intent(this, SetupWizardActivity.class));
            finish();
            return;
        }

        disableStatusBarExpansion();
        hideSystemUI();

        isDrawerView = false;

        String langCode = prefs.getString(KEY_SYSTEM_LANGUAGE, "en");
        String regionCode = prefs.getString(KEY_SYSTEM_REGION, "US");
        int fontScaleInt = prefs.getInt("pref_font_scale", 100);
        fontScale = fontScaleInt / 100.0f;
        I18n.applyLocaleAndScale(this, langCode, regionCode, fontScale);
        applyFontScale();
        applyBackgroundPolicy(this);

        drawerBtn.setText(I18n.get(I18n.KEY_ALL_APPS, langCode));
        settingsBtn.setText(I18n.get(I18n.KEY_SETTINGS, langCode));

        screenOnStartTime = System.currentTimeMillis();
        updateTimeDisplay();
        if (handler != null && tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            long delayMs = 60000 - (System.currentTimeMillis() % 60000);
            if (delayMs < 1000) delayMs = 60000;
            handler.postDelayed(tickRunnable, delayMs);
        }
        updateBatteryDisplay();
        refreshInstalledAppsCache();
        applyThemeTokens();
        renderApps();

        checkAndTerminateExitedApp();
        if (!prefs.getBoolean(QuickStartGuideDialog.PREF_GUIDE_SHOWN, false)) {
            QuickStartGuideDialog.show(this, currentTokens(), langCode);
        }
    }

    private void applyFontScale() {
        if (timeView != null) timeView.setTextSize(58 * fontScale);
        if (dateView != null) dateView.setTextSize(14 * fontScale);
        if (detoxView != null) detoxView.setTextSize(13 * fontScale);
        if (osVersionView != null) osVersionView.setTextSize(14 * fontScale);
        if (batteryView != null) batteryView.setTextSize(14 * fontScale);
        if (drawerBtn != null) drawerBtn.setTextSize(15 * fontScale);
        if (settingsBtn != null) settingsBtn.setTextSize(15 * fontScale);
    }

    private ThemePreset currentTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return ThemePreset.fromId(prefs.getString(KEY_SELECTED_THEME, ThemePreset.AMOLED.getId()));
    }

    private ThemeTokens currentTokens() {
        ThemeTokens tokens = ThemeTokens.get(currentTheme());
        if (GrayscaleManager.isGrayscaleEnabled(this)) {
            return tokens.toMonochrome();
        }
        return tokens;
    }

    private void applyThemeTokens() {
        GrayscaleManager.applyGrayscaleToWindow(this);
        ThemeTokens tokens = currentTokens();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().setSystemBarsAppearance(
                    tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } catch (Throwable ignored) {}

        if (!isDrawerView) {
            rootLayout.setBackgroundColor(tokens.bg);
            topSection.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
        } else {
            rootLayout.setBackgroundColor(tokens.surface);
        }

        osVersionView.setTextColor(tokens.accent);
        batteryView.setTextColor(tokens.textMuted);
        timeView.setTextColor(tokens.textPrimary);
        dateView.setTextColor(tokens.textMuted);
        detoxView.setTextColor(tokens.accent);
        dividerView.setBackgroundColor(tokens.border);
        drawerBtn.setTextColor(tokens.navAccent);
        settingsBtn.setTextColor(tokens.navAccent);
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
        // TontOS compositor owns chrome. Use app top bar; don't hide
        // Pixel system bars via WindowInsets/controller.
        // No-op keeps background/defaults visible and avoids AOSP dependency.
    }

    private void disableStatusBarExpansion() {
        // TontOS owns its shade (UltraShadeDialog + in-app top bar).
        // No Pixel/AOSP 'cmd statusbar' dependency.
    }

    private void updateDateFormats(String langCode, String regionCode) {
        Locale targetLocale = (regionCode != null && !regionCode.isEmpty())
            ? new Locale(langCode, regionCode)
            : new Locale(langCode);
        if (cachedLocale == null || !cachedLocale.equals(targetLocale)) {
            cachedLocale = targetLocale;
            cachedTimeFormat = new SimpleDateFormat("HH:mm", cachedLocale);
            cachedDateFormat = new SimpleDateFormat("EEEE, MMMM d", cachedLocale);
        }
    }

    private void updateTimeDisplay() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String langCode = prefs.getString(KEY_SYSTEM_LANGUAGE, "en");
        String regionCode = prefs.getString(KEY_SYSTEM_REGION, "US");
        updateDateFormats(langCode, regionCode);

        reusableDate.setTime(System.currentTimeMillis());
        timeView.setText(cachedTimeFormat.format(reusableDate));
        dateView.setText(cachedDateFormat.format(reusableDate));

        long currentSessionSecs = (System.currentTimeMillis() - screenOnStartTime) / 1000;
        long totalSecs = screenTimeTodaySecs + currentSessionSecs;
        long mins = totalSecs / 60;
        long hrs = mins / 60;
        long remMins = mins % 60;

        int budgetMins = prefs.getInt(KEY_DAILY_BUDGET_MINS, DEFAULT_BUDGET_MINUTES);
        long budgetHrs = budgetMins / 60;
        long budgetRemMins = budgetMins % 60;
        String budgetStr;
        if (budgetHrs > 0 && budgetRemMins == 0) {
            budgetStr = String.format(cachedLocale, "%dh", budgetHrs);
        } else if (budgetHrs > 0) {
            budgetStr = String.format(cachedLocale, "%dh %02dm", budgetHrs, budgetRemMins);
        } else {
            budgetStr = String.format(cachedLocale, "%dm", budgetMins);
        }

        String usageStr = (hrs > 0) 
            ? String.format(cachedLocale, "%dh %02dm", hrs, remMins)
            : String.format(cachedLocale, "%dm", mins);

        ThemeTokens tokens = ThemeTokens.get(currentTheme());
        if (budgetMins <= 0) {
            detoxView.setVisibility(View.GONE);
        } else {
            detoxView.setVisibility(View.VISIBLE);
            detoxView.setSingleLine(true);
            detoxView.setEllipsize(TextUtils.TruncateAt.END);

            int percentRemaining = Math.max(0, Math.min(100, (int) Math.round((budgetMins - mins) * 100.0 / budgetMins)));
            int totalBlocks = 6;
            int filledBlocks = Math.max(0, Math.min(totalBlocks, (int) Math.round(percentRemaining * (double) totalBlocks / 100.0)));
            StringBuilder track = new StringBuilder(totalBlocks);
            for (int i = 0; i < totalBlocks; i++) {
                track.append(i < filledBlocks ? "■" : "□");
            }

            if (totalSecs > (long) budgetMins * 60) {
                detoxView.setTextColor(Color.parseColor("#FF5555"));
                String exceededLabel = I18n.get(I18n.KEY_EXCEEDED, langCode).toUpperCase(cachedLocale);
                String text = String.format(cachedLocale, "[ %s/%s · %s ]", usageStr, budgetStr, exceededLabel);
                detoxView.setText(text);
            } else {
                detoxView.setTextColor(tokens.accent);
                String text = String.format(cachedLocale, "[ %s/%s · %d%% [%s] ]", usageStr, budgetStr, percentRemaining, track.toString());
                detoxView.setText(text);
            }
        }
    }

    private void updateBatteryDisplay() {
        int mode = BatteryProtectionManager.getMode(this);
        String modeTag = "";
        if (mode == BatteryProtectionManager.MODE_80) {
            modeTag = " [80%]";
        } else if (mode == BatteryProtectionManager.MODE_BYPASS) {
            modeTag = " [BYPASS]";
        }
        batteryView.setText(batteryLevel + "%" + (isCharging ? " ⚡" : "") + modeTag);
    }

    /**
     * O(N) Cache refresh: queries PackageManager and indexes into hash map for O(1) lookups.
     */
    private void refreshInstalledAppsCache() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);
        cachedInstalledApps.clear();
        cachedAppLookup.clear();

        for (ResolveInfo ri : activities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals("com.android.settings") || pkg.equals("com.android.stk")) {
                continue;
            }
            String label = ri.loadLabel(pm).toString();
            String actName = ri.activityInfo.name;
            AppItem item = new AppItem(label, pkg, actName);
            cachedInstalledApps.add(item);
            cachedAppLookup.put(pkg, item);
        }
        Collections.sort(cachedInstalledApps);
    }

    private void renderApps() {
        appListContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ThemeTokens tokens = currentTokens();

        if (isDrawerView) {
            renderDrawerApps(tokens);
        } else {
            renderHomePinnedApps(prefs, tokens);
        }
    }

    private void renderDrawerApps(final ThemeTokens tokens) {
        rootLayout.setBackgroundColor(tokens.surface);
        topSection.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String langCode = prefs.getString(KEY_SYSTEM_LANGUAGE, "en");

        LinearLayout drawerTop = new LinearLayout(this);
        drawerTop.setOrientation(LinearLayout.HORIZONTAL);
        drawerTop.setGravity(Gravity.CENTER_VERTICAL);
        drawerTop.setPadding(0, 10, 0, 16);

        TextView backBtn = new TextView(this);
        backBtn.setText(I18n.get(I18n.KEY_BACK, langCode));
        backBtn.setTextColor(tokens.navAccent);
        backBtn.setTextSize(15 * fontScale);
        backBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        backBtn.setBackground(createTouchFeedbackDrawable(tokens));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        backBtn.setLayoutParams(bLp);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isDrawerView = false;
                renderApps();
            }
        });
        drawerTop.addView(backBtn);

        final TextView countBadge = new TextView(this);
        countBadge.setText(I18n.format(I18n.KEY_APPS_COUNT_FMT, langCode, cachedInstalledApps.size()));
        countBadge.setTextColor(tokens.textMuted);
        countBadge.setTextSize(13 * fontScale);
        countBadge.setTypeface(Typeface.MONOSPACE);
        drawerTop.addView(countBadge);

        appListContainer.addView(drawerTop);

        TextView drawerTitle = new TextView(this);
        drawerTitle.setText(I18n.get(I18n.KEY_ALL_APPS_DRAWER, langCode));
        drawerTitle.setTextColor(tokens.textPrimary);
        drawerTitle.setTextSize(16 * fontScale);
        drawerTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        drawerTitle.setPadding(0, 0, 0, 12);
        appListContainer.addView(drawerTitle);

        // Monospace EditText search bar styled to theme (transparent, no chunky box)
        EditText searchBar = new EditText(this);
        searchBar.setHint(I18n.get(I18n.KEY_SEARCH_APPS, langCode));
        searchBar.setHintTextColor(tokens.textMuted);
        searchBar.setTextColor(tokens.textPrimary);
        searchBar.setTextSize(14 * fontScale);
        searchBar.setTypeface(Typeface.MONOSPACE);
        searchBar.setSingleLine(true);
        searchBar.setMinHeight(dpToPx(48));
        searchBar.setMinimumHeight(dpToPx(48));
        searchBar.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));

        final GradientDrawable searchBg = new GradientDrawable();
        searchBg.setShape(GradientDrawable.RECTANGLE);
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke(dpToPx(1), tokens.border);
        searchBg.setCornerRadius(dpToPx(4));
        searchBar.setBackground(searchBg);

        searchBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                searchBg.setStroke(dpToPx(1), hasFocus ? tokens.accent : tokens.border);
            }
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.setMargins(0, 0, 0, dpToPx(12));
        searchBar.setLayoutParams(searchLp);
        appListContainer.addView(searchBar);

        final LinearLayout drawerAppsList = new LinearLayout(this);
        drawerAppsList.setOrientation(LinearLayout.VERTICAL);
        appListContainer.addView(drawerAppsList);

        // Initial populate of cached apps
        filterDrawerApps("", drawerAppsList, tokens, countBadge, langCode);

        final String finalLangCode = langCode;
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDrawerApps(s != null ? s.toString() : "", drawerAppsList, tokens, countBadge, finalLangCode);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterDrawerApps(String query, LinearLayout container, ThemeTokens tokens, TextView countBadge, String langCode) {
        container.removeAllViews();
        String cleanQuery = (query != null) ? query.trim().toLowerCase(Locale.ROOT) : "";
        int matchCount = 0;
        char lastSectionChar = 0;

        for (final AppItem app : cachedInstalledApps) {
            if (cleanQuery.isEmpty() || app.lowerLabel.contains(cleanQuery)) {
                matchCount++;
                if (cleanQuery.isEmpty()) {
                    char sectionChar = app.label.isEmpty() ? '#' : Character.toUpperCase(app.label.charAt(0));
                    if (sectionChar != lastSectionChar) {
                        lastSectionChar = sectionChar;
                        TextView secHeader = new TextView(this);
                        secHeader.setText("--- [ " + sectionChar + " ] ---");
                        secHeader.setTextColor(tokens.accent);
                        secHeader.setTextSize(13 * fontScale);
                        secHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                        secHeader.setPadding(0, dpToPx(12), 0, dpToPx(4));
                        container.addView(secHeader);
                    }
                }
                addAppRow(container, app.label, tokens.textPrimary, tokens, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launchApp(app);
                    }
                }, new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        AppPrivacyGuard.showAppPrivacySheet(MainActivity.this, tokens, app.packageName, app.label, langCode, new Runnable() {
                            @Override
                            public void run() {
                                renderApps();
                            }
                        });
                        return true;
                    }
                });
            }
        }

        if (countBadge != null) {
            countBadge.setText(I18n.format(I18n.KEY_APPS_COUNT_FMT, langCode, matchCount));
        }

        if (matchCount == 0) {
            TextView emptyView = new TextView(this);
            emptyView.setText("  " + I18n.format(I18n.KEY_NO_APPS_MATCH_FMT, langCode, query));
            emptyView.setTextColor(tokens.textMuted);
            emptyView.setTextSize(14 * fontScale);
            emptyView.setTypeface(Typeface.MONOSPACE);
            emptyView.setPadding(0, dpToPx(24), 0, dpToPx(24));
            container.addView(emptyView);
        }
    }

    private void renderHomePinnedApps(SharedPreferences prefs, ThemeTokens tokens) {
        rootLayout.setBackgroundColor(tokens.bg);
        topSection.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);

        int homeLimit = prefs.getInt(KEY_HOME_APP_LIMIT, DEFAULT_HOME_APP_LIMIT);
        Set<String> pinned = prefs.getStringSet(KEY_PINNED_APPS, DEFAULT_PINNED_PACKAGES);

        // Algorithmic optimization: Parse ordered list in O(M) time and resolve with O(1) hash map lookups
        String orderCsv = prefs.getString(KEY_PINNED_APPS_ORDER, null);
        List<String> orderedPkgs = new ArrayList<>();
        if (orderCsv != null && !orderCsv.trim().isEmpty()) {
            for (String p : orderCsv.split(",")) {
                String clean = p.trim();
                if (pinned.contains(clean) && !orderedPkgs.contains(clean)) {
                    orderedPkgs.add(clean);
                }
            }
        }
        for (String p : pinned) {
            if (!orderedPkgs.contains(p)) {
                orderedPkgs.add(p);
            }
        }

        int shown = 0;
        for (String pkg : orderedPkgs) {
            final AppItem app = cachedAppLookup.get(pkg);
            if (app != null && shown < homeLimit) {
                addAppRow(appListContainer, app.label, tokens.textPrimary, tokens, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launchApp(app);
                    }
                }, new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        String langCode = prefs.getString(KEY_SYSTEM_LANGUAGE, "en");
                        AppPrivacyGuard.showAppPrivacySheet(MainActivity.this, tokens, app.packageName, app.label, langCode, new Runnable() {
                            @Override
                            public void run() {
                                renderApps();
                            }
                        });
                        return true;
                    }
                });
                shown++;
            }
        }
    }

    private void addAppRow(String label, int textColor, View.OnClickListener listener) {
        addAppRow(label, textColor, ThemeTokens.get(currentTheme()), listener);
    }

    private void addAppRow(String label, int textColor, ThemeTokens tokens, View.OnClickListener listener) {
        addAppRow(appListContainer, label, textColor, tokens, listener);
    }

    private void addAppRow(LinearLayout parent, String label, int textColor, View.OnClickListener listener) {
        addAppRow(parent, label, textColor, ThemeTokens.get(currentTheme()), listener);
    }

    private void addAppRow(LinearLayout parent, String label, int textColor, ThemeTokens tokens, View.OnClickListener listener) {
        addAppRow(parent, label, textColor, tokens, listener, null);
    }

    private void addAppRow(LinearLayout parent, String label, int textColor, ThemeTokens tokens, View.OnClickListener listener, View.OnLongClickListener longClickListener) {
        TextView btn = new TextView(this);
        SpannableString span = new SpannableString(">  " + label);
        span.setSpan(new ForegroundColorSpan(tokens.accent), 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(textColor), 2, span.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        btn.setText(span);
        btn.setTextColor(textColor);
        btn.setTextSize(20 * fontScale);
        btn.setTypeface(Typeface.MONOSPACE);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setMinHeight(dpToPx(48));
        btn.setMinimumHeight(dpToPx(48));
        btn.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setBackground(createTouchFeedbackDrawable(tokens));
        btn.setOnClickListener(listener);
        if (longClickListener != null) {
            btn.setOnLongClickListener(longClickListener);
        }
        parent.addView(btn);
    }

    private Drawable createTouchFeedbackDrawable() {
        return createTouchFeedbackDrawable(ThemeTokens.get(currentTheme()));
    }

    private Drawable createTouchFeedbackDrawable(ThemeTokens tokens) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(tokens.controlBgPressed);
        pressed.setCornerRadius(dpToPx(4));

        ColorDrawable normal = new ColorDrawable(Color.TRANSPARENT);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void launchApp(final AppItem app) {
        if (app == null) return;
        ThemeTokens tokens = ThemeTokens.get(currentTheme());
        launchApp(app.packageName, app.label, tokens);
    }

    public void launchApp(final String pkg, final String label, final ThemeTokens tokens) {
        isDrawerView = false;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int frictionSecs = prefs.getInt("pref_friction_seconds", 0);

        if (frictionSecs > 0 && !isFrictionWhitelisted(pkg)) {
            FrictionDialog.show(this, tokens, label, frictionSecs, new Runnable() {
                @Override
                public void run() {
                    performLaunch(pkg);
                }
            });
        } else {
            performLaunch(pkg);
        }
    }

    private boolean isFrictionWhitelisted(final String pkg) {
        if (pkg == null) return true;
        if (pkg.equals("org.fossify.phone") || pkg.equals("org.fossify.messages") || pkg.equals("org.fossify.contacts")) {
            return true;
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("dialer") || p.contains("telecom") || p.contains("mms") || (p.contains("message") && !p.contains("whatsapp")) || p.contains("contact")) {
            return true;
        }
        return false;
    }

    private String mLastLaunchedPackage = null;

    private void performLaunch(final String pkg) {
        if (pkg == null) return;
        mLastLaunchedPackage = pkg;
        AppItem app = cachedAppLookup.get(pkg);
        if (app != null && app.activityName != null) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new ComponentName(app.packageName, app.activityName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
        try {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(pkg);
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } else {
                Toast.makeText(MainActivity.this, "Could not open " + (app != null ? app.label : pkg), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) {
            Toast.makeText(MainActivity.this, "Could not open " + (app != null ? app.label : pkg), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAndTerminateExitedApp() {
        if (mLastLaunchedPackage == null) return;
        final String exitedPkg = mLastLaunchedPackage;
        mLastLaunchedPackage = null;

        if (exitedPkg.equals(getPackageName())) return;
        if (isEssentialSystemPackage(exitedPkg)) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String policy = prefs.getString("bg_policy", "whitelist");
        if ("allowed".equals(policy)) {
            return;
        }

        boolean canRunBg = false;
        if ("whitelist".equals(policy)) {
            Set<String> allowed = prefs.getStringSet("bg_allowed_apps", Collections.<String>emptySet());
            canRunBg = allowed != null && allowed.contains(exitedPkg);
            if (AppPrivacyGuard.isBgRestricted(this, exitedPkg)) {
                canRunBg = false;
            }
        }

        if (!canRunBg) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    SafeCommandExecutor.executePrivileged("am", "force-stop", exitedPkg);
                }
            }, "TontOS-KillExitedApp").start();
        }
    }

    private boolean isEssentialSystemPackage(String pkg) {
        if (pkg == null) return true;
        if (pkg.equals(getPackageName())
                || pkg.equals("org.fossify.phone")
                || pkg.equals("org.fossify.messages")
                || pkg.equals("com.android.phone")
                || pkg.equals("com.android.server.telecom")
                || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.inputmethod.latin")
                || pkg.equals("org.fossify.contacts")) {
            return true;
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("telecom") || p.contains("dialer") || p.contains("inputmethod");
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (isDrawerView) {
            isDrawerView = false;
            renderApps();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isDrawerView && swipeDetector != null) {
            swipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void openUltraShade() {
        UltraShadeDialog.show(this, currentTokens(), batteryLevel);
    }

    @Override
    public void onBackPressed() {
        if (isDrawerView) {
            isDrawerView = false;
            renderApps();
            return;
        }
        super.onBackPressed();
    }
}
