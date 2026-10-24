use tont_input::{
    AudioMixerController, EV_KEY, HardwareKey, KEY_VOLUMEUP, KeyAction, KeyProcessor, RawInputEvent,
};

#[test]
fn test_power_short_press_toggles_display() {
    let mut proc = KeyProcessor::new();
    proc.handle_key_down(HardwareKey::Power, 1000);
    let action = proc.handle_key_up(HardwareKey::Power, 1200);
    assert_eq!(action, Some(KeyAction::ToggleDisplay));
}

#[test]
fn test_power_long_press_opens_menu() {
    let mut proc = KeyProcessor::new();
    proc.handle_key_down(HardwareKey::Power, 1000);
    let action = proc.handle_key_up(HardwareKey::Power, 2500);
    assert_eq!(action, Some(KeyAction::OpenPowerMenu));
}

#[test]
fn test_volume_keys() {
    let mut proc = KeyProcessor::new();
    proc.handle_key_down(HardwareKey::VolumeUp, 1000);
    assert_eq!(
        proc.handle_key_up(HardwareKey::VolumeUp, 1050),
        Some(KeyAction::VolumeUp)
    );

    proc.handle_key_down(HardwareKey::VolumeDown, 2000);
    assert_eq!(
        proc.handle_key_up(HardwareKey::VolumeDown, 2050),
        Some(KeyAction::VolumeDown)
    );
}

#[test]
fn test_emergency_sos_five_presses() {
    let mut proc = KeyProcessor::new();
    for i in 0..4 {
        proc.handle_key_down(HardwareKey::Power, i * 200);
        assert_eq!(
            proc.handle_key_up(HardwareKey::Power, i * 200 + 50),
            Some(KeyAction::ToggleDisplay)
        );
    }
    // 5th tap triggers SOS
    proc.handle_key_down(HardwareKey::Power, 800);
    assert_eq!(
        proc.handle_key_up(HardwareKey::Power, 850),
        Some(KeyAction::EmergencySos)
    );
}

#[test]
fn test_raw_input_event_deserialization() {
    let mut bytes = [0u8; 24];
    // time_sec = 10
    bytes[0..8].copy_from_slice(&10i64.to_ne_bytes());
    // time_usec = 500000 (500ms)
    bytes[8..16].copy_from_slice(&500000i64.to_ne_bytes());
    // type = EV_KEY (1)
    bytes[16..18].copy_from_slice(&EV_KEY.to_ne_bytes());
    // code = KEY_VOLUMEUP (115)
    bytes[18..20].copy_from_slice(&KEY_VOLUMEUP.to_ne_bytes());
    // value = 1 (down)
    bytes[20..24].copy_from_slice(&1i32.to_ne_bytes());

    let raw = RawInputEvent::from_bytes(&bytes).expect("valid input event");
    assert_eq!(raw.code, KEY_VOLUMEUP);
    assert_eq!(raw.value, 1);

    let (key, is_down, timestamp_ms) = raw.to_hardware_event().expect("hardware event");
    assert_eq!(key, HardwareKey::VolumeUp);
    assert!(is_down);
    assert_eq!(timestamp_ms, 10500);
}

#[test]
fn test_audio_mixer_controller() {
    let mut mixer = AudioMixerController::new(50);
    assert_eq!(mixer.get_state().level_percent, 50);

    let state = mixer.volume_up();
    assert_eq!(state.level_percent, 55);

    let state = mixer.volume_down();
    assert_eq!(state.level_percent, 50);

    let state = mixer.toggle_mute();
    assert!(state.is_muted);
    assert_eq!(state.level_percent, 0);

    let state = mixer.toggle_mute();
    assert!(!state.is_muted);
    assert_eq!(state.level_percent, 50);

    let state = mixer.set_volume(98);
    assert_eq!(state.level_percent, 98);
    let state = mixer.volume_up();
    assert_eq!(state.level_percent, 100); // capped at 100
}
