use tont_common::SyncWindowConfig;
use tont_syncd::{SyncEvent, SyncScheduler};

#[test]
fn test_sync_window_cycle() {
    let config = SyncWindowConfig {
        interval_secs: 10,
        duration_secs: 2,
    };
    let mut scheduler = SyncScheduler::new(config);

    assert_eq!(scheduler.tick(5), SyncEvent::NoChange);
    assert_eq!(scheduler.tick(5), SyncEvent::WindowOpened);
    assert_eq!(scheduler.tick(1), SyncEvent::NoChange);
    assert_eq!(scheduler.tick(1), SyncEvent::WindowClosed);
    assert_eq!(scheduler.tick(2), SyncEvent::NoChange);
}

#[test]
fn test_time_until_next_event_in_sleep() {
    let config = SyncWindowConfig {
        interval_secs: 1800,
        duration_secs: 60,
    };
    let mut scheduler = SyncScheduler::new(config);
    assert_eq!(scheduler.time_until_next_event_secs(), 1800);

    scheduler.tick(600);
    assert_eq!(scheduler.time_until_next_event_secs(), 1200);

    scheduler.tick(1199);
    assert_eq!(scheduler.time_until_next_event_secs(), 1);
}

#[test]
fn test_time_until_next_event_in_window() {
    let config = SyncWindowConfig {
        interval_secs: 10,
        duration_secs: 5,
    };
    let mut scheduler = SyncScheduler::new(config);
    scheduler.tick(10); // Window opens
    assert_eq!(scheduler.time_until_next_event_secs(), 5);

    scheduler.tick(2);
    assert_eq!(scheduler.time_until_next_event_secs(), 3);
}

#[test]
fn test_large_time_step_saturation() {
    let config = SyncWindowConfig {
        interval_secs: 100,
        duration_secs: 10,
    };
    let mut scheduler = SyncScheduler::new(config);
    // Overshooting sleep duration
    assert_eq!(scheduler.tick(500), SyncEvent::WindowOpened);
    // Overshooting window duration
    assert_eq!(scheduler.tick(50), SyncEvent::WindowClosed);
}

#[test]
fn test_multi_cycle_progression() {
    let config = SyncWindowConfig {
        interval_secs: 4,
        duration_secs: 2,
    };
    let mut scheduler = SyncScheduler::new(config);

    for _ in 0..5 {
        assert_eq!(scheduler.tick(4), SyncEvent::WindowOpened);
        assert_eq!(scheduler.tick(2), SyncEvent::WindowClosed);
    }
}

#[test]
fn test_sync_event_debug_and_equality() {
    assert_eq!(SyncEvent::WindowOpened, SyncEvent::WindowOpened);
    assert_ne!(SyncEvent::WindowOpened, SyncEvent::WindowClosed);
    let debug_str = format!("{:?}", SyncEvent::NoChange);
    assert_eq!(debug_str, "NoChange");
}
