package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Minimalist top slide-down shade mimicking Xiaomi Ultra Battery Saver mode,
 * adhering 100% to the active ThemeTokens (AMOLED Black, Amber CRT, E-Ink Monochrome, Slate Midnight).
 */
public final class UltraShadeDialog {

    private UltraShadeDialog() {}

    public static void show(final Activity activity, final ThemeTokens tokens, final int batteryLevel) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                if (activity instanceof UltraShadeActivity && !activity.isFinishing()) {
                    activity.finish();
                }
            }
        });

        SharedPreferences prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        final String lang = prefs.getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        final java.util.Set<String> tilesOrder = prefs.getStringSet("shade_tiles", null);

        int densityDpi = activity.getResources().getDisplayMetrics().densityDpi;
        final float density = densityDpi / 160.0f;
        final int dp12 = (int) (12 * density);
        final int dp8 = (int) (8 * density);
        final int dp16 = (int) (16 * density);
        final int dp48 = (int) (48 * density);

        // Root container
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp16, dp16, dp16, dp16);

        // Background styling matching active theme
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(tokens.bg);
        rootBg.setStroke((int) (2 * density), tokens.border);
        // Rounded bottom edges for slide-down sheet effect
        float r = 16 * density;
        rootBg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        root.setBackground(rootBg);

        // Top drag handle indicator
        TextView dragHandle = new TextView(activity);
        dragHandle.setText("━━━━━━");
        dragHandle.setTextColor(tokens.border);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setTextSize(14);
        dragHandle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        dragHandle.setPadding(0, 0, 0, dp8);
        dragHandle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { try { dialog.dismiss(); } catch (Throwable ignored) {} }
        });
        root.addView(dragHandle);

        // Header Title
        final TextView headerTitle = new TextView(activity);
        final Runnable updateHeader = new Runnable() {
            @Override
            public void run() {
                boolean isUltra = UltraBatterySaverManager.isUltraBatterySaverActive(activity);
                headerTitle.setText(isUltra
                    ? I18n.get(I18n.KEY_SHADE_HEADER, lang)
                    : I18n.get(I18n.KEY_QUICK_SETTINGS_HEADER, lang));
                headerTitle.setTextColor(isUltra ? (tokens.isLight ? Color.parseColor("#CC0000") : Color.parseColor("#FF5555")) : tokens.accent);
            }
        };
        updateHeader.run();
        headerTitle.setTextSize(15);
        headerTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        headerTitle.setGravity(Gravity.CENTER);
        root.addView(headerTitle);

        // Show the measured battery level only; no runtime measurements are available.
        TextView batteryEstimateView = new TextView(activity);
        batteryEstimateView.setText(batteryLevel + "%");
        batteryEstimateView.setTextColor(tokens.textPrimary);
        batteryEstimateView.setTextSize(13);
        batteryEstimateView.setTypeface(Typeface.MONOSPACE);
        batteryEstimateView.setGravity(Gravity.CENTER);
        batteryEstimateView.setPadding(0, (int) (4 * density), 0, dp12);
        root.addView(batteryEstimateView);

        // Divider line
        View divider = new View(activity);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)
        );
        divLp.setMargins(0, 0, 0, dp12);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(tokens.border);
        root.addView(divider);

        // Grid of Quick Toggles (2 rows x 3 columns or 3 rows x 2 columns)
        // Row 1: Modo Avión | Modo No Molestar
        LinearLayout row1 = createToggleRow(activity);
        final TextView btnAirplane = createToggleButton(activity, tokens, dp48);
        final TextView btnDnd = createToggleButton(activity, tokens, dp48);
        row1.addView(btnAirplane);
        row1.addView(createSpacer(activity, dp8));
        row1.addView(btnDnd);
        root.addView(row1);

        // Spacer between rows
        root.addView(createVerticalSpacer(activity, dp8));

        // Row 2: Wi-Fi | Datos Móviles
        LinearLayout row2 = createToggleRow(activity);
        final TextView btnWifi = createToggleButton(activity, tokens, dp48);
        final TextView btnData = createToggleButton(activity, tokens, dp48);
        row2.addView(btnWifi);
        row2.addView(createSpacer(activity, dp8));
        row2.addView(btnData);
        root.addView(row2);

        // Spacer between rows
        root.addView(createVerticalSpacer(activity, dp8));

        // Row 3: Bluetooth | Linterna
        LinearLayout row3 = createToggleRow(activity);
        final TextView btnBluetooth = createToggleButton(activity, tokens, dp48);
        final TextView btnTorch = createToggleButton(activity, tokens, dp48);
        row3.addView(btnBluetooth);
        row3.addView(createSpacer(activity, dp8));
        row3.addView(btnTorch);
        root.addView(row3);

        // Spacer between rows
        root.addView(createVerticalSpacer(activity, dp8));

        // Row 4: Ultra Battery Saver | NFC
        LinearLayout row4 = createToggleRow(activity);
        final TextView btnUltra = createToggleButton(activity, tokens, dp48);
        final TextView btnNfc = createToggleButton(activity, tokens, dp48);
        final boolean nfcSupported = UltraBatterySaverManager.getNfcState(activity) != UltraBatterySaverManager.ToggleState.UNKNOWN
                || android.nfc.NfcAdapter.getDefaultAdapter(activity) != null;
        row4.addView(btnUltra);
        row4.addView(createSpacer(activity, dp8));
        if (nfcSupported) {
            row4.addView(btnNfc);
        } else {
            android.widget.Space ph = new android.widget.Space(activity);
            ph.setLayoutParams(new LinearLayout.LayoutParams(0, dp48, 1.0f));
            row4.addView(ph);
        }
        root.addView(row4);

        // Update states helper
        final Runnable stateRefresher = new Runnable() {
            @Override
            public void run() {
                // Ultra Saver Mode
                boolean isUltra = UltraBatterySaverManager.isUltraBatterySaverActive(activity);
                updateToggleStyle(btnUltra, tokens,
                    I18n.get(I18n.KEY_TOGGLE_ULTRA_SAVER, lang) + ": "
                        + (isUltra ? I18n.get(I18n.KEY_STATE_ON, lang) : I18n.get(I18n.KEY_STATE_OFF, lang)),
                    isUltra, density);
                updateHeader.run();

                // Airplane Mode
                UltraBatterySaverManager.ToggleState airplaneState =
                    UltraBatterySaverManager.getAirplaneModeState(activity);
                updateToggleStyle(btnAirplane, tokens,
                    I18n.get(I18n.KEY_TOGGLE_AIRPLANE, lang) + ": "
                        + UltraBatterySaverManager.getToggleStateDisplay(airplaneState, lang),
                    airplaneState == UltraBatterySaverManager.ToggleState.ON, density);

                // DND Mode
                UltraBatterySaverManager.ToggleState dndState =
                    UltraBatterySaverManager.getDndState(activity);
                updateToggleStyle(btnDnd, tokens,
                    I18n.get(I18n.KEY_TOGGLE_DND, lang) + ": "
                        + UltraBatterySaverManager.getToggleStateDisplay(dndState, lang),
                    dndState == UltraBatterySaverManager.ToggleState.ON, density);

                // Wi-Fi
                UltraBatterySaverManager.ToggleState wifiState =
                    UltraBatterySaverManager.getWifiState(activity);
                updateToggleStyle(btnWifi, tokens,
                    I18n.get(I18n.KEY_TOGGLE_WIFI, lang) + ": "
                        + UltraBatterySaverManager.getToggleStateDisplay(wifiState, lang),
                    wifiState == UltraBatterySaverManager.ToggleState.ON, density);

                // Mobile Data
                UltraBatterySaverManager.ToggleState dataState =
                    UltraBatterySaverManager.getMobileDataState(activity);
                updateToggleStyle(btnData, tokens,
                    I18n.get(I18n.KEY_TOGGLE_DATA, lang) + ": "
                        + UltraBatterySaverManager.getToggleStateDisplay(dataState, lang),
                    dataState == UltraBatterySaverManager.ToggleState.ON, density);

                // Bluetooth
                UltraBatterySaverManager.ToggleState bluetoothState =
                    UltraBatterySaverManager.getBluetoothState(activity);
                updateToggleStyle(btnBluetooth, tokens,
                    I18n.get(I18n.KEY_TOGGLE_BLUETOOTH, lang) + ": "
                        + UltraBatterySaverManager.getToggleStateDisplay(bluetoothState, lang),
                    bluetoothState == UltraBatterySaverManager.ToggleState.ON, density);

                // Torch
                UltraBatterySaverManager.ToggleState torchState =
                    UltraBatterySaverManager.getTorchState(activity);
                updateToggleStyle(btnTorch, tokens,
                    I18n.get(I18n.KEY_TOGGLE_TORCH, lang) + ": "
                        + UltraBatterySaverManager.getToggleStateDisplay(torchState, lang),
                    torchState == UltraBatterySaverManager.ToggleState.ON, density);

                // NFC
                try { 
                    UltraBatterySaverManager.ToggleState nfcState = UltraBatterySaverManager.getNfcState(activity);
                    updateToggleStyle(btnNfc, tokens,
                        I18n.get(I18n.KEY_TOGGLE_NFC, lang) + ": " + UltraBatterySaverManager.getToggleStateDisplay(nfcState, lang),
                        nfcState == UltraBatterySaverManager.ToggleState.ON, density);
                    btnNfc.setVisibility(nfcSupported ? View.VISIBLE : View.GONE);
                } catch (Throwable ignored) {}
                // tiles config
                try {
                    btnAirplane.setVisibility(isTileEnabled(tilesOrder, "airplane") ? View.VISIBLE : View.GONE);
                    btnDnd.setVisibility(isTileEnabled(tilesOrder, "dnd") ? View.VISIBLE : View.GONE);
                    btnWifi.setVisibility(isTileEnabled(tilesOrder, "wifi") ? View.VISIBLE : View.GONE);
                    btnData.setVisibility(isTileEnabled(tilesOrder, "data") ? View.VISIBLE : View.GONE);
                    btnBluetooth.setVisibility(isTileEnabled(tilesOrder, "bluetooth") ? View.VISIBLE : View.GONE);
                    btnTorch.setVisibility(isTileEnabled(tilesOrder, "torch") ? View.VISIBLE : View.GONE);
                    btnNfc.setVisibility((isTileEnabled(tilesOrder, "nfc") && nfcSupported) ? View.VISIBLE : View.GONE);
                } catch (Throwable ignored2) {}
            }
        };

        // Click listeners
        btnUltra.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean active = UltraBatterySaverManager.isUltraBatterySaverActive(activity);
                UltraBatterySaverManager.ActionResult result =
                    UltraBatterySaverManager.setUltraBatterySaver(activity, !active);
                handleActionResult(activity, dialog, v, stateRefresher, lang, result);
            }
        });

        btnAirplane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (LockScreenManager.isDeviceLocked(activity) && LockSecurityManager.getSecurityType(activity) != LockSecurityManager.SecurityType.NONE) {
                    Toast.makeText(activity, I18n.get(I18n.KEY_LOCK_PIN_PROMPT, lang), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    LockScreenManager.launchLockScreen(activity);
                    return;
                }
                UltraBatterySaverManager.ToggleState cur = UltraBatterySaverManager.getAirplaneModeState(activity);
                boolean enable = cur != UltraBatterySaverManager.ToggleState.ON;
                UltraBatterySaverManager.ActionResult result =
                    UltraBatterySaverManager.setAirplaneMode(activity, enable);
                handleActionResult(activity, dialog, v, stateRefresher, lang, result);
            }
        });

        btnDnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UltraBatterySaverManager.ToggleState state =
                    UltraBatterySaverManager.getDndState(activity);
                UltraBatterySaverManager.ActionResult result =
                    state == UltraBatterySaverManager.ToggleState.UNKNOWN
                        ? UltraBatterySaverManager.ActionResult.UNSUPPORTED
                        : UltraBatterySaverManager.setDnd(
                            activity, state != UltraBatterySaverManager.ToggleState.ON);
                handleActionResult(activity, dialog, v, stateRefresher, lang, result);
            }
        });

        btnWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UltraBatterySaverManager.ToggleState state =
                    UltraBatterySaverManager.getWifiState(activity);
                UltraBatterySaverManager.ActionResult result =
                    state == UltraBatterySaverManager.ToggleState.UNKNOWN
                        ? UltraBatterySaverManager.openWifiSettings(activity)
                        : UltraBatterySaverManager.setWifi(
                            activity, state != UltraBatterySaverManager.ToggleState.ON);
                handleActionResult(activity, dialog, v, stateRefresher, lang, result);
            }
        });

        btnData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleActionResult(
                    activity, dialog, v, stateRefresher, lang,
                    UltraBatterySaverManager.openMobileDataSettings(activity));
            }
        });

        btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UltraBatterySaverManager.ToggleState state =
                    UltraBatterySaverManager.getBluetoothState(activity);
                UltraBatterySaverManager.ActionResult result;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && state == UltraBatterySaverManager.ToggleState.UNKNOWN) {
                    result = UltraBatterySaverManager.requestBluetoothPermission(activity);
                    if (result == UltraBatterySaverManager.ActionResult.REQUESTED) {
                        result = UltraBatterySaverManager.openBluetoothSettings(activity);
                    }
                } else if (state == UltraBatterySaverManager.ToggleState.UNKNOWN) {
                    result = UltraBatterySaverManager.openBluetoothSettings(activity);
                } else {
                    result = UltraBatterySaverManager.setBluetooth(
                        activity, state != UltraBatterySaverManager.ToggleState.ON);
                }
                handleActionResult(activity, dialog, v, stateRefresher, lang, result);
            }
        });

        btnNfc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UltraBatterySaverManager.ToggleState st = UltraBatterySaverManager.getNfcState(activity);
                UltraBatterySaverManager.ActionResult r = (st == UltraBatterySaverManager.ToggleState.UNKNOWN)
                        ? UltraBatterySaverManager.openNfcSettings(activity)
                        : UltraBatterySaverManager.setNfc(activity, st != UltraBatterySaverManager.ToggleState.ON);
                // toggleable: deselect immediately, refresh after
                try { btnNfc.setSelected(r == UltraBatterySaverManager.ActionResult.APPLIED && st != UltraBatterySaverManager.ToggleState.ON); } catch (Throwable ignored) {}
                handleActionResult(activity, dialog, v, stateRefresher, lang, r);
            }
        });

        btnTorch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UltraBatterySaverManager.ActionResult result =
                    UltraBatterySaverManager.toggleTorch(activity);
                handleActionResult(activity, dialog, v, stateRefresher, lang, result);
            }
        });

        // Initialize state styling
        stateRefresher.run();

        // Bottom action bar: [ <-- CERRAR ] and [ AJUSTES ]
        root.addView(createVerticalSpacer(activity, dp16));
        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnClose = new TextView(activity);
        btnClose.setText(ensureBrackets(I18n.get(I18n.KEY_SHADE_CLOSE, lang)));
        btnClose.setTextColor(tokens.textMuted);
        btnClose.setTextSize(13);
        btnClose.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setClickable(true);
        btnClose.setFocusable(true);
        btnClose.setBackground(createButtonDrawable(tokens.controlBgNormal, tokens.border, (int) (1 * density), (int) (4 * density)));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, dp48, 1.0f);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        bottomRow.addView(btnClose);

        bottomRow.addView(createSpacer(activity, dp8));

        TextView btnSettings = new TextView(activity);
        btnSettings.setText(ensureBrackets(I18n.get(I18n.KEY_SHADE_SETTINGS, lang)));
        btnSettings.setTextColor(tokens.navAccent);
        btnSettings.setTextSize(13);
        btnSettings.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setClickable(true);
        btnSettings.setFocusable(true);
        btnSettings.setBackground(createButtonDrawable(tokens.controlBgNormal, tokens.navAccent, (int) (1 * density), (int) (4 * density)));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(0, dp48, 1.0f);
        btnSettings.setLayoutParams(settingsLp);
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (LockScreenManager.isDeviceLocked(activity) && LockScreenManager.isLockScreenEnabled(activity)) {
                    Toast.makeText(activity, I18n.get(I18n.KEY_LOCK_PIN_PROMPT, lang), Toast.LENGTH_SHORT).show();
                    LockScreenManager.launchLockScreen(activity);
                    return;
                }
                activity.startActivity(new Intent(activity, SettingsActivity.class));
            }
        });
        bottomRow.addView(btnSettings);
        root.addView(bottomRow);

        dialog.setContentView(root);

        // Window properties for top slide-down placement
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.TOP);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setWindowAnimations(0); // Disable jarring default dialog scale-in

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
                    window.getInsetsController().setSystemBarsAppearance(
                        tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } catch (Throwable ignored) {}
        }

        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        root.setTranslationY(-root.getHeight());
                        root.animate()
                            .translationY(0f)
                            .setDuration(220)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    }
                });
            }
        });

        dialog.show();
    }

    private static String ensureBrackets(String val) {
        return TactileUi.ensureBrackets(val);
    }

    private static boolean isTileEnabled(java.util.Set<String> tilesOrder, String id) {
        if (tilesOrder == null || tilesOrder.isEmpty()) return true;
        return tilesOrder.contains(id);
    }

    private static void handleActionResult(
            Activity activity,
            Dialog dialog,
            View view,
            Runnable stateRefresher,
            String lang,
            UltraBatterySaverManager.ActionResult result) {
        Toast.makeText(
            activity,
            UltraBatterySaverManager.getActionResultDisplay(result, lang),
            Toast.LENGTH_LONG
        ).show();
        if (result == UltraBatterySaverManager.ActionResult.APPLIED
                || result == UltraBatterySaverManager.ActionResult.REQUESTED
                || result == UltraBatterySaverManager.ActionResult.SETTINGS_REQUIRED
                || result == UltraBatterySaverManager.ActionResult.PERMISSION_REQUIRED) {
            try { view.postDelayed(stateRefresher, 600); } catch (Throwable ignored) {}
        }
    }

    private static LinearLayout createToggleRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private static TextView createToggleButton(Context context, ThemeTokens tokens, int height) {
        TextView btn = new TextView(context);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, height, 1.0f));
        btn.setTextSize(12);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(4, 0, 4, 0);
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }

    private static void updateToggleStyle(TextView btn, ThemeTokens tokens, String text, boolean isActive, float density) {
        btn.setText(text);
        if (isActive) {
            btn.setTextColor(tokens.activeSelectionText);
            btn.setBackground(createButtonDrawable(tokens.activeSelectionBg, tokens.accent, (int) (2 * density), (int) (4 * density)));
        } else {
            btn.setTextColor(tokens.textMuted);
            btn.setBackground(createButtonDrawable(tokens.controlBgNormal, tokens.border, (int) (1 * density), (int) (4 * density)));
        }
    }

    private static View createSpacer(Context context, int width) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }

    private static View createVerticalSpacer(Context context, int height) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    private static StateListDrawable createButtonDrawable(int normalBg, int strokeColor, int strokeWidth, int cornerRadius) {
        return TactileUi.createButtonDrawable(normalBg, strokeColor, strokeColor, strokeWidth, cornerRadius);
    }
}
