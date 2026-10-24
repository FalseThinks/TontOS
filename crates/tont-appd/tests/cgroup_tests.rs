use std::fs;
use std::path::Path;
use tont_appd::CgroupFreezer;

fn test_dir(name: &str) -> std::path::PathBuf {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../target/tont-appd-tests")
        .join(name);
    fs::create_dir_all(&dir).unwrap();
    dir
}

#[test]
fn test_freeze_and_thaw() {
    let temp_dir = test_dir("cgroup-freeze");

    CgroupFreezer::freeze_cgroup(&temp_dir).unwrap();
    let content = fs::read_to_string(temp_dir.join("cgroup.freeze")).unwrap();
    assert_eq!(content, "1");

    CgroupFreezer::thaw_cgroup(&temp_dir).unwrap();
    let content = fs::read_to_string(temp_dir.join("cgroup.freeze")).unwrap();
    assert_eq!(content, "0");

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_is_frozen_check() {
    let temp_dir = test_dir("cgroup-events");

    fs::write(temp_dir.join("cgroup.events"), "populated 1\nfrozen 1\n").unwrap();
    assert!(CgroupFreezer::is_frozen(&temp_dir).unwrap());

    fs::write(temp_dir.join("cgroup.events"), "populated 1\nfrozen 0\n").unwrap();
    assert!(!CgroupFreezer::is_frozen(&temp_dir).unwrap());

    let _ = fs::remove_dir_all(&temp_dir);
}
