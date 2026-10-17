package org.tontos.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of setting explanations displayed when touching the (?) badge.
 */
public final class SettingsDescriptions {

    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();

    static {
        // Section 1: Display & Theme
        DESCRIPTIONS.put(I18n.KEY_THEME_PRESET, "Selects visual contrast palette: AMOLED Pure Black, Amber CRT Phosphor, E-Ink Monochrome Paper, or Slate Midnight.");
        DESCRIPTIONS.put(I18n.KEY_GRAYSCALE_MODE, "Forces system-wide monochromacy via hardware display daltonizer to eliminate dopamine triggers.");
        DESCRIPTIONS.put(I18n.KEY_BRIGHTNESS, "Adjusts screen backlight level manually to prevent battery drain from auto-brightness sensors.");
        DESCRIPTIONS.put(I18n.KEY_TIMEOUT, "Controls inactivity timeout before device turns off screen to save energy and discourage mindless use.");
        DESCRIPTIONS.put(I18n.KEY_DAILY_BUDGET, "Sets daily screen-time limit. When reached, home screen shows warning badge and usage alerts.");
        DESCRIPTIONS.put(I18n.KEY_LANGUAGE, "Configures operating system and launcher localization across 12 supported languages.");
        DESCRIPTIONS.put(I18n.KEY_REGION, "Sets regional date, number and carrier format settings.");
        DESCRIPTIONS.put(I18n.KEY_FONT_SIZE, "Scales user interface and monospace font sizes proportionally.");
        DESCRIPTIONS.put(I18n.KEY_BATTERY_PROTECTION, "Caps battery charge at 80% or enables bypass charging via kernel sysfs to extend battery lifespan.");
        DESCRIPTIONS.put(I18n.KEY_GLYPH_INTERFACE, "Enables Nothing Phone rear LED glyph patterns for essential silent alerts.");

        // Section 2: Sound & Volume
        DESCRIPTIONS.put(I18n.KEY_MEDIA_VOL, "Controls multimedia, music, and browser playback volume level.");
        DESCRIPTIONS.put(I18n.KEY_RING_VOL, "Adjusts incoming phone call audio ringer volume level.");
        DESCRIPTIONS.put(I18n.KEY_ALARM_VOL, "Sets morning and scheduled alarm buzzer volume level.");
        DESCRIPTIONS.put(I18n.KEY_RINGER_PROFILE, "Toggles between Normal, Vibrate, and Silent ringer modes.");
        DESCRIPTIONS.put(I18n.KEY_NOTIFICATION_DELIVERY_TITLE, "Batches alerts: delivers immediately, as scheduled digests (12:00/18:00), or starred contacts only.");
        DESCRIPTIONS.put(I18n.KEY_RINGTONE_TITLE, "Selects incoming phone call audio tone.");
        DESCRIPTIONS.put(I18n.KEY_NOTIFICATION_SOUND_TITLE, "Selects sound tone played for incoming app notifications.");
        DESCRIPTIONS.put(I18n.KEY_ALARM_SOUND_TITLE, "Selects sound played for clock alarms.");
        DESCRIPTIONS.put(I18n.KEY_EQUALIZER, "Master switch and 5-band frequency hardware audio filtering (60Hz to 14kHz).");
        DESCRIPTIONS.put("pref_eq_enabled", "Enables or disables system hardware equalizer audio processing.");
        DESCRIPTIONS.put(I18n.KEY_EQ_PRESET, "Selects pre-tuned audio profile: Flat, Bass Boost, Vocal, Treble Boost, or Rock.");

        // Section 3: Home & Drawer
        DESCRIPTIONS.put(I18n.KEY_APP_LIMIT_TITLE, "Limits max number of pinned applications on home screen to eliminate visual clutter.");
        DESCRIPTIONS.put(I18n.KEY_FRICTION_TITLE, "Introduces mindful countdown delay before launching apps to curb compulsive habits.");
        DESCRIPTIONS.put(I18n.KEY_APP_PRIVACY_GUARD, "Inspects runtime and background permissions granted to installed applications.");

        // Section 4: Connectivity
        DESCRIPTIONS.put(I18n.KEY_WIFI_RADIO, "Toggles Wi-Fi radio interface hardware state directly via system service.");
        DESCRIPTIONS.put(I18n.KEY_BT_RADIO, "Toggles Bluetooth transceiver for audio and peripherals.");
        DESCRIPTIONS.put(I18n.KEY_CELLULAR_DATA, "Enables or disables mobile internet packet data connection.");
        DESCRIPTIONS.put(I18n.KEY_NFC_RADIO, "Controls Near Field Communication radio for contactless reading and tags.");
        DESCRIPTIONS.put(I18n.KEY_SYNC_BATCHING, "Schedules background radio wakeups in intervals (15m, 30m, 60m) to save battery.");
        DESCRIPTIONS.put(I18n.KEY_BG_POLICY, "Restricts apps from executing in background: Whitelist (only allowed apps), Strict (none), or Allow.");
        DESCRIPTIONS.put(I18n.KEY_SHADE_TILES_TITLE, "Customizes which hardware toggle tiles appear in the pull-down Ultra Battery Saver shade.");
        DESCRIPTIONS.put(I18n.KEY_APP_NETWORK_FIREWALL, "Controls per-app Internet access rules via AppOps and kernel packet filters.");

        // Section 5: Device & Updates
        DESCRIPTIONS.put(I18n.KEY_UPDATE_CHANNEL, "Selects Over-The-Air system upgrade track: Release (Stable), Beta, or Nightly.");
        DESCRIPTIONS.put(I18n.KEY_CREATE_SNAPSHOT, "Creates an encrypted, tamper-evident JSON cryptographic snapshot of all launcher configurations.");
        DESCRIPTIONS.put(I18n.KEY_RESTORE_SNAPSHOT, "Restores settings and app layouts from verified cryptographic snapshot archive.");
        DESCRIPTIONS.put(I18n.KEY_RUN_WIZARD_ROW, "Reruns the initial out-of-box setup wizard to reconfigure language, theme, and detox budget.");
        DESCRIPTIONS.put(I18n.KEY_DEV_SETTINGS, "Launches Android Developer Options for USB debugging and OEM unlock.");
        DESCRIPTIONS.put(I18n.KEY_HARDWARE_TEST, "Opens hidden engineering diagnostics test screen (*#*#4636#*#*).");
    }

