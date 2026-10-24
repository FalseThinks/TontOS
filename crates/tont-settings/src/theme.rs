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

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct ThemeTokens {
    pub id: &'static str,
    pub name: &'static str,
    pub bg: &'static str,
    pub surface: &'static str,
    pub accent: &'static str,
    pub accent_muted: &'static str,
    pub text_primary: &'static str,
    pub text_muted: &'static str,
    pub border: &'static str,
    pub nav_accent: &'static str,
    pub nav_bg: &'static str,
}

impl ThemePreset {
    pub fn tokens(&self) -> ThemeTokens {
        match self {
            Self::AmoledBlack => ThemeTokens {
                id: "AMOLED",
                name: "AMOLED Black",
                bg: "#000000",
                surface: "#0D110E",
                accent: "#44DD88",
                accent_muted: "#1B5E38",
                text_primary: "#E5E7EB",
                text_muted: "#6B7280",
                border: "#1B261F",
                nav_accent: "#38BDF8",
                nav_bg: "#0B1926",
            },
            Self::AmberCrt => ThemeTokens {
                id: "AMBER",
                name: "Amber CRT",
                bg: "#000000",
                surface: "#0F0A00",
                accent: "#FFB000",
                accent_muted: "#805800",
                text_primary: "#FFB000",
                text_muted: "#996A00",
                border: "#332300",
                nav_accent: "#FF6E40",
                nav_bg: "#1A0A00",
            },
            Self::EinkMonochrome => ThemeTokens {
                id: "E-INK",
                name: "E-Ink Paper",
                bg: "#FFFFFF",
                surface: "#F4F4F6",
                accent: "#000000",
                accent_muted: "#333333",
                text_primary: "#000000",
                text_muted: "#555555",
                border: "#777777",
                nav_accent: "#1D4ED8",
                nav_bg: "#EFF6FF",
            },
            Self::SlateMidnight => ThemeTokens {
                id: "SLATE",
                name: "Slate Midnight",
                bg: "#0E141B",
                surface: "#161F28",
                accent: "#58A6FF",
                accent_muted: "#1F4A85",
                text_primary: "#F0F6FC",
                text_muted: "#8B949E",
                border: "#212B36",
                nav_accent: "#F78166",
                nav_bg: "#261815",
            },
        }
    }
}
