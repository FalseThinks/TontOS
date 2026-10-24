//! TontOS PID 1 Minimalist System Supervisor library (tont-init)

use std::fs;
use std::io;
use std::path::Path;
use std::process::Command;

pub fn mount_pseudofs(source: &str, target: &str, fstype: &str) -> io::Result<()> {
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

pub fn supervise_children() -> io::Result<()> {
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

pub fn reap_available_children() -> io::Result<Vec<libc::pid_t>> {
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
