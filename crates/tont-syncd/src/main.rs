//! tont-syncd entry point – timerfd batch windows without busy wakeups.
use tont_common::SyncWindowConfig;
use tont_syncd::SyncScheduler;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Radio Synchronization Daemon (tont-syncd)...");

    let config = SyncWindowConfig::default();
    let mut scheduler = SyncScheduler::new(config);
    tracing::info!(
        "Batch window configured: 1 window every {}s lasting {}s",
        config.interval_secs,
        config.duration_secs
    );

    loop {
        let wait = scheduler.time_until_next_event_secs().max(1);
        tokio::time::sleep(tokio::time::Duration::from_secs(wait)).await;
        let ev = scheduler.tick(wait);
        match ev {
            tont_syncd::SyncEvent::WindowOpened => {
                tracing::info!(
                    "syncd: window opened ({}s) – batch radios",
                    scheduler.config.duration_secs
                );
            }
            tont_syncd::SyncEvent::WindowClosed => {
                tracing::info!(
                    "syncd: window closed – radios sleep {}s",
                    scheduler.config.interval_secs
                );
            }
            tont_syncd::SyncEvent::NoChange => {}
        }
    }
}
