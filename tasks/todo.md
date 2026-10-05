# TontOS Task List

## Phase 1: Foundation & Shared IPC
- [x] Task 1.1: Initialize Cargo workspace and create `crates/tont-common`
  - Acceptance: Workspace `Cargo.toml` links crates; `tont-common` defines `PowerState`, `AppStatus`, `SyncConfig` with serde.
  - Verify: `cargo test -p tont-common` passes.
  - Files: `Cargo.toml`, `crates/tont-common/Cargo.toml`, `crates/tont-common/src/lib.rs`
- [x] Task 1.2: Implement `crates/tont-powerd` daemon skeleton
  - Acceptance: Race-free suspend via `/sys/power/wakeup_count` + mockable battery monitor.
  - Verify: `cargo test -p tont-powerd` passes.
  - Files: `crates/tont-powerd/Cargo.toml`, `crates/tont-powerd/src/lib.rs`, `crates/tont-powerd/src/main.rs`

## Checkpoint: Foundation
- [x] `cargo check --workspace` passes cleanly.
- [x] Foundation unit tests pass.

## Phase 2: Process Lifecycle & Radio Daemons
- [x] Task 2.1: Implement `crates/tont-appd` cgroups v2 freezer controller
  - Acceptance: Provide `freeze_app(cgroup_path)` and `thaw_app(cgroup_path)` targeting `/sys/fs/cgroup`.
  - Verify: `cargo test -p tont-appd` passes.
  - Files: `crates/tont-appd/Cargo.toml`, `crates/tont-appd/src/lib.rs`, `crates/tont-appd/src/main.rs`
- [x] Task 2.2: Implement `crates/tont-syncd` radio batching scheduler
  - Acceptance: Configurable sync window timer via `rustix` `timerfd_create`.
  - Verify: `cargo test -p tont-syncd` passes.
  - Files: `crates/tont-syncd/Cargo.toml`, `crates/tont-syncd/src/lib.rs`, `crates/tont-syncd/src/main.rs`

## Phase 3: Compositor & UI Shell
- [x] Task 3.1: Implement `crates/tont-compositor` skeleton
  - Acceptance: Minimal Wayland compositor skeleton with desktop test harness + DRM/KMS.
  - Verify: `cargo check -p tont-compositor` succeeds.
  - Files: `crates/tont-compositor/Cargo.toml`, `crates/tont-compositor/src/main.rs`
- [x] Task 3.2: Implement `crates/tont-shell` minimalist AMOLED launcher
  - Acceptance: Slint UI with AMOLED `#000000`, clock, battery gauge, app launcher.
  - Verify: `cargo check -p tont-shell` succeeds.
  - Files: `crates/tont-shell/Cargo.toml`, `crates/tont-shell/ui/main.slint`, `crates/tont-shell/src/main.rs`

## Phase 4: Telephony & Modem Integration
- [x] Task 4.1: Implement `crates/tont-telephony` models & call/SMS state machine
  - Acceptance: `CallState` and `SmsMessage` state machine for inbound call + message dispatch.
  - Verify: `cargo test -p tont-telephony` passes.
  - Files: `crates/tont-telephony/Cargo.toml`, `crates/tont-telephony/src/lib.rs`, `crates/tont-telephony/src/main.rs`

## Phase 5: Waydroid Container Lifecycle Management
- [x] Task 5.1: Implement `WaydroidController` in `crates/tont-appd`
  - Acceptance: Session state machine (Stopped, Running, Frozen), package name check, cgroups v2 container freeze.
  - Verify: `cargo test -p tont-appd` passes.
  - Files: `crates/tont-appd/src/waydroid.rs`, `crates/tont-appd/src/lib.rs`

## Phase 6: Sensor Subsystem (Orientation, Proximity & Light)
- [x] Task 6.1: Implement `crates/tont-sensors` daemon
  - Acceptance: IIO sensor models, accelerometer orientation with hysteresis, ear proximity check.
  - Verify: `cargo test -p tont-sensors` passes.
  - Files: `crates/tont-sensors/Cargo.toml`, `crates/tont-sensors/src/lib.rs`, `crates/tont-sensors/src/main.rs`

