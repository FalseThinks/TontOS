//! TontOS Sensor Daemon (tont-sensors)
//! Interfaces with Linux Industrial I/O (IIO) subsystem for orientation, proximity, and light sensing.

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum SensorError {
    #[error("I/O error reading sensor node: {0}")]
    Io(#[from] std::io::Error),
    #[error("Failed to parse sensor value: {0}")]
    Parse(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ScreenOrientation {
    Portrait,
    PortraitInverted,
    LandscapeLeft,
    LandscapeRight,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ProximityState {
    /// Ear or object is close to screen (trigger display blanking & touch disable during calls).
    Near,
    /// Nothing obstructing the screen.
    Far,
}

pub struct SensorHub;

impl SensorHub {
    /// Calculate screen orientation from accelerometer vector (x, y, z) with gravity normalization.
    pub fn compute_orientation(x: i32, y: i32, _z: i32) -> ScreenOrientation {
        let abs_x = x.unsigned_abs();
        let abs_y = y.unsigned_abs();

        if abs_y >= abs_x {
            if y < 0 {
                ScreenOrientation::Portrait
            } else {
                ScreenOrientation::PortraitInverted
            }
        } else if x > 0 {
            ScreenOrientation::LandscapeRight
        } else {
            ScreenOrientation::LandscapeLeft
        }
    }

    /// Evaluate proximity sensor raw value against hardware trigger threshold.
    pub fn compute_proximity(raw_value: i32, threshold: i32) -> ProximityState {
        if raw_value >= threshold {
            ProximityState::Near
        } else {
            ProximityState::Far
        }
    }

    /// Read raw integer value from a sysfs/IIO device channel node.
    pub fn read_iio_channel(path: &Path) -> Result<i32, SensorError> {
        let content = fs::read_to_string(path)?;
        content
            .trim()
            .parse::<i32>()
            .map_err(|e| SensorError::Parse(e.to_string()))
    }
}
