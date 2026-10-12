# ADR-002: Native AOSP Custom Product Integration (tontos_cf_x86_64)

## Status
Accepted (Supersedes [DECISION-01](DECISION-01-upstream-base.md))

## Date
2026-10-12

## Maintainer
FalseThinks <paproken@protonmail.com>

## Context
Previously, TontOS relied on post-install debloater scripts over standard AOSP virtual builds (`aosp_cf_x86_64_only_phone`). This approach had several critical drawbacks:
1. Default packages (such as `QuickSearchBox`, `Launcher3`, `Gallery2`, `Calendar`, and Google telemetry stubs) were compiled into platform system partitions and only disabled after first boot.
2. The custom launcher lacked built-in platform certificate signing and had to be installed via post-boot ADB sideloading or ad-hoc rootfs injection scripts.
3. Seccomp filter policies in Cuttlefish caused system crashes on modern host kernels (Linux 7.2.9 glibc `madvise` code `102` / `MADV_GUARD_INSTALL`).

The owner requested that TontOS be compiled natively from source as a pure vanilla ROM from ground zero, excluding all unwanted packages at build time.

## Decision
1. **Dedicated Vendor Hierarchy:** Register a first-party vendor tree at `vendor/tontos/` inside the AOSP source tree (`~/aosp/tontos/`).
2. **Custom Target Product:** Define `tontos_cf_x86_64-bp4a-userdebug` via `AndroidProducts.mk`, inheriting base Cuttlefish hardware definitions while stripping unnecessary stock packages.
3. **Build-Time Debloating:** Use `PRODUCT_PACKAGES` overrides in `vendor/tontos/products/tontos_cf_x86_64.mk` to eliminate `QuickSearchBox`, `Launcher3`, `DeskClock`, and related bloatware before partition images are generated.
4. **Privileged In-Tree Launcher:** Build `TontOSLauncher` directly through Soong (`Android.bp`) with `privileged: true`, `certificate: "platform"`, and install target `/system_ext/priv-app/TontOSLauncher/`.
5. **Seccomp Hotfix:** Update crosvm seccomp policy files (`gpu_device.policy`, `gpu_common.policy`) to allow `madvise: 1` syscalls required by modern glibc versions.

## Alternatives Considered

### Post-Boot Shell Debloating
- **Pros:** Does not require modifying AOSP makefiles.
- **Cons:** Bloatware packages remain on disk; factory resets restore disabled bloatware; violates the requirement of a clean vanilla distribution from ground zero.
- **Rejected:** The product must be clean by design, not merely patched post-install.

### Generic System Image (GSI) Patching
- **Pros:** Can run across disparate Treble-compliant hardware.
- **Cons:** Flaky emulator support, lack of vendor-specific hardware controls, complex AVB signing verification.
- **Rejected:** Native target definition provides full control over build artifacts and bootloader compatibility.

## Consequences
- The compiled AOSP images (`super.img`, `system.img`, `system_ext.img`) contain zero stock search boxes or duplicate launchers.
- `TontOSLauncher` acquires platform permissions (`WRITE_SECURE_SETTINGS`, `STATUS_BAR`) natively upon initial boot.
- Builds are invoked via `lunch tontos_cf_x86_64-bp4a-userdebug` and assembled deterministically using Soong/Ninja.
