package org.tontos.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the standard charging preference without claiming unsupported hardware control.
 *
 * Ordinary Android apps have no public API for charge limits or bypass charging.
 */
public final class BatteryProtectionManager {

    public static final String PREF_BATTERY_CHARGE_LIMIT_MODE = "pref_battery_charge_limit_mode";

    public static final int MODE_100 = 0;
    public static final int MODE_80 = 1;
    public static final int MODE_BYPASS = 2;

    public enum ModeResult {
        APPLIED,
        UNSUPPORTED,
        INVALID
    }

    private BatteryProtectionManager() {}

    public static int getMode(Context context) {
        return MODE_100;
    }

    public static ModeResult setMode(Context context, int mode) {
        if (context == null || (mode != MODE_100 && mode != MODE_80 && mode != MODE_BYPASS)) {
            return ModeResult.INVALID;
        }
        if (mode != MODE_100) return ModeResult.UNSUPPORTED;

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_BATTERY_CHARGE_LIMIT_MODE, MODE_100).apply();
        return ModeResult.APPLIED;
    }

    public static String getModeDisplay(int mode, String langCode) {
        if (mode == MODE_80) {
            return I18n.get(I18n.KEY_BATTERY_80, langCode);
        } else if (mode == MODE_BYPASS) {
            return I18n.get(I18n.KEY_BATTERY_BYPASS, langCode);
        } else {
            return I18n.get(I18n.KEY_BATTERY_100, langCode);
        }
    }

    public static String getSettingsDisplay(String langCode) {
        return I18n.get(I18n.KEY_BATTERY_CTRL_NA, langCode);
    }

    public static String getOptionDisplay(int mode, String langCode) {
        if (mode == MODE_80) {
            return I18n.get(I18n.KEY_BATTERY_80_NA, langCode);
        }
        if (mode == MODE_BYPASS) {
            return I18n.get(I18n.KEY_BATTERY_BYPASS_NA, langCode);
        }
        return getModeDisplay(mode, langCode);
    }

    public static String getModeResultDisplay(ModeResult result, String langCode) {
        if (result == ModeResult.UNSUPPORTED) {
            return I18n.get(I18n.KEY_BATTERY_UNSUPPORTED_MSG, langCode);
        }
        if (result == ModeResult.APPLIED) {
            return I18n.get(I18n.KEY_BATTERY_APPLIED_MSG, langCode);
        }
        return I18n.get(I18n.KEY_BATTERY_INVALID_MSG, langCode);
    }

    /**
     * Retained for existing callers; node presence is not evidence that this app can safely
     * control charging, so this capability remains unavailable.
     */
    public static boolean isHardwareSupported() {
        return false;
    }

    /**
     * Retained for compatibility with the battery receiver. It deliberately performs no
     * filesystem or shell writes because charge-control interfaces are privileged and
     * vendor-specific.
     */
    public static void applyHardwareProtection(Context context, int currentBatteryLevel, boolean isCharging) {
        // No ordinary-app Android API applies charging limits or bypass mode.
    }

}
