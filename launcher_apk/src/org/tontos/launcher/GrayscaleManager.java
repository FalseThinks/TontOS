package org.tontos.launcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.provider.Settings;
import android.view.View;
import android.view.Window;

/**
 * Grayscale & LiveDisplay Monochromacy Manager for TontOS.
 * Controls AOSP Daltonizer accessibility settings and window-level hardware layer desaturation.
 */
public final class GrayscaleManager {

    public static final String PREF_GRAYSCALE_ENABLED = "pref_grayscale_enabled";
    private static final String SECURE_KEY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled";
    private static final String SECURE_KEY_DALTONIZER = "accessibility_display_daltonizer";

    private GrayscaleManager() {}

    /**
     * Checks if grayscale / monochromacy mode is currently active.
     */
    public static boolean isGrayscaleEnabled(Context context) {
        if (context == null) return false;
        try {
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.contains(PREF_GRAYSCALE_ENABLED)) {
                return prefs.getBoolean(PREF_GRAYSCALE_ENABLED, false);
            }
            ContentResolver cr = context.getContentResolver();
            int en = Settings.Secure.getInt(cr, SECURE_KEY_DALTONIZER_ENABLED, -99);
            int mode = Settings.Secure.getInt(cr, SECURE_KEY_DALTONIZER, -99);
            if (en != -99 && mode != -99) {
                return (en == 1 && mode == 0);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Enables or disables system-wide grayscale monochromacy.
     */
    public static boolean setGrayscaleEnabled(Context context, boolean enable) {
        if (context == null) return false;

        // Persist state in SharedPreferences
        try {
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(PREF_GRAYSCALE_ENABLED, enable).apply();
        } catch (Exception ignored) {}

        int daltonizerEnabled = enable ? 1 : 0;
        int daltonizer = enable ? 0 : -1;

        // Attempt 1: Direct ContentResolver Settings.Secure write
        try {
            ContentResolver cr = context.getContentResolver();
            boolean r1 = Settings.Secure.putInt(cr, SECURE_KEY_DALTONIZER_ENABLED, daltonizerEnabled);
            boolean r2 = Settings.Secure.putInt(cr, SECURE_KEY_DALTONIZER, daltonizer);
            if (r1 && r2) {
                return true;
            }
        } catch (Exception ignored) {}

        // Attempt 2: su / sh execution fallback
        String cmd = "settings put secure " + SECURE_KEY_DALTONIZER_ENABLED + " " + daltonizerEnabled +
                     " && settings put secure " + SECURE_KEY_DALTONIZER + " " + daltonizer;

        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            if (p.waitFor() == 0) return true;
        } catch (Exception ignored) {}

        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            if (p.waitFor() == 0) return true;
        } catch (Exception ignored) {}

        try {
            Process p1 = Runtime.getRuntime().exec(new String[]{"settings", "put", "secure", SECURE_KEY_DALTONIZER_ENABLED, String.valueOf(daltonizerEnabled)});
            Process p2 = Runtime.getRuntime().exec(new String[]{"settings", "put", "secure", SECURE_KEY_DALTONIZER, String.valueOf(daltonizer)});
            p1.waitFor();
            p2.waitFor();
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Applies full-window hardware-accelerated desaturation to an Activity.
     */
    public static void applyGrayscaleToWindow(Activity activity) {
        if (activity == null) return;
        applyGrayscaleToWindow(activity.getWindow(), isGrayscaleEnabled(activity));
    }

    /**
     * Applies full-window hardware-accelerated desaturation to any Window (Activity or Dialog).
     */
    public static void applyGrayscaleToWindow(Window window, boolean isGray) {
        if (window == null) return;
        try {
            View decor = window.getDecorView();
            if (decor == null) return;
            if (isGray) {
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0f);
                ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
                Paint paint = new Paint();
                paint.setColorFilter(filter);
                decor.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
            } else {
                decor.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        } catch (Throwable ignored) {}
    }
}
