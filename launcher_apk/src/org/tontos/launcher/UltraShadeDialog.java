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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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

        SharedPreferences prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        final String lang = prefs.getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");

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
        dragHandle.setPadding(0, 0, 0, dp8);
        root.addView(dragHandle);

        // Header Title
        TextView headerTitle = new TextView(activity);
        headerTitle.setText(I18n.get(I18n.KEY_SHADE_HEADER, lang));
        headerTitle.setTextColor(tokens.accent);
        headerTitle.setTextSize(15);
        headerTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        headerTitle.setGravity(Gravity.CENTER);
        root.addView(headerTitle);

        // Autonomy and battery estimate
        int hoursRemaining = UltraBatterySaverManager.getEstimatedAutonomyHours(batteryLevel);
        TextView batteryEstimateView = new TextView(activity);
        String estimateText = I18n.format(I18n.KEY_SHADE_ESTIMATE_FMT, lang, batteryLevel, hoursRemaining);
        batteryEstimateView.setText(estimateText);
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
        final Button btnAirplane = createToggleButton(activity, tokens, dp48);
        final Button btnDnd = createToggleButton(activity, tokens, dp48);
        row1.addView(btnAirplane);
        row1.addView(createSpacer(activity, dp8));
        row1.addView(btnDnd);
        root.addView(row1);

        // Spacer between rows
        root.addView(createVerticalSpacer(activity, dp8));

        // Row 2: Wi-Fi | Datos Móviles
        LinearLayout row2 = createToggleRow(activity);
        final Button btnWifi = createToggleButton(activity, tokens, dp48);
        final Button btnData = createToggleButton(activity, tokens, dp48);
        row2.addView(btnWifi);
        row2.addView(createSpacer(activity, dp8));
        row2.addView(btnData);
        root.addView(row2);

        // Spacer between rows
        root.addView(createVerticalSpacer(activity, dp8));

        // Row 3: Bluetooth | Linterna
        LinearLayout row3 = createToggleRow(activity);
        final Button btnBluetooth = createToggleButton(activity, tokens, dp48);
        final Button btnTorch = createToggleButton(activity, tokens, dp48);
        row3.addView(btnBluetooth);
        row3.addView(createSpacer(activity, dp8));
        row3.addView(btnTorch);
        root.addView(row3);

        // Update states helper
        final Runnable stateRefresher = new Runnable() {
            @Override
            public void run() {
                // Airplane Mode
                boolean airplaneOn = UltraBatterySaverManager.isAirplaneModeOn(activity);
                updateToggleStyle(btnAirplane, tokens,
                    I18n.get(I18n.KEY_TOGGLE_AIRPLANE, lang) + ": " + (airplaneOn ? I18n.get(I18n.KEY_STATE_ON, lang) : I18n.get(I18n.KEY_STATE_OFF, lang)),
                    airplaneOn, density);

                // DND Mode
                boolean dndOn = UltraBatterySaverManager.isDndOn(activity);
                updateToggleStyle(btnDnd, tokens,
                    I18n.get(I18n.KEY_TOGGLE_DND, lang) + ": " + (dndOn ? I18n.get(I18n.KEY_STATE_ON, lang) : I18n.get(I18n.KEY_STATE_OFF, lang)),
                    dndOn, density);

                // Wi-Fi
                boolean wifiOn = UltraBatterySaverManager.isWifiOn(activity);
                updateToggleStyle(btnWifi, tokens,
                    I18n.get(I18n.KEY_TOGGLE_WIFI, lang) + ": " + (wifiOn ? I18n.get(I18n.KEY_STATE_ON, lang) : I18n.get(I18n.KEY_STATE_OFF, lang)),
                    wifiOn, density);

                // Mobile Data
                boolean dataOn = UltraBatterySaverManager.isMobileDataOn(activity);
                updateToggleStyle(btnData, tokens,
                    I18n.get(I18n.KEY_TOGGLE_DATA, lang) + ": " + (dataOn ? I18n.get(I18n.KEY_STATE_ON, lang) : I18n.get(I18n.KEY_STATE_OFF, lang)),
                    dataOn, density);

                // Bluetooth
                boolean btOn = UltraBatterySaverManager.isBluetoothOn();
                updateToggleStyle(btnBluetooth, tokens,
                    I18n.get(I18n.KEY_TOGGLE_BLUETOOTH, lang) + ": " + (btOn ? I18n.get(I18n.KEY_STATE_ON, lang) : I18n.get(I18n.KEY_STATE_OFF, lang)),
                    btOn, density);

                // Torch
                boolean torchOn = UltraBatterySaverManager.isTorchOn();
                updateToggleStyle(btnTorch, tokens,
                    I18n.get(I18n.KEY_TOGGLE_TORCH, lang) + ": " + (torchOn ? I18n.get(I18n.KEY_STATE_ON, lang) : I18n.get(I18n.KEY_STATE_OFF, lang)),
                    torchOn, density);
            }
        };

        // Click listeners
        btnAirplane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = UltraBatterySaverManager.isAirplaneModeOn(activity);
                UltraBatterySaverManager.setAirplaneMode(activity, !cur);
                v.postDelayed(stateRefresher, 300);
            }
        });

        btnDnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = UltraBatterySaverManager.isDndOn(activity);
                UltraBatterySaverManager.setDnd(activity, !cur);
                v.postDelayed(stateRefresher, 200);
            }
        });

        btnWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = UltraBatterySaverManager.isWifiOn(activity);
                UltraBatterySaverManager.setWifi(activity, !cur);
                v.postDelayed(stateRefresher, 300);
            }
        });

        btnData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = UltraBatterySaverManager.isMobileDataOn(activity);
                UltraBatterySaverManager.setMobileData(activity, !cur);
                v.postDelayed(stateRefresher, 300);
            }
        });

        btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = UltraBatterySaverManager.isBluetoothOn();
                UltraBatterySaverManager.setBluetooth(!cur);
                v.postDelayed(stateRefresher, 300);
            }
        });

        btnTorch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UltraBatterySaverManager.toggleTorch(activity);
                stateRefresher.run();
            }
        });

        // Initialize state styling
        stateRefresher.run();

        // Bottom action bar: [ <-- CERRAR ] and [ AJUSTES ]
        root.addView(createVerticalSpacer(activity, dp16));
        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnClose = new Button(activity);
        btnClose.setText(I18n.get(I18n.KEY_SHADE_CLOSE, lang));
        btnClose.setTextColor(tokens.textMuted);
        btnClose.setTextSize(13);
        btnClose.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
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

        Button btnSettings = new Button(activity);
        btnSettings.setText(I18n.get(I18n.KEY_SHADE_SETTINGS, lang));
        btnSettings.setTextColor(tokens.navAccent);
        btnSettings.setTextSize(13);
        btnSettings.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnSettings.setBackground(createButtonDrawable(tokens.controlBgNormal, tokens.navAccent, (int) (1 * density), (int) (4 * density)));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(0, dp48, 1.0f);
        btnSettings.setLayoutParams(settingsLp);
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
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
            window.setWindowAnimations(android.R.style.Animation_Dialog);

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
                    window.getInsetsController().setSystemBarsAppearance(
                        tokens.isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            } catch (Throwable ignored) {}
        }

        dialog.show();
    }

    private static LinearLayout createToggleRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private static Button createToggleButton(Context context, ThemeTokens tokens, int height) {
        Button btn = new Button(context);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, height, 1.0f));
        btn.setTextSize(12);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(4, 0, 4, 0);
        return btn;
    }

    private static void updateToggleStyle(Button btn, ThemeTokens tokens, String text, boolean isActive, float density) {
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
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalBg);
        normal.setStroke(strokeWidth, strokeColor);
        normal.setCornerRadius(cornerRadius);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(strokeColor);
        pressed.setStroke(strokeWidth, strokeColor);
        pressed.setCornerRadius(cornerRadius);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}
