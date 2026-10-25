package org.tontos.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * System Hardening, Debloater & Provisioning Controller for TontOS.
 * Executed deterministically during SetupWizard and launcher initialization.
 */
public final class SystemProvisioningManager {

    private static final String TAG = "SystemProvisioning";

    private static final String[] BLOAT_PACKAGES = {
        "com.android.carrierdefaultapp",
        "com.android.printspooler",
        "com.android.traceur",
        "com.android.musicfx",
        "com.android.egg",
        "com.android.threadnetwork.demoapp",
        "com.google.mainline.adservices",
        "com.google.android.federatedcompute",
        "com.android.devicediagnostics"
    };

    private static final String[] AUTO_PERMISSIONS = {
        "android.permission.CAMERA",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.POST_NOTIFICATIONS"
    };

    private static final String[][] FOSS_APP_PERMISSIONS = {
        {
            "org.fossify.gallery",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_MEDIA_LOCATION",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "org.fossify.camera",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "org.fossify.messages",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "org.fossify.phone",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "org.fossify.contacts",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "org.fossify.filemanager",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "org.fossify.musicplayer",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "eu.faircode.email",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.GET_ACCOUNTS"
        },
        {
            "de.baumann.browser",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "org.breezyweather",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "app.organicmaps",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS"
        },
        {
            "com.aurora.store",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS"
        }
    };

    private SystemProvisioningManager() {}

    private static void grantPermissionSafely(Context context, String pkg, String perm) {
        try {
            PackageManager pm = context.getPackageManager();
            PermissionInfo pi = pm.getPermissionInfo(perm, 0);
            if ((pi.getProtection() & PermissionInfo.PROTECTION_DANGEROUS) == 0) {
                // Normal or signature permission; auto-granted by manifest or system
                return;
            }
            Method grantMethod = pm.getClass().getMethod("grantRuntimePermission", String.class, String.class, UserHandle.class);
            grantMethod.invoke(pm, pkg, perm, Process.myUserHandle());
            return;
        } catch (Throwable ignored) {
            // Fallback to privileged pm grant command
        }
        SafeCommandExecutor.executePrivileged("pm", "grant", pkg, perm);
    }

    private static void setAppOpSafely(Context context, String pkg, String opStr, String cmdMode) {
        try {
            Object appOps = context.getSystemService(Context.APP_OPS_SERVICE);
            int uid = context.getPackageManager().getPackageUid(pkg, 0);
            Method setMode = appOps.getClass().getMethod("setMode", String.class, int.class, String.class, int.class);
            int modeInt = "allow".equalsIgnoreCase(cmdMode) ? 0 : 1; // 0 = MODE_ALLOWED, 1 = MODE_IGNORED
            setMode.invoke(appOps, opStr, uid, pkg, modeInt);
            return;
        } catch (Throwable ignored) {
            // Fallback to privileged cmd appops set
        }
        SafeCommandExecutor.executePrivileged("cmd", "appops", "set", pkg, opStr, cmdMode);
    }

