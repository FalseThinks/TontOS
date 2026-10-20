use tont_shell::shade::{BrightnessControl, DetoxTracker, QuickShade, VolumeControl};

#[test]
fn test_brightness_scaling() {
    assert_eq!(BrightnessControl::scale_to_raw(50, 255), 127);
    assert_eq!(BrightnessControl::scale_to_raw(100, 4096), 4096);
    assert_eq!(BrightnessControl::scale_from_raw(2048, 4096), 50);
}

#[test]
fn test_brightness_clamping() {
    let mut b = BrightnessControl::new(150);
    assert_eq!(b.percent, 100);

    b.set_percent(200);
    assert_eq!(b.percent, 100);

    b.set_percent(0);
    assert_eq!(b.percent, 0);

    // Zero max_brightness safe guard
    assert_eq!(BrightnessControl::scale_from_raw(50, 0), 0);
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
fn test_volume_boundary_clamping() {
    let mut vol = VolumeControl::default();
    vol.step_up(100);
    assert_eq!(vol.percent, 100);

    vol.step_down(150);
    assert_eq!(vol.percent, 0);
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
    assert_eq!(
        detox.format_screen_time(),
        "[ 2h 30m / 2h budget ! EXCEEDED ]"
    );
    assert!(detox.is_exceeded());
}

#[test]
fn test_detox_custom_budget_minutes() {
    let mut detox = DetoxTracker::default();
    detox.set_daily_budget_mins(45); // 45m budget
    assert_eq!(detox.daily_budget_secs, 2700);

    detox.record_screen_on(600); // 10m
    assert_eq!(detox.format_screen_time(), "[ 10m / 45m budget ]");

    detox.set_daily_budget_mins(90); // 1h 30m
    assert_eq!(detox.format_screen_time(), "[ 10m / 1h 30m budget ]");
}

#[test]
fn test_quick_shade_toggle() {
    let mut shade = QuickShade::default();
    assert!(!shade.is_open);

    shade.pull_down();
    assert!(shade.is_open);

    shade.dismiss();
    assert!(!shade.is_open);

    shade.toggle();
    assert!(shade.is_open);
    shade.toggle();
    assert!(!shade.is_open);
}
