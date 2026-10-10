# TontOS

TontOS is an experimental Linux host project that runs a separate Android
userspace through Waydroid. It is **not currently an Android ROM, GSI, or
bootable phone image**.

The tested baseline is an isolated Arch Linux x86_64 VM with Waydroid (Android
13) and a headless Weston/Pixman session. The launcher has booted, rendered,
and loaded an external HTTP page in that guest. A Waydroid user-session
stop/start recovered the launcher and preserved its selected theme. Privileged
container recovery and VM-reboot recovery are still unverified. No physical
device, ARM64 target, cellular radio, or charging-control support is claimed.

## Build and tests

```sh
cargo fmt --all -- --check
cargo test --workspace
./scripts/build_launcher_apk.sh
```

The launcher build requires JDK 17 and Android SDK platform 34/build-tools
36.0.0. Set `ANDROID_SDK_ROOT` or `ANDROID_HOME`; intermediates use a temporary
directory, and `OUTPUT_APK` can override the default APK path.

The legacy-named `scripts/make_gsi_rootfs.sh` now creates a Linux rootfs
staging archive at `target/linux-rootfs/tontos-linux-rootfs.tar.gz`; it does
not build Android or produce a flashable image. `scripts/make_ota_package.sh`
fails closed until a signed, target-compatible updater is implemented.
Bootloader status is read-only, and the integrity scripts do not claim CTS,
Play Integrity, or Verified Boot certification.

Battery charging limits and bypass charging are hardware-specific. TontOS
reports these controls as unsupported unless the guest exposes a verified
interface; battery savings and performance improvements require measurements
on supported physical hardware.
