package org.tontos.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.util.Log;

/**
 * Lock Screen Configuration & State Manager for TontOS.
 * Implements Facade and Strategy patterns for customizable lock screen experience.
 */
public final class LockScreenManager {

    private static final String TAG = "LockScreenManager";
    public static final String PREFS_NAME = "tontos_prefs";

    public static final String KEY_LOCKSCREEN_ENABLED = "pref_lockscreen_enabled";
    public static final String KEY_LOCK_CLOCK_STYLE = "pref_lock_clock_style";
    public static final String KEY_LOCK_THEME_OVERRIDE = "pref_lock_theme_override";
    public static final String KEY_LOCK_SHOW_DETOX = "pref_lock_show_detox";
    public static final String KEY_LOCK_SHOW_BATTERY = "pref_lock_show_battery";
    public static final String KEY_LOCK_OWNER_INFO = "pref_lock_owner_info";
    public static final String KEY_LOCK_SHORTCUT_LEFT = "pref_lock_shortcut_left";
    public static final String KEY_LOCK_SHORTCUT_RIGHT = "pref_lock_shortcut_right";
    public static final String KEY_LOCK_NOTIF_MODE = "pref_lock_notif_mode";

    public static final String THEME_MATCH_ACTIVE = "MATCH_ACTIVE";
    public static final String DEFAULT_OWNER_INFO = "TontOS • Stay intentional";

    private static boolean receiverRegistered = false;
    private static BroadcastReceiver screenReceiver = null;

    private LockScreenManager() {}

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return MainActivity.getSafeSharedPreferences(context, PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isLockScreenEnabled(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_LOCKSCREEN_ENABLED, true);
    }

