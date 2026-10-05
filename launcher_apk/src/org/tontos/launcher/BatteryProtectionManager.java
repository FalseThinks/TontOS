package org.tontos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Battery Health & Hardware Charging Control Subsystem for TontOS.
 * Supports:
 * 1. 80% charge ceiling (stops cell swelling and prolongs Li-Ion cycle life).
 * 2. Bypass charging (powers hardware directly from USB VBUS via input_suspend without cycling battery cell).
 * 3. Kernel sysfs hooks for standard Android 14+ Health HAL, Qualcomm PMIC and MediaTek Charger.
 */
public final class BatteryProtectionManager {

    private static final String TAG = "BatteryProtectMgr";

    public static final String PREF_BATTERY_CHARGE_LIMIT_MODE = "pref_battery_charge_limit_mode";

    public static final int MODE_100 = 0;
    public static final int MODE_80 = 1;
    public static final int MODE_BYPASS = 2;

    private static int lastMode = -1;
    private static int lastBatteryLevel = -1;
    private static boolean lastCharging = false;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // Standard Android 14+ Health HAL nodes
    private static final String NODE_CHARGE_CONTROL_LIMIT = "/sys/class/power_supply/battery/charge_control_limit";
    private static final String NODE_CHARGE_CONTROL_LIMIT_MAX = "/sys/class/power_supply/battery/charge_control_limit_max";

    // Qualcomm & MediaTek hardware PMIC charging switches
    private static final String NODE_CHARGING_ENABLED = "/sys/class/power_supply/battery/charging_enabled";
    private static final String NODE_INPUT_SUSPEND = "/sys/class/power_supply/battery/input_suspend";
    private static final String NODE_QCOM_RESTRICTED = "/sys/class/qcom-battery/restricted_charging";
    private static final String NODE_MTK_CHARGING = "/sys/devices/platform/charger/charging_enabled";

    private BatteryProtectionManager() {}

    public static int getMode(Context context) {
        if (context == null) return MODE_100;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_BATTERY_CHARGE_LIMIT_MODE, MODE_100);
    }

    public static void setMode(Context context, int mode) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_BATTERY_CHARGE_LIMIT_MODE, mode).apply();
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

    /**
     * Checks if device kernel exposes charging control interfaces.
     */
    public static boolean isHardwareSupported() {
        return new File(NODE_CHARGE_CONTROL_LIMIT).exists()
            || new File(NODE_CHARGING_ENABLED).exists()
            || new File(NODE_INPUT_SUSPEND).exists()
            || new File(NODE_QCOM_RESTRICTED).exists()
            || new File(NODE_MTK_CHARGING).exists();
    }

    /**
     * Applies charging limits according to current battery level and chosen mode.
     */
    public static void applyHardwareProtection(Context context, final int currentBatteryLevel, final boolean isCharging) {
        if (!isHardwareSupported()) return;
        final int mode = getMode(context);
        synchronized (BatteryProtectionManager.class) {
            if (mode == lastMode && isCharging == lastCharging) {
                if (mode != MODE_80 || (lastBatteryLevel >= 80 && currentBatteryLevel >= 80) || (lastBatteryLevel <= 78 && currentBatteryLevel <= 78)) {
                    return;
                }
            }
            lastMode = mode;
            lastBatteryLevel = currentBatteryLevel;
            lastCharging = isCharging;
        }

        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                switch (mode) {
                    case MODE_80:
                        // Standard Android 14+ Health HAL
                        writeSysfs(NODE_CHARGE_CONTROL_LIMIT, "80");
                        if (currentBatteryLevel >= 80) {
                            // Cut charging when ceiling reached
                            writeSysfs(NODE_CHARGING_ENABLED, "0");
                            writeSysfs(NODE_MTK_CHARGING, "0");
                            writeSysfs(NODE_QCOM_RESTRICTED, "1");
                        } else if (currentBatteryLevel <= 78) {
                            // Hysteresis: resume charging when drops below 78%
                            writeSysfs(NODE_CHARGING_ENABLED, "1");
                            writeSysfs(NODE_MTK_CHARGING, "1");
                            writeSysfs(NODE_QCOM_RESTRICTED, "0");
                            writeSysfs(NODE_INPUT_SUSPEND, "0");
                        }
                        break;

                    case MODE_BYPASS:
                        // Suspend VBUS charging intake; power SoC directly from USB
                        writeSysfs(NODE_INPUT_SUSPEND, "1");
                        writeSysfs(NODE_CHARGING_ENABLED, "0");
                        writeSysfs(NODE_MTK_CHARGING, "0");
                        break;

                    case MODE_100:
                    default:
                        // Full standard charging to 100%
                        writeSysfs(NODE_CHARGE_CONTROL_LIMIT, "100");
                        writeSysfs(NODE_CHARGING_ENABLED, "1");
                        writeSysfs(NODE_INPUT_SUSPEND, "0");
                        writeSysfs(NODE_MTK_CHARGING, "1");
                        writeSysfs(NODE_QCOM_RESTRICTED, "0");
                        break;
                }
            }
        });
    }

    private static boolean writeSysfs(String path, String value) {
        File file = new File(path);
        if (!file.exists()) return false;

        // Try direct userspace write
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(value.getBytes());
            fos.flush();
            return true;
        } catch (Throwable t) {
            // Fallback via shell/su command for privileged sysfs nodes
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "echo " + value + " > " + path});
                return p.waitFor() == 0;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
