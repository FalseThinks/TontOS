# TontOS Android Platform Intent

## Outcome

Build a shared Android platform for TontOS with an installer and build tooling
that detects a phone's hardware and assembles or compiles the appropriate
device configuration.

## User and motivation

The platform is for the project owner and people who want to install TontOS on
compatible phones. The goal is to evolve beyond the current Linux + Waydroid
prototype toward a real, maintainable Android ROM.

## Success

For every explicitly supported device, the installation flow identifies the
device, selects compatible platform components, configures Android, boots, and
passes a documented set of basic hardware and user-facing feature checks.

## Constraints

- Use publicly available source code where possible.
- Reuse compatible OEM firmware and proprietary components supplied for the
  target device when necessary and legally distributable.
- Require an unlockable bootloader and sufficient device support.
- Use Rust where it provides a practical benefit for detection, build
  orchestration, validation, or installation tooling.
- Add supported devices incrementally; do not claim universal compatibility or
  synthesize unavailable firmware, kernels, or drivers.

## Out of scope

- Claiming that one unmodified binary can boot on every phone.
- Bypassing a locked bootloader or fabricating missing device drivers.
- Treating the current Waydroid guest or launcher APK as a phone ROM.
