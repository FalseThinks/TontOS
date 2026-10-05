//! tont-telephony entry point
use tont_telephony::TelephonyManager;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Telephony Daemon (tont-telephony)...");

    let mgr = TelephonyManager::new();
    tracing::info!(
        "Telephony core online. Network state: {:?}",
        mgr.network_status
    );
}
