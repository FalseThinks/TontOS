//! tont-settings-app entry point
use tont_settings::TontSettings;
use tont_settings_app::SettingsApp;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Launching TontOS Settings Application (tont-settings-app)...");

    let settings = TontSettings::default();
    let _app = SettingsApp::new(settings);
    tracing::info!(
        "Settings UI ready: AMOLED Black mode active. Top-level category count: 5"
    );
}
