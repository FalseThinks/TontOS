//! tont-appd entry point
use std::path::Path;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS App Lifecycle Daemon (tont-appd)...");

    let cgroup_root = Path::new("/sys/fs/cgroup/tontos.slice");
    if cgroup_root.exists() {
        tracing::info!("TontOS cgroup root detected at {:?}", cgroup_root);
    } else {
        tracing::info!("Running without dedicated cgroup slice. Will manage per-app cgroups dynamically.");
    }
}
