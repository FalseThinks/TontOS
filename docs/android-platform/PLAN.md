# Implementation Plan: TontOS Android Platform

Status: Approved plan; Phase 0 is in progress.
Task list: [`TASKS.md`](TASKS.md)

## Overview

Evolve the current Linux + Waydroid prototype into a source-built Android
product with a shared TontOS experience, explicit device profiles, Rust
host-side build/install orchestration, and physical-device acceptance. The
owner approved AOSP/Cuttlefish as a non-flashable prototype and deferred the
production ROM base until a physical pilot device can be audited. The owner
also chose to investigate running Cuttlefish directly on the local CachyOS
host. Cuttlefish can validate shared Android product behavior, but physical-device build,
install, recovery, and hardware support remain blocked until an exact phone is
selected and available.

## Architecture decisions

- Keep Android platform/product/kernel/device builds in the selected upstream
  build system; Rust orchestrates detection, validation, build execution, and
  artifact bookkeeping.
- Keep the Android source checkout separate from this repository's Rust
  workspace. Store pinned source/patch metadata here, not a second copy of the
  whole upstream tree.
- Use versioned device profiles as the contract between hardware support,
  Rust orchestration, installation, and validation.
- Use a local modular CLI with process/transport adapters; do not split into
  services or a plugin system until concrete reuse requires it.
- Build and assemble on the host PC after identifying the target. The phone
  does not compile AOSP during installation.
- Require explicit target identity, artifact integrity, user confirmation,
  and device-specific recovery evidence before any flash operation.
- Keep the first support promise limited to a pilot device. Add additional
  devices only after complete build, install, boot, feature, and recovery
  evidence.
- Keep OTA scope behind a device-specific feasibility/design gate; do not
  claim a generic OTA or rollback path.
- Use `aosp_cf_x86_64_only_phone` as the initial Cuttlefish product. This
  selects the prototype target, not the production Android release or phone
  ROM base.

## Phases and checkpoints

### Phase 0: Resolve prototype base, emulator target, and build feasibility

The owner approved AOSP/Cuttlefish as a non-flashable prototype and selected
`aosp_cf_x86_64_only_phone` as the virtual target. The owner chose to attempt
Cuttlefish directly on local CachyOS. KVM, `/dev/net/tun`, `/dev/vhost-net`,
and `/dev/vhost-vsock` are present, but Cuttlefish host tools are absent and
the official host setup documents Debian packages. The running kernel is
`7.2.9-1-cachyos`; `/dev/kvm` opens with KVM API 12 and `/dev/vhost-net`,
`/dev/vhost-vsock`, and `/dev/net/tun` open read/write. The modules are
resolvable. The AUR host-support package was adapted to current upstream
commit `b2977389c5930d8b06cb8708ac95866e45fc6cd5` and installed; its systemd
network service remains disabled/inactive. It does not provide Cuttlefish
commands or images. CI reports build `16373615` as latest known-good, but its
artifact listing requires authenticated browser/RPC access, so matching
archives have not been obtained. Guest networking and Cuttlefish startup
remain unverified. The SDK has an emulator binary, but it is not the selected
runtime. The host also has approximately 288 GB free disk and 15 GiB RAM,
below the official AOSP source-build requirements of 400 GB and 64 GB. Keep
local emulation and source compilation as separate host decisions.

**Checkpoint:** approved prototype scope (complete), verified Cuttlefish
runtime on the selected host, and feasible source-build host. The production
base is deferred; this checkpoint does not authorize or claim support for
phone flashing.

### Phase 1: Reproducible Android base and shared product

Pin the upstream manifest; build an unmodified reference target; establish a
TontOS product target; integrate the first-party shared experience; and verify
the result in Cuttlefish/emulator.

**Checkpoint:** clean source sync and repeatable emulator boot, with a recorded
source revision and artifact manifest.

### Phase 2: Physical device support (blocked until a phone is available)

After the owner selects and provides an exact physical phone, define and
validate its profile; audit device tree, kernel, VINTF, vendor interface, and
permitted OEM components; then build and verify the target.

**Checkpoint:** device/product build succeeds, static compatibility checks
pass, and all binary inputs have provenance.

### Phase 3: Rust host orchestration

