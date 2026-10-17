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

        private AttestationStatus() {}
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

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(tokens.bg));
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
                    window.getInsetsController().setSystemBarsAppearance(
                        tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } catch (Throwable ignored) {}
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            GrayscaleManager.applyGrayscaleToWindow(window, GrayscaleManager.isGrayscaleEnabled(activity));
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int screenWidthPx = activity.getResources().getDisplayMetrics().widthPixels;
        int screenWidthDp = (int) (screenWidthPx / density);
        int padH = screenWidthDp < 360 ? (int) (14 * density) : (int) (20 * density);
        int padV = (int) (20 * density);

        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(tokens.bg);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(tokens.bg);
        root.setPadding(padH, padV, padH, padV);

        if (screenWidthDp > 520) {
            root.setLayoutParams(new LinearLayout.LayoutParams((int) (480 * density), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Header Title
        TextView header = new TextView(activity);
        header.setText(I18n.get(I18n.KEY_NFC_ATTESTATION_TITLE, langCode));
        header.setTextColor(tokens.accent);
        header.setTextSize(screenWidthDp < 360 ? 15 : 17);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setSingleLine(true);
        header.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.setPadding(0, 0, 0, (int) (6 * density));
        root.addView(header);

        View divider = new View(activity);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density + 0.5f)
        );
        divParams.setMargins(0, 0, 0, (int) (12 * density));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

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

        scroll.addView(list);
        root.addView(scroll);

        // Close button
        TextView closeBtn = new TextView(activity);
        closeBtn.setText(I18n.get(I18n.KEY_CANCEL, langCode));
        closeBtn.setTextColor(tokens.navAccent);
        closeBtn.setTextSize(13);
        closeBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setMinimumHeight((int) (44 * density));
        closeBtn.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);

        StateListDrawable btnSld = new StateListDrawable();
        GradientDrawable btnPressed = new GradientDrawable();
        btnPressed.setColor(tokens.controlBgPressed);
        btnPressed.setCornerRadius((int) (4 * density));
        btnSld.addState(new int[]{android.R.attr.state_pressed}, btnPressed);
        btnSld.addState(new int[]{android.R.attr.state_focused}, btnPressed);
        btnSld.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        closeBtn.setBackground(btnSld);

        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(0, (int) (10 * density), 0, 0);
        closeBtn.setLayoutParams(closeLp);
        root.addView(closeBtn);

        outer.addView(root);
        dialog.setContentView(outer);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
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
}
