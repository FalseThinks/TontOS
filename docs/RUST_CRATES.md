# TontOS Rust Workspace Architecture & Crate Reference

Maintainer: FalseThinks <paproken@protonmail.com>

## Overview

The TontOS workspace (`crates/`) contains 14 specialized Rust crates built with Rust 2021 edition. They divide into two categories:
1. **Host-Side Orchestration & CLI Tooling:** Validates environments, checks physical device connectivity, coordinates AOSP platform builds (`tont-romctl`), and verifies disk image integrity.
2. **Guest-Side Minimal Daemons:** Provides low-power system services, cgroup lifecycle supervisors, hardware key listeners, and network sync coordinators.

```
                                    +--------------------+
                                    |    tont-romctl     | (Host Orchestrator & CLI)
                                    +---------+----------+
                                              |
                          --------------------+--------------------
                          |                                       |
                [ AOSP Source Tree ]                    [ Guest Daemons / Linux ]
                vendor/tontos/                          crates/tont-*
                          |                                       |
               +----------v----------+                 +----------v----------+
               |   TontOSLauncher    |                 |   tont-compositor   |
               | (System Priv-App)   |                 |     tont-init       |
               +---------------------+                 +---------------------+
```

---

## Crate Directory & Responsibility Matrix

| Crate | Target Role | Primary Responsibility | Key Structs / Enums |
|---|---|---|---|
| [`tont-common`](file:///home/paproka/Desktop/TontoOS/crates/tont-common) | Shared Library | Core cross-crate types, power states, sync windows | `PowerState`, `BatteryStatus`, `SyncWindowConfig` |
| [`tont-romctl`](file:///home/paproka/Desktop/TontoOS/crates/tont-romctl) | Host CLI & Tool | AOSP build orchestration, streaming SHA-256, target validation | `BuildPlan`, `ArtifactSpec`, `RomctlError` |
| [`tont-powerd`](file:///home/paproka/Desktop/TontoOS/crates/tont-powerd) | Guest Daemon | Race-free kernel suspend, battery telemetry, charging limits | `PowerController`, `BatteryInfo`, `PowerError` |
| [`tont-appd`](file:///home/paproka/Desktop/TontoOS/crates/tont-appd) | Guest Daemon | Linux cgroups app lifecycles, background freeze/thaw | `AppLifecycleState`, `ProcessSupervisor` |
| [`tont-syncd`](file:///home/paproka/Desktop/TontoOS/crates/tont-syncd) | Guest Daemon | Radio duty-cycling & batched synchronization windows | `SyncWindowConfig`, `SyncScheduler` |
| [`tont-compositor`](file:///home/paproka/Desktop/TontoOS/crates/tont-compositor) | Guest Daemon | Minimal low-refresh framebuffer / Wayland compositor | `DisplayFrame`, `MonochromePalette` |
| [`tont-shell`](file:///home/paproka/Desktop/TontoOS/crates/tont-shell) | Guest / CLI | Interactive terminal shell, application catalog data | `AppEntry`, `ShellSession` |
| [`tont-telephony`](file:///home/paproka/Desktop/TontoOS/crates/tont-telephony) | Guest Daemon | Cellular state tracking, minimal SMS record definitions | `SmsMessage`, `ModemState` |
| [`tont-sensors`](file:///home/paproka/Desktop/TontOS/crates/tont-sensors) | Guest Daemon | IIO sensor polling, rate-limiting, and privacy gating | `SensorReading`, `PrivacyMask` |
| [`tont-input`](file:///home/paproka/Desktop/TontoOS/crates/tont-input) | Guest Daemon | Linux evdev input processing, key remapping, debounce | `InputEvent`, `KeyAction` |
| [`tont-settings`](file:///home/paproka/Desktop/TontoOS/crates/tont-settings) | Shared Library | System settings schema, theme tokens, JSON config store | `TontSettings`, `ThemePreset`, `ThemeTokens` |
| [`tont-settings-app`](file:///home/paproka/Desktop/TontoOS/crates/tont-settings-app) | Guest App | Terminal UI client for configuring `tont-settings` | `SettingsTui`, `MenuAction` |
| [`tont-backup`](file:///home/paproka/Desktop/TontoOS/crates/tont-backup) | Guest / Host | Cryptographic snapshot archives with SHA-256 verification | `BackupPackage`, `BackupManifest`, `BackupPayload` |
| [`tont-init`](file:///home/paproka/Desktop/TontoOS/crates/tont-init) | Guest PID 1 | Early-boot setup, sysfs mount, cgroup hierarchy initialization | `InitConfig`, `ServiceDescriptor` |

---

## Detailed Crate Specifications

### 1. `tont-romctl` (Build Orchestrator & ROM Controller)
- **Location:** `crates/tont-romctl`
- **Purpose:** Host tool executing deterministic AOSP platform builds and verifying generated image archives (`super.img`, `system.img`, `boot.img`).
- **Core Design Principles:**
  - **No Uncontrolled Shell Executions:** Arguments are passed as explicit `&[&str]` arrays to `std::process::Command`, preventing command-injection attacks.
  - **Zero-Allocation SHA-256 Streaming:** The `sha256_file()` function employs a fixed 64 KiB stack buffer (`[0u8; 64 * 1024]`) and streaming `sha2::Sha256` updates. It verifies large images with zero heap allocations on the hash loop and negligible RSS footprint.
  - **Failsafe Verification:** Rejects unverified artifacts with explicit `RomctlError::IntegrityMismatch`.

```rust
// Streaming verification example from tont-romctl
pub fn sha256_file(path: &Path) -> Result<(String, u64), RomctlError> {
    let mut f = File::open(path)?;
    let mut h = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    let mut total_size = 0u64;

    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
        total_size += n as u64;
    }

    Ok((format!("{:x}", h.finalize()), total_size))
}
```

### 2. `tont-common`
- **Location:** `crates/tont-common`
- **Purpose:** Central shared type library with zero external runtime overhead (depends only on `serde`).
- **Key Types:**
  - `PowerState`: `Active`, `SuspendToIdle`, `DeepSleep`.
  - `BatteryStatus`: `Charging`, `Discharging`, `Full`, `NotCharging`, `Unknown`.
  - `SyncWindowConfig`: Configures duty cycles (`interval_secs: 1800`, `duration_secs: 60`).

### 3. `tont-powerd`
- **Location:** `crates/tont-powerd`
- **Purpose:** Deterministic power and suspend management using Linux kernel sysfs interfaces.
- **Race-Free Suspend:**
  1. Reads `/sys/power/wakeup_count`.
  2. Writes the obtained count back to `/sys/power/wakeup_count`. The kernel returns error (`EINVAL` / `EAGAIN`) if any wake event fired concurrently.
  3. Writes `mem` or `freeze` to `/sys/power/state` only upon successful confirmation.

### 4. `tont-settings`
- **Location:** `crates/tont-settings`
- **Purpose:** Serializes and deserializes user preferences and color palettes.
- **Theme Palette Integration:**
  - `ThemePreset::AmoledBlack`: Pure `#000000` background, high contrast `#FFFFFF` foreground.
  - `ThemePreset::AmberCrt`: Phosphor amber tones for night-time retinal comfort (`#FFB000`).
  - `ThemePreset::EinkMonochrome`: High-contrast paper white (`#FFFFFF`) with dense carbon black ink (`#000000`).
  - `ThemePreset::SlateMidnight`: Low-blue charcoal slate (`#1E293B`).

### 5. `tont-backup`
- **Location:** `crates/tont-backup`
- **Purpose:** Creates and verifies self-contained JSON state snapshots.
- **Integrity Validation:** Every archive encodes a `BackupManifest` containing a SHA-256 digest of the raw `BackupPayload`. `verify()` enforces byte-for-byte fidelity before restoring any settings.

---

## Verification & Workspace Testing

Run the full Rust test suite and check code formatting:

```sh
# Run all workspace unit tests
cargo test --workspace

# Check formatting across all crates
cargo fmt --all -- --check

# Check lints with Clippy
cargo clippy --workspace --all-targets -- -D warnings
```
