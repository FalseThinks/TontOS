//! tont-settings entry point
use std::path::Path;
use tont_settings::TontSettings;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Settings & Customization Daemon (tont-settings)...");

    let config_path = Path::new("/etc/tontos/settings.json");
    let settings = if config_path.exists() {
        TontSettings::load_from_file(config_path)
            .unwrap_or_else(|_| TontSettings::embedded_default())
    } else {
        TontSettings::embedded_default()
    };

    tracing::info!(
        "Connectivity status: WiFi={}, BT={}, MobileData={}, GPS={}",
        settings.connectivity.wifi_enabled,
        settings.connectivity.bluetooth_enabled,
        settings.connectivity.mobile_data_enabled,
        settings.connectivity.gps_enabled
    );
    tracing::info!("Active Ringtone: {}", settings.sound.ringtone_path);
    tracing::info!("Default Phone App: {}", settings.default_apps.phone_app);
}
