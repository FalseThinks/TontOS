package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
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
 * App Network Firewall & Privacy Controller for TontOS.
 * Implements GrapheneOS/CalyxOS per-app network isolation architecture.
 * Combines Android AppOps restrictions with root/privileged kernel iptables packet filtering.
 */
public final class AppNetworkFirewall {

    private static final String TAG = "AppNetworkFirewall";
    public static final String PREF_BLOCKED_NETWORK_APPS = "pref_blocked_network_apps";

    public static class AppEntry {
        public final String pkg;
        public final String label;
        public final String lowerLabel;

        public AppEntry(String pkg, String label) {
            this.pkg = pkg;
            this.label = label != null ? label : pkg;
            this.lowerLabel = this.label.toLowerCase(Locale.ROOT);
        }
    }

    private AppNetworkFirewall() {}

    /**
     * Checks whether network access for the given package is blocked in SharedPreferences.
     */
    public static boolean isAppNetworkBlocked(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> blocked = prefs.getStringSet(PREF_BLOCKED_NETWORK_APPS, null);
        return blocked != null && blocked.contains(pkg);
    }

    /**
     * Returns total count of apps currently blocked from network access.
     */
    public static int getBlockedAppsCount(Context context) {
        if (context == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> blocked = prefs.getStringSet(PREF_BLOCKED_NETWORK_APPS, null);
        return blocked != null ? blocked.size() : 0;
    }

    /**
     * Returns formatted summary string of blocked apps count for the current language.
     */
    public static String getBlockedCountSummary(Context context, String curLangCode) {
        int count = getBlockedAppsCount(context);
        return I18n.format(I18n.KEY_NET_BLOCKED_COUNT_FMT, curLangCode, count);
    }

    /**
     * Updates SharedPreferences and executes AppOp / iptables restrictions for the target package.
     */
    public static void setAppNetworkBlocked(Context context, String pkg, boolean blocked) {
        if (context == null || pkg == null) return;

        if (!SafeCommandExecutor.isValidPackageName(pkg)) {
            Log.w(TAG, "Refusing network policy operation for invalid package: " + pkg);
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = prefs.getStringSet(PREF_BLOCKED_NETWORK_APPS, null);
        Set<String> updated = new HashSet<>(current != null ? current : Collections.<String>emptySet());

        if (blocked) {
            updated.add(pkg);
        } else {
            updated.remove(pkg);
        }
        prefs.edit().putStringSet(PREF_BLOCKED_NETWORK_APPS, updated).apply();

        // 1. AppOps Restrictions (GrapheneOS/CalyxOS architecture)
        executeAppOps(pkg, blocked);

        // 2. Kernel iptables Packet Filtering (if rooted / privileged)
        int uid = -1;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkg, 0);
            uid = ai.uid;
        } catch (Throwable t) {
            Log.w(TAG, "Could not resolve UID for pkg: " + pkg, t);
        }

        if (uid >= 10000 && uid <= 19999999) {
            executeIptables(uid, blocked);
        }
    }

    /**
     * Executes AppOps restrictions to isolate background network activity and restricted settings.
     */
    private static void executeAppOps(String pkg, boolean blocked) {
        String mode = blocked ? "deny" : "allow";
        SafeCommandExecutor.execute("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", mode);
        SafeCommandExecutor.execute("cmd", "appops", "set", pkg, "ACCESS_RESTRICTED_SETTINGS", mode);
    }

    /**
     * Configures kernel netfilter rules via iptables/ip6tables for UID isolation.
     * Operates with tokenized command arguments to prevent shell interpolation.
     * Supports both primary user apps and secondary sandbox profiles (UID <= 19999999).
     */
    private static void executeIptables(int uid, boolean blocked) {
        if (uid < 10000 || uid > 19999999) return;
        String uidStr = String.valueOf(uid);

        // Delete existing rules first (cleanup)
        SafeCommandExecutor.executePrivileged("iptables", "-D", "OUTPUT", "-m", "owner", "--uid-owner", uidStr, "-j", "REJECT");
        SafeCommandExecutor.executePrivileged("ip6tables", "-D", "OUTPUT", "-m", "owner", "--uid-owner", uidStr, "-j", "REJECT");

        if (blocked) {
            boolean v4 = SafeCommandExecutor.executePrivileged("iptables", "-I", "OUTPUT", "-m", "owner", "--uid-owner", uidStr, "-j", "REJECT");
            boolean v6 = SafeCommandExecutor.executePrivileged("ip6tables", "-I", "OUTPUT", "-m", "owner", "--uid-owner", uidStr, "-j", "REJECT");
            if (v4 || v6) {
                Log.d(TAG, "Applied iptables rule for UID " + uid + " (blocked=true)");
            } else {
                Log.w(TAG, "iptables rule failed for UID " + uid + " (blocked=true)");
            }
        } else {
            Log.d(TAG, "Cleared iptables rules for UID " + uid + " (blocked=false)");
        }
    }

    /**
     * Displays AMOLED modal dialog listing all user installed apps with instant network policy toggles.
     */
    public static void showFirewallDialog(final Activity activity, final ThemeTokens tokens, final String curLangCode) {
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
        }

        final float density = activity.getResources().getDisplayMetrics().density;
        final int pad = (int) (24 * density);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(pad, pad, pad, pad);

