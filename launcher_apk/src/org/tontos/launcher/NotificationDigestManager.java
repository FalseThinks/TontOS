package org.tontos.launcher;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;

/**
 * Manages scheduled notification batching and calm delivery logic (LightOS / Mudita pattern).
 * Synchronizes notification interruptions with Android's Do Not Disturb (ZenMode) system.
 */
public final class NotificationDigestManager {

    public enum DeliveryMode {
        IMMEDIATE,
        SCHEDULED_DIGEST,
        STARRED_ONLY
    }

    public static final String PREF_KEY_DELIVERY_MODE = "notification_delivery_mode";
    public static final int DIGEST_HOUR_MIDDAY = 12;
    public static final int DIGEST_HOUR_EVENING = 18;

    private NotificationDigestManager() {}

    /**
     * Retrieves the active notification delivery mode from preferences.
     */
    public static DeliveryMode getDeliveryMode(Context context) {
        if (context == null) {
            return DeliveryMode.IMMEDIATE;
        }
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String modeName = prefs.getString(PREF_KEY_DELIVERY_MODE, DeliveryMode.IMMEDIATE.name());
        try {
            return DeliveryMode.valueOf(modeName);
        } catch (Exception e) {
            return DeliveryMode.IMMEDIATE;
        }
    }

    /**
     * Persists the selected delivery mode into preferences.
     */
    public static void setDeliveryMode(Context context, DeliveryMode mode) {
        if (context == null || mode == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_KEY_DELIVERY_MODE, mode.name()).apply();
    }

    /**
     * Returns the localized digest schedule summary string (12:00 & 18:00).
     */
    public static String getDigestScheduleSummary(Context context, String langCode) {
        return I18n.get(I18n.KEY_NOTIF_DIGEST, langCode);
    }

    /**
     * Synchronizes Android Do Not Disturb / InterruptionFilter with the specified DeliveryMode.
     * STARRED_ONLY -> INTERRUPTION_FILTER_PRIORITY
     * SCHEDULED_DIGEST -> INTERRUPTION_FILTER_NONE
     * IMMEDIATE -> INTERRUPTION_FILTER_ALL
     */
    public static void syncDoNotDisturb(Context context, DeliveryMode mode) {
        if (context == null || mode == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                try {
                    int interruptionFilter;
                    switch (mode) {
                        case STARRED_ONLY:
                            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
                            break;
                        case SCHEDULED_DIGEST:
                            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
                            break;
                        case IMMEDIATE:
                        default:
                            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
                            break;
                    }
                    nm.setInterruptionFilter(interruptionFilter);
                } catch (SecurityException ignored) {
                }
            }
        }
    }

    /**
     * Convenience method to synchronize Do Not Disturb using the saved delivery mode.
     */
    public static void syncDoNotDisturb(Context context) {
        syncDoNotDisturb(context, getDeliveryMode(context));
    }

    /**
     * Checks if current time is within scheduled digest release hours (12:00 or 18:00).
     */
    public static boolean isDigestReleaseWindow() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        return hour == DIGEST_HOUR_MIDDAY || hour == DIGEST_HOUR_EVENING;
    }
}
