//! tont-romctl: Host-side Android build and artifact verification orchestrator.
//! Rust orchestrates platform builds; it does not replace Soong or Ninja. No shell-interpolated execution.

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::io;
use std::path::Path;
use thiserror::Error;

pub const SCHEMA_VERSION: u32 = 1;
pub const EMULATOR_TARGET: &str = "aosp_cf_x86_64_only_phone";

#[derive(Debug, Error)]
pub enum RomctlError {
    #[error("invalid device '{0}': expected vendor/codename")]
    InvalidDevice(String),
    #[error("invalid variant '{0}': expected user|userdebug|eng")]
    InvalidVariant(String),
    #[error("transport '{command}' failed: {source}")]
    Transport {
        command: String,
        #[source]
        source: io::Error,
    },
    #[error("command '{command}' failed (status {status:?}): {stderr}")]
    CommandFailed {
        command: String,
        status: Option<i32>,
        stderr: String,
    },
    #[error("refusing real build without AOSP tree at '{0}' (use --dry-run)")]
    NoSourceTree(String),
    #[error("manifest error: {0}")]
    Manifest(String),
    #[error("integrity mismatch for '{path}': expected {expected}, got {got}")]
    IntegrityMismatch {
        path: String,
        expected: String,
        got: String,
    },
    #[error("I/O: {0}")]
    Io(#[from] io::Error),
    #[error("JSON: {0}")]
    Json(#[from] serde_json::Error),
}

impl RomctlError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::InvalidDevice(_) => "E_INVALID_DEVICE",
            Self::InvalidVariant(_) => "E_INVALID_VARIANT",
            Self::Transport { .. } | Self::CommandFailed { .. } => "E_TRANSPORT",
            Self::NoSourceTree(_) => "E_NO_SOURCE_TREE",
            Self::Manifest(_) => "E_MANIFEST",
            Self::IntegrityMismatch { .. } => "E_INTEGRITY",
            Self::Io(_) => "E_IO",
            Self::Json(_) => "E_JSON",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Variant {
    User,
    Userdebug,
    Eng,
}

impl std::str::FromStr for Variant {
    type Err = RomctlError;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "user" => Ok(Self::User),
            "userdebug" => Ok(Self::Userdebug),
            "eng" => Ok(Self::Eng),
            other => Err(RomctlError::InvalidVariant(other.to_owned())),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceId {
    pub vendor: String,
    pub codename: String,
}

impl DeviceId {
    pub fn parse(s: &str) -> Result<Self, RomctlError> {
        let (vendor, codename) = s
            .split_once('/')
            .ok_or_else(|| RomctlError::InvalidDevice(s.to_owned()))?;
        for part in [vendor, codename] {
            if part.is_empty()
                || !part.chars().all(|c| {
                    c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_' || c == '-' || c == '.'
                })
            {
                return Err(RomctlError::InvalidDevice(s.to_owned()));
            }
        }
        Ok(Self {
            vendor: vendor.to_owned(),
            codename: codename.to_owned(),
        })
    }

    pub fn as_str(&self) -> String {
        format!("{}/{}", self.vendor, self.codename)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DetectedDevice {
    pub serial: String,
    pub state: String,
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
}

#[derive(Debug, Default)]
pub struct SystemRunner;

impl CommandRunner for SystemRunner {
    fn run(&mut self, program: &str, args: &[&str]) -> io::Result<CommandOutput> {
        let out = std::process::Command::new(program).args(args).output()?;
        Ok(CommandOutput {
            success: out.status.success(),
            status: out.status.code(),
            stdout: String::from_utf8_lossy(&out.stdout).into_owned(),
            stderr: String::from_utf8_lossy(&out.stderr).into_owned(),
        })
    }
}

fn exec(
    runner: &mut impl CommandRunner,
    program: &str,
    args: &[&str],
) -> Result<String, RomctlError> {
    let cmd = std::iter::once(program)
        .chain(args.iter().copied())
        .collect::<Vec<_>>()
        .join(" ");
    let out = runner
        .run(program, args)
        .map_err(|source| RomctlError::Transport {
            command: cmd.clone(),
            source,
        })?;
    if !out.success {
        return Err(RomctlError::CommandFailed {
            command: cmd,
            status: out.status,
            stderr: out.stderr.trim_end().to_owned(),
        });
    }
    Ok(out.stdout)
}

/// Lists connected Android devices via `adb devices`.
pub fn detect_devices(runner: &mut impl CommandRunner) -> Result<Vec<DetectedDevice>, RomctlError> {
    let stdout = exec(runner, "adb", &["devices"])?;
    let mut devices = Vec::new();
    for line in stdout.lines().skip(1) {
        let line = line.trim();
        if line.is_empty() || line.starts_with('*') {
            continue;
        }
        let mut cols = line.split_whitespace();
        if let (Some(serial), Some(state)) = (cols.next(), cols.next()) {
            devices.push(DetectedDevice {
                serial: serial.to_owned(),
                state: state.to_owned(),
            });
        }
    }
    Ok(devices)
}

/// Reads device properties via `adb -s <serial> shell getprop` without shell interpolation.
pub fn inspect_device(
    runner: &mut impl CommandRunner,
    serial: &str,
) -> Result<BTreeMap<String, String>, RomctlError> {
    if serial.is_empty() || serial.contains(|c: char| c.is_whitespace()) {
        return Err(RomctlError::Manifest(format!("invalid serial '{serial}'")));
    }
    let stdout = exec(runner, "adb", &["-s", serial, "shell", "getprop"])?;
    let mut props = BTreeMap::new();
    for line in stdout.lines() {
        // Line format: [key]: [value]
        let line = line.trim();
        if let Some((k, v)) = line.split_once("]: [") {
            let k = k.strip_prefix('[').unwrap_or(k).to_owned();
            let v = v.strip_suffix(']').unwrap_or(v).to_owned();
            if !k.is_empty() {
                props.insert(k, v);
            }
        }
    }
    Ok(props)
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ArtifactEntry {
    pub path: String,
    pub sha256: String,
    pub size_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct BuildPlan {
    pub schema_version: u32,
    pub device: String,
    pub variant: Variant,
    pub lunch_target: String,
    pub dry_run: bool,
    pub modules: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct BuildManifest {
    pub schema_version: u32,
    pub device: String,
    pub source_revision: String,
    pub variant: Variant,
    pub artifacts: Vec<ArtifactEntry>,
}

/// Plans target build without side effects in dry-run mode.
/// Non-dry-run builds fail closed without an explicit verified AOSP tree.
pub fn plan_build(device: &str, variant: &str, dry_run: bool) -> Result<BuildPlan, RomctlError> {
    let id = DeviceId::parse(device)?;
    let variant: Variant = variant.parse()?;
    let lunch_target = if id.codename == EMULATOR_TARGET {
        EMULATOR_TARGET.to_owned()
    } else {
        format!(
            "{}_{}-{}",
            id.vendor,
            id.codename,
            match variant {
                Variant::User => "user",
                Variant::Userdebug => "userdebug",
                Variant::Eng => "eng",
            }
        )
    };
    Ok(BuildPlan {
        schema_version: SCHEMA_VERSION,
        device: id.as_str(),
        variant,
        lunch_target,
        dry_run,
        modules: vec![
            "bootimage".to_owned(),
            "systemimage".to_owned(),
            "vbmetaimage".to_owned(),
        ],
    })
}

/// Ensures valid AOSP tree exists if not dry-run. Fails closed when unset.
pub fn ensure_source_tree(dry_run: bool) -> Result<String, RomctlError> {
    if dry_run {
        return Ok(String::new());
    }
    let tree = std::env::var("AOSP_TREE").unwrap_or_default();
    if tree.is_empty() || !Path::new(&tree).join("build/envsetup.sh").exists() {
        return Err(RomctlError::NoSourceTree(if tree.is_empty() {
            "<unset AOSP_TREE>".to_owned()
        } else {
            tree
        }));
    }
    Ok(tree)
}

pub fn sha256_file(path: &Path) -> Result<(String, u64), RomctlError> {
    use sha2::{Digest, Sha256};
    use std::fs::File;
    use std::io::{BufReader, Read};

    let f = File::open(path)?;
    let mut reader = BufReader::with_capacity(16 * 1024 * 1024, f);
    let mut h = Sha256::new();
    let mut buf = vec![0u8; 1024 * 1024];
    let mut total_size = 0u64;

    loop {
        let n = reader.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
        total_size += n as u64;
    }

    Ok((format!("{:x}", h.finalize()), total_size))
}

/// Verifies artifact integrity against cryptographic manifest.
pub fn verify_manifest(manifest_path: &Path) -> Result<Vec<ArtifactEntry>, RomctlError> {
    let content = std::fs::read_to_string(manifest_path).map_err(|e| {
        RomctlError::Manifest(format!("cannot read {}: {e}", manifest_path.display()))
    })?;
    let manifest: BuildManifest = serde_json::from_str(&content)?;
    if manifest.schema_version != SCHEMA_VERSION {
        return Err(RomctlError::Manifest(format!(
            "unsupported schema_version {}",
            manifest.schema_version
        )));
    }
    let base = manifest_path.parent().unwrap_or(Path::new("."));
    for art in &manifest.artifacts {
        let p = base.join(&art.path);
        let (got, size) = sha256_file(&p)
            .map_err(|e| RomctlError::Manifest(format!("cannot hash {}: {e}", p.display())))?;
        if got != art.sha256 {
            return Err(RomctlError::IntegrityMismatch {
                path: art.path.clone(),
                expected: art.sha256.clone(),
                got,
            });
        }
        if size != art.size_bytes {
            return Err(RomctlError::Manifest(format!(
                "size mismatch for {}: manifest {} vs file {size}",
                art.path, art.size_bytes
            )));
        }
    }
    Ok(manifest.artifacts)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;

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
}
