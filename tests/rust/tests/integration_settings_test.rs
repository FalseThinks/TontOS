use tont_backup::BackupPackage;
use tont_input::{HardwareKey, KeyAction, KeyProcessor};
use tont_settings::TontSettings;
use tont_shell::shade::{BrightnessControl, DetoxTracker, VolumeControl};
use tont_shell::{AppCategory, AppEntry, AppRegistry, ShellState};

#[test]
fn test_shell_state_and_settings_coordination() {
    let settings = TontSettings::default();
    assert_eq!(settings.display.screen_timeout_secs, 30);
    assert_eq!(settings.display.screen_time_budget_mins, 120);

    let registry = AppRegistry::default();
    let state = ShellState::new("14:30".to_string(), None, &registry);
    assert_eq!(state.current_time_str, "14:30");
    assert_eq!(state.available_apps.len(), 13);
}

#[test]
fn test_quick_shade_hardware_controls_and_input_events() {
    let mut proc = KeyProcessor::new();

    // Volume up key event
    proc.handle_key_down(HardwareKey::VolumeUp, 100);
    let action = proc.handle_key_up(HardwareKey::VolumeUp, 150);
    assert_eq!(action, Some(KeyAction::VolumeUp));

    let mut vol = VolumeControl::default();
    vol.step_up(5);
    assert_eq!(vol.percent, 65);

    let mut bright = BrightnessControl::default();
    bright.set_percent(80);
    assert_eq!(bright.percent, 80);

    let mut detox = DetoxTracker::default();
    detox.record_screen_on(1200); // 20 mins
    assert_eq!(detox.format_screen_time(), "[ 20m / 2h budget ]");
}

#[test]
fn test_backup_and_settings_persistence_envelope() {
    let mut settings = TontSettings::default();
    settings.sound.ring_volume_percent = 70;
    settings.display.font_scale = 1.15;

    let apps = vec![AppEntry {
        id: "org.tontos.custom".to_string(),
        display_name: "Custom App".to_string(),
        category: AppCategory::Utility,
    }];

    let backup = BackupPackage::create(
        settings.clone(),
        vec![],
        apps,
        1700000000,
        "x86_64".to_string(),
    )
    .unwrap();

    assert!(backup.verify_integrity());
    assert_eq!(
        backup.payload.settings.sound.ring_volume_percent,
        settings.sound.ring_volume_percent
    );
    assert_eq!(
        backup.payload.settings.display.font_scale,
        settings.display.font_scale
    );
}