## Phase 7: Hardware Key Events (Power & Volume Buttons)
- [x] Task 7.1: Implement `crates/tont-input` hardware button processor
  - Acceptance: Power button short press (display/sleep) vs long press (power menu), volume routing.
  - Verify: `cargo test -p tont-input` passes.
  - Files: `crates/tont-input/Cargo.toml`, `crates/tont-input/src/lib.rs`, `crates/tont-input/src/main.rs`

## Phase 8: System Init & GSI Rootfs Packaging
- [x] Task 8.1: Create systemd unit service files for all TontOS daemons
  - Acceptance: Unit files in `init/` with correct dependencies and restart policies.
  - Verify: Service file syntax check.
  - Files: `init/*.service`
- [x] Task 8.2: Implement `scripts/make_gsi_rootfs.sh` packaging pipeline
  - Acceptance: Script builds binaries and stages rootfs for sparse `system.img`.
  - Verify: `./scripts/make_gsi_rootfs.sh --dry-run` succeeds.
  - Files: `scripts/make_gsi_rootfs.sh`

## Phase 9: Connectivity Services & Full Customization Engine
- [x] Task 9.1: Implement `crates/tont-settings` configuration & hardware toggle manager
  - Acceptance: Manage WiFi/BT/Data/GPS toggles, default apps, ringtone paths, AMOLED display prefs.
  - Verify: `cargo test -p tont-settings` passes.
  - Files: `crates/tont-settings/Cargo.toml`, `crates/tont-settings/src/lib.rs`, `crates/tont-settings/src/main.rs`

## Phase 10: TontOS Settings Application
- [x] Task 10.1: Implement `crates/tont-settings-app` minimalist AMOLED settings app
  - Acceptance: Category navigation (Connectivity, Sounds, Default Apps, Display/Battery, About) and config mutations.
  - Verify: `cargo test -p tont-settings-app` passes.
  - Files: `crates/tont-settings-app/Cargo.toml`, `crates/tont-settings-app/src/lib.rs`, `crates/tont-settings-app/src/main.rs`

## Phase 11: Quick-Settings Drop-Down Shade & Detox Engine
- [x] Task 11.1: Implement `QuickShade` in `crates/tont-shell`
  - Acceptance: Brightness `/sys/class/backlight` control, volume sliders, quick toggles, screen-time detox counter.
  - Verify: `cargo test -p tont-shell` passes.
  - Files: `crates/tont-shell/src/shade.rs`, `crates/tont-shell/src/lib.rs`

## Phase 12: Backup & Restore Tool
- [x] Task 12.1: Implement `crates/tont-backup` archival & restore engine
  - Acceptance: Export/import system settings, SMS history, app registry with SHA-256 integrity verification.
  - Verify: `cargo test -p tont-backup` passes.
  - Files: `crates/tont-backup/Cargo.toml`, `crates/tont-backup/src/lib.rs`, `crates/tont-backup/src/main.rs`

## Phase 13: GSI Image Build & Live Input/Mixer Integration
- [x] Task 13.1: Build flashable Project Treble sparse GSI image (`target/gsi/tontos-system.img`) using `mke2fs`, `e2fsdroid`, `img2simg`
  - Acceptance: Self-contained 40M sparse ext4 image with 10 release musl binaries, systemd units, default `/etc/tontos/settings.json`.
  - Verify: Fastboot-ready image output verified in `target/gsi/`.
  - Files: `scripts/make_gsi_rootfs.sh`, `target/gsi/tontos-system.img`
- [x] Task 13.2: Implement Linux evdev raw input parser and `AudioMixerController` in `crates/tont-input`
  - Acceptance: Non-blocking discovery across `/dev/input/event*`, Unix socket volume control, key state transitions.
  - Verify: `cargo test -p tont-input` passes (6/6 tests); verified on emulator with 14 active evdev nodes.
  - Files: `crates/tont-input/src/lib.rs`, `crates/tont-input/src/main.rs`
- [x] Task 13.3: Emulator live verification of backup generation and tamper detection
  - Acceptance: Generate and verify encrypted/hashed backup on emulator (`emulator-5554`).
  - Verify: `tont-backup create` and `tont-backup verify` execution on device.

## Phase 14: Core Features (Launcher APK)
- [x] Task 14.1: Implement Update Channel Selector in `SettingsActivity.java` Section 5
  - Acceptance: `[ RELEASE ] [ BETA ] [ NIGHTLY ]` choices; saved in `ota_update_channel`; updates channel label + browser URL.
  - Verify: Channel selection updates prefs and opens matching release target.
  - Files: `launcher_apk/src/org/tontos/launcher/SettingsActivity.java`
