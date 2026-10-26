package org.tontos.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * System-Wide Top Edge Gesture & Core Service for TontOS.
 * Intercepts swipe-down gestures at the top of the screen across the entire OS
 * to present the customized TontOS UltraShadeDialog rather than the stock AOSP shade.
 * Also ensures LockScreenManager screen-off listeners stay persistently active.
 */
public class TontOSShadeService extends Service {

    private static final String TAG = "TontOSShadeService";
    private static final String CHANNEL_ID = "tontos_core_service";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View overlayView;
    private float startY = 0f;
    private float startX = 0f;
    private boolean isTracking = false;

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Elevate to foreground service to prevent cgroup freezing
        startAsForegroundService();

        // 2. Ensure global lock screen receiver is persistently running
        LockScreenManager.registerScreenReceiver(this);

        // 3. Setup top edge swipe overlay
        try {
            setupTopEdgeOverlay();
        } catch (Throwable t) {
            Log.w(TAG, "Could not attach overlay view: " + t.getMessage());
        }
    }

    private void startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "TontOS System Core",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("System services for TontOS Lock Screen and Custom Shade");
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }

            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("TontOS")
                .setContentText("Minimalist System Active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
            } catch (Throwable t) {
                try {
                    startForeground(NOTIFICATION_ID, notification);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void setupTopEdgeOverlay() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        overlayView = new View(this);
        overlayView.setClickable(true);
        overlayView.setFocusable(false);
        overlayView.setBackgroundColor(Color.TRANSPARENT);

        final int overlayHeight = dpToPx(56);
        final int triggerThreshold = dpToPx(16);

        int layoutType;
        if (checkCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE") == android.content.pm.PackageManager.PERMISSION_GRANTED
                || checkCallingOrSelfPermission("android.permission.INTERNAL_SYSTEM_WINDOW") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            layoutType = 2024; // TYPE_NAVIGATION_BAR_PANEL - higher Z-order than stock StatusBar
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (SetupWizardActivity.isWizardActive()) {
                    return false;
                }
                android.content.SharedPreferences prefs = MainActivity.getSafeSharedPreferences(TontOSShadeService.this, MainActivity.PREFS_NAME);
                if (prefs == null || !prefs.getBoolean(MainActivity.KEY_SETUP_COMPLETED, false)) {
                    return false;
                }
                Log.i(TAG, "overlay touch: action=" + event.getActionMasked() + " Y=" + event.getRawY());
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        isTracking = true;
                        return true; // Crucial: must consume DOWN to receive subsequent MOVE/UP events

                    case MotionEvent.ACTION_MOVE:
                        if (isTracking) {
                            float deltaY = event.getRawY() - startY;
                            float deltaX = Math.abs(event.getRawX() - startX);
                            if (deltaY > triggerThreshold && deltaY > deltaX) {
                                isTracking = false;
                                triggerCustomShade();
                                return true;
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isTracking = false;
                        break;
                }
                return true;
            }
        });

        windowManager.addView(overlayView, params);
    }

    private void triggerCustomShade() {
        if (SetupWizardActivity.isWizardActive()) {
            return;
        }
        android.content.SharedPreferences prefs = MainActivity.getSafeSharedPreferences(this, MainActivity.PREFS_NAME);
        boolean setupCompleted = prefs != null && prefs.getBoolean(MainActivity.KEY_SETUP_COMPLETED, false);
        if (!setupCompleted) {
            return;
        }

        try {
            SafeCommandExecutor.executePrivileged("cmd", "statusbar", "collapse-instant");
        } catch (Throwable ignored) {}

        Intent intent = new Intent(this, UltraShadeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_NO_ANIMATION
            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForegroundService();
        LockScreenManager.registerScreenReceiver(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Throwable ignored) {}
            overlayView = null;
        }
    }
}
