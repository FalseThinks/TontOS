package org.tontos.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facade managing the lifecycle of the TontOS Isolated Sandbox Profile (User 10+).
 * Enforces strict Linux kernel DAC filesystem isolation, Zero-RAM process pausing,
 * and GrapheneOS-style AppOps telemetry containment for Google-dependent applications.
 */
public final class SandboxManager {

    private static final String TAG = "SandboxManager";
    public static final String DEFAULT_SANDBOX_NAME = "TontOS Sandbox";
    public static final String PREF_SANDBOX_USER_ID = "pref_sandbox_user_id";

    private static final Pattern USER_ID_PATTERN = Pattern.compile("UserInfo\\{([0-9]+):([^:]+):([0-9a-f]+)\\}");
    private static final Pattern CREATE_USER_ID_PATTERN = Pattern.compile("(?:id\\s+|UserInfo\\{)([0-9]+)");

    public enum ProfileState {
        NOT_CREATED,
        PAUSED,
        RUNNING
    }

    private SandboxManager() {}

    /**
     * Inspects active Android user profiles and returns the current Sandbox state.
     */
    public static ProfileState getSandboxState(Context context) {
        int userId = getSandboxUserId(context);
        if (userId < 10) {
            return ProfileState.NOT_CREATED;
        }

        if (isUserRunning(userId)) {
            return ProfileState.RUNNING;
        } else {
            return ProfileState.PAUSED;
        }
    }

    /**
     * Resolves the User ID of the isolated sandbox profile.
     * Returns -1 if no sandbox profile exists.
     */
    public static int getSandboxUserId(Context context) {
        if (context == null) return -1;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        int cachedId = prefs.getInt(PREF_SANDBOX_USER_ID, -1);

        String userListOutput = SafeCommandExecutor.executeWithOutput("pm", "list", "users");
        if (userListOutput != null && !userListOutput.isEmpty()) {
            Matcher m = USER_ID_PATTERN.matcher(userListOutput);
            while (m.find()) {
                try {
                    int uid = Integer.parseInt(m.group(1));
                    String name = m.group(2);
                    if (uid >= 10 && (name.contains("Sandbox") || uid == cachedId)) {
                        prefs.edit().putInt(PREF_SANDBOX_USER_ID, uid).apply();
                        return uid;
                    }
                } catch (Throwable ignored) {}
            }
        }

        return -1;
    }

    /**
     * Creates a new unprivileged secondary user profile isolated by Linux DAC.
     */
    public static boolean createSandboxProfile(Context context, String profileName) {
        if (context == null) return false;
        String name = (profileName != null && !profileName.isEmpty()) ? profileName : DEFAULT_SANDBOX_NAME;

        if (!SafeCommandExecutor.isSafeArgument(name)) {
            name = "TontOS_Sandbox";
        }

        String createOutput = SafeCommandExecutor.executeWithOutput("pm", "create-user", name);
        int createdId = parseCreatedUserId(createOutput);

        if (createdId < 10) {
            createOutput = SafeCommandExecutor.executeWithOutput("cmd", "user", "create-user", name);
            createdId = parseCreatedUserId(createOutput);
        }

        if (createdId >= 10) {
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putInt(PREF_SANDBOX_USER_ID, createdId).apply();
            Log.i(TAG, "Successfully created isolated sandbox profile User ID: " + createdId);

            // Apply telemetry restrictions immediately
            applyTelemetryIsolation(context, createdId);
            return true;
        }

        Log.w(TAG, "Failed to create sandbox profile. Output: " + createOutput);
        return false;
    }

    /**
     * Starts and unlocks the isolated user profile.
     */
    public static boolean startSandboxProfile(Context context, int userId) {
        if (userId < 10) return false;
        boolean ok = SafeCommandExecutor.executePrivileged("am", "start-user", String.valueOf(userId));
        if (ok) {
            Log.i(TAG, "Started sandbox profile User " + userId);
        }
        return ok;
    }

    /**
     * Pauses the isolated sandbox profile via force-stop, terminating all background
     * processes, sockets, and memory allocations (Zero-RAM mode).
     */
    public static boolean pauseSandboxProfile(Context context, int userId) {
        if (userId < 10) return false;
        // am stop-user -f terminates all processes belonging to the user
        boolean ok = SafeCommandExecutor.executePrivileged("am", "stop-user", "-f", String.valueOf(userId));
        if (ok) {
            Log.i(TAG, "Halted sandbox profile User " + userId + " (Zero-RAM freeze active)");
        }
        return ok;
    }

