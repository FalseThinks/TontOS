#!/usr/bin/env bash
set -euo pipefail

SERIAL=""

while [[ $# -gt 0 ]]; do
    case "$1" in
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
            echo "Usage: $0 [-s <device-serial>]"
            echo "Read Android boot/build properties; this does not verify attestation."
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

query_users() {
    if [[ "$IS_DEVICE" == true ]]; then
        pm list users 2>/dev/null || true
    else
        "${ADB_CMD[@]}" shell pm list users 2>/dev/null | tr -d '\r' || true
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

echo "Android boot/build property report (read-only)"
echo "These reported values do not establish Play Integrity, CTS certification, or Verified Boot."
echo
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

echo
echo "Android users (as reported by Package Manager):"
USERS="$(query_users)"
if [[ -n "$USERS" ]]; then
    printf '%s\n' "$USERS"
else
    echo "(unavailable)"
fi

echo
echo "No integrity verdict or certification status was evaluated."
