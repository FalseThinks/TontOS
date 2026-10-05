//! TontOS App Lifecycle Daemon (tont-appd)
//! Manages Linux cgroups v2 atomic process freezing for inactive mobile applications.

pub mod waydroid;
pub use waydroid::{WaydroidController, WaydroidSessionState};

use std::fs;
use std::path::Path;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppdError {
    #[error("I/O error on cgroup: {0}")]
    Io(#[from] std::io::Error),
    #[error("Invalid cgroup event format: {0}")]
    Parse(String),
}

pub struct CgroupFreezer;

impl CgroupFreezer {
    /// Freeze an entire application process tree atomically via cgroups v2.
    pub fn freeze_cgroup(cgroup_dir: &Path) -> Result<(), AppdError> {
        let freeze_file = cgroup_dir.join("cgroup.freeze");
        fs::write(freeze_file, "1")?;
        Ok(())
    }

    /// Thaw a frozen application process tree.
    pub fn thaw_cgroup(cgroup_dir: &Path) -> Result<(), AppdError> {
        let freeze_file = cgroup_dir.join("cgroup.freeze");
        fs::write(freeze_file, "0")?;
        Ok(())
    }

    /// Check if the cgroup has reached the frozen state.
    pub fn is_frozen(cgroup_dir: &Path) -> Result<bool, AppdError> {
        let events_file = cgroup_dir.join("cgroup.events");
        let content = fs::read_to_string(events_file)?;
        for line in content.lines() {
            if line.trim() == "frozen 1" {
                return Ok(true);
            }
        }
        Ok(false)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_freeze_and_thaw() {
        let temp_dir = std::env::temp_dir().join("tont_test_cgroup_freeze");
        fs::create_dir_all(&temp_dir).unwrap();

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
        let temp_dir = std::env::temp_dir().join("tont_test_cgroup_events");
        fs::create_dir_all(&temp_dir).unwrap();

        fs::write(temp_dir.join("cgroup.events"), "populated 1\nfrozen 1\n").unwrap();
        assert!(CgroupFreezer::is_frozen(&temp_dir).unwrap());

        fs::write(temp_dir.join("cgroup.events"), "populated 1\nfrozen 0\n").unwrap();
        assert!(!CgroupFreezer::is_frozen(&temp_dir).unwrap());

        let _ = fs::remove_dir_all(&temp_dir);
    }
}
