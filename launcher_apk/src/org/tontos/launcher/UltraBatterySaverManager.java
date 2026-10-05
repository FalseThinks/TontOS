package org.tontos.launcher;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

/**
 * Facade manager for Ultra Battery Saver mode and minimalist hardware toggles
 * (Airplane mode, Do Not Disturb, Wi-Fi, Mobile Data, Bluetooth, Torch).
 */
public final class UltraBatterySaverManager {

    private static boolean isTorchOn = false;

    private UltraBatterySaverManager() {}

    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    public static void setAirplaneMode(Context context, boolean enable) {
        try {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, enable ? 1 : 0);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", enable);
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            try {
                Runtime.getRuntime().exec(new String[]{
                    "cmd", "connectivity", "airplane-mode", enable ? "enable" : "disable"
                });
            } catch (Throwable ignored) {}
        }
    }

    public static boolean isDndOn(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int filter = nm.getCurrentInterruptionFilter();
            return filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                || filter == NotificationManager.INTERRUPTION_FILTER_NONE
                || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS;
        }
        return false;
    }

    public static void setDnd(Context context, boolean enable) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                if (nm.isNotificationPolicyAccessGranted()) {
                    nm.setInterruptionFilter(enable
                        ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        : NotificationManager.INTERRUPTION_FILTER_ALL);
                    return;
                }
            } catch (Exception ignored) {}
        }
        try {
            Runtime.getRuntime().exec(new String[]{
                "cmd", "notification", "set_dnd", enable ? "priority" : "off"
            });
        } catch (Exception ignored) {}
    }

    public static boolean isWifiOn(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wm != null && wm.isWifiEnabled();
    }

    public static void setWifi(Context context, boolean enable) {
        try {
            Runtime.getRuntime().exec(new String[]{
                "svc", "wifi", enable ? "enable" : "disable"
            });
        } catch (Exception ignored) {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                try { wm.setWifiEnabled(enable); } catch (Exception ignored2) {}
            }
        }
    }

    public static boolean isMobileDataOn(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null && tm.isDataEnabled();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void setMobileData(Context context, boolean enable) {
        try {
            Runtime.getRuntime().exec(new String[]{
                "svc", "data", enable ? "enable" : "disable"
            });
        } catch (Exception ignored) {
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) tm.setDataEnabled(enable);
            } catch (Exception ignored2) {}
        }
    }

    public static boolean isBluetoothOn() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void setBluetooth(boolean enable) {
        try {
            Runtime.getRuntime().exec(new String[]{
                "cmd", "bluetooth", enable ? "enable" : "disable"
            });
        } catch (Exception ignored) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    if (enable) adapter.enable(); else adapter.disable();
                }
            } catch (Exception ignored2) {}
        }
    }

    public static boolean isTorchOn() {
        return isTorchOn;
    }

    public static void toggleTorch(Context context) {
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                String[] ids = cm.getCameraIdList();
                if (ids.length > 0) {
                    isTorchOn = !isTorchOn;
                    cm.setTorchMode(ids[0], isTorchOn);
                }
            }
        } catch (Exception ignored) {}
    }

    public static int getEstimatedAutonomyHours(int batteryLevel) {
        if (batteryLevel <= 0) return 0;
        return Math.max(1, Math.round(batteryLevel * 0.75f));
    }
}
