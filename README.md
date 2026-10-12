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

**Intentional Mobile Computing · Bloat-Free AOSP · Low-Dopamine Monospace Architecture**

[![CI Pipeline](https://img.shields.io/github/actions/workflow/status/FalseThinks/TontOS/main.yml?branch=main&style=flat-square&label=CI%20Pipeline&logo=githubactions&logoColor=white)](https://github.com/FalseThinks/TontOS/actions/workflows/main.yml)
[![Release](https://img.shields.io/badge/release-v0.0.1--alpha-2563EB.svg?style=flat-square&logo=github)](https://github.com/FalseThinks/TontOS/releases)
[![Rust](https://img.shields.io/badge/rust-1.99.0%20(2024%20Edition)-DEA584.svg?style=flat-square&logo=rust&logoColor=white)](https://www.rust-lang.org/)
[![Android](https://img.shields.io/badge/AOSP-16%20(BP4A)%20|%20API%2034-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://source.android.com/)
[![Tests](https://img.shields.io/badge/tests-53%20passed%20(100%25)-10B981.svg?style=flat-square)](crates/)
[![Safety](https://img.shields.io/badge/unsafe_code-0%20blocks%20(100%25%20safe)-059669.svg?style=flat-square)](crates/)
[![Docker Builder](https://img.shields.io/badge/docker-debian%20slim%20glibc-2496ED.svg?style=flat-square&logo=docker&logoColor=white)](Dockerfile.aosp)
[![License](https://img.shields.io/badge/license-Apache--2.0%20%7C%20MIT-gray.svg?style=flat-square)](LICENSE)

*Created and maintained by [FalseThinks](https://github.com/FalseThinks) (`paproken@protonmail.com`)*

</div>

---

## Overview

**TontOS** is an open-source, minimalist mobile operating system engineered from the ground up for digital autonomy, deep focus, and battery preservation. Unlike superficial launcher skins or post-install debloater scripts, TontOS integrates directly into the **Android Open Source Project (AOSP)** build system, producing a pure, unencumbered ROM target devoid of background analytics, vendor services, duplicate applications, and dopamine-inducing visual triggers.

---

## Core Architectural Pillars

### 1. 🧘 Digital Detox by Design
- **100% Monospace Visual Uniformity:** Eliminates variable-width typographic cues and marketing typography across all system dialogs, settings, and drawer views.
- **Ultra-Low Stimulus Color Presets:** High-contrast AMOLED Black (`#000000`), Amber CRT phosphor, Slate Midnight, and inverted E-Ink Paper.
- **KMS / SurfaceFlinger Grayscale:** Direct system daltonizer integration removing color dopamine at the compositor layer.
- **Mindful Launch Friction:** Configurable 3s to 10s pause before opening distracting non-essential apps.
- **Notification Batching & Digests:** Eliminates instant ping interruptions by bundling non-starred alerts into scheduled delivery windows (12:00 and 18:00).

### 2. 🛡️ Native Vanilla AOSP Product
- **Hermetic Custom Target (`tontos_cf_x86_64`):** Defined cleanly under `vendor/tontos/` with its own `AndroidProducts.mk` and `BoardConfig.mk`.
- **Zero-Bloat Build:** Excludes default Google Mobile Services (GMS), Telemetry, search bars, and redundant media engines at compile-time.
- **FOSS Core Ecosystem:** Pre-integrates vetted open-source utilities (`org.fossify.*`, `eu.faircode.email`, `com.aurora.store`).

### 3. 🦀 Hardened Rust System Suite (14 Crates)
- **Rust 1.99.0 & 2024 Edition:** Built using modern let-chain idioms, zero-allocation iterators, and strict safety contracts.
- **`forbid(unsafe_code)`:** 100% verified safe Rust across all daemons and library crates.
- **Streaming Cryptography:** Artifact verification and snapshot generation use streaming SHA-256 with constant memory footprint.
- **Atomic Cgroups Management:** Background task throttling and application freezing via cgroups v2 (`tont-appd`).

### 4. 🔒 System Hardening & Security
- **Command Sanitization:** All privileged system operations (`cmd statusbar`, `cmd appops`, `iptables`) execute strictly via [`SafeCommandExecutor`](launcher_apk/src/org/tontos/launcher/SafeCommandExecutor.java) with regex validation (`^[a-zA-Z0-9._-]+$`), eliminating shell injection risks.
- **Per-App Network Firewall:** Toggle background network access and internet socket permissions per UID.
- **Battery Health Throttling:** 80% charge threshold limiting via sysfs nodes to extend battery lifespan.

---

## Repository Architecture

```text
.
├── crates/                    # 14 Rust Workspace System Crates
│   ├── tont-common/           # Core traits, error types, and logging primitives
│   ├── tont-romctl/           # Hardware sysfs control, battery 80% limits, profiles
│   ├── tont-powerd/           # Thermal states, screen timeouts, wakelock daemon
│   ├── tont-appd/             # Process supervisor & cgroups v2 atomic freezer
│   ├── tont-syncd/            # Radio synchronization batching
│   ├── tont-settings/         # Low-power theme tokens and hardware settings engine
│   ├── tont-storage/          # Atomic disk serialization & cryptographic snapshots
│   └── ...                    # Input, compositor, telephony, backup daemons
├── launcher_apk/              # First-Party System Launcher (Java 17, AOSP System Ext)
│   ├── src/org/tontos/launcher/
│   │   ├── MainActivity.java        # Zero-allocation monospace home & drawer
│   │   ├── SettingsActivity.java    # Hardware & OS preferences
│   │   ├── SettingsRowFactory.java  # Modular decoupled UI builder
│   │   ├── SafeCommandExecutor.java # Sanitized process executor
│   │   ├── GrayscaleManager.java    # Daltonizer display monochrome controller
│   │   └── AppNetworkFirewall.java  # Per-app UID firewall interface
│   └── AndroidManifest.xml
├── platform/                  # AOSP Vendor Tree & Manifest Definitions
│   ├── vendor/tontos/         # Product definitions (tontos_cf_x86_64.mk)
│   └── manifests/             # Pinned AOSP BP4A revision manifests
├── scripts/                   # Tooling, Container & Preflight Scripts
│   ├── docker_build_rom.sh    # Isolated AOSP ROM compilation in Docker
│   ├── build_launcher_apk.sh  # Standalone launcher APK compilation & signing
│   └── generate_ci_report.sh  # Executive vulnerability and coverage reporter
├── Dockerfile.aosp            # Ultra-lightweight Debian Slim glibc builder (28MB)
└── docs/                      # Architectural Decision Records (ADRs) & Specs
```

---

## Verification & Build Instructions

### 1. Rust Workspace (Crates & Daemons)

Requires stable Rust 1.99.0+ (`rust-toolchain.toml` automatically configures this):

```bash
# Check formatting
cargo fmt --all -- --check

# Run Clippy with zero-warning threshold
cargo clippy --workspace --all-targets -- -D warnings

# Execute test suite (53/53 tests)
cargo test --workspace

# Verify documentation
RUSTDOCFLAGS="-D warnings" cargo doc --workspace --no-deps
```

### 2. Standalone Android Launcher APK

Requires OpenJDK 17 and Android SDK Platform 34 / Build-Tools 36.0.0:

```bash
./scripts/build_launcher_apk.sh
```
*The compiled, optimized, and signed APK will be output to `build/tontos-launcher.apk` (< 2 MB budget).*

### 3. Complete AOSP ROM Compilation (Docker)

Use the hermetic Debian Slim container to build the full flashable system image:

```bash
# Automated compilation helper mounting your AOSP source tree
./scripts/docker_build_rom.sh
```

Or run interactively inside the container:
```bash
docker run --rm -it \
    -v "$HOME/aosp/tontos:/home/tontos/aosp" \
    -v "$HOME/.ccache:/home/tontos/.ccache" \
    tontos-builder:latest \
    bash -c "source build/envsetup.sh && lunch tontos_cf_x86_64-bp4a-userdebug && SOONG_JOBS=1 m -j1 droid"
```

---

## Continuous Integration & Quality Gates

Every push and Pull Request is rigorously audited through our multi-stage GitHub Actions pipeline:

| Job | Responsibility | Verification Tool | Gate Threshold |
|:---|:---|:---|:---:|
| **PR Branch Guard** | Branch policy enforcement | Shell / GitHub Context | Blocks non-`development` PRs |
| **Rust Quality** | Linting, formatting & tests | `cargo fmt`, `clippy`, `cargo test` | 0 warnings, 100% pass |
| **Rust Security** | Dependency vulnerabilities | `cargo-audit`, `cargo-deny` | 0 advisories, verified licenses |
| **Android Build** | System app verification | `javac 17`, `apksigner verify` | Strict size < 2 MB, valid signature |
| **Secret Scan** | Credential leak prevention | `gitleaks v8` (`.gitleaks.toml`) | 0 leaked tokens or keys |
| **Vulnerability Scan**| Filesystem & OS packages | `aquasecurity/trivy` | 0 Critical / High CVEs |
| **Static Analysis** | SAST code flaw detection | `semgrep scan` | 0 tainted injection flaws |
| **UI Visual DOM** | Monospace DOM verification | Playwright Chromium | 100% assertion pass |
| **Docker Bootstrap**| ROM compilation dry-run | Docker Engine & Glibc test | Successful lunch bootstrap |

A consolidated executive report is generated and attached to each run summary via [`scripts/generate_ci_report.sh`](scripts/generate_ci_report.sh).

---

## Open Source, Forking & Contribution Guidelines

TontOS is 100% free and open-source software. Community forks, custom device adaptations, and hardware tree contributions are warmly welcomed!

### Forking Rules & Mandatory Attribution
Per the **Apache License 2.0 (Section 4)** and **MIT License** terms:
- You are free to fork this project, adapt it to physical devices, and redistribute modified builds.
- **Mandatory Attribution:** Any fork, derivative ROM, or redistributed binary **must retain the original copyright notice** and attribute the author:
  > **"Based on TontOS by FalseThinks <paproken@protonmail.com>"**
  *(See [`NOTICE`](NOTICE) for legal specifics).*

### Contribution Workflow
1. Fork the repository and create your feature branch from **`development`** (`git checkout -b feat/my-feature upstream/development`).
2. Adhere to our zero-unsafe Rust policy and monospace UI guidelines.
3. Ensure all tests and linters pass locally.
4. Submit your Pull Request targeting **`development`**.
5. All contributions require review and approval from [@FalseThinks](https://github.com/FalseThinks).

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## License

TontOS is dual-licensed under:
- **Apache License, Version 2.0** ([LICENSE](LICENSE))
- **MIT License** ([LICENSE-MIT](LICENSE-MIT))

See [NOTICE](NOTICE) for copyright and attribution details.  
Copyright © 2026 **FalseThinks** (`paproken@protonmail.com`). All rights reserved.
