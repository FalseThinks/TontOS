# TontOS

<div align="center">

```text
████████╗ ██████╗ ███╗   ██╗████████╗ ██████╗ ███████╗
╚══██╔══╝██╔═══██╗████╗  ██║╚══██╔══╝██╔═══██╗██╔════╝
   ██║   ██║   ██║██╔██╗ ██║   ██║   ██║   ██║███████╗
   ██║   ██║   ██║██║╚██╗██║   ██║   ██║   ██║╚════██║
   ██║   ╚██████╔╝██║ ╚████║   ██║   ╚██████╔╝███████║
   ╚═╝    ╚═════╝ ╚═╝  ╚═══╝   ╚═╝    ╚═════╝ ╚══════╝
```

**Minimalist AOSP Distribution · Rust System Daemons · Low-Distraction Interface**

[![Main Workflow](https://img.shields.io/github/actions/workflow/status/FalseThinks/TontOS/main.yml?branch=main&style=flat-square&label=CI&logo=githubactions&logoColor=white)](https://github.com/FalseThinks/TontOS/actions/workflows/main.yml)
[![Release](https://img.shields.io/badge/release-v0.0.2--alpha-2563EB.svg?style=flat-square&logo=github)](https://github.com/FalseThinks/TontOS/releases)
[![Rust](https://img.shields.io/badge/rust-1.99.0%20(2024%20Edition)-DEA584.svg?style=flat-square&logo=rust&logoColor=white)](https://www.rust-lang.org/)
[![AOSP](https://img.shields.io/badge/AOSP-17.0.0__r1-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://source.android.com/)
[![Tests](https://img.shields.io/badge/tests-53%20passed-10B981.svg?style=flat-square)](crates/)
[![Safety](https://img.shields.io/badge/unsafe_code-forbid-059669.svg?style=flat-square)](crates/)
[![Docker Builder](https://img.shields.io/badge/docker-debian%20slim%20glibc-2496ED.svg?style=flat-square&logo=docker&logoColor=white)](Dockerfile.aosp)
[![License](https://img.shields.io/badge/license-Apache--2.0%20%7C%20MIT-gray.svg?style=flat-square)](LICENSE)

*Maintained by [FalseThinks](https://github.com/FalseThinks) (`paproken@protonmail.com`)*

</div>

---

## Table of Contents

1. [Overview](#overview)
2. [Core Architecture](#core-architecture)
   - [1. System & Hardware Daemons (Rust)](#1-system--hardware-daemons-rust)
   - [2. Minimalist Interface & Digital Wellbeing](#2-minimalist-interface--digital-wellbeing)
   - [3. AOSP Target & Security Hardening](#3-aosp-target--security-hardening)
3. [Repository Structure](#repository-structure)
4. [Build Requirements](#build-requirements)
5. [Quick Start & Compilation](#quick-start--compilation)
   - [Option A: Standalone Launcher APK](#option-a-standalone-launcher-apk)
   - [Option B: Containerized ROM Build (Recommended)](#option-b-containerized-rom-build-recommended)
   - [Option C: Generic System Image (GSI)](#option-c-generic-system-image-gsi)
   - [Option D: Native Host Build & Cuttlefish VM](#option-d-native-host-build--cuttlefish-vm)
   - [Low-Memory Host Optimization (16 GB RAM)](#low-memory-host-optimization-16-gb-ram)
6. [Automation Reference](#automation-reference)
7. [Localization](#localization)
8. [License](#license)

---

## Overview

**TontOS** is an AOSP-based mobile operating system engineered for deterministic power efficiency, zero telemetry, and minimal cognitive load.

Built directly into the AOSP build tree (`android-17.0.0_r1`), TontOS strips Google Mobile Services (GMS), background telemetry, and stock bloatware at compile time. It replaces high-overhead system services with memory-safe Rust 1.99.0 daemons (`#![forbid(unsafe_code)]`) and provides a strictly monospace, low-power user interface.

---

## Core Architecture

### 1. System & Hardware Daemons (Rust)
System management is offloaded to 14 dedicated Rust crates (2024 Edition) operating under strict memory guarantees:
* **Process & Resource Control (`tont-appd`):** Manages background execution and memory throttling via `cgroups v2` freezer hierarchies.
* **Power & Hardware (`tont-powerd`, `tont-romctl`):** Direct `sysfs` management for thermal states, wakelock governance, and hardware battery charge ceilings (80% threshold).
* **Network & Radio (`tont-syncd`):** Batches background radio wakeups to minimize baseband power draw.
* **Storage (`tont-storage`):** Atomic disk serialization and constant-memory streaming SHA-256 snapshot verification.

### 2. Minimalist Interface & Digital Wellbeing
* **Monospace UI & Display Pipeline:** Strictly typographic launcher (`< 2 MB` footprint) paired with SurfaceFlinger/KMS grayscale daltonizer integration, 6 minimal lockscreen clock layouts, and 4 low-power palettes (AMOLED `#000000`, Amber CRT, Slate, E-Ink).
* **Intentional Friction:** Configurable launch delays (3s–10s) for non-whitelisted applications and scheduled notification batching (12:00 and 18:00 digests).
* **Integrated AudioFX:** Native 5-band equalizer (60Hz–14kHz) with 5 preset profiles and zero external service dependencies.

### 3. AOSP Target & Security Hardening
* **Hermetic Build Target (`tontos_cf_x86_64`):** Dedicated `BoardConfig.mk` and product definitions under `platform/products/` with compile-time GMS removal and pre-granted permissions (`default-permissions-tontos.xml`) for vetted FOSS packages (`org.fossify.*`, `eu.faircode.email`, `com.aurora.store`).
* **Universal Treble GSI:** Multi-architecture system images for `arm64` (a64-ab), `arm` (a32-ab), and `x86_64`.
* **Execution & Network Isolation:** Per-UID background socket control ([`AppNetworkFirewall.java`](launcher_apk/src/org/tontos/launcher/AppNetworkFirewall.java)) and strict argument validation for privileged execution paths ([`SafeCommandExecutor.java`](launcher_apk/src/org/tontos/launcher/SafeCommandExecutor.java)).

---

## Repository Structure

```text
.
├── crates/                    # Rust Workspace (14 System Daemons & Libraries)
│   ├── tont-common/           # Shared traits, error primitives, and logging
│   ├── tont-appd/             # cgroups v2 process supervisor and freezer
│   ├── tont-powerd/           # Thermal governance and wakelock daemon
│   ├── tont-romctl/           # sysfs hardware interface and charge limiting
│   ├── tont-syncd/            # Radio wakeup batching
│   ├── tont-settings/         # Theme tokens and hardware configuration engine
│   ├── tont-storage/          # Atomic persistence and streaming checksums
│   └── ...                    # Compositor, input, backup, and telephony crates
├── launcher_apk/              # System Launcher (Java 17, AOSP system_ext)
├── platform/                  # AOSP BoardConfig, manifests, and product targets
└── scripts/                   # Build, GSI packaging, and VM automation
```

---

## Build Requirements

| Resource | Launcher Only | Full AOSP / GSI Build |
|:---|:---|:---|
| **Host OS** | Linux (x86_64) | Debian 12+, Ubuntu 22.04+, Arch, Fedora |
| **CPU** | 4 Cores | 8–16+ Cores |
| **Memory** | 4 GB RAM | 16 GB RAM + 32 GB Swap (32 GB+ RAM recommended) |
| **Storage** | 500 MB | 150 GB NVMe SSD |
| **Toolchain** | OpenJDK 17, Android SDK 34 / Build-Tools 36.0.0 | Docker 24.0+, Rust 1.99.0+, `adb`, `fastboot`, `pnpm` |

---

## Quick Start & Compilation

### Option A: Standalone Launcher APK
Deploy the TontOS interface on any existing Android device without flashing a full ROM:

```bash
./scripts/build_launcher_apk.sh
adb install -r launcher_apk/tontos-launcher.apk
adb shell cmd package set-home-activity org.tontos.launcher/.MainActivity

# Optional: Enable system-wide grayscale daltonizer support
adb shell pm grant org.tontos.launcher android.permission.WRITE_SECURE_SETTINGS
```

### Option B: Containerized ROM Build (Recommended)
Compiles the full AOSP 17 target inside an isolated Debian Slim container with automatic thread scaling (`-j$(nproc)`) and a 50 GB `ccache` volume:

```bash
./scripts/aosp_preflight.sh
./scripts/sync_aosp.sh
./scripts/docker_build_rom.sh
```
*Override defaults via environment variables: `BUILD_JOBS=12 BUILD_TARGET="bootimage systemimage" ./scripts/docker_build_rom.sh`*

### Option C: Generic System Image (GSI)
Generate Project Treble system images for physical hardware:

```bash
# Target specific architecture (arm64, arm, x86_64) or 'all'
./scripts/build_gsi.sh --arch=arm64
```
Flash via `fastbootd` on dynamic partition devices:
```bash
adb reboot fastboot
fastboot flash system out/gsi/arm64/tontos-gsi-arm64.img
fastboot -w && fastboot reboot
```

### Option D: Native Host Build & Cuttlefish VM
For direct compilation on the host environment:

```bash
cd ~/aosp/tontos
source build/envsetup.sh
lunch tontos_cf_x86_64-trunk_staging-userdebug

export USE_CCACHE=1
export CCACHE_DIR="$HOME/.ccache"
ccache -M 50G

m -j$(nproc) bootimage systemimage vbmetaimage
```
Manage the local Cuttlefish virtual device (WebRTC UI available at `https://localhost:8443`):
```bash
./scripts/run_cuttlefish.sh
./scripts/stop_cuttlefish.sh
```

### Low-Memory Host Optimization (16 GB RAM)
AOSP 17 `soong_build` requires strict Go runtime memory governance on 16 GB RAM hosts to prevent kernel OOM terminations (`exit code 137`):
* **Go GC Constraints:** `GOMEMLIMIT=7GiB`, `GOGC=15`, and `GOMAXPROCS=2` cap AST heap allocation bursts during Blueprint analysis.
* **Swap & OOMD:** Provision at least 32 GB of active swap (`btrfs filesystem mkswapfile` or `fallocate`) and configure `systemd-oomd` (`SwapUsedLimit=98%` or `ManagedOOMPreference=omit` on `docker.service`) before starting full builds. Run `./scripts/aosp_preflight.sh` to validate host readiness.

---

## Automation Reference

| Script | Description | Key Flags / Variables |
|:---|:---|:---|
| [`build_launcher_apk.sh`](scripts/build_launcher_apk.sh) | Compiles and signs `tontos-launcher.apk` (< 2 MB) via `javac`, `d8`, and `aapt2`. | Outputs to `launcher_apk/` |
| [`docker_build_rom.sh`](scripts/docker_build_rom.sh) | Containerized parallel AOSP 17 compilation (`bootimage`, `systemimage`, `vbmetaimage`). | `BUILD_JOBS`, `BUILD_TARGET`, `CCACHE_DIR` |
| [`build_gsi.sh`](scripts/build_gsi.sh) | Multi-arch Treble GSI builder (`arm64`, `arm`, `x86_64`, `all`) with checksum manifests. | `--arch`, `--docker`, `--jobs=N`, `--clean` |
| [`sync_aosp.sh`](scripts/sync_aosp.sh) | Minimal manifest sync targeting `android-17.0.0_r1` via `platform/manifests/`. | — |
| [`aosp_preflight.sh`](scripts/aosp_preflight.sh) | Verifies host CPU cores, RAM, swap allocation, and disk space prior to AOSP sync. | — |
| [`run_cuttlefish.sh`](scripts/run_cuttlefish.sh) | Launches Cuttlefish KVM instance with WebRTC streaming (`https://localhost:8443`). | Port `6520`, UDS `/tmp/cf_avd_1000` |
| [`stop_cuttlefish.sh`](scripts/stop_cuttlefish.sh) | Gracefully stops Cuttlefish VM instances and releases WebRTC/ADB sockets. | — |
| [`make_gsi_rootfs.sh`](scripts/make_gsi_rootfs.sh) | Stages GSI root filesystem, `priv-app` configurations, and FOSS APK permissions. | — |
| [`make_initramfs.sh`](scripts/make_initramfs.sh) | Generates minimal Linux initramfs with early mount points and `cgroups v2` hierarchy. | — |
| [`make_ota_package.sh`](scripts/make_ota_package.sh) | Bundles signed cryptographic OTA update payloads. | — |
| [`debloat_custom_rom.sh`](scripts/debloat_custom_rom.sh) | Post-boot ADB utility to disable third-party telemetry and lock home activity role. | Run via `adb shell` |
| [`generate_ci_report.sh`](scripts/generate_ci_report.sh) | Consolidates SAST, dependency audits, unit tests, and binary size checks. | Emits Markdown summary |

---

## Localization

TontOS provides full system coverage for **Español (`es-ES`)**, **Português (`pt-PT` / `pt-BR`)**, **Deutsch (`de-DE`)**, **Français (`fr-FR`)**, **Italiano (`it-IT`)**, and **English (`en-US`)**. Missing localization keys deterministically fall back to `en-US` via [`I18n.java`](launcher_apk/src/org/tontos/launcher/I18n.java).

### Roadmap
- [x] Primary tier localization across setup wizard, app drawer, settings, and contextual help dialogs.
- [ ] Community translation pipeline (Web/PO/JSON).
- [ ] Expanded language tier: Polish (`pl`), Dutch (`nl`), Ukrainian (`uk`), Japanese (`ja`), Simplified Chinese (`zh`).
- [ ] Bi-directional (RTL) layout support for Arabic and Hebrew.

---

## License

Dual-licensed under **Apache-2.0 OR MIT**. See [LICENSE](LICENSE) for full terms and mandatory attribution requirements.
