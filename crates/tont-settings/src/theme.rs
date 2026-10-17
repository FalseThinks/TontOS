//! TontOS Minimalist Theme Engine Models
//! Supports 4 zero/low-power visual profiles with subpixel-efficient color tokens.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum ThemePreset {
    #[default]
    AmoledBlack,
    AmberCrt,
    EinkMonochrome,
    SlateMidnight,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ThemeTokens {
    pub id: String,
    pub name: String,
    pub bg: String,
    pub surface: String,
    pub accent: String,
    pub accent_muted: String,
    pub text_primary: String,
    pub text_muted: String,
    pub border: String,
    pub nav_accent: String,
    pub nav_bg: String,
}

impl ThemePreset {
    pub fn tokens(&self) -> ThemeTokens {
        match self {
            Self::AmoledBlack => ThemeTokens {
                id: "AMOLED".into(),
                name: "AMOLED Black".into(),
                bg: "#000000".into(),
                surface: "#0D110E".into(),
                accent: "#44DD88".into(),
                accent_muted: "#1B5E38".into(),
                text_primary: "#E5E7EB".into(),
                text_muted: "#6B7280".into(),
                border: "#1B261F".into(),
                nav_accent: "#38BDF8".into(),
                nav_bg: "#0B1926".into(),
            },
            Self::AmberCrt => ThemeTokens {
                id: "AMBER".into(),
                name: "Amber CRT".into(),
                bg: "#000000".into(),
                surface: "#0F0A00".into(),
                accent: "#FFB000".into(),
                accent_muted: "#805800".into(),
                text_primary: "#FFB000".into(),
                text_muted: "#996A00".into(),
                border: "#332300".into(),
                nav_accent: "#FF6E40".into(),
                nav_bg: "#1A0A00".into(),
            },
            Self::EinkMonochrome => ThemeTokens {
                id: "E-INK".into(),
                name: "E-Ink Paper".into(),
                bg: "#FFFFFF".into(),
                surface: "#F4F4F6".into(),
                accent: "#000000".into(),
                accent_muted: "#333333".into(),
                text_primary: "#000000".into(),
                text_muted: "#555555".into(),
                border: "#777777".into(),
                nav_accent: "#1D4ED8".into(),
                nav_bg: "#EFF6FF".into(),
            },
            Self::SlateMidnight => ThemeTokens {
                id: "SLATE".into(),
                name: "Slate Midnight".into(),
                bg: "#0E141B".into(),
                surface: "#161F28".into(),
                accent: "#58A6FF".into(),
                accent_muted: "#1F4A85".into(),
                text_primary: "#F0F6FC".into(),
                text_muted: "#8B949E".into(),
                border: "#212B36".into(),
                nav_accent: "#F78166".into(),
                nav_bg: "#261815".into(),
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_theme_is_amoled_black() {
        let preset = ThemePreset::default();
        assert_eq!(preset, ThemePreset::AmoledBlack);
        let tokens = preset.tokens();
        assert_eq!(tokens.bg, "#000000");
        assert_eq!(tokens.accent, "#44DD88");
        assert_eq!(tokens.nav_accent, "#38BDF8");
        assert_eq!(tokens.nav_bg, "#0B1926");
    }

    #[test]
    fn test_amber_zero_blue_emission_tokens() {
        let preset = ThemePreset::AmberCrt;
        let tokens = preset.tokens();
        assert_eq!(tokens.bg, "#000000");
        assert_eq!(tokens.accent, "#FFB000");
        assert_eq!(tokens.text_primary, "#FFB000");
        assert_eq!(tokens.nav_accent, "#FF6E40");
        assert_eq!(tokens.nav_bg, "#1A0A00");
    }

    #[test]
    fn test_theme_preset_serialization() {
        let preset = ThemePreset::SlateMidnight;
        let json = serde_json::to_string(&preset).unwrap();
        assert_eq!(json, "\"slate_midnight\"");

        let deserialized: ThemePreset = serde_json::from_str(&json).unwrap();
        assert_eq!(deserialized, ThemePreset::SlateMidnight);
    }

    #[test]
    fn test_eink_paper_contrast_tokens() {
        let preset = ThemePreset::EinkMonochrome;
        let tokens = preset.tokens();
        assert_eq!(tokens.bg, "#FFFFFF");
        assert_eq!(tokens.surface, "#F4F4F6");
        assert_eq!(tokens.accent, "#000000");
        assert_eq!(tokens.accent_muted, "#333333");
        assert_eq!(tokens.text_primary, "#000000");
        assert_eq!(tokens.text_muted, "#555555");
        assert_eq!(tokens.border, "#777777");
        assert_eq!(tokens.nav_accent, "#1D4ED8");
        assert_eq!(tokens.nav_bg, "#EFF6FF");
    }

    #[test]
    fn test_all_themes_have_valid_hex_colors() {
        let presets = [
            ThemePreset::AmoledBlack,
            ThemePreset::AmberCrt,
            ThemePreset::EinkMonochrome,
            ThemePreset::SlateMidnight,
        ];

        for preset in presets {
            let tokens = preset.tokens();
            for color in [
                &tokens.bg,
                &tokens.surface,
                &tokens.accent,
                &tokens.accent_muted,
                &tokens.text_primary,
                &tokens.text_muted,
                &tokens.border,
                &tokens.nav_accent,
                &tokens.nav_bg,
            ] {
                assert!(color.starts_with('#'), "Color {color} must start with #");
                assert_eq!(
                    color.len(),
                    7,
                    "Color {color} must be 7 characters (#RRGGBB)"
                );
                assert!(
                    color[1..].chars().all(|c| c.is_ascii_hexdigit()),
                    "Color {color} must contain valid hex digits"
                );
            }
        }
    }
}
