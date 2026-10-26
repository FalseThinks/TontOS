package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Reports NFC capabilities and Android-reported app installation details.
 * This class does not assess payment security or perform platform attestation.
 */
public final class NfcPaymentSandboxManager {

    public enum GmsInstallStatus {
        UNKNOWN("UNKNOWN", "Desconocido", "Unavailable"),
        NOT_INSTALLED("NOT_INSTALLED", "No instalado", "Not installed"),
        USER_INSTALLED("USER_INSTALLED", "Instalado por el usuario (aislamiento no verificado)", "User-installed (isolation not verified)"),
        SYSTEM_INSTALLED("SYSTEM_INSTALLED", "App del sistema (aislamiento no verificado)", "System app (isolation not verified)");

        public final String id;
        public final String labelEs;
        public final String labelEn;

        GmsInstallStatus(String id, String labelEs, String labelEn) {
            this.id = id;
            this.labelEs = labelEs;
            this.labelEn = labelEn;
        }

        public String getDisplay(String langCode) {
            switch (this) {
                case NOT_INSTALLED:
                    return I18n.get(I18n.KEY_GMS_NOT_INSTALLED, langCode);
                case USER_INSTALLED:
                    return I18n.get(I18n.KEY_GMS_USER_INSTALLED, langCode);
                case SYSTEM_INSTALLED:
                    return I18n.get(I18n.KEY_GMS_SYSTEM_INSTALLED, langCode);
                case UNKNOWN:
                default:
                    return I18n.get(I18n.KEY_GMS_UNKNOWN, langCode);
            }
        }
    }

    public static final class AttestationStatus {
        public final String basicIntegrity = "Not evaluated";
        public final String deviceIntegrity = "Not evaluated";
        public final String strongIntegrity = "Not evaluated";
        public final String platformStatus = "Unavailable (no trusted attestation API integrated)";
        public final String defaultSummary = "NFC on; attestation unavailable";
        public final String verificationStatus = "Not verified by this app";
        public final String isolationStatus = "isolation not verified";

        private AttestationStatus() {}
    }

    public static final String PREF_NFC_SANDBOX_HCE = "pref_nfc_sandbox_hce";

    public static boolean isSandboxHceEnabled(Context context) {
        if (context == null) return false;
        android.content.SharedPreferences sp = context.getSharedPreferences("tontos_prefs", Context.MODE_PRIVATE);
        return sp.getBoolean(PREF_NFC_SANDBOX_HCE, false);
    }

    public static void setSandboxHceEnabled(Context context, boolean enabled) {
        if (context == null) return;
        android.content.SharedPreferences sp = context.getSharedPreferences("tontos_prefs", Context.MODE_PRIVATE);
        sp.edit().putBoolean(PREF_NFC_SANDBOX_HCE, enabled).apply();
        try {
            SafeCommandExecutor.executePrivileged(
                enabled
                    ? "cmd role add-role-holder --user all android.app.role.WALLET $(cmd role get-role-holders android.app.role.WALLET 2>/dev/null) 2>/dev/null; setprop persist.sys.nfc_sandbox_hce 1"
                    : "setprop persist.sys.nfc_sandbox_hce 0"
            );
        } catch (Throwable ignored) {}
    }

    private NfcPaymentSandboxManager() {}

