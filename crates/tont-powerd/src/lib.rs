//! TontOS Power Management Core (tont-powerd)
//! Enforces race-free sleep transitions and battery fuel gauge inspection.

use std::fs;
use std::path::Path;
use thiserror::Error;
use tont_common::{BatteryInfo, BatteryStatus};

#[derive(Debug, Error)]
pub enum PowerError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Parse error: {0}")]
    Parse(String),
    #[error("Wakeup race detected: system woke during suspend initiation")]
    WakeupRace,
}

pub struct PowerController;

impl PowerController {
    /// Read the current kernel wakeup count.
    pub fn read_wakeup_count(path: &Path) -> Result<u64, PowerError> {
        let content = fs::read_to_string(path)?;
        content
            .trim()
            .parse::<u64>()
            .map_err(|e| PowerError::Parse(e.to_string()))
    }

    /// Perform a race-free suspend transition.
    ///
    /// 1. Verifies that the wakeup count has not changed.
    /// 2. If valid, writes the target state (`mem` or `freeze`) to `/sys/power/state`.
    pub fn initiate_suspend(
        wakeup_count_path: &Path,
        state_path: &Path,
        current_count: u64,
        target_state: &str,
    ) -> Result<(), PowerError> {
        // Step 1: Write count back to kernel. Kernel returns EINVAL/EAGAIN if an event arrived.
        if let Err(_e) = fs::write(wakeup_count_path, current_count.to_string()) {
            return Err(PowerError::WakeupRace);
        }

        // Step 2: Write suspend state
        fs::write(state_path, target_state)?;
        Ok(())
    }

    /// Parse battery info from sysfs battery path.
    pub fn read_battery(dir: &Path) -> Result<BatteryInfo, PowerError> {
        let capacity_str = fs::read_to_string(dir.join("capacity"))?;
        let capacity = capacity_str
            .trim()
            .parse::<u8>()
            .map_err(|e| PowerError::Parse(e.to_string()))?;

        let status_str = fs::read_to_string(dir.join("status")).unwrap_or_default();
        let status = match status_str.trim() {
            "Charging" => BatteryStatus::Charging,
            "Discharging" => BatteryStatus::Discharging,
            "Full" => BatteryStatus::Full,
            "Not charging" => BatteryStatus::NotCharging,
            _ => BatteryStatus::Unknown,
        };

        Ok(BatteryInfo {
            capacity_percent: capacity,
            status,
        })
    }
}

