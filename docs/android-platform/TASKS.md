# TontOS Android Platform Tasks

Status: Approved task breakdown; Phase 0 is in progress.
Specification: [`README.md`](README.md)
Plan: [`PLAN.md`](PLAN.md)

Task dependencies are explicit. A task with no dependency is independent.
Files listed are expected change areas and should remain within the task's
focused scope.

## Phase 0: Prototype base, emulator, and build feasibility

### T01 — Recording the approved prototype base

- **Description:** Record the approved AOSP/Cuttlefish prototype scope and
  explicitly defer the production ROM base until a physical pilot is audited.
- **Acceptance:** AOSP/Cuttlefish is identified as non-flashable prototype
  only; production base/release and physical-device support are not implied.
- **Verification:** Decision record links to official AOSP/Cuttlefish guidance
  and states the limits of emulator evidence.
- **Dependencies:** None.
- **Files:** `docs/android-platform/` decision record.
- **Scope:** S; decision recorded.

### T02 — Selecting the initial Cuttlefish target

- **Description:** Use AOSP's documented x86_64 phone-form-factor Cuttlefish
  product for the non-flashable prototype.
- **Acceptance:** `aosp_cf_x86_64_only_phone` is recorded as the virtual target
  and labeled emulator-only, not a phone/device-support target.
- **Verification:** Confirm KVM availability, establish an experimentally
  viable local host path on CachyOS, then boot the target and record the image
  revision and host configuration. Basic KVM API and vhost/TUN open checks now
  pass after reboot, but network configuration and runtime startup remain
  unverified. Host support package is installed from current upstream commit
  `b2977389c5930d8b06cb8708ac95866e45fc6cd5`, but `launch_cvd` and the matching
  image/host archives are still missing. The host service remains disabled.
  CI identifies build `16373615` as latest known-good; artifact retrieval
  requires authenticated browser/RPC access.
- **Dependencies:** T01.
- **Files:** `docs/android-platform/` virtual-target decision record.
- **Scope:** S; target selected, CachyOS runtime verification pending.

### T03 — Selecting a feasible Android build host

- **Description:** Compare local, upgraded-local, and remote build
  environments against upstream requirements for the selected reference
  target. This is the source-compilation host decision, separate from where
  the emulator runs.
- **Acceptance:** Select a host with a measured path to meet the upstream build
  requirements; document available disk, RAM, CPU, OS, and cost/permission
  constraints before source sync.
- **Verification:** Check the chosen environment's resources and run its
  documented preflight. Current local snapshot: x86_64, 16 CPU threads,
  15 GiB RAM, about 288 GB free, `/dev/kvm` present; OS is CachyOS (Arch-based).
- **Dependencies:** T01.
- **Files:** `docs/android-platform/` host requirements; build environment
  configuration.
- **Scope:** M.

### T04 — Selecting and auditing a physical pilot phone

- **Description:** When a physical phone is available, select its exact
  model/SKU/region and audit device tree, kernel sources, VINTF, vendor
  interface, firmware/blobs, licensing/provenance, bootloader, partitions, and
  recovery options.
- **Acceptance:** A physical target is available for controlled testing and
  every required input has a source/version/provenance record; missing or
  unusable inputs produce an explicit no-go.
- **Verification:** Reproduce the inventory from the device and OEM/upstream
  references; verify the stock recovery route before any unlock.
- **Dependencies:** T01.
- **Files:** `docs/android-platform/` audit report; proposed device profile.
- **Scope:** M; blocked until a physical target is selected and available.

### Checkpoint: Emulator prototype feasibility

- **Acceptance:** T01 prototype scope is approved; T02's target boots on a
  verified runtime path; T03's source-build host is feasible and measured.
  T04 is not required for the emulator prototype but blocks all
  physical-device support/flashing tasks.

## Phase 1: Reproducible Android base

### T05 — Pinning and syncing Android source

- **Description:** Add a minimal, reviewable source manifest and setup
  documentation for the selected upstream; keep the source checkout outside
  versioned repository content.
