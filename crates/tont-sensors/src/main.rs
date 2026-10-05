//! tont-sensors entry point
use std::path::Path;
use tont_sensors::SensorHub;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Sensor Daemon (tont-sensors)...");

    let iio_dir = Path::new("/sys/bus/iio/devices");
    if iio_dir.exists() {
        tracing::info!("Linux IIO device subsystem detected at {:?}", iio_dir);
    } else {
        tracing::info!("Virtual development environment (IIO simulated).");
    }

    // Default upright orientation
    let orientation = SensorHub::compute_orientation(0, -980, 0);
    tracing::info!("Initial screen orientation: {:?}", orientation);
}
