#!/usr/bin/env bash
set -euo pipefail

SERIAL=""
CHECK_SANDBOX=false
TARGET_USER_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --sandbox)
            CHECK_SANDBOX=true
            shift
            ;;
        --user)
            CHECK_SANDBOX=true
            TARGET_USER_ID="$2"
            shift 2
            ;;
        -s|--serial)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for $1" >&2
                exit 2
            fi
            SERIAL="$2"
            shift 2
            ;;
        -s=*|--serial=*)
            SERIAL="${1#*=}"
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--sandbox] [--user <id>] [-s <device-serial>]"
            echo "  Read Android boot/build properties and report integrity readiness."
            echo "  --sandbox:    Inspect Play Integrity readiness within isolated Sandbox Profile (User 10+)"
            echo "  --user <id>:  Inspect Play Integrity and GMS state for specific user profile"
            echo "  -s <serial>:  Target ADB device serial"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

if [[ -z "${ANDROID_ROOT:-}" ]] && ! command -v getprop >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb || true)"
    if [[ -z "$ADB_BIN" ]]; then
        echo "ERROR: 'adb' was not found; no Android device properties were inspected." >&2
        exit 1
    fi
    ADB_CMD=("$ADB_BIN")
    if [[ -n "$SERIAL" ]]; then
        ADB_CMD+=("-s" "$SERIAL")
    fi
    IS_DEVICE=false
else
    IS_DEVICE=true
    ADB_CMD=()
fi

query_prop() {
    local prop="$1"
    if [[ "$IS_DEVICE" == true ]]; then
        getprop "$prop" 2>/dev/null || true
    else
        "${ADB_CMD[@]}" shell getprop "$prop" 2>/dev/null | tr -d '\r' || true
    fi
}

query_shell() {
    local cmd="$1"
    if [[ "$IS_DEVICE" == true ]]; then
        eval "$cmd" 2>/dev/null || true
    else
        "${ADB_CMD[@]}" shell "$cmd" 2>/dev/null | tr -d '\r' || true
    fi
}

print_property() {
    local label="$1"
    local property="$2"
    local value
    value="$(query_prop "$property")"
    if [[ -n "$value" ]]; then
        printf '  %-28s %s\n' "$label:" "$value"
    else
        printf '  %-28s %s\n' "$label:" "(unavailable or unset)"
    fi
}

echo "=========================================================="
echo "  TontOS Play Integrity & Attestation Diagnostic Engine"
echo "  NOTE: Inspected values do not establish Play Integrity, CTS certification, or Verified Boot."
echo "=========================================================="
echo ""
echo "--> [1] Android Boot & Build Properties (Platform Baseline):"
print_property "Verified-boot-state" "ro.boot.verifiedbootstate"
print_property "Bootloader-locked-property" "ro.boot.flash.locked"
print_property "VBMeta-device-state" "ro.boot.vbmeta.device_state"
print_property "Verity-mode" "ro.boot.veritymode"
print_property "Build-type" "ro.build.type"
print_property "Build-tags" "ro.build.tags"
print_property "Debuggable-property" "ro.debuggable"
print_property "Secure-property" "ro.secure"
print_property "Build-fingerprint" "ro.build.fingerprint"
print_property "Security-patch-level" "ro.build.version.security_patch"

echo ""
echo "--> [2] Android User Profiles (DAC Separation):"
USERS="$(query_shell "pm list users")"
if [[ -n "$USERS" ]]; then
    printf '%s\n' "$USERS"
else
    echo "  (unavailable)"
fi

if [[ "$CHECK_SANDBOX" == true ]]; then
    echo ""
    echo "--> [3] Sandboxed Profile (User 10+) Integrity & Isolation Audit:"
    
    SANDBOX_ID="$TARGET_USER_ID"
    if [[ -z "$SANDBOX_ID" ]]; then
        SANDBOX_ID=$(echo "$USERS" | grep -E "UserInfo\{[1-9][0-9]+:" | head -n1 | sed -E 's/.*UserInfo\{([0-9]+):.*/\1/' || true)
    fi

    if [[ -z "$SANDBOX_ID" ]]; then
        echo "  [INFO] No active Sandbox user profile (User >= 10) detected."
        echo "         Run './bin/helpers/setup_sandbox_profile.sh --create' to initialize."
    else
        echo "  Target Sandbox Profile User ID: $SANDBOX_ID"
        
        # Check running state
        USER_STATE="$(query_shell "am get-started-user-state $SANDBOX_ID")"
        echo "  Lifecycle State: $USER_STATE"
        
        # Check GMS packages inside this user profile
        echo ""
        echo "  Installed GMS & Sandboxed Packages in User $SANDBOX_ID:"
        PKGS="$(query_shell "pm list packages --user $SANDBOX_ID")"
        if echo "$PKGS" | grep -q "com.google.android.gms"; then
            echo "    [+] com.google.android.gms is installed in User $SANDBOX_ID"
        else
            echo "    [-] com.google.android.gms NOT found in User $SANDBOX_ID"
        fi
        if echo "$PKGS" | grep -q "com.google.android.gsf"; then
            echo "    [+] com.google.android.gsf is installed in User $SANDBOX_ID"
        fi
        if echo "$PKGS" | grep -q "com.android.vending"; then
            echo "    [+] com.android.vending is installed in User $SANDBOX_ID"
        fi

        # Check AppOps Telemetry Shield for GMS
        echo ""
        echo "  AppOps Telemetry Shield Verification (User $SANDBOX_ID):"
        BODY_OP="$(query_shell "cmd appops get --user $SANDBOX_ID com.google.android.gms BODY_SENSORS")"
        if echo "$BODY_OP" | grep -qi "deny"; then
            echo "    [PASS] BODY_SENSORS: DENIED (Telemetry Shield active)"
        else
            echo "    [WARN] BODY_SENSORS: $BODY_OP (Shield not applied)"
        fi

        LOC_OP="$(query_shell "cmd appops get --user $SANDBOX_ID com.google.android.gms ACCESS_BACKGROUND_LOCATION")"
        if echo "$LOC_OP" | grep -qi "deny"; then
            echo "    [PASS] ACCESS_BACKGROUND_LOCATION: DENIED (Telemetry Shield active)"
        else
            echo "    [WARN] ACCESS_BACKGROUND_LOCATION: $LOC_OP"
        fi

        # Sandbox Profile Isolation Summary
        echo ""
        echo "  Profile Isolation & Attestation Notice:"
        echo "    * Sandbox DAC Profile: Unprivileged multi-user boundary active"
        echo "    * Online Attestation: Requires live Google Play Integrity API verification on target device"
        echo "    * Hardware Enclave: Subject to SoC Keymint/TEE attestation support"
    fi
fi

echo ""
echo "No integrity verdict or certification status was evaluated."
echo "=========================================================="
echo "  Report complete."
echo "=========================================================="
exit 0
