# Implementation Plan: TontOS (v1.0-beta) - Feature Slices & Theme Engine

## Overview
Add to TontOS v1.0-beta:
1. OTA update channel selector (`release`, `beta`, `nightly`) with channel-aware release endpoints.
2. Pinned apps custom reordering in main view (`[ ^ ]` and `[ v ]` priority sort).
3. Cryptographic backup + restore via `/data/local/tmp/tont-backup` with live preferences snapshot/restore.
4. Low-power theme engine (AMOLED Black, Amber CRT, E-Ink Monochrome, Slate Midnight), zero-recreate dynamic token switch in Android + Rust `tont-settings`.
5. GitHub CI/CD quality pipeline (Trivy, Semgrep/CodeQL, cargo-audit, Playwright/Maestro UI test harness) + repo initialization.
6. Region & Language pickers, first-boot Install Wizard (`SetupWizardActivity.java`), FairEmail integration.

---

## Architecture Decision Record (ADR)
- **Problem:**
  - Need user-configurable OTA release channels without cloud sync bloat.
  - Need manual ordering for pinned apps on minimalist home screen without heavy drag-drop libraries.
  - Need full snapshot + restore of user state without cloud Google Backup.
  - Need 4 low-power/detox visual themes without `recreate()` UI flicker or XML theme bloat.
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
  - Update Channel: `ota_update_channel` preference drives GitHub Releases URLs.
  - App Reordering: Ordered list in `pinned_apps_order` preference, modified via up/down buttons in Settings Section 3.
  - Backup & Restore: Two buttons in Section 5 run `tont-backup create` and `tont-backup restore` with SharedPreferences serialization.
  - Theme Engine: `ThemeTokens` class provides color tokens for 4 presets, applied live to headers, buttons, accents, backgrounds.
  - CI/CD: `.github/workflows/ci.yml` (Rust + Android build + Trivy + Semgrep), `.github/workflows/codeql.yml`, `.github/workflows/ui-tests.yml`, `deny.toml`, `.gitignore`.
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
  - Immediate responsive UI updates.
  - Build launcher APK, install on emulator, run unit tests, verify wizard and settings live.

---

## Task Breakdown

### Phase 14: Core Features (Launcher APK)
- [ ] Task 14.1: Implement Update Channel Selector in `SettingsActivity.java` Section 5.
- [ ] Task 14.2: Implement App Sorting & Reordering (`[ ^ ]`, `[ v ]`) in `SettingsActivity.java` Section 3 and `MainActivity.java`.
- [ ] Task 14.3: Implement Backup Create & Restore in `SettingsActivity.java` Section 5 and test integration with `/data/local/tmp/tont-backup`.
- [ ] Task 14.4: Implement `ThemeTokens` and Presets (`AMOLED`, `AMBER`, `E-INK`, `SLATE`) in `SettingsActivity.java` Section 1 and `MainActivity.java`.

### Phase 15: Rust Backend Theme Models
- [ ] Task 15.1: Implement `ThemeConfig` and `ThemePreset` models in `crates/tont-settings`.
- [ ] Task 15.2: Unit tests in `crates/tont-settings` verifying theme serialization and token invariants.

### Phase 16: CI/CD Quality Pipeline & Repository Initialization
- [ ] Task 16.1: Initialize Git repo configuration (`.gitignore`, `rustfmt.toml`, `clippy.toml`, `.editorconfig`, `deny.toml`).
- [ ] Task 16.2: Implement GitHub Actions CI workflows (`ci.yml`, `codeql.yml`, `dependabot.yml`) with Trivy, Semgrep, and Rust quality gates.
- [ ] Task 16.3: Implement UI automated tests (Maestro flows & Playwright test runner).

### Phase 17: Region/Language Pickers, Install Wizard & FairEmail
- [ ] Task 17.1: Add Language & Region pickers in `SettingsActivity.java` and persist system locale.
- [ ] Task 17.2: Implement First-Startup Install Wizard (`SetupWizardActivity.java`) registered in `AndroidManifest.xml`.
- [ ] Task 17.3: Add FairEmail (`eu.faircode.email`) to default apps list and download/verify on emulator.

### Checkpoint: Complete Verification
- [ ] `./scripts/build_launcher_apk.sh && adb install -r launcher_apk/tontos-launcher.apk` succeeds.
- [ ] `cargo test --workspace` passes cleanly (39+ tests green).
- [ ] Live UI verification on emulator and screenshot artifacts collected.
