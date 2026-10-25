package org.tontos.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * System settings bridge for TontOS.
 * Synchronizes launcher preferences with Android system settings.
 */
public final class GlobalSettingsBridge {

    private static final String TAG = "GlobalSettingsBridge";
    public static final String PREFS_NAME = "tontos_prefs";
    public static final String KEY_FONT_SCALE = "pref_font_scale";
    public static final String KEY_TIMEOUT = "screen_timeout_secs";
    public static final String KEY_BRIGHTNESS = "screen_brightness_percent";

    private GlobalSettingsBridge() {}

    /**
     * Resolves current font scale factor (0.85f - 1.30f).
     */
    public static float getFontScale(Context context) {
        if (context == null) return 1.0f;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int scaleInt = prefs.getInt(KEY_FONT_SCALE, 100);
        return Math.max(0.5f, Math.min(2.0f, scaleInt / 100.0f));
    }

    /**
     * Globally applies UI Font Scale to the entire Android operating system and all apps.
     */
    public static void applyGlobalFontScale(final Context context, final float scale) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        final int scaleInt = Math.round(scale * 100.0f);

        // 1. Save locally in TontOS launcher preferences
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FONT_SCALE, scaleInt).apply();

        // 2. Apply asynchronously to Android framework, system providers, and ActivityManager
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Update Settings.System font_scale
                    try {
                        Settings.System.putFloat(appCtx.getContentResolver(), Settings.System.FONT_SCALE, scale);
                    } catch (Throwable ignored) {}

                    // Privileged shell command fallback
                    SafeCommandExecutor.executePrivileged("settings", "put", "system", "font_scale", String.format(Locale.US, "%.2f", scale));

                    // Broadcast global configuration change
                    try {
                        Intent configIntent = new Intent(Intent.ACTION_CONFIGURATION_CHANGED);
                        appCtx.sendBroadcast(configIntent);
                    } catch (Throwable ignored) {}

                } catch (Throwable t) {
                    Log.w(TAG, "Failed applying global font scale: " + t.getMessage());
                }
            }
        }, "TontOS-GlobalFontApplier").start();

        // 3. Synchronize local process resources
        try {
            Configuration localConfig = new Configuration(context.getResources().getConfiguration());
            localConfig.fontScale = scale;
            context.getResources().updateConfiguration(localConfig, context.getResources().getDisplayMetrics());
        } catch (Throwable ignored) {}
    }

    /**
     * Globally applies display screen timeout to the Android system.
     */
    public static void applyGlobalScreenTimeout(final Context context, final int secs) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TIMEOUT, secs).apply();

        final long ms = (secs <= 0) ? 2147483647L : ((long) secs * 1000L);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Settings.System.putInt(appCtx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, (int) Math.min(ms, Integer.MAX_VALUE));
                    } catch (Throwable ignored) {}
                    SafeCommandExecutor.executePrivileged("settings", "put", "system", "screen_off_timeout", String.valueOf(ms));
                } catch (Throwable t) {
                    Log.w(TAG, "Failed applying screen timeout: " + t.getMessage());
                }
            }
        }, "TontOS-GlobalTimeoutApplier").start();
    }

    /**
     * Globally applies screen brightness (0-100%) to the Android system.
     */
    public static void applyGlobalBrightness(final Context context, final int progressPercent) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_BRIGHTNESS, progressPercent).apply();

        final int val255 = (int) Math.round((Math.max(0, Math.min(100, progressPercent)) / 100.0) * 255.0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Settings.System.putInt(appCtx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                        Settings.System.putInt(appCtx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, val255);
                    } catch (Throwable ignored) {}
                    SafeCommandExecutor.executePrivileged("settings", "put", "system", "screen_brightness_mode", "0");
                    SafeCommandExecutor.executePrivileged("settings", "put", "system", "screen_brightness", String.valueOf(val255));
                } catch (Throwable t) {
                    Log.w(TAG, "Failed applying brightness: " + t.getMessage());
                }
            }
        }, "TontOS-GlobalBrightnessApplier").start();
    }

    /**
     * Globally applies Android ringer mode (NORMAL, VIBRATE, SILENT).
     */
    public static void applyGlobalRingerMode(final Context context, final int ringerMode) {
        if (context == null) return;
        final String modeStr;
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            modeStr = "SILENT";
        } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            modeStr = "VIBRATE";
        } else {
            modeStr = "NORMAL";
        }

        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setRingerMode(ringerMode);
            }
        } catch (Throwable ignored) {}

        new Thread(new Runnable() {
            @Override
            public void run() {
                SafeCommandExecutor.executePrivileged("cmd", "notification", "allow_dnd", context.getPackageName());
                SafeCommandExecutor.executePrivileged("cmd", "audio", "set-ringer-mode", modeStr);
            }
        }, "TontOS-GlobalRingerApplier").start();
    }

    /**
     * Globally sets stream volume.
     */
    public static void applyGlobalStreamVolume(final Context context, final int streamType, final int volume) {
        if (context == null) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(streamType, volume, AudioManager.FLAG_SHOW_UI);
            }
        } catch (Throwable ignored) {}

        new Thread(new Runnable() {
            @Override
            public void run() {
                SafeCommandExecutor.executePrivileged("cmd", "media_session", "volume", "--stream", String.valueOf(streamType), "--set", String.valueOf(volume));
            }
        }, "TontOS-GlobalVolumeApplier").start();
    }

    /**
     * Configures Lock Screen Owner Info and emergency message globally in Android Keyguard.
     */
    public static void applyGlobalLockScreenOwnerInfo(final Context context, final String ownerInfo) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        final String text = ownerInfo != null ? ownerInfo.trim() : "";
        final int enabledVal = text.isEmpty() ? 0 : 1;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Settings.Secure.putString(appCtx.getContentResolver(), "lock_screen_owner_info", text);
                        Settings.Secure.putInt(appCtx.getContentResolver(), "lock_screen_owner_info_enabled", enabledVal);
                    } catch (Throwable ignored) {}

                    SafeCommandExecutor.executePrivileged("settings", "put", "secure", "lock_screen_owner_info", text);
                    SafeCommandExecutor.executePrivileged("settings", "put", "secure", "lock_screen_owner_info_enabled", String.valueOf(enabledVal));
                } catch (Throwable t) {
                    Log.w(TAG, "Failed applying lock screen owner info: " + t.getMessage());
                }
            }
        }, "TontOS-GlobalOwnerInfoApplier").start();
    }

    /**
     * Configures Lock Screen Notification privacy policy globally in Android Keyguard.
     */
    public static void applyGlobalLockScreenNotifications(final Context context, final LockNotificationMode mode) {
        if (context == null || mode == null) return;
        final Context appCtx = context.getApplicationContext();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    switch (mode) {
                        case DONT_SHOW:
                            try {
                                Settings.Secure.putInt(appCtx.getContentResolver(), "lock_screen_show_notifications", 0);
                            } catch (Throwable ignored) {}
                            SafeCommandExecutor.executePrivileged("settings", "put", "secure", "lock_screen_show_notifications", "0");
                            break;

                        case HIDE_SENSITIVE:
                            try {
                                Settings.Secure.putInt(appCtx.getContentResolver(), "lock_screen_show_notifications", 1);
                                Settings.Secure.putInt(appCtx.getContentResolver(), "lock_screen_allow_private_notifications", 0);
                            } catch (Throwable ignored) {}
                            SafeCommandExecutor.executePrivileged("settings", "put", "secure", "lock_screen_show_notifications", "1");
                            SafeCommandExecutor.executePrivileged("settings", "put", "secure", "lock_screen_allow_private_notifications", "0");
                            break;

                        case SHOW_ALL:
                        default:
                            try {
                                Settings.Secure.putInt(appCtx.getContentResolver(), "lock_screen_show_notifications", 1);
                                Settings.Secure.putInt(appCtx.getContentResolver(), "lock_screen_allow_private_notifications", 1);
                            } catch (Throwable ignored) {}
                            SafeCommandExecutor.executePrivileged("settings", "put", "secure", "lock_screen_show_notifications", "1");
                            SafeCommandExecutor.executePrivileged("settings", "put", "secure", "lock_screen_allow_private_notifications", "1");
                            break;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed applying lock screen notifications: " + t.getMessage());
                }
            }
        }, "TontOS-GlobalLockNotifApplier").start();
    }
}
