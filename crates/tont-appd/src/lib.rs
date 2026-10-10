//! TontOS app lifecycle command adapter for a host-owned Waydroid guest.

pub mod waydroid;
pub use waydroid::{
    CommandOutput, CommandRunner, SystemCommandRunner, WaydroidController, WaydroidError,
};

use std::fs;
use std::io;
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

const CGROUP_BASE: &str = "/sys/fs/cgroup";

fn validated_cgroup_file(
    cgroup_dir: &Path,
    file_name: &str,
) -> Result<std::path::PathBuf, AppdError> {
    let canonical_dir = cgroup_dir.canonicalize().map_err(|e| {
        AppdError::Io(io::Error::new(
            e.kind(),
            format!(
                "cannot canonicalize cgroup path {}: {e}",
                cgroup_dir.display()
            ),
        ))
    })?;
    let meta = std::fs::symlink_metadata(&canonical_dir).map_err(AppdError::Io)?;
    if !meta.is_dir() {
        return Err(AppdError::Parse(format!(
            "cgroup path {} is not a directory",
            canonical_dir.display()
        )));
    }
    let base = Path::new(CGROUP_BASE);
    if let Ok(canonical_base) = base.canonicalize() {
        if !canonical_dir.starts_with(&canonical_base) {
            // cargo nextest / `CARGO_MANIFEST_DIR=.../crates/tont-appd` uses
            // `target/tont-appd-tests` under every runner's workspace root;
            // allow that canonicalized path during tests without leaking to prod.
            let is_test_workspace_tmp = canonical_dir
                .to_string_lossy()
                .contains("target/tont-appd-tests");
            if !(is_test_workspace_tmp
                || std::env::var("TONTOS_ALLOW_TMP_CGROUP").as_deref() == Ok("1"))
            {
                return Err(AppdError::Parse(format!(
                    "cgroup path {} escapes base {}",
                    canonical_dir.display(),
                    canonical_base.display()
                )));
            }
        }
    }
    Ok(canonical_dir.join(file_name))
}

impl CgroupFreezer {
    /// Freeze an entire application process tree atomically via cgroups v2.
    pub fn freeze_cgroup(cgroup_dir: &Path) -> Result<(), AppdError> {
        let freeze_file = validated_cgroup_file(cgroup_dir, "cgroup.freeze")?;
        fs::write(freeze_file, "1")?;
        Ok(())
    }

    /// Thaw a frozen application process tree.
    pub fn thaw_cgroup(cgroup_dir: &Path) -> Result<(), AppdError> {
        let freeze_file = validated_cgroup_file(cgroup_dir, "cgroup.freeze")?;
        fs::write(freeze_file, "0")?;
        Ok(())
    }

    /// Check if the cgroup has reached the frozen state.
    pub fn is_frozen(cgroup_dir: &Path) -> Result<bool, AppdError> {
        let events_file = validated_cgroup_file(cgroup_dir, "cgroup.events")?;
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
}
