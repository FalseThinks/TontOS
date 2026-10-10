package org.tontos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Hardware Controller & Sensor Fusion Bridge for Nothing OS Glyph Interface.
 * Controls Awinic AW210xx 12-bit PWM multi-zone LED strips via sysfs:
 * - Flip to Glyph (face-down silence via Accelerometer + Proximity sensor fusion)
 * - Essential Glyph notification latches (top-right diagonal strip B)
 * - Battery / Detox charge progression indicators
 */
public final class NothingGlyphManager {

    private static final String TAG = "NothingGlyphMgr";

    public static final String PREF_GLYPH_MASTER_ENABLE = "pref_glyph_master_enable";
    public static final String PREF_GLYPH_FLIP_TO_GLYPH = "pref_glyph_flip_to_glyph";
    public static final String PREF_GLYPH_ESSENTIAL = "pref_glyph_essential";

    // Awinic aw210xx sysfs root and attributes
    private static final String GLYPH_SYSFS_DIR = "/sys/class/leds/aw210xx_led";
    private static final String ATTR_ALL_WHITE_LEDS = GLYPH_SYSFS_DIR + "/all_white_leds_br";
    private static final String ATTR_FRONT_CAM_LED = GLYPH_SYSFS_DIR + "/front_cam_led_br";
    private static final String ATTR_ROUND_LEDS = GLYPH_SYSFS_DIR + "/round_leds_br";
    private static final String ATTR_VLINE_LEDS = GLYPH_SYSFS_DIR + "/vline_leds_br";
    private static final String ATTR_DOT_LED = GLYPH_SYSFS_DIR + "/dot_led_br";

    private static boolean isFlipActive = false;
    private static int preFlipRingerMode = AudioManager.RINGER_MODE_NORMAL;
    private static SensorManager sensorManager;
    private static SensorEventListener flipListener;

    private NothingGlyphManager() {}

    /**
     * Determines whether Nothing Glyph hardware is present on this device.
     */
    public static boolean isGlyphHardwarePresent() {
        return new File(GLYPH_SYSFS_DIR).exists()
            || "Nothing".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public static boolean isMasterEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_GLYPH_MASTER_ENABLE, isGlyphHardwarePresent());
    }

    public static void setMasterEnabled(Context context, boolean enable) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_GLYPH_MASTER_ENABLE, enable).apply();
        if (!enable) {
            turnOffAll();
            stopFlipToGlyph();
        } else if (isFlipToGlyphEnabled(context)) {
            startFlipToGlyph(context);
        }
    }

    public static boolean isFlipToGlyphEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_GLYPH_FLIP_TO_GLYPH, false);
    }

    public static void setFlipToGlyphEnabled(Context context, boolean enable) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_GLYPH_FLIP_TO_GLYPH, enable).apply();
        if (enable && isMasterEnabled(context)) {
            startFlipToGlyph(context);
        } else {
            stopFlipToGlyph();
        }
    }

    public static boolean isEssentialGlyphEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_GLYPH_ESSENTIAL, false);
    }

    public static void setEssentialGlyphEnabled(Context context, boolean enable) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_GLYPH_ESSENTIAL, enable).apply();
        if (!enable) {
            writeSysfs(ATTR_FRONT_CAM_LED, 0);
        }
    }

    /**
     * Latches the diagonal Essential Glyph indicator at 50% PWM (2048) until dismissed.
     */
    public static void setEssentialGlyphActive(boolean active) {
        writeSysfs(ATTR_FRONT_CAM_LED, active ? 2048 : 0);
    }

    /**
     * Pulses a Glyph segment with a specific brightness and auto-extinguishes.
     */
    public static void pulseGlyph(final String attr, final int brightness, int durationMs) {
        writeSysfs(attr, brightness);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                writeSysfs(attr, 0);
            }
        }, Math.max(50, durationMs));
    }

    public static void turnOffAll() {
        writeSysfs(ATTR_ALL_WHITE_LEDS, 0);
    }

    /**
     * Initializes low-power sensor fusion for Flip to Glyph (flat face-down accelerometer + proximity).
     */
    public static synchronized void startFlipToGlyph(final Context context) {
        if (context == null || flipListener != null) return;
        final Context appContext = context.getApplicationContext();
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) return;

        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (accel == null) return;

        final float[] gravity = new float[3];
        final boolean[] isNear = new boolean[]{false};
        final AudioManager audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);

        flipListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                    isNear[0] = (event.values[0] < (event.sensor.getMaximumRange() > 0 ? event.sensor.getMaximumRange() : 1.0f));
                } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    gravity[0] = event.values[0];
                    gravity[1] = event.values[1];
                    gravity[2] = event.values[2];
                }

                // Face-down condition: Z acceleration near -9.8 m/s^2 (screen pointing downward), tilt under 15 degrees
                boolean isFaceDown = (gravity[2] < -8.5f && Math.abs(gravity[0]) < 2.5f && Math.abs(gravity[1]) < 2.5f);
                boolean flatAndNear = isFaceDown && isNear[0];

                if (flatAndNear && !isFlipActive) {
                    isFlipActive = true;
                    if (audioManager != null) {
                        preFlipRingerMode = audioManager.getRingerMode();
                        try {
                            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                        } catch (Throwable ignored) {}
                    }
                    // Double blink confirmation pulse
                    pulseGlyph(ATTR_ROUND_LEDS, 3000, 100);
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            pulseGlyph(ATTR_ROUND_LEDS, 3000, 100);
                        }
                    }, 220);
                } else if (!isFaceDown && isFlipActive) {
                    isFlipActive = false;
                    if (audioManager != null) {
                        try {
                            audioManager.setRingerMode(preFlipRingerMode);
                        } catch (Throwable ignored) {}
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(flipListener, accel, SensorManager.SENSOR_DELAY_NORMAL);
        if (prox != null) {
            sensorManager.registerListener(flipListener, prox, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public static synchronized void stopFlipToGlyph() {
        if (sensorManager != null && flipListener != null) {
            sensorManager.unregisterListener(flipListener);
            flipListener = null;
        }
        isFlipActive = false;
    }

    private static final int GLYPH_PWM_MAX = 4095; // AW210xx 12-bit

    private static boolean isAllowedGlyphPath(String path) {
        if (path == null) return false;
        return path.equals(ATTR_ALL_WHITE_LEDS)
            || path.equals(ATTR_FRONT_CAM_LED)
            || path.equals(ATTR_ROUND_LEDS)
            || path.equals(ATTR_VLINE_LEDS)
            || path.equals(ATTR_DOT_LED);
    }

    private static boolean writeSysfs(String path, int value) {
        if (!isAllowedGlyphPath(path)) return false;
        if (value < 0 || value > GLYPH_PWM_MAX) return false;
        File file = new File(path);
        if (!file.exists()) return false;
        String valStr = String.valueOf(value);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(valStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.flush();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
