# Spec: Android Platform Base

Module ID: `android-platform-base`
Status: Draft for owner review
Intent: [`../intent/android-platform.md`](../intent/android-platform.md)

## Objective

Replace the assumption that a launcher over Waydroid is the Android product
with a maintainable Android source-based platform. Provide one shared TontOS
product experience while preserving upstream Android architecture and making
device-specific integration explicit.

The base-selection study must compare AOSP and a maintained Android
distribution such as LineageOS for upstream cadence, device-support reuse,
build/release requirements, licensing, security updates, and ability to carry
TontOS product changes. This spec does not claim that either base is already
selected.

## Tech stack and architecture

- Android platform source, product configuration, and Soong/Blueprint as
  required by the selected upstream branch.
- Rust 2021 host tools remain separate from the Android platform build.
- Current repository dependencies are a Rust workspace using CI's stable
  toolchain; no Android source tree or Android platform version is pinned yet.
- Preserve Android's framework/vendor separation; device implementations must
  declare and validate the relevant VINTF compatibility.
- Model TontOS shared UI/system changes as upstream-compatible product or
  system components. Keep target-specific kernel, vendor, and firmware
  configuration in the device-support boundary.

## Commands

Current repository checks:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```

Android platform command shape after the upstream and product target are
selected:

```sh
source build/envsetup.sh
lunch <product>-<release_config>-userdebug
m
```

`<product>` and `<release_config>` must come from the selected base and a
validated device profile; these placeholders are not executable targets yet.

## Project structure

- `platform/` — upstream Android source checkout (separate managed workspace,
  not vendored into this repository).
- `product/` — shared TontOS product, system apps, overlays, and product
  configuration.
- `device/<vendor>/<codename>/` — device-specific implementation owned by
  `device-support`.
- `docs/android-platform/` — capability map, module contracts, decisions, and
  verification records.

## Code style and interfaces

Use Android upstream conventions for Android product/device files and the
repository's `rustfmt`/Clippy conventions for Rust host tools. Shared Android
interfaces must be versioned and validated at module boundaries; device
specific facts must not leak into shared product configuration.

Example profile reference (illustrative contract, not an existing file):

```yaml
schema_version: 1
device:
  vendor: example
  codename: example-device
  android_product: tont_example
  support_status: experimental
```

## Testing strategy

- Validate source manifests and product configuration before building.
- Build at least one selected device product from a clean source sync.
- Run Android platform/unit tests relevant to modified modules.
- Boot the built image in Cuttlefish/emulator for framework-level checks;
  emulator results do not count as physical-device support.
- Validate VINTF compatibility and record the exact source revision, product,
  build variant, and build artifacts.

## Boundaries

- **Always:** keep upstream and TontOS changes distinguishable; pin source
  revisions; separate shared product logic from device-specific integration;
  report unsupported functions honestly.
- **Ask first:** choose AOSP versus a downstream base; change Android release
  cadence; add major build dependencies; define supported security/update
  guarantees.
- **Never:** call the Waydroid guest a phone ROM; claim a build boots on a
  device without evidence; weaken upstream security properties silently.

## Success criteria

- A maintained upstream base and release branch are selected and pinned.
- A shared TontOS product builds reproducibly for an explicitly supported
  device target.
- The shared product configuration contains no device-specific kernel or
  vendor assumptions.
- Build and compatibility evidence is retained for the release candidate.

## Open questions

- AOSP or LineageOS (or another maintained base)?
- Which Android release branch is appropriate at project kickoff?
- Which first physical phone is available for bring-up and recovery testing?
