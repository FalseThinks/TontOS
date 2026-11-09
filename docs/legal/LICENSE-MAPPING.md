# TontOS — Comprehensive License Mapping

**Maintainer:** FalseThinks <paproken@protonmail.com>  
**Version:** 0.0.4  
**Date:** 2026-11-01  

---

## 1. Original TontOS Components

Subject to **TontOS-NCSL-1.0** (Non-Commercial Source License):

| Path | Component | Description | Copyright Holder | License |
|---|---|---|---|---|
| `crates/tont-common` | Common Core Types | Core data structures, IPC types, power enums | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-powerd` | Thermal & Power Daemon | Thermal governor & battery saver state machine | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-appd` | Process & Cgroup Daemon | Process lifecycle, cgroups v2 resource quotas | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-syncd` | Background Sync Daemon | Radio batching and opportunistic data sync | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-compositor` | Compositor Backend | Low-overhead frame buffer & damage tracker | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-shell` | Minimal Shell | Monospace CLI and system inspection tools | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-telephony` | Telephony Coordinator | Modem power governor and radio state sync | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-sensors` | Sensor Throttling Daemon | Adaptive sensor polling and tilt detection | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-input` | Touch & Input Daemon | Input filter and hardware button interception | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-settings` | Settings Store Daemon | Cryptographic settings storage and IPC bridge | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-backup` | Backup Manager | SHA-256 verifiable encrypted backups | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-init` | Low-Level Init Daemon | Zombie process reaper and signal dispatcher | FalseThinks | TontOS-NCSL-1.0 |
| `crates/tont-romctl` | ROM Control Daemon | OTA updates, A/B partition verification | FalseThinks | TontOS-NCSL-1.0 |
| `launcher_apk/` | Privileged Launcher App | Minimalist detox launcher, UltraShade, Volume HUD | FalseThinks | TontOS-NCSL-1.0 |
| `bin/` | Automation & Build Scripts | Compilation, Cuttlefish testing, test runners | FalseThinks | TontOS-NCSL-1.0 |
| `tests/` | Automated Test Suites | Rust and Java verification test suites | FalseThinks | TontOS-NCSL-1.0 |

---

## 2. Third-Party Platform & OS Dependencies

| Component | Path | Upstream Project | Upstream License | Retained in TontOS |
|---|---|---|---|---|
| AOSP Platform | `platform/`, `patches/` | Google LLC / Android Open Source Project | Apache-2.0 | Apache-2.0 headers preserved |
| Linux Kernel | Kernel tree / drivers | Linus Torvalds and Linux contributors | GPL-2.0-only with syscall note | Unchanged GPL-2.0 |
| tokio | Rust crate dependency | Tokio Contributors | MIT | Upstream MIT |
| rustix | Rust crate dependency | Dan Gohman and rustix contributors | Apache-2.0 / MIT | Upstream Apache-2.0 / MIT |
| serde / serde_json | Rust crate dependency | David Tolnay and serde contributors | Apache-2.0 / MIT | Upstream Apache-2.0 / MIT |
| thiserror | Rust crate dependency | David Tolnay | Apache-2.0 / MIT | Upstream Apache-2.0 / MIT |
| tracing / tracing-subscriber | Rust crate dependency | Tokio Contributors | MIT | Upstream MIT |
| sha2 | Rust crate dependency | RustCrypto Developers | Apache-2.0 / MIT | Upstream Apache-2.0 / MIT |
| libc | Rust crate dependency | The Rust Project Developers | Apache-2.0 / MIT | Upstream Apache-2.0 / MIT |

---

## 3. Recommended FOSS Applications Suite (17 Packages)

Catalog defined in `apps/catalog.json`. Applications retain independent upstream licenses:

| Application | Package ID | Upstream Project | License | Delivery Method |
|---|---|---|---|---|
| Fossify Phone | `org.fossify.phone` | Fossify Org | GPL-3.0-or-later | Verified On-Demand Fetch |
| Fossify Messages | `org.fossify.messages` | Fossify Org | GPL-3.0-or-later | Verified On-Demand Fetch |
| Fossify Contacts | `org.fossify.contacts` | Fossify Org | GPL-3.0-or-later | Verified On-Demand Fetch |
| Fossify Gallery | `org.fossify.gallery` | Fossify Org | GPL-3.0-or-later | Verified On-Demand Fetch |
| Fossify Camera | `org.fossify.camera` | Fossify Org | GPL-3.0-or-later | Verified On-Demand Fetch |
| Fossify File Manager | `org.fossify.filemanager` | Fossify Org | GPL-3.0-or-later | Verified On-Demand Fetch |
| Fossify Music Player | `org.fossify.musicplayer` | Fossify Org | GPL-3.0-or-later | Verified On-Demand Fetch |
| FairEmail | `eu.faircode.email` | Marcel Bokhorst | GPL-3.0-only | Verified On-Demand Fetch |
| FOSS Browser | `de.baumann.browser` | scoute-dich | GPL-3.0-or-later | Verified On-Demand Fetch |
| HeliBoard | `helium314.keyboard` | Helium314 | GPL-3.0-or-later | Verified On-Demand Fetch |
| Organic Maps | `app.organicmaps` | Organic Maps Community | Apache-2.0 | Verified On-Demand Fetch |
| Breezy Weather | `org.breezyweather` | Breezy Weather Contributors | GPL-3.0-or-later | Verified On-Demand Fetch |
| PDF Viewer Plus | `me.zhanghai.android.pdfviewer` | Zhang Hai | Apache-2.0 | Verified On-Demand Fetch |
| OpenDocument Reader | `at.tomtasche.reader.foss` | Tom Tasche | GPL-3.0-or-later | Verified On-Demand Fetch |
| NewPipe | `org.schabi.newpipe` | Team NewPipe | GPL-3.0-or-later | Verified On-Demand Fetch |
| BitChat | `com.bitchat.droid` | BitChat Team | GPL-3.0-or-later | Verified On-Demand Fetch |
| Aurora Store | `com.aurora.store` | Rahul Patel / AuroraOSS | GPL-3.0-or-later | Verified On-Demand Fetch |
