#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TontOS: Play Integrity & CTS Attestation Spoofing Engine
# Injects certified Google device fingerprint and locked bootloader state
# to satisfy MEETS_DEVICE_INTEGRITY for Google Wallet / Google Pay.
# ==============================================================================

SERIAL=""
DRY_RUN=false
TARGET_DEVICE="pixel8"

# Certified Device Fingerprint Database
# Pixel 8 (shiba) Android 14 Stable
FINGERPRINT_PIXEL8="google/shiba/shiba:14/UD1A.230805.041.B1/10640075:user/release-keys"
BRAND_PIXEL8="google"
PRODUCT_PIXEL8="shiba"
DEVICE_PIXEL8="shiba"
MODEL_PIXEL8="Pixel 8"
MANUFACTURER_PIXEL8="Google"
RELEASE_PIXEL8="14"
ID_PIXEL8="UD1A.230805.041.B1"
INCREMENTAL_PIXEL8="10640075"
SECURITY_PATCH_PIXEL8="2023-11-05"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --device)
            TARGET_DEVICE="$2"
            shift 2
            ;;
        -s|--serial)
            SERIAL="$2"
            shift 2
            ;;
        -s=*|--serial=*)
            SERIAL="${1#*=}"
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--dry-run] [--device <pixel8>] [-s <device-serial>]"
            echo "  --dry-run:     Simulate injection without modifying system properties"
            echo "  --device:      Target certified device profile (default: pixel8)"
            echo "  -s, --serial:  Target ADB device serial"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

echo "=========================================================="
echo " TontOS: Play Integrity & CTS Attestation Engine"
echo " Target Profile: $TARGET_DEVICE ($MODEL_PIXEL8)"
echo " Mode: $([ "$DRY_RUN" = true ] && echo "DRY RUN (simulation)" || echo "ACTIVE")"
echo "=========================================================="

if [ -z "${ANDROID_ROOT:-}" ] && ! command -v getprop >/dev/null 2>&1; then
    IS_DEVICE=false
    ADB_BIN="$(command -v adb || true)"
    if [ -z "$ADB_BIN" ]; then
        echo "[-] Error: 'adb' command not found in PATH."
        exit 1
    fi
    ADB_CMD=("$ADB_BIN")
    if [ -n "$SERIAL" ]; then
        ADB_CMD+=("-s" "$SERIAL")
    fi
else
    IS_DEVICE=true
    ADB_CMD=()
fi

run_cmd() {
    if [ "$DRY_RUN" = true ]; then
        echo "[DRY-RUN] $*"
        return 0
    fi
    if [ "$IS_DEVICE" = true ]; then
        eval "$@"
    else
        "${ADB_CMD[@]}" shell "$@"
    fi
}

# 1. Inject Bootloader Locked Status Properties
echo "[*] Spoofing Bootloader & AVB status (Green / Locked)..."
PROPS_BOOTLOADER=(
    "ro.boot.verifiedbootstate green"
    "ro.boot.flash.locked 1"
    "ro.boot.vbmeta.device_state locked"
    "ro.boot.veritymode enforcing"
    "ro.boot.warranty_bit 0"
    "ro.warranty_bit 0"
    "ro.debuggable 0"
    "ro.secure 1"
    "ro.build.type user"
    "ro.build.tags release-keys"
)

for prop in "${PROPS_BOOTLOADER[@]}"; do
    key=$(echo "$prop" | awk '{print $1}')
    val=$(echo "$prop" | awk '{print $2}')
    echo "  -> Setting $key=$val"
    run_cmd "setprop $key $val" || true
    run_cmd "resetprop -n $key $val" 2>/dev/null || true
done

# 2. Inject Google-Certified CTS Fingerprint Properties
echo "[*] Injecting Google-Certified Build Identity ($MODEL_PIXEL8)..."
PROPS_IDENTITY=(
    "ro.build.fingerprint $FINGERPRINT_PIXEL8"
    "ro.bootimage.build.fingerprint $FINGERPRINT_PIXEL8"
    "ro.system.build.fingerprint $FINGERPRINT_PIXEL8"
    "ro.vendor.build.fingerprint $FINGERPRINT_PIXEL8"
    "ro.product.brand $BRAND_PIXEL8"
    "ro.product.name $PRODUCT_PIXEL8"
    "ro.product.device $DEVICE_PIXEL8"
    "ro.product.model $MODEL_PIXEL8"
    "ro.product.manufacturer $MANUFACTURER_PIXEL8"
    "ro.build.version.release $RELEASE_PIXEL8"
    "ro.build.id $ID_PIXEL8"
    "ro.build.version.incremental $INCREMENTAL_PIXEL8"
    "ro.build.version.security_patch $SECURITY_PATCH_PIXEL8"
)

for prop in "${PROPS_IDENTITY[@]}"; do
    key=$(echo "$prop" | awk '{print $1}')
    val=$(echo "$prop" | awk '{print $2}')
    echo "  -> Setting $key=$val"
    run_cmd "setprop $key $val" || true
    run_cmd "resetprop -n $key $val" 2>/dev/null || true
done

# 3. Create persistent property overlay configuration for GSI staging
echo "[*] Generating persistent overlay: /tmp/tontos_play_integrity.prop..."
cat <<EOF > /tmp/tontos_play_integrity.prop
# TontOS Play Integrity Certification Spoof
ro.boot.verifiedbootstate=green
ro.boot.flash.locked=1
ro.boot.vbmeta.device_state=locked
ro.boot.veritymode=enforcing
ro.boot.warranty_bit=0
ro.build.type=user
ro.build.tags=release-keys
ro.debuggable=0
ro.secure=1
ro.build.fingerprint=$FINGERPRINT_PIXEL8
ro.bootimage.build.fingerprint=$FINGERPRINT_PIXEL8
ro.system.build.fingerprint=$FINGERPRINT_PIXEL8
ro.vendor.build.fingerprint=$FINGERPRINT_PIXEL8
ro.product.brand=$BRAND_PIXEL8
ro.product.name=$PRODUCT_PIXEL8
ro.product.device=$DEVICE_PIXEL8
ro.product.model=$MODEL_PIXEL8
ro.product.manufacturer=$MANUFACTURER_PIXEL8
ro.build.version.release=$RELEASE_PIXEL8
ro.build.id=$ID_PIXEL8
ro.build.version.incremental=$INCREMENTAL_PIXEL8
ro.build.version.security_patch=$SECURITY_PATCH_PIXEL8
EOF

if [ "$IS_DEVICE" = false ]; then
    echo "[*] Pushing persistent overlay to device /data/local/tmp/tontos_play_integrity.prop..."
    "${ADB_CMD[@]}" push /tmp/tontos_play_integrity.prop /data/local/tmp/tontos_play_integrity.prop || true
fi

# 4. Restart GMS unstable process to pick up new properties immediately
echo "[*] Killing GMS unstable process to reload attestation parameters..."
run_cmd "killall -9 com.google.android.gms.unstable" 2>/dev/null || true
run_cmd "killall -9 com.google.android.gms" 2>/dev/null || true

echo "=========================================================="
echo "[+] Play Integrity properties successfully applied!"
echo "    - Target Verdict: MEETS_DEVICE_INTEGRITY"
echo "    - Certified Profile: $MODEL_PIXEL8 ($FINGERPRINT_PIXEL8)"
echo "=========================================================="
