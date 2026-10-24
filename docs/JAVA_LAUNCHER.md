# TontOS Launcher Architecture & Java Component Reference

Maintainer: FalseThinks <paproken@protonmail.com>

## Overview

The TontOS Launcher (`launcher_apk/` and `vendor/tontos/apps/TontOSLauncher/`) is the first-party system shell for TontOS. It replaces conventional distraction-heavy Android launchers with a minimalist, high-contrast monospace environment engineered for digital detox, battery longevity, and low visual stimulation.

```
                           +------------------------+
                           |      MainActivity      | (Home Screen & Drawer)
                           +-----------+------------+
                                       |
                +----------------------+----------------------+
                |                                             |
   +------------v------------+                   +------------v------------+
   |   SettingsActivity      |                   |   SetupWizardActivity   |
   | (5 Categorized Sections)|                   | (First-Boot Onboarding) |
   +------------+------------+                   +-------------------------+
                |
    +-----------+-----------------------------------------------+
    |                           |                               |
+---v----------------+  +-------v--------------+  +-------------v--------------+
| GrayscaleManager   |  | FrictionDialog       |  | AppNetworkFirewall         |
| (KMS Monochromacy) |  | (Mindful Pause)      |  | (AppOps / iptables)        |
+--------------------+  +----------------------+  +----------------------------+
    |                           |                               |
+---v----------------+  +-------v--------------+  +-------------v--------------+
| NotificationDigest |  | AppPrivacyGuard      |  | UltraShadeDialog           |
| (12:00/18:00 DND)  |  | (Sensor Isolation)   |  | (Zero-Distraction Shade)   |
+--------------------+  +----------------------+  +----------------------------+
```

---

## Privileged Platform Integration

When built as part of the native AOSP ROM (`vendor/tontos/apps/TontOSLauncher/Android.bp`), the launcher runs as a privileged system application:

- **Location:** `/system_ext/priv-app/TontOSLauncher/TontOSLauncher.apk`
- **Soong Blueprint Properties:**
  - `platform_apis: true`: Grants access to internal Android system APIs (`@hide`).
  - `certificate: "platform"`: Signed with the ROM's platform release key.
  - `privileged: true`: Placed in privileged permissions domain.
  - `system_ext_specific: true`: Installed into the `system_ext` partition.
- **Privileged Capabilities:**
  - Writing to `Settings.Secure` (enabling system-wide Daltonizer monochrome mode).
  - Toggling Do Not Disturb interruption filters via `NotificationManager`.
  - Managing network and background restrictions via `cmd appops`.
  - Suppressing notification shade expansion (`cmd statusbar send-disable-flag`).

---

## Zero-Allocation & Monospace UI Rules

1. **Strict Monospace Typography:**
   - 100% of UI elements (headers, app titles, clock, battery counters, status badges, and dialog text) enforce `Typeface.MONOSPACE`.
   - Visual balance is maintained through whitespace alignment and ASCII delimiters (`[ --- ]`, `>  `, `•`), avoiding heavy raster graphic assets.
2. **Allocation-Free Hot Loops:**
   - `SimpleDateFormat` date formatters are static singletons cached per instance, eliminating ~7,200 allocations per hour on idle clock ticks.
   - App drawer search pre-caches lowercased labels (`lowerLabel`) during catalog generation, allowing $O(1)$ substring matching without allocations on keystrokes.
   - Package manager labels (`ResolveInfo.loadLabel()`) are resolved once during initialization and cached, eliminating repeated Binder IPC calls during list rendering and reordering.

---

## Core Component Reference

### 1. `MainActivity.java`
- **Role:** Home screen, app launcher, single-line detox budget ticker, swipe/button drawer.
- **Single-Line Ellipsize:** The daily focus ticker format `[ 45m/2h · 62% [====...] ]` enforces `setSingleLine(true)` and `setEllipsize(TextUtils.TruncateAt.END)` to prevent layout shifts.
- **Dynamic System Bars:** Updates status bar icon tint dynamically based on theme luminance (`tokens.isLight()`) via `WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS`.

### 2. `SettingsActivity.java`
- **Role:** Central system preferences hub structured into 5 clear sections:
  1. **Display, Themes & Language:** 4 color presets, screen timeout, font scale slider (85%-130%), system daltonizer grayscale toggle, multilingual picker.
  2. **Sound & Volume:** Media, ring, alarm sliders, 5-band hardware equalizer (-10 dB to +10 dB across 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz), and scheduled notification digest.
  3. **Home Screen & All Apps:** Home app count limit (3-10 or Unlimited), mindful launch delay friction, manual priority reordering (`[^]`, `[v]`).
  4. **Connectivity & Radios:** Wi-Fi, Bluetooth, Cellular quick toggles, background sync interval batching (15m, 30m, 60m), and per-app network firewall.
  5. **Device Information & Updates:** Kernel semver display, Android version, cryptographic backup snapshot generator, and OTA update channel selector.

### 3. `GrayscaleManager.java`
- **Role:** Eliminates visual dopamine triggers by enforcing system monochromacy.
- **Dual-Layer Implementation:**
  - **Layer 1 (Platform):** Writes `accessibility_display_daltonizer_enabled = 1` and `accessibility_display_daltonizer = 0` to `Settings.Secure`.
  - **Layer 2 (Fallback):** In unprivileged environments, applies a hardware-accelerated `ColorMatrixColorFilter` with 0 saturation (`cm.setSaturation(0f)`) directly to the window decor view.

### 4. `FrictionDialog.java`
- **Role:** Implements mindful pause friction (LightOS / Blloc Ratio pattern).
- **Behavior:**
  - Prompts with a countdown timer (e.g. 5 seconds) and breathing indicator before launching non-essential applications.
  - User can click `[ CANCEL ]` to abort or wait for completion. Essential communication apps (Phone, SMS, Contacts) bypass friction by default.

### 5. `AppNetworkFirewall.java`
- **Role:** Per-app network access toggle inspired by GrapheneOS / CalyxOS.
- **Mechanism:**
  - Uses `cmd appops set <pkg> RUN_IN_BACKGROUND deny` and `ACCESS_RESTRICTED_SETTINGS deny`.
  - When root access or platform iptables is present, adds packet rejection rules by UID (`iptables -I OUTPUT -m owner --uid-owner <UID> -j REJECT`).

### 6. `NotificationDigestManager.java`
- **Role:** Batches notifications to eliminate continuous phone interruptions.
- **Modes:**
  - `IMMEDIATE`: Normal delivery.
  - `SCHEDULED_DIGEST`: Silent batching, delivered in calm digests at 12:00 and 18:00.
  - `STARRED_ONLY`: Do Not Disturb priority mode permitting alerts only from favorite contacts.

### 7. `ThemeTokens.java` & `AppEnums.java`
- **Role:** Central domain enumeration and color token palette.
- **Themes Supported:**
  - `AMOLED`: Deep pitch black (`#000000`) for zero OLED power consumption.
  - `AMBER`: Phosphor cathode-ray tube amber (`#FFB000`) for minimal blue-light eye strain.
  - `E_INK`: Ultra-crisp reflective paper (`#FFFFFF`) with pure carbon ink (`#000000`).
  - `SLATE`: Low-contrast charcoal slate (`#1E293B`) for muted desktop environments.

---

## Standalone APK Compilation

To build and sign the launcher APK without compiling the full AOSP tree:

```sh
# Requires JDK 17 and Android SDK build-tools
./bin/build_launcher_apk.sh
```

Output APK is generated at `launcher_apk/tontos-launcher.apk` signed with v2/v3 signature schemes.
