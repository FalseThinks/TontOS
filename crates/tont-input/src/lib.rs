//! TontOS Hardware Button Processor (tont-input)
//! Translates physical phone keys (Power, Volume) into system power and audio actions.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum HardwareKey {
    Power,
    VolumeUp,
    VolumeDown,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum KeyAction {
    ToggleDisplay,
    OpenPowerMenu,
    VolumeUp,
    VolumeDown,
    EmergencySos,
}

#[derive(Default)]
pub struct KeyProcessor {
    power_press_start_ms: Option<u64>,
    power_tap_count: u32,
    last_power_release_ms: u64,
}

impl KeyProcessor {
    pub fn new() -> Self {
        Self::default()
    }

    /// Record key depression.
    pub fn handle_key_down(&mut self, key: HardwareKey, timestamp_ms: u64) {
        if key == HardwareKey::Power {
            self.power_press_start_ms = Some(timestamp_ms);

            // Reset tap count if more than 600ms passed since last tap
            if timestamp_ms.saturating_sub(self.last_power_release_ms) > 600 {
                self.power_tap_count = 0;
            }
        }
    }

    /// Record key release and evaluate target action.
    pub fn handle_key_up(&mut self, key: HardwareKey, timestamp_ms: u64) -> Option<KeyAction> {
        match key {
            HardwareKey::VolumeUp => Some(KeyAction::VolumeUp),
            HardwareKey::VolumeDown => Some(KeyAction::VolumeDown),
            HardwareKey::Power => {
                let start_ms = self.power_press_start_ms.take()?;
                let duration_ms = timestamp_ms.saturating_sub(start_ms);
                self.last_power_release_ms = timestamp_ms;

                // Long press > 1200ms -> Power Menu
                if duration_ms >= 1200 {
                    self.power_tap_count = 0;
                    return Some(KeyAction::OpenPowerMenu);
                }

                // Short press logic: track multi-tap for Emergency SOS (5 taps)
                self.power_tap_count += 1;
                if self.power_tap_count >= 5 {
                    self.power_tap_count = 0;
                    return Some(KeyAction::EmergencySos);
                }

                Some(KeyAction::ToggleDisplay)
            }
        }
    }
}

pub const EV_KEY: u16 = 1;
pub const KEY_VOLUMEDOWN: u16 = 114;
pub const KEY_VOLUMEUP: u16 = 115;
pub const KEY_POWER: u16 = 116;

/// Standard Linux input_event structure (24 bytes on 64-bit platforms)
#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct RawInputEvent {
    pub time_sec: i64,
    pub time_usec: i64,
    pub type_: u16,
    pub code: u16,
    pub value: i32,
}

impl RawInputEvent {
    pub fn from_bytes(bytes: &[u8]) -> Option<Self> {
        if bytes.len() < 24 {
            return None;
        }
        let time_sec = i64::from_ne_bytes(bytes[0..8].try_into().ok()?);
        let time_usec = i64::from_ne_bytes(bytes[8..16].try_into().ok()?);
        let type_ = u16::from_ne_bytes(bytes[16..18].try_into().ok()?);
        let code = u16::from_ne_bytes(bytes[18..20].try_into().ok()?);
        let value = i32::from_ne_bytes(bytes[20..24].try_into().ok()?);
        Some(Self {
            time_sec,
            time_usec,
            type_,
            code,
            value,
        })
    }

    /// Converts raw event into TontOS HardwareKey event if applicable.
    /// Returns (HardwareKey, is_down, timestamp_ms).
    pub fn to_hardware_event(&self) -> Option<(HardwareKey, bool, u64)> {
        if self.type_ != EV_KEY {
            return None;
        }
        let key = match self.code {
            KEY_POWER => HardwareKey::Power,
            KEY_VOLUMEUP => HardwareKey::VolumeUp,
            KEY_VOLUMEDOWN => HardwareKey::VolumeDown,
            _ => return None,
        };
        let is_down = match self.value {
            1 => true,
            0 => false,
            _ => return None, // ignore key auto-repeat (value == 2)
        };
        let timestamp_ms = (self.time_sec as u64) * 1000 + (self.time_usec as u64) / 1000;
        Some((key, is_down, timestamp_ms))
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct VolumeState {
    pub level_percent: u8,
    pub is_muted: bool,
}

/// System audio volume mixer controller driven by physical keys or drop-down shade.
pub struct AudioMixerController {
    current_volume: u8,
    pre_mute_volume: u8,
    is_muted: bool,
    step_percent: u8,
}

impl AudioMixerController {
    pub fn new(initial_volume: u8) -> Self {
        let initial = initial_volume.min(100);
        Self {
            current_volume: initial,
            pre_mute_volume: initial,
            is_muted: false,
            step_percent: 5,
        }
    }

    pub fn volume_up(&mut self) -> VolumeState {
        if self.is_muted {
            self.is_muted = false;
        }
        self.current_volume = (self.current_volume + self.step_percent).min(100);
        self.get_state()
    }

    pub fn volume_down(&mut self) -> VolumeState {
        if self.is_muted {
            self.is_muted = false;
        }
        self.current_volume = self.current_volume.saturating_sub(self.step_percent);
        self.get_state()
    }

    pub fn set_volume(&mut self, level: u8) -> VolumeState {
        self.current_volume = level.min(100);
        if self.current_volume > 0 {
            self.is_muted = false;
        }
        self.get_state()
    }

    pub fn toggle_mute(&mut self) -> VolumeState {
        if self.is_muted {
            self.is_muted = false;
            self.current_volume = self.pre_mute_volume.max(5);
        } else {
            self.is_muted = true;
            self.pre_mute_volume = self.current_volume;
            self.current_volume = 0;
        }
        self.get_state()
    }

    pub fn get_state(&self) -> VolumeState {
        VolumeState {
            level_percent: self.current_volume,
            is_muted: self.is_muted,
        }
    }
}

