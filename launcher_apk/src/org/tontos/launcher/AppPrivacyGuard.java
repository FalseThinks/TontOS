package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GrapheneOS-style Application Privacy & Control Manager for TontOS.
 * Provides per-app granular enforcement for:
 * 1. Network Permission (revoking internet/socket access per app)
 * 2. Sensors Permission (revoking physical motion/body sensors to block fingerprinting & vibration keylogging)
 * 3. Background Execution Isolation (restricting background wake & services)
 * 4. Force Stop / Process termination
 * 5. Direct access to system App Details & Scopes
 */
public final class AppPrivacyGuard {

    private static final String TAG = "AppPrivacyGuard";
    public static final String PREF_BLOCKED_SENSORS_APPS = "pref_blocked_sensors_apps";
    public static final String PREF_BLOCKED_CAMERA_MIC_APPS = "pref_blocked_camera_mic_apps";
    public static final String PREF_BLOCKED_LOCATION_APPS = "pref_blocked_location_apps";
    public static final String PREF_BLOCKED_STORAGE_APPS = "pref_blocked_storage_apps";
    public static final String PREF_BLOCKED_CONTACTS_APPS = "pref_blocked_contacts_apps";
    public static final String PREF_RESTRICTED_BG_APPS = "pref_restricted_bg_apps";

    public static final String[] SENSORS_OPS = new String[]{
        "BODY_SENSORS", "HIGH_SAMPLING_RATE_SENSORS", "ACTIVITY_RECOGNITION"
    };
    public static final String[] CAMERA_MIC_OPS = new String[]{
        "CAMERA", "RECORD_AUDIO"
    };
    public static final String[] LOCATION_OPS = new String[]{
        "FINE_LOCATION", "COARSE_LOCATION", "MONITOR_LOCATION"
    };
    public static final String[] STORAGE_OPS = new String[]{
        "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO",
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"
    };
    public static final String[] CONTACTS_OPS = new String[]{
        "READ_CONTACTS", "WRITE_CONTACTS", "READ_CALL_LOG"
    };

    private AppPrivacyGuard() {}

    // ==========================================
    // 1. GRANULAR PERMISSION CATEGORIES
    // ==========================================

