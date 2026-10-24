//! TontOS Minimalist AMOLED Settings Application (tont-settings-app)
//! Provides UI state management for configuring connectivity, sounds, apps, and display.

use serde::{Deserialize, Serialize};
use thiserror::Error;
use tont_settings::{SettingsError, TontSettings};

#[derive(Debug, Error)]
pub enum AppError {
    #[error("Settings error: {0}")]
    Settings(#[from] SettingsError),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum SettingsCategory {
    Connectivity,
    SoundAndRingtones,
    DefaultApplications,
    DisplayAndBattery,
    AboutSystem,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum AppRole {
    Dialer,
    Messaging,
    Browser,
    Maps,
    Gallery,
    Music,
    Files,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum SettingsAction {
    ToggleWifi,
    ToggleBluetooth,
    ToggleMobileData,
    ToggleGps,
    ToggleAirplaneMode,
    SetRingtone(String),
    SetScreenTimeout(u32),
    SetDefaultApp(AppRole, String),
    SetScreenTimeBudget(u32),
}

#[derive(Default)]
pub struct SettingsApp {
    pub current_category: Option<SettingsCategory>,
    pub settings: TontSettings,
}

impl SettingsApp {
    pub fn new(settings: TontSettings) -> Self {
        Self {
            current_category: None,
            settings,
        }
    }

    /// Navigate to a specific settings sub-menu.
    pub fn navigate_to(&mut self, category: SettingsCategory) {
        self.current_category = Some(category);
    }

    /// Return to top-level menu.
    pub fn navigate_back(&mut self) {
        self.current_category = None;
    }

    /// Apply a user-initiated setting modification.
    pub fn dispatch(&mut self, action: SettingsAction) -> Result<(), AppError> {
        match action {
            SettingsAction::ToggleWifi => {
                self.settings.connectivity.wifi_enabled = !self.settings.connectivity.wifi_enabled;
            }
            SettingsAction::ToggleBluetooth => {
                self.settings.connectivity.bluetooth_enabled =
                    !self.settings.connectivity.bluetooth_enabled;
            }
            SettingsAction::ToggleMobileData => {
                self.settings.connectivity.mobile_data_enabled =
                    !self.settings.connectivity.mobile_data_enabled;
            }
            SettingsAction::ToggleGps => {
                self.settings.connectivity.gps_enabled = !self.settings.connectivity.gps_enabled;
            }
            SettingsAction::ToggleAirplaneMode => {
                let new_mode = !self.settings.connectivity.airplane_mode;
                self.settings.connectivity.airplane_mode = new_mode;
                if new_mode {
                    // Turn off all transmitters when airplane mode is enabled
                    self.settings.connectivity.wifi_enabled = false;
                    self.settings.connectivity.bluetooth_enabled = false;
                    self.settings.connectivity.mobile_data_enabled = false;
                }
            }
            SettingsAction::SetRingtone(path) => {
                self.settings.set_custom_ringtone(path)?;
            }
            SettingsAction::SetScreenTimeout(secs) => {
                self.settings.display.screen_timeout_secs = secs;
            }
            SettingsAction::SetDefaultApp(role, package_id) => match role {
                AppRole::Dialer => self.settings.default_apps.phone_app = package_id,
                AppRole::Messaging => self.settings.default_apps.messaging_app = package_id,
                AppRole::Browser => self.settings.default_apps.browser_app = package_id,
                AppRole::Maps => self.settings.default_apps.maps_app = package_id,
                AppRole::Gallery => self.settings.default_apps.gallery_app = package_id,
                AppRole::Music => self.settings.default_apps.music_app = package_id,
                AppRole::Files => self.settings.default_apps.files_app = package_id,
            },
            SettingsAction::SetScreenTimeBudget(mins) => {
                self.settings.display.screen_time_budget_mins = mins;
            }
        }
        Ok(())
    }
}
