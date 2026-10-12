#!/usr/bin/env bash
set -euo pipefail

# Legacy filename retained for compatibility. This packages a Linux rootfs archive;
# it does not build Android, a GSI, or a flashable system image.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$ROOT/target/linux-rootfs"
OUTPUT_ARCHIVE="$OUTPUT_DIR/tontos-linux-rootfs.tar.gz"
TARGET_ARCH="${TARGET_ARCH:-x86_64-unknown-linux-gnu}"
ROOTFS_DIR=""
ARCHIVE_TEMP=""
DRY_RUN=false

cleanup() {
    if [[ -n "$ROOTFS_DIR" && -d "$ROOTFS_DIR" ]]; then
        rm -rf -- "$ROOTFS_DIR"
    fi
    if [[ -n "$ARCHIVE_TEMP" && -f "$ARCHIVE_TEMP" ]]; then
        rm -f -- "$ARCHIVE_TEMP"
    fi
}
trap cleanup EXIT

for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --arch=*) TARGET_ARCH="${arg#*=}" ;;
        *)
            echo "Unknown argument: $arg" >&2
            echo "Usage: $0 [--dry-run] [--arch=<rust-target>]" >&2
            exit 2
            ;;
    esac
done

if [[ ! "$TARGET_ARCH" =~ ^[A-Za-z0-9_]+(-[A-Za-z0-9_]+)*$ ]]; then
    echo "Invalid Rust target triple: $TARGET_ARCH" >&2
    exit 2
fi

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

echo "TontOS Linux rootfs archive packager (not an Android GSI or flashable image)"
echo "Target architecture: $TARGET_ARCH"
echo "Output archive:      $OUTPUT_ARCHIVE"

if [[ "$DRY_RUN" == true ]]; then
    echo "[DRY RUN] No build or output files will be created."
    for service in "$ROOT"/init/*.service; do
        [[ -f "$service" ]] || {
            echo "Missing systemd unit files in $ROOT/init" >&2
            exit 1
        }
    done
    echo "[DRY RUN] Systemd unit inputs are present; binaries were not built or checked."
    exit 0
fi

cd "$ROOT"
mkdir -p "$OUTPUT_DIR"
if [[ -e "$OUTPUT_ARCHIVE" ]]; then
    echo "Refusing to overwrite existing archive: $OUTPUT_ARCHIVE" >&2
    exit 1
fi

echo "Building workspace binaries for $TARGET_ARCH..."
if ! command -v clang >/dev/null 2>&1; then
    echo "Required linker 'clang' not found in PATH; install clang or set target.$TARGET_ARCH.linker in .cargo/config.toml" >&2
    exit 1
fi
cargo build --workspace --target "$TARGET_ARCH" \
    --config "target.$TARGET_ARCH.linker=\"clang\"" --release

BIN_SRC="$ROOT/target/$TARGET_ARCH/release"
for bin in "${BINARIES[@]}"; do
    if [[ ! -f "$BIN_SRC/$bin" ]]; then
        echo "Required Linux binary missing: $BIN_SRC/$bin" >&2
        exit 1
    fi
done

ROOTFS_DIR="$(mktemp -d "$OUTPUT_DIR/.rootfs.XXXXXXXX")"
ARCHIVE_TEMP="$(mktemp "$OUTPUT_DIR/.tontos-linux-rootfs.XXXXXXXX")"
mkdir -p \
    "$ROOTFS_DIR/usr/bin" \
    "$ROOTFS_DIR/usr/lib/systemd/system" \
    "$ROOTFS_DIR/usr/share/tontos/apps" \
    "$ROOTFS_DIR/usr/share/sounds/tontos" \
    "$ROOTFS_DIR/etc/tontos" \
    "$ROOTFS_DIR/var/lib/tontos"

for bin in "${BINARIES[@]}"; do
    install -m 0755 "$BIN_SRC/$bin" "$ROOTFS_DIR/usr/bin/$bin"
done

for service in "$ROOT"/init/*.service; do
    install -m 0644 "$service" "$ROOTFS_DIR/usr/lib/systemd/system/"
done

if [[ -f "$ROOT/launcher_apk/tontos-launcher.apk" ]]; then
    install -m 0644 "$ROOT/launcher_apk/tontos-launcher.apk" \
        "$ROOTFS_DIR/usr/share/tontos/apps/"
fi

shopt -s nullglob
for apk in "$ROOT"/apps/*.apk; do
    install -m 0644 "$apk" "$ROOTFS_DIR/usr/share/tontos/apps/"
done
shopt -u nullglob

if [[ -f "$ROOT/crates/tont-settings/src/default_settings.json" ]]; then
    install -m 0644 "$ROOT/crates/tont-settings/src/default_settings.json" "$ROOTFS_DIR/etc/tontos/settings.json"
else
    cat > "$ROOTFS_DIR/etc/tontos/settings.json" <<'EOF'
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
    "maps_app": "app.organicmaps",
    "gallery_app": "org.fossify.gallery",
    "music_app": "org.fossify.musicplayer",
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
    "font_scale": 1.0,
    "screen_time_budget_mins": 120,
    "theme": "amoled_black"
  }
}
EOF
fi

cat > "$ROOTFS_DIR/etc/os-release" <<'EOF'
NAME="TontOS"
ID="tontos"
VERSION="0.0.1-alpha"
PRETTY_NAME="TontOS Digital Detox Mobile Linux"
HOME_URL="https://github.com/tontos/tontos"
EOF

cat > "$ROOTFS_DIR/README.txt" <<'EOF'
TontOS Linux root filesystem staging archive.
This is not Android, a GSI, a recovery package, or a flashable image.
It makes no device identity, Verified Boot, or security-patch claims.
Target-specific installation and boot integration are not provided.
EOF

tar -czf "$ARCHIVE_TEMP" -C "$ROOTFS_DIR" .
chmod 0644 "$ARCHIVE_TEMP"
ln "$ARCHIVE_TEMP" "$OUTPUT_ARCHIVE"
rm -f -- "$ARCHIVE_TEMP"
ARCHIVE_TEMP=""
echo "Linux rootfs archive created: $OUTPUT_ARCHIVE"
echo "This archive is not an Android GSI or flashable system image."
