# TontOS Android Platform Capability Map

Status: approved by the project owner on 2026-10-06.

| Module ID | Responsibility | Depends on |
|---|---|---|
| `android-platform-base` | Select and integrate a maintained Android base, shared TontOS experience, settings, and customization | — |
| `device-support` | Device inventory, bootloader/kernel/vendor compatibility, authorized OEM components, and per-device profiles | `android-platform-base` |
| `rust-build-orchestrator` | Detect target, validate prerequisites, and build/assemble reproducible device artifacts | `device-support` |
| `safe-install-recovery` | Preflight, guided installation, artifact integrity, device-specific recovery, and supported rollback | `rust-build-orchestrator` |
| `core-feature-integration` | Bring up and validate core Android functions and TontOS features on supported devices | `device-support`, `android-platform-base` |
| `device-validation-release` | Real-device test matrix, support status, release artifacts, and honest compatibility claims | `safe-install-recovery`, `core-feature-integration` |

## Build order

`android-platform-base` → `device-support` → `rust-build-orchestrator` →
`safe-install-recovery` → `core-feature-integration` and
`device-validation-release`. The final two may proceed in parallel after an
installable target exists.

Rust is used for practical host-side detection, validation, and orchestration.
It does not replace Android's platform build system and cannot create missing
kernel, firmware, HAL, or device drivers.

Each module has a contract in the matching `SPEC-<module-id>.md` file. The
implementation plan and task breakdown are intentionally pending specification
approval.
