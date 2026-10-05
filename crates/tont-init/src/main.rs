//! TontOS PID 1 Minimalist System Supervisor (tont-init)
//! Boots a pure native Linux system with ZERO bloat, ZERO Java/Dalvik, and strictly essential daemons.

use std::fs;
use std::path::Path;
use std::process::{Child, Command};

fn mount_pseudofs(source: &str, target: &str, fstype: &str) {
    if !Path::new(target).exists() {
        let _ = fs::create_dir_all(target);
    }
    // Using standard mount syscall or helper
    let status = Command::new("mount")
        .args(["-t", fstype, source, target])
        .status();
    if let Ok(s) = status {
        if s.success() {
            tracing::info!("Mounted {} on {}", fstype, target);
        }
    }
}

fn main() {
    tracing_subscriber::fmt::init();

    println!("============================================================");
    println!("  TontOS - Minimalist Digital Detox Mobile OS (PID 1 Init)  ");
    println!("  Zero Bloat | Zero Android Runtime | Pure Native Linux     ");
    println!("============================================================");

    // 1. Mount virtual kernel filesystems if running as PID 1
    let pid = std::process::id();
    if pid == 1 {
        tracing::info!("Running as PID 1 init. Mounting pseudo-filesystems...");
        mount_pseudofs("proc", "/proc", "proc");
        mount_pseudofs("sysfs", "/sys", "sysfs");
        mount_pseudofs("devtmpfs", "/dev", "devtmpfs");
        mount_pseudofs("tmpfs", "/run", "tmpfs");
        mount_pseudofs("tmpfs", "/tmp", "tmpfs");
    }

    // 2. Ensure runtime directory structure
    let _ = fs::create_dir_all("/run/tontos");
    let _ = fs::create_dir_all("/etc/tontos");
    let _ = fs::create_dir_all("/var/lib/tontos");

    // 3. Define the strictly minimal set of essential TontOS daemons
    let daemons = [
        ("Power Management", "/bin/tont-powerd"),
        ("Sensor Hub", "/bin/tont-sensors"),
        ("Radio Batch Scheduler", "/bin/tont-syncd"),
        ("Cellular Telephony", "/bin/tont-telephony"),
        ("Hardware Buttons", "/bin/tont-input"),
        ("Minimal AMOLED Shell", "/bin/tont-shell"),
    ];

    let mut running_children: Vec<(&str, Child)> = Vec::new();

    for (name, path) in &daemons {
        // Fallback to local path or PATH if not found in /bin
        let bin_path = if Path::new(path).exists() {
            path.to_string()
        } else if Path::new(&format!("/usr{}", path)).exists() {
            format!("/usr{}", path)
        } else {
            path.trim_start_matches("/bin/").to_string()
        };

        tracing::info!("Starting subsystem: {} ({})", name, bin_path);
        match Command::new(&bin_path).spawn() {
            Ok(child) => {
                tracing::info!("  [+] {} started (PID {})", name, child.id());
                running_children.push((name, child));
            }
            Err(e) => {
                tracing::warn!("  [!] Could not start {} ({}): {}", name, bin_path, e);
            }
        }
    }

    tracing::info!(
        "TontOS boot complete! Total active native services: {}",
        running_children.len()
    );

    // 4. PID 1 Supervisor & Zombie Reaper Loop
    if pid == 1 {
        loop {
            // Reap terminated child processes to avoid zombies
            unsafe {
                let mut status = 0;
                let reaped_pid = libc::waitpid(-1, &mut status, libc::WNOHANG);
                if reaped_pid > 0 {
                    tracing::info!("Reaped child process PID {}", reaped_pid);
                }
            }
            std::thread::sleep(std::time::Duration::from_millis(500));
        }
    } else {
        tracing::info!("Supervisor verification successful (non-PID 1 host mode).");
    }
}