- **Acceptance:** Source revision and manifest are immutable/reviewable; clean
  sync recreates the same source revision without storing credentials.
- **Verification:** Run the documented sync on a clean workspace and verify
  manifest revision hashes.
- **Dependencies:** T01, T03.
- **Files:** `platform/manifests/`; `docs/android-platform/`.
- **Scope:** M.

### T06 — Building an unmodified upstream reference target

- **Description:** Build and boot the selected upstream's supported reference
  target before applying TontOS changes.
- **Acceptance:** Reference build completes and reaches Android home/framework
  readiness; exact source, product, variant, and logs are recorded.
- **Verification:** Run the upstream target build and Cuttlefish/emulator boot
  smoke checks from a clean workspace.
- **Dependencies:** T02, T05.
- **Files:** `docs/android-platform/` reference-build record; build scripts only
  if required.
- **Scope:** M.

### T07 — Defining the shared TontOS Android product

- **Description:** Create the smallest upstream-native product configuration
  for shared TontOS branding, defaults, and first-party app inclusion.
- **Acceptance:** Product target builds without device-specific kernel/vendor
  assumptions and does not alter unrelated upstream targets.
- **Verification:** Build the product target and inspect generated product
  metadata/artifact manifest.
- **Dependencies:** T06.
- **Files:** `product/tontos/` and upstream product registration.
- **Scope:** M.

### T08 — Integrating the TontOS home experience

- **Description:** Adapt/reuse the current launcher as a first-party Android
  home experience within the selected product.
- **Acceptance:** Home activity is discoverable, selectable as default home,
  survives reboot, and reports unsupported device operations honestly.
- **Verification:** Install/boot the product in Cuttlefish and run launcher
  navigation, onboarding, settings, and restart smoke tests.
- **Dependencies:** T07.
- **Files:** `product/tontos/`; `launcher_apk/` or replacement Android app
  module; focused UI tests.
- **Scope:** M.

### Checkpoint: Shared product

- **Acceptance:** Clean build and emulator boot pass; first-party home is
  usable; source and artifact provenance are retained.

## Phase 2: Pilot device profile and build

### T09 — Defining the versioned device profile schema

- **Description:** Convert the proposed profile fields into a strict,
  versioned machine-readable schema.
- **Acceptance:** Required device identity, product, bootloader, partitions,
  kernel/vendor sources, VINTF, firmware provenance, and support status are
  validated; unknown schema versions fail clearly.
- **Verification:** Positive, missing-field, invalid-enum, and unsupported
  schema fixtures pass/fail as expected.
- **Dependencies:** T04.
- **Files:** `devices/schema/`; `tests/device/profile/`.
- **Scope:** M.

### T10 — Parsing and validating device profiles

- **Description:** Implement typed profile loading/validation in Rust without
  adding speculative abstractions.
- **Acceptance:** Valid profile maps to a typed configuration; malformed or
  ambiguous profiles return stable actionable errors.
- **Verification:** `cargo test -p tont-device-profile` (or the owning crate's
  focused tests) covers valid and invalid fixtures.
- **Dependencies:** T09.
- **Files:** `crates/tont-device-profile/` or initial CLI crate; profile tests.
- **Scope:** M.

### T11 — Pinning the pilot device product configuration

- **Description:** Add the minimal product registration, device makefile, and
  board configuration for the audited pilot target.
- **Acceptance:** The device product resolves to the exact codename/SKU and
  pinned source/profile inputs; no mutable source branch is selected silently.
- **Verification:** Run upstream product-configuration parsing and validate
  the selected product/codename before compiling images.
- **Dependencies:** T04, T05, T09.
- **Files:** `AndroidProducts.mk`, `device.mk`, `BoardConfig.mk`, and the
  pilot's `devices/<vendor>/<codename>.yaml`.
- **Scope:** M.

### T12 — Establishing OEM component provenance

- **Description:** Package or reference only pilot firmware/vendor components
  and kernel build inputs allowed by the documented licensing and distribution
  policy.
- **Acceptance:** Every required binary has origin, version, hash, license
  status, extraction method, and redistribution status; missing inputs block
  the build clearly.
