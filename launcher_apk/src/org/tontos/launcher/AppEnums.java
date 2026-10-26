package org.tontos.launcher;

import android.graphics.Color;
import java.util.List;
import java.util.Locale;

/**
 * Enums for TontOS Launcher themes, sections, and wizard steps.
 */
public final class AppEnums {
    private AppEnums() {}
}

/**
 * Visual detox theme presets.
 */
enum ThemePreset {
    AMOLED(
        "AMOLED",
        "AMOLED Black",
        Color.BLACK,
        Color.parseColor("#0D110E"),
        Color.parseColor("#44DD88"),
        Color.parseColor("#1B5E38"),
        Color.parseColor("#E5E7EB"),
        Color.parseColor("#9CA3AF"),
        Color.parseColor("#1B261F"),
        false,
        Color.parseColor("#0D0D0D"),
        Color.parseColor("#222222"),
        Color.parseColor("#1A1A1A"),
        Color.parseColor("#44DD88"),
        Color.parseColor("#38BDF8"),
        Color.parseColor("#0B1926")
    ),
    AMBER(
        "AMBER",
        "Amber CRT",
        Color.BLACK,
        Color.parseColor("#0F0A00"),
        Color.parseColor("#FFB000"),
        Color.parseColor("#805800"),
        Color.parseColor("#FFB000"),
        Color.parseColor("#CCA000"),
        Color.parseColor("#332300"),
        false,
        Color.parseColor("#0D0D0D"),
        Color.parseColor("#222222"),
        Color.parseColor("#1A1A1A"),
        Color.parseColor("#FFB000"),
        Color.parseColor("#FF6E40"),
        Color.parseColor("#1A0A00")
    ),
    E_INK(
        "E-INK",
        "E-Ink Paper",
        Color.parseColor("#FFFFFF"),
        Color.parseColor("#F4F4F6"),
        Color.parseColor("#000000"),
        Color.parseColor("#333333"),
        Color.parseColor("#000000"),
        Color.parseColor("#444444"),
        Color.parseColor("#777777"),
        true,
        Color.parseColor("#F4F4F6"),
        Color.parseColor("#DDDDDD"),
        Color.parseColor("#000000"),
        Color.parseColor("#FFFFFF"),
        Color.parseColor("#000000"),
        Color.parseColor("#F4F4F6")
    ),
    SLATE(
        "SLATE",
        "Slate Midnight",
        Color.parseColor("#0E141B"),
        Color.parseColor("#161F28"),
        Color.parseColor("#58A6FF"),
        Color.parseColor("#1F4A85"),
        Color.parseColor("#F0F6FC"),
        Color.parseColor("#A3B1C2"),
        Color.parseColor("#212B36"),
        false,
        Color.parseColor("#0D0D0D"),
        Color.parseColor("#222222"),
        Color.parseColor("#1A1A1A"),
        Color.parseColor("#58A6FF"),
        Color.parseColor("#F78166"),
        Color.parseColor("#261815")
    );

    private final String id;
    private final String displayName;
    public final int bg;
    public final int surface;
    public final int accent;
    public final int accentMuted;
    public final int textPrimary;
    public final int textMuted;
    public final int border;
    public final boolean isLight;
    public final int controlBgNormal;
    public final int controlBgPressed;
    public final int activeSelectionBg;
    public final int activeSelectionText;
    public final int navAccent;
    public final int navBg;

