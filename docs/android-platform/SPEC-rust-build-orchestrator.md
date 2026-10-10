# Spec: Rust Build Orchestrator

Module ID: `rust-build-orchestrator`
Status: Draft for owner review
Intent: [`../intent/android-platform.md`](../intent/android-platform.md)

## Objective

Provide a Rust host-side CLI that identifies a connected supported phone,
validates its profile and build prerequisites, and drives the selected
upstream Android build system to create a matching, auditable build.

Compilation occurs on the host computer before flashing. The Android platform
build is not compiled on the phone during installation: AOSP requires a
configured host source/build environment and explicit product target.

## Tech stack and architecture

- Rust 2021, stable toolchain (the workspace currently follows CI stable and
  does not pin `rust-toolchain.toml`).
- Existing workspace dependencies include Tokio, Serde, Serde JSON, and
  thiserror. New dependencies require review.
- Keep Android build logic in AOSP/Soong/Bazel and use Rust as orchestration,
  validation, logging, and artifact-manifest tooling.
- Use a modular host CLI with ports/adapters for device transport (`adb`,
  `fastboot`), profile loading, and build execution. Keep external process
  invocation behind testable boundaries.
- Pipeline: profile load → connected-device detection → prerequisite check →
  build plan → explicit build → artifact verification/manifest.

## Contract

Proposed CLI:

```text
tontosctl device detect --format json
tontosctl device inspect --serial <serial>
tontosctl build --device <vendor/codename> --variant userdebug --dry-run
tontosctl build --device <vendor/codename> --variant user
tontosctl artifacts verify --manifest <path>
```

`detect` is read-only. `--dry-run` must not sync sources, write device
partitions, or mutate system configuration. A build result records upstream
revision, device profile/schema version, build variant, tool versions, artifact
paths, size, and SHA-256 hashes. Machine output uses a versioned JSON schema;
errors have stable machine-readable codes and human-readable context.

## Commands

Existing checks:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```

Future CLI acceptance commands are the proposed `tontosctl` commands above;
the binary does not exist yet.

## Project structure

- `crates/tont-romctl/` — CLI and application orchestration.
- `crates/tont-device-profile/` — shared profile and validation contract, only
  if more than one consumer needs it.
- `crates/tont-build/` — build-plan and artifact-manifest logic, introduced
  only when separated modules justify it.
- `tests/fixtures/devices/` — representative profiles and transport output.

The initial implementation should stay in one CLI crate until concrete
reuse justifies splitting crates.

## Code style and interfaces

Use typed command inputs/results, explicit enums for build and detection
outcomes, boundary validation for device/transport output, and structured
errors. Never use shell-interpolated command strings for serial numbers,
profile fields, or paths; pass arguments separately.

Illustrative build metadata:

```json
{
  "schema_version": 1,
  "device": "vendor/codename",
  "source_revision": "immutable-revision",
  "variant": "userdebug",
  "artifacts": [{"path": "boot.img", "sha256": "computed-digest"}]
}
```

## Testing strategy

- Unit tests for profile-to-target mapping, argument construction, and
  deterministic manifests.
- Fake transport/build adapters for timeout, missing tool, malformed output,
  and nonzero exit scenarios.
- Integration tests execute harmless `--version`/dry-run paths using fake
  executables; no physical device required.
- One clean build on the pilot-device build host proves upstream integration.
- Verify hashes and record build inputs before handing artifacts to installer.

## Boundaries

- **Always:** execute subprocesses without a shell; validate external output;
  bound time/resource use; report the exact failed stage; preserve logs and
  metadata without secrets.
- **Ask first:** add dependencies, perform network/source sync, consume
  user-provided firmware, or run long builds that change source trees.
- **Never:** flash a device from the build command; silently select a
  different device; treat missing profile data as generic compatibility.

## Success criteria

- Read-only detection maps a connected supported target to exactly one
  validated profile or returns an actionable ambiguity/unsupported error.
- Dry-run prints the complete planned build inputs and commands without
  mutation.
- A clean build emits reproducible metadata and verified hashes.
- Tests prove malformed device output and tool failures do not look successful.

## Open questions

- Which host operating systems must the CLI support initially?
- Should the CLI download/sync Android sources, or require the user to
  provision a verified source checkout?
- Which build variants may be distributed beyond development?
