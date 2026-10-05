package org.tontos.launcher;

/**
 * Color tokens for UI styling.
 * Bridges ThemePreset into immutable view tokens.
 */
public final class ThemeTokens {
    public final String id;
    public final String name;
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

    public ThemeTokens(ThemePreset preset) {
        this.id = preset.getId();
        this.name = preset.getDisplayName();
        this.bg = preset.bg;
        this.surface = preset.surface;
        this.accent = preset.accent;
        this.accentMuted = preset.accentMuted;
        this.textPrimary = preset.textPrimary;
        this.textMuted = preset.textMuted;
        this.border = preset.border;
        this.isLight = preset.isLight;
        this.controlBgNormal = preset.controlBgNormal;
        this.controlBgPressed = preset.controlBgPressed;
        this.activeSelectionBg = preset.activeSelectionBg;
        this.activeSelectionText = preset.activeSelectionText;
        this.navAccent = preset.navAccent;
        this.navBg = preset.navBg;
    }

    public ThemeTokens(
        String id,
        String name,
        int bg,
        int surface,
        int accent,
        int accentMuted,
        int textPrimary,
        int textMuted,
        int border,
        boolean isLight,
        int controlBgNormal,
        int controlBgPressed,
        int activeSelectionBg,
        int activeSelectionText,
        int navAccent,
        int navBg
    ) {
        this.id = id;
        this.name = name;
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

    public boolean isLight() {
        return isLight;
    }

    public static int toGrayscale(int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int lum = Math.round(0.2126f * r + 0.7152f * g + 0.0722f * b);
        if (lum > 255) lum = 255;
        return (a << 24) | (lum << 16) | (lum << 8) | lum;
    }

    public ThemeTokens toMonochrome() {
        int monoAccent;
        int monoNavAccent;
        if (!isLight) {
            int lumAccent = Math.max(200, Math.round(0.2126f * ((accent >> 16) & 0xFF) + 0.7152f * ((accent >> 8) & 0xFF) + 0.0722f * (accent & 0xFF)));
            if (lumAccent > 255) lumAccent = 255;
            monoAccent = 0xFF000000 | (lumAccent << 16) | (lumAccent << 8) | lumAccent;

            int lumNav = Math.max(190, Math.round(0.2126f * ((navAccent >> 16) & 0xFF) + 0.7152f * ((navAccent >> 8) & 0xFF) + 0.0722f * (navAccent & 0xFF)));
            if (lumNav > 255) lumNav = 255;
            monoNavAccent = 0xFF000000 | (lumNav << 16) | (lumNav << 8) | lumNav;
        } else {
            int lumAccent = Math.min(45, Math.round(0.2126f * ((accent >> 16) & 0xFF) + 0.7152f * ((accent >> 8) & 0xFF) + 0.0722f * (accent & 0xFF)));
            monoAccent = 0xFF000000 | (lumAccent << 16) | (lumAccent << 8) | lumAccent;

            int lumNav = Math.min(55, Math.round(0.2126f * ((navAccent >> 16) & 0xFF) + 0.7152f * ((navAccent >> 8) & 0xFF) + 0.0722f * (navAccent & 0xFF)));
            monoNavAccent = 0xFF000000 | (lumNav << 16) | (lumNav << 8) | lumNav;
        }

        return new ThemeTokens(
            this.id + "_mono",
            this.name + " [Mono]",
            this.bg,
            toGrayscale(this.surface),
            monoAccent,
            toGrayscale(this.accentMuted),
            toGrayscale(this.textPrimary),
            toGrayscale(this.textMuted),
            toGrayscale(this.border),
            this.isLight,
            toGrayscale(this.controlBgNormal),
            toGrayscale(this.controlBgPressed),
            toGrayscale(this.activeSelectionBg),
            toGrayscale(this.activeSelectionText),
            monoNavAccent,
            toGrayscale(this.navBg)
        );
    }

    public static ThemeTokens get(String themeId) {
        ThemePreset preset = ThemePreset.fromId(themeId);
        return new ThemeTokens(preset);
    }

    public static ThemeTokens get(ThemePreset preset) {
        return new ThemeTokens(preset);
    }
}