    private SettingsDescriptions() {}

    public static String get(String key) {
        if (key == null) return null;
        String val = DESCRIPTIONS.get(key);
        if (val != null) return val;
        // Case-insensitive or normalized check
        String clean = key.trim().toLowerCase();
        for (Map.Entry<String, String> entry : DESCRIPTIONS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) || clean.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        // Fallback default helpful text if not specifically defined
        return "Configuration setting for " + key + ". Touch to modify system preference.";
    }

    public static void showExplanationDialog(Context context, ThemeTokens tokens, String title, String explanation) {
        if (context == null || explanation == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(
            context,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );
        builder.setTitle(title);
        builder.setMessage(explanation);
        builder.setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }

        Button pBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (pBtn != null) {
            pBtn.setTextColor(tokens.accent);
            pBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        }
    }

    public static View createHelpBadge(final Context context, final ThemeTokens tokens, final String labelKey, final String fallbackTitle) {
        final String text = get(labelKey);
        if (text == null) return null;

        int pad = (int) (4 * context.getResources().getDisplayMetrics().density + 0.5f);
        TextView badge = new TextView(context);
        badge.setText(" (?)");
        badge.setTextColor(tokens.navAccent);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setPadding(pad, 0, pad, 0);
        badge.setClickable(true);
        badge.setFocusable(true);
        badge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExplanationDialog(context, tokens, fallbackTitle != null ? fallbackTitle : "Setting Information", text);
            }
        });
        return badge;
    }
}