    /**
     * Executes aggressive debloat and system isolation asynchronously.
     */
    public static void executeInitialProvisioning(final Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Debloat non-essential stock/telemetry packages for user 0
                    for (String pkg : BLOAT_PACKAGES) {
                        try {
                            SafeCommandExecutor.executePrivileged("pm", "disable-user", "--user", "0", pkg);
                            SafeCommandExecutor.executePrivileged("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "ignore");
                        } catch (Throwable ignored) {}
                    }

                    // 2. Pre-grant privileged and runtime permissions to TontOSLauncher
                    for (String perm : AUTO_PERMISSIONS) {
                        grantPermissionSafely(appContext, "org.tontos.launcher", perm);
                    }

                    // 2b. Pre-grant essential permissions and AppOps to FOSS suite
                    for (String[] entry : FOSS_APP_PERMISSIONS) {
                        String pkg = entry[0];
                        for (int i = 1; i < entry.length; i++) {
                            grantPermissionSafely(appContext, pkg, entry[i]);
                        }
                    }

                    // AppOps storage and media authorizations
                    setAppOpSafely(appContext, "org.fossify.filemanager", "android:manage_external_storage", "allow");
                    setAppOpSafely(appContext, "org.fossify.gallery", "android:manage_external_storage", "allow");
                    setAppOpSafely(appContext, "org.fossify.camera", "android:camera", "allow");
                    setAppOpSafely(appContext, "org.fossify.camera", "android:record_audio", "allow");
                    setAppOpSafely(appContext, "com.aurora.store", "android:manage_external_storage", "allow");

                    // 3. Lock default launcher to TontOSLauncher
                    SafeCommandExecutor.executePrivileged("cmd", "package", "set-home-activity", "org.tontos.launcher/.MainActivity");
                    SafeCommandExecutor.executePrivileged("cmd", "role", "add-role-holder", "android.app.role.HOME", "org.tontos.launcher", "1");

                    // 4. Set FOSS dialer as default phone app and disable legacy AOSP dialer/contacts
                    SafeCommandExecutor.executePrivileged("cmd", "role", "set-bypassing-role-qualification", "true");
                    SafeCommandExecutor.executePrivileged("cmd", "role", "add-role-holder", "--user", "0", "android.app.role.DIALER", "org.fossify.phone", "1");
                    SafeCommandExecutor.executePrivileged("pm", "disable-user", "--user", "0", "com.android.dialer");
                    SafeCommandExecutor.executePrivileged("pm", "disable-user", "--user", "0", "com.android.contacts");

                    // 4b. Configure HeliBoard FOSS keyboard as default IME
                    try {
                        SafeCommandExecutor.executePrivileged("ime", "enable", "helium314.keyboard/.latin.LatinIME");
                        SafeCommandExecutor.executePrivileged("ime", "set", "helium314.keyboard/.latin.LatinIME");
                        SafeCommandExecutor.executePrivileged("settings", "put", "secure", "default_input_method", "helium314.keyboard/.latin.LatinIME");
                        SafeCommandExecutor.executePrivileged("settings", "put", "secure", "enabled_input_methods", "helium314.keyboard/.latin.LatinIME:com.android.inputmethod.latin/.LatinIME");
                    } catch (Throwable ignored) {}

                    // 5. Disable SystemUI notification shade and peek expansion
                    SafeCommandExecutor.executePrivileged("cmd", "statusbar", "send-disable-flag",
                        "statusbar-expansion", "quick-settings", "notification-peek", "system-icons", "notification-icons", "clock");

                    // 6. Set privacy defaults
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "heads_up_notifications_enabled", "0");
                    SafeCommandExecutor.executePrivileged("settings", "put", "secure", "notification_bubbles", "0");

                    // 7. Ensure default sound settings reference existing media audio files
                    SafeCommandExecutor.executePrivileged("settings", "put", "system", "ringtone", "content://media/internal/audio/media/5");
                    SafeCommandExecutor.executePrivileged("settings", "put", "system", "notification_sound", "content://media/internal/audio/media/7");
                    SafeCommandExecutor.executePrivileged("settings", "put", "system", "alarm_alert", "content://media/internal/audio/media/9");

                    // 8. Enable Developer Options by default
                    try {
                        android.provider.Settings.Global.putInt(appContext.getContentResolver(), android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
                    } catch (Throwable ignored) {}
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "development_settings_enabled", "1");

                    // 9. ROM Lock Screen & Power Policy: Preserve hardware AOSP keyguard & FBE encryption
                    SafeCommandExecutor.executePrivileged("cmd", "deviceidle", "whitelist", "+org.tontos.launcher");
                    SafeCommandExecutor.executePrivileged("cmd", "appops", "set", "org.tontos.launcher", "RUN_IN_BACKGROUND", "allow");
                    SafeCommandExecutor.executePrivileged("cmd", "appops", "set", "org.tontos.launcher", "SYSTEM_ALERT_WINDOW", "allow");
                    LockScreenManager.registerScreenReceiver(appContext);

                    // 10. Performance First: 0ms window animations system-wide
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "window_animation_scale", "0");
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "transition_animation_scale", "0");
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "animator_duration_scale", "0");

                    // 11. Ultra Battery Saving: Disable background scanning and enforce aggressive Doze sleep (30s)
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "wifi_scan_always_enabled", "0");
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "ble_scan_always_enabled", "0");
                    SafeCommandExecutor.executePrivileged("settings", "put", "global", "device_idle_constants",
                        "inactive_to=30000,sensing_to=0,locating_to=0,location_accuracy=20.0,motion_inactive_to=0,idle_after_inactive_to=0,idle_pending_to=60000,max_idle_pending_to=120000,idle_pending_factor=2.0,idle_to=1800000,max_idle_to=21600000,idle_factor=2.0,min_time_to_alarm=3600000");

                    // 12. Start System-Wide Top Edge Gesture & Lockscreen Core Service
                    try {
                        Intent serviceIntent = new Intent(appContext, TontOSShadeService.class);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            appContext.startForegroundService(serviceIntent);
                        } else {
                            appContext.startService(serviceIntent);
                        }
                    } catch (Throwable ignored) {}

                    Log.i(TAG, "Initial TontOS system provisioning, debloat and hardening completed successfully.");
                } catch (Throwable t) {
                    Log.w(TAG, "Provisioning step encountered error: " + t.getMessage());
                }
            }
        }, "TontOS-Provisioning-Thread").start();
    }
}