    public static boolean isNfcSupported(Context context) {
        if (context == null) return false;
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            return adapter != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isNfcEnabled(Context context) {
        if (context == null) return false;
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            return adapter != null && adapter.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isHceSupported(Context context) {
        if (context == null) return false;
        try {
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String getDefaultPaymentApp(Context context) {
        if (context == null) return "None";
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            if (adapter == null) return "No NFC";
            CardEmulation ce = CardEmulation.getInstance(adapter);
            if (ce == null) return "No HCE";

            String defaultComponent = Settings.Secure.getString(context.getContentResolver(), "nfc_payment_default_component");
            if (defaultComponent != null && !defaultComponent.isEmpty()) {
                ComponentName cn = ComponentName.unflattenFromString(defaultComponent);
                if (cn != null) {
                    try {
                        CharSequence label = context.getPackageManager().getApplicationLabel(
                            context.getPackageManager().getApplicationInfo(cn.getPackageName(), 0)
                        );
                        return label.toString();
                    } catch (Throwable ignored) {
                        return cn.getPackageName();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "None";
    }

    public static GmsInstallStatus getGmsInstallStatus(Context context) {
        if (context == null) return GmsInstallStatus.UNKNOWN;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo("com.google.android.gms", 0);
            boolean isSystem = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (isSystem) {
                return GmsInstallStatus.SYSTEM_INSTALLED;
            }
            return GmsInstallStatus.USER_INSTALLED;
        } catch (PackageManager.NameNotFoundException e) {
            return GmsInstallStatus.NOT_INSTALLED;
        } catch (Throwable ignored) {
            return GmsInstallStatus.UNKNOWN;
        }
    }

    public static AttestationStatus getAttestationStatus() {
        return new AttestationStatus();
    }

    public static String getSummaryStatus(Context context, String langCode) {
        if (!isNfcSupported(context)) {
            return I18n.get(I18n.KEY_NFC_NO_HARDWARE, langCode);
        }
        if (!isNfcEnabled(context)) {
            return I18n.get(I18n.KEY_NFC_OFF, langCode);
        }
        return I18n.get(I18n.KEY_NFC_ON_NO_ATTESTATION, langCode);
    }

    public static void showDiagnosticsDialog(final Activity activity, final ThemeTokens tokens, final String langCode) {
        if (activity == null || activity.isFinishing()) return;

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_NFC_ATTESTATION_TITLE, langCode));
        final float density = dlg.getDensity();
        final ThemeTokens themeTokens = dlg.getTokens();
        final LinearLayout list = dlg.getContentContainer();

        boolean nfcSupported = isNfcSupported(activity);
        boolean nfcEnabled = isNfcEnabled(activity);
        boolean hceSupported = isHceSupported(activity);
        String defaultApp = getDefaultPaymentApp(activity);
        GmsInstallStatus gmsInstallStatus = getGmsInstallStatus(activity);
        AttestationStatus attestation = getAttestationStatus();

        // Section 1: NFC Hardware & HCE
        addCategoryHeader(list, I18n.get(I18n.KEY_NFC_SEC_HW, langCode), tokens);
        addStatusRow(list, "NFC", nfcSupported ? (nfcEnabled ? "[ " + I18n.get(I18n.KEY_STATE_ON, langCode) + " ]" : "[ " + I18n.get(I18n.KEY_STATE_OFF, langCode) + " ]") : "[ " + I18n.get(I18n.KEY_NOT_SUPPORTED, langCode) + " ]", nfcEnabled, tokens);
        addStatusRow(list, "HCE (Host Emulation)", hceSupported ? "[ OK ]" : "[ " + I18n.get(I18n.KEY_NOT_SUPPORTED, langCode) + " ]", hceSupported, tokens);
        addStatusRow(list, I18n.get(I18n.KEY_NFC_PAYMENT_APP, langCode), defaultApp, !"None".equals(defaultApp), tokens);

        addCategoryHeader(list, I18n.get(I18n.KEY_NFC_SEC_GMS, langCode), tokens);
        addStatusRow(list, I18n.get(I18n.KEY_NFC_GMS_INSTALL, langCode), gmsInstallStatus.getDisplay(langCode), false, tokens);

        addCategoryHeader(list, I18n.get(I18n.KEY_NFC_SEC_ATTESTATION, langCode), tokens);
        addStatusRow(list, "Play Integrity API", attestation.platformStatus, false, tokens);
        addStatusRow(list, "Basic / Device / Strong", I18n.get(I18n.KEY_NFC_NOT_EVALUATED, langCode), false, tokens);
        addStatusRow(list, "Verified Boot / CTS", I18n.get(I18n.KEY_NFC_NOT_VERIFIED, langCode), false, tokens);

        // Section: Isolated Sandbox / Multi-Profile HCE Routing
        addCategoryHeader(list, I18n.get(I18n.KEY_NFC_SEC_SANDBOX, langCode), tokens);
        addDescriptionText(list, I18n.get(I18n.KEY_NFC_SANDBOX_HCE_DESC, langCode), tokens);
        final boolean isSandboxHce = isSandboxHceEnabled(activity);
        addToggleRow(list, I18n.get(I18n.KEY_NFC_SANDBOX_HCE_TITLE, langCode), isSandboxHce ? "[ " + I18n.get(I18n.KEY_STATE_ON, langCode) + " ]" : "[ " + I18n.get(I18n.KEY_STATE_OFF, langCode) + " ]", isSandboxHce, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !isSandboxHceEnabled(activity);
                setSandboxHceEnabled(activity, next);
                dlg.dismiss();
                showDiagnosticsDialog(activity, tokens, langCode);
            }
        }, tokens);

        // Actions
        addCategoryHeader(list, I18n.get(I18n.KEY_NFC_SEC_ACTIONS, langCode), tokens);
        addActionRow(list, I18n.get(I18n.KEY_NFC_ACTIONS_PAYMENTS, langCode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS));
                } catch (Throwable e) {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                    } catch (Throwable t) {
                        Toast.makeText(activity, "NFC settings not available", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }, tokens);

        // Close button
        dlg.addActionButton(
            I18n.get(I18n.KEY_CANCEL, langCode),
            themeTokens.navAccent,
            themeTokens.border,
            true,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                }
            }
        );

