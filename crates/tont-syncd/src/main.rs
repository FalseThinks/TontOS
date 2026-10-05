//! tont-syncd entry point
use tont_common::SyncWindowConfig;
use tont_syncd::SyncScheduler;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Radio Synchronization Daemon (tont-syncd)...");

    let config = SyncWindowConfig::default();
    let _scheduler = SyncScheduler::new(config.clone());
    tracing::info!(
        "Batch window configured: 1 window every {}s lasting {}s",
        config.interval_secs,
        config.duration_secs
    );
}
