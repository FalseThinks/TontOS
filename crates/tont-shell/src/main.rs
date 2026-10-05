//! tont-shell entry point
use tont_shell::{AppRegistry, ShellState};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Minimalist AMOLED Shell (tont-shell)...");

    let registry = AppRegistry::default();
    let state = ShellState::new("10:00".to_string(), None, &registry);
    tracing::info!(
        "Shell initialized with {} available apps (F-Droid ready, Google-free base)",
        state.available_apps.len()
    );
}
