//! TontOS PID 1 Minimalist System Supervisor (tont-init)
//! Boots a pure native Linux system with ZERO bloat, ZERO Java/Dalvik, and strictly essential daemons.

use std::fs;
use std::io;
use std::path::Path;
use std::process::{Child, Command};

fn mount_pseudofs(source: &str, target: &str, fstype: &str) -> io::Result<()> {
    if !Path::new(target).exists() {
        fs::create_dir_all(target)?;
    }
    let status = Command::new("mount")
        .args(["-t", fstype, source, target])
        .status()?;
    if status.success() {
        tracing::info!("Mounted {} on {}", fstype, target);
        Ok(())
    } else {
        Err(io::Error::other(format!(
            "mount {fstype} on {target} failed: {status}"
        )))
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
    // Canonical list shared with scripts/make_gsi_rootfs.sh BINARIES block;
    // tont-init intentionally does NOT supervise tont-appd/compositor/backup here:
    // Waydroid lifecycle is owned by the logged-in Wayland session (see plan.md:99),
    // compositor/shell are session-owned, backup is on-demand.
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
        let bin_path = if Path::new(path).exists() {
            path.to_string()
        } else if Path::new(&format!("/usr{}", path)).exists() {
            format!("/usr{}", path)
        } else {
            tracing::warn!(
                "  [!] Could not start {name}: {path} not found at /bin or /usr/bin, failing closed (no PATH fallback)"
            );
            continue;
        };

        tracing::info!("Starting subsystem: {} ({})", name, bin_path);
        let mut cmd = Command::new(&bin_path);
        cmd.env("PATH", "/usr/bin:/bin");
        // Ensure clean env for PID 1; keep minimal required vars via env_clear fallback not needed here
        match cmd.spawn() {
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

fn supervise_children() -> io::Result<()> {
    let mut sigchld_set = unsafe { std::mem::zeroed::<libc::sigset_t>() };
    if unsafe { libc::sigemptyset(&mut sigchld_set) } == -1 {
        return Err(io::Error::last_os_error());
    }
    if unsafe { libc::sigaddset(&mut sigchld_set, libc::SIGCHLD) } == -1 {
        return Err(io::Error::last_os_error());
    }
    if unsafe { libc::sigprocmask(libc::SIG_BLOCK, &sigchld_set, std::ptr::null_mut()) } == -1 {
        return Err(io::Error::last_os_error());
    }

    loop {
        for reaped_pid in reap_available_children()? {
            tracing::info!("Reaped child process PID {}", reaped_pid);
        }

        let received_signal = unsafe { libc::sigwaitinfo(&sigchld_set, std::ptr::null_mut()) };
        if received_signal == libc::SIGCHLD {
            continue;
        }

        let error = io::Error::last_os_error();
        if error.kind() == io::ErrorKind::Interrupted {
            continue;
        }
        return Err(if received_signal == -1 {
            error
        } else {
            io::Error::other(format!(
                "unexpected signal {received_signal} while waiting for SIGCHLD"
            ))
        });
    }
}

fn reap_available_children() -> io::Result<Vec<libc::pid_t>> {
    let mut reaped_pids = Vec::new();

    loop {
        let mut status = 0;
        let reaped_pid = unsafe { libc::waitpid(-1, &mut status, libc::WNOHANG) };
        if reaped_pid > 0 {
            reaped_pids.push(reaped_pid);
            continue;
        }
        if reaped_pid == 0 {
            return Ok(reaped_pids);
        }

        let error = io::Error::last_os_error();
        match error.raw_os_error() {
            Some(libc::EINTR) => continue,
            Some(libc::ECHILD) => return Ok(reaped_pids),
            _ => return Err(error),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::reap_available_children;
    use std::time::{Duration, Instant};

    #[test]
    fn reaps_all_exited_children_in_one_pass() {
        let child_pids: Vec<libc::pid_t> = (0..3)
            .map(|_| {
                let pid = unsafe { libc::fork() };
                assert!(pid >= 0, "fork failed: {}", std::io::Error::last_os_error());
                if pid == 0 {
                    unsafe { libc::_exit(0) };
                }
                pid
            })
            .collect();

        for pid in &child_pids {
            wait_until_exited(*pid);
        }

        let mut reaped_pids = reap_available_children().expect("reaping children should succeed");
        reaped_pids.sort_unstable();
        let mut expected_pids = child_pids;
        expected_pids.sort_unstable();
        assert_eq!(reaped_pids, expected_pids);
    }

    fn wait_until_exited(pid: libc::pid_t) {
        let deadline = Instant::now() + Duration::from_secs(5);

        loop {
            let mut info = unsafe { std::mem::zeroed::<libc::siginfo_t>() };
            let result = unsafe {
                libc::waitid(
                    libc::P_PID,
                    pid as libc::id_t,
                    &mut info,
                    libc::WEXITED | libc::WNOHANG | libc::WNOWAIT,
                )
            };
            assert_eq!(
                result,
                0,
                "waitid failed: {}",
                std::io::Error::last_os_error()
            );
            if unsafe { info.si_pid() } == pid {
                return;
            }
            assert!(
                Instant::now() < deadline,
                "child {pid} did not exit in time"
            );
            std::thread::sleep(Duration::from_millis(1));
        }
    }
}
