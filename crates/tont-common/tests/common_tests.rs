use tont_common::{AppLifecycleState, BatteryInfo, BatteryStatus, PowerState, SyncWindowConfig};

#[test]
fn test_sync_window_default() {
    let config = SyncWindowConfig::default();
    assert_eq!(config.interval_secs, 1800);
    assert_eq!(config.duration_secs, 60);
}

#[test]
fn test_serde_roundtrip() {
    let info = BatteryInfo {
        capacity_percent: 85,
        status: BatteryStatus::Discharging,
    };
    let serialized = serde_json::to_string(&info).unwrap();
    let deserialized: BatteryInfo = serde_json::from_str(&serialized).unwrap();
    assert_eq!(info, deserialized);
}

#[test]
fn test_power_state_variants_serde() {
    let states = [
        PowerState::Active,
        PowerState::SuspendToIdle,
        PowerState::DeepSleep,
    ];
    for state in states {
        let json = serde_json::to_string(&state).unwrap();
        let de: PowerState = serde_json::from_str(&json).unwrap();
        assert_eq!(state, de);
    }
}

#[test]
fn test_battery_status_all_variants() {
    let statuses = [
        BatteryStatus::Charging,
        BatteryStatus::Discharging,
        BatteryStatus::Full,
        BatteryStatus::NotCharging,
        BatteryStatus::Unknown,
    ];
    for status in statuses {
        let json = serde_json::to_string(&status).unwrap();
        let de: BatteryStatus = serde_json::from_str(&json).unwrap();
        assert_eq!(status, de);
    }
}

#[test]
fn test_battery_info_boundary_values() {
    let empty = BatteryInfo {
        capacity_percent: 0,
        status: BatteryStatus::NotCharging,
    };
    assert_eq!(empty.capacity_percent, 0);

    let full = BatteryInfo {
        capacity_percent: 100,
        status: BatteryStatus::Full,
    };
    assert_eq!(full.capacity_percent, 100);
    assert_eq!(full.status, BatteryStatus::Full);
}

#[test]
fn test_app_lifecycle_state_serde() {
    let states = [
        AppLifecycleState::Foreground,
        AppLifecycleState::Background,
        AppLifecycleState::Frozen,
    ];
    for state in states {
        let json = serde_json::to_string(&state).unwrap();
        let de: AppLifecycleState = serde_json::from_str(&json).unwrap();
        assert_eq!(state, de);
    }
}

#[test]
fn test_sync_window_custom_values() {
    let config = SyncWindowConfig {
        interval_secs: 900,
        duration_secs: 30,
    };
    assert_eq!(config.interval_secs, 900);
    assert_eq!(config.duration_secs, 30);

    let copied = config;
    assert_eq!(config, copied);
}

#[test]
fn test_clone_and_debug_implementations() {
    let p = PowerState::Active;
    let formatted = format!("{:?}", p);
    assert_eq!(formatted, "Active");

    let b = BatteryInfo {
        capacity_percent: 50,
        status: BatteryStatus::Charging,
    };
    assert_eq!(b.clone(), b);
}
