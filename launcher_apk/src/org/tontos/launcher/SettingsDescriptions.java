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
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.WindowManager;
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
        DESCRIPTIONS.put(I18n.KEY_REFRESH_RATE_30, "Configure the system frame rate cap: 30 FPS for optimal battery life, 60 FPS for standard smoothness, or 120 FPS if your display panel supports it.");
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
        DESCRIPTIONS_ES.put(I18n.KEY_REFRESH_RATE_30, "Configura el límite de fotogramas del sistema: 30 FPS para máximo ahorro de batería, 60 FPS para fluidez estándar o 120 FPS si la pantalla lo admite.");
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

        // Ultra Battery Saver
        DESCRIPTIONS.put("ultra_battery_saver", "Emergency battery conservation: limits CPU/GPU clocks, pauses background sync, and suspends animations.");
        DESCRIPTIONS_ES.put("ultra_battery_saver", "Modo de emergencia para estirar la batería al máximo: reduce frecuencias de CPU/GPU, suspende animaciones y restringe sincronizaciones en segundo plano.");

        // Lockscreen settings
        DESCRIPTIONS.put(I18n.KEY_LOCKSCREEN_ENABLE, "Enable or disable TontOS minimalist lock screen.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCKSCREEN_ENABLE, "Activa o desactiva la pantalla de bloqueo minimalista de TontOS.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_CLOCK_STYLE, "Select visual clock style on lock screen: Big, Stacked, Terminal, or Compact.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_CLOCK_STYLE, "Estilo del reloj en la pantalla de bloqueo: Grande, Apilado, Terminal o Compacto.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_THEME_OVERRIDE, "Apply a specific visual theme exclusively to the lock screen.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_THEME_OVERRIDE, "Aplica un tema de color independiente para la pantalla de bloqueo.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_NOTIF_MODE, "Control notification visibility on lock screen: None, Hide Content, or Show All.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_NOTIF_MODE, "Visibilidad de notificaciones en la pantalla de bloqueo: Ocultar todas, Ocultar contenido o Mostrar todas.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_SHOW_DETOX, "Show daily screen time budget and detox status on the lock screen.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_SHOW_DETOX, "Muestra el contador de tiempo de uso diario y presupuesto digital en la pantalla de bloqueo.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_SHOW_BATTERY, "Show battery percentage and charging status on the lock screen.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_SHOW_BATTERY, "Muestra el porcentaje y estado de carga de la batería en la pantalla de bloqueo.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_OWNER_INFO, "Display emergency owner contact message on the lock screen.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_OWNER_INFO, "Mensaje de contacto o emergencia visible en la pantalla de bloqueo.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_SECURITY_TYPE, "Choose lock security method: None, Swipe, PIN, Pattern, or Password.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_SECURITY_TYPE, "Método de seguridad de bloqueo: Ninguno, Deslizar, PIN, Patrón o Contraseña.");
        DESCRIPTIONS.put(I18n.KEY_LOCK_FINGERPRINT_TOGGLE, "Enable biometric fingerprint unlock sensor.");
        DESCRIPTIONS_ES.put(I18n.KEY_LOCK_FINGERPRINT_TOGGLE, "Permite desbloquear usando el sensor biométrico de huellas dactilares.");

        // Device hardware specs
        DESCRIPTIONS.put("device_model", "Hardware device model and manufacturer designation.");
        DESCRIPTIONS_ES.put("device_model", "Identificador del modelo y fabricante del dispositivo.");
        DESCRIPTIONS.put("android_version", "Underlying Android operating system version and API level.");
        DESCRIPTIONS_ES.put("android_version", "Versión del sistema operativo Android y nivel de API (Android 17 / API 37).");
        DESCRIPTIONS.put("os_version", "Installed version and release channel of TontOS.");
        DESCRIPTIONS_ES.put("os_version", "Versión de la distribución minimalista TontOS instalada.");
        DESCRIPTIONS.put("soc_platform", "Central processing unit chipset and system hardware architecture.");
        DESCRIPTIONS_ES.put("soc_platform", "Chipset del procesador y arquitectura de hardware del sistema.");
        DESCRIPTIONS.put("kernel_version", "Linux kernel release managing hardware drivers and low-level security.");
        DESCRIPTIONS_ES.put("kernel_version", "Versión del núcleo Linux que administra el hardware y aislamiento de procesos.");
        DESCRIPTIONS.put("build_id", "Official cryptographic system build signature and release fingerprint.");
        DESCRIPTIONS_ES.put("build_id", "Identificador de compilación oficial del sistema operativo.");
        DESCRIPTIONS.put("ram_specs", "Total physical system memory and available RAM capacity.");
        DESCRIPTIONS_ES.put("ram_specs", "Capacidad total de memoria RAM física y espacio disponible en tiempo real.");
    }

    private SettingsDescriptions() {}

    public static String get(String key) {
        return get(key, "en");
    }

    public static String get(String key, String langCode) {
        if (key == null) return null;
        boolean isEs = langCode != null && langCode.toLowerCase(Locale.ROOT).startsWith("es");
        String norm = key.replace("===", "").trim().toLowerCase(Locale.ROOT);

        // Fast keyword / semantic matching for localized strings
        if (norm.contains("ultra") && norm.contains("bater")) {
            return isEs ? DESCRIPTIONS_ES.get("ultra_battery_saver") : DESCRIPTIONS.get("ultra_battery_saver");
        }
        if (norm.contains("tema") || norm.contains("theme")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_THEME_PRESET) : DESCRIPTIONS.get(I18n.KEY_THEME_PRESET);
        }
        if (norm.contains("grises") || norm.contains("grayscale") || norm.contains("monochrome")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_GRAYSCALE_MODE) : DESCRIPTIONS.get(I18n.KEY_GRAYSCALE_MODE);
        }
        if (norm.contains("brillo") || norm.contains("brightness")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_BRIGHTNESS) : DESCRIPTIONS.get(I18n.KEY_BRIGHTNESS);
        }
        if (norm.contains("fuente") || norm.contains("font")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_FONT_SIZE) : DESCRIPTIONS.get(I18n.KEY_FONT_SIZE);
        }
        if (norm.contains("espera") || norm.contains("timeout")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_TIMEOUT) : DESCRIPTIONS.get(I18n.KEY_TIMEOUT);
        }
        if (norm.contains("modelo") || norm.contains("model")) {
            return isEs ? DESCRIPTIONS_ES.get("device_model") : DESCRIPTIONS.get("device_model");
        }
        if (norm.contains("versión de android") || norm.contains("android version")) {
            return isEs ? DESCRIPTIONS_ES.get("android_version") : DESCRIPTIONS.get("android_version");
        }
        if (norm.contains("sistema operativo") || norm.contains("operating system")) {
            return isEs ? DESCRIPTIONS_ES.get("os_version") : DESCRIPTIONS.get("os_version");
        }
        if (norm.contains("soc") || norm.contains("hardware")) {
            return isEs ? DESCRIPTIONS_ES.get("soc_platform") : DESCRIPTIONS.get("soc_platform");
        }
        if (norm.contains("kernel")) {
            return isEs ? DESCRIPTIONS_ES.get("kernel_version") : DESCRIPTIONS.get("kernel_version");
        }
        if (norm.contains("compilación") || norm.contains("build")) {
            return isEs ? DESCRIPTIONS_ES.get("build_id") : DESCRIPTIONS.get("build_id");
        }
        if (norm.contains("ram") || norm.contains("memoria") || norm.contains("memory")) {
            return isEs ? DESCRIPTIONS_ES.get("ram_specs") : DESCRIPTIONS.get("ram_specs");
        }
        if (norm.contains("bootloader")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_BOOTLOADER_LOCK_STATUS) : DESCRIPTIONS.get(I18n.KEY_BOOTLOADER_LOCK_STATUS);
        }
        if (norm.contains("idioma") || norm.contains("language")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_LANGUAGE) : DESCRIPTIONS.get(I18n.KEY_LANGUAGE);
        }
        if (norm.contains("región") || norm.contains("region")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_REGION) : DESCRIPTIONS.get(I18n.KEY_REGION);
        }
        if (norm.contains("presupuesto") || norm.contains("budget") || norm.contains("detox")) {
            return isEs ? DESCRIPTIONS_ES.get(I18n.KEY_DAILY_BUDGET) : DESCRIPTIONS.get(I18n.KEY_DAILY_BUDGET);
        }

        if (isEs) {
            String esVal = DESCRIPTIONS_ES.get(key);
            if (esVal != null) return esVal;
            for (Map.Entry<String, String> entry : DESCRIPTIONS_ES.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key) || norm.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    return entry.getValue();
                }
            }
        }
        String val = DESCRIPTIONS.get(key);
        if (val != null) return val;
        for (Map.Entry<String, String> entry : DESCRIPTIONS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) || norm.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }

        return isEs
            ? "Ajuste de configuración del sistema para " + key + "."
            : "System configuration preference for " + key + ".";
    }

    public static void showExplanationDialog(Context context, ThemeTokens tokens, String title, String explanation) {
        if (context == null || explanation == null) return;
        String langCode = "en";
        try {
            langCode = MainActivity.getSafeSharedPreferences(context, MainActivity.PREFS_NAME).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}

        final Dialog dlg = new Dialog(context);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setCancelable(true);
        dlg.setCanceledOnTouchOutside(true);

        Window win = dlg.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(0.70f);
        }

        float density = context.getResources().getDisplayMetrics().density;
        int screenWidthPx = context.getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = Math.min((int) (screenWidthPx * 0.90f), (int) (440 * density));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (20 * density), (int) (18 * density), (int) (20 * density), (int) (18 * density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setStroke((int) (1.5f * density), tokens != null ? tokens.border : Color.DKGRAY);
        bg.setCornerRadius(8 * density);
        card.setBackground(bg);

        // Header Title
        TextView titleView = new TextView(context);
        String cleanTitle = title != null ? title.replace("===", "").trim().toUpperCase(Locale.ROOT) : "INFO";
        titleView.setText("=== " + cleanTitle + " ===");
        titleView.setTextColor(tokens != null ? tokens.navAccent : Color.parseColor("#4DA6FF"));
        titleView.setTextSize(14f);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, (int) (8 * density));
        card.addView(titleView);

        // Header Divider
        View divider = new View(context);
        divider.setBackgroundColor(tokens != null ? tokens.border : Color.DKGRAY);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
        divLp.setMargins(0, 0, 0, (int) (14 * density));
        divider.setLayoutParams(divLp);
        card.addView(divider);

        // Scrollable message body
        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView msgView = new TextView(context);
        msgView.setText(explanation);
        msgView.setTextColor(tokens != null ? tokens.textPrimary : Color.WHITE);
        msgView.setTextSize(13.5f);
        msgView.setTypeface(Typeface.MONOSPACE);
        msgView.setLineSpacing(3, 1.25f);
        scroll.addView(msgView);
        card.addView(scroll);

        // Interactive dismiss button
        TextView doneBtn = new TextView(context);
        doneBtn.setText(SettingsRowFactory.ensureBrackets(I18n.get(I18n.KEY_DONE, langCode)).toUpperCase(Locale.ROOT));
        doneBtn.setTextColor(tokens != null ? tokens.navAccent : Color.parseColor("#4DA6FF"));
        doneBtn.setTextSize(13f);
        doneBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        doneBtn.setGravity(Gravity.CENTER);
        int padV = (int) (10 * density);
        doneBtn.setPadding(0, padV, 0, padV);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, (int) (16 * density), 0, 0);
        doneBtn.setLayoutParams(btnLp);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(tokens != null ? tokens.controlBgNormal : Color.parseColor("#1A1A1A"));
        btnBg.setStroke((int) (1 * density), tokens != null ? tokens.border : Color.DKGRAY);
        btnBg.setCornerRadius(4 * density);
        doneBtn.setBackground(btnBg);

        doneBtn.setClickable(true);
        doneBtn.setFocusable(true);
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        card.addView(doneBtn);

        dlg.setContentView(card, new ViewGroup.LayoutParams(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        dlg.show();
    }

    public static boolean isFpsSetting(String labelKey, String fallbackTitle) {
        if (I18n.KEY_REFRESH_RATE_30.equals(labelKey)) return true;
        String lk = labelKey != null ? labelKey.toUpperCase(Locale.ROOT) : "";
        String ft = fallbackTitle != null ? fallbackTitle.toUpperCase(Locale.ROOT) : "";
        return lk.contains("FPS") || ft.contains("FPS") || lk.contains("REFRESH") || ft.contains("REFRESH")
            || lk.contains("CUADROS") || ft.contains("CUADROS") || lk.contains("REFRESCO") || ft.contains("REFRESCO");
    }

    public static void showFpsCustomDialog(final Context context, final ThemeTokens tokens, final Runnable onFpsChanged) {
        if (context == null) return;
        String langCode = "en";
        try {
            langCode = MainActivity.getSafeSharedPreferences(context, MainActivity.PREFS_NAME)
                .getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
        } catch (Throwable ignored) {}

        final boolean isEs = langCode != null && langCode.toLowerCase(Locale.ROOT).startsWith("es");
        final boolean has120 = SettingsActivity.is120HzSupported(context);

        final int[] curFps = new int[1];
        try {
            curFps[0] = MainActivity.getSafeSharedPreferences(context, MainActivity.PREFS_NAME)
                .getInt("pref_target_fps", 60);
        } catch (Throwable ignored) {
            curFps[0] = 60;
        }

        String title = isEs ? "=== TASA DE REFRESCO ===" : "=== DISPLAY REFRESH RATE ===";
        final AmoledDialog dlg = new AmoledDialog(context, tokens, title);
        dlg.setCancelable(true);

        final float density = context.getResources().getDisplayMetrics().density;
        final LinearLayout content = dlg.getContentContainer();

        // Subtitle / Intro
        TextView introView = new TextView(context);
        introView.setText(isEs
            ? "Frecuencia de actualización y cuadros por segundo de la pantalla."
            : "Screen refresh rate and frames-per-second configuration.");
        introView.setTextColor(tokens.textPrimary);
        introView.setTextSize(13f);
        introView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        introView.setPadding(0, (int) (6 * density), 0, (int) (10 * density));
        content.addView(introView);

        // Hardware panel status card
        final TextView hwStatus = new TextView(context);
        final Runnable updateStatus = new Runnable() {
            @Override
            public void run() {
                hwStatus.setText(isEs
                    ? ("• PANEL FÍSICO: " + (has120 ? "Soporta hasta 120 Hz" : "Panel estándar 60 Hz")
                       + "\n• LÍMITE ACTIVO: [ " + curFps[0] + " FPS ]")
                    : ("• HARDWARE PANEL: " + (has120 ? "Supports up to 120 Hz" : "Standard 60 Hz panel")
                       + "\n• ACTIVE CAP: [ " + curFps[0] + " FPS ]"));
            }
        };
        updateStatus.run();
        hwStatus.setTextColor(tokens.navAccent);
        hwStatus.setTextSize(12f);
        hwStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hwStatus.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));
        GradientDrawable hwBg = new GradientDrawable();
        hwBg.setColor(tokens.surface);
        hwBg.setStroke((int) (1 * density), tokens.border);
        hwBg.setCornerRadius(4 * density);
        hwStatus.setBackground(hwBg);
        content.addView(hwStatus);

        // Selector buttons container inside dialog
        LinearLayout selectorContainer = new LinearLayout(context);
        selectorContainer.setOrientation(LinearLayout.VERTICAL);
        selectorContainer.setPadding(0, (int) (12 * density), 0, (int) (8 * density));

        int[] fpsOptions = has120 ? new int[]{30, 60, 120} : new int[]{30, 60};
        final TextView[] optionViews = new TextView[fpsOptions.length];

        final Runnable refreshButtonStyles = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < optionViews.length; i++) {
                    int fps = fpsOptions[i];
                    boolean selected = (fps == curFps[0]);
                    TextView btn = optionViews[i];
                    btn.setText(getFpsButtonLabel(fps, isEs, selected));
                    btn.setTextColor(tokens.navAccent);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(selected ? tokens.activeSelectionBg : tokens.controlBgNormal);
                    bg.setStroke((int) ((selected ? 2 : 1) * density), selected ? tokens.navAccent : tokens.border);
                    bg.setCornerRadius(4 * density);
                    btn.setBackground(bg);
                }
            }
        };

        for (int i = 0; i < fpsOptions.length; i++) {
            final int fps = fpsOptions[i];
            final TextView optBtn = new TextView(context);
            optBtn.setTextSize(13f);
            optBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            optBtn.setGravity(Gravity.CENTER_VERTICAL);
            optBtn.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
            LinearLayout.LayoutParams optLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            optLp.setMargins(0, (int) (4 * density), 0, (int) (4 * density));
            optBtn.setLayoutParams(optLp);
            optBtn.setClickable(true);
            optBtn.setFocusable(true);
            optBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    curFps[0] = fps;
                    try {
                        MainActivity.getSafeSharedPreferences(context, MainActivity.PREFS_NAME).edit()
                            .putInt("pref_target_fps", fps)
                            .putBoolean("pref_refresh_rate_30", fps == 30)
                            .apply();
                    } catch (Throwable ignored) {}
                    SettingsActivity.applyRefreshRateCap(context, fps);
                    updateStatus.run();
                    refreshButtonStyles.run();
                    if (onFpsChanged != null) {
                        onFpsChanged.run();
                    }
                }
            });
            optionViews[i] = optBtn;
            selectorContainer.addView(optBtn);
        }
        refreshButtonStyles.run();
        content.addView(selectorContainer);

        // Explanatory breakdown text
        TextView detailsView = new TextView(context);
        detailsView.setText(isEs
            ? "DETALLES DE RENDIMIENTO:\n"
              + "• 30 FPS: Máximo ahorro energético. Reduce un ~35% el consumo de pantalla y GPU. Modo recomendado para digital detox.\n"
              + "• 60 FPS: Fluidez estándar de Android para uso cotidiano equilibrado.\n"
              + "• 120 FPS: Suavidad visual extrema para animaciones y desplazamientos táctiles (requiere soporte de pantalla)."
            : "PERFORMANCE DETAILS:\n"
              + "• 30 FPS: Maximum battery conservation. Cuts ~35% display & GPU power draw. Recommended for digital detox.\n"
              + "• 60 FPS: Standard Android smoothness for balanced daily responsiveness.\n"
              + "• 120 FPS: High-refresh ultra smoothness for fluid tactile scrolling (requires display hardware support).");
        detailsView.setTextColor(tokens.textMuted);
        detailsView.setTextSize(11.5f);
        detailsView.setTypeface(Typeface.MONOSPACE);
        detailsView.setLineSpacing(2, 1.2f);
        detailsView.setPadding(0, (int) (8 * density), 0, (int) (16 * density));
        content.addView(detailsView);

        dlg.addActionButton(I18n.get(I18n.KEY_DONE, langCode), tokens.navAccent, tokens.border, true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });

        dlg.show();
    }

    private static String getFpsButtonLabel(int fps, boolean isEs, boolean selected) {
        String mark = selected ? "● " : "○ ";
        if (fps == 30) {
            return mark + "[ 30 FPS ]  " + (isEs ? "Ahorro de batería" : "Battery Saver");
        } else if (fps == 60) {
            return mark + "[ 60 FPS ]  " + (isEs ? "Estándar equilibrado" : "Standard Balanced");
        } else if (fps == 120) {
            return mark + "[ 120 FPS ] " + (isEs ? "Alta fluidez" : "High Fluidity");
        }
        return mark + "[ " + fps + " FPS ]";
    }

    public static View createHelpBadge(final Context context, final ThemeTokens tokens, final String labelKey, final String fallbackTitle) {
        return createHelpBadge(context, tokens, labelKey, fallbackTitle, null);
    }

    public static View createHelpBadge(final Context context, final ThemeTokens tokens, final String labelKey, final String fallbackTitle, final Runnable onCustomAction) {
        if (isFpsSetting(labelKey, fallbackTitle)) {
            return null; // Eliminar [?] de los ajustes de FPS
        }
        String langCode = "en";
        try {
            langCode = MainActivity.getSafeSharedPreferences(context, MainActivity.PREFS_NAME).getString(MainActivity.KEY_SYSTEM_LANGUAGE, "en");
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
                if (isFpsSetting(labelKey, fallbackTitle)) {
                    Runnable cb = onCustomAction;
                    if (cb == null && context instanceof SettingsActivity) {
                        cb = new Runnable() {
                            @Override
                            public void run() {
                                ((SettingsActivity) context).buildSettingsUI();
                            }
                        };
                    }
                    showFpsCustomDialog(context, tokens, cb);
                    return;
                }
                showExplanationDialog(context, tokens, fallbackTitle != null ? fallbackTitle : "Setting Information", text);
            }
        });
        return badge;
    }
}
