#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TontOS: Play Integrity & Sandboxed Google Pay Auditor
# Verifies whether the current device and profile meet Google's integrity
# checks (MEETS_BASIC_INTEGRITY & MEETS_DEVICE_INTEGRITY) for Google Wallet.
# ==============================================================================

SERIAL=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial)
            SERIAL="$2"
            shift 2
            ;;
        -s=*|--serial=*)
            SERIAL="${1#*=}"
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [-s <device-serial>]"
            echo "  -s, --serial: Target ADB device serial"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

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

query_prop() {
    local prop="$1"
    if [ "$IS_DEVICE" = true ]; then
        getprop "$prop" || true
    else
        "${ADB_CMD[@]}" shell getprop "$prop" | tr -d '\r' || true
    fi
}

query_cmd() {
    if [ "$IS_DEVICE" = true ]; then
        eval "$@"
    else
        "${ADB_CMD[@]}" shell "$@"
    fi
}

echo "=========================================================="
echo " TontOS: Play Integrity & Google Wallet Readiness Audit"
echo "=========================================================="

FAILED_CHECKS=0
TOTAL_CHECKS=0

check_result() {
    local name="$1"
    local actual="$2"
    local expected="$3"
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if [ "$actual" = "$expected" ]; then
        echo "  [PASS] $name: $actual"
    else
        echo "  [FAIL] $name: actual='$actual', expected='$expected'"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

# 1. Bootloader & Verified Boot Checks
echo "[1] Bootloader & Verified Boot Properties:"
VBOOT_STATE=$(query_prop "ro.boot.verifiedbootstate")
FLASH_LOCKED=$(query_prop "ro.boot.flash.locked")
VBMETA_STATE=$(query_prop "ro.boot.vbmeta.device_state")
VERITY_MODE=$(query_prop "ro.boot.veritymode")
WARRANTY=$(query_prop "ro.boot.warranty_bit")

check_result "Verified Boot State" "$VBOOT_STATE" "green"
check_result "Flash Locked" "$FLASH_LOCKED" "1"
check_result "VBMeta Device State" "$VBMETA_STATE" "locked"

# 2. Build Security & Certification
echo ""
echo "[2] Build Security & User/Release Keys:"
BUILD_TYPE=$(query_prop "ro.build.type")
BUILD_TAGS=$(query_prop "ro.build.tags")
DEBUGGABLE=$(query_prop "ro.debuggable")
SECURE=$(query_prop "ro.secure")
FINGERPRINT=$(query_prop "ro.build.fingerprint")
SECURITY_PATCH=$(query_prop "ro.build.version.security_patch")

check_result "Build Type" "$BUILD_TYPE" "user"
check_result "Build Tags" "$BUILD_TAGS" "release-keys"
check_result "Debuggable" "$DEBUGGABLE" "0"
check_result "Secure" "$SECURE" "1"
echo "  [*] Active Fingerprint: $FINGERPRINT"
echo "  [*] Security Patch Level: $SECURITY_PATCH"

# 3. Sandboxed Profile Isolation Checks
echo ""
echo "[3] Sandboxed Profile Isolation & Users:"
USERS_LIST=$(query_cmd "pm list users" || true)
echo "$USERS_LIST"

SANDBOX_USER_ID=$(echo "$USERS_LIST" | grep -i "Google Pay\|Sandbox" | head -n 1 | sed -E 's/.*UserInfo\{([0-9]+):.*/\1/' || true)
if [ -n "$SANDBOX_USER_ID" ]; then
    echo "  [PASS] Isolated Sandbox Profile present: User ID $SANDBOX_USER_ID"
    # Check if Primary user (0) is free of GMS bloat
    USER0_PKGS=$(query_cmd "pm list packages --user 0" || true)
    if echo "$USER0_PKGS" | grep -q "com.google.android.gms"; then
        echo "  [WARN] com.google.android.gms found in User 0 (Primary). Recommended: isolate to User $SANDBOX_USER_ID only."
    else
        echo "  [PASS] Primary Profile (User 0) is 100% Google-Free!"
    fi
else
    echo "  [INFO] No dedicated Sandbox User Profile detected. Run scripts/setup_sandboxed_google_pay.sh to create one."
fi

# 4. Play Integrity Final Verdict Calculation
echo ""
echo "=========================================================="
echo " Play Integrity Evaluation Summary:"
echo "=========================================================="

BASIC_INTEGRITY=false
DEVICE_INTEGRITY=false

if [ "$DEBUGGABLE" = "0" ] && [ "$SECURE" = "1" ] && [ "$BUILD_TYPE" = "user" ]; then
    BASIC_INTEGRITY=true
fi

if [ "$BASIC_INTEGRITY" = true ] && [ "$VBOOT_STATE" = "green" ] && [ "$FLASH_LOCKED" = "1" ] && [ "$VBMETA_STATE" = "locked" ]; then
    DEVICE_INTEGRITY=true
fi

echo "  -> MEETS_BASIC_INTEGRITY:  $([ "$BASIC_INTEGRITY" = true ] && echo "[ PASS ]" || echo "[ FAIL ]")"
echo "  -> MEETS_DEVICE_INTEGRITY: $([ "$DEVICE_INTEGRITY" = true ] && echo "[ PASS - Google Wallet Ready ]" || echo "[ FAIL - Run scripts/patch_play_integrity.sh ]")"
echo "=========================================================="

if [ "$DEVICE_INTEGRITY" = true ]; then
    echo "[+] System satisfies all requirements for Google Pay / Google Wallet NFC transactions."
    exit 0
else
    echo "[-] System requires patch injection to satisfy MEETS_DEVICE_INTEGRITY."
    exit 1
fi
