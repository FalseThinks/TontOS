use tont_settings_app::{AppRole, SettingsAction, SettingsApp, SettingsCategory};

#[test]
fn test_navigation_flow() {
    let mut app = SettingsApp::default();
    assert_eq!(app.current_category, None);

    app.navigate_to(SettingsCategory::Connectivity);
    assert_eq!(app.current_category, Some(SettingsCategory::Connectivity));

    app.navigate_back();
    assert_eq!(app.current_category, None);
}

#[test]
fn test_toggle_airplane_mode() {
    let mut app = SettingsApp::default();
    assert!(app.settings.connectivity.wifi_enabled);

    app.dispatch(SettingsAction::ToggleAirplaneMode).unwrap();
    assert!(app.settings.connectivity.airplane_mode);
    assert!(!app.settings.connectivity.wifi_enabled);
    assert!(!app.settings.connectivity.bluetooth_enabled);
    assert!(!app.settings.connectivity.mobile_data_enabled);
}

#[test]
fn test_set_custom_default_browser() {
    let mut app = SettingsApp::default();
    app.dispatch(SettingsAction::SetDefaultApp(
        AppRole::Browser,
        "org.mozilla.fennec_fdroid".to_string(),
    ))
    .unwrap();

    assert_eq!(
        app.settings.default_apps.browser_app,
        "org.mozilla.fennec_fdroid"
    );
}

#[test]
fn test_set_valid_ringtone() {
    let mut app = SettingsApp::default();
    app.dispatch(SettingsAction::SetRingtone(
        "/home/user/Music/bell.ogg".to_string(),
    ))
    .unwrap();

    assert_eq!(
        app.settings.sound.ringtone_path,
        "/home/user/Music/bell.ogg"
    );
}
