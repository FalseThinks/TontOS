use std::process::ExitCode;
use tont_appd::WaydroidController;

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("tont-appd: {error}");
            ExitCode::FAILURE
        }
    }
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    let mut args = std::env::args().skip(1);
    let command = args
        .next()
        .ok_or("expected status, start, stop, or launch")?;
    let argument = args.next();
    if args.next().is_some() {
        return Err("unexpected extra argument".into());
    }

    let mut controller = WaydroidController::new();
    match (command.as_str(), argument) {
        ("status", None) => print!("{}", controller.status()?),
        ("start", None) => controller.start_session()?,
        ("stop", None) => controller.stop_session()?,
        ("launch", Some(package)) => controller.launch_app(&package)?,
        ("launch", None) => return Err("launch requires an Android package name".into()),
        _ => return Err("expected status, start, stop, or launch <package>".into()),
    }

    Ok(())
}
