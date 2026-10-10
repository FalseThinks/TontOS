# TontOS Android Platform Design

This directory contains the approved capability map and draft technical
contracts for evolving TontOS from its current Linux + Waydroid prototype into
a device-aware Android platform.

## Documents

- [Intent](../intent/android-platform.md) — confirmed product outcome and
  constraints.
- [Capability map](CAPABILITY_MAP.md) — approved module boundaries and build
  order.
- [Upstream base decision](DECISION-01-upstream-base.md) — evidence and
  recommendation for the AOSP/LineageOS choice.
- [Android platform base](SPEC-android-platform-base.md)
- [Device support](SPEC-device-support.md)
- [Rust build orchestrator](SPEC-rust-build-orchestrator.md)
- [Safe install and recovery](SPEC-safe-install-recovery.md)
- [Core feature integration](SPEC-core-feature-integration.md)
- [Device validation and release](SPEC-device-validation-release.md)
- [Implementation plan](PLAN.md)
- [Proposed task breakdown](TASKS.md)

The module specifications have been approved for planning. The implementation
plan and task breakdown are now proposed artifacts and require owner review
before implementation begins.

## Current technical facts and sources

- AOSP builds require an explicit product target (`lunch`) and host build
  environment; Rust tooling can orchestrate that system but does not replace
  it: <https://source.android.com/docs/setup/build/building>
- Android kernel sources/toolchains/build configurations are selected
  separately from the platform tree:
  <https://source.android.com/docs/setup/build/building-kernels>
- VINTF manifests and compatibility matrices represent device-provided and
  framework-required interfaces:
  <https://source.android.com/docs/core/architecture/vintf>
- Partition layout and system-as-root behavior are Android-version/device
  dependent: <https://source.android.com/docs/core/architecture/partitions/system-as-root>
- Bootloader unlocking is device/user-controlled and can require a factory
  reset: <https://source.android.com/docs/core/architecture/bootloader/locking_unlocking>
- AVB signing and rollback protection require bootloader/platform-specific
  trust integration:
  <https://source.android.com/docs/security/features/verifiedboot/avb>
- A/B update rollback depends on device slots, bootloader state, and
  update-engine integration; it is not a generic installer's promise:
  <https://source.android.com/docs/core/ota/ab>

## Architecture decision record

- **Problem:** Build one recognizable TontOS Android product that can support
  multiple devices without pretending their boot chains and hardware are
  interchangeable.
- **Existing architecture:** This repository is a Linux + Waydroid host
  prototype with Rust workspace services and an Android launcher APK. It has
  no AOSP checkout, device tree, OEM vendor integration, or phone flashing
  flow. Existing desktop-oriented Rust services are not Android HALs.
- **Candidates:** (1) extend Waydroid; (2) create one generic ROM binary;
  (3) Android source-based shared product plus explicit device profiles and
  host-side Rust orchestration.
- **Selected approach:** candidate 3. Keep upstream Android platform builds
  native, isolate per-device support, and use a Rust host CLI for safe
  detection, validation, orchestration, and artifact bookkeeping.
- **Why simplest effective:** It reuses Android's existing build/compatibility
  system, leaves device-specific facts at the device boundary, and avoids
  unsupported universal hardware abstractions or replacing Soong with a new
  build system.
- **Rejected:** Waydroid does not produce a flashable phone OS; a generic
  binary cannot substitute for vendor kernel/firmware/HAL/partition
  requirements; microservices/plugins add no value for a local build tool at
  this stage.
- **Consequences and verification:** Select a maintained upstream base and a
  pilot phone before implementation; require source/profile provenance,
  physical-device installation and recovery, and feature-specific tests.
  Host-side build orchestration is the intended interpretation of
  device-specific compilation during installation.

The base distribution, first device, build-host support matrix, OEM component
policy, and release signing/update policy remain open decisions.