    public static boolean isCategoryBlocked(Context context, String prefKey, String pkg) {
        if (context == null || pkg == null || prefKey == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> blocked = prefs.getStringSet(prefKey, null);
        return blocked != null && blocked.contains(pkg);
    }

    public static void setCategoryBlocked(Context context, String prefKey, String pkg, boolean blocked, String[] appOps) {
        if (context == null || pkg == null || prefKey == null) return;
        if (!SafeCommandExecutor.isValidPackageName(pkg)) {
            Log.w(TAG, "Refusing operation for invalid package identifier: " + pkg);
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = prefs.getStringSet(prefKey, null);
        Set<String> updated = new HashSet<>(current != null ? current : Collections.<String>emptySet());

        if (blocked) {
            updated.add(pkg);
        } else {
            updated.remove(pkg);
        }
        prefs.edit().putStringSet(prefKey, updated).apply();

        if (appOps != null) {
            String mode = blocked ? "deny" : "allow";
            for (String op : appOps) {
                SafeCommandExecutor.execute("cmd", "appops", "set", pkg, op, mode);
            }
        }
    }

    public static boolean isSensorsBlocked(Context context, String pkg) {
        return isCategoryBlocked(context, PREF_BLOCKED_SENSORS_APPS, pkg);
    }

    public static void setSensorsBlocked(Context context, String pkg, boolean blocked) {
        setCategoryBlocked(context, PREF_BLOCKED_SENSORS_APPS, pkg, blocked, SENSORS_OPS);
    }

    public static boolean isCameraMicBlocked(Context context, String pkg) {
        return isCategoryBlocked(context, PREF_BLOCKED_CAMERA_MIC_APPS, pkg);
    }

    public static void setCameraMicBlocked(Context context, String pkg, boolean blocked) {
        setCategoryBlocked(context, PREF_BLOCKED_CAMERA_MIC_APPS, pkg, blocked, CAMERA_MIC_OPS);
    }

    public static boolean isLocationBlocked(Context context, String pkg) {
        return isCategoryBlocked(context, PREF_BLOCKED_LOCATION_APPS, pkg);
    }

    public static void setLocationBlocked(Context context, String pkg, boolean blocked) {
        setCategoryBlocked(context, PREF_BLOCKED_LOCATION_APPS, pkg, blocked, LOCATION_OPS);
    }

    public static boolean isStorageBlocked(Context context, String pkg) {
        return isCategoryBlocked(context, PREF_BLOCKED_STORAGE_APPS, pkg);
    }

    public static void setStorageBlocked(Context context, String pkg, boolean blocked) {
        setCategoryBlocked(context, PREF_BLOCKED_STORAGE_APPS, pkg, blocked, STORAGE_OPS);
    }

    public static boolean isContactsBlocked(Context context, String pkg) {
        return isCategoryBlocked(context, PREF_BLOCKED_CONTACTS_APPS, pkg);
    }

    public static void setContactsBlocked(Context context, String pkg, boolean blocked) {
        setCategoryBlocked(context, PREF_BLOCKED_CONTACTS_APPS, pkg, blocked, CONTACTS_OPS);
    }

    // ==========================================
    // 2. BACKGROUND RESTRICTION (GRAPHENEOS MODEL)
    // ==========================================

    public static boolean isBgRestricted(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> restricted = prefs.getStringSet(PREF_RESTRICTED_BG_APPS, null);
        return restricted != null && restricted.contains(pkg);
    }

    public static void setBgRestricted(Context context, String pkg, boolean restricted) {
        if (context == null || pkg == null) return;
        if (!SafeCommandExecutor.isValidPackageName(pkg)) {
            Log.w(TAG, "Refusing operation for invalid package identifier: " + pkg);
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = prefs.getStringSet(PREF_RESTRICTED_BG_APPS, null);
        Set<String> updated = new HashSet<>(current != null ? current : Collections.<String>emptySet());

        if (restricted) {
            updated.add(pkg);
        } else {
            updated.remove(pkg);
        }
        prefs.edit().putStringSet(PREF_RESTRICTED_BG_APPS, updated).apply();

        String mode = restricted ? "deny" : "allow";
        SafeCommandExecutor.execute("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", mode);
        SafeCommandExecutor.execute("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", mode);
        if (restricted) {
            SafeCommandExecutor.execute("cmd", "deviceidle", "whitelist", "-" + pkg);
        }
    }

    // ==========================================
    // 3. FORCE STOP & SYSTEM DETAILS
    // ==========================================

    public static void forceStopApp(Context context, String pkg) {
        if (pkg == null || !SafeCommandExecutor.isValidPackageName(pkg)) {
            Log.w(TAG, "Refusing force stop for invalid package: " + pkg);
            return;
        }
        boolean ok = SafeCommandExecutor.execute("am", "force-stop", pkg);
        if (ok) {
            Toast.makeText(context, "Force stopped: " + pkg, Toast.LENGTH_SHORT).show();
        } else {
            Log.w(TAG, "Failed to force stop " + pkg);
            Toast.makeText(context, "Could not force stop app", Toast.LENGTH_SHORT).show();
        }
    }

    public static void openAppDetails(Activity activity, String pkg) {
        if (activity == null || pkg == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable t) {
            Log.w(TAG, "Could not open details for " + pkg, t);
            Toast.makeText(activity, "Could not open system settings", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // 4. INDIVIDUAL APP PRIVACY SHEET (GRAPHENEOS)
    // ==========================================

    public static void showAppPrivacySheet(
        final Activity activity,
        final ThemeTokens tokens,
        final String pkg,
        final String appLabel,
        final String langCode,
        final Runnable onDismissCallback
    ) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dlg = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dlg.getWindow();
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
        final int pad = (int) (24 * density);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(pad, pad, pad, pad);

        // Header Title
        TextView titleView = new TextView(activity);
        titleView.setText("GRAPHENEOS PRIVACY GUARD");
        titleView.setTextColor(tokens.accent);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, (int) (4 * density));
        root.addView(titleView);

        // App Label & Package Subtitle
        TextView appSub = new TextView(activity);
        appSub.setText((appLabel != null ? appLabel : pkg) + " [" + pkg + "]");
        appSub.setTextColor(tokens.textMuted);
        appSub.setTextSize(12);
        appSub.setTypeface(Typeface.MONOSPACE);
        appSub.setPadding(0, 0, 0, (int) (12 * density));
        root.addView(appSub);

        // Tactile Dynamic Line Divider
        View divider = new View(activity);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (16 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll);

        final int blockedColor = tokens.isLight ? Color.parseColor("#CC0000") : Color.parseColor("#FF5555");
        final int allowedColor = tokens.navAccent;
        final String blockedLabel = I18n.get(I18n.KEY_STATUS_BLOCKED, langCode);
        final String allowedLabel = I18n.get(I18n.KEY_STATUS_ALLOWED, langCode);
        final String restrictedLabel = I18n.get(I18n.KEY_STATUS_RESTRICTED, langCode);

        // Section 1 Header: PERMISOS / PERMISSIONS
        TextView permHeader = new TextView(activity);
        permHeader.setText(I18n.get(I18n.KEY_PERMS_HEADER, langCode));
        permHeader.setTextColor(tokens.accent);
        permHeader.setTextSize(14);
        permHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        permHeader.setPadding(0, (int) (4 * density), 0, (int) (8 * density));
        content.addView(permHeader);

        interface PrivacyToggleHandler {
            boolean isBlocked();
            void setBlocked(boolean blocked);
        }
        class PrivacyCategoryItem {
            final String titleKey;
            final String descKey;
            final String offLabel;
            final PrivacyToggleHandler handler;
            PrivacyCategoryItem(String titleKey, String descKey, String offLabel, PrivacyToggleHandler handler) {
                this.titleKey = titleKey;
                this.descKey = descKey;
                this.offLabel = offLabel;
                this.handler = handler;
            }
        }

        PrivacyCategoryItem[] items = new PrivacyCategoryItem[] {
            new PrivacyCategoryItem(I18n.KEY_PERM_NET_TITLE, I18n.KEY_PERM_NET_DESC, blockedLabel, new PrivacyToggleHandler() {
                public boolean isBlocked() { return AppNetworkFirewall.isAppNetworkBlocked(activity, pkg); }
                public void setBlocked(boolean b) { AppNetworkFirewall.setAppNetworkBlocked(activity, pkg, b); }
            }),
            new PrivacyCategoryItem(I18n.KEY_PERM_SENSORS_TITLE, I18n.KEY_PERM_SENSORS_DESC, blockedLabel, new PrivacyToggleHandler() {
                public boolean isBlocked() { return isSensorsBlocked(activity, pkg); }
                public void setBlocked(boolean b) { setSensorsBlocked(activity, pkg, b); }
            }),
            new PrivacyCategoryItem(I18n.KEY_PERM_CAM_MIC_TITLE, I18n.KEY_PERM_CAM_MIC_DESC, blockedLabel, new PrivacyToggleHandler() {
                public boolean isBlocked() { return isCameraMicBlocked(activity, pkg); }
                public void setBlocked(boolean b) { setCameraMicBlocked(activity, pkg, b); }
            }),
            new PrivacyCategoryItem(I18n.KEY_PERM_LOC_TITLE, I18n.KEY_PERM_LOC_DESC, blockedLabel, new PrivacyToggleHandler() {
                public boolean isBlocked() { return isLocationBlocked(activity, pkg); }
                public void setBlocked(boolean b) { setLocationBlocked(activity, pkg, b); }
            }),
            new PrivacyCategoryItem(I18n.KEY_PERM_STORAGE_TITLE, I18n.KEY_PERM_STORAGE_DESC, blockedLabel, new PrivacyToggleHandler() {
                public boolean isBlocked() { return isStorageBlocked(activity, pkg); }
                public void setBlocked(boolean b) { setStorageBlocked(activity, pkg, b); }
            }),
            new PrivacyCategoryItem(I18n.KEY_PERM_CONTACTS_TITLE, I18n.KEY_PERM_CONTACTS_DESC, blockedLabel, new PrivacyToggleHandler() {
                public boolean isBlocked() { return isContactsBlocked(activity, pkg); }
                public void setBlocked(boolean b) { setContactsBlocked(activity, pkg, b); }
            }),
            new PrivacyCategoryItem(I18n.KEY_PERM_BG_TITLE, I18n.KEY_PERM_BG_DESC, restrictedLabel, new PrivacyToggleHandler() {
                public boolean isBlocked() { return isBgRestricted(activity, pkg); }
                public void setBlocked(boolean b) { setBgRestricted(activity, pkg, b); }
            }),
        };

        for (final PrivacyCategoryItem item : items) {
            final boolean initialBlocked = item.handler.isBlocked();
            final TextView toggleBtn = new TextView(activity);
            addPrivacyToggleRow(
                content,
                activity,
                tokens,
                density,
                I18n.get(item.titleKey, langCode),
                I18n.get(item.descKey, langCode),
                initialBlocked,
                toggleBtn,
                blockedColor,
                allowedColor,
                item.offLabel,
                allowedLabel,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean next = !item.handler.isBlocked();
                        item.handler.setBlocked(next);
                        updateToggleBtnState(toggleBtn, next, item.offLabel, allowedLabel, blockedColor, allowedColor);
                    }
                }
            );
        }

        // Dynamic Line Divider
        View actDivider = new View(activity);
        LinearLayout.LayoutParams actDivParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        actDivParams.setMargins(0, (int) (14 * density), 0, (int) (12 * density));
        actDivider.setLayoutParams(actDivParams);
        actDivider.setBackgroundColor(tokens.border);
        content.addView(actDivider);

        // Section 2 Header: ACCIONES / ACTIONS
        TextView actHeader = new TextView(activity);
        actHeader.setText(I18n.get(I18n.KEY_ACTIONS_HEADER, langCode));
        actHeader.setTextColor(tokens.accent);
        actHeader.setTextSize(14);
        actHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        actHeader.setPadding(0, 0, 0, (int) (8 * density));
        content.addView(actHeader);

        // --- ACTION 1: FORCE STOP ---
        addActionButton(content, activity, tokens, density, I18n.get(I18n.KEY_ACTION_FORCE_STOP, langCode), tokens.accent, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forceStopApp(activity, pkg);
            }
        });

        // --- ACTION 2: AJUSTES DE ANDROID ---
        addActionButton(content, activity, tokens, density, I18n.get(I18n.KEY_ACTION_ANDROID_SETTINGS, langCode), tokens.textPrimary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppDetails(activity, pkg);
            }
        });

        // --- ACTION 3: DESINSTALAR APLICACIÓN ---
        addActionButton(content, activity, tokens, density, I18n.get(I18n.KEY_ACTION_UNINSTALL, langCode), blockedColor, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                    uninstallIntent.setData(Uri.parse("package:" + pkg));
                    uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(uninstallIntent);
                    dlg.dismiss();
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to uninstall " + pkg, t);
                    Toast.makeText(activity, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Close Button
        TextView closeBtn = new TextView(activity);
        closeBtn.setText(I18n.get(I18n.KEY_CANCEL, langCode));
        closeBtn.setTextColor(tokens.navAccent);
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setMinimumHeight((int) (48 * density));
        closeBtn.setPadding(0, (int) (12 * density), 0, (int) (12 * density));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        root.addView(closeBtn);

        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (onDismissCallback != null) {
                    onDismissCallback.run();
                }
            }
        });

        dlg.setContentView(root);
        dlg.show();
    }

    private static void addPrivacyToggleRow(
        LinearLayout container,
        Activity activity,
        ThemeTokens tokens,
        float density,
        String title,
        String subtitle,
        boolean isRestricted,
        final TextView toggleBtn,
        int blockedColor,
        int allowedColor,
        String restrictedLabel,
        String allowedLabel,
        View.OnClickListener onClick
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (10 * density), 0, (int) (10 * density));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(activity);
        t.setText(title);
        t.setTextColor(tokens.textPrimary);
        t.setTextSize(14);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        t.setLayoutParams(tLp);
        top.addView(t);

        toggleBtn.setText(isRestricted ? "[ " + restrictedLabel + " ]" : "[ " + allowedLabel + " ]");
        toggleBtn.setTextColor(isRestricted ? blockedColor : allowedColor);
        toggleBtn.setTextSize(12);
        toggleBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        toggleBtn.setGravity(Gravity.CENTER);
        toggleBtn.setMinimumHeight((int) (44 * density));
        toggleBtn.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
        toggleBtn.setClickable(true);
        toggleBtn.setFocusable(true);

        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(tokens.controlBgPressed);
        pressed.setCornerRadius((int) (4 * density));
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        toggleBtn.setBackground(sld);

        toggleBtn.setOnClickListener(onClick);
        top.addView(toggleBtn);
        row.addView(top);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = new TextView(activity);
            s.setText(subtitle);
            s.setTextColor(tokens.textMuted);
            s.setTextSize(11);
            s.setTypeface(Typeface.MONOSPACE);
            s.setPadding(0, (int) (2 * density), 0, 0);
            row.addView(s);
        }

        container.addView(row);
    }


    private static void updateToggleBtnState(TextView btn, boolean isRestricted, String restrictedText, String allowedText, int blockedColor, int allowedColor) {
        btn.setText(isRestricted ? "[ " + restrictedText + " ]" : "[ " + allowedText + " ]");
        btn.setTextColor(isRestricted ? blockedColor : allowedColor);
    }

    private static void addActionButton(
        LinearLayout container,
        Activity activity,
        ThemeTokens tokens,
        float density,
        String label,
        int textColor,
        View.OnClickListener onClick
    ) {
        TextView btn = new TextView(activity);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTextSize(13);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight((int) (44 * density));
        btn.setPadding(0, (int) (10 * density), 0, (int) (10 * density));
        btn.setClickable(true);
        btn.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke((int) (1 * density), tokens.border);
        bg.setCornerRadius((int) (4 * density));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int) (8 * density), 0, (int) (8 * density));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(onClick);
        container.addView(btn);
    }

    // ==========================================
    // 5. ALL APPS GRAPHENEOS PRIVACY HUB
    // ==========================================

    public static void showAllAppsPrivacyDialog(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode
    ) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dlg = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dlg.getWindow();
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
        final int pad = (int) (24 * density);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(pad, pad, pad, pad);

        // Header Title
        TextView titleView = new TextView(activity);
        titleView.setText("GRAPHENEOS APP PRIVACY GUARD");
        titleView.setTextColor(tokens.accent);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, (int) (4 * density));
        root.addView(titleView);

        // Subtitle
        final TextView summaryView = new TextView(activity);
        summaryView.setTextColor(tokens.textMuted);
        summaryView.setTextSize(12);
        summaryView.setTypeface(Typeface.MONOSPACE);
        summaryView.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(summaryView);

        // Dynamic Line Divider
        View divider = new View(activity);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (12 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        // Search Filter Input
        final EditText searchBox = new EditText(activity);
        searchBox.setHint(I18n.get(I18n.KEY_SEARCH_APPS, langCode));
        searchBox.setHintTextColor(tokens.textMuted);
        searchBox.setTextColor(tokens.textPrimary);
        searchBox.setTextSize(13);
        searchBox.setTypeface(Typeface.MONOSPACE);
        searchBox.setSingleLine(true);
        searchBox.setMinimumHeight((int) (44 * density));
        searchBox.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));

        final GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.TRANSPARENT);
        searchBg.setStroke((int) (1 * density), tokens.border);
        searchBg.setCornerRadius((int) (4 * density));
        searchBox.setBackground(searchBg);

        searchBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                searchBg.setStroke((int) (1 * density), hasFocus ? tokens.accent : tokens.border);
            }
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(0, 0, 0, (int) (12 * density));
        searchBox.setLayoutParams(searchLp);
        root.addView(searchBox);

        // Scrollable List
        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        root.addView(scroll);

        // Query apps
        PackageManager pm = activity.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

        Map<String, AppNetworkFirewall.AppEntry> appMap = new HashMap<>();
        for (ResolveInfo ri : activities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName()) || pkg.equals("com.android.settings") || pkg.equals("com.android.stk")
                    || pkg.equals("com.android.dialer") || pkg.equals("com.android.contacts")) {
                continue;
            }
            if (!appMap.containsKey(pkg)) {
                String label;
                try {
                    CharSequence cs = ri.loadLabel(pm);
                    label = (cs != null && cs.length() > 0) ? cs.toString() : pkg;
                } catch (Throwable t) {
                    label = pkg;
                }
                appMap.put(pkg, new AppNetworkFirewall.AppEntry(pkg, label));
            }
        }

        final List<AppNetworkFirewall.AppEntry> appsList = new ArrayList<>(appMap.values());
        Collections.sort(appsList, new Comparator<AppNetworkFirewall.AppEntry>() {
            @Override
            public int compare(AppNetworkFirewall.AppEntry a, AppNetworkFirewall.AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        renderAllAppsList("", appsList, listContainer, activity, tokens, langCode, summaryView, density);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderAllAppsList(s != null ? s.toString().trim() : "", appsList, listContainer, activity, tokens, langCode, summaryView, density);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Close Button
        TextView closeBtn = new TextView(activity);
        closeBtn.setText(I18n.get(I18n.KEY_CANCEL, langCode));
        closeBtn.setTextColor(tokens.navAccent);
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setMinimumHeight((int) (48 * density));
        closeBtn.setPadding(0, (int) (12 * density), 0, (int) (12 * density));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        root.addView(closeBtn);

        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (activity instanceof SettingsActivity) {
                    ((SettingsActivity) activity).buildSettingsUI();
                }
            }
        });

        dlg.setContentView(root);
        dlg.show();
    }

    private static void renderAllAppsList(
        final String query,
        final List<AppNetworkFirewall.AppEntry> allApps,
        final LinearLayout container,
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final TextView summaryView,
        final float density
    ) {
        container.removeAllViews();
        String q = (query != null) ? query.toLowerCase(Locale.ROOT) : "";
        int matchCount = 0;
        int restrictedAppsCount = 0;

        for (final AppNetworkFirewall.AppEntry app : allApps) {
            final boolean netBlocked = AppNetworkFirewall.isAppNetworkBlocked(activity, app.pkg);
            final boolean sensorsBlocked = isSensorsBlocked(activity, app.pkg);
            final boolean camMicBlocked = isCameraMicBlocked(activity, app.pkg);
            final boolean locBlocked = isLocationBlocked(activity, app.pkg);
            final boolean storageBlocked = isStorageBlocked(activity, app.pkg);
            final boolean contactsBlocked = isContactsBlocked(activity, app.pkg);
            final boolean bgRestricted = isBgRestricted(activity, app.pkg);

            boolean hasRestrictions = netBlocked || sensorsBlocked || camMicBlocked || locBlocked || storageBlocked || contactsBlocked || bgRestricted;
            if (hasRestrictions) {
                restrictedAppsCount++;
            }

            if (!q.isEmpty() && !app.lowerLabel.contains(q) && !app.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            matchCount++;

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (48 * density));
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
            row.setClickable(true);
            row.setFocusable(true);

            StateListDrawable sld = new StateListDrawable();
            GradientDrawable pressed = new GradientDrawable();
            pressed.setColor(tokens.controlBgPressed);
            pressed.setCornerRadius((int) (4 * density));
            sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sld.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            row.setBackground(sld);

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            textCol.setLayoutParams(textLp);

            TextView labelView = new TextView(activity);
            labelView.setText(app.label);
            labelView.setTextColor(tokens.textPrimary);
            labelView.setTextSize(14);
            labelView.setTypeface(Typeface.MONOSPACE);
            labelView.setSingleLine(true);
            textCol.addView(labelView);

            StringBuilder statusSb = new StringBuilder();
            if (netBlocked) statusSb.append(I18n.get(I18n.KEY_PRIVACY_TAG_NET, langCode));
            if (sensorsBlocked) statusSb.append(I18n.get(I18n.KEY_PRIVACY_TAG_SENSORS, langCode));
            if (camMicBlocked) statusSb.append(I18n.get(I18n.KEY_PRIVACY_TAG_CAM_MIC, langCode));
            if (locBlocked) statusSb.append(I18n.get(I18n.KEY_PRIVACY_TAG_GPS, langCode));
            if (storageBlocked) statusSb.append(I18n.get(I18n.KEY_PRIVACY_TAG_STORAGE, langCode));
            if (contactsBlocked) statusSb.append(I18n.get(I18n.KEY_PRIVACY_TAG_CONTACTS, langCode));
            if (bgRestricted) statusSb.append(I18n.get(I18n.KEY_PRIVACY_TAG_BG, langCode));
            if (statusSb.length() == 0) {
                statusSb.append(I18n.get(I18n.KEY_PRIVACY_STANDARD, langCode));
            } else {
                statusSb.append(I18n.get(I18n.KEY_STATUS_BLOCKED, langCode));
            }

            TextView statusView = new TextView(activity);
            statusView.setText(statusSb.toString());
            statusView.setTextColor(hasRestrictions ? (tokens.isLight ? Color.parseColor("#CC0000") : Color.parseColor("#FF5555")) : tokens.textMuted);
            statusView.setTextSize(11);
            statusView.setTypeface(Typeface.MONOSPACE);
            textCol.addView(statusView);

            row.addView(textCol);

            TextView configBtn = new TextView(activity);
            configBtn.setText(I18n.get(I18n.KEY_PRIVACY_CONFIGURE, langCode));
            configBtn.setTextColor(tokens.navAccent);
            configBtn.setTextSize(12);
            configBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            configBtn.setPadding((int) (8 * density), 0, 0, 0);
            row.addView(configBtn);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAppPrivacySheet(activity, tokens, app.pkg, app.label, langCode, new Runnable() {
                        @Override
                        public void run() {
                            renderAllAppsList(query, allApps, container, activity, tokens, langCode, summaryView, density);
                        }
                    });
                }
            });

            container.addView(row);
        }

        summaryView.setText(I18n.format(I18n.KEY_PRIVACY_SUMMARY_FMT, langCode, allApps.size(), restrictedAppsCount));
    }
}
