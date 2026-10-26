use std::fs;
use std::path::Path;
use tont_backup::{BackupError, BackupPackage};
use tont_settings::TontSettings;
use tont_telephony::SmsMessage;

#[test]
fn test_backup_create_save_load_roundtrip() {
    let temp_dir = Path::new("target").join(format!("tont_test_backup_{}", std::process::id()));
    let backup_path = temp_dir.join("tontos_backup.json");

    let settings = TontSettings::default();
    let sms = vec![SmsMessage {
        message_id: 1,
        sender: "Bank".to_string(),
        content: "Auth code 123456".to_string(),
        timestamp_secs: 1700000000,
    }];
    let apps = vec![];

    let backup =
        BackupPackage::create(settings, sms, apps, 1700000000, "Treble-ARM64".to_string()).unwrap();

    assert!(backup.verify_integrity());

    backup.save_to_file(&backup_path).unwrap();
    let loaded = BackupPackage::load_from_file(&backup_path).unwrap();

    assert_eq!(backup, loaded);
    assert_eq!(loaded.payload.sms_history[0].sender, "Bank");

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_backup_tampering_fails_verification() {
    let settings = TontSettings::default();
    let mut backup = BackupPackage::create(
        settings,
        vec![],
        vec![],
        1700000000,
        "Treble-ARM64".to_string(),
    )
    .unwrap();

    assert!(backup.verify_integrity());

    // Tamper with payload without updating manifest checksum
    backup.payload.settings.connectivity.wifi_enabled = false;
    assert!(!backup.verify_integrity());
}

#[test]
fn test_tamper_manifest_sha256_directly() {
    let settings = TontSettings::default();
    let mut backup = BackupPackage::create(
        settings,
        vec![],
        vec![],
        1700000000,
        "Treble-ARM64".to_string(),
    )
    .unwrap();

    assert!(backup.verify_integrity());

    // Alter single hex character of SHA-256
    backup.manifest.payload_sha256 =
        "0000000000000000000000000000000000000000000000000000000000000000".to_string();
    assert!(!backup.verify_integrity());
}

#[test]
fn test_empty_backup_payload_valid() {
    let settings = TontSettings::default();
    let backup =
        BackupPackage::create(settings, vec![], vec![], 0, "Generic-x86_64".to_string()).unwrap();

    assert!(backup.verify_integrity());
    assert_eq!(backup.manifest.payload_sha256.len(), 64);
    assert_eq!(backup.payload.sms_history.len(), 0);
    assert_eq!(backup.payload.installed_apps.len(), 0);
}

#[test]
fn test_load_nonexistent_file_io_error() {
    let nonexistent = Path::new("/tmp/nonexistent_tontos_backup_12345.json");
    let res = BackupPackage::load_from_file(nonexistent);
    assert!(res.is_err());
    match res.unwrap_err() {
        BackupError::Io(_) => {}
        _ => panic!("Expected IO error"),
    }
}
