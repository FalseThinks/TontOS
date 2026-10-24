#!/usr/bin/env bash
set -euo pipefail

# TontOS Pure Linux Initramfs Packager
# Packages the minimal Rust daemons and tont-init into a bootable cpio.gz initramfs.

TARGET_ARCH="x86_64-unknown-linux-musl"
STAGING_DIR="/tmp/tontos_initramfs_staging"
OUTPUT_DIR="target/boot"
OUTPUT_INITRAMFS="$OUTPUT_DIR/tontos-initramfs.cpio.gz"
BIN_SRC="target/$TARGET_ARCH/release"

echo "=============================================="
echo " TontOS: Building Pure Linux Initramfs"
echo " Target Architecture: $TARGET_ARCH"
echo " Output: $OUTPUT_INITRAMFS"
echo "=============================================="

# 1. Compile release binaries
echo "1. Compiling static-pie release binaries..."
cargo build --workspace --target "$TARGET_ARCH" --config "target.$TARGET_ARCH.linker=\"clang\"" --release

# 2. Stage filesystem
echo "2. Staging minimal root filesystem..."
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR"/{bin,sbin,etc/tontos,run/tontos,var/lib/tontos,proc,sys,dev,tmp,usr/bin}

BINARIES=(
    "tont-init"
    "tont-powerd"
    "tont-appd"
    "tont-syncd"
    "tont-compositor"
    "tont-shell"
    "tont-telephony"
    "tont-sensors"
    "tont-input"
    "tont-settings-app"
    "tont-backup"
)

for bin in "${BINARIES[@]}"; do
    if [ -f "$BIN_SRC/$bin" ]; then
        cp "$BIN_SRC/$bin" "$STAGING_DIR/bin/"
        chmod 755 "$STAGING_DIR/bin/$bin"
        echo "  [x] Staged $bin"
    fi
done

# Set /init entry point to tont-init
ln -sf /bin/tont-init "$STAGING_DIR/init"

# Copy default settings
if [ -f "/tmp/tontos_rootfs_staging/etc/tontos/settings.json" ]; then
    cp "/tmp/tontos_rootfs_staging/etc/tontos/settings.json" "$STAGING_DIR/etc/tontos/"
fi

# 3. Create CPIO archive and compress with gzip
echo "3. Packaging initramfs..."
mkdir -p "$OUTPUT_DIR"
ABS_OUT="$(pwd)/$OUTPUT_INITRAMFS"
(cd "$STAGING_DIR" && find . -print0 | cpio --null --create --format=newc | gzip -9 > "$ABS_OUT")

echo "=============================================="
echo "[+] Bootable Pure Linux Initramfs Created!"
echo "    Size: $(du -h "$OUTPUT_INITRAMFS" | cut -f1)"
echo "    Init: /init -> /bin/tont-init (PID 1)"
echo "=============================================="
