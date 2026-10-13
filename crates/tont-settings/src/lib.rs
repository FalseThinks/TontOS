//! TontOS Customization & Connectivity Settings Manager (tont-settings)
//! Controls radio hardware toggles, customizable default apps, ringtones, and display parameters.

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum SettingsError {
    #[error("I/O error reading settings: {0}")]
    Io(#[from] std::io::Error),
    #[error("JSON serialization error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("Invalid ringtone path: {0}")]
    InvalidAudioPath(String),
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ConnectivityConfig {
    pub wifi_enabled: bool,
    pub bluetooth_enabled: bool,
    pub mobile_data_enabled: bool,
    pub gps_enabled: bool,
    pub airplane_mode: bool,
    pub nfc_enabled: bool,
}

impl Default for ConnectivityConfig {
    fn default() -> Self {
        Self {
            wifi_enabled: true,
            bluetooth_enabled: false,
            mobile_data_enabled: true,
            gps_enabled: true,
            airplane_mode: false,
            nfc_enabled: false,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DefaultAppsConfig {
    pub phone_app: String,
    pub messaging_app: String,
    pub browser_app: String,
    pub maps_app: String,
    pub gallery_app: String,
    pub music_app: String,
    pub files_app: String,
}

impl Default for DefaultAppsConfig {
    fn default() -> Self {
        Self {
            phone_app: "org.fossify.phone".to_string(),
            messaging_app: "org.fossify.messages".to_string(),
            browser_app: "de.baumann.browser".to_string(),
            maps_app: "app.organicmaps".to_string(),
            gallery_app: "org.fossify.gallery".to_string(),
            music_app: "org.fossify.musicplayer".to_string(),
            files_app: "org.fossify.filemanager".to_string(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SoundConfig {
    pub ringtone_path: String,
    pub notification_tone_path: String,
    pub alarm_tone_path: String,
    pub ring_volume_percent: u8,
    pub vibration_enabled: bool,
}

impl Default for SoundConfig {
    fn default() -> Self {
        Self {
            ringtone_path: "/usr/share/sounds/tontos/ring_minimal.ogg".to_string(),
            notification_tone_path: "/usr/share/sounds/tontos/notify_pulse.ogg".to_string(),
            alarm_tone_path: "/usr/share/sounds/tontos/alarm_chime.ogg".to_string(),
            ring_volume_percent: 80,
            vibration_enabled: true,
        }
    }
}

pub mod theme;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct DisplayConfig {
    pub screen_timeout_secs: u32,
    pub dark_amoled_mode: bool,
    pub font_scale: f32,
    pub screen_time_budget_mins: u32,
    pub theme: theme::ThemePreset,
}

impl Default for DisplayConfig {
    fn default() -> Self {
        Self {
            screen_timeout_secs: 30,
            dark_amoled_mode: true,
            font_scale: 1.0,
            screen_time_budget_mins: 120, // 2h default
            theme: theme::ThemePreset::AmoledBlack,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct LockScreenConfig {
    pub enabled: bool,
    pub clock_style: String,
    pub theme_override: String,
    pub show_detox: bool,
    pub show_battery: bool,
    pub owner_info: String,
    pub shortcut_left: String,
    pub shortcut_right: String,
}

impl Default for LockScreenConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            clock_style: "THEME_DEFAULT".to_string(),
            theme_override: "MATCH_ACTIVE".to_string(),
            show_detox: true,
            show_battery: true,
            owner_info: String::new(),
            shortcut_left: "TORCH".to_string(),
            shortcut_right: "CAMERA".to_string(),
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct TontSettings {
    pub connectivity: ConnectivityConfig,
    pub default_apps: DefaultAppsConfig,
    pub sound: SoundConfig,
    pub display: DisplayConfig,
    #[serde(default)]
    pub lock_screen: LockScreenConfig,
}

const EMBEDDED_DEFAULT_JSON: &str = include_str!("default_settings.json");

impl TontSettings {
    pub fn embedded_default() -> Self {
        serde_json::from_str(EMBEDDED_DEFAULT_JSON).expect("embedded default_settings.json invalid")
    }

    pub fn load_from_file(path: &Path) -> Result<Self, SettingsError> {
        let content = fs::read_to_string(path)?;
        let settings = serde_json::from_str(&content)?;
        Ok(settings)
    }

    pub fn save_to_file(&self, path: &Path) -> Result<(), SettingsError> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let json = serde_json::to_string_pretty(self)?;
        fs::write(path, json)?;
        Ok(())
    }

    /// Set a custom ringtone path with audio file extension validation.
    pub fn set_custom_ringtone(&mut self, path: String) -> Result<(), SettingsError> {
        let p = Path::new(&path);
        let ext = p
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or_default()
            .to_lowercase();

        if !matches!(ext.as_str(), "ogg" | "wav" | "mp3" | "flac") {
            return Err(SettingsError::InvalidAudioPath(path));
        }

        self.sound.ringtone_path = path;
        Ok(())
    }

    /// Change a default app mapping.
    pub fn set_default_dialer(&mut self, app_id: String) {
        self.default_apps.phone_app = app_id;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_settings_save_and_load() {
        let temp_dir = std::env::temp_dir().join("tont_test_settings");
        let path = temp_dir.join("settings.json");

        let mut settings = TontSettings::default();
        settings.connectivity.wifi_enabled = false;
        settings.default_apps.browser_app = "org.mozilla.fennec_fdroid".to_string();

        settings.save_to_file(&path).unwrap();
        let loaded = TontSettings::load_from_file(&path).unwrap();

        assert_eq!(settings, loaded);
        assert!(!loaded.connectivity.wifi_enabled);
        assert_eq!(loaded.default_apps.browser_app, "org.mozilla.fennec_fdroid");

        let _ = fs::remove_dir_all(&temp_dir);
    }

    #[test]
    fn test_custom_ringtone_validation() {
        let mut settings = TontSettings::default();

        // Valid audio extensions
        assert!(
            settings
                .set_custom_ringtone("/home/user/Music/my_ring.ogg".to_string())
                .is_ok()
        );
        assert!(
            settings
                .set_custom_ringtone("/home/user/Music/custom.mp3".to_string())
                .is_ok()
        );

        // Invalid non-audio file
        assert!(
            settings
                .set_custom_ringtone("/etc/passwd".to_string())
                .is_err()
        );
        assert!(
            settings
                .set_custom_ringtone("malicious.sh".to_string())
                .is_err()
        );
    }

    #[test]
    fn test_lock_screen_config_default_and_embedded() {
        let default_lock = LockScreenConfig::default();
        assert!(default_lock.enabled);
        assert_eq!(default_lock.clock_style, "THEME_DEFAULT");
        assert_eq!(default_lock.shortcut_left, "TORCH");
        assert_eq!(default_lock.shortcut_right, "CAMERA");

        let embedded = TontSettings::embedded_default();
        assert!(embedded.lock_screen.enabled);
        assert_eq!(embedded.lock_screen.theme_override, "MATCH_ACTIVE");
    }
}
