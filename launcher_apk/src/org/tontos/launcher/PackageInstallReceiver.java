package org.tontos.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Automatically provisions sandbox profile and enforces privacy restrictions
 * upon new application installation.
 */
public class PackageInstallReceiver extends BroadcastReceiver {

    private static final String TAG = "PackageInstallReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            MainActivity.invalidateAppCache();
        }
        if (!Intent.ACTION_PACKAGE_ADDED.equals(action)) return;

        Uri data = intent.getData();
        if (data == null) return;
        final String pkg = data.getSchemeSpecificPart();
        if (pkg == null || pkg.isEmpty() || pkg.equals(context.getPackageName())) return;
        if (!SafeCommandExecutor.isValidPackageName(pkg)) {
            final String safePkgForLog = pkg.replace('\n', '_').replace('\r', '_');
            Log.w(TAG, "Rejecting invalid package name: " + safePkgForLog);
            return;
        }

        // Skip re-processing when app is merely updated
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;

        Log.i(TAG, "New app installed: " + pkg + ". Auto-provisioning sandbox and firewall policy.");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Ensure isolated sandbox profile exists
                    int sandboxId = SandboxManager.getSandboxUserId(context);
                    if (sandboxId < 10) {
                        SandboxManager.createSandboxProfile(context, SandboxManager.DEFAULT_SANDBOX_NAME);
                        sandboxId = SandboxManager.getSandboxUserId(context);
                    }

                    // 2. Provision package into sandbox if profile active
                    if (sandboxId >= 10) {
                        SafeCommandExecutor.executePrivileged("pm", "install-existing", "--user", String.valueOf(sandboxId), pkg);
                    }

                    // 3. Auto-restrict network if configured (Firewall)
                    AppNetworkFirewall.setAppNetworkBlocked(context, pkg, true);

                    // 4. Deny background execution and restricted settings by default for untrusted apps
                    SafeCommandExecutor.executePrivileged("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "deny");
                    SafeCommandExecutor.executePrivileged("cmd", "appops", "set", pkg, "ACCESS_RESTRICTED_SETTINGS", "deny");
                } catch (Throwable t) {
                    Log.w(TAG, "Error provisioning sandbox for " + pkg, t);
                }
            }
        }, "TontOS-SandboxProvisioner").start();
    }
}
