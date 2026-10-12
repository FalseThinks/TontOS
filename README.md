# TontOS

TontOS is an open-source, minimalist operating system built for digital detox, focus, and battery longevity. It combines a clean, bloatware-free AOSP platform with a high-contrast monospace home environment and host-side Rust orchestration tools.

Maintainer: FalseThinks <paproken@protonmail.com>

## Architecture & Documentation

TontOS is engineered across three clean layers:

1. **AOSP Platform Tree (`~/aosp/tontos`):** Native Android 16 (BP4A) source tree compiled with Soong/Ninja. Defines the `tontos_cf_x86_64` target product in `vendor/tontos/`, stripped at build time of search widgets, app duplication, and vendor bloatware.
   - Architectural Decision: [ADR-002: Native AOSP Custom Product Integration](docs/android-platform/DECISION-02-native-tontos-product.md)
   - Platform Specs: [AOSP Platform Architecture](docs/android-platform/README.md)
2. **TontOS Launcher (`vendor/tontos/apps/TontOSLauncher` / `launcher_apk/`):** First-party system launcher providing low-stimulus monospace typography, AMOLED themes, mindful launch friction, scheduled notification digests, and system-level privacy controls.
   - Detailed Documentation: [Java Launcher Reference](docs/JAVA_LAUNCHER.md)
3. **Rust Workspace (`crates/`):** 14 specialized Rust crates providing build planning, device detection (`tontosctl`), cryptographic artifact verification with streaming SHA-256, and minimal guest daemons.
   - Detailed Documentation: [Rust Workspace Reference](docs/RUST_CRATES.md)

## Build and Tests

```sh
# Rust workspace verification
cargo fmt --all -- --check
cargo test --workspace

# Standalone launcher APK compilation
./scripts/build_launcher_apk.sh

# Complete AOSP ROM compilation via Alpine Docker container
./scripts/docker_build_rom.sh
```

The launcher build requires JDK 17 and Android SDK platform 34 / build-tools 36.0.0. Set `ANDROID_SDK_ROOT` or `ANDROID_HOME`; intermediates use a temporary directory, and `OUTPUT_APK` can override the default APK path.

The Alpine Docker image (`Dockerfile`) encapsulates all dependencies (OpenJDK 17, Python 3, repo, ccache, and glibc compatibility) and mounts the local AOSP source tree to build `tontos_cf_x86_64` in an isolated container.

The legacy-named `scripts/make_gsi_rootfs.sh` creates a Linux rootfs staging archive at `target/linux-rootfs/tontos-linux-rootfs.tar.gz`; it does not build Android or produce a flashable image. `scripts/make_ota_package.sh` fails closed until a signed, target-compatible updater is implemented. Bootloader status is read-only, and the integrity scripts do not claim CTS, Play Integrity, or Verified Boot certification.

Battery charging limits and bypass charging are hardware-specific. TontOS reports these controls as unsupported unless the guest exposes a verified interface; battery savings and performance improvements require measurements on supported physical hardware.