- **Verification:** Recompute hashes and run a profile/blob completeness check
  from a clean input directory.
- **Dependencies:** T04, T09.
- **Files:** Pilot vendor/kernel source manifest; device profile; provenance
  policy.
- **Scope:** M.

### T13 — Building the pilot Android product

- **Description:** Assemble the shared TontOS product with the pilot device
  configuration and permitted vendor inputs.
- **Acceptance:** Clean build emits the expected device artifacts and a build
  manifest; no Android/host architecture mismatch is hidden.
- **Verification:** Run the exact upstream product build, inspect artifact
  types, sizes, hashes, and build logs.
- **Dependencies:** T07, T10, T11, T12.
- **Files:** Product/device build configuration; build verification record.
- **Scope:** M.

### T14 — Checking pilot VINTF and static compatibility

- **Description:** Validate framework/device manifests, compatibility
  matrices, partition assumptions, and AVB inputs for the pilot build.
- **Acceptance:** All required checks pass or produce an explicit documented
  blocker; no warning is converted to success silently.
- **Verification:** Run the selected branch's VINTF/compatibility and image
  verification tools against the built artifacts.
- **Dependencies:** T13.
- **Files:** Device VINTF/configuration; build checks.
- **Scope:** M.

### Checkpoint: Emulator product readiness

- **Acceptance:** Profile tests, provenance, product build, and compatibility
  checks pass for the virtual target. This checkpoint does not establish
  physical-device compatibility or authorize flashing.

## Phase 3: Rust build orchestrator

### T15 — Defining CLI and error contracts

- **Description:** Define command grammar, versioned JSON output, stable error
  codes, logging/redaction, and dry-run guarantees before implementation.
- **Acceptance:** Contract covers detect, inspect, build, artifact verify, and
  install-plan commands; unsupported/ambiguous outcomes are distinct.
- **Verification:** Contract fixtures parse and demonstrate representative
  success, unsupported, and failure responses.
- **Dependencies:** T09, T14.
- **Files:** `docs/android-platform/` CLI contract; CLI crate documentation.
- **Scope:** S.

### T16 — Creating the Rust CLI skeleton

- **Description:** Implement the CLI entry point and typed command/result
  routing using existing workspace conventions.
- **Acceptance:** Commands return explicit structured outcomes and no command
  reports success for unimplemented work.
- **Verification:** `cargo fmt --all -- --check`; `cargo test -p tont-romctl`;
  `cargo clippy -p tont-romctl --all-targets -- -D warnings`.
- **Dependencies:** T15.
- **Files:** `crates/tont-romctl/`; workspace manifest.
- **Scope:** M.

### T17 — Detecting connected devices read-only

- **Description:** Add `adb`/`fastboot` detection and profile matching behind a
  fakeable transport boundary.
- **Acceptance:** Detect is read-only; zero, multiple, malformed, unknown, or
  unsupported devices never select a profile implicitly.
- **Verification:** Fake-command tests cover output parsing, timeouts, device
  ambiguity, and unsupported identities.
- **Dependencies:** T10, T16.
- **Files:** `crates/tont-romctl/src/device/`; transport tests.
- **Scope:** M.

### T18 — Planning Android builds without mutation

- **Description:** Generate a complete dry-run build plan from the selected
  device profile and upstream manifest.
- **Acceptance:** Plan records source revisions, tools, product/variant,
  required inputs, outputs, and commands; dry-run performs no sync/build/write.
- **Verification:** Golden tests compare deterministic plans; fake executables
  prove no mutating command runs.
- **Dependencies:** T13, T16, T17.
- **Files:** `crates/tont-romctl/src/build/`; plan tests.
- **Scope:** M.

### T19 — Running builds and recording artifact manifests

- **Description:** Execute the reviewed build plan with bounded process
  handling and produce integrity metadata.
- **Acceptance:** Nonzero exit, timeout, missing tools, or missing artifacts
  fail explicitly; manifest records exact inputs and artifact SHA-256.
