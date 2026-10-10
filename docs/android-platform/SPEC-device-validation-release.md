# Spec: Device Validation and Release

Module ID: `device-validation-release`
Status: Draft for owner review
Intent: [`../intent/android-platform.md`](../intent/android-platform.md)

## Objective

Publish TontOS builds only for devices whose install, boot, recovery, core
functions, and security properties have been tested. Make compatibility and
known limitations visible for each release.

## Tech stack and architecture

- Existing GitHub Actions workflow plus reproducible Android build runners
  selected after host requirements are measured.
- Cuttlefish/emulator for common Android framework checks; physical devices for
  hardware acceptance.
- Versioned device profile, build manifest, test report, hashes, and signing
  metadata tie each artifact to its source and test evidence.
- Release status is per device: `experimental`, `supported`, or `retired`.

## Commands

Existing baseline:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```

Future release verification commands are defined with the selected AOSP
branch, profile and hardware lab. CI may publish experimental artifacts, but
must not label them production-supported without passing physical-device
criteria.

## Project structure

- `.github/workflows/` — source validation, builds, emulator checks, and
  artifact publication.
- `tests/device/<codename>/` — automated/manual hardware procedures and
  reports.
- `releases/<version>/<codename>/` — generated manifests and compatibility
  summaries, not checked-in build binaries unless explicitly required.
- `docs/android-platform/` — support policy and release criteria.

## Code style and interfaces

Each test report identifies device model/SKU, bootloader state, firmware/vendor
revision, Android source revision, build variant, test results, and known
limitations. Machine-readable status must distinguish skipped (with reason),
failed, not run, and passed; missing hardware must never count as a pass.

## Testing strategy

- Static checks and unit tests run on every pull request.
- Emulator/Cuttlefish integration validates common framework flows.
- Device bring-up and release smoke tests run on each physical supported
  device.
- Installation recovery, boot, networking, display, input, audio, and device
  features are release acceptance gates.
- Regression test the install and recovery path before upgrading any supported
  device profile.

## Boundaries

- **Always:** publish hashes and exact source/profile revisions; list tested
  devices and untested features; preserve failed logs and skip reasons.
- **Ask first:** mark a device `supported`, publish artifacts containing OEM
  components, or change the update/signing trust model.
- **Never:** infer physical-device compatibility from a successful build or
  emulator; silently suppress hardware test failures; imply official
  certification that was not obtained.

## Success criteria

- Every released artifact maps to a device profile, immutable source revision,
  build manifest, and integrity hash.
- Support status is backed by completed installation, boot, recovery, and
  feature test records on the same device variant.
- CI distinguishes failed, skipped, and untested work and blocks release
  promotion when required evidence is absent.

## Open questions

- What minimum build/test hardware and release cadence are affordable?
- Where should signing keys be stored and who controls release signing?
- What security update and end-of-support policy can the project maintain?
