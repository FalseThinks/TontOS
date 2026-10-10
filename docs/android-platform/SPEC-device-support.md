# Spec: Device Support

Module ID: `device-support`
Status: Draft for owner review
Intent: [`../intent/android-platform.md`](../intent/android-platform.md)

## Objective

Describe each supported phone's boot, partition, kernel, vendor interface, and
hardware requirements in a reviewable device profile. Compatibility is
explicit per profile; there is no universal-device claim.

## Tech stack and architecture

- Android device/product makefiles, device tree, kernel build configuration,
  VINTF manifests/matrices, and OEM component metadata as required by the
  selected upstream.
- Rust tooling consumes a versioned profile schema; it validates rather than
  invents device support.
- OEM blobs are separate inputs with source, version, hash, license/provenance,
  and redistribution status recorded.

## Contract

Each device profile must record at minimum:

```yaml
schema_version: 1
device:
  vendor: required
  codename: required
  product: required
  support_status: experimental # experimental | supported | retired
bootloader:
  unlock_supported: required
  unlock_data_wipe: required
partitions:
  layout_revision: required
  slots: required
components:
  kernel_source: required
  vendor_image_source: required
  firmware_provenance: required
interfaces:
  vintf_manifest: required
validation:
  last_tested_build: optional
  hardware_test_report: optional
```

The schema is a proposal. Required fields and enum values are finalized in
implementation task 1 before a device profile is accepted.

## Commands

Current repository checks:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```

Per-device Android build entry point:

```sh
source build/envsetup.sh
lunch <device-product>-<release_config>-userdebug
m
```

## Project structure

- `device/<vendor>/<codename>/` — device configuration and build files.
- `kernel/<vendor>/<kernel-family>/` — source references or separately synced
  kernel source; large upstream trees are not copied into this repository.
- `vendor/<vendor>/<codename>/` — permitted and documented prebuilt OEM
  components, or reproducible extraction metadata where redistribution is not
  allowed.
- `devices/<vendor>/<codename>.yaml` — TontOS profile consumed by Rust tools.
- `tests/device/<codename>/` — profile validation and hardware acceptance
  cases.

## Code style and interfaces

Device profiles are declarative, schema-versioned input. Unknown schema
versions, missing provenance, mismatched codenames, incompatible VINTF data,
and unsupported partition layouts are explicit errors, never guessed defaults.
Use upstream conventions inside Android device trees and Rust conventions in
the host-side profile parser.

## Testing strategy

- Unit-test profile parsing, schema version handling, required fields, and
  source/hash validation.
- Compare reported bootloader identity with the profile before build/install.
- Run Android VINTF compatibility checks for each build.
- Run hardware checks for every feature claimed by the device support status.
- A profile is not `supported` based only on successful compilation or
  emulator boot.

## Boundaries

- **Always:** identify exact vendor/codename/variant; verify licensing and
  provenance; separate open source from proprietary binaries; require positive
  evidence before marking hardware supported.
- **Ask first:** add a device profile requiring unredistributable firmware,
  alter device partition assumptions, or declare a device generally supported.
- **Never:** fabricate drivers/firmware; infer a device solely from a user-set
  string; flash a profile that does not match the connected device.

## Success criteria

- Profiles validate against one versioned schema and have traceable input
  provenance.
- A profile selects the correct Android product and device configuration.
- VINTF and kernel/vendor compatibility checks pass for the supported
  combinations.
- Experimental and supported devices are clearly distinguished.

## Open questions

- Which physical device and exact regional/SKU variant will be the pilot?
- Which OEM blobs can legally be redistributed, and which must be extracted
  from user-provided firmware?
- What is the minimum supported vendor/Android firmware baseline per device?