- **Verification:** Fake-process integration tests and one clean pilot build
  through the CLI.
- **Dependencies:** T18.
- **Files:** `crates/tont-romctl/src/build/`; manifest tests.
- **Scope:** M.

### Checkpoint: Build orchestrator

- **Acceptance:** Device detection and dry-run are safe, the pilot build runs
  through the CLI, and output provenance verifies.

## Phase 4: Safe install and recovery

### T20 — Creating immutable installation plans

- **Description:** Generate a per-device plan showing target identity,
  artifacts, partitions, user-data effects, prerequisites, and recovery path.
- **Acceptance:** Plan binds device identity and artifact hashes; unsupported
  or missing recovery data blocks apply.
- **Verification:** Unit tests cover mismatch, changed artifacts, missing
  recovery, and dry-run without writes.
- **Dependencies:** T19.
- **Files:** `crates/tont-romctl/src/install/`; install-plan tests.
- **Scope:** M.

### T21 — Requiring explicit install confirmation

- **Description:** Add a confirmation step bound to the exact plan and
  revalidate device state immediately before any write.
- **Acceptance:** No bootloader unlock, erase, or flash occurs without explicit
  confirmation; stale plans are rejected.
- **Verification:** Fake transport asserts zero write commands for absent,
  cancelled, or stale confirmation.
- **Dependencies:** T20.
- **Files:** Install state machine and security tests.
- **Scope:** M.

### T22 — Applying the pilot device installation

- **Description:** Implement only the reviewed pilot-specific installation
  actions and verified artifact types.
- **Acceptance:** Wrong device/profile, unverified hash, disallowed partition,
  or failed precondition stops before writing.
- **Verification:** Run on a recoverable pilot device with serial logs and
  preserve the original stock recovery route.
- **Dependencies:** T21, T14.
- **Files:** `crates/tont-romctl/src/install/`; pilot install profile/tests.
- **Scope:** M for the single pilot install path; split by stage if the
  implementation exceeds five files.

### T23 — Testing interrupted install and recovery

- **Description:** Exercise disconnect, process crash, failed boot, recovery
  entry, and restore/reflash procedures for the pilot.
- **Acceptance:** Each failure has an observed recovery procedure; no
  unverified rollback claim is made.
- **Verification:** Controlled failure-injection and recovery on physical
  hardware; owner reviews recovery evidence.
- **Dependencies:** T22.
- **Files:** `tests/install/`; pilot recovery guide.
- **Scope:** M.

### Checkpoint: Safe physical installation

- **Acceptance:** Pilot install and recovery pass on hardware with exact
  artifact hashes and retained logs.

## Phase 5: Core feature integration

### T24 — Establishing the per-device feature matrix

- **Description:** Define mandatory versus optional capabilities and exact
  observations for each supported hardware feature.
- **Acceptance:** Matrix includes status values passed/failed/skipped/not
  tested and a source of evidence for each assertion.
- **Verification:** Validate matrix schema with examples for both supported
  and unavailable hardware.
- **Dependencies:** T14, T23.
- **Files:** `tests/device/schema/`; `docs/android-platform/` feature matrix.
- **Scope:** S.

### T25 — Validating display, touch, and hardware input

- **Description:** Verify first-boot usability, display rendering, touch, and
  hardware-key behavior on the pilot.
- **Acceptance:** Mandatory display/input functions have repeatable results;
  absent interfaces are reported as unsupported.
- **Verification:** Run automated smoke tests plus documented physical checks.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; only observed defects in owned product
  components.
- **Scope:** M.

### T26 — Validating Wi-Fi and network connectivity

- **Description:** Verify Wi-Fi connection, persistence, DNS, and network
  access using Android device interfaces.
- **Acceptance:** Advertised connectivity passes or is explicitly marked
  unsupported/not tested.
- **Verification:** Record physical connectivity results, permission behavior,
  and disconnect recovery.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; focused product fixes for evidenced
  defects only.
- **Scope:** S.

### T27 — Validating audio input and output

- **Description:** Verify speaker, microphone, audio routing, and volume
  controls on the pilot.
