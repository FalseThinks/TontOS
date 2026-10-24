package org.tontos.launcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Minimalist Digital Detox Lock Screen Activity for TontOS.
 * Provides custom tactile clock layouts, detox indicators, and emergency shortcuts.
 */
public class LockScreenActivity extends Activity {

    private static final String PREFS_NAME = "tontos_prefs";
    private static final int MIN_TOUCH_TARGET_DP = 48;

    private LinearLayout rootLayout;
    private TextView headerTitle;
    private TextView batteryView;
    private LinearLayout clockContainer;
    private TextView clockMain;
    private TextView clockSub;
    private TextView dateView;
    private TextView detoxView;
    private TextView ownerView;
    private LinearLayout bottomDock;
    private TextView leftShortcutBtn;
    private TextView unlockBtn;
    private TextView rightShortcutBtn;

    private Handler handler;
    private Runnable tickRunnable;
    private GestureDetector gestureDetector;
    private BroadcastReceiver batteryReceiver;

    private int batteryLevel = 100;
    private boolean isCharging = false;
    private ThemeTokens tokens;
    private LockClockStyle clockStyle;
    private String curLangCode;

    private final SimpleDateFormat timeFormat24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat timeFormatSec = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat timeFormatHour = new SimpleDateFormat("HH", Locale.getDefault());
    private final SimpleDateFormat timeFormatMin = new SimpleDateFormat("mm", Locale.getDefault());
    private final SimpleDateFormat dateFormatFull = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
    private final SimpleDateFormat dateFormatIso = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final Date now = new Date();

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
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

        // Configure window to display on top of keyguard and wake screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        tokens = LockScreenManager.getResolvedThemeTokens(this);
        clockStyle = LockScreenManager.getClockStyle(this);
        curLangCode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("system_language", "en");

        buildLockUI();
        setupGestureDetector();

