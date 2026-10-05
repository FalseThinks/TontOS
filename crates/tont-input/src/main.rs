//! tont-input entry point
use std::path::Path;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::UnixListener;
use tokio::sync::Mutex;
use tont_input::{
    AudioMixerController, KeyAction, KeyProcessor, RawInputEvent,
};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    tracing::info!("Starting TontOS Hardware Button Processor (tont-input)...");

    let processor = Arc::new(Mutex::new(KeyProcessor::new()));
    let mixer = Arc::new(Mutex::new(AudioMixerController::new(80)));

    // Ensure socket directory exists or fallback to /data/local/tmp or /tmp
    let socket_path = if Path::new("/run/tontos").exists() {
        "/run/tontos/volume.sock"
    } else if Path::new("/data/local/tmp").exists() {
        "/data/local/tmp/tontos_volume.sock"
    } else {
        "/tmp/tontos_volume.sock"
    };

    let _ = std::fs::remove_file(socket_path);
    let listener = UnixListener::bind(socket_path).ok();
    if listener.is_some() {
        tracing::info!("Volume IPC server listening on {}", socket_path);
    } else {
        tracing::warn!("Volume IPC socket unavailable at {} (SELinux/restricted). Hardware evdev monitoring active.", socket_path);
    }

    // Scan /dev/input for key event sources
    let mut input_nodes = Vec::new();
    if let Ok(entries) = std::fs::read_dir("/dev/input") {
        for entry in entries.flatten() {
            let path = entry.path();
            if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                if name.starts_with("event") {
                    input_nodes.push(path);
                }
            }
        }
    }
    tracing::info!("Discovered {} evdev input nodes in /dev/input", input_nodes.len());

    // Spawn evdev monitoring tasks for accessible event devices
    for node in input_nodes {
        let node_path = node.clone();
        let proc = Arc::clone(&processor);
        let mix = Arc::clone(&mixer);

        tokio::spawn(async move {
            if let Ok(mut file) = tokio::fs::File::open(&node_path).await {
                tracing::info!("Monitoring evdev node {:?}", node_path);
                let mut buf = [0u8; 24];
                while file.read_exact(&mut buf).await.is_ok() {
                    if let Some(raw) = RawInputEvent::from_bytes(&buf) {
                        if let Some((key, is_down, ts)) = raw.to_hardware_event() {
                            let mut p = proc.lock().await;
                            let action = if is_down {
                                p.handle_key_down(key, ts);
                                None
                            } else {
                                p.handle_key_up(key, ts)
                            };

                            if let Some(action) = action {
                                match action {
                                    KeyAction::VolumeUp => {
                                        let mut m = mix.lock().await;
                                        let state = m.volume_up();
                                        tracing::info!("Hardware VolumeUp -> {}%", state.level_percent);
                                    }
                                    KeyAction::VolumeDown => {
                                        let mut m = mix.lock().await;
                                        let state = m.volume_down();
                                        tracing::info!("Hardware VolumeDown -> {}%", state.level_percent);
                                    }
                                    KeyAction::ToggleDisplay => {
                                        tracing::info!("Hardware Power Tap -> Toggle Display");
                                    }
                                    KeyAction::OpenPowerMenu => {
                                        tracing::warn!("Hardware Power Long-Press -> Open Power Menu");
                                    }
                                    KeyAction::EmergencySos => {
                                        tracing::error!("Hardware 5-Tap -> EMERGENCY SOS TRIGGERED!");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    // Process IPC commands on socket if bound
    if let Some(listener) = listener {
        loop {
            if let Ok((mut stream, _)) = listener.accept().await {
                let mix = Arc::clone(&mixer);
                tokio::spawn(async move {
                    let mut buf = [0u8; 64];
                    if let Ok(n) = stream.read(&mut buf).await {
                        let cmd = String::from_utf8_lossy(&buf[..n]).trim().to_string();
                        let mut m = mix.lock().await;
                        let state = match cmd.as_str() {
                            "UP" => m.volume_up(),
                            "DOWN" => m.volume_down(),
                            "MUTE" => m.toggle_mute(),
                            "GET" => m.get_state(),
                            s if s.starts_with("SET:") => {
                                let val: u8 = s[4..].parse().unwrap_or(50);
                                m.set_volume(val)
                            }
                            _ => m.get_state(),
                        };
                        let response = serde_json::to_vec(&state).unwrap_or_default();
                        let _ = stream.write_all(&response).await;
                    }
                });
            }
        }
    } else {
        // Keep process running to process background evdev hardware tasks
        tokio::signal::ctrl_c().await?;
    }
    Ok(())
}
