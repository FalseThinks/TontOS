# Implementation Plan: TontOS Prototype

## Overview
TontOS is an experimental Linux host with a separate Android userspace provided by Waydroid. It is not currently an Android ROM, GSI, or bootable phone image.

Current product slices:
1. Release-channel preference (`release`, `beta`, `nightly`) that opens matching release pages; it is not an OTA updater.
2. Pinned apps custom reordering in main view (`[ ^ ]` and `[ v ]` priority sort).
3. Cryptographic backup + restore via `/data/local/tmp/tont-backup` with live preferences snapshot/restore.
4. Visual theme presets (AMOLED Black, Amber CRT, E-Ink Monochrome, Slate Midnight); no measured battery savings are claimed.
5. Consolidated GitHub Actions quality pipeline (`.github/workflows/main.yml`) with Rust, Android, Trivy, Semgrep, UI tests, and conditionally enabled CodeQL.
6. Region & Language pickers, first-boot Install Wizard (`SetupWizardActivity.java`), FairEmail integration.

---

## Architecture Decision Record (ADR)
- **Problem:**
  - Need user-configurable release-page channels without claiming an OTA updater that does not exist.
  - Need manual ordering for pinned apps on minimalist home screen without heavy drag-drop libraries.
  - Need full snapshot + restore of user state without cloud Google Backup.
  - Need 4 visual/detox themes without `recreate()` UI flicker or XML theme bloat.
  - Need robust automated security/quality checks (Trivy, Semgrep, CodeQL, audit) and UI testing for CI.
  - Need OOBE setup wizard on first boot for language, region, theme, and screen budget without bloatware setup services.
- **Existing Architecture / Patterns:**
  - Android Frontend: `MainActivity.java` + `SettingsActivity.java` use programmatic layouts, zero XML bloat, direct `SharedPreferences` persistence.
  - Rust Backend: `crates/tont-backup` (AES-GCM / SHA-256 CLI), `crates/tont-settings` (Rust configuration models).
- **Candidates Considered:**
  - App Reordering: Drag-drop `ItemTouchHelper` vs discrete `[ ^ ]` / `[ v ]` reorder buttons with array swap. Selected: discrete reorder buttons (fast feedback, accessible, minimal code, zero gesture conflict).
  - Theme Engine: Android `setTheme()` with `activity.recreate()` vs `ThemeTokens` POJO with direct view styling. Selected: `ThemeTokens` POJO (no flicker, no restart lag, keeps scroll state).
  - CI Quality & Security Tools: SonarQube (requires paid server/token), Fortify (commercial) vs. GitHub CodeQL + Aquasecurity Trivy (free/open-source FS & vulnerability scanner) + Semgrep (free open-source SAST) + cargo-audit/deny. Selected: CodeQL + Trivy + Semgrep + cargo-deny.
  - UI Testing: Playwright (web / Android bridge) + Maestro mobile flow definitions. Selected: Maestro flow YAMLs + Playwright Android runner harness.
  - Setup Wizard: Separate monolithic wizard APK vs. integrated `SetupWizardActivity.java` gated on `is_setup_completed` preference. Selected: integrated zero-dependency activity launched when `!prefs.getBoolean("setup_completed", false)`.
- **Selected Approach:**
  - Release Channel: `ota_update_channel` preference selects a GitHub Releases page; no OTA payload is produced or installed.
  - App Reordering: Ordered list in `pinned_apps_order` preference, modified via up/down buttons in Settings Section 3.
  - Backup & Restore: Two buttons in Section 5 run `tont-backup create` and `tont-backup restore` with SharedPreferences serialization.
  - Theme Engine: `ThemeTokens` class provides color tokens for 4 presets, applied live to headers, buttons, accents, backgrounds.
  - CI/CD: `.github/workflows/main.yml` consolidates quality and UI jobs; CodeQL/SARIF is gated while the repository is private.
  - Install Wizard: `SetupWizardActivity.java` with 5 steps: Welcome -> Language & Region -> Theme Preset -> Daily Budget -> Done.
  - FairEmail: Add `eu.faircode.email` to default pinned apps and launcher detection.
