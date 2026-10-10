package org.tontos.launcher;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Read-only view of the bootloader state reported by Android.
 * The launcher cannot change the bootloader or attest Verified Boot.
 */
public final class BootloaderManager {

    private static final String TAG = "BootloaderManager";
    private static final String LOCKED_PROPERTY = "ro.boot.flash.locked";

    private BootloaderManager() {}

    /**
     * Reports the Android boot property without implying Verified Boot attestation.
     */
    public static String getStatusDisplay(String langCode) {
        String state = readLockedProperty();
        if ("1".equals(state)) {
            return I18n.get(I18n.KEY_BOOTLOADER_LOCKED, langCode);
        }
        if ("0".equals(state)) {
            return I18n.get(I18n.KEY_BOOTLOADER_UNLOCKED, langCode);
        }
        return I18n.get(I18n.KEY_BOOTLOADER_UNKNOWN, langCode);
    }

    private static String readLockedProperty() {
        Process process;
        try {
            process = Runtime.getRuntime().exec(new String[]{"getprop", LOCKED_PROPERTY});
        } catch (IOException error) {
            Log.w(TAG, "Unable to read Android bootloader status", error);
            return "";
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String value = reader.readLine();
            if (process.waitFor() != 0) {
                Log.w(TAG, "Android did not return bootloader status");
                return "";
            }
            return value != null ? value.trim() : "";
        } catch (IOException error) {
            Log.w(TAG, "Unable to read Android bootloader status", error);
            return "";
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while reading Android bootloader status", error);
            return "";
        }
    }
}