- [x] Task 14.2: Implement App Sorting & Reordering in Settings Section 3 and Main View
  - Acceptance: `[ ^ ]` and `[ v ]` buttons per pinned app; order saved in `pinned_apps_order`; `MainActivity.java` renders in custom order.
  - Verify: Changing order in Settings updates Home screen app order.
  - Files: `launcher_apk/src/org/tontos/launcher/SettingsActivity.java`, `launcher_apk/src/org/tontos/launcher/MainActivity.java`
- [x] Task 14.3: Implement Cryptographic Backup Create & Restore
  - Acceptance: `[ CREATE BACKUP ]` dumps SharedPreferences and creates `.tontbak` archive; `[ RESTORE BACKUP ]` unpacks and reloads prefs; verified with native `tont-backup verify` on device.
  - Verify: Backup creates valid `.tontbak` file; restore recovers settings.
  - Files: `launcher_apk/src/org/tontos/launcher/SettingsActivity.java`
- [x] Task 14.4: Implement Minimalist Low-Power Theme Engine
  - Acceptance: Presets `AMOLED Black`, `Amber CRT`, `E-Ink Monochrome`, `Slate Midnight` in `ThemeTokens`; selectable in Section 1 & Wizard; dynamic colors on Home and Settings without reload.
  - Verify: Switching preset applies background, text, and accent colors instantly.
  - Files: `launcher_apk/src/org/tontos/launcher/ThemeTokens.java`, `launcher_apk/src/org/tontos/launcher/SettingsActivity.java`, `launcher_apk/src/org/tontos/launcher/MainActivity.java`

## Phase 15: Rust Backend Theme Models
- [x] Task 15.1: Implement `ThemeConfig` and `ThemePreset` in `crates/tont-settings`
  - Acceptance: Rust models match color token matrix; serde JSON serialization/deserialization.
  - Verify: `cargo test -p tont-settings` passes with theme unit tests.
  - Files: `crates/tont-settings/src/theme.rs`, `crates/tont-settings/src/lib.rs`

## Phase 16: CI/CD Quality Pipeline & Repository Initialization
- [x] Task 16.1: Initialize Git repo configuration
  - Acceptance: `.gitignore`, `rustfmt.toml`, `clippy.toml`, `.editorconfig`, `deny.toml` created at root.
  - Verify: `git status` cleanly ignores build artifacts; `cargo fmt --check` respects config.
  - Files: `.gitignore`, `rustfmt.toml`, `clippy.toml`, `.editorconfig`, `deny.toml`
- [x] Task 16.2: Implement GitHub Actions CI workflows
  - Acceptance: `.github/workflows/ci.yml` (Rust quality + Android build + Trivy fs scan + Semgrep SAST), `.github/workflows/codeql.yml`, `.github/dependabot.yml`.
  - Verify: YAML lint syntax validation.
  - Files: `.github/workflows/ci.yml`, `.github/workflows/codeql.yml`, `.github/dependabot.yml`
- [x] Task 16.3: Implement UI automated tests
  - Acceptance: Maestro flow YAMLs in `.maestro/flows/` + Playwright UI test runner in `tests/ui/` + `.github/workflows/ui-tests.yml`.
  - Verify: Test runner executes cleanly against emulator or headless target.
  - Files: `.maestro/flows/*.yaml`, `tests/ui/*`, `.github/workflows/ui-tests.yml`

## Phase 17: Region/Language Pickers, Install Wizard & FairEmail
- [x] Task 17.1: Add Language & Region pickers in `SettingsActivity.java`
  - Acceptance: Choice rows for Language (English, Español, Deutsch, Français) and Region in Section 1 and Setup Wizard.
  - Verify: Selection updates SharedPreferences and changes UI labels.
  - Files: `launcher_apk/src/org/tontos/launcher/SettingsActivity.java`, `launcher_apk/src/org/tontos/launcher/SetupWizardActivity.java`
- [x] Task 17.2: Implement First-Startup Install Wizard
  - Acceptance: `SetupWizardActivity.java` runs if `!prefs.getBoolean("setup_completed", false)`; 4-step OOBE wizard (Welcome, Language/Region, Theme, Detox Budget); marks setup completed.
  - Verify: Fresh install opens wizard; completing moves to home screen.
  - Files: `launcher_apk/src/org/tontos/launcher/SetupWizardActivity.java`, `launcher_apk/AndroidManifest.xml`
