//! TontOS Quick Settings Drop-Down Shade & Detox Engine (tont-shell::shade)
//! Provides drop-down hardware controls (brightness, volume, toggles) and digital detox intentionality tracking.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct BrightnessControl {
    pub percent: u8,
}

impl Default for BrightnessControl {
    fn default() -> Self {
        Self { percent: 40 } // Sensible default for battery saving
    }
}

impl BrightnessControl {
    pub fn new(percent: u8) -> Self {
        Self {
            percent: percent.min(100),
        }
    }

    /// Convert percentage (0..100) to raw hardware backlight sysfs integer.
    pub fn scale_to_raw(percent: u8, max_brightness: u32) -> u32 {
        let p = percent.min(100) as u32;
        (p * max_brightness) / 100
    }

    /// Convert raw hardware backlight sysfs integer to percentage (0..100).
    pub fn scale_from_raw(raw: u32, max_brightness: u32) -> u8 {
        if max_brightness == 0 {
            return 0;
        }
        let clamped = raw.min(max_brightness);
        ((clamped * 100) / max_brightness) as u8
    }

    pub fn set_percent(&mut self, val: u8) {
        self.percent = val.min(100);
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct VolumeControl {
    pub percent: u8,
    pub is_muted: bool,
}

impl Default for VolumeControl {
    fn default() -> Self {
        Self {
            percent: 60,
            is_muted: false,
        }
    }
}

impl VolumeControl {
    pub fn step_up(&mut self, step: u8) {
        self.percent = (self.percent + step).min(100);
        self.is_muted = false;
    }

    pub fn step_down(&mut self, step: u8) {
        self.percent = self.percent.saturating_sub(step);
    }

    pub fn toggle_mute(&mut self) {
        self.is_muted = !self.is_muted;
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DetoxTracker {
    pub screen_on_secs_today: u64,
    pub daily_budget_secs: u64,
}

impl Default for DetoxTracker {
    fn default() -> Self {
        Self {
            screen_on_secs_today: 0,
            daily_budget_secs: 7200, // 2h default
        }
    }
}

impl DetoxTracker {
    pub fn record_screen_on(&mut self, elapsed_secs: u64) {
        self.screen_on_secs_today += elapsed_secs;
    }

    pub fn set_daily_budget_mins(&mut self, mins: u32) {
        self.daily_budget_secs = (mins as u64) * 60;
    }

    pub fn is_exceeded(&self) -> bool {
        self.screen_on_secs_today > self.daily_budget_secs
    }

    pub fn format_screen_time(&self) -> String {
        let mins = self.screen_on_secs_today / 60;
        let hours = mins / 60;
        let remaining_mins = mins % 60;

        let budget_mins = self.daily_budget_secs / 60;
        let budget_hours = budget_mins / 60;
        let budget_rem = budget_mins % 60;

        let budget_str = if budget_hours > 0 && budget_rem == 0 {
            format!("{}h", budget_hours)
        } else if budget_hours > 0 {
            format!("{}h {}m", budget_hours, budget_rem)
        } else {
            format!("{}m", budget_mins)
        };

        let usage_str = if hours > 0 {
            format!("{}h {:02}m", hours, remaining_mins)
        } else {
            format!("{}m", mins)
        };

        if self.is_exceeded() {
            format!("[ {} / {} budget ! EXCEEDED ]", usage_str, budget_str)
        } else {
            format!("[ {} / {} budget ]", usage_str, budget_str)
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct QuickShade {
    pub is_open: bool,
    pub brightness: BrightnessControl,
    pub volume: VolumeControl,
    pub detox: DetoxTracker,
}

impl QuickShade {
    pub fn toggle(&mut self) {
        self.is_open = !self.is_open;
    }

    pub fn pull_down(&mut self) {
        self.is_open = true;
    }

    pub fn dismiss(&mut self) {
        self.is_open = false;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_brightness_scaling() {
        assert_eq!(BrightnessControl::scale_to_raw(50, 255), 127);
        assert_eq!(BrightnessControl::scale_to_raw(100, 4096), 4096);
        assert_eq!(BrightnessControl::scale_from_raw(2048, 4096), 50);
    }

    #[test]
    fn test_volume_adjustments() {
        let mut vol = VolumeControl::default();
        vol.step_up(10);
        assert_eq!(vol.percent, 70);

        vol.step_down(20);
        assert_eq!(vol.percent, 50);

        vol.toggle_mute();
        assert!(vol.is_muted);
        vol.step_up(5);
        assert!(!vol.is_muted); // Stepping up unmutes
    }

    #[test]
    fn test_detox_tracker() {
        let mut detox = DetoxTracker::default(); // 2h budget default
        detox.record_screen_on(1800); // 30 minutes
        assert_eq!(detox.format_screen_time(), "[ 30m / 2h budget ]");
        assert!(!detox.is_exceeded());

        detox.record_screen_on(3600); // 1h 30m total
        assert_eq!(detox.format_screen_time(), "[ 1h 30m / 2h budget ]");
        assert!(!detox.is_exceeded());

        detox.record_screen_on(3600); // 2h 30m total (exceeded)
        assert_eq!(detox.format_screen_time(), "[ 2h 30m / 2h budget ! EXCEEDED ]");
        assert!(detox.is_exceeded());
    }

    #[test]
    fn test_quick_shade_toggle() {
        let mut shade = QuickShade::default();
        assert!(!shade.is_open);

        shade.pull_down();
        assert!(shade.is_open);

        shade.dismiss();
        assert!(!shade.is_open);
    }
}
