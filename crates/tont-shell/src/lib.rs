//! TontOS Minimalist AMOLED Shell Core (tont-shell)
//! Implements distraction-free, intentional mobile UX with strict app whitelisting.

use serde::{Deserialize, Serialize};
use tont_common::BatteryInfo;

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum AppCategory {
    Communication,
    Mobility,
    Security,
    Utility,
    Media,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct AppEntry {
    pub id: String,
    pub display_name: String,
    pub category: AppCategory,
}

pub struct AppRegistry {
    installed_apps: Vec<AppEntry>,
}

impl Default for AppRegistry {
    fn default() -> Self {
        Self {
            installed_apps: vec![
                // App Store
                AppEntry {
                    id: "com.aurora.store".to_string(),
                    display_name: "Aurora Store".to_string(),
                    category: AppCategory::Utility,
                },
                // Calling & Contacts
                AppEntry {
                    id: "org.fossify.phone".to_string(),
                    display_name: "Phone".to_string(),
                    category: AppCategory::Communication,
                },
                AppEntry {
                    id: "org.fossify.contacts".to_string(),
                    display_name: "Contacts".to_string(),
                    category: AppCategory::Communication,
                },
                // Messages & Files
                AppEntry {
                    id: "org.fossify.messages".to_string(),
                    display_name: "Messages".to_string(),
                    category: AppCategory::Communication,
                },
                AppEntry {
                    id: "org.fossify.filemanager".to_string(),
                    display_name: "Files".to_string(),
                    category: AppCategory::Utility,
                },
                // Gallery & Music
                AppEntry {
                    id: "org.fossify.gallery".to_string(),
                    display_name: "Gallery".to_string(),
                    category: AppCategory::Media,
                },
                AppEntry {
                    id: "org.fossify.music".to_string(),
                    display_name: "Music".to_string(),
                    category: AppCategory::Media,
                },
                AppEntry {
                    id: "org.breezyweather".to_string(),
                    display_name: "Weather".to_string(),
                    category: AppCategory::Utility,
                },
                // System Settings
                AppEntry {
                    id: "org.tontos.settings".to_string(),
                    display_name: "Settings".to_string(),
                    category: AppCategory::Security,
                },
            ],
        }
    }
}

impl AppRegistry {
    pub fn install_app(&mut self, app: AppEntry) {
        if !self.installed_apps.iter().any(|a| a.id == app.id) {
            self.installed_apps.push(app);
        }
    }

    pub fn list_apps(&self) -> &[AppEntry] {
        &self.installed_apps
    }
}

pub mod shade;
pub use shade::{BrightnessControl, DetoxTracker, QuickShade, VolumeControl};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ShellState {
    pub battery: Option<BatteryInfo>,
    pub current_time_str: String,
    pub available_apps: Vec<AppEntry>,
    pub quick_shade: QuickShade,
}

impl ShellState {
    pub fn new(time_str: String, battery: Option<BatteryInfo>, registry: &AppRegistry) -> Self {
        Self {
            battery,
            current_time_str: time_str,
            available_apps: registry.list_apps().to_vec(),
            quick_shade: QuickShade::default(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_app_registry_default() {
        let registry = AppRegistry::default();
        let apps = registry.list_apps();
        assert!(apps.iter().any(|a| a.id == "com.aurora.store"));
        assert!(apps.iter().any(|a| a.id == "org.fossify.phone"));
        assert!(apps.iter().any(|a| a.id == "org.fossify.contacts"));
        assert!(apps.iter().any(|a| a.id == "org.fossify.gallery"));
        assert!(apps.iter().any(|a| a.id == "org.fossify.music"));
        assert!(apps.iter().any(|a| a.id == "org.fossify.filemanager"));
        assert!(apps.iter().any(|a| a.id == "org.breezyweather"));
        assert!(apps.iter().any(|a| a.id == "org.tontos.settings"));
    }

    #[test]
    fn test_install_arbitrary_app() {
        let mut registry = AppRegistry::default();
        registry.install_app(AppEntry {
            id: "com.anyapp.custom".to_string(),
            display_name: "Custom User App".to_string(),
            category: AppCategory::Utility,
        });

        let apps = registry.list_apps();
        assert!(apps.iter().any(|a| a.id == "com.anyapp.custom"));
    }

    #[test]
    fn test_shell_state_receives_all_installed_apps() {
        let registry = AppRegistry::default();
        let state = ShellState::new("12:00".to_string(), None, &registry);
        assert_eq!(state.available_apps.len(), registry.list_apps().len());
    }
}
