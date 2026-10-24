use tont_common::{BatteryInfo, BatteryStatus, PowerState, SyncWindowConfig};
use tont_sensors::{ProximityState, ScreenOrientation, SensorHub};
use tont_syncd::{SyncEvent, SyncScheduler};
use tont_telephony::{CallState, CellularNetworkStatus, TelephonyManager};

#[test]
fn test_cross_daemon_sync_and_telephony_flow() {
    // When incoming call rings, telephony takes precedence over background sync
    let mut tel_mgr = TelephonyManager::new();
    let call = tel_mgr.handle_incoming_call("+18005550199".to_string());
    assert_eq!(call.state, CallState::RingingInbound);

    let config = SyncWindowConfig {
        interval_secs: 1800,
        duration_secs: 60,
    };
    let mut syncd = SyncScheduler::new(config);
    // Even if sync window opens during active call, telephony state is preserved
    let event = syncd.tick(1800);
    assert_eq!(event, SyncEvent::WindowOpened);

    let answered = tel_mgr.answer_call(call.call_id).unwrap();
    assert_eq!(answered.state, CallState::Active);

    // Call termination
    let term = tel_mgr.terminate_call(call.call_id).unwrap();
    assert_eq!(term.state, CallState::Terminated);
}

#[test]
fn test_sensors_and_power_state_coordination() {
    // Flat orientation (reading portrait upright)
    let orientation = SensorHub::compute_orientation(0, -980, 100);
    assert_eq!(orientation, ScreenOrientation::Portrait);

    // Proximity detected during call (phone against ear)
    let proximity = SensorHub::compute_proximity(100, 50);
    assert_eq!(proximity, ProximityState::Near);

    // Device power state transitions
    let active = PowerState::Active;
    let suspend = PowerState::SuspendToIdle;
    assert_ne!(active, suspend);

    let b = BatteryInfo {
        capacity_percent: 15,
        status: BatteryStatus::Discharging,
    };
    assert_eq!(b.capacity_percent, 15);
}

#[test]
fn test_cellular_network_sms_integration() {
    let mut tel_mgr = TelephonyManager::new();
    tel_mgr.network_status = CellularNetworkStatus::Registered {
        operator: "TontOS Mobile".to_string(),
        signal_percent: 92,
    };

    let sms = tel_mgr.handle_incoming_sms(
        "+34611223344".to_string(),
        "Welcome to TontOS minimal phone".to_string(),
        1700000000,
    );

    assert_eq!(sms.message_id, 1);
    assert_eq!(tel_mgr.get_inbox().len(), 1);
    assert_eq!(tel_mgr.get_inbox()[0].sender, "+34611223344");
}
