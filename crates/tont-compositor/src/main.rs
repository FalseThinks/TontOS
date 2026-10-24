//! tont-compositor entry point
use tont_compositor::DirectScanoutEvaluator;

fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Wayland Compositor (tont-compositor)...");

    let evaluator = DirectScanoutEvaluator::new(1080, 2400);
    tracing::info!(
        "Display scanout pipeline initialized: {}x{} portrait",
        evaluator.display_width,
        evaluator.display_height
    );
}