    public static void setLockScreenEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putBoolean(KEY_LOCKSCREEN_ENABLED, enabled).apply();
    }

    public static LockClockStyle getClockStyle(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return LockClockStyle.THEME_DEFAULT;
        String id = prefs.getString(KEY_LOCK_CLOCK_STYLE, LockClockStyle.THEME_DEFAULT.getId());
        return LockClockStyle.fromId(id);
    }

    public static void setClockStyle(Context context, LockClockStyle style) {
        if (style == null) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putString(KEY_LOCK_CLOCK_STYLE, style.getId()).apply();
    }

    public static String getThemeOverride(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null ? prefs.getString(KEY_LOCK_THEME_OVERRIDE, THEME_MATCH_ACTIVE) : THEME_MATCH_ACTIVE;
    }

    public static void setThemeOverride(Context context, String override) {
        if (override == null) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putString(KEY_LOCK_THEME_OVERRIDE, override).apply();
    }

    public static boolean isShowDetox(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_LOCK_SHOW_DETOX, true);
    }

    public static void setShowDetox(Context context, boolean show) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putBoolean(KEY_LOCK_SHOW_DETOX, show).apply();
    }

    public static boolean isShowBattery(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_LOCK_SHOW_BATTERY, true);
    }

    public static void setShowBattery(Context context, boolean show) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putBoolean(KEY_LOCK_SHOW_BATTERY, show).apply();
    }

    public static String getOwnerInfo(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null ? prefs.getString(KEY_LOCK_OWNER_INFO, DEFAULT_OWNER_INFO) : DEFAULT_OWNER_INFO;
    }

    public static void setOwnerInfo(Context context, String info) {
        String sanitized = info != null ? info.trim() : "";
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putString(KEY_LOCK_OWNER_INFO, sanitized).apply();
        GlobalSettingsBridge.applyGlobalLockScreenOwnerInfo(context, sanitized);
    }

    public static LockNotificationMode getNotificationMode(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return LockNotificationMode.DONT_SHOW;
        String id = prefs.getString(KEY_LOCK_NOTIF_MODE, LockNotificationMode.DONT_SHOW.getId());
        return LockNotificationMode.fromId(id);
    }

    public static void setNotificationMode(Context context, LockNotificationMode mode) {
        if (mode == null) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putString(KEY_LOCK_NOTIF_MODE, mode.getId()).apply();
        GlobalSettingsBridge.applyGlobalLockScreenNotifications(context, mode);
    }

    public static float getFontScale(Context context) {
        return GlobalSettingsBridge.getFontScale(context);
    }

    public static LockShortcut getLeftShortcut(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return LockShortcut.TORCH;
        String id = prefs.getString(KEY_LOCK_SHORTCUT_LEFT, LockShortcut.TORCH.getId());
        return LockShortcut.fromId(id);
    }

    public static void setLeftShortcut(Context context, LockShortcut shortcut) {
        if (shortcut == null) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putString(KEY_LOCK_SHORTCUT_LEFT, shortcut.getId()).apply();
    }

    public static LockShortcut getRightShortcut(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return LockShortcut.CAMERA;
        String id = prefs.getString(KEY_LOCK_SHORTCUT_RIGHT, LockShortcut.CAMERA.getId());
        return LockShortcut.fromId(id);
    }

    public static void setRightShortcut(Context context, LockShortcut shortcut) {
        if (shortcut == null) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putString(KEY_LOCK_SHORTCUT_RIGHT, shortcut.getId()).apply();
    }

    /**
     * Resolves ThemeTokens for lock screen either from theme override or active theme preset.
     */
    public static ThemeTokens getResolvedThemeTokens(Context context) {
        if (context == null) return ThemeTokens.get(ThemePreset.AMOLED);
        String override = getThemeOverride(context);
        if (!THEME_MATCH_ACTIVE.equalsIgnoreCase(override)) {
            return ThemeTokens.get(override);
        }
        SharedPreferences prefs = getPrefs(context);
        String activeTheme = prefs != null ? prefs.getString("selected_theme", "AMOLED") : "AMOLED";
        return ThemeTokens.get(activeTheme);
    }

    /**
     * Executes designated lock screen shortcut safely.
     * Prevents data exfiltration by enforcing unlock authentication for sensitive user applications.
     */
    public static void executeShortcut(Activity activity, LockShortcut shortcut) {
        if (activity == null || shortcut == null || shortcut == LockShortcut.NONE) return;

        switch (shortcut) {
            case TORCH:
                UltraBatterySaverManager.toggleTorch(activity);
                break;
            case CAMERA:
                launchApp(activity, "org.fossify.camera", new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                break;
            default:
                // Sensitive user apps (Phone, Browser, Messages, Email, Calculator) require authentication if locked
                if (isDeviceLocked(activity) && isLockScreenEnabled(activity) && LockSecurityManager.getSecurityType(activity) != LockSecurityManager.SecurityType.NONE) {
                    if (activity instanceof LockScreenActivity) {
                        ((LockScreenActivity) activity).handleUnlockAction();
                    } else {
                        launchLockScreen(activity);
                    }
                    return;
                }
                switch (shortcut) {
                    case PHONE:
                        launchApp(activity, "org.fossify.phone", new Intent(Intent.ACTION_DIAL));
                        break;
                    case BROWSER:
                        launchApp(activity, "de.baumann.browser", null);
                        break;
                    case MESSAGES:
                        launchApp(activity, "org.fossify.messages", new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING));
                        break;
                    case EMAIL:
                        launchApp(activity, "eu.faircode.email", new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL));
                        break;
                    case CALCULATOR:
                        launchApp(activity, "com.android.calculator2", null);
                        break;
                    default:
                        break;
                }
                break;
        }
    }

    private static void launchApp(Activity activity, String pkg, Intent fallback) {
        try {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) intent = fallback;
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed launching " + pkg + ": " + t.getMessage());
        }
    }

    private static volatile Boolean isDeviceLocked = null;

    public static boolean isDeviceLocked() {
        return isDeviceLocked != null && isDeviceLocked;
    }

    public static boolean isDeviceLocked(Context context) {
        if (isDeviceLocked == null) {
            if (context != null && LockSecurityManager.getSecurityType(context) != LockSecurityManager.SecurityType.NONE) {
                isDeviceLocked = true;
                return true;
            }
            return false;
        }
        return isDeviceLocked;
    }

    public static void setDeviceLocked(boolean locked) {
        isDeviceLocked = locked;
    }

    public static void setDeviceUnlocked() {
        isDeviceLocked = false;
    }

    /**
     * Registers screen state broadcast receiver to display TontOS lockscreen when display turns off.
     */
    public static synchronized void registerScreenReceiver(final Context context) {
        if (context == null || receiverRegistered) return;
        final Context appContext = context.getApplicationContext();

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    isDeviceLocked = true;
                    if (isLockScreenEnabled(appContext)) {
                        launchLockScreen(appContext);
                    }
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    if (isDeviceLocked(appContext) && isLockScreenEnabled(appContext)) {
                        launchLockScreen(appContext);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.setPriority(1000);
        appContext.registerReceiver(screenReceiver, filter);
        receiverRegistered = true;
    }

    public static void launchLockScreen(Context context) {
        isDeviceLocked = true;
        try {
            Intent lockIntent = new Intent(context, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(lockIntent);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to launch LockScreenActivity: " + t.getMessage());
        }
    }
}
