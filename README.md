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
[![Tests](https://img.shields.io/badge/tests-54%20passed-10B981.svg?style=flat-square)](crates/)
[![Safety](https://img.shields.io/badge/unsafe_code-forbid-059669.svg?style=flat-square)](crates/)
[![Docker Builder](https://img.shields.io/badge/docker-debian%20slim%20glibc-2496ED.svg?style=flat-square&logo=docker&logoColor=white)](Dockerfile.aosp)
[![License](https://img.shields.io/badge/license-Apache--2.0%20%7C%20MIT-gray.svg?style=flat-square)](LICENSE)

*Maintained by [FalseThinks](https://github.com/FalseThinks) (`paproken@protonmail.com`)*

</div>

---

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
   - [1. System & Hardware Daemons (Rust)](#1-system--hardware-daemons-rust)
   - [2. Minimalist Interface & Digital Wellbeing](#2-minimalist-interface--digital-wellbeing)
   - [3. AOSP Target & Low-RAM Constraints](#3-aosp-target--low-ram-constraints)
3. [Repository Structure](#repository-structure)
4. [Quick Start & Compilation](#quick-start--compilation)
5. [Core Features](#core-features)
6. [License](#license)

---

## Overview

TontOS is an open-source mobile operating system engineered for digital detox, battery preservation, and minimal cognitive load:
- **Zero Google Bloatware:** Built without Google Mobile Services (GMS), telemetry, or tracking daemons.
- **100% Monospace Interface:** Pure typographic UI without colorful icons, widgets, or gamified animations.
- **Low Memory Footprint:** Configured for low system RAM consumption (target $\le$ 500 MB idle) using AOSP low-RAM optimizations and strict process controls.
- **Digital Detox Tools:** Intentional launch delays (mindful pause), daily screen-time budget, system-wide grayscale, and aggressive background app termination.
- **Preinstalled FOSS Essentials:** Lightweight open-source utilities (`org.fossify.*`, `eu.faircode.email`, `de.baumann.browser`).

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
* **Integrated AudioFX:** Native 5-band equalizer (60Hz–14kHz) with sound profiles.

### 3. AOSP Target & Low-RAM Constraints
* **Low-RAM Profile (`ro.config.low_ram=true`):** Android Go framework profile capping dalvik heap growth to 128 MB and trimming background processes to $\le$ 4 cached apps (`ro.sys.fw.bg_apps_limit=4`).
* **Background Task Termination:** Immediate termination of exited apps upon returning to the home screen, freeing RAM immediately unless whitelisted.
* **Sanitized Execution:** Privileged system commands execute through [`SafeCommandExecutor.java`](launcher_apk/src/org/tontos/launcher/SafeCommandExecutor.java) with strict package regex validation.

---

## Repository Structure

```text
.
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
├── launcher_apk/              # System Launcher (Java 17, AOSP system_ext)
├── platform/                  # AOSP BoardConfig, manifests, and product targets
├── scripts/                   # Build, test, and VM automation scripts
└── tests/                     # Test suites (Rust, Java, UI)
```

---

## Quick Start & Compilation

### 1. Build Launcher APK
```bash
./scripts/build_launcher_apk.sh
adb install -r launcher_apk/tontos-launcher.apk
adb shell cmd package set-home-activity org.tontos.launcher/.MainActivity
```

### 2. Run All Tests
```bash
./scripts/run_all_tests.sh
```

### 3. Containerized ROM Build
```bash
env BUILD_TARGET="systemimage" ./scripts/docker_build_rom.sh
```

### 4. Run in Cuttlefish Emulator
```bash
./scripts/run_cuttlefish.sh
./scripts/stop_cuttlefish.sh
```

---

## Core Features

1. **Four Low-Power Themes:** AMOLED Black (`#000000`), Amber CRT, Slate Midnight, and E-Ink Paper.
2. **Minimalist App Drawer:** Fast alphabetical app search and privacy inspection on long-press.
3. **Background Process Management:** Apps without background execution permission are terminated immediately upon returning home.
4. **Mindful Launch Pause:** Configurable countdown timer (3s–10s) before opening non-essential apps.
5. **Integrated AudioFX:** Built-in 5-band equalizer (60Hz–14kHz) with sound profiles.
6. **Cryptographic Backup:** Monospace snapshot manager with SHA-256 verification and custom backup paths.

---

## License

Dual-licensed under **Apache License 2.0** and **MIT License**. See [LICENSE](LICENSE) for details.
