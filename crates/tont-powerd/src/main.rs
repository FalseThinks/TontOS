//! tont-powerd entry point
use std::path::Path;
use tont_powerd::PowerController;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Power Daemon (tont-powerd)...");

    let battery_dir = Path::new("/sys/class/power_supply/battery");
    if battery_dir.exists() {
        match PowerController::read_battery(battery_dir) {
            Ok(info) => tracing::info!(
                "Battery capacity: {}%, status: {:?}",
                info.capacity_percent,
                info.status
            ),
            Err(e) => tracing::warn!("Failed to read battery: {}", e),
        }
    } else {
        tracing::info!("Running in virtualized environment (no hardware battery).");
    }
}
