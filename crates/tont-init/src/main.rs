//! TontOS PID 1 Minimalist System Supervisor binary (tont-init)

use std::fs;
use std::path::Path;
use std::process::Command;
use tont_init::{mount_pseudofs, reap_available_children, supervise_children};

fn main() {
    tracing_subscriber::fmt::init();
    tracing::info!("TontOS PID 1 Init starting");

    // 1. Mount virtual kernel filesystems if running as PID 1
    let pid = std::process::id();
    if pid == 1 {
        tracing::info!("Running as PID 1 init. Mounting pseudo-filesystems...");
        for (source, target, fstype) in [
            ("proc", "/proc", "proc"),
            ("sysfs", "/sys", "sysfs"),
            ("devtmpfs", "/dev", "devtmpfs"),
            ("tmpfs", "/run", "tmpfs"),
            ("tmpfs", "/tmp", "tmpfs"),
        ] {
            if let Err(error) = mount_pseudofs(source, target, fstype) {
                tracing::error!("Failed to mount {fstype} on {target}: {error}");
            }
        }
    }

    // 2. Ensure runtime directory structure
    let _ = fs::create_dir_all("/run/tontos");
    let _ = fs::create_dir_all("/etc/tontos");
    let _ = fs::create_dir_all("/var/lib/tontos");

    // 3. Define the strictly minimal set of essential TontOS daemons.
    let daemons = [
        ("Power Management", "/bin/tont-powerd"),
        ("Sensor Hub", "/bin/tont-sensors"),
        ("Radio Batch Scheduler", "/bin/tont-syncd"),
        ("Cellular Telephony", "/bin/tont-telephony"),
        ("Hardware Buttons", "/bin/tont-input"),
        ("Minimal AMOLED Shell", "/bin/tont-shell"),
    ];

    let mut started_count: usize = 0;

    for (name, path) in &daemons {
        let bin_path = if Path::new(path).exists() {
            path.to_string()
        } else {
            let usr_path = format!("/usr{path}");
            if Path::new(&usr_path).exists() {
                usr_path
            } else {
                tracing::warn!(
                    "  [!] Could not start {name}: {path} not found at /bin or /usr/bin, failing closed (no PATH fallback)"
                );
                continue;
            }
        };

        tracing::info!("Starting subsystem: {} ({})", name, bin_path);
        let mut cmd = Command::new(&bin_path);
        cmd.env("PATH", "/usr/bin:/bin");
        match cmd.spawn() {
            Ok(child) => {
                tracing::info!("  [+] {} started (PID {})", name, child.id());
                started_count += 1;
            }
            Err(e) => {
                tracing::warn!("  [!] Could not start {} ({}): {}", name, bin_path, e);
            }
        }
    }

    tracing::info!(
        "TontOS boot complete! Total active native services: {}",
        started_count
    );

    // 4. PID 1 Supervisor & Zombie Reaper Loop
    if pid == 1 {
        if let Err(error) = supervise_children() {
            tracing::error!(
                "Event-driven child supervision failed; using periodic reaping: {}",
                error
            );
            loop {
                match reap_available_children() {
                    Ok(reaped_pids) => {
                        for reaped_pid in reaped_pids {
                            tracing::info!("Reaped child process PID {}", reaped_pid);
                        }
                    }
                    Err(error) => tracing::error!("Could not reap child processes: {}", error),
                }
                std::thread::sleep(std::time::Duration::from_millis(500));
            }
        }
    } else {
        tracing::info!("Supervisor verification successful (non-PID 1 host mode).");
    }
}