Implement read-only target discovery and validation, then dry-run and real
build orchestration using argument-safe subprocess boundaries. Produce a
verifiable build manifest.

**Checkpoint:** fake-device tests cover malformed output and process failures;
the pilot target builds from a clean host checkout through the CLI.

### Phase 4: Safe installation and recovery

Implement inspect/plan/confirm/apply stages; add device-specific install steps;
and test failure, disconnect, and recovery behavior on the pilot device.

**Checkpoint:** physical-device installation, first boot, and documented
recovery all pass. No emulator-only evidence can satisfy this gate.

### Phase 5: Core feature integration

Validate shared UX and hardware features separately. Record per-feature
capability status; unsupported hardware remains visible and safely unavailable.
Measure battery/performance before making optimization claims.

**Checkpoint:** pilot feature matrix contains results for every advertised
feature, including permissions, errors, unsupported cases, and hardware
conditions.

### Phase 6: Release and expand compatibility

Harden CI and artifact provenance, define signing/update responsibilities,
publish a support matrix and install/recovery documentation, and promote only
targets with complete evidence.

**Checkpoint:** release candidate maps to immutable source/profile inputs,
verified artifacts, physical-device results, and known limitations.

## Dependency and parallel work

Dependency chain:

```text
prototype-base decision + virtual target + feasible build host
  → shared Android product + Cuttlefish tests
  → physical phone selection and device-source audit
  → device profile and pilot build
  → Rust build orchestration
  → safe physical installation/recovery
  → core hardware validation
  → release evidence and support status
```

After the upstream/product and profile contracts are approved, emulator UI
integration and profile parser tests may proceed independently. Physical
hardware feature validation can be split by subsystem only after a phone is
selected and boots reliably. Flashing/recovery work is strictly sequential
and must not be parallelized across an unverified device.

## Verification commands

Existing repository checks:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
bash tests/safety/test_packaging_safety.sh
```

Android source build command shape (exact product and branch are unresolved):

```sh
source build/envsetup.sh
lunch <product>-<release_config>-userdebug
m
```

Future Rust CLI acceptance entry points:

```sh
tontosctl device detect --format json
tontosctl build --device <vendor/codename> --variant userdebug --dry-run
tontosctl artifacts verify --manifest <path>
tontosctl install plan --device <vendor/codename> --artifacts <manifest> --dry-run
```

The `tontosctl` commands are specifications, not current binaries. Hardware
test commands and CI targets are selected after the upstream and pilot device
are known.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| No exact pilot model or variant | Cannot establish a real device target | Make model/SKU selection an explicit phase-0 owner decision |
| Missing/incompatible OEM firmware, kernel source, or drivers | Build or key phone features fail | Audit inputs before implementation; reject unsupported profile instead of guessing |
| Upstream base cadence or downstream patching burden | Security fixes become unsustainable | Compare maintenance model and record update ownership before selection |
| Bootloader unlock erases user data or is unavailable | Data loss or device cannot install | Confirm unlock path, stock recovery image, and user consent before any flash |
| Partition/AVB/slot behavior differs by device | Boot failure or untrusted images | Encode device-specific install contracts and test recovery on physical hardware |
| Android builds exceed available disk/RAM/time | Unreliable local or CI builds | Measure a clean build early and document minimum host requirements |
| Proprietary component licensing/provenance is unclear | Redistribution or compliance issue | Track source/hash/license per blob and separate non-redistributable inputs |
| Universal support claims exceed evidence | Users may brick devices or lose core functions | Publish per-device status and feature matrix; no profile, no support claim |

## Open decisions

1. Verify the selected experimental Cuttlefish host path on CachyOS; no
   Cuttlefish host tools are installed yet.
2. Remote/upgraded source-build host and measured minimum resources.
3. Android branch/release to target; select it with the source manifest, not
   by silently following a moving branch.
4. Production base (AOSP versus a device-supported downstream) after choosing
   and auditing an exact pilot phone.
5. Exact pilot device model, SKU, region, and recoverability.
6. Which OEM binaries can be redistributed versus user-extracted.
7. Release signing ownership, security update cadence, and OTA scope.

Resolve the local emulator and source-build host paths before starting Phase 1.
Resolve the exact physical-device and OEM-input decisions before Phase 2.
Resolve release signing, security cadence, and OTA scope before public release
or adding more devices.
