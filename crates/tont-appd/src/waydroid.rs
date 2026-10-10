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
            Err(mpsc::RecvTimeoutError::Timeout) => Err(io::Error::new(
                io::ErrorKind::TimedOut,
                format!("{program} timed out after {}s", timeout.as_secs()),
            )),
            Err(mpsc::RecvTimeoutError::Disconnected) => {
                Err(io::Error::other("runner thread disconnected"))
            }
        }
    }
}

pub struct WaydroidController<R = SystemCommandRunner> {
    runner: R,
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
        let command = std::iter::once(program)
            .chain(args.iter().copied())
            .collect::<Vec<_>>()
            .join(" ");
        let output = self
            .runner
            .run_with_timeout(program, args, timeout)
            .map_err(|source| {
                if source.kind() == io::ErrorKind::TimedOut {
                    WaydroidError::CommandTimeout {
                        command: command.clone(),
                        timeout_secs: timeout.as_secs(),
                    }
                } else {
                    WaydroidError::CommandIo {
                        command: command.clone(),
                        source,
                    }
                }
            })?;

        if !output.success {
            return Err(WaydroidError::CommandFailed {
                command,
                status: output.status,
                stderr: output.stderr.trim_end().to_owned(),
            });
        }

        Ok(output.stdout)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;

    #[derive(Default)]
    struct FakeRunner {
        calls: Vec<(String, Vec<String>)>,
        results: VecDeque<io::Result<CommandOutput>>,
    }

    impl FakeRunner {
        fn returning(outputs: impl IntoIterator<Item = CommandOutput>) -> Self {
            Self {
                calls: Vec::new(),
                results: outputs.into_iter().map(Ok).collect(),
            }
        }

        fn output(stdout: &str) -> CommandOutput {
            CommandOutput {
                success: true,
                status: Some(0),
                stdout: stdout.to_owned(),
                stderr: String::new(),
            }
        }
    }

    impl CommandRunner for FakeRunner {
        fn run(&mut self, program: &str, args: &[&str]) -> io::Result<CommandOutput> {
            self.calls.push((
                program.to_owned(),
                args.iter().map(|arg| (*arg).to_owned()).collect(),
            ));
            self.results
                .pop_front()
                .expect("unexpected command invocation")
        }
    }

    #[test]
    fn status_returns_real_command_output() {
        let runner =
            FakeRunner::returning([FakeRunner::output("Session: RUNNING\nContainer: RUNNING\n")]);
        let mut controller = WaydroidController::with_runner(runner);

        assert_eq!(
            controller.status().unwrap(),
            "Session: RUNNING\nContainer: RUNNING\n"
        );
        assert_eq!(
            controller.runner.calls,
            [("waydroid".to_owned(), vec!["status".to_owned()])]
        );
    }

    #[test]
    fn start_and_stop_run_commands_in_order() {
        let runner = FakeRunner::returning([
            FakeRunner::output(""),
            FakeRunner::output(""),
            FakeRunner::output(""),
            FakeRunner::output(""),
        ]);
        let mut controller = WaydroidController::with_runner(runner);

        controller.start_session().unwrap();
        controller.stop_session().unwrap();

        assert_eq!(
            controller.runner.calls,
            [
                (
                    "systemctl".to_owned(),
                    vec!["start".to_owned(), "waydroid-container.service".to_owned()]
                ),
                (
                    "waydroid".to_owned(),
                    vec!["session".to_owned(), "start".to_owned()]
                ),
                (
                    "waydroid".to_owned(),
                    vec!["session".to_owned(), "stop".to_owned()]
                ),
                (
                    "systemctl".to_owned(),
                    vec!["stop".to_owned(), "waydroid-container.service".to_owned()]
                ),
            ]
        );
    }

    #[test]
    fn launch_validates_package_before_running_command() {
        let runner = FakeRunner::returning([FakeRunner::output("")]);
        let mut controller = WaydroidController::with_runner(runner);

        controller.launch_app("org.fdroid.fdroid").unwrap();
        assert_eq!(
            controller.runner.calls,
            [(
                "waydroid".to_owned(),
                vec![
                    "app".to_owned(),
                    "launch".to_owned(),
                    "org.fdroid.fdroid".to_owned()
                ]
            )]
        );

        assert!(matches!(
            controller.launch_app("org.example;touch /bad"),
            Err(WaydroidError::InvalidPackageName(_))
        ));
        assert_eq!(controller.runner.calls.len(), 1);
    }

    #[test]
    fn command_failure_includes_status_and_stderr_and_stops_sequence() {
        let failed = CommandOutput {
            success: false,
            status: Some(5),
            stdout: String::new(),
            stderr: "permission denied\n".to_owned(),
        };
        let runner = FakeRunner::returning([failed]);
        let mut controller = WaydroidController::with_runner(runner);

        let error = controller.start_session().unwrap_err();
        assert_eq!(
            error.to_string(),
            "systemctl start waydroid-container.service failed with status Some(5): permission denied"
        );
        assert_eq!(controller.runner.calls.len(), 1);
    }

    #[test]
    fn container_stop_failure_reports_session_already_stopped() {
        let failed = CommandOutput {
            success: false,
            status: Some(1),
            stdout: String::new(),
            stderr: "Access denied\n".to_owned(),
        };
        let runner = FakeRunner::returning([FakeRunner::output(""), failed]);
        let mut controller = WaydroidController::with_runner(runner);

        let error = controller.stop_session().unwrap_err();
        assert_eq!(
            error.to_string(),
            "Partial Waydroid stop: the session stopped, but stopping the container service failed: systemctl stop waydroid-container.service failed with status Some(1): Access denied"
        );
        assert!(matches!(
            error,
            WaydroidError::PartialStop {
                source: ref cause
            } if matches!(**cause, WaydroidError::CommandFailed { .. })
        ));
        assert_eq!(
            controller.runner.calls,
            [
                (
                    "waydroid".to_owned(),
                    vec!["session".to_owned(), "stop".to_owned()]
                ),
                (
                    "systemctl".to_owned(),
                    vec!["stop".to_owned(), "waydroid-container.service".to_owned()]
                )
            ]
        );
    }

    #[test]
    fn command_spawn_errors_are_returned() {
        let runner = FakeRunner {
            calls: Vec::new(),
            results: VecDeque::from([Err(io::Error::new(
                io::ErrorKind::NotFound,
                "command not found",
            ))]),
        };
        let mut controller = WaydroidController::with_runner(runner);

        assert!(matches!(
            controller.status(),
            Err(WaydroidError::CommandIo { .. })
        ));
    }
}
