# Upstream Base Decision: AOSP or LineageOS

Status: Accepted for the non-flashable emulator prototype; production base
deferred until a physical pilot device can be audited.
Date: 2026-10-06

## Context

The owner has no physical pilot phone yet and intends to begin with
emulation. The long-term intent remains a phone-installable Android platform.
The local host is x86_64 CachyOS (Arch-based) with KVM available, 16 CPU
threads, 15 GiB RAM, and about 288 GB free on the workspace filesystem at the
time of assessment. It has `adb`, but neither the Cuttlefish host tools nor the
`launch_cvd` command is installed. The Android SDK contains an emulator binary
and SDK Manager 16.0, but the emulator is not on `PATH` and is not the selected
runtime. Official Cuttlefish setup instructions install Debian packages and
configure host groups/kernel modules; this host therefore does not currently
have a documented, ready-to-run Cuttlefish setup.

After reboot, the running kernel and installed module tree both report
`7.2.9-1-cachyos`. `/dev/kvm` opens successfully and reports KVM API 12;
`/dev/vhost-net`, `/dev/vhost-vsock`, and `/dev/net/tun` also open read/write
as the current user. `modprobe` can resolve `vhost_net`, `vhost_vsock`, and
`tun`. These checks establish basic host device access, not a working
Cuttlefish runtime or configured guest networking.

The owner approved using the available AUR `cuttlefish-base-git` recipe despite
its stale metadata. Its package build initially failed because the recipe
applied an obsolete patch and tried to install two helpers removed upstream.
The local build was adjusted to the current source and installed as
`cuttlefish-base-git` version `1:r14511.20260924.b2977389c5-1`, from upstream
commit `b2977389c5930d8b06cb8708ac95866e45fc6cd5`. The resulting package is
unsigned and reports no package-signature validation. It installed host
resource scripts, a systemd unit, udev rules, module configuration, and
created the `cvdnetwork` group. The service remains disabled and inactive;
guest bridges/networking have not been started or configured. This package
does not include `launch_cvd`, `cvd`, or Android device images.

The official AOSP CI status endpoint reports build `16373615` as the latest
known-good build for `aosp_cf_x86_64_only_phone-userdebug`. The artifact viewer
itself is reachable, but its artifact list uses a Google RPC requiring caller
identity, and guessed anonymous direct-download paths return 404. Therefore
the matching `cvd-host_package.tar.gz` and image archive have not been
downloaded.

Current official AOSP build requirements specify a 64-bit x86 workstation, at
least 400 GB free disk, and 64 GB RAM. The current host does not meet the
documented disk or memory requirements for a full local AOSP build. A remote
build host/runner or upgraded local resources must be selected and tested
before source compilation. KVM availability supports virtualization capability
but does not establish that Cuttlefish is installed or ready.

## Alternatives considered

### AOSP as the production base

- **Advantages:** Direct access to the Android platform source and official
  Cuttlefish product targets; natural fit for developing shared framework and
  product changes without selecting a phone first.
- **Costs:** Device-specific trees, kernel, vendor interfaces, and firmware
  still need separate bring-up; no ready-made support for the future pilot is
  guaranteed.

### LineageOS as the production base

- **Advantages:** Can reuse existing community device configurations and
  device-specific installation knowledge when the exact model is supported.
- **Costs:** Support is exact-model-specific. The official device list warns
  that unlisted devices are unsupported and builds for another, even nearly
  identical, device cannot be used. No physical model is available to verify
  a suitable target, and the public device list alone cannot establish its
  source/build readiness.

### AOSP only as an emulator prototype, defer production base selection

- **Advantages:** Allows shared product/launcher development and framework
  tests in Cuttlefish before the owner acquires a phone.
- **Costs:** It is not a final production-base choice and could create porting
  work if the eventual physical device is best supported by a downstream
  distribution.

## Decision

The owner approved AOSP with Cuttlefish for an explicitly non-flashable
prototype and deferred choosing the production ROM base until an exact physical
device can be audited. Use the official x86_64 phone-form-factor product
`aosp_cf_x86_64_only_phone` as the initial virtual target. Do not describe this
prototype as a supported phone ROM or as evidence of physical-device support.

The owner selected a direct CachyOS Cuttlefish attempt for local emulation. This
host path is not documented by AOSP and must be verified experimentally before
it is treated as working. The separate AOSP source-build host remains open; do
not begin a full source sync/build until a host path meeting the published
resource requirements is selected.

## Consequences

- The prototype can validate shared Android framework/product behavior, but
  cannot establish device-specific kernel, vendor, radio, power, camera, or
  recovery support.
- The AOSP production base and Android release branch remain undecided until a
  physical pilot device and its support options are audited.
- AOSP source compilation is not feasible on the measured local host under the
  current published requirements.
- Cuttlefish host support files are installed, but its host package, device
  images, and commands have not been obtained. The local CachyOS path remains
  experimental until launch, adb readiness, networking, and shutdown have
  been verified.
- KVM/vhost device access now passes a basic open/API preflight, but guest
  networking and Cuttlefish startup remain unverified.

## Evidence

- AOSP build instructions require choosing a product target and show
  `aosp_cf_x86_64_only_phone`; they then build with `m`:
  <https://source.android.com/docs/setup/build/building>
- Cuttlefish is Google's canonical virtual device for current AOSP development
  and is intended for framework/product testing without physical hardware:
  <https://source.android.com/docs/devices/cuttlefish>
- Cuttlefish host setup documents building/installing Debian packages, then
  downloading matching device images and host packages:
  <https://source.android.com/docs/devices/cuttlefish/get-started>
- The Arch AUR `cuttlefish-base-git` recipe is community-maintained and marked
  out of date. Its stale patch/helper list required local adaptation to build
  against current upstream; the resulting unsigned package is installed:
  <https://aur.archlinux.org/packages/cuttlefish-base-git>
- The public CI status endpoint lists build `16373615` as latest known-good
  for the selected target:
  <https://ci.android.com/builds/branches/aosp-android-latest-release/status.json>
- Current AOSP workstation requirements are 400 GB free disk and 64 GB RAM:
  <https://source.android.com/docs/setup/start/requirements>
- LineageOS explicitly limits support to listed devices and warns against
  using builds for similar models:
  <https://wiki.lineageos.org/devices/>

## Deferred decisions

1. Verify the owner-selected local Cuttlefish path on CachyOS without assuming
   that Debian host packages or stale AUR recipes are current; host package
   support is installed, but the matching runtime/image artifacts remain
   unavailable through the current non-interactive CI path.
2. Choose a remote build host/runner or increase local resources before
   attempting a full platform build.
3. Select the AOSP release branch when the build host and source-sync task are
   ready.
4. Select an exact physical pilot phone before choosing the production base or
   starting device-profile, flashing, and hardware-support tasks.
