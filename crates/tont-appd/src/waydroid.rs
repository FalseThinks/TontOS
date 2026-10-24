use std::io;
use std::process::Command;
use std::time::Duration;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum WaydroidError {
    #[error("Invalid Android package name: {0}")]
    InvalidPackageName(String),
    #[error("Could not execute {command}: {source}")]
    CommandIo {
        command: String,
        #[source]
        source: io::Error,
    },
    #[error("{command} failed with status {status:?}: {stderr}")]
    CommandFailed {
        command: String,
        status: Option<i32>,
        stderr: String,
    },
    #[error("{command} timed out after {timeout_secs}s")]
    CommandTimeout { command: String, timeout_secs: u64 },
    #[error("Partial Waydroid start: container started but session start failed: {source}")]
    PartialStart {
        #[source]
        source: Box<WaydroidError>,
    },
    #[error(
        "Partial Waydroid stop: the session stopped, but stopping the container service failed: {source}"
    )]
    PartialStop {
        #[source]
        source: Box<WaydroidError>,
    },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommandOutput {
    pub success: bool,
    pub status: Option<i32>,
    pub stdout: String,
    pub stderr: String,
}

pub trait CommandRunner {
    fn run(&mut self, program: &str, args: &[&str]) -> io::Result<CommandOutput>;

    fn run_with_timeout(
        &mut self,
        program: &str,
        args: &[&str],
        _timeout: Duration,
    ) -> io::Result<CommandOutput> {
        self.run(program, args)
    }
}

#[derive(Debug, Default)]
pub struct SystemCommandRunner;

impl CommandRunner for SystemCommandRunner {
    fn run(&mut self, program: &str, args: &[&str]) -> io::Result<CommandOutput> {
        self.run_with_timeout(program, args, Duration::from_secs(120))
    }

    fn run_with_timeout(
        &mut self,
        program: &str,
        args: &[&str],
        timeout: Duration,
    ) -> io::Result<CommandOutput> {
        use std::process::Stdio;
        use std::sync::mpsc;

        let child = Command::new(program)
            .args(args)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()?;

        let child_pid = child.id();
        let (tx, rx) = mpsc::channel();
        std::thread::spawn(move || {
            let out = child.wait_with_output();
            let _ = tx.send(out);
        });

        match rx.recv_timeout(timeout) {
            Ok(Ok(output)) => Ok(CommandOutput {
                success: output.status.success(),
                status: output.status.code(),
                stdout: String::from_utf8_lossy(&output.stdout).into_owned(),
                stderr: String::from_utf8_lossy(&output.stderr).into_owned(),
            }),
            Ok(Err(e)) => Err(e),
            Err(mpsc::RecvTimeoutError::Timeout) => {
                if let Some(pid) = rustix::process::Pid::from_raw(child_pid as i32) {
                    let _ = rustix::process::kill_process(pid, rustix::process::Signal::Kill);
                }
                Err(io::Error::new(
                    io::ErrorKind::TimedOut,
                    format!("{program} timed out after {}s", timeout.as_secs()),
                ))
            }
            Err(mpsc::RecvTimeoutError::Disconnected) => {
                Err(io::Error::other("runner thread disconnected"))
            }
        }
    }
}

pub struct WaydroidController<R = SystemCommandRunner> {
    pub runner: R,
}

impl WaydroidController<SystemCommandRunner> {
    pub fn new() -> Self {
        Self::with_runner(SystemCommandRunner)
    }
}

impl Default for WaydroidController<SystemCommandRunner> {
    fn default() -> Self {
        Self::new()
    }
}

impl<R: CommandRunner> WaydroidController<R> {
    pub fn with_runner(runner: R) -> Self {
        Self { runner }
    }

    pub fn status(&mut self) -> Result<String, WaydroidError> {
        self.execute("waydroid", &["status"])
    }

    /// Start the privileged container through systemd, then start this user's session.
    ///
    /// Run this command as the logged-in Wayland user. Any authorization required
    /// for the system unit must be provided by system policy; this adapter does not
    /// elevate privileges or switch users. If the session fails after the container
    /// started, a compensating container stop is attempted and the error is wrapped
    /// as PartialStart so callers can remediate (e.g. `systemctl status waydroid-container.service`).
    pub fn start_session(&mut self) -> Result<(), WaydroidError> {
        self.execute("systemctl", &["start", "waydroid-container.service"])?;
        if let Err(source) = self.execute("waydroid", &["session", "start"]) {
            let _ = self
                .runner
                .run("systemctl", &["stop", "waydroid-container.service"]);
            return Err(WaydroidError::PartialStart {
                source: Box::new(source),
            });
        }
        Ok(())
    }

    /// Stop this user's session before asking systemd to stop the container.
    pub fn stop_session(&mut self) -> Result<(), WaydroidError> {
        self.execute("waydroid", &["session", "stop"])?;
        self.execute("systemctl", &["stop", "waydroid-container.service"])
            .map_err(|source| WaydroidError::PartialStop {
                source: Box::new(source),
            })?;
        Ok(())
    }

    /// Launch an installed Android package in the current Wayland user's session.
    pub fn launch_app(&mut self, package: &str) -> Result<(), WaydroidError> {
        if !Self::is_valid_package_name(package) {
            return Err(WaydroidError::InvalidPackageName(package.to_owned()));
        }

        self.execute("waydroid", &["app", "launch", package])?;
        Ok(())
    }

    pub fn is_valid_package_name(package: &str) -> bool {
        if package.is_empty() || package.len() > 128 || !package.contains('.') {
            return false;
        }

        package.split('.').all(|part| {
            let mut chars = part.chars();
            matches!(chars.next(), Some(first) if first.is_ascii_alphabetic())
                && chars.all(|character| character.is_ascii_alphanumeric() || character == '_')
        })
    }

    fn execute(&mut self, program: &str, args: &[&str]) -> Result<String, WaydroidError> {
        self.execute_with_timeout(program, args, Duration::from_secs(120))
    }

    fn execute_with_timeout(
        &mut self,
        program: &str,
        args: &[&str],
        timeout: Duration,
    ) -> Result<String, WaydroidError> {
        let output = self
            .runner
            .run_with_timeout(program, args, timeout)
            .map_err(|source| {
                let command = std::iter::once(program)
                    .chain(args.iter().copied())
                    .collect::<Vec<_>>()
                    .join(" ");
                if source.kind() == io::ErrorKind::TimedOut {
                    WaydroidError::CommandTimeout {
                        command,
                        timeout_secs: timeout.as_secs(),
                    }
                } else {
                    WaydroidError::CommandIo { command, source }
                }
            })?;

        if !output.success {
            let command = std::iter::once(program)
                .chain(args.iter().copied())
                .collect::<Vec<_>>()
                .join(" ");
            return Err(WaydroidError::CommandFailed {
                command,
                status: output.status,
                stderr: output.stderr.trim_end().to_owned(),
            });
        }

        Ok(output.stdout)
    }
}
