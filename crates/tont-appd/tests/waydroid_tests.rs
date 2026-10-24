use std::collections::VecDeque;
use std::io;
use tont_appd::{CommandOutput, CommandRunner, WaydroidController, WaydroidError};

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
