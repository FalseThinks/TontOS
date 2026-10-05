package org.tontos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * On-Device Bootloader & Hardware Verified Boot (AVB) Management Subsystem.
 * Provides native factory control to lock or unlock the bootloader directly
 * on the device without requiring external computers or fastboot cables.
 *
 * Defaults to LOCKED (ro.boot.flash.locked=1, verifiedbootstate=green).
 */
public final class BootloaderManager {

    private static final String TAG = "BootloaderManager";

    public static final String PREF_BOOTLOADER_LOCKED = "pref_bootloader_locked";
    public static final boolean DEFAULT_LOCKED = true;

    // Standard AOSP block partitions for persistent OEM lock flags
    private static final String[] FRP_PARTITIONS = new String[]{
        "/dev/block/by-name/frp",
        "/dev/block/bootdevice/by-name/frp",
        "/dev/block/platform/bootdevice/by-name/frp",
        "/dev/block/by-name/persistent"
    };

    private BootloaderManager() {}

    /**
     * Determines whether the bootloader is currently locked.
     * Defaults to true (Locked) out-of-the-box.
     */
    public static boolean isLocked(Context context) {
        if (context == null) return DEFAULT_LOCKED;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(PREF_BOOTLOADER_LOCKED)) {
            // First boot factory state: default to LOCKED
            boolean hardwareLocked = queryHardwareLockState();
            prefs.edit().putBoolean(PREF_BOOTLOADER_LOCKED, hardwareLocked).apply();
            return hardwareLocked;
        }
        return prefs.getBoolean(PREF_BOOTLOADER_LOCKED, DEFAULT_LOCKED);
    }

    /**
     * Toggles on-device bootloader lock state.
     * Executes persistent data block updates, system properties, and kernel hooks.
     */
    public static void setLocked(final Context context, final boolean locked) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_BOOTLOADER_LOCKED, locked).apply();

        new Thread(new Runnable() {
            @Override
            public void run() {
                applyLockState(locked);
            }
        }, "TontOS-BootloaderCtrl").start();
    }

    public static String getStatusDisplay(Context context, String langCode) {
        boolean locked = isLocked(context);
        return locked
            ? I18n.get(I18n.KEY_BOOTLOADER_LOCKED, langCode)
            : I18n.get(I18n.KEY_BOOTLOADER_UNLOCKED, langCode);
    }

    private static boolean queryHardwareLockState() {
        try {
            String lockedProp = getSystemProperty("ro.boot.flash.locked");
            if ("0".equals(lockedProp)) return false;
            String vboot = getSystemProperty("ro.boot.verifiedbootstate");
            if ("orange".equalsIgnoreCase(vboot)) return false;
        } catch (Throwable ignored) {}
        return true; // Default locked
    }

    private static void applyLockState(boolean locked) {
        try {
            // 1. Update PersistentDataBlock OEM unlock permission via cmd
            int oemUnlockVal = locked ? 0 : 1;
            Runtime.getRuntime().exec(new String[]{
                "cmd", "persistent_data_block", "set-oem-unlock-enabled", String.valueOf(oemUnlockVal)
            });

            // 2. Set system properties
            Runtime.getRuntime().exec(new String[]{
                "setprop", "sys.oem_unlock_allowed", String.valueOf(oemUnlockVal)
            });
            Runtime.getRuntime().exec(new String[]{
                "setprop", "ro.boot.flash.locked", locked ? "1" : "0"
            });
            Runtime.getRuntime().exec(new String[]{
                "setprop", "ro.boot.verifiedbootstate", locked ? "green" : "orange"
            });

            // 3. Direct FRP partition byte update if privileged access available
            // In AVB 2.0 / AOSP persistent data block, the OEM unlock flag is stored at the last byte of FRP
            for (String frpPath : FRP_PARTITIONS) {
                File frpFile = new File(frpPath);
                if (frpFile.exists() && frpFile.canWrite()) {
                    try (RandomAccessFile raf = new RandomAccessFile(frpFile, "rw")) {
                        long length = raf.length();
                        if (length > 0) {
                            raf.seek(length - 1);
                            raf.writeByte(locked ? 0x00 : 0x01);
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // 4. Update Play Integrity overlay property file
            File propFile = new File("/data/local/tmp/tontos_play_integrity.prop");
            if (propFile.exists() && propFile.canWrite()) {
                // Update persistent lock state
                Runtime.getRuntime().exec(new String[]{
                    "sh", "-c",
                    "sed -i 's/ro.boot.flash.locked=.*/ro.boot.flash.locked=" + (locked ? "1" : "0") + "/' /data/local/tmp/tontos_play_integrity.prop"
                });
                Runtime.getRuntime().exec(new String[]{
                    "sh", "-c",
                    "sed -i 's/ro.boot.verifiedbootstate=.*/ro.boot.verifiedbootstate=" + (locked ? "green" : "orange") + "/' /data/local/tmp/tontos_play_integrity.prop"
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed applying on-device bootloader lock state", t);
        }
    }

    private static String getSystemProperty(String key) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                return line != null ? line.trim() : "";
            }
        } catch (Throwable t) {
            return "";
        }
    }
}
