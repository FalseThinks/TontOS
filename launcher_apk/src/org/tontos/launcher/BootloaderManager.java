package org.tontos.launcher;

/**
 * Read-only view of the bootloader state reported by Android.
 * The launcher cannot change the bootloader or attest Verified Boot.
 */
public final class BootloaderManager {

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
        return SafeCommandExecutor.executeWithOutput("getprop", LOCKED_PROPERTY).trim();
    }
}
