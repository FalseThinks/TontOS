//! TontOS Common Core Definitions
//! Minimal, zero-overhead shared types for power, process, and sync states.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum PowerState {
    Active,
    SuspendToIdle,
    DeepSleep,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum BatteryStatus {
    Charging,
    Discharging,
    Full,
    NotCharging,
    Unknown,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct BatteryInfo {
    pub capacity_percent: u8,
    pub status: BatteryStatus,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AppLifecycleState {
    Foreground,
    Background,
    Frozen,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SyncWindowConfig {
    pub interval_secs: u64,
    pub duration_secs: u64,
}

impl Default for SyncWindowConfig {
    fn default() -> Self {
        Self {
            interval_secs: 1800, // 30 minutes
            duration_secs: 60,   // 60 seconds
        }
    }
}

