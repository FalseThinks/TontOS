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
[![Tests](https://img.shields.io/badge/tests-142%20passed-10B981.svg?style=flat-square)](tests/)
[![Coverage](https://img.shields.io/badge/coverage-95.5%25%20JaCoCo-22C55E.svg?style=flat-square)](tests/java/)
[![Clippy](https://img.shields.io/badge/clippy-0%20warnings-brightgreen.svg?style=flat-square)](crates/)
[![Safety](https://img.shields.io/badge/unsafe_code-forbid-059669.svg?style=flat-square)](crates/)
[![Docker Builder](https://img.shields.io/badge/docker-debian%20slim%20glibc-2496ED.svg?style=flat-square&logo=docker&logoColor=white)](Dockerfile)
[![License](https://img.shields.io/badge/license-Apache--2.0%20%7C%20MIT-gray.svg?style=flat-square)](LICENSE)

*Maintained by [FalseThinks](https://github.com/FalseThinks) (`paproken@protonmail.com`)*

</div>

---

## Table of Contents

1. [Overview](#overview)
2. [Hardware & PC Requirements](#hardware--pc-requirements)
3. [System Architecture](#system-architecture)
   - [1. System & Hardware Daemons (Rust)](#1-system--hardware-daemons-rust)
   - [2. Minimalist Interface & Digital Wellbeing](#2-minimalist-interface--digital-wellbeing)
   - [3. AOSP Target & Low-RAM Constraints](#3-aosp-target--low-ram-constraints)
4. [Repository Structure](#repository-structure)
5. [Quick Start & Compilation](#quick-start--compilation)
6. [Flashing & Installation Guide](#flashing--installation-guide)
7. [Core Features](#core-features)
8. [License](#license)

---

## Overview

TontOS is an open-source mobile operating system engineered for digital detox, battery preservation, and minimal cognitive load:
- **Zero Google Bloatware:** Built without proprietary Google Mobile Services (GMS), telemetry, or tracking daemons.
- **100% Monospace Interface:** Pure typographic UI supporting 12 languages without colorful icons, widgets, or gamified animations.
- **Low Memory Footprint:** Configured for low system RAM consumption (target $\le$ 500 MB idle) using AOSP low-RAM optimizations and strict process controls.
- **Digital Detox Tools:** Intentional launch delays (mindful pause), daily screen-time budget, system-wide grayscale, and aggressive background app termination.
- **Ultra Battery Saver:** Comprehensive radio duty-cycling, background Doze enforcement, and location/scan throttling.
- **Privacy & Sandboxing:** Granular per-app network firewall and isolated secondary profile sandboxing for proprietary/Google dependencies.
- **Custom Minimalist System Components:** Monospace drop-down notification shade and customizable lock screen replacing distracting stock AOSP counterparts.
- **Preinstalled FOSS Essentials:** Lightweight open-source utilities (`org.fossify.*`, `eu.faircode.email`, `de.baumann.browser`).

---

## Hardware & PC Requirements

### 1. Target Device (Smartphone / Hardware)
- **Architecture:** ARM64 (`arm64-v8a`) or x86_64 (virtual reference).
- **RAM:** Minimum 1 GB (optimized low-RAM AOSP configuration; idle target $\le$ 500 MB).
- **Partition Scheme:** A/B dynamic partitions with Project Treble compliance (Android 10+ baseline).
- **Bootloader:** Unlocked.

### 2. Host Development PC
| Workflow | CPU | RAM | Free Disk Space | Operating System |
| :--- | :--- | :--- | :--- | :--- |
| **Launcher APK & Rust Testing** | 2+ Cores | 4 GB | 10 GB | Linux / macOS / Windows (Docker) |
| **Cuttlefish VM Emulation** | 4+ Cores (VT-x / AMD-V) | 8 GB | 25 GB | Linux with `/dev/kvm` access |
| **Full AOSP ROM Compilation** | 8+ Cores recommended | 16 GB + 16–32 GB Swap/zRAM | 150 GB (NVMe SSD) | Linux (Arch, Debian, Ubuntu) or Docker |

> [!NOTE]
> When compiling full AOSP on host machines with 16 GB of physical RAM, Soong and Ninja require tiered swap (zRAM + NVMe swapfile) or limiting parallel threads via `--jobs=4` to prevent OOM termination. Alternatively, use `./scripts/clean_install_and_monitor.sh --docker` or `./scripts/helpers/setup_host_swap.sh setup` automatically.

---

## System Architecture

### 1. System & Hardware Daemons (Rust)
System management is handled by 14 dedicated Rust crates (2024 Edition, `forbid(unsafe_code)`):
* **Process & Resource Control (`tont-appd`):** Manages background execution and memory throttling via `cgroups v2`.
* **Power & Thermal (`tont-powerd`, `tont-romctl`):** Direct hardware and thermal state management.
* **Backup & Verification (`tont-backup`):** Cryptographic snapshot generation with SHA-256 integrity verification.
* **Network & Radio (`tont-syncd`):** Batches background radio wakeups to minimize baseband power draw.

### 2. Minimalist Interface & Digital Wellbeing
* **Monospace UI & Display Pipeline:** Typographic launcher (< 2 MB footprint) with 4 low-power palettes (AMOLED `#000000`, Amber CRT, Slate, E-Ink) and SurfaceFlinger/KMS grayscale daltonizer integration.
* **Intentional Friction:** Configurable launch delays (3s–10s) for non-whitelisted applications to break compulsive habits.
* **Custom Dropdown Shade & Lock Screen:** Dedicated monospace shade service (`UltraShadeActivity` / `TontOSShadeService`) and customizable lock screen (`LockScreenManager`) with swipe/double-tap gestures, owner info, and quiet notification delivery.
* **Ultra Battery Saver & AudioFX:** Native 5-band equalizer (60Hz–14kHz), aggressive Doze enforcement, and radio duty-cycling.

### 3. AOSP Target & Low-RAM Constraints
* **Low-RAM Profile (`ro.config.low_ram=true`):** Android Go framework profile capping dalvik heap growth to 128 MB and trimming background processes to $\le$ 4 cached apps (`ro.sys.fw.bg_apps_limit=4`).
* **Background Task Termination:** Immediate termination of exited apps upon returning to the home screen, freeing RAM immediately unless whitelisted.
* **Privacy Guard & Play Integrity Sandbox:** Granular per-app network firewall (`AppNetworkFirewall`) and isolated profile sandboxing (`SandboxManager`) for MicroG / Google-dependent banking & transport applications.
* **Sanitized Execution:** Privileged system commands execute through [`SafeCommandExecutor.java`](launcher_apk/src/org/tontos/launcher/SafeCommandExecutor.java) with strict package regex validation.

---

## Repository Structure

```text
.
├── apps/                      # Prebuilt audited FOSS applications
├── bin/                       # Automation scripts & entrypoints (symlinked as scripts/)
│   ├── clean_install_and_monitor.sh # Master pipeline: swap -> test -> build -> boot -> monitor
│   ├── docker_build_rom.sh    # Hermetic containerized ROM builder
│   ├── run_all_tests.sh       # Unified test gate (Rust, Java JaCoCo, APK)
│   ├── build_launcher_apk.sh  # Standalone launcher compiler & signer
│   └── helpers/               # Specialized automation (host swap, GSI, sandbox, Cuttlefish)
├── crates/                    # Rust Workspace (14 System Daemons & Libraries)
│   ├── tont-appd/             # Process supervision and cgroups v2 controls
│   ├── tont-backup/           # Cryptographic backup and snapshot verification
│   ├── tont-common/           # Shared traits and error types
│   ├── tont-compositor/       # KMS / DRM display pipeline helpers
│   ├── tont-init/             # Early boot init routines
│   ├── tont-input/            # Gesture and key event handling
│   ├── tont-powerd/           # Thermal and power management
│   ├── tont-romctl/           # Hardware and system control utilities
│   ├── tont-sensors/          # Sensor privacy controls
│   ├── tont-settings/         # Theme tokens and configuration engine
│   ├── tont-settings-app/     # Settings daemon backend
│   ├── tont-shell/            # Monospace shell interface
│   ├── tont-syncd/            # Sync batching daemon
│   └── tont-telephony/        # Cellular and radio state manager
├── docs/                      # Architecture specifications & component references
├── init/                      # Systemd service unit files and early init configs
├── launcher_apk/              # System Launcher (Java 17, AOSP system_ext priv-app)
├── patches/                   # AOSP platform and framework diffs
├── platform/                  # AOSP BoardConfig, manifests, and product targets
├── tests/                     # Test suites (Rust workspace, Java modular, UI)
└── tools/                     # Build & verification tooling (JaCoCo agent/CLI)
```

---

## Quick Start & Compilation

### 1. Zero-Setup Containerized Build (Docker - Recommended)
Hermetic build environment: all dependencies (Rust, Java 17, AOSP, Repo, CCache) run in an isolated Debian container with caches (`.ccache/`) and artifacts (`out/`) preserved inside the repository:
```bash
# Build AOSP ROM inside Docker:
./scripts/docker_build_rom.sh --build

# Run test suite inside Docker:
./scripts/docker_build_rom.sh --test

# Build launcher APK inside Docker:
./scripts/docker_build_rom.sh --apk

# Interactive container shell:
./scripts/docker_build_rom.sh --shell

# Clean internal caches and output:
./scripts/docker_build_rom.sh --clean
```

### 2. Unified Clean Install & Monitor (Orchestrator)
Orchestrates memory preflight, workspace testing, optional AOSP compile (host or `--docker`), Cuttlefish boot, and live RAM monitoring:
```bash
# Boot existing build with live performance monitoring:
./scripts/clean_install_and_monitor.sh

# Complete cycle in Docker (zero host toolchain or swap changes):
./scripts/clean_install_and_monitor.sh --docker

# Local host build (incremental):
./scripts/clean_install_and_monitor.sh --build
```

### 3. Automated Host Memory & Swap Setup (Linux - Optional for Host Builds)
Prepares host memory before heavy host-native AOSP builds (auto-detects Btrfs vs Ext4, configures tiered zRAM + 32GB NVMe swapfile, and applies kernel sysctl):
```bash
# Check memory & swap status:
./scripts/helpers/setup_host_swap.sh status

# Configure and activate:
sudo ./scripts/helpers/setup_host_swap.sh setup

# Clean/reclaim disk space (swapoff & delete /swapfile_aosp):
sudo ./scripts/helpers/setup_host_swap.sh clean
```

### 4. Build Launcher APK (Fast Iteration)
```bash
./scripts/build_launcher_apk.sh
adb install -r launcher_apk/tontos-launcher.apk
adb shell cmd package set-home-activity org.tontos.launcher/.MainActivity
```

### 5. Run System Test Suite (Unified Gate)
Executes Rust formatting, Clippy, 97 Rust crate tests, 45 Java modular tests with JaCoCo bytecode coverage, and signs the launcher APK:
```bash
./scripts/run_all_tests.sh
```

### 6. Hermetic Containerized ROM Build
```bash
env BUILD_TARGET="systemimage" ./scripts/docker_build_rom.sh
```

---

## Flashing & Installation Guide

### Method 1: Physical Device Flashing (Fastboot / Project Treble)
Deploy the compiled TontOS system image to any Treble-compliant Android smartphone:

1. **Reboot to Dynamic Fastboot Mode (`fastbootd`):**
   ```bash
   adb reboot fastboot
   ```
2. **Flash Dynamic Partitions:**
   ```bash
   # Flash boot and system partitions:
   fastboot flash boot out/target/product/vsoc_x86_64_only/boot.img
   fastboot flash system out/target/product/vsoc_x86_64_only/system.img
   ```
3. **Format User Data (Factory Wipe) & Reboot:**
   ```bash
   fastboot -w
   fastboot reboot
   ```

### Method 2: Cuttlefish Virtual Platform (Automated VM Testing)
Boot a clean factory-state Cuttlefish instance on Linux with hardware KVM acceleration:
```bash
# Automated launch with real-time memory and log monitoring:
./scripts/clean_install_and_monitor.sh

# Or provision and flash clean standard Android AVD emulator:
./scripts/helpers/flash_virtual_device.sh --wipe-data --cold-boot
```

### Method 3: Standalone Launcher Deployment (Any Stock Android 10+)
Install the TontOS digital detox interface onto your daily personal smartphone without flashing partitions:
```bash
# Build and sign the APK:
./scripts/build_launcher_apk.sh

# Sideload and assign default home role:
adb install -r launcher_apk/tontos-launcher.apk
adb shell cmd package set-home-activity org.tontos.launcher/.MainActivity
```

---

## Core Features

1. **Four Low-Power Themes:** AMOLED Black (`#000000`), Amber CRT (`#FFB000`), Slate Midnight (`#1E293B`), and E-Ink Paper (`#FFFFFF`).
2. **100% Monospace Typographic UI:** Pure monospace layout with zero raster bloat, supporting 12 international languages.
3. **System-Wide Grayscale & Daltonizer:** Hardware/KMS monochromacy via `Settings.Secure` to eliminate visual dopamine hooks.
4. **Mindful Launch Pause:** Configurable countdown timer (3s–10s) and breathing indicator before launching non-essential applications.
5. **Ultra Battery Saver Mode:** Comprehensive radio duty-cycling, background Doze enforcement, and location/scan throttling.
6. **Custom Minimalist Lock Screen & Dropdown Shade:** Complete replacement of stock AOSP notifications and lock screen with distraction-free monospace components.
7. **Per-App Network Firewall:** Granular AppOps and iptables network permission gating per package.
8. **MicroG & Sandboxed Google Services:** Unprivileged isolated profile support with Play Integrity compatibility shims.
9. **Integrated AudioFX:** Native 5-band equalizer (60Hz–14kHz) with sound profiles.
10. **Cryptographic Backup:** Monospace snapshot manager with SHA-256 verification and custom backup paths.

---

## License

Dual-licensed under **Apache License 2.0** and **MIT License**. See [LICENSE](LICENSE) for details.