- **Why It Is Simplest & Effective (Ponytail Ultra):**
  - No external cloud services or proprietary tooling required.
  - Reuses native standard Android APIs (Locale, SharedPreferences, ComponentName).
  - Keeps 100% bloat-free zero-XML layout architecture.
- **Alternatives Rejected:**
  - Proprietary SAST (Fortify) requiring enterprise server licenses.
  - Multi-megabyte Google SetupWizard package.
- **Consequences & Verification:**
  - Immediate responsive UI updates. Theme color changes are not evidence of lower device power consumption.
  - Build launcher APK, install on emulator, run unit tests, verify wizard and settings live.

---

## Task Breakdown

### Phase 14: Core Features (Launcher APK)
- [x] Task 14.1: Implement Update Channel Selector in `SettingsActivity.java` Section 5.
- [x] Task 14.2: Implement App Sorting & Reordering (`[ ^ ]`, `[ v ]`) in `SettingsActivity.java` Section 3 and `MainActivity.java`.
- [x] Task 14.3: Implement Backup Create & Restore in `SettingsActivity.java` Section 5 and test integration with `/data/local/tmp/tont-backup`.
- [x] Task 14.4: Implement `ThemeTokens` and Presets (`AMOLED`, `AMBER`, `E-INK`, `SLATE`) in `SettingsActivity.java` Section 1 and `MainActivity.java`.

### Phase 15: Rust Backend Theme Models
- [x] Task 15.1: Implement `ThemeConfig` and `ThemePreset` models in `crates/tont-settings`.
- [x] Task 15.2: Unit tests in `crates/tont-settings` verifying theme serialization and token invariants.

### Phase 16: CI/CD Quality Pipeline & Repository Initialization
- [x] Task 16.1: Initialize Git repo configuration (`.gitignore`, `rustfmt.toml`, `clippy.toml`, `.editorconfig`, `deny.toml`).
- [x] Task 16.2: Implement GitHub Actions CI workflows (`ci.yml`, `codeql.yml`, `dependabot.yml`) with Trivy, Semgrep, and Rust quality gates.
- [x] Task 16.3: Implement UI automated tests (Maestro flows & Playwright test runner).

### Phase 17: Region/Language Pickers, Install Wizard & FairEmail
- [x] Task 17.1: Add Language & Region pickers in `SettingsActivity.java` and persist system locale.
- [x] Task 17.2: Implement First-Startup Install Wizard (`SetupWizardActivity.java`) registered in `AndroidManifest.xml`.
- [x] Task 17.3: Add FairEmail (`eu.faircode.email`) to default apps list and download/verify on emulator.

### Checkpoint: Complete Verification — 2026-10-10 local static PASS, E2E runner pending
- [x] `./bin/build_launcher_apk.sh && adb install -r launcher_apk/tontos-launcher.apk` succeeds.
- [x] `cargo test --workspace` passes cleanly (39+ tests green).
- [x] Live UI verification on emulator and screenshot artifacts collected.

## Phase 20+: Linux host with a real Android runtime

### Goal and constraints
Replace the current pseudo-GSI packaging direction with a Linux host that runs a real Android userspace through Waydroid. The first reproducible target is an x86_64 VM; ARM64 and physical-device enablement follow through explicit compatibility targets. “All possible hardware” is not a single build target: kernel features, graphics, input, audio, radio and power interfaces vary by device.

