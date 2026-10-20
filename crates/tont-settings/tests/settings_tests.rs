use std::fs;
use tont_settings::{
    ConnectivityConfig, DisplayConfig, LockScreenConfig, SoundConfig, TontSettings,
};

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

#[test]
fn test_display_timeouts_and_budgets_validity() {
    let valid_timeouts = [0, 15, 30, 60, 120, 300, 600];
    let valid_budgets = [0, 60, 120, 180, 240];

    let mut config = DisplayConfig::default();
    assert_eq!(config.screen_timeout_secs, 30);
    assert_eq!(config.screen_time_budget_mins, 120);

    for timeout in valid_timeouts {
        config.screen_timeout_secs = timeout;
        assert_eq!(config.screen_timeout_secs, timeout);
    }

    for budget in valid_budgets {
        config.screen_time_budget_mins = budget;
        assert_eq!(config.screen_time_budget_mins, budget);
    }
}

#[test]
fn test_display_font_scales() {
    let mut config = DisplayConfig::default();
    assert_eq!(config.font_scale, 1.0);

    let allowed_scales = [0.85, 1.00, 1.15, 1.30];
    for scale in allowed_scales {
        config.font_scale = scale;
        assert_eq!(config.font_scale, scale);
    }
}

#[test]
fn test_sound_config_volume_and_vibration() {
    let mut sound = SoundConfig::default();
    assert_eq!(sound.ring_volume_percent, 80);
    assert!(sound.vibration_enabled);

    sound.ring_volume_percent = 0;
    assert_eq!(sound.ring_volume_percent, 0);

    sound.ring_volume_percent = 100;
    assert_eq!(sound.ring_volume_percent, 100);
}

#[test]
fn test_connectivity_toggles_and_airplane_mode() {
    let mut conn = ConnectivityConfig::default();
    assert!(conn.wifi_enabled);
    assert!(!conn.bluetooth_enabled);
    assert!(conn.mobile_data_enabled);
    assert!(!conn.airplane_mode);

    conn.airplane_mode = true;
    conn.wifi_enabled = false;
    conn.mobile_data_enabled = false;
    conn.bluetooth_enabled = false;

    assert!(conn.airplane_mode);
    assert!(!conn.wifi_enabled);
    assert!(!conn.mobile_data_enabled);
}

#[test]
fn test_lock_screen_shortcuts_reconfiguration() {
    let lock = LockScreenConfig {
        shortcut_left: "PHONE".to_string(),
        shortcut_right: "NONE".to_string(),
        clock_style: "ANALOG_MINIMAL".to_string(),
        ..Default::default()
    };

    assert_eq!(lock.shortcut_left, "PHONE");
    assert_eq!(lock.shortcut_right, "NONE");
    assert_eq!(lock.clock_style, "ANALOG_MINIMAL");
}
