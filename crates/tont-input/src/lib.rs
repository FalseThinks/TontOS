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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_power_short_press_toggles_display() {
        let mut proc = KeyProcessor::new();
        proc.handle_key_down(HardwareKey::Power, 1000);
        let action = proc.handle_key_up(HardwareKey::Power, 1200);
        assert_eq!(action, Some(KeyAction::ToggleDisplay));
    }

    #[test]
    fn test_power_long_press_opens_menu() {
        let mut proc = KeyProcessor::new();
        proc.handle_key_down(HardwareKey::Power, 1000);
        let action = proc.handle_key_up(HardwareKey::Power, 2500);
        assert_eq!(action, Some(KeyAction::OpenPowerMenu));
    }

    #[test]
    fn test_volume_keys() {
        let mut proc = KeyProcessor::new();
        proc.handle_key_down(HardwareKey::VolumeUp, 1000);
        assert_eq!(
            proc.handle_key_up(HardwareKey::VolumeUp, 1050),
            Some(KeyAction::VolumeUp)
        );

        proc.handle_key_down(HardwareKey::VolumeDown, 2000);
        assert_eq!(
            proc.handle_key_up(HardwareKey::VolumeDown, 2050),
            Some(KeyAction::VolumeDown)
        );
    }

    #[test]
    fn test_emergency_sos_five_presses() {
        let mut proc = KeyProcessor::new();
        for i in 0..4 {
            proc.handle_key_down(HardwareKey::Power, i * 200);
            assert_eq!(
                proc.handle_key_up(HardwareKey::Power, i * 200 + 50),
                Some(KeyAction::ToggleDisplay)
            );
        }
        // 5th tap triggers SOS
        proc.handle_key_down(HardwareKey::Power, 800);
        assert_eq!(
            proc.handle_key_up(HardwareKey::Power, 850),
            Some(KeyAction::EmergencySos)
        );
    }

    #[test]
    fn test_raw_input_event_deserialization() {
        let mut bytes = [0u8; 24];
        // time_sec = 10
        bytes[0..8].copy_from_slice(&10i64.to_ne_bytes());
        // time_usec = 500000 (500ms)
        bytes[8..16].copy_from_slice(&500000i64.to_ne_bytes());
        // type = EV_KEY (1)
        bytes[16..18].copy_from_slice(&EV_KEY.to_ne_bytes());
        // code = KEY_VOLUMEUP (115)
        bytes[18..20].copy_from_slice(&KEY_VOLUMEUP.to_ne_bytes());
        // value = 1 (down)
        bytes[20..24].copy_from_slice(&1i32.to_ne_bytes());

        let raw = RawInputEvent::from_bytes(&bytes).expect("valid input event");
        assert_eq!(raw.code, KEY_VOLUMEUP);
        assert_eq!(raw.value, 1);

        let (key, is_down, timestamp_ms) = raw.to_hardware_event().expect("hardware event");
        assert_eq!(key, HardwareKey::VolumeUp);
        assert!(is_down);
        assert_eq!(timestamp_ms, 10500);
    }

    #[test]
    fn test_audio_mixer_controller() {
        let mut mixer = AudioMixerController::new(50);
        assert_eq!(mixer.get_state().level_percent, 50);

        let state = mixer.volume_up();
        assert_eq!(state.level_percent, 55);

        let state = mixer.volume_down();
        assert_eq!(state.level_percent, 50);

        let state = mixer.toggle_mute();
        assert!(state.is_muted);
        assert_eq!(state.level_percent, 0);

        let state = mixer.toggle_mute();
        assert!(!state.is_muted);
        assert_eq!(state.level_percent, 50);

        let state = mixer.set_volume(98);
        assert_eq!(state.level_percent, 98);
        let state = mixer.volume_up();
        assert_eq!(state.level_percent, 100); // capped at 100
    }
}