        // Header Title
        TextView titleView = new TextView(activity);
        titleView.setText(I18n.get(I18n.KEY_APP_NETWORK_FIREWALL, curLangCode).toUpperCase(Locale.ROOT));
        titleView.setTextColor(tokens.accent);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, (int) (4 * density));
        root.addView(titleView);

        // Subtitle / Blocked Count Summary
        final TextView summaryView = new TextView(activity);
        summaryView.setText(getBlockedCountSummary(activity, curLangCode));
        summaryView.setTextColor(tokens.textMuted);
        summaryView.setTextSize(12);
        summaryView.setTypeface(Typeface.MONOSPACE);
        root.addView(summaryView);

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
        searchBox.setHint(I18n.get(I18n.KEY_SEARCH_APPS, curLangCode));
        searchBox.setHintTextColor(tokens.textMuted);
        searchBox.setTextColor(tokens.textPrimary);
        searchBox.setTextSize(13);
        searchBox.setTypeface(Typeface.MONOSPACE);
        searchBox.setSingleLine(true);
        searchBox.setMinimumHeight((int) (44 * density));
        searchBox.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));

        final GradientDrawable searchBg = new GradientDrawable();
        searchBg.setShape(GradientDrawable.RECTANGLE);
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

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.setMargins(0, 0, 0, (int) (12 * density));
        searchBox.setLayoutParams(searchLp);
        root.addView(searchBox);

        // Scrollable Apps List Container
        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        root.addView(scroll);

        // Query and Deduplicate Installed Apps
        PackageManager pm = activity.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

        Map<String, AppEntry> appMap = new HashMap<>();
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
                appMap.put(pkg, new AppEntry(pkg, label));
            }
        }

        final List<AppEntry> appsList = new ArrayList<>(appMap.values());
        Collections.sort(appsList, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // Initial list render
        renderAppList("", appsList, listContainer, activity, tokens, curLangCode, summaryView, density);

        // Search text watcher
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = (s != null) ? s.toString().trim() : "";
                renderAppList(query, appsList, listContainer, activity, tokens, curLangCode, summaryView, density);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Close / Back Button
        TextView closeBtn = new TextView(activity);
        closeBtn.setText(I18n.get(I18n.KEY_CANCEL, curLangCode));
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

    private static void renderAppList(
        String query,
        List<AppEntry> allApps,
        LinearLayout container,
        final Activity activity,
        final ThemeTokens tokens,
        final String curLangCode,
        final TextView summaryView,
        final float density
    ) {
        container.removeAllViews();
        String q = (query != null) ? query.toLowerCase(Locale.ROOT) : "";
        int matchCount = 0;

        final int blockedColor = tokens.isLight ? Color.parseColor("#CC0000") : Color.parseColor("#FF5555");
        final int allowedColor = tokens.navAccent;

        for (final AppEntry app : allApps) {
            if (!q.isEmpty() && !app.lowerLabel.contains(q) && !app.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            matchCount++;

            final String pkg = app.pkg;
            final boolean isBlocked = isAppNetworkBlocked(activity, pkg);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (48 * density));
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            TextView labelView = new TextView(activity);
            labelView.setText(app.label);
            labelView.setTextColor(tokens.textPrimary);
            labelView.setTextSize(13);
            labelView.setTypeface(Typeface.MONOSPACE);
            labelView.setSingleLine(true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            labelView.setLayoutParams(labelLp);
            row.addView(labelView);

            final TextView toggleBtn = new TextView(activity);
            toggleBtn.setText(isBlocked ? I18n.get(I18n.KEY_NET_BLOCKED, curLangCode) : I18n.get(I18n.KEY_NET_ALLOWED, curLangCode));
            toggleBtn.setTextColor(isBlocked ? blockedColor : allowedColor);
            toggleBtn.setTextSize(12);
            toggleBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            toggleBtn.setGravity(Gravity.CENTER);
            toggleBtn.setMinimumHeight((int) (44 * density));
            toggleBtn.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));
            toggleBtn.setClickable(true);
            toggleBtn.setFocusable(true);

            StateListDrawable sld = new StateListDrawable();
            GradientDrawable pressed = new GradientDrawable();
            pressed.setColor(tokens.controlBgPressed);
            pressed.setCornerRadius((int) (4 * density));
            sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sld.addState(new int[]{android.R.attr.state_focused}, pressed);
            sld.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            toggleBtn.setBackground(sld);

            toggleBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean currentlyBlocked = isAppNetworkBlocked(activity, pkg);
                    boolean newBlocked = !currentlyBlocked;
                    setAppNetworkBlocked(activity, pkg, newBlocked);

                    toggleBtn.setText(newBlocked ? I18n.get(I18n.KEY_NET_BLOCKED, curLangCode) : I18n.get(I18n.KEY_NET_ALLOWED, curLangCode));
                    toggleBtn.setTextColor(newBlocked ? blockedColor : allowedColor);
                    summaryView.setText(getBlockedCountSummary(activity, curLangCode));
                }
            });

            row.addView(toggleBtn);
            container.addView(row);
        }

        if (matchCount == 0) {
            TextView emptyView = new TextView(activity);
            emptyView.setText(I18n.format(I18n.KEY_NO_APPS_MATCH_FMT, curLangCode, query));
            emptyView.setTextColor(tokens.textMuted);
            emptyView.setTextSize(13);
            emptyView.setTypeface(Typeface.MONOSPACE);
            emptyView.setPadding(0, (int) (24 * density), 0, (int) (24 * density));
            container.addView(emptyView);
        }
    }
}
