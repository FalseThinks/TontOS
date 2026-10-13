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

    public static final String THEME_MATCH_ACTIVE = "MATCH_ACTIVE";
    public static final String DEFAULT_OWNER_INFO = "TontOS • Stay intentional";

    private static boolean receiverRegistered = false;
    private static BroadcastReceiver screenReceiver = null;

    private LockScreenManager() {}

    public static boolean isLockScreenEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LOCKSCREEN_ENABLED, true);
    }

    public static void setLockScreenEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCKSCREEN_ENABLED, enabled).apply();
    }

    public static LockClockStyle getClockStyle(Context context) {
        if (context == null) return LockClockStyle.THEME_DEFAULT;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_LOCK_CLOCK_STYLE, LockClockStyle.THEME_DEFAULT.getId());
        return LockClockStyle.fromId(id);
    }

    public static void setClockStyle(Context context, LockClockStyle style) {
        if (context == null || style == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCK_CLOCK_STYLE, style.getId()).apply();
    }

    public static String getThemeOverride(Context context) {
        if (context == null) return THEME_MATCH_ACTIVE;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LOCK_THEME_OVERRIDE, THEME_MATCH_ACTIVE);
    }

    public static void setThemeOverride(Context context, String override) {
        if (context == null || override == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCK_THEME_OVERRIDE, override).apply();
    }

    public static boolean isShowDetox(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LOCK_SHOW_DETOX, true);
    }

    public static void setShowDetox(Context context, boolean show) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCK_SHOW_DETOX, show).apply();
    }

    public static boolean isShowBattery(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LOCK_SHOW_BATTERY, true);
    }

    public static void setShowBattery(Context context, boolean show) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCK_SHOW_BATTERY, show).apply();
    }

    public static String getOwnerInfo(Context context) {
        if (context == null) return DEFAULT_OWNER_INFO;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LOCK_OWNER_INFO, DEFAULT_OWNER_INFO);
    }

    public static void setOwnerInfo(Context context, String info) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCK_OWNER_INFO, info != null ? info.trim() : "").apply();
    }

    public static LockShortcut getLeftShortcut(Context context) {
        if (context == null) return LockShortcut.TORCH;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_LOCK_SHORTCUT_LEFT, LockShortcut.TORCH.getId());
        return LockShortcut.fromId(id);
    }

    public static void setLeftShortcut(Context context, LockShortcut shortcut) {
        if (context == null || shortcut == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCK_SHORTCUT_LEFT, shortcut.getId()).apply();
    }

    public static LockShortcut getRightShortcut(Context context) {
        if (context == null) return LockShortcut.CAMERA;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_LOCK_SHORTCUT_RIGHT, LockShortcut.CAMERA.getId());
        return LockShortcut.fromId(id);
    }

    public static void setRightShortcut(Context context, LockShortcut shortcut) {
        if (context == null || shortcut == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCK_SHORTCUT_RIGHT, shortcut.getId()).apply();
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
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String activeTheme = prefs.getString("selected_theme", "AMOLED");
        return ThemeTokens.get(activeTheme);
    }

    /**
     * Executes designated lock screen shortcut safely.
     */
    public static void executeShortcut(Activity activity, LockShortcut shortcut) {
        if (activity == null || shortcut == null || shortcut == LockShortcut.NONE) return;
        PackageManager pm = activity.getPackageManager();

        switch (shortcut) {
            case TORCH:
                UltraBatterySaverManager.toggleTorch(activity);
                break;
            case CAMERA:
                try {
                    Intent camIntent = pm.getLaunchIntentForPackage("org.fossify.camera");
                    if (camIntent == null) {
                        camIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    }
                    camIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(camIntent);
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to launch camera: " + t.getMessage());
                }
                break;
            case PHONE:
                try {
                    Intent phoneIntent = pm.getLaunchIntentForPackage("org.fossify.phone");
                    if (phoneIntent == null) {
                        phoneIntent = new Intent(Intent.ACTION_DIAL);
                    }
                    phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(phoneIntent);
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to launch phone: " + t.getMessage());
                }
                break;
            case BROWSER:
                try {
                    Intent browserIntent = pm.getLaunchIntentForPackage("de.baumann.browser");
                    if (browserIntent != null) {
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(browserIntent);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to launch browser: " + t.getMessage());
                }
                break;
            default:
                break;
        }
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
                    if (isLockScreenEnabled(appContext)) {
                        Intent lockIntent = new Intent(appContext, LockScreenActivity.class);
                        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        appContext.startActivity(lockIntent);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        appContext.registerReceiver(screenReceiver, filter);
        receiverRegistered = true;
    }
}
