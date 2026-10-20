use std::fs;
use tont_sensors::{ProximityState, ScreenOrientation, SensorError, SensorHub};

#[test]
fn test_orientation_detection() {
    // Normal upright portrait (gravity pulling downwards along -Y)
    assert_eq!(
        SensorHub::compute_orientation(50, -980, 100),
        ScreenOrientation::Portrait
    );

    // Landscape Left (gravity along -X)
    assert_eq!(
        SensorHub::compute_orientation(-980, 50, 100),
        ScreenOrientation::LandscapeLeft
    );

    // Landscape Right (gravity along +X)
    assert_eq!(
        SensorHub::compute_orientation(980, 50, 100),
        ScreenOrientation::LandscapeRight
    );

    // Inverted portrait
    assert_eq!(
        SensorHub::compute_orientation(0, 980, 100),
        ScreenOrientation::PortraitInverted
    );
}

#[test]
fn test_proximity_thresholding() {
    assert_eq!(SensorHub::compute_proximity(100, 50), ProximityState::Near);
    assert_eq!(SensorHub::compute_proximity(20, 50), ProximityState::Far);
}

#[test]
fn test_read_iio_channel() {
    let temp_dir = std::env::temp_dir().join("tont_test_iio");
    fs::create_dir_all(&temp_dir).unwrap();
    let node = temp_dir.join("in_illuminance_raw");
    fs::write(&node, "350\n").unwrap();

    let val = SensorHub::read_iio_channel(&node).unwrap();
    assert_eq!(val, 350);

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_proximity_boundary_conditions() {
    // Exactly at threshold
    assert_eq!(SensorHub::compute_proximity(50, 50), ProximityState::Near);
    // Just below threshold
    assert_eq!(SensorHub::compute_proximity(49, 50), ProximityState::Far);
    // Zero threshold
    assert_eq!(SensorHub::compute_proximity(0, 0), ProximityState::Near);
    // Negative values
    assert_eq!(SensorHub::compute_proximity(-1, 0), ProximityState::Far);
}

#[test]
fn test_read_iio_channel_parse_error() {
    let temp_dir = std::env::temp_dir().join("tont_test_iio_err");
    fs::create_dir_all(&temp_dir).unwrap();
    let node = temp_dir.join("in_illuminance_raw");
    fs::write(&node, "invalid_number\n").unwrap();

    let res = SensorHub::read_iio_channel(&node);
    assert!(res.is_err());
    match res.unwrap_err() {
        SensorError::Parse(_) => {}
        _ => panic!("Expected Parse error"),
    }

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn test_orientation_extreme_values() {
    assert_eq!(
        SensorHub::compute_orientation(0, i32::MIN, 0),
        ScreenOrientation::Portrait
    );
    assert_eq!(
        SensorHub::compute_orientation(i32::MAX, 0, 0),
        ScreenOrientation::LandscapeRight
    );
}

#[test]
fn test_orientation_axis_equality() {
    // When abs_y >= abs_x, portrait dominates
    assert_eq!(
        SensorHub::compute_orientation(500, -500, 0),
        ScreenOrientation::Portrait
    );
    assert_eq!(
        SensorHub::compute_orientation(500, 500, 0),
        ScreenOrientation::PortraitInverted
    );
}

#[test]
fn test_orientation_serde_roundtrip() {
    let orientations = [
        ScreenOrientation::Portrait,
        ScreenOrientation::PortraitInverted,
        ScreenOrientation::LandscapeLeft,
        ScreenOrientation::LandscapeRight,
    ];
    for o in orientations {
        let json = serde_json::to_string(&o).unwrap();
        let de: ScreenOrientation = serde_json::from_str(&json).unwrap();
        assert_eq!(o, de);
    }
}