- **Acceptance:** Audio paths pass independently; missing hardware paths are
  not implied by telephony tests.
- **Verification:** Run controlled playback, recording, route, and volume
  checks on the physical device.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; focused audio configuration only for
  evidenced defects.
- **Scope:** S.

### T28 — Validating camera behavior

- **Description:** Verify camera discovery, preview, capture, and permission
  behavior on the pilot.
- **Acceptance:** Camera is advertised only if functional end to end;
  permission denial and unavailable hardware are handled visibly.
- **Verification:** Run camera and permission checks on physical hardware.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; camera configuration only for an
  evidenced defect.
- **Scope:** S.

### T29 — Validating sensors

- **Description:** Verify each present accelerometer, proximity, light,
  rotation, or other sensor exposed by the pilot.
- **Acceptance:** Each sensor is individually classified and reports expected
  values or an explicit unavailable state.
- **Verification:** Compare Android sensor output with controlled physical
  movement, light, and proximity tests.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; sensor configuration only for an
  evidenced defect.
- **Scope:** S.

### T30 — Validating Bluetooth and location

- **Description:** Verify Bluetooth discovery/connection and location
  providers where the pilot includes the respective hardware/services.
- **Acceptance:** Each capability is tested independently; unavailable
  services are not presented as active.
- **Verification:** Run controlled pairing and location checks with permission
  denied and radio-off cases.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; focused configuration for evidenced
  defects.
- **Scope:** S.

### T31 — Validating cellular calls and SMS

- **Description:** Integrate real modem/RIL behavior only if pilot hardware and
  upstream support it; replace no telephony model with claims unsupported by
  a real modem.
- **Acceptance:** Calls and SMS are tested on hardware; emergency-call
  limitations are documented; unsupported paths are not advertised.
- **Verification:** Run controlled SIM/call/SMS tests and safety-review
  emergency behavior without placing unapproved emergency calls.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; telephony integration only if the
  upstream/device contract supports it.
- **Scope:** M.

### T32 — Validating mobile data

- **Description:** Verify mobile data separately from Wi-Fi and voice/SMS on
  the pilot modem.
- **Acceptance:** Data registration, connectivity, and network-loss recovery
  pass or are explicitly marked unsupported.
- **Verification:** Run controlled SIM/network tests and retain results.
- **Dependencies:** T24.
- **Files:** `tests/device/<codename>/`; modem configuration only for evidenced
  defects.
- **Scope:** S.

### T33 — Validating privacy and platform security

- **Description:** Review permissions, app isolation, boot-state reporting,
  debug-build exposure, and secure defaults in the integrated product.
- **Acceptance:** Security claims map to upstream/device evidence; unknown
  attestation or boot states remain unknown rather than inferred.
- **Verification:** Run Android security tests for the selected branch and
  inspect production build settings.
- **Dependencies:** T08, T23.
- **Files:** Product security configuration; security test records.
- **Scope:** M.

### T34 — Measuring battery and performance baselines

- **Description:** Measure idle and representative workloads on the pilot
  before changing power/performance behavior.
- **Acceptance:** Report conditions, sample size, thermal/battery state,
  baseline values, and limitations; optimization claims require a measurable
  improvement without feature regressions.
- **Verification:** Repeat measurements on the same device/build against
  owner-approved thresholds.
- **Dependencies:** T25, T26, T27, T33.
- **Files:** `tests/performance/`; measurement report; targeted implementation
  files only after evidence.
- **Scope:** M.

### T35 — Showing truthful per-device capability status

- **Description:** Surface supported, unavailable, permission-required, and
  untested states in the user-facing product.
- **Acceptance:** UI reflects actual operation results and profile evidence;
  failed operations cannot leave controls appearing active.
- **Verification:** UI/integration tests cover success, permission,
  unsupported, and runtime-failure cases.
- **Dependencies:** T25, T26, T27, T28, T29, T30, T31, T32, T33.
- **Files:** Product settings/UI; focused corresponding tests.
- **Scope:** M.

