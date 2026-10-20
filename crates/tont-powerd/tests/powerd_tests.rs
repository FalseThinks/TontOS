use std::fs;
use tont_common::BatteryStatus;
use tont_powerd::{PowerController, PowerError};

#[test]
fn test_read_wakeup_count() {
    let temp_dir = std::env::temp_dir().join("tont_test_power_count");
    fs::create_dir_all(&temp_dir).unwrap();
    let count_file = temp_dir.join("wakeup_count");
    fs::write(&count_file, "42\n").unwrap();

    let count = PowerController::read_wakeup_count(&count_file).unwrap();
    assert_eq!(count, 42);
    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_read_battery() {
    let temp_dir = std::env::temp_dir().join("tont_test_battery");
    fs::create_dir_all(&temp_dir).unwrap();
    fs::write(temp_dir.join("capacity"), "95\n").unwrap();
    fs::write(temp_dir.join("status"), "Discharging\n").unwrap();

    let info = PowerController::read_battery(&temp_dir).unwrap();
    assert_eq!(info.capacity_percent, 95);
    assert_eq!(info.status, BatteryStatus::Discharging);
    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_parse_invalid_wakeup_count_fails() {
    let temp_dir = std::env::temp_dir().join("tont_test_invalid_count");
    fs::create_dir_all(&temp_dir).unwrap();
    let count_file = temp_dir.join("wakeup_count");
    fs::write(&count_file, "not_a_number\n").unwrap();

    let res = PowerController::read_wakeup_count(&count_file);
    assert!(res.is_err());
    match res.unwrap_err() {
        PowerError::Parse(_) => {}
        _ => panic!("Expected Parse error"),
    }
    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_read_battery_all_statuses() {
    let temp_dir = std::env::temp_dir().join("tont_test_battery_statuses");
    fs::create_dir_all(&temp_dir).unwrap();
    fs::write(temp_dir.join("capacity"), "100\n").unwrap();

    let pairs = [
        ("Charging", BatteryStatus::Charging),
        ("Full", BatteryStatus::Full),
        ("Not charging", BatteryStatus::NotCharging),
        ("UnknownStatus", BatteryStatus::Unknown),
    ];

    for (status_str, expected) in pairs {
        fs::write(temp_dir.join("status"), format!("{}\n", status_str)).unwrap();
        let info = PowerController::read_battery(&temp_dir).unwrap();
        assert_eq!(info.status, expected);
    }

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_read_battery_invalid_capacity() {
    let temp_dir = std::env::temp_dir().join("tont_test_battery_invalid_cap");
    fs::create_dir_all(&temp_dir).unwrap();
    fs::write(temp_dir.join("capacity"), "not_a_number\n").unwrap();

    let res = PowerController::read_battery(&temp_dir);
    assert!(res.is_err());
    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_initiate_suspend_success() {
    let temp_dir = std::env::temp_dir().join("tont_test_suspend");
    fs::create_dir_all(&temp_dir).unwrap();
    let wakeup_file = temp_dir.join("wakeup_count");
    let state_file = temp_dir.join("state");

    fs::write(&wakeup_file, "10\n").unwrap();

    let res = PowerController::initiate_suspend(&wakeup_file, &state_file, 10, "mem");
    assert!(res.is_ok());

    let state_content = fs::read_to_string(&state_file).unwrap();
    assert_eq!(state_content, "mem");

    let _ = fs::remove_dir_all(&temp_dir);
}
