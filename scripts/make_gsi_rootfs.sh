#!/usr/bin/env bash
set -euo pipefail

# TontOS Project Treble GSI Rootfs Packager
# Packages compiled binaries and service units into a flashable system.img layout.

DRY_RUN=false
BUILD_IMAGE=true
TARGET_ARCH="${TARGET_ARCH:-x86_64-unknown-linux-musl}"
IMAGE_SIZE="${IMAGE_SIZE:-512M}"
ROOTFS_DIR="/tmp/tontos_rootfs_staging"
OUTPUT_DIR="target/gsi"
TARGET_RAW_IMG="$OUTPUT_DIR/tontos-system.raw.img"
TARGET_SPARSE_IMG="$OUTPUT_DIR/tontos-system.img"

for arg in "$@"; do
    case "$arg" in
        --dry-run)
            DRY_RUN=true
            ;;
        --no-img)
            BUILD_IMAGE=false
            ;;
        --arch=*)
            TARGET_ARCH="${arg#*=}"
            ;;
        --size=*)
            IMAGE_SIZE="${arg#*=}"
            ;;
    esac
done

echo "=============================================="
echo " TontOS: GSI Rootfs Packager"
echo " Target Architecture: $TARGET_ARCH"
echo " Staging directory:   $ROOTFS_DIR"
echo " Image Size:          $IMAGE_SIZE"
echo " Output Image:        $TARGET_SPARSE_IMG"
echo "=============================================="