### T36 — Deciding initial OTA and update scope

- **Description:** Evaluate pilot slots, boot control, AVB, update_engine,
  signing, server operations, and rollback behavior; decide whether updates are
  in the first release.
- **Acceptance:** Decision record defines a supportable update design or
  explicitly defers OTA; no generic OTA claim is made.
- **Verification:** Match the decision against device-profile evidence and
  official upstream implementation requirements.
- **Dependencies:** T14, T23, T33.
- **Files:** `docs/android-platform/` update decision record.
- **Scope:** S.

### Checkpoint: Pilot feature completeness

- **Acceptance:** Every advertised feature has hardware evidence and truthful
  status; battery/performance claims have repeatable measurements.

## Phase 6: CI, release, and support expansion

### T37 — Automating source and product quality gates

- **Description:** Add CI for Rust contracts, profile validation, Android
  product builds, emulator tests, and artifact manifests within measured
  runner constraints.
- **Acceptance:** Pull requests fail on required quality/build checks; test
  skips include reasons and cannot satisfy physical-hardware release gates.
- **Verification:** Exercise a passing and intentionally failing sample
  change; verify required check names and uploaded artifacts.
- **Dependencies:** T08, T14, T19, T24.
- **Files:** `.github/workflows/`; CI scripts and test config.
- **Scope:** M.

### T38 — Establishing release signing and provenance

- **Description:** Define signing-key ownership/storage, release build variant,
  source/profile provenance, artifact hashes, and publishing permissions.
- **Acceptance:** Secrets never enter source/artifacts/logs; every release
  artifact has verifiable source, profile, and signature/hash metadata.
- **Verification:** Perform a non-production signing drill and independent
  artifact verification.
- **Dependencies:** T23, T33, T36, T37.
- **Files:** Release scripts/workflow; signing policy documentation.
- **Scope:** M.

### T39 — Publishing installation and support documentation

- **Description:** Document exact supported device variants, prerequisites,
  bootloader/data-loss warnings, installation, recovery, known issues, and
  update/security support.
- **Acceptance:** Instructions match a successful clean installation; unsupported
  models and untested features are clearly excluded.
- **Verification:** A reviewer follows the procedure from a clean host and
  reaches the same supported state.
- **Dependencies:** T23, T35, T36, T38.
- **Files:** `README.md`; `docs/android-platform/`; device support matrix.
- **Scope:** M.

### T40 — Promoting the pilot to supported status

- **Description:** Review all release evidence and decide whether the pilot
  meets the project's support bar.
- **Acceptance:** Support status is approved only when install, boot, recovery,
  mandatory feature, security, and release criteria are all complete.
- **Verification:** Release checklist links every criterion to test output,
  hardware report, profile, and immutable artifact manifest.
- **Dependencies:** T25, T26, T27, T28, T29, T30, T31, T32, T33, T34, T35,
  T36, T39.
- **Files:** `docs/android-platform/` release checklist and support matrix.
- **Scope:** S.

### T41 — Adding another validated device

- **Description:** Repeat the device audit/profile/build/install/feature/release
  path for the next candidate phone without weakening the pilot's criteria.
- **Acceptance:** New profile passes the same support gate; shared product
  changes remain compatible with existing supported devices.
- **Verification:** Run full device-specific acceptance and regression-test
  previously supported profiles.
- **Dependencies:** T40.
- **Files:** New `device/`, `devices/`, and `tests/device/` entries.
- **Scope:** M per new device; create focused tasks per candidate, and split
  subsystems exceeding five files.

### Checkpoint: First supported release

- **Acceptance:** T35 passes; published release has device-specific evidence,
  signed/verified artifacts, recovery instructions, and explicit support
  boundaries.

## Initial stop conditions

- Do not start product/device implementation until T01–T04 pass the feasibility
  checkpoint.
- Do not flash until T20–T21 and the pilot recovery route are reviewed.
- Do not promote a device based on compilation or emulator results alone.
- Stop a device bring-up if required firmware, kernel support, lawful
  component provenance, or a recovery route cannot be established.
