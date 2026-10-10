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
        String lang = langCode == null ? "en" : langCode.toLowerCase();
        if (lang.startsWith("de")) return "100% · Steuerung n. v.";
        if (lang.startsWith("pl")) return "100% · sterowanie n/d";
        if (lang.startsWith("es")) return "100% · control no disp.";
        return "100% · controls N/A";
    }

    public static String getOptionDisplay(int mode, String langCode) {
        if (mode == MODE_100) return getModeDisplay(mode, langCode);
        String lang = langCode == null ? "en" : langCode.toLowerCase();
        if (mode == MODE_80) {
            if (lang.startsWith("de")) return "80%-Limit — nicht verfügbar";
            if (lang.startsWith("pl")) return "Limit 80% — niedostępny";
            if (lang.startsWith("es")) return "Límite 80% — no disponible";
            return "80% limit — unavailable";
        }
        if (mode == MODE_BYPASS) {
            if (lang.startsWith("de")) return "Bypass — nicht verfügbar";
            if (lang.startsWith("pl")) return "Bypass — niedostępny";
            if (lang.startsWith("es")) return "Bypass — no disponible";
            return "Bypass — unavailable";
        }
        return getModeDisplay(mode, langCode);
    }

    public static String getModeResultDisplay(ModeResult result, String langCode) {
        if (result == ModeResult.UNSUPPORTED) {
            String lang = langCode == null ? "en" : langCode.toLowerCase();
            if (lang.startsWith("de")) return "Ladelimits und Bypass-Steuerung sind dieser App auf diesem Gerät nicht verfügbar.";
            if (lang.startsWith("pl")) return "Limity ładowania i sterowanie bypass są niedostępne dla tej aplikacji na tym urządzeniu.";
            if (lang.startsWith("es")) return "Los límites de carga y el bypass no están disponibles para esta aplicación en este dispositivo.";
            return "Charge limits and bypass control are unavailable to this app on this device.";
        }
        if (result == ModeResult.APPLIED) {
            String lang = langCode == null ? "en" : langCode.toLowerCase();
            if (lang.startsWith("de")) return "Standardladen ausgewählt; es wird kein Ladelimit angewendet.";
            if (lang.startsWith("pl")) return "Wybrano standardowe ładowanie; limit ładowania nie jest stosowany.";
            if (lang.startsWith("es")) return "Carga estándar seleccionada; no se aplica ningún límite.";
            return "Standard charging selected; no charge limit is applied.";
        }
        String lang = langCode == null ? "en" : langCode.toLowerCase();
        if (lang.startsWith("de")) return "Ungültige Ladeoption.";
        if (lang.startsWith("pl")) return "Nieprawidłowa opcja ładowania.";
        if (lang.startsWith("es")) return "Opción de carga no válida.";
        return "Invalid charging option.";
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
