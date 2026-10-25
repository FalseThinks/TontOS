package org.tontos.launcher;

import android.app.Dialog;
import android.content.Context;
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
import android.widget.TextView;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of setting explanations displayed when touching the (?) badge.
 */
public final class SettingsDescriptions {

    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
    private static final Map<String, String> DESCRIPTIONS_ES = new HashMap<>();

    static {
        // Section 1: Display & Theme
        DESCRIPTIONS.put(I18n.KEY_THEME_PRESET, "Choose your color theme: AMOLED Pure Black, Amber Phosphor, E-Ink Paper, or Slate Midnight.");
        DESCRIPTIONS.put(I18n.KEY_GRAYSCALE_MODE, "Turns the screen black and white to reduce eye strain and screen addiction.");
        DESCRIPTIONS.put(I18n.KEY_BRIGHTNESS, "Adjusts screen brightness level.");
        DESCRIPTIONS.put(I18n.KEY_TIMEOUT, "Time before the screen turns off automatically when not in use.");
        DESCRIPTIONS.put(I18n.KEY_DAILY_BUDGET, "Set a daily screen-time limit with reminders when exceeded.");
        DESCRIPTIONS.put(I18n.KEY_LANGUAGE, "Choose system and launcher language.");
        DESCRIPTIONS.put(I18n.KEY_REGION, "Set regional format for dates and numbers.");
        DESCRIPTIONS.put(I18n.KEY_FONT_SIZE, "Adjust text size across the launcher.");
        DESCRIPTIONS.put(I18n.KEY_BATTERY_PROTECTION, "Limits charging to 80% to extend battery lifespan.");
        DESCRIPTIONS.put(I18n.KEY_GLYPH_INTERFACE, "Enables Nothing Phone rear LED light patterns for silent notifications.");

        // Section 2: Sound & Volume
        DESCRIPTIONS.put(I18n.KEY_MEDIA_VOL, "Media and music playback volume.");
        DESCRIPTIONS.put(I18n.KEY_RING_VOL, "Incoming call ringtone volume.");
        DESCRIPTIONS.put(I18n.KEY_ALARM_VOL, "Clock alarm buzzer volume.");
        DESCRIPTIONS.put(I18n.KEY_RINGER_PROFILE, "Switch between Normal, Vibrate, and Silent sound profiles.");
        DESCRIPTIONS.put(I18n.KEY_NOTIFICATION_DELIVERY_TITLE, "Batch alerts: receive right away, as daily digests (12:00/18:00), or starred contacts only.");
        DESCRIPTIONS.put(I18n.KEY_RINGTONE_TITLE, "Choose ringtone for incoming calls.");
        DESCRIPTIONS.put(I18n.KEY_NOTIFICATION_SOUND_TITLE, "Choose sound for app notifications.");
        DESCRIPTIONS.put(I18n.KEY_ALARM_SOUND_TITLE, "Choose sound for alarms.");
        DESCRIPTIONS.put(I18n.KEY_EQUALIZER, "5-band audio equalizer for sound tuning.");
        DESCRIPTIONS.put("pref_eq_enabled", "Turn the audio equalizer on or off.");
        DESCRIPTIONS.put(I18n.KEY_EQ_PRESET, "Select sound preset: Flat, Bass Boost, Vocal, Treble Boost, or Rock.");

        // Section 3: Home & Drawer
        DESCRIPTIONS.put(I18n.KEY_APP_LIMIT_TITLE, "Maximum number of pinned apps on your home screen.");
        DESCRIPTIONS.put(I18n.KEY_FRICTION_TITLE, "Adds a brief countdown pause before launching apps to prevent compulsive checking.");
        DESCRIPTIONS.put(I18n.KEY_APP_PRIVACY_GUARD, "Review and manage permissions granted to installed apps.");

        // Section 4: Connectivity
        DESCRIPTIONS.put(I18n.KEY_WIFI_RADIO, "Turn Wi-Fi connection on or off.");
        DESCRIPTIONS.put(I18n.KEY_BT_RADIO, "Turn Bluetooth on or off for headphones and accessories.");
        DESCRIPTIONS.put(I18n.KEY_CELLULAR_DATA, "Turn mobile data connection on or off.");
        DESCRIPTIONS.put(I18n.KEY_NFC_RADIO, "Turn NFC on or off for contactless tags.");
        DESCRIPTIONS.put(I18n.KEY_SYNC_BATCHING, "Group background syncs into intervals (15m, 30m, 60m) to save battery.");
        DESCRIPTIONS.put(I18n.KEY_BG_POLICY, "Control background app activity: Whitelist (allowed apps only), Strict (none), or Allow.");
        DESCRIPTIONS.put(I18n.KEY_SHADE_TILES_TITLE, "Choose quick toggle buttons shown in the top pull-down panel.");
        DESCRIPTIONS.put(I18n.KEY_APP_NETWORK_FIREWALL, "Block internet access for individual apps.");

        // Section 5: Device & Updates
        DESCRIPTIONS.put(I18n.KEY_UPDATE_CHANNEL, "Choose system update channel: Stable, Beta, or Nightly.");
        DESCRIPTIONS.put(I18n.KEY_CREATE_SNAPSHOT, "Create a backup of your settings and home screen layout.");
        DESCRIPTIONS.put(I18n.KEY_RESTORE_SNAPSHOT, "Restore settings and home screen layout from a backup.");
        DESCRIPTIONS.put(I18n.KEY_RUN_WIZARD_ROW, "Run the initial setup wizard again.");
        DESCRIPTIONS.put(I18n.KEY_KEYBOARD, "Choose the active keyboard.");
        DESCRIPTIONS.put(I18n.KEY_BOOTLOADER_LOCK_STATUS, "Shows bootloader lock status.");
        DESCRIPTIONS.put(I18n.KEY_DEV_SETTINGS, "Open Android Developer Options.");
        DESCRIPTIONS.put(I18n.KEY_HARDWARE_TEST, "Open phone testing menu (*#*#4636#*#*).");

        // Spanish descriptions
        DESCRIPTIONS_ES.put(I18n.KEY_THEME_PRESET, "Elige el tema de color: AMOLED Negro Puro, Ámbar Fósforo, Papel E-Ink o Azul Medianoche.");
        DESCRIPTIONS_ES.put(I18n.KEY_GRAYSCALE_MODE, "Pone la pantalla en blanco y negro para reducir distracciones y fatiga visual.");
        DESCRIPTIONS_ES.put(I18n.KEY_BRIGHTNESS, "Ajusta el nivel de brillo de la pantalla.");
        DESCRIPTIONS_ES.put(I18n.KEY_TIMEOUT, "Tiempo antes de que la pantalla se apague sola por inactividad.");
        DESCRIPTIONS_ES.put(I18n.KEY_DAILY_BUDGET, "Fija un límite de uso diario con recordatorios al superarlo.");
        DESCRIPTIONS_ES.put(I18n.KEY_LANGUAGE, "Configura el idioma del sistema y del launcher.");
        DESCRIPTIONS_ES.put(I18n.KEY_REGION, "Formato regional para fechas y números.");
        DESCRIPTIONS_ES.put(I18n.KEY_FONT_SIZE, "Ajusta el tamaño del texto en el launcher.");
        DESCRIPTIONS_ES.put(I18n.KEY_BATTERY_PROTECTION, "Limita la carga al 80% para cuidar la salud de la batería.");
        DESCRIPTIONS_ES.put(I18n.KEY_GLYPH_INTERFACE, "Activa las luces LED traseras de Nothing Phone para notificaciones silenciosas.");

        DESCRIPTIONS_ES.put(I18n.KEY_MEDIA_VOL, "Volumen para música, vídeos y juegos.");
        DESCRIPTIONS_ES.put(I18n.KEY_RING_VOL, "Volumen del timbre para llamadas entrantes.");
        DESCRIPTIONS_ES.put(I18n.KEY_ALARM_VOL, "Volumen de la alarma del despertador.");
        DESCRIPTIONS_ES.put(I18n.KEY_RINGER_PROFILE, "Cambia entre modo Sonido, Vibración y Silencio.");
        DESCRIPTIONS_ES.put(I18n.KEY_NOTIFICATION_DELIVERY_TITLE, "Organiza avisos: al instante, resúmenes diarios (12:00/18:00) o solo contactos destacados.");
        DESCRIPTIONS_ES.put(I18n.KEY_RINGTONE_TITLE, "Elige el tono de llamada.");
        DESCRIPTIONS_ES.put(I18n.KEY_NOTIFICATION_SOUND_TITLE, "Elige el sonido de las notificaciones.");
        DESCRIPTIONS_ES.put(I18n.KEY_ALARM_SOUND_TITLE, "Elige el sonido de la alarma.");
        DESCRIPTIONS_ES.put(I18n.KEY_EQUALIZER, "Ecualizador de audio de 5 bandas.");
        DESCRIPTIONS_ES.put("pref_eq_enabled", "Activa o desactiva el ecualizador de audio.");
        DESCRIPTIONS_ES.put(I18n.KEY_EQ_PRESET, "Elige un perfil de sonido: Plano, Graves, Vocal, Agudos o Rock.");

        DESCRIPTIONS_ES.put(I18n.KEY_APP_LIMIT_TITLE, "Número máximo de aplicaciones fijadas en la pantalla principal.");
        DESCRIPTIONS_ES.put(I18n.KEY_FRICTION_TITLE, "Añade una pausa de unos segundos antes de abrir apps para evitar su uso impulsivo.");
        DESCRIPTIONS_ES.put(I18n.KEY_APP_PRIVACY_GUARD, "Revisa y administra los permisos de cada aplicación instalada.");

        DESCRIPTIONS_ES.put(I18n.KEY_WIFI_RADIO, "Activa o desactiva la conexión Wi-Fi.");
        DESCRIPTIONS_ES.put(I18n.KEY_BT_RADIO, "Activa o desactiva Bluetooth para auriculares y accesorios.");
        DESCRIPTIONS_ES.put(I18n.KEY_CELLULAR_DATA, "Activa o desactiva los datos móviles de internet.");
        DESCRIPTIONS_ES.put(I18n.KEY_NFC_RADIO, "Activa o desactiva la conexión NFC.");
        DESCRIPTIONS_ES.put(I18n.KEY_SYNC_BATCHING, "Agrupa sincronizaciones periódicas (15m, 30m, 60m) para ahorrar batería.");
        DESCRIPTIONS_ES.put(I18n.KEY_BG_POLICY, "Controla qué apps pueden funcionar en segundo plano: Solo permitidas, Ninguna o Todas.");
        DESCRIPTIONS_ES.put(I18n.KEY_SHADE_TILES_TITLE, "Elige qué botones rápidos aparecen en el panel superior.");
        DESCRIPTIONS_ES.put(I18n.KEY_APP_NETWORK_FIREWALL, "Bloquea el acceso a Internet de aplicaciones individuales.");

        DESCRIPTIONS_ES.put(I18n.KEY_UPDATE_CHANNEL, "Canal de actualizaciones del sistema: Estable, Beta o Nightly.");
        DESCRIPTIONS_ES.put(I18n.KEY_CREATE_SNAPSHOT, "Crea una copia de seguridad con tus ajustes y aplicaciones.");
        DESCRIPTIONS_ES.put(I18n.KEY_RESTORE_SNAPSHOT, "Restaura tus ajustes desde una copia de seguridad.");
        DESCRIPTIONS_ES.put(I18n.KEY_RUN_WIZARD_ROW, "Vuelve a abrir el asistente de configuración inicial.");
        DESCRIPTIONS_ES.put(I18n.KEY_KEYBOARD, "Elige el teclado en pantalla predeterminado.");
        DESCRIPTIONS_ES.put(I18n.KEY_BOOTLOADER_LOCK_STATUS, "Muestra el estado de bloqueo del bootloader.");
        DESCRIPTIONS_ES.put(I18n.KEY_DEV_SETTINGS, "Abre las Opciones de Desarrollador de Android.");
        DESCRIPTIONS_ES.put(I18n.KEY_HARDWARE_TEST, "Abre el menú de pruebas del teléfono (*#*#4636#*#*).");
    }

