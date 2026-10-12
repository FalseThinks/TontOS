//! tontosctl entry point: detect/inspect/build/verify, dry-run fail-closed.
use std::path::PathBuf;
use std::process::ExitCode;
use tont_romctl::{
    RomctlError, SystemRunner, detect_devices, ensure_source_tree, inspect_device, plan_build,
    verify_manifest,
};

fn usage() -> &'static str {
    "usage:\n  tontosctl device detect [--format json]\n  tontosctl device inspect --serial <serial> [--format json]\n  tontosctl build --device <vendor/codename> --variant <user|userdebug|eng> [--dry-run]\n  tontosctl artifacts verify --manifest <path>"
}

fn emit_error(err: RomctlError) -> ExitCode {
    let body = serde_json::json!({
        "schema_version": 1,
        "ok": false,
        "code": err.code(),
        "message": err.to_string(),
    });
    eprintln!("{body}");
    eprintln!("tontosctl: {err} [{}]", err.code());
    ExitCode::FAILURE
}

fn run() -> Result<(), RomctlError> {
    let mut args = std::env::args().skip(1);
    let group = args.next().ok_or_else(|| {
        RomctlError::Manifest("missing command group: device|build|artifacts".to_owned())
    })?;
    let cmd = args.next().ok_or_else(|| {
        RomctlError::Manifest("missing command: detect|inspect|verify".to_owned())
    })?;
    let rest: Vec<String> = args.collect();
    let mut runner = SystemRunner;

    match (group.as_str(), cmd.as_str()) {
        ("device", "detect") => {
            let json = rest.iter().any(|a| a == "--format=json" || a == "json");
            let _ = json;
            let devices = detect_devices(&mut runner)?;
            println!(
                "{}",
                serde_json::json!({
                    "schema_version": 1,
                    "ok": true,
                    "devices": devices,
                })
            );
            Ok(())
        }
        ("device", "inspect") => {
            let serial = rest
                .windows(2)
                .find(|w| w[0] == "--serial")
                .map(|w| w[1].clone())
                .ok_or_else(|| {
                    RomctlError::Manifest("inspect requires --serial <serial>".to_owned())
                })?;
            let props = inspect_device(&mut runner, &serial)?;
            println!(
                "{}",
                serde_json::json!({
                    "schema_version": 1,
                    "ok": true,
                    "serial": serial,
                    "properties": props,
                })
            );
            Ok(())
        }
        ("build", _) => {
            // Forma contrato: `tontosctl build --device ... --variant ... [--dry-run]`.
            // Aquí cmd ya es primer flag (--device...), rest el resto.
            let mut all = vec![cmd.clone()];
            all.extend(rest);
            let device = all
                .windows(2)
                .find(|w| w[0] == "--device")
                .map(|w| w[1].clone())
                .ok_or_else(|| {
                    RomctlError::Manifest("build requires --device <vendor/codename>".to_owned())
                })?;
            let variant = all
                .windows(2)
                .find(|w| w[0] == "--variant")
                .map(|w| w[1].clone())
                .ok_or_else(|| {
                    RomctlError::Manifest(
                        "build requires --variant <user|userdebug|eng>".to_owned(),
                    )
                })?;
            let dry_run = all.iter().any(|a| a == "--dry-run");
            let plan = plan_build(&device, &variant, dry_run)?;
            // Fail-closed: sin árbol no hay build real.
            let tree = ensure_source_tree(dry_run)?;
            if dry_run {
                println!("{}", serde_json::to_string_pretty(&plan)?);
                return Ok(());
            }
            // Phase 0/1: Valid plan and source tree present; execution delegated to platform build system.
            println!(
                "{}",
                serde_json::json!({
                    "schema_version": 1,
                    "ok": false,
                    "code": "E_NOT_IMPLEMENTED",
                    "message": format!("plan valid for {} ({}); execution delegated to AOSP tree={tree}", plan.device, plan.lunch_target),
                    "plan": plan,
                })
            );
            Err(RomctlError::Manifest(
                "direct invocation requires running Soong/Ninja in the target AOSP tree".to_owned(),
            ))
        }
        ("artifacts", "verify") => {
            let manifest = rest
                .windows(2)
                .find(|w| w[0] == "--manifest")
                .map(|w| w[1].clone())
                .ok_or_else(|| {
                    RomctlError::Manifest("verify requires --manifest <path>".to_owned())
                })?;
            let arts = verify_manifest(&PathBuf::from(&manifest))?;
            println!(
                "{}",
                serde_json::json!({
                    "schema_version": 1,
                    "ok": true,
                    "manifest": manifest,
                    "artifacts": arts,
                })
            );
            Ok(())
        }
        _ => Err(RomctlError::Manifest(usage().to_owned())),
    }
}

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => emit_error(e),
    }
}