    /**
     * Completely removes and purges the isolated sandbox profile and its storage.
     */
    public static boolean destroySandboxProfile(Context context, int userId) {
        if (userId < 10) return false;
        boolean ok = SafeCommandExecutor.executePrivileged("pm", "remove-user", String.valueOf(userId));
        if (!ok) {
            ok = SafeCommandExecutor.executePrivileged("cmd", "user", "remove-user", String.valueOf(userId));
        }

        if (ok) {
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove(PREF_SANDBOX_USER_ID).apply();
            Log.i(TAG, "Destroyed sandbox profile User " + userId);
        }
        return ok;
    }

    /**
     * Enforces GrapheneOS-style AppOps telemetry denial on Google Mobile Services
     * inside the isolated profile.
     */
    public static void applyTelemetryIsolation(Context context, int userId) {
        if (userId < 10) return;
        String uidStr = String.valueOf(userId);

        String[] targetPkgs = {
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending"
        };

        String[] deniedOps = {
            "BODY_SENSORS",
            "HIGH_SAMPLING_RATE_SENSORS",
            "ACTIVITY_RECOGNITION",
            "ACCESS_BACKGROUND_LOCATION",
            "READ_CONTACTS",
            "READ_CALL_LOG"
        };

        for (String pkg : targetPkgs) {
            for (String op : deniedOps) {
                SafeCommandExecutor.executePrivileged("cmd", "appops", "set", "--user", uidStr, pkg, op, "deny");
            }
        }
        Log.i(TAG, "Applied strict AppOps telemetry restrictions to User " + userId);
    }

    /**
     * Returns a human-readable summary of the sandbox profile status.
     */
    public static String getSandboxStatusSummary(Context context, String curLangCode) {
        if (context == null) return "";
        ProfileState state = getSandboxState(context);
        int userId = getSandboxUserId(context);
        switch (state) {
            case RUNNING:
                return I18n.get(I18n.KEY_SANDBOX_STATUS_RUNNING, curLangCode) + " [ID: " + userId + "]";
            case PAUSED:
                return I18n.get(I18n.KEY_SANDBOX_STATUS_PAUSED, curLangCode) + " [ID: " + userId + "]";
            case NOT_CREATED:
            default:
                return I18n.get(I18n.KEY_SANDBOX_STATUS_NOT_CREATED, curLangCode);
        }
    }