        dlg.show();
    }

    private static void addCategoryHeader(LinearLayout container, String title, ThemeTokens tokens) {
        TextView tv = new TextView(container.getContext());
        tv.setText(title);
        tv.setTextColor(tokens.accent);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setPadding(0, 16, 0, 6);
        container.addView(tv);
    }

    private static void addStatusRow(LinearLayout container, String label, String value, boolean isPositive, ThemeTokens tokens) {
        LinearLayout row = new LinearLayout(container.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 4, 0, 4);

        TextView lbl = new TextView(container.getContext());
        lbl.setText(label);
        lbl.setTextColor(tokens.textPrimary);
        lbl.setTextSize(12);
        lbl.setTypeface(Typeface.MONOSPACE);
        lbl.setSingleLine(true);
        lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lbl.setLayoutParams(lp);
        row.addView(lbl);

        TextView val = new TextView(container.getContext());
        val.setText(value);
        val.setTextColor(isPositive ? tokens.navAccent : tokens.textMuted);
        val.setTextSize(12);
        val.setTypeface(Typeface.MONOSPACE, isPositive ? Typeface.BOLD : Typeface.NORMAL);
        val.setSingleLine(true);
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(val);

        container.addView(row);
    }

    private static void addActionRow(LinearLayout container, String actionText, View.OnClickListener listener, ThemeTokens tokens) {
        TextView btn = new TextView(container.getContext());
        btn.setText("[ " + actionText + " ]");
        btn.setTextColor(tokens.navAccent);
        btn.setTextSize(13);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setPadding(0, 10, 0, 10);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(listener);
        container.addView(btn);
    }

    private static void addDescriptionText(LinearLayout container, String desc, ThemeTokens tokens) {
        TextView tv = new TextView(container.getContext());
        tv.setText(desc);
        tv.setTextColor(tokens.textMuted);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(0, 0, 0, 6);
        container.addView(tv);
    }

    private static void addToggleRow(LinearLayout container, String label, String value, boolean isPositive, View.OnClickListener listener, ThemeTokens tokens) {
        LinearLayout row = new LinearLayout(container.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);

        TextView lbl = new TextView(container.getContext());
        lbl.setText(label);
        lbl.setTextColor(tokens.textPrimary);
        lbl.setTextSize(12);
        lbl.setTypeface(Typeface.MONOSPACE);
        lbl.setSingleLine(true);
        lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lbl.setLayoutParams(lp);
        row.addView(lbl);

        TextView val = new TextView(container.getContext());
        val.setText(value);
        val.setTextColor(isPositive ? tokens.navAccent : tokens.textMuted);
        val.setTextSize(12);
        val.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        val.setSingleLine(true);
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(val);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(listener);

        container.addView(row);
    }
}
