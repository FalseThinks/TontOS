package org.tontos.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/**
 * Fullscreen translucent host activity for UltraShadeDialog.
 * Allows launching the customized TontOS drop-down shade from anywhere in the OS,
 * including third-party apps, top edge gestures, and quick settings broadcasts.
 */
public class UltraShadeActivity extends Activity {

    public static final String ACTION_OPEN_SHADE = "org.tontos.launcher.ACTION_OPEN_SHADE";
    private static final String PREFS_NAME = "tontos_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LockScreenManager.isDeviceLocked(this) && LockScreenManager.isLockScreenEnabled(this)) {
            finish();
            return;
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);

        SharedPreferences prefs = MainActivity.getSafeSharedPreferences(this, PREFS_NAME, Context.MODE_PRIVATE);
        String themeId = prefs.getString("selected_theme", "AMOLED");
        ThemeTokens tokens = ThemeTokens.get(themeId);

        // Fetch current battery percentage via sticky broadcast
        int batteryLevel = 100;
        try {
            Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale;
                }
            }
        } catch (Throwable ignored) {}

        UltraShadeDialog.show(this, tokens, batteryLevel);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