    ThemePreset(String id, String displayName, int bg, int surface, int accent,
                int accentMuted, int textPrimary, int textMuted, int border,
                boolean isLight, int controlBgNormal, int controlBgPressed,
                int activeSelectionBg, int activeSelectionText,
                int navAccent, int navBg) {
        this.id = id;
        this.displayName = displayName;
        this.bg = bg;
        this.surface = surface;
        this.accent = accent;
        this.accentMuted = accentMuted;
        this.textPrimary = textPrimary;
        this.textMuted = textMuted;
        this.border = border;
        this.isLight = isLight;
        this.controlBgNormal = controlBgNormal;
        this.controlBgPressed = controlBgPressed;
        this.activeSelectionBg = activeSelectionBg;
        this.activeSelectionText = activeSelectionText;
        this.navAccent = navAccent;
        this.navBg = navBg;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isLight() {
        return isLight;
    }

    public static ThemePreset fromId(String id) {
        if (id == null) return AMOLED;
        for (ThemePreset preset : values()) {
            if (preset.id.equalsIgnoreCase(id) || preset.name().equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return AMOLED;
    }
}

/**
 * Settings Navigation Sections.
 * Replaces integer state codes with a strongly-typed enum.
 */
enum SettingsSection {
    MENU("TontOS SETTINGS (Beta)", "System & Preferences Configuration"),
    DISPLAY("1. DISPLAY, THEMES & LANGUAGE", "Brightness, 4 visual themes, screen-time budget, language"),
    SOUND("2. SOUND & VOLUME", "Media, ring, alarm sliders & sound profiles"),
    HOME_DRAWER("3. HOME SCREEN & ALL APPS", "App sorting [^] [v], manual priority, all apps list"),
    CONNECTIVITY("4. CONNECTIVITY & RADIOS", "Wi-Fi, Bluetooth, Mobile Data, sync batching"),
    SYSTEM("5. ABOUT DEVICE & UPDATES", "Specs, release channel, backups & setup");

    private final String title;
    private final String subtitle;

    SettingsSection(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    public String getTitle() {
        return title;
    }

    public String getTitle(String langCode) {
        switch (this) {
            case MENU: return "TontOS " + I18n.get(I18n.KEY_SETTINGS, langCode).replace("[ ", "").replace(" ]", "") + " (Beta)";
            case DISPLAY: return "1. " + I18n.get(I18n.KEY_SEC_DISPLAY, langCode);
            case SOUND: return "2. " + I18n.get(I18n.KEY_SEC_SOUND, langCode);
            case HOME_DRAWER: return "3. " + I18n.get(I18n.KEY_SEC_HOME, langCode);
            case CONNECTIVITY: return "4. " + I18n.get(I18n.KEY_SEC_CONNECTIVITY, langCode);
            case SYSTEM: return "5. " + I18n.get(I18n.KEY_SEC_SYSTEM, langCode);
            default: return title;
        }
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getSubtitle(String langCode) {
        switch (this) {
            case MENU: return I18n.get(I18n.KEY_MENU_SUBTITLE, langCode);
            case DISPLAY: return I18n.get(I18n.KEY_DESC_DISPLAY, langCode);
            case SOUND: return I18n.get(I18n.KEY_DESC_SOUND, langCode);
            case HOME_DRAWER: return I18n.get(I18n.KEY_DESC_HOME, langCode);
            case CONNECTIVITY: return I18n.get(I18n.KEY_DESC_CONNECTIVITY, langCode);
            case SYSTEM: return I18n.get(I18n.KEY_DESC_SYSTEM, langCode);
            default: return subtitle;
        }
    }
}

/**
 * Nested Sub-sections for Settings to eliminate visual clutter and group options cleanly.
 */
enum SettingsSubSection {
    NONE,
    // Section 1: DISPLAY
    DISPLAY_STYLE,
    DISPLAY_LOCALE,
    DISPLAY_LOCKSCREEN,
    DISPLAY_DETOX,
    // Section 2: SOUND
    SOUND_VOLUMES,
    SOUND_EQUALIZER,
    // Section 3: HOME & APPS
    HOME_CONFIG,
    HOME_PINNED,
    HOME_DRAWER_ALL,
    // Section 4: CONNECTIVITY
    CONN_FIREWALL,
    CONN_RADIOS,
    // Section 5: SYSTEM
    SYS_INFO,
    SYS_UPDATES,
    SYS_BACKUP,
    SYS_ADVANCED
}

/**
 * Step enumeration for the First-Startup Install Wizard (OOBE).
 */
enum WizardStep {
    WELCOME(0, "Welcome"),
    LANGUAGE_REGION(1, "1. Language & Region"),
    THEME_PRESET(2, "2. Theme Preset"),
    DETOX_BUDGET(3, "3. Focus & Detox Limit"),
    SECURITY(4, "4. Device Security"),
    COMPLETE(5, "5. Setup Complete");

    private final int index;
    private final String headerTitle;

    WizardStep(int index, String headerTitle) {
        this.index = index;
        this.headerTitle = headerTitle;
    }

    public int getIndex() {
        return index;
    }

    public String getHeaderTitle() {
        return headerTitle;
    }

    public static WizardStep fromIndex(int index) {
        for (WizardStep s : values()) {
            if (s.index == index) return s;
        }
        return WELCOME;
    }
}

/**
 * Manual GitHub release channels for TontOS.
 * These links do not install or verify system updates.
 */
enum UpdateChannel {
    RELEASE("RELEASE", "Release", "https://github.com/FalseThinks/TontOS/releases/latest"),
    BETA("BETA", "Beta", "https://github.com/FalseThinks/TontOS/releases");

    private final String code;
    private final String label;
    private final String releaseUrl;

    UpdateChannel(String code, String label, String releaseUrl) {
        this.code = code;
        this.label = label;
        this.releaseUrl = releaseUrl;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }

    public static UpdateChannel fromCode(String code) {
        if (code == null) return BETA;
        for (UpdateChannel ch : values()) {
            if (ch.code.equalsIgnoreCase(code) || ch.name().equalsIgnoreCase(code)) {
                return ch;
            }
        }
        return BETA;
    }
}

/**
 * Lock Screen Clock presentation styles.
 */
enum LockClockStyle {
    THEME_DEFAULT("THEME_DEFAULT", "Theme Default"),
    BIG_MONOSPACE("BIG_MONOSPACE", "Big Monospace 24h"),
    VERTICAL_STACKED("VERTICAL_STACKED", "Vertical Stacked (HH/MM)"),
    TERMINAL_RETRO("TERMINAL_RETRO", "Terminal Retro [HH:mm:ss]"),
    COMPACT_SINGLE("COMPACT_SINGLE", "Compact Single Line"),
    MINIMAL_DATE_ONLY("MINIMAL_DATE_ONLY", "Minimal Date Only (Detox)"),
    CLOCK_OFF("CLOCK_OFF", "Clock Hidden (Zero Distraction)");

    private final String id;
    private final String label;

    LockClockStyle(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }

    public static LockClockStyle fromId(String id) {
        if (id == null) return THEME_DEFAULT;
        for (LockClockStyle style : values()) {
            if (style.id.equalsIgnoreCase(id) || style.name().equalsIgnoreCase(id)) {
                return style;
            }
        }
        return THEME_DEFAULT;
    }
}

/**
 * Lock screen quick access action shortcuts.
 */
enum LockShortcut {
    NONE("NONE", "None"),
    TORCH("TORCH", "Flashlight"),
    CAMERA("CAMERA", "Camera"),
    PHONE("PHONE", "Phone"),
    MESSAGES("MESSAGES", "Messages"),
    BROWSER("BROWSER", "FOSS Browser"),
    EMAIL("EMAIL", "Email"),
    CALCULATOR("CALCULATOR", "Calculator");

    private final String id;
    private final String label;

    LockShortcut(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }

    public static LockShortcut fromId(String id) {
        if (id == null) return NONE;
        for (LockShortcut s : values()) {
            if (s.id.equalsIgnoreCase(id) || s.name().equalsIgnoreCase(id)) {
                return s;
            }
        }
        return NONE;
    }
}

/**
 * Lock screen notification privacy policies.
 */
enum LockNotificationMode {
    DONT_SHOW("DONT_SHOW", "Do not show notifications (Pure Detox)"),
    HIDE_SENSITIVE("HIDE_SENSITIVE", "Hide sensitive content"),
    SHOW_ALL("SHOW_ALL", "Show all notifications");

    private final String id;
    private final String label;

    LockNotificationMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }

    public static LockNotificationMode fromId(String id) {
        if (id == null) return DONT_SHOW;
        for (LockNotificationMode m : values()) {
            if (m.id.equalsIgnoreCase(id) || m.name().equalsIgnoreCase(id)) {
                return m;
            }
        }
        return DONT_SHOW;
    }
}
