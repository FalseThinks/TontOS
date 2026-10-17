package org.tontos.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import android.telephony.TelephonyManager;

/**
 * Provides supported Android controls and routes restricted controls to system settings.
 */
public final class UltraBatterySaverManager {

    public enum ToggleState {
        ON,
        OFF,
        UNKNOWN
    }

    public enum ActionResult {
        APPLIED,
        REQUESTED,
        SETTINGS_REQUIRED,
        PERMISSION_REQUIRED,
        UNSUPPORTED,
        FAILED
    }

    private static final int CAMERA_PERMISSION_REQUEST = 761;
    private static final int BLUETOOTH_PERMISSION_REQUEST = 762;

    private static CameraManager torchCameraManager;
    private static String torchCameraId;
    private static ToggleState torchState = ToggleState.UNKNOWN;

    private static final CameraManager.TorchCallback TORCH_CALLBACK = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            synchronized (UltraBatterySaverManager.class) {
                if (cameraId.equals(torchCameraId)) {
                    torchState = enabled ? ToggleState.ON : ToggleState.OFF;
                }
            }
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            synchronized (UltraBatterySaverManager.class) {
                if (cameraId.equals(torchCameraId)) {
                    torchState = ToggleState.UNKNOWN;
                }
            }
        }
    };

    private UltraBatterySaverManager() {}

    public static ToggleState getAirplaneModeState(Context context) {
        if (context == null) return ToggleState.UNKNOWN;
        try {
            return toState(Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0);
        } catch (RuntimeException ignored) {
            return ToggleState.UNKNOWN;
        }
    }

    public static ActionResult openAirplaneModeSettings(Context context) {
        return openSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS);
    }

    public static ToggleState getDndState(Context context) {
        NotificationManager nm = getNotificationManager(context);
        if (nm == null) return ToggleState.UNKNOWN;
        try {
            int filter = nm.getCurrentInterruptionFilter();
            if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    || filter == NotificationManager.INTERRUPTION_FILTER_NONE
                    || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                return ToggleState.ON;
            }
            return filter == NotificationManager.INTERRUPTION_FILTER_ALL
                ? ToggleState.OFF : ToggleState.UNKNOWN;
        } catch (RuntimeException ignored) {
            return ToggleState.UNKNOWN;
        }
    }

    public static ActionResult setDnd(Context context, boolean enable) {
        NotificationManager nm = getNotificationManager(context);
        if (nm == null) return ActionResult.UNSUPPORTED;
        if (!nm.isNotificationPolicyAccessGranted()) {
            return openSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        }
        try {
            nm.setInterruptionFilter(enable
                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALL);
            return getDndState(context) == toState(enable) ? ActionResult.APPLIED : ActionResult.REQUESTED;
        } catch (SecurityException denied) {
            return openSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        } catch (RuntimeException failure) {
            return ActionResult.FAILED;
        }
    }

    public static ToggleState getWifiState(Context context) {
        WifiManager wm = getWifiManager(context);
        if (wm == null) return ToggleState.UNKNOWN;
        try {
            return toState(wm.isWifiEnabled());
        } catch (RuntimeException ignored) {
            return ToggleState.UNKNOWN;
        }
    }

    public static ActionResult setWifi(Context context, boolean enable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            WifiManager wm = getWifiManager(context);
            if (wm != null) {
                try {
                    if (wm.setWifiEnabled(enable)) {
                        return getWifiState(context) == toState(enable)
                            ? ActionResult.APPLIED : ActionResult.REQUESTED;
                    }
                } catch (SecurityException ignored) {
                    // Fall through to the system settings screen.
                }
            }
        }
        return openWifiSettings(context);
    }

    public static ActionResult openWifiSettings(Context context) {
        return openSettings(context, Settings.ACTION_WIFI_SETTINGS);
    }

    public static ToggleState getMobileDataState(Context context) {
        if (context == null) return ToggleState.UNKNOWN;
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return tm == null ? ToggleState.UNKNOWN : toState(tm.isDataEnabled());
        } catch (RuntimeException ignored) {
            return ToggleState.UNKNOWN;
        }
    }

    public static ActionResult openMobileDataSettings(Context context) {
        return openSettings(context, Settings.ACTION_WIRELESS_SETTINGS);
    }

    public static ToggleState getBluetoothState(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            return ToggleState.UNKNOWN;
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return ToggleState.UNKNOWN;
            int state = adapter.getState();
            if (state == BluetoothAdapter.STATE_ON) return ToggleState.ON;
            return state == BluetoothAdapter.STATE_OFF ? ToggleState.OFF : ToggleState.UNKNOWN;
        } catch (RuntimeException ignored) {
            return ToggleState.UNKNOWN;
        }
    }

    public static ActionResult setBluetooth(Context context, boolean enable) {
        // Tiramisu+: direct toggle is privileged; try best-effort then fall back WITHOUT leaving shade
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
                if (ad != null) {
                    boolean ok = enable ? ad.enable() : ad.disable();
                    if (ok && getBluetoothState(context) == toState(enable)) return ActionResult.APPLIED;
                }
            } catch (RuntimeException ignored) {}
            return ActionResult.SETTINGS_REQUIRED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            requestPermission(context, Manifest.permission.BLUETOOTH_CONNECT, BLUETOOTH_PERMISSION_REQUEST);
            return ActionResult.PERMISSION_REQUIRED;
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                boolean accepted = enable ? adapter.enable() : adapter.disable();
                if (accepted) {
                    return getBluetoothState(context) == toState(enable)
                        ? ActionResult.APPLIED : ActionResult.REQUESTED;
                }
            }
        } catch (SecurityException ignored) {
            return openBluetoothSettings(context);
        } catch (RuntimeException ignored) {
            return ActionResult.FAILED;
        }
        return openBluetoothSettings(context);
    }

    public static ActionResult requestBluetoothPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            return ActionResult.REQUESTED;
        }
        requestPermission(context, Manifest.permission.BLUETOOTH_CONNECT, BLUETOOTH_PERMISSION_REQUEST);
        return ActionResult.PERMISSION_REQUIRED;
    }

    public static ActionResult openBluetoothSettings(Context context) {
        return openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    public static ToggleState getTorchState(Context context) {
        synchronized (UltraBatterySaverManager.class) {
            CameraManager manager = getTorchCameraManager(context);
            return manager == null || torchCameraId == null ? ToggleState.UNKNOWN : torchState;
        }
    }

    public static ActionResult toggleTorch(Context context) {
        synchronized (UltraBatterySaverManager.class) {
            CameraManager manager = getTorchCameraManager(context);
            if (manager == null || torchCameraId == null) return ActionResult.UNSUPPORTED;
            boolean enable = torchState != ToggleState.ON;
            try {
                manager.setTorchMode(torchCameraId, enable);
                return ActionResult.REQUESTED;
            } catch (SecurityException denied) {
                SafeCommandExecutor.execute("pm", "grant", "org.tontos.launcher", "android.permission.CAMERA");
                try {
                    manager.setTorchMode(torchCameraId, enable);
                    return ActionResult.REQUESTED;
                } catch (Throwable t) {
                    return ActionResult.FAILED;
                }
            } catch (CameraAccessException failure) {
                return ActionResult.FAILED;
            } catch (RuntimeException failure) {
                return ActionResult.FAILED;
            }
        }
    }

    public static String getActionResultDisplay(ActionResult result, String langCode) {
        if (result == ActionResult.SETTINGS_REQUIRED) {
            return I18n.get(I18n.KEY_ACTION_SETTINGS_REQ, langCode);
        }
        if (result == ActionResult.PERMISSION_REQUIRED) {
            return I18n.get(I18n.KEY_ACTION_PERM_REQ, langCode);
        }
        if (result == ActionResult.UNSUPPORTED) {
            return I18n.get(I18n.KEY_ACTION_UNSUPPORTED, langCode);
        }
        if (result == ActionResult.FAILED) {
            return I18n.get(I18n.KEY_ACTION_FAILED, langCode);
        }
        if (result == ActionResult.APPLIED) {
            return I18n.get(I18n.KEY_ACTION_APPLIED, langCode);
        }
        return I18n.get(I18n.KEY_ACTION_WAITING, langCode);
    }

    public static String getToggleStateDisplay(ToggleState state, String langCode) {
        if (state == ToggleState.ON) return I18n.get(I18n.KEY_STATE_ON, langCode);
        if (state == ToggleState.OFF) return I18n.get(I18n.KEY_STATE_OFF, langCode);
        return I18n.get(I18n.KEY_STATE_UNKNOWN, langCode);
    }

    private static NotificationManager getNotificationManager(Context context) {
        return context == null ? null
            : (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static WifiManager getWifiManager(Context context) {
        return context == null ? null
            : (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private static CameraManager getTorchCameraManager(Context context) {
        if (context == null) return null;
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return null;
        if (manager != torchCameraManager) {
            if (torchCameraManager != null) {
                try { torchCameraManager.unregisterTorchCallback(TORCH_CALLBACK); } catch (RuntimeException ignored) {}
            }
            torchCameraManager = manager;
            torchCameraId = null;
            torchState = ToggleState.UNKNOWN;
            try {
                for (String cameraId : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    if (Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                        torchCameraId = cameraId;
                        break;
                    }
                }
                if (torchCameraId != null) {
                    manager.registerTorchCallback(TORCH_CALLBACK, new Handler(Looper.getMainLooper()));
                }
            } catch (CameraAccessException ignored) {
                torchCameraId = null;
            } catch (RuntimeException ignored) {
                torchCameraId = null;
            }
        }
        return manager;
    }

    public static void releaseTorchCallback() {
        if (torchCameraManager != null) {
            try { torchCameraManager.unregisterTorchCallback(TORCH_CALLBACK); } catch (RuntimeException ignored) {}
            torchCameraManager = null;
            torchCameraId = null;
            torchState = ToggleState.UNKNOWN;
        }
    }

    public static ToggleState getNfcState(Context context) {
        try {
            NfcAdapter a = NfcAdapter.getDefaultAdapter(context);
            if (a == null) return ToggleState.UNKNOWN;
            return toState(a.isEnabled());
        } catch (RuntimeException ignored) {
            return ToggleState.UNKNOWN;
        }
    }

    public static ActionResult setNfc(Context context, boolean enable) {
        boolean currentOn = getNfcState(context) == ToggleState.ON;
        if (currentOn == enable) return ActionResult.APPLIED;
        boolean toggled = false;
        try {
            NfcAdapter a = NfcAdapter.getDefaultAdapter(context);
            if (a != null) {
                try {
                    java.lang.reflect.Method m = enable ? a.getClass().getMethod("enable") : a.getClass().getMethod("disable");
                    Boolean res = (Boolean) m.invoke(a);
                    if (res != null && res) toggled = true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        if (!toggled) {
            toggled = SafeCommandExecutor.execute("svc", "nfc", enable ? "enable" : "disable");
        }
        if (!toggled) {
            SafeCommandExecutor.execute("su", "-c", "svc nfc " + (enable ? "enable" : "disable"));
        }

        if (context != null) {
            context.getSharedPreferences("tontos_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("nfc_enabled", enable).apply();
        }

        if (getNfcState(context) == toState(enable) || toggled) {
            return ActionResult.APPLIED;
        }
        return openNfcSettings(context);
    }

    public static ActionResult openNfcSettings(Context context) {
        if (tryOpen(context, android.provider.Settings.ACTION_NFC_SETTINGS)) return ActionResult.SETTINGS_REQUIRED;
        return openSettings(context, Settings.ACTION_WIRELESS_SETTINGS);
    }

    private static boolean tryOpen(Context context, String action) {
        if (context == null) return false;
        Intent intent = new Intent(action);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); return true; } catch (RuntimeException ignored) { return false; }
    }

    private static boolean hasPermission(Context context, String permission) {
        return context != null
            && context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static void requestPermission(Context context, String permission, int requestCode) {
        // Only an Activity can show the system permission dialog; non-Activity callers
        // get PERMISSION_REQUIRED and must re-invoke from an Activity.
        if (context instanceof Activity && !hasPermission(context, permission)) {
            ((Activity) context).requestPermissions(new String[]{permission}, requestCode);
        }
    }

    private static ActionResult openSettings(Context context, String action) {
        if (context == null) return ActionResult.FAILED;
        Intent intent = new Intent(action);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return ActionResult.SETTINGS_REQUIRED;
        } catch (RuntimeException unavailable) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                if (!(context instanceof Activity)) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return ActionResult.SETTINGS_REQUIRED;
            } catch (RuntimeException ignored) {
                return ActionResult.FAILED;
            }
        }
    }

    private static ToggleState toState(boolean enabled) {
        return enabled ? ToggleState.ON : ToggleState.OFF;
    }
}