- [x] Task 17.3: Integrate FairEmail
  - Acceptance: `eu.faircode.email` included in default pinned apps list; detected in launcher and drawer; installed and verified on emulator.
  - Verify: App listed in pinned defaults and can be launched when installed.
  - Files: `launcher_apk/src/org/tontos/launcher/MainActivity.java`, `launcher_apk/src/org/tontos/launcher/SettingsActivity.java`

## Phase 18: UX Audit & Frontend Design System Polish
- [x] Task 18.1: UX Audit (Maze & Jakob Nielsen's 10 Heuristics)
  - Acceptance: Evaluated against 10 usability heuristics and Maze metrics via subagent.
  - Report: Documented severity matrix; prioritized touch target sizes, layout wrap bug, safety confirmations.
- [x] Task 18.2: Frontend Design Architecture & Pattern Integration
  - Acceptance: Installed `frontend-design` skill; applied State Pattern (wizard), Strategy Pattern (themes), Builder Pattern (UI rows).
  - Design Tokens: 48dp minimum touch targets, AMOLED black (#000000), tactile borders.
- [x] Task 18.3: Implementation & Polish
  - Settings: 48dp reorder hitboxes with margin separation, vertical info layout (fixing kernel wrap), AMOLED confirmation dialogs.
  - Setup Wizard: Pinned bottom dual navigation dock (`[ < PREVIOUS ]` / `[ NEXT > ]`), 48dp tactile choice targets, live theme switching.
  - MainActivity: 10-block detox budget progress bar (`[■■■■■■■■■■]`), real-time drawer search filter with `TextWatcher`.
  - Verification: Clean APK build (44KB), all tests passing, emulator screenshot verification.

## Checkpoint: Final Verification
- [x] `./scripts/build_launcher_apk.sh && adb install -r launcher_apk/tontos-launcher.apk` succeeds.
- [x] `cargo test --workspace` passes cleanly (42/42 tests green).
- [x] Live UI verification on emulator and screenshot artifacts captured.

## Phase 19: Minimal Ultra Battery Saver Top Shade & Quick Toggles
- [x] Task 19.1: Implement `UltraBatterySaverManager.java` facade
  - Acceptance: Direct control for Airplane Mode, Do Not Disturb (DND), Wi-Fi, Mobile Data, Bluetooth, and Flashlight Torch.
  - Verify: Hardware states update and persist correctly.
  - Files: `launcher_apk/src/org/tontos/launcher/UltraBatterySaverManager.java`
- [x] Task 19.2: Implement `UltraShadeDialog.java` UI
  - Acceptance: Minimal slide-down shade mimetizing Xiaomi Ultra Battery Saver mode, 100% styled with active `ThemeTokens` (AMOLED, Amber CRT, E-Ink, Slate).
  - Verify: Dynamic background, borders, active chips, and autonomy forecast render cleanly.
  - Files: `launcher_apk/src/org/tontos/launcher/UltraShadeDialog.java`
- [x] Task 19.3: Gesture and Tap Integration in `MainActivity.java`
  - Acceptance: Swipe down gesture anywhere on screen or tap on top clock/battery bar triggers `UltraShadeDialog.show()`.
  - Verify: Gestures responsive without interfering with app list scrolling.
  - Files: `launcher_apk/src/org/tontos/launcher/MainActivity.java`
- [x] Task 19.4: Manifest Permissions & Localization
  - Acceptance: Permissions added to `AndroidManifest.xml`; keys translated into `es`, `en`, `de`, `pl` in `I18n.java`.
  - Verify: Build succeeds (160K APK) and labels render localized.
  - Files: `launcher_apk/AndroidManifest.xml`, `launcher_apk/src/org/tontos/launcher/I18n.java`, `scripts/debloat_custom_rom.sh`
- [x] Task 19.5: Live Emulator Verification Across Themes
  - Acceptance: Verified on `emulator-5554` across AMOLED, Amber CRT, and E-Ink Paper.
  - Verify: Screenshots `shade_opened.png`, `shade_swipe.png`, `shade_dnd.png`, `shade_amber.png`, `shade_eink.png`, `shade_slate.png`.

