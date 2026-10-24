use std::collections::VecDeque;
use std::io;
use tont_romctl::{
    ArtifactEntry, BuildManifest, CommandOutput, CommandRunner, DeviceId, RomctlError,
    SCHEMA_VERSION, Variant, detect_devices, ensure_source_tree, plan_build, sha256_file,
    verify_manifest,
};

#[derive(Default)]
struct Fake {
    outputs: VecDeque<io::Result<CommandOutput>>,
    calls: Vec<(String, Vec<String>)>,
}

impl Fake {
    fn ok(stdout: &str) -> CommandOutput {
        CommandOutput {
            success: true,
            status: Some(0),
            stdout: stdout.to_owned(),
            stderr: String::new(),
        }
    }
    fn with(o: Vec<CommandOutput>) -> Self {
        Self {
            outputs: o.into_iter().map(Ok).collect(),
            calls: Vec::new(),
        }
    }
}

impl CommandRunner for Fake {
    fn run(&mut self, program: &str, args: &[&str]) -> io::Result<CommandOutput> {
        self.calls.push((
            program.to_owned(),
            args.iter().map(|s| (*s).to_owned()).collect(),
        ));
        self.outputs.pop_front().expect("unexpected call")
    }
}

#[test]
fn device_parse_ok_and_rejects_shell() {
    assert!(DeviceId::parse("acme/coral").is_ok());
    assert!(DeviceId::parse("acme/coral;rm -rf /").is_err());
    assert!(DeviceId::parse("no-slash").is_err());
    assert!(DeviceId::parse("ACME/CORAL").is_err());
}

#[test]
fn detect_parses_adb_devices() {
    let mut f = Fake::with(vec![Fake::ok(
        "List of devices attached\nemulator-5554\tdevice\nABC123\tunauthorized\n\n",
    )]);
    let devs = detect_devices(&mut f).unwrap();
    assert_eq!(devs.len(), 2);
    assert_eq!(devs[0].serial, "emulator-5554");
    assert_eq!(devs[1].state, "unauthorized");
    assert_eq!(f.calls[0].0, "adb");
}

#[test]
fn dry_run_has_no_side_effects_and_plans_modules() {
    let plan = plan_build("acme/coral", "userdebug", true).unwrap();
    assert!(plan.dry_run);
    assert_eq!(plan.modules, ["bootimage", "systemimage", "vbmetaimage"]);
    assert!(ensure_source_tree(true).is_ok());
}

#[test]
fn real_build_fails_closed_without_tree() {
    unsafe { std::env::remove_var("AOSP_TREE") };
    let err = ensure_source_tree(false).unwrap_err();
    assert_eq!(err.code(), "E_NO_SOURCE_TREE");
}

#[test]
fn verify_roundtrip_ok_and_tamper_fails() {
    let dir = std::env::temp_dir().join("tont_romctl_test");
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    let payload = dir.join("boot.img");
    std::fs::write(&payload, b"fake-boot").unwrap();
    let (hash, size) = sha256_file(&payload).unwrap();
    let manifest = BuildManifest {
        schema_version: SCHEMA_VERSION,
        device: "acme/coral".to_owned(),
        source_revision: "rev0".to_owned(),
        variant: Variant::Userdebug,
        artifacts: vec![ArtifactEntry {
            path: "boot.img".to_owned(),
            sha256: hash,
            size_bytes: size,
        }],
    };
    let mp = dir.join("manifest.json");
    std::fs::write(&mp, serde_json::to_string_pretty(&manifest).unwrap()).unwrap();
    assert!(verify_manifest(&mp).is_ok());
    std::fs::write(&payload, b"tampered").unwrap();
    let err = verify_manifest(&mp).unwrap_err();
    assert!(matches!(
        err,
        RomctlError::IntegrityMismatch { .. } | RomctlError::Manifest(_)
    ));
    let _ = std::fs::remove_dir_all(&dir);
}
