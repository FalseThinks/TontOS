# Spec: Core Feature Integration

Module ID: `core-feature-integration`
Status: Draft for owner review
Intent: [`../intent/android-platform.md`](../intent/android-platform.md)

## Objective

Deliver a coherent Android user experience and integrate TontOS customization
without substituting simulations for core phone behavior. Each feature is
implemented through Android's supported framework/HAL/device interface and
verified per device.

## Scope and architecture

Shared Android product scope:

- Home/launcher, navigation, onboarding, visual themes, localization, and
  settings.
- App install/launch, display, input, audio, Wi-Fi/network, and normal Android
  permissions/privacy controls.
- Battery status and power behavior using supported Android interfaces.

Device-dependent scope:

- Cellular voice/SMS/data, NFC/HCE, camera, sensors, Bluetooth, GPS, hardware
  keys, charging controls, and vendor-specific power features.
- A feature is reported as supported only when the device profile exposes the
  required interface and real-hardware acceptance passes.

The existing launcher APK may inform design and be reused only after its
behavior and permissions are reviewed for integration into the selected
Android product. Current in-memory telephony models and UI toggles are not
evidence of modem or hardware integration.

## Commands

Current repository checks:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
./bin/build_launcher_apk.sh
```

After Android product integration, run the selected AOSP test targets and the
hardware acceptance suite defined in the device profile. Exact target names
remain open until the base and pilot device are selected.

## Project structure

- `product/` — shared TontOS Android product configuration and system UI.
- `apps/` — first-party Android apps, subject to upstream integration choice.
- `device/<vendor>/<codename>/` — device-dependent implementation.
- `tests/android/` — framework/app tests.
- `tests/device/<codename>/` — hardware-specific test reports.

## Code style and interfaces

Prefer Android framework APIs for standard user-facing behavior. Put
vendor-specific behavior behind a narrow, capability-checked adapter. Expose
capabilities as measured states such as `SUPPORTED`, `UNAVAILABLE`,
`PERMISSION_REQUIRED`, or `NOT_TESTED`; do not confuse visible controls with
working capabilities. User-facing status must distinguish requested,
effective, and unsupported settings.

## Testing strategy

- Unit/UI tests for shared launcher, settings, onboarding, accessibility, and
  localization.
- Cuttlefish/emulator tests for framework interactions and app lifecycle.
- Real-hardware tests for input, display, audio, network, camera, sensors,
  telephony, NFC, and power where claimed.
- Battery and performance claims require reproducible baselines on hardware
  with specified workloads and thermal/battery conditions.
- Unsupported hardware features must be tested for safe and honest
  unavailable behavior.

## Boundaries

- **Always:** honor Android permission/security boundaries; distinguish
  configured from applied; keep device-only features optional and report
  limitations.
- **Ask first:** request privileged platform permissions, integrate proprietary
  services, or change default data/privacy behavior.
- **Never:** claim charging limits, emergency calling, payment integrity,
  encryption, OTA, or power savings without implementation and evidence.

## Success criteria

- Shared core UX works end to end on the pilot device.
- Every advertised hardware feature has device-specific evidence and a
  corresponding regression test where automatable.
- Unavailable features are clearly represented and cannot appear active when
  the underlying operation failed.
- Power/performance claims are measured rather than inferred from emulator
  behavior.

## Open questions

- Which first-party applications are essential for the initial ROM?
- Which features are mandatory for the first supported device versus later?
- What accessibility, privacy, and battery/performance targets define
  acceptance?