### Architecture decision
- **Problem:** TontOS currently packages Linux binaries and APKs into an ext4 image called `system.img`, but does not contain Android framework/runtime components. Its Waydroid controller only mutates in-memory state.
- **Existing architecture/patterns:** Rust workspace and `tont-appd` lifecycle seam; Java launcher APK; shell scripts that stage an ext4 image; existing Android emulator tooling.
- **Candidates considered:** Port the project into AOSP/LineageOS (conflicts with the selected Linux+Waydroid direction and requires device-specific HAL/kernel work); retain the fake state model (does not run Android); use the supported Waydroid host/container runtime and integrate its real lifecycle.
- **Selected approach:** Treat Linux as the host OS and Waydroid as the Android guest. Integrate through Waydroid’s supported host package/commands and lifecycle rather than pretending the guest is an Android GSI. Keep host power/performance management separate from guest Android policies.
- **Why simplest:** It reuses the chosen, existing Android container runtime and avoids creating an unsupported Android framework or device HAL.
- **Rejected alternatives:** A home-grown Android runtime or an AOSP device port before a concrete device is selected.
- **Consequences and verification:** The current packager creates a Linux rootfs staging archive only; it does not produce a bootable host OS. Host and Android guest artifacts have separate lifecycles. Runtime acceptance requires an x86_64 Linux VM with Binder support, booted Waydroid, and a real guest APK launch; compiling Rust or launching the Android emulator alone is not sufficient.

### Runtime acceptance contract
- Start with an isolated disposable x86_64 VM running a Waydroid-supported Linux host, systemd and a Wayland session. If no physical display is attached, provision an explicit headless compositor such as Weston or Cage.
- Verify the Binder IPC devices actually used by the selected Waydroid build (the Arch baseline exposes `/dev/binderfs/anbox-*`), required namespaces/cgroups, render/EGL support or a declared software-rendering fallback, and the active Wayland socket before attempting runtime startup.
- Configure and verify the VM's IP forwarding and supported firewall backend for the Waydroid bridge. Initialize images using supported Waydroid package/CLI mechanisms; keep Android guest images separate from the Linux host rootfs.
- Start the privileged container and the unprivileged user's session in their proper contexts. `tont-appd` does not elevate itself; systemd authorization must be provided by host policy. Wait for both `sys.boot_completed=1` and a successful Package Manager query (`pm path android`).
- Install and launch a compatible guest APK, verify a rendered surface, and prove guest DNS resolution plus HTTP retrieval. Stop and restart the session/container, verify socket cleanup under `/run/waydroid`, then verify recovery after VM reboot.
- Only designated disposable runtime runners may treat detected missing prerequisites as skips, with a machine-readable reason. On those runners, initialization, boot, app, network, rendering, lifecycle and reboot failures must fail. Setup/teardown must not mutate the developer host.

### Implementation phases
1. **x86_64 runtime foundation:** Provision an isolated Arch Linux x86_64 VM as the first supported test baseline; validate kernel/runtime, graphics and networking prerequisites. The current Linux rootfs archive is not a bootable host image. Remove misleading GSI/Verified Boot identity claims; install and initialize Waydroid using upstream-supported mechanisms.
2. **Runtime integration:** Replace `tont-appd`’s in-memory-only lifecycle with real Waydroid status/start/stop/launch operations, correct user/session ownership, actionable errors, and bounded recovery behavior. Preserve a fakeable command seam for unit tests, but require the full VM acceptance contract above.
3. **Basic system and customization:** Exercise launcher, settings persistence, app installation/launch, input, network/audio/display paths in the guest. Close functional gaps only against observable end-to-end behavior.
4. **Battery and performance:** Establish repeatable idle and interaction baselines first; remove busy polling/no-op daemons; use Android’s native power behavior where available and host mechanisms for Linux. Do not claim universal charging controls or battery savings without supported hardware and measured results.
5. **Updates and trust:** Separate host distribution updates from Android guest image updates; keep OTA generation disabled until a supported authenticated updater and rollback checks exist; remove fabricated bootloader/Verified Boot controls.
6. **Expand compatibility:** Add ARM64 and device-family targets only with documented kernel/graphics/input/power prerequisites and dynamic validation on representative hardware.

### Stop conditions and checkpoints
- Do not call the x86_64 target a phone ROM or GSI.
- Do not claim battery/performance improvement based only on static inspection or emulator telemetry.
- Stop expansion when the phase’s acceptance criteria pass; unsupported hardware features must report unsupported rather than silently claiming success.
- Checkpoint after the real guest boot/launch path and again after power/performance measurements.
- Android Emulator tests alone do not satisfy Waydroid host/runtime acceptance.