        handler = new Handler(Looper.getMainLooper());
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeAndStats();
                long delay = (clockStyle == LockClockStyle.TERMINAL_RETRO)
                    ? 1000L
                    : Math.max(1000L, 60000L - (System.currentTimeMillis() % 60000L));
                handler.postDelayed(this, delay);
            }
        };
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        tokens = LockScreenManager.getResolvedThemeTokens(this);
        clockStyle = LockScreenManager.getClockStyle(this);
        curLangCode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("system_language", "en");
        buildLockUI();
        updateTimeAndStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tokens = LockScreenManager.getResolvedThemeTokens(this);
        clockStyle = LockScreenManager.getClockStyle(this);
        curLangCode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("system_language", "en");
        buildLockUI();
        updateTimeAndStats();
        setupBatteryReceiver();
        if (handler != null && tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            handler.post(tickRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Throwable ignored) {}
            batteryReceiver = null;
        }
        if (handler != null && tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Throwable ignored) {}
            batteryReceiver = null;
        }
    }

    private void unlockDevice() {
        LockScreenManager.setDeviceUnlocked();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null && (e1.getY() - e2.getY()) > dpToPx(80) && Math.abs(velocityY) > 200) {
                    unlockDevice();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return false;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null && gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Intentionally ignore back press on lock screen to prevent unauthorized bypass
    }

    private void setupBatteryReceiver() {
        if (batteryReceiver != null) return;
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale;
                }
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                              status == BatteryManager.BATTERY_STATUS_FULL);
                updateBatteryDisplay();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        try {
            Intent bat = registerReceiver(batteryReceiver, filter);
            if (bat != null) {
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale;
                }
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                              status == BatteryManager.BATTERY_STATUS_FULL);
                updateBatteryDisplay();
            }
        } catch (Throwable ignored) {}
    }

    private void buildLockUI() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(tokens.bg);
        rootLayout.setPadding(dpToPx(24), dpToPx(36), dpToPx(24), dpToPx(24));

        // Top Status Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        final float fontScale = LockScreenManager.getFontScale(this);

        headerTitle = new TextView(this);
        headerTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        headerTitle.setTextSize(13 * fontScale);
        headerTitle.setTextColor(tokens.accent);
        headerTitle.setText(getThemeLockHeader(tokens));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerRow.addView(headerTitle, titleLp);

        batteryView = new TextView(this);
        batteryView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        batteryView.setTextSize(13 * fontScale);
        batteryView.setTextColor(tokens.textMuted);
        batteryView.setVisibility(LockScreenManager.isShowBattery(this) ? View.VISIBLE : View.GONE);
        headerRow.addView(batteryView);

        rootLayout.addView(headerRow);

        // Center Content Area (Clock + Date + Detox + Message)
        LinearLayout centerArea = new LinearLayout(this);
        centerArea.setOrientation(LinearLayout.VERTICAL);
        centerArea.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        centerArea.setLayoutParams(centerLp);

        clockContainer = new LinearLayout(this);
        clockContainer.setOrientation(LinearLayout.VERTICAL);
        clockContainer.setGravity(Gravity.CENTER);

        clockMain = new TextView(this);
        clockMain.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clockMain.setGravity(Gravity.CENTER);
        clockContainer.addView(clockMain);

        clockSub = new TextView(this);
        clockSub.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clockSub.setGravity(Gravity.CENTER);
        clockSub.setVisibility(View.GONE);
        clockContainer.addView(clockSub);

        centerArea.addView(clockContainer);

        dateView = new TextView(this);
        dateView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        dateView.setTextSize(14 * fontScale);
        dateView.setTextColor(tokens.textMuted);
        dateView.setGravity(Gravity.CENTER);
        dateView.setPadding(0, dpToPx(8), 0, dpToPx(16));
        centerArea.addView(dateView);

        detoxView = new TextView(this);
        detoxView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        detoxView.setTextSize(13 * fontScale);
        detoxView.setTextColor(tokens.accent);
        detoxView.setGravity(Gravity.CENTER);
        detoxView.setPadding(0, 0, 0, dpToPx(12));
        detoxView.setVisibility(LockScreenManager.isShowDetox(this) ? View.VISIBLE : View.GONE);
        centerArea.addView(detoxView);

        ownerView = new TextView(this);
        ownerView.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
        ownerView.setTextSize(13 * fontScale);
        ownerView.setTextColor(tokens.textPrimary);
        ownerView.setGravity(Gravity.CENTER);
        String ownerInfo = LockScreenManager.getOwnerInfo(this);
        if (!TextUtils.isEmpty(ownerInfo)) {
            ownerView.setText(ownerInfo);
            ownerView.setVisibility(View.VISIBLE);
        } else {
            ownerView.setVisibility(View.GONE);
        }
        centerArea.addView(ownerView);

        rootLayout.addView(centerArea);

        // Bottom Quick Access Dock
        buildBottomDock();
        rootLayout.addView(bottomDock);

        setContentView(rootLayout);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().setSystemBarsAppearance(
                    tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } catch (Throwable ignored) {}
    }

    private String getThemeLockHeader(ThemeTokens tokens) {
        String activePresetId = preferences().getString("selected_theme", "AMOLED");
        String override = LockScreenManager.getThemeOverride(this);
        String effectiveTheme = LockScreenManager.THEME_MATCH_ACTIVE.equalsIgnoreCase(override)
            ? activePresetId : override;

        if ("AMBER".equalsIgnoreCase(effectiveTheme)) {
            return "[ TONT-OS // CRT LOCK ]";
        } else if ("E-INK".equalsIgnoreCase(effectiveTheme)) {
            return "TONT-OS — FOCUS LOCK";
        } else if ("SLATE".equalsIgnoreCase(effectiveTheme)) {
            return "• SLATE MIDNIGHT •";
        }
        return "• TontOS •";
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void buildBottomDock() {
        bottomDock = new LinearLayout(this);
        bottomDock.setOrientation(LinearLayout.HORIZONTAL);
        bottomDock.setGravity(Gravity.CENTER_VERTICAL);
        bottomDock.setPadding(0, dpToPx(12), 0, 0);

        final LockShortcut leftShortcut = LockScreenManager.getLeftShortcut(this);
        final LockShortcut rightShortcut = LockScreenManager.getRightShortcut(this);

        // Left Shortcut
        leftShortcutBtn = createShortcutButton(leftShortcut);
        leftShortcutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LockScreenManager.executeShortcut(LockScreenActivity.this, leftShortcut);
            }
        });
        bottomDock.addView(leftShortcutBtn);

        // Center Unlock Bar
        final float fontScale = LockScreenManager.getFontScale(this);
        unlockBtn = new TextView(this);
        unlockBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        unlockBtn.setTextSize(12 * fontScale);
        unlockBtn.setTextColor(tokens.navAccent);
        unlockBtn.setText(I18n.get(I18n.KEY_LOCK_UNLOCK_HINT, curLangCode));
        unlockBtn.setGravity(Gravity.CENTER);
        unlockBtn.setMinimumHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        unlockBtn.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
        unlockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unlockDevice();
            }
        });
        LinearLayout.LayoutParams unlockLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        unlockLp.setMargins(dpToPx(8), 0, dpToPx(8), 0);
        bottomDock.addView(unlockBtn, unlockLp);

        // Right Shortcut
        rightShortcutBtn = createShortcutButton(rightShortcut);
        rightShortcutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LockScreenManager.executeShortcut(LockScreenActivity.this, rightShortcut);
            }
        });
        bottomDock.addView(rightShortcutBtn);
    }

    private TextView createShortcutButton(LockShortcut shortcut) {
        final float fontScale = LockScreenManager.getFontScale(this);
        TextView btn = new TextView(this);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setTextSize(12 * fontScale);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(dpToPx(MIN_TOUCH_TARGET_DP));
        btn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

        if (shortcut == LockShortcut.NONE) {
            btn.setVisibility(View.INVISIBLE);
            btn.setText("        ");
        } else {
            btn.setVisibility(View.VISIBLE);
            btn.setTextColor(tokens.textPrimary);
            btn.setText(getShortcutLabel(shortcut));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(tokens.controlBgNormal);
            bg.setStroke(dpToPx(1), tokens.border);
            bg.setCornerRadius(dpToPx(4));
            btn.setBackground(bg);
        }
        return btn;
    }

    private String getShortcutLabel(LockShortcut shortcut) {
        switch (shortcut) {
            case TORCH:
                return I18n.get(I18n.KEY_LOCK_ACTION_TORCH, curLangCode);
            case CAMERA:
                return I18n.get(I18n.KEY_LOCK_ACTION_CAMERA, curLangCode);
            case PHONE:
                return I18n.get(I18n.KEY_LOCK_ACTION_PHONE, curLangCode);
            case MESSAGES:
                return I18n.get(I18n.KEY_LOCK_ACTION_MESSAGES, curLangCode);
            case BROWSER:
                return I18n.get(I18n.KEY_LOCK_ACTION_BROWSER, curLangCode);
            case EMAIL:
                return I18n.get(I18n.KEY_LOCK_ACTION_EMAIL, curLangCode);
            case CALCULATOR:
                return I18n.get(I18n.KEY_LOCK_ACTION_CALCULATOR, curLangCode);
            default:
                return "";
        }
    }

    private void updateBatteryDisplay() {
        if (batteryView != null) {
            String batText = batteryLevel + "%" + (isCharging ? " ⚡" : "");
            batteryView.setText(batText);
        }
    }

    private void updateTimeAndStats() {
        now.setTime(System.currentTimeMillis());
        final float fontScale = LockScreenManager.getFontScale(this);

        // Update Clock Views based on Strategy
        switch (clockStyle) {
            case VERTICAL_STACKED:
                clockMain.setVisibility(View.VISIBLE);
                clockMain.setTextSize(68 * fontScale);
                clockMain.setTextColor(tokens.accent);
                clockMain.setText(timeFormatHour.format(now));
                clockSub.setVisibility(View.VISIBLE);
                clockSub.setTextSize(68 * fontScale);
                clockSub.setTextColor(tokens.accent);
                clockSub.setText(timeFormatMin.format(now));
                break;

            case TERMINAL_RETRO:
                clockMain.setVisibility(View.VISIBLE);
                clockMain.setTextSize(20 * fontScale);
                clockMain.setTextColor(tokens.accent);
                clockSub.setVisibility(View.GONE);
                String termText = "> sys.time: " + timeFormatSec.format(now) + "\n" +
                                  "> sys.date: " + dateFormatIso.format(now) + "\n" +
                                  "> sys.mode: SECURE_LOCK";
                clockMain.setText(termText);
                break;

            case COMPACT_SINGLE:
                clockMain.setVisibility(View.VISIBLE);
                clockMain.setTextSize(26 * fontScale);
                clockMain.setTextColor(tokens.textPrimary);
                clockSub.setVisibility(View.GONE);
                clockMain.setText(timeFormat24.format(now) + " · " + dateFormatFull.format(now));
                break;

            case BIG_MONOSPACE:
                clockMain.setVisibility(View.VISIBLE);
                clockMain.setTextSize(74 * fontScale);
                clockMain.setTextColor(tokens.accent);
                clockSub.setVisibility(View.GONE);
                clockMain.setText(timeFormat24.format(now));
                break;

            case MINIMAL_DATE_ONLY:
                clockMain.setVisibility(View.VISIBLE);
                clockMain.setTextSize(22 * fontScale);
                clockMain.setTextColor(tokens.textPrimary);
                clockSub.setVisibility(View.GONE);
                clockMain.setText(dateFormatFull.format(now));
                break;

            case CLOCK_OFF:
                clockMain.setVisibility(View.GONE);
                clockSub.setVisibility(View.GONE);
                break;

            case THEME_DEFAULT:
            default:
                clockMain.setVisibility(View.VISIBLE);
                if (tokens.isLight) { // E-Ink
                    clockMain.setTextSize(72 * fontScale);
                    clockMain.setTextColor(Color.BLACK);
                } else {
                    clockMain.setTextSize(64 * fontScale);
                    clockMain.setTextColor(tokens.accent);
                }
                clockSub.setVisibility(View.GONE);
                clockMain.setText(timeFormat24.format(now));
                break;
        }

        // Date Display
        if (clockStyle == LockClockStyle.COMPACT_SINGLE ||
            clockStyle == LockClockStyle.TERMINAL_RETRO ||
            clockStyle == LockClockStyle.MINIMAL_DATE_ONLY ||
            clockStyle == LockClockStyle.CLOCK_OFF) {
            dateView.setVisibility(View.GONE);
        } else {
            dateView.setVisibility(View.VISIBLE);
            dateView.setText(dateFormatFull.format(now));
        }

        // Detox Progress Display
        if (LockScreenManager.isShowDetox(this)) {
            detoxView.setVisibility(View.VISIBLE);
            int budgetMins = preferences().getInt("daily_budget_mins", 120);
            if (budgetMins <= 0) {
                detoxView.setVisibility(View.GONE);
            } else {
                long screenTimeMins = MainActivity.getScreenTimeTodayMins();
                int percentRemaining = (int) Math.max(0, 100 - (screenTimeMins * 100 / budgetMins));
                int totalBlocks = 6;
                int filledBlocks = Math.max(0, Math.min(totalBlocks, (int) Math.round(percentRemaining * (double) totalBlocks / 100.0)));
                StringBuilder track = new StringBuilder(totalBlocks);
                for (int i = 0; i < totalBlocks; i++) {
                    track.append(i < filledBlocks ? "■" : "□");
                }
                String detoxText = String.format(Locale.getDefault(), "[ %dm/%dm · %d%% [%s] ]",
                    screenTimeMins, budgetMins, percentRemaining, track.toString());
                detoxView.setText(detoxText);
            }
        } else {
            detoxView.setVisibility(View.GONE);
        }

        updateBatteryDisplay();
    }
}