if [ "$DRY_RUN" = true ]; then
    echo "[DRY RUN] Validating binary artifacts, APKs, and systemd units..."
    ls -l init/*.service
    ls -l launcher_apk/tontos-launcher.apk
    ls -l apps/*.apk
    cargo check --workspace
    echo "[DRY RUN] GSI packager validation successful."
    exit 0
fi

echo "1. Compiling workspace binaries for target ($TARGET_ARCH)..."
cargo build --workspace --target "$TARGET_ARCH" --config "target.$TARGET_ARCH.linker=\"clang\"" --release

echo "2. Staging root filesystem..."
rm -rf "$ROOTFS_DIR"
mkdir -p "$ROOTFS_DIR"/bin
mkdir -p "$ROOTFS_DIR"/usr/bin
mkdir -p "$ROOTFS_DIR"/system/bin
mkdir -p "$ROOTFS_DIR"/system/priv-app/TontOSLauncher
mkdir -p "$ROOTFS_DIR"/system/app
mkdir -p "$ROOTFS_DIR"/etc/systemd/system
mkdir -p "$ROOTFS_DIR"/etc/tontos
mkdir -p "$ROOTFS_DIR"/var/lib/tontos
mkdir -p "$ROOTFS_DIR"/usr/share/sounds/tontos

# Full list of 11 TontOS system binaries from crates/
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

BIN_SRC="target/$TARGET_ARCH/release"
for bin in "${BINARIES[@]}"; do
    if [ -f "$BIN_SRC/$bin" ]; then
        cp "$BIN_SRC/$bin" "$ROOTFS_DIR/usr/bin/"
        cp "$BIN_SRC/$bin" "$ROOTFS_DIR/bin/"
        cp "$BIN_SRC/$bin" "$ROOTFS_DIR/system/bin/"
        chmod 755 "$ROOTFS_DIR/usr/bin/$bin"
        chmod 755 "$ROOTFS_DIR/bin/$bin"
        chmod 755 "$ROOTFS_DIR/system/bin/$bin"
        echo "  [x] Staged $bin"
    else
        echo "  [!] Warning: $BIN_SRC/$bin not found!"
    fi
done

# Stage TontOS Launcher in /system/priv-app/TontOSLauncher/TontOSLauncher.apk
echo "3. Staging system & privileged APKs..."
LAUNCHER_APK_SRC="launcher_apk/tontos-launcher.apk"
if [ -f "$LAUNCHER_APK_SRC" ]; then
    cp "$LAUNCHER_APK_SRC" "$ROOTFS_DIR/system/priv-app/TontOSLauncher/TontOSLauncher.apk"
    chmod 644 "$ROOTFS_DIR/system/priv-app/TontOSLauncher/TontOSLauncher.apk"
    chmod 755 "$ROOTFS_DIR/system/priv-app/TontOSLauncher"
    echo "  [x] Staged TontOSLauncher.apk in /system/priv-app/TontOSLauncher/"
else
    echo "  [!] Warning: $LAUNCHER_APK_SRC not found!"
fi

# Stage FOSS APK suite in /system/app/
for apk in apps/*.apk; do
    if [ -f "$apk" ]; then
        apk_basename="$(basename "$apk")"
        apk_dir="${apk_basename%.apk}"
        # Flat staging in /system/app/
        cp "$apk" "$ROOTFS_DIR/system/app/$apk_basename"
        chmod 644 "$ROOTFS_DIR/system/app/$apk_basename"
        # Subfolder staging in /system/app/<app>/ (Android directory scan convention)
        mkdir -p "$ROOTFS_DIR/system/app/$apk_dir"
        chmod 755 "$ROOTFS_DIR/system/app/$apk_dir"
        ln -sf "../$apk_basename" "$ROOTFS_DIR/system/app/$apk_dir/$apk_basename" 2>/dev/null || true
        echo "  [x] Staged $apk_basename in /system/app/"
    fi
done

# Ensure explicit permissions (0644 for APKs, 0755 for directories)
find "$ROOTFS_DIR/system/priv-app" "$ROOTFS_DIR/system/app" -type d -exec chmod 755 {} +
find "$ROOTFS_DIR/system/priv-app" "$ROOTFS_DIR/system/app" -type f -name "*.apk" -exec chmod 644 {} +

# Copy systemd units
echo "4. Staging systemd unit files..."
cp init/*.service "$ROOTFS_DIR/etc/systemd/system/"

# Create default /etc/tontos/settings.json
echo "5. Generating default TontOS settings..."
cat << 'EOF' > "$ROOTFS_DIR/etc/tontos/settings.json"
{
  "connectivity": {
    "wifi_enabled": true,
    "bluetooth_enabled": false,
    "mobile_data_enabled": true,
    "gps_enabled": true,
    "airplane_mode": false,
    "nfc_enabled": false
  },
  "default_apps": {
    "phone_app": "org.fossify.phone",
    "messaging_app": "org.fossify.messages",
    "browser_app": "de.baumann.browser",
    "maps_app": "org.tontos.maps",
    "gallery_app": "org.fossify.gallery",
    "music_app": "org.fossify.music",
    "files_app": "org.fossify.filemanager"
  },
  "sound": {
    "ringtone_path": "/usr/share/sounds/tontos/ring_minimal.ogg",
    "notification_tone_path": "/usr/share/sounds/tontos/notify_pulse.ogg",
    "alarm_tone_path": "/usr/share/sounds/tontos/alarm_chime.ogg",
    "ring_volume_percent": 80,
    "vibration_enabled": true
  },
  "display": {
    "screen_timeout_secs": 30,
    "dark_amoled_mode": true,
    "font_scale": 1.0
  }
}
EOF

# Provide os-release
cat << 'EOF' > "$ROOTFS_DIR/etc/os-release"
NAME="TontOS"
ID="tontos"
VERSION="0.1.0-alpha"
PRETTY_NAME="TontOS Digital Detox Mobile Linux"
HOME_URL="https://github.com/tontos/tontos"
EOF

# 5b. Stage Google Play Integrity & CTS certified build.prop
echo "5b. Staging Play Integrity CTS certification properties..."
mkdir -p "$ROOTFS_DIR/system"
cat << 'EOF' >> "$ROOTFS_DIR/system/build.prop"
# TontOS Play Integrity & CTS Certification Spoof (MEETS_DEVICE_INTEGRITY)
ro.oem_unlock_supported=1
sys.oem_unlock_allowed=0
ro.boot.verifiedbootstate=green
ro.boot.flash.locked=1
ro.boot.vbmeta.device_state=locked
ro.boot.veritymode=enforcing
ro.boot.warranty_bit=0
ro.build.type=user
ro.build.tags=release-keys
ro.debuggable=0
ro.secure=1
ro.build.fingerprint=google/shiba/shiba:14/UD1A.230805.041.B1/10640075:user/release-keys
ro.bootimage.build.fingerprint=google/shiba/shiba:14/UD1A.230805.041.B1/10640075:user/release-keys
ro.system.build.fingerprint=google/shiba/shiba:14/UD1A.230805.041.B1/10640075:user/release-keys
ro.vendor.build.fingerprint=google/shiba/shiba:14/UD1A.230805.041.B1/10640075:user/release-keys
ro.product.brand=google
ro.product.name=shiba
ro.product.device=shiba
ro.product.model=Pixel 8
ro.product.manufacturer=Google
ro.build.version.release=14
ro.build.id=UD1A.230805.041.B1
ro.build.version.incremental=10640075
ro.build.version.security_patch=2023-11-05
EOF
chmod 644 "$ROOTFS_DIR/system/build.prop"

if [ "$BUILD_IMAGE" = true ]; then
    echo "6. Generating flashable GSI image..."
    mkdir -p "$OUTPUT_DIR"
    rm -f "$TARGET_RAW_IMG" "$TARGET_SPARSE_IMG"

    # Truncate raw ext4 image
    truncate -s "$IMAGE_SIZE" "$TARGET_RAW_IMG"

    # Format as ext4
    mke2fs -t ext4 -b 4096 -F -L "system" "$TARGET_RAW_IMG"

    # Populate with e2fsdroid
    e2fsdroid -e -a /system -f "$ROOTFS_DIR" "$TARGET_RAW_IMG"

    # Convert to Android sparse image
    img2simg "$TARGET_RAW_IMG" "$TARGET_SPARSE_IMG"

    echo "=============================================="
    echo " GSI Build Complete!"
    echo " Raw Image:    $TARGET_RAW_IMG ($(du -h "$TARGET_RAW_IMG" | cut -f1))"
    echo " Sparse Image: $TARGET_SPARSE_IMG ($(du -h "$TARGET_SPARSE_IMG" | cut -f1))"
    echo " Ready for fastboot flashing:"
    echo "   fastboot flash system $TARGET_SPARSE_IMG"
    echo "=============================================="
else
    echo "Staging complete at $ROOTFS_DIR (image generation skipped)."
fi
