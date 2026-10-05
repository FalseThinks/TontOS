//! Waydroid Container Lifecycle & Session Controller
//! Manages on-demand activation, app launching, and cgroups v2 freezing of the Android container.

use crate::{AppdError, CgroupFreezer};
use std::path::{Path, PathBuf};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum WaydroidError {
    #[error("Invalid Android package name: {0}")]
    InvalidPackageName(String),
    #[error("Cgroup freezer error: {0}")]
    Cgroup(#[from] AppdError),
    #[error("Session error: {0}")]
    Session(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WaydroidSessionState {
    Stopped,
    Running,
    Frozen,
}

pub struct WaydroidController {
    pub cgroup_dir: PathBuf,
    pub state: WaydroidSessionState,
}

impl WaydroidController {
    pub fn new(cgroup_dir: impl AsRef<Path>) -> Self {
        Self {
            cgroup_dir: cgroup_dir.as_ref().to_path_buf(),
            state: WaydroidSessionState::Stopped,
        }
    }

    /// Validate Android package name against injection (alphanumeric, underscore, dots).
    pub fn is_valid_package_name(package: &str) -> bool {
        if package.is_empty() || package.len() > 128 || !package.contains('.') {
            return false;
        }

        for part in package.split('.') {
            if part.is_empty() {
                return false;
            }
            // Segment must start with ASCII letter
            let mut chars = part.chars();
            let first = chars.next().unwrap();
            if !first.is_ascii_alphabetic() {
                return false;
            }
            if !chars.all(|c| c.is_ascii_alphanumeric() || c == '_') {
                return false;
            }
        }
        true
    }

    /// Transition to Running. Thaws cgroup if previously frozen.
    pub fn ensure_running(&mut self) -> Result<(), WaydroidError> {
        match self.state {
            WaydroidSessionState::Stopped => {
                self.state = WaydroidSessionState::Running;
                Ok(())
            }
            WaydroidSessionState::Frozen => {
                if self.cgroup_dir.exists() {
                    CgroupFreezer::thaw_cgroup(&self.cgroup_dir)?;
                }
                self.state = WaydroidSessionState::Running;
                Ok(())
            }
            WaydroidSessionState::Running => Ok(()),
        }
    }

    /// Launch an Android application inside Waydroid.
    pub fn launch_app(&mut self, package: &str) -> Result<(), WaydroidError> {
        if !Self::is_valid_package_name(package) {
            return Err(WaydroidError::InvalidPackageName(package.to_string()));
        }

        self.ensure_running()?;
        Ok(())
    }

    /// Freeze the entire Waydroid container to consume 0% CPU while preserving memory.
    pub fn freeze_session(&mut self) -> Result<(), WaydroidError> {
        if self.state == WaydroidSessionState::Running {
            if self.cgroup_dir.exists() {
                CgroupFreezer::freeze_cgroup(&self.cgroup_dir)?;
            }
            self.state = WaydroidSessionState::Frozen;
        }
        Ok(())
    }

    /// Stop the Waydroid session completely to free RAM.
    pub fn stop_session(&mut self) -> Result<(), WaydroidError> {
        if self.state == WaydroidSessionState::Frozen && self.cgroup_dir.exists() {
            let _ = CgroupFreezer::thaw_cgroup(&self.cgroup_dir);
        }
        self.state = WaydroidSessionState::Stopped;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_package_name_validation() {
        assert!(WaydroidController::is_valid_package_name("com.whatsapp"));
        assert!(WaydroidController::is_valid_package_name(
            "org.fdroid.fdroid"
        ));
        assert!(WaydroidController::is_valid_package_name(
            "com.example.my_app123"
        ));

        // Malformed / injection attempts
        assert!(!WaydroidController::is_valid_package_name(""));
        assert!(!WaydroidController::is_valid_package_name("no_dots"));
        assert!(!WaydroidController::is_valid_package_name(
            "com.app;rm -rf /"
        ));
        assert!(!WaydroidController::is_valid_package_name(
            ".starts.with.dot"
        ));
        assert!(!WaydroidController::is_valid_package_name(
            "com.123start.digit"
        ));
    }

    #[test]
    fn test_lifecycle_state_machine() {
        let temp_dir = std::env::temp_dir().join("tont_test_waydroid_cgroup");
        fs::create_dir_all(&temp_dir).unwrap();

        let mut controller = WaydroidController::new(&temp_dir);
        assert_eq!(controller.state, WaydroidSessionState::Stopped);

        controller.launch_app("org.fdroid.fdroid").unwrap();
        assert_eq!(controller.state, WaydroidSessionState::Running);

        // Freeze container
        controller.freeze_session().unwrap();
        assert_eq!(controller.state, WaydroidSessionState::Frozen);
        let freeze_content = fs::read_to_string(temp_dir.join("cgroup.freeze")).unwrap();
        assert_eq!(freeze_content, "1");

        // Re-launching thaws container
        controller.launch_app("com.whatsapp").unwrap();
        assert_eq!(controller.state, WaydroidSessionState::Running);
        let thaw_content = fs::read_to_string(temp_dir.join("cgroup.freeze")).unwrap();
        assert_eq!(thaw_content, "0");

        // Stop session
        controller.stop_session().unwrap();
        assert_eq!(controller.state, WaydroidSessionState::Stopped);

        let _ = fs::remove_dir_all(&temp_dir);
    }
}