    private SettingsDescriptions() {}

    public static String get(String key) {
        return get(key, "en");
    }

    public static String get(String key, String langCode) {
        if (key == null) return null;
        if (langCode != null && langCode.toLowerCase(Locale.ROOT).startsWith("es")) {
            String esVal = DESCRIPTIONS_ES.get(key);
            if (esVal != null) return esVal;
            String clean = key.trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : DESCRIPTIONS_ES.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key) || clean.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    return entry.getValue();
                }
            }
        }
        String val = DESCRIPTIONS.get(key);
        if (val != null) return val;
        // Case-insensitive or normalized check
        String clean = key.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : DESCRIPTIONS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) || clean.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        // Fallback default helpful text if not specifically defined
        return (langCode != null && langCode.startsWith("es"))
            ? "Ajuste de configuración para " + key + ". Toque para modificar."
            : "Configuration setting for " + key + ". Touch to modify system preference.";
    }

    public static void showExplanationDialog(Context context, ThemeTokens tokens, String title, String explanation) {
        if (context == null || explanation == null) return;
        String langCode = "en";
        try {
            langCode = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}

        final AmoledDialog dlg = new AmoledDialog(context, tokens, title != null ? title.toUpperCase(Locale.ROOT) : "INFO");
        dlg.setCancelable(true);

        float density = context.getResources().getDisplayMetrics().density;
        TextView msgView = new TextView(context);
        msgView.setText(explanation);
        msgView.setTextColor(tokens.textPrimary);
        msgView.setTextSize(14f);
        msgView.setTypeface(Typeface.MONOSPACE);
        msgView.setLineSpacing(3, 1.25f);
        msgView.setPadding(0, (int) (12 * density), 0, (int) (20 * density));
        dlg.getContentContainer().addView(msgView);

        dlg.addActionButton(I18n.get(I18n.KEY_DONE, langCode), tokens.navAccent, tokens.border, true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });

        dlg.show();
    }

    public static View createHelpBadge(final Context context, final ThemeTokens tokens, final String labelKey, final String fallbackTitle) {
        String langCode = "en";
        try {
            langCode = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}
        final String text = get(labelKey, langCode);
        if (text == null) return null;

        int pad = (int) (4 * context.getResources().getDisplayMetrics().density + 0.5f);
        TextView badge = new TextView(context);
        badge.setText(" [?]");
        badge.setTextColor(tokens.navAccent);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setSingleLine(true);
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
