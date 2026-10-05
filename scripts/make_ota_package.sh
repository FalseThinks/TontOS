#!/usr/bin/env bash
# make_ota_package.sh — Generate TontOS OTA update package
# Output: target/gsi/tontos-ota.zip
# Usage:  ./scripts/make_ota_package.sh [--version <ver>] [--channel <RELEASE|BETA|NIGHTLY>]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
TARGET_DIR="$ROOT/target/gsi"
IMG="$TARGET_DIR/tontos-system.img"
OUT="$TARGET_DIR/tontos-ota.zip"
WORK="$TARGET_DIR/ota_work"

VERSION="${VERSION:-1.0.0}"
CHANNEL="${CHANNEL:-RELEASE}"
BUILD_DATE="$(date -u +%Y%m%d)"
BUILD_TS="$(date -u +%s)"
DEVICE_NAME="TontOS-GSI"
FINGERPRINT="org.tontos.gsi/${DEVICE_NAME}:${VERSION}/${BUILD_DATE}.${CHANNEL}/release-keys"

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --channel) CHANNEL="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ ! -f "$IMG" ]]; then
  echo "[ERROR] System image not found: $IMG"
  echo "        Run ./scripts/make_gsi_rootfs.sh first."
  exit 1
fi

echo "=== TontOS OTA Package Builder ==="
echo "  Image:    $IMG"
echo "  Version:  $VERSION"
echo "  Channel:  $CHANNEL"
echo "  Build:    $BUILD_DATE"
echo ""

# Compute SHA256 and size of the system image
IMG_SHA256="$(sha256sum "$IMG" | awk '{print $1}')"
IMG_SIZE="$(stat -c%s "$IMG")"

echo "[1/5] SHA256: $IMG_SHA256"
echo "[1/5] Size:   $IMG_SIZE bytes"

# Prepare work directory
rm -rf "$WORK"
mkdir -p "$WORK/META-INF/com/android"
mkdir -p "$WORK/images"

# payload.bin = the sparse system image (raw OTA payload)
echo "[2/5] Copying payload (system image)..."
cp "$IMG" "$WORK/payload.bin"

# payload_properties.txt — required by update_engine / OTA client
echo "[3/5] Writing payload_properties.txt..."
cat > "$WORK/payload_properties.txt" << EOF
FILE_HASH=${IMG_SHA256}
FILE_SIZE=${IMG_SIZE}
METADATA_HASH=${IMG_SHA256}
METADATA_SIZE=${IMG_SIZE}
EOF

# META-INF/com/android/metadata — OTA metadata for recovery
echo "[4/5] Writing OTA metadata..."
cat > "$WORK/META-INF/com/android/metadata" << EOF
ota-type=BLOCK
ota-required-cache=0
ota-wipe-user-data=false
post-build=${FINGERPRINT}
post-build-incremental=${BUILD_DATE}.${CHANNEL}
post-sdk-level=36
post-security-patch-level=$(date -u +%Y-%m-%d)
post-timestamp=${BUILD_TS}
pre-device=TontOS-GSI
serialno=NONE
EOF

# OTA update-binary stub (required by some recoveries)
mkdir -p "$WORK/META-INF/com/google/android"
cat > "$WORK/META-INF/com/google/android/updater-script" << 'EOF'
# TontOS OTA updater-script (stub — update_engine handles actual flashing)
ui_print("TontOS OTA Update");
ui_print("Version: TONTOS_VERSION");
ui_print("Channel: TONTOS_CHANNEL");
ui_print("Verifying payload...");
EOF

cat > "$WORK/META-INF/com/google/android/update-binary" << 'EOF'
#!/sbin/sh
# Stub binary — real flashing done by update_engine via payload.bin
echo "TontOS OTA stub — update_engine will apply payload.bin"
exit 0
EOF
chmod +x "$WORK/META-INF/com/google/android/update-binary"

# tontos-ota-info.json — human-readable descriptor for OTA servers / TontOS updater daemon
cat > "$WORK/tontos-ota-info.json" << EOF
{
  "version": "${VERSION}",
  "channel": "${CHANNEL}",
  "build_date": "${BUILD_DATE}",
  "build_timestamp": ${BUILD_TS},
  "device": "${DEVICE_NAME}",
  "fingerprint": "${FINGERPRINT}",
  "payload": {
    "filename": "payload.bin",
    "sha256": "${IMG_SHA256}",
    "size": ${IMG_SIZE}
  },
  "release_notes": {
    "en": "TontOS ${VERSION} — Privacy-first minimalist ROM. FOSS-only, AMOLED-native, digital detox.",
    "es": "TontOS ${VERSION} — ROM minimalista centrada en privacidad. Solo FOSS, AMOLED nativo, detox digital."
  },
  "download_url": "https://github.com/paproka/TontOS/releases/download/v${VERSION}/tontos-ota.zip"
}
EOF

# Pack everything into the OTA zip
echo "[5/5] Packing OTA zip..."
rm -f "$OUT"
if command -v zip &>/dev/null; then
  (cd "$WORK" && zip -r -9 "$OUT" . -x "*.DS_Store")
else
  # Fallback: python3 zipfile (always available)
  python3 - "$WORK" "$OUT" << 'PYEOF'
import sys, zipfile, os, pathlib
work = pathlib.Path(sys.argv[1])
out  = sys.argv[2]
with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for f in sorted(work.rglob('*')):
        if f.is_file():
            zf.write(f, f.relative_to(work))
print(f"Packed {out}")
PYEOF
fi

OTA_SIZE="$(du -sh "$OUT" | awk '{print $1}')"
OTA_SHA256="$(sha256sum "$OUT" | awk '{print $1}')"

echo ""
echo "=== OTA Package Ready ==="
echo "  Output:   $OUT"
echo "  Size:     $OTA_SIZE"
echo "  SHA256:   $OTA_SHA256"
echo ""
echo "=== Deploy Options ==="
echo "  Sideload (ADB recovery):"
echo "    adb sideload $OUT"
echo ""
echo "  Flash via fastboot (GSI slot):"
echo "    fastboot flash system $IMG"
echo ""
echo "  Host OTA descriptor (for tont-syncd OTA channel):"
echo "    cat $WORK/tontos-ota-info.json"
echo ""

# Cleanup work dir
rm -rf "$WORK"
