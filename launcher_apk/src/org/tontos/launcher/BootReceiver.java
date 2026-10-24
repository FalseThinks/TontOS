package org.tontos.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Boot Completed BroadcastReceiver for TontOS.
 * Ensures the customized Lock Screen receiver, System-Wide Top Edge Gesture Service,
 * and system provisioning/hardening start immediately on ROM boot.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "TontOSBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        Log.i(TAG, "Device booted with action: " + (intent != null ? intent.getAction() : "null"));

        // 1. Ensure global lock screen receiver is active immediately on boot
        LockScreenManager.registerScreenReceiver(context);

        // 2. Start system-wide shade service
        try {
            Intent serviceIntent = new Intent(context, TontOSShadeService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start TontOSShadeService: " + t.getMessage());
        }

        // 3. Run initial provisioning and debloating
        SystemProvisioningManager.executeInitialProvisioning(context);
    }
}