    /**
     * Displays AMOLED modal dialog managing the full lifecycle of the Sandbox Profile:
     * Creation, Start/Unlock, Pause (Zero-RAM mode), Telemetry Shield, and Safe Purge.
     */
    public static void showSandboxDialog(
        final Activity activity,
        final ThemeTokens tokens,
        final String curLangCode,
        final Runnable onStateChanged
    ) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(tokens.bg));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
                    window.getInsetsController().setSystemBarsAppearance(
                        tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } catch (Throwable ignored) {}
            GrayscaleManager.applyGrayscaleToWindow(window, GrayscaleManager.isGrayscaleEnabled(activity));
        }

        final float density = activity.getResources().getDisplayMetrics().density;
        final int pad = (int) (20 * density + 0.5f);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(pad, pad, pad, pad);

        // Header Title
        TextView header = new TextView(activity);
        header.setText(I18n.get(I18n.KEY_SANDBOX_TITLE, curLangCode).toUpperCase(Locale.ROOT));
        header.setTextColor(tokens.accent);
        header.setTextSize(17);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 0, 0, (int) (6 * density));
        root.addView(header);

        // Divider
        View divider = new View(activity);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (16 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        // Scroll Container
        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);

        final ProfileState state = getSandboxState(activity);
        final int userId = getSandboxUserId(activity);

        // Status Card
        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(tokens.surface);
        statusBg.setStroke((int) (1 * density), tokens.border);
        statusBg.setCornerRadius((int) (4 * density));
        statusCard.setBackground(statusBg);

        TextView statusLabel = new TextView(activity);
        statusLabel.setText("=== PROFILE STATUS ===");
        statusLabel.setTextColor(tokens.textMuted);
        statusLabel.setTextSize(11);
        statusLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        statusCard.addView(statusLabel);

        TextView statusValue = new TextView(activity);
        int statusColor;
        String statusText;
        if (state == ProfileState.RUNNING) {
            statusColor = Color.parseColor("#44DD88");
            statusText = "[ " + I18n.get(I18n.KEY_SANDBOX_STATUS_RUNNING, curLangCode) + " · USER " + userId + " ]";
        } else if (state == ProfileState.PAUSED) {
            statusColor = Color.parseColor("#FFB000");
            statusText = "[ " + I18n.get(I18n.KEY_SANDBOX_STATUS_PAUSED, curLangCode) + " · USER " + userId + " ]";
        } else {
            statusColor = tokens.textMuted;
            statusText = "[ " + I18n.get(I18n.KEY_SANDBOX_STATUS_NOT_CREATED, curLangCode) + " ]";
        }
        statusValue.setText(statusText);
        statusValue.setTextColor(statusColor);
        statusValue.setTextSize(14);
        statusValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        statusValue.setPadding(0, (int) (4 * density), 0, 0);
        statusCard.addView(statusValue);

        container.addView(statusCard);

        // Architecture Explanation
        TextView expHeader = new TextView(activity);
        expHeader.setText("=== ISOLATION ARCHITECTURE ===");
        expHeader.setTextColor(tokens.textMuted);
        expHeader.setTextSize(11);
        expHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        expHeader.setPadding(0, (int) (18 * density), 0, (int) (6 * density));
        container.addView(expHeader);

        TextView expText = new TextView(activity);
        expText.setText(I18n.get(I18n.KEY_SANDBOX_EXPLANATION, curLangCode));
        expText.setTextColor(tokens.textPrimary);
        expText.setTextSize(13);
        expText.setTypeface(Typeface.MONOSPACE);
        expText.setLineSpacing(0, 1.25f);
        container.addView(expText);

        // Action Buttons Container
        LinearLayout actionsLayout = new LinearLayout(activity);
        actionsLayout.setOrientation(LinearLayout.VERTICAL);
        actionsLayout.setPadding(0, (int) (20 * density), 0, (int) (10 * density));

        if (state == ProfileState.NOT_CREATED) {
            TextView createBtn = createActionButton(
                activity,
                "[ " + I18n.get(I18n.KEY_SANDBOX_CREATE_BTN, curLangCode) + " ]",
                tokens.accent,
                tokens.accent,
                tokens.controlBgNormal,
                tokens.controlBgPressed,
                density,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean ok = createSandboxProfile(activity, DEFAULT_SANDBOX_NAME);
                        if (ok) {
                            Toast.makeText(activity, "Sandbox profile initialized", Toast.LENGTH_SHORT).show();
                            if (onStateChanged != null) onStateChanged.run();
                            dialog.dismiss();
                            showSandboxDialog(activity, tokens, curLangCode, onStateChanged);
                        } else {
                            Toast.makeText(activity, "Failed to create sandbox profile", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            );
            actionsLayout.addView(createBtn);
        } else if (state == ProfileState.PAUSED) {
            // Start Profile Button
            TextView startBtn = createActionButton(
                activity,
                "[ " + I18n.get(I18n.KEY_SANDBOX_START_BTN, curLangCode) + " ]",
                tokens.accent,
                tokens.accent,
                tokens.controlBgNormal,
                tokens.controlBgPressed,
                density,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startSandboxProfile(activity, userId);
                        if (onStateChanged != null) onStateChanged.run();
                        dialog.dismiss();
                        showSandboxDialog(activity, tokens, curLangCode, onStateChanged);
                    }
                }
            );
            actionsLayout.addView(startBtn);

            // Shield Button
            TextView shieldBtn = createActionButton(
                activity,
                "[ " + I18n.get(I18n.KEY_SANDBOX_SHIELD_BTN, curLangCode) + " ]",
                tokens.textPrimary,
                tokens.border,
                tokens.controlBgNormal,
                tokens.controlBgPressed,
                density,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyTelemetryIsolation(activity, userId);
                        Toast.makeText(activity, I18n.get(I18n.KEY_SANDBOX_SHIELD_SUCCESS, curLangCode), Toast.LENGTH_SHORT).show();
                    }
                }
            );
            LinearLayout.LayoutParams shieldLp = (LinearLayout.LayoutParams) shieldBtn.getLayoutParams();
            shieldLp.setMargins(0, (int) (10 * density), 0, 0);
            actionsLayout.addView(shieldBtn);

            // Destroy Button
            TextView destroyBtn = createActionButton(
                activity,
                "[ " + I18n.get(I18n.KEY_SANDBOX_DESTROY_BTN, curLangCode) + " ]",
                Color.parseColor("#FF5555"),
                Color.parseColor("#882222"),
                tokens.controlBgNormal,
                tokens.controlBgPressed,
                density,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(activity)
                            .setTitle(I18n.get(I18n.KEY_SANDBOX_DESTROY_BTN, curLangCode))
                            .setMessage(I18n.get(I18n.KEY_SANDBOX_CONFIRM_DESTROY, curLangCode))
                            .setPositiveButton(I18n.get(I18n.KEY_CONFIRM, curLangCode), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    destroySandboxProfile(activity, userId);
                                    if (onStateChanged != null) onStateChanged.run();
                                    dialog.dismiss();
                                    showSandboxDialog(activity, tokens, curLangCode, onStateChanged);
                                }
                            })
                            .setNegativeButton(I18n.get(I18n.KEY_CANCEL, curLangCode), null)
                            .show();
                    }
                }
            );
            LinearLayout.LayoutParams destroyLp = (LinearLayout.LayoutParams) destroyBtn.getLayoutParams();
            destroyLp.setMargins(0, (int) (10 * density), 0, 0);
            actionsLayout.addView(destroyBtn);
        } else { // RUNNING
            // Pause Profile Button (Zero-RAM mode)
            TextView pauseBtn = createActionButton(
                activity,
                "[ " + I18n.get(I18n.KEY_SANDBOX_PAUSE_BTN, curLangCode) + " ]",
                Color.parseColor("#FFB000"),
                Color.parseColor("#FFB000"),
                tokens.controlBgNormal,
                tokens.controlBgPressed,
                density,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pauseSandboxProfile(activity, userId);
                        if (onStateChanged != null) onStateChanged.run();
                        dialog.dismiss();
                        showSandboxDialog(activity, tokens, curLangCode, onStateChanged);
                    }
                }
            );
            actionsLayout.addView(pauseBtn);

            // Shield Button
            TextView shieldBtn = createActionButton(
                activity,
                "[ " + I18n.get(I18n.KEY_SANDBOX_SHIELD_BTN, curLangCode) + " ]",
                tokens.textPrimary,
                tokens.border,
                tokens.controlBgNormal,
                tokens.controlBgPressed,
                density,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyTelemetryIsolation(activity, userId);
                        Toast.makeText(activity, I18n.get(I18n.KEY_SANDBOX_SHIELD_SUCCESS, curLangCode), Toast.LENGTH_SHORT).show();
                    }
                }
            );
            LinearLayout.LayoutParams shieldLp = (LinearLayout.LayoutParams) shieldBtn.getLayoutParams();
            shieldLp.setMargins(0, (int) (10 * density), 0, 0);
            actionsLayout.addView(shieldBtn);

            // Destroy Button
            TextView destroyBtn = createActionButton(
                activity,
                "[ " + I18n.get(I18n.KEY_SANDBOX_DESTROY_BTN, curLangCode) + " ]",
                Color.parseColor("#FF5555"),
                Color.parseColor("#882222"),
                tokens.controlBgNormal,
                tokens.controlBgPressed,
                density,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(activity)
                            .setTitle(I18n.get(I18n.KEY_SANDBOX_DESTROY_BTN, curLangCode))
                            .setMessage(I18n.get(I18n.KEY_SANDBOX_CONFIRM_DESTROY, curLangCode))
                            .setPositiveButton(I18n.get(I18n.KEY_CONFIRM, curLangCode), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    destroySandboxProfile(activity, userId);
                                    if (onStateChanged != null) onStateChanged.run();
                                    dialog.dismiss();
                                    showSandboxDialog(activity, tokens, curLangCode, onStateChanged);
                                }
                            })
                            .setNegativeButton(I18n.get(I18n.KEY_CANCEL, curLangCode), null)
                            .show();
                    }
                }
            );
            LinearLayout.LayoutParams destroyLp = (LinearLayout.LayoutParams) destroyBtn.getLayoutParams();
            destroyLp.setMargins(0, (int) (10 * density), 0, 0);
            actionsLayout.addView(destroyBtn);
        }

        container.addView(actionsLayout);
        scroll.addView(container);
        root.addView(scroll);

        // Bottom Dismiss Button
        TextView backBtn = createActionButton(
            activity,
            "[ <-- " + I18n.get(I18n.KEY_BACK, curLangCode).toUpperCase(Locale.ROOT) + " ]",
            tokens.navAccent,
            tokens.navAccent,
            Color.TRANSPARENT,
            tokens.controlBgPressed,
            density,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            }
        );
        LinearLayout.LayoutParams backLp = (LinearLayout.LayoutParams) backBtn.getLayoutParams();
        backLp.setMargins(0, (int) (12 * density), 0, 0);
        root.addView(backBtn);

        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.show();
    }

    private static TextView createActionButton(
        Context ctx,
        String text,
        int textColor,
        int strokeColor,
        int bgColor,
        int pressedColor,
        float density,
        View.OnClickListener onClick
    ) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(13);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight((int) (44 * density + 0.5f));
        btn.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        btn.setClickable(true);
        btn.setFocusable(true);

        StateListDrawable sld = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(pressedColor);
        pressed.setStroke((int) (2 * density), strokeColor);
        pressed.setCornerRadius((int) (4 * density));

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(bgColor);
        normal.setStroke((int) (1 * density), strokeColor);
        normal.setCornerRadius((int) (4 * density));

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);

        btn.setBackground(sld);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        btn.setOnClickListener(onClick);
        return btn;
    }

    public static int parseCreatedUserId(String output) {
        if (output == null || output.isEmpty()) return -1;
        Matcher m = CREATE_USER_ID_PATTERN.matcher(output);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    private static boolean isUserRunning(int userId) {
        String output = SafeCommandExecutor.executeWithOutput("am", "get-started-user-state", String.valueOf(userId));
        return output != null && output.contains("RUNNING");
    }
}
