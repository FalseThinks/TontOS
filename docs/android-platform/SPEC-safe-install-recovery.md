# Spec: Safe Install and Recovery

Module ID: `safe-install-recovery`
Status: Draft for owner review
Intent: [`../intent/android-platform.md`](../intent/android-platform.md)

## Objective

Install only a verified build for a positively identified, explicitly
supported phone. Make destructive effects and device limitations clear before
any write, and provide only recovery/rollback procedures proven for that
device.

## Tech stack and architecture

- Rust host CLI from `rust-build-orchestrator`.
- `adb`/`fastboot` and upstream device-specific installation mechanisms.
- Device profile controls permitted partitions and recovery behavior; there is
  no universal flashing recipe.
- Artifact provenance and integrity verification precede installation.

## Contract

Proposed flow:

```text
inspect → compatibility check → show exact changes/data-loss warning →
explicit user confirmation → verify artifacts → device-specific install →
reboot → first-boot health check → recovery guidance on failure
```

The flow must stop if identity, bootloader state, profile, partition layout,
artifact signature/hash, or required recovery capability is unknown or
inconsistent. It must never attempt a bootloader unlock automatically.

## Commands

Current repository checks:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```

Proposed safe interface:

```sh
tontosctl install plan --device <vendor/codename> --artifacts <manifest> --dry-run
tontosctl install apply --device <vendor/codename> --artifacts <manifest>
tontosctl recovery guide --device <vendor/codename>
```

`install apply` is not implemented and must not exist as a no-confirmation or
generic fastboot mode. Documentation must describe that bootloader unlocking
often factory-resets user data.

## Project structure

- `crates/tont-romctl/src/install/` — state machine and confirmation workflow.
- `crates/tont-romctl/src/transport/` — fastboot/adb adapters.
- `devices/<vendor>/<codename>/install/` — reviewed device-specific actions.
- `tests/install/` — command fakes, destructive-step guards, and failure
  recovery tests.

## Code style and interfaces

Installation is a stateful operation with explicit states and typed outcomes.
Use a dry-run plan as a first-class output and require a separate confirmation
token/interaction tied to the exact device, artifact hashes, and actions.
Retries must not repeat destructive writes without rechecking current device
state.

## Testing strategy

- Unit-test every state transition and verify unexpected transitions fail
  closed.
- Fake `fastboot`/`adb` to test device mismatch, disconnects, timeouts,
  partial-write uncertainty, and nonzero exits.
- Assert no write command occurs in dry-run or before confirmation.
- Validate recovery instructions and factory-reset warnings for each device
  profile.
- Initial flashing/recovery acceptance occurs on a disposable or recoverable
  physical pilot device, not only an emulator.

## Boundaries

- **Always:** show target identity, data-loss effects, partition list, and
  recovery limits before writing; verify artifact hashes; stop on uncertainty.
- **Ask first:** unlock bootloader, erase userdata, flash critical partitions,
  relock bootloader, or install an image not produced by the selected build.
- **Never:** bypass bootloader security; silently unlock/relock; flash unknown
  devices; promise rollback where the bootloader/partition layout cannot
  provide it.

## Success criteria

- No write is possible until the connected device and artifacts match an
  approved profile and the user confirms the exact plan.
- All tested command errors and uncertain partial outcomes surface explicit
  recovery steps; no success-shaped fallback occurs.
- Pilot installation, first boot, and recovery are demonstrated on real
  hardware before general availability.

## Open questions

- Which host operating systems and transport methods are in the first release?
- Is bootloader unlocking (and its factory reset) acceptable to target users?
- Which recovery path is supportable for the pilot device?
