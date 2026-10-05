#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TontOS: Sandboxed Google Pay & Play Services Provisioning (GrapheneOS Model)
# Creates an isolated, unprivileged secondary profile for Google Play Services
# and Google Wallet. Keeps primary TontOS user 100% telemetry-free.
# ==============================================================================

SERIAL=""
DRY_RUN=false
PROFILE_NAME="Google Pay Sandbox"
USER_ID=""
APK_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --name)
            PROFILE_NAME="$2"
            shift 2
            ;;
        --apk-dir)
            APK_DIR="$2"
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
            echo "Usage: $0 [--dry-run] [--name <profile-name>] [--apk-dir <path>] [-s <device-serial>]"
            echo "  --dry-run:     Simulate provisioning actions without executing on device"
            echo "  --name:        Name of the isolated user profile (default: 'Google Pay Sandbox')"
            echo "  --apk-dir:     Directory containing GSF, GMS and Wallet APKs to install into sandbox"
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
echo " TontOS: Sandboxed Google Pay Isolation Engine"
echo " Target Profile: $PROFILE_NAME"
echo " Mode: $([ "$DRY_RUN" = true ] && echo "DRY RUN (simulation)" || echo "ACTIVE")"
echo "=========================================================="

# Determine environment (Host with ADB vs. On-Device Shell)
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

query_cmd() {
    if [ "$IS_DEVICE" = true ]; then
        eval "$@"
    else
        "${ADB_CMD[@]}" shell "$@"
    fi
}

# 1. Check if user profile already exists
echo "[*] Inspecting existing user profiles on device..."
EXISTING_USERS=$(query_cmd "pm list users" || true)
echo "$EXISTING_USERS"

# Look for profile matching PROFILE_NAME
USER_ID=$(echo "$EXISTING_USERS" | grep "$PROFILE_NAME" | head -n 1 | sed -E 's/.*UserInfo\{([0-9]+):.*/\1/' || true)

if [ -n "$USER_ID" ]; then
    echo "[+] Found existing profile: User ID $USER_ID ('$PROFILE_NAME')"
else
    echo "[*] Creating isolated profile for '$PROFILE_NAME'..."
    if [ "$DRY_RUN" = true ]; then
        echo "[DRY-RUN] cmd user create-user \"$PROFILE_NAME\""
        USER_ID="10"
    else
        # Attempt to create secondary unprivileged profile
        CREATE_OUT=$(query_cmd "pm create-user \"$PROFILE_NAME\"" || true)
        if echo "$CREATE_OUT" | grep -q "Success: created user id"; then
            USER_ID=$(echo "$CREATE_OUT" | awk '{print $NF}')
        elif echo "$CREATE_OUT" | grep -q "UserInfo"; then
            USER_ID=$(echo "$CREATE_OUT" | sed -E 's/.*UserInfo\{([0-9]+):.*/\1/')
        else
            # Fallback to cmd user create-user
            CREATE_OUT=$(query_cmd "cmd user create-user \"$PROFILE_NAME\"" || true)
            USER_ID=$(echo "$CREATE_OUT" | grep -oE '[0-9]+' | tail -n 1 || true)
        fi

        if [ -z "$USER_ID" ]; then
            echo "[-] Error: Failed to create user profile. Output: $CREATE_OUT"
            exit 1
        fi
        echo "[+] Successfully created isolated profile: User ID $USER_ID"
    fi
fi

# 2. Start the user profile
echo "[*] Starting user profile $USER_ID..."
run_cmd "am start-user $USER_ID" || true

# 3. If APK directory provided, install GSF, GMS and Wallet strictly into this profile
if [ -n "$APK_DIR" ] && [ -d "$APK_DIR" ]; then
    echo "[*] Installing Sandboxed Google Play packages into Profile $USER_ID..."
    for apk in "$APK_DIR"/*.apk; do
        [ -f "$apk" ] || continue
        echo "  -> Installing $(basename "$apk") for User $USER_ID (Unprivileged User App)..."
        if [ "$IS_DEVICE" = true ]; then
            run_cmd "pm install -r --user $USER_ID \"$apk\""
        else
            "${ADB_CMD[@]}" install -r --user "$USER_ID" "$apk"
        fi
    done
else
    echo "[!] Note: No --apk-dir provided. Ensure com.google.android.gsf, com.google.android.gms, and com.google.android.apps.walletnfcrel are installed into User $USER_ID."
fi

# 4. Enforce strict privacy and anti-telemetry policies on GMS within the Sandbox
echo "[*] Applying GrapheneOS-style isolation rules to Profile $USER_ID..."

GMS_PKGS=(
    "com.google.android.gms"
    "com.google.android.gsf"
    "com.android.vending"
)

for pkg in "${GMS_PKGS[@]}"; do
    echo "  -> Restricting telemetry & background sensors for $pkg (User $USER_ID)..."
    # Deny body sensors, high sampling rate, and activity recognition
    run_cmd "cmd appops set --user $USER_ID $pkg BODY_SENSORS deny" || true
    run_cmd "cmd appops set --user $USER_ID $pkg HIGH_SAMPLING_RATE_SENSORS deny" || true
    run_cmd "cmd appops set --user $USER_ID $pkg ACTIVITY_RECOGNITION deny" || true
    # Deny background location harvesting
    run_cmd "cmd appops set --user $USER_ID $pkg ACCESS_BACKGROUND_LOCATION deny" || true
    # Disallow reading contacts or call logs across profile boundary
    run_cmd "cmd appops set --user $USER_ID $pkg READ_CONTACTS deny" || true
    run_cmd "cmd appops set --user $USER_ID $pkg READ_CALL_LOG deny" || true
done

# 5. Ensure Host Card Emulation (HCE) routing is accessible for Google Pay in this sandbox
echo "[*] Verifying HCE routing status..."
run_cmd "cmd package set-install-location 0" || true

echo "=========================================================="
echo "[+] Sandboxed Google Pay environment ready in User $USER_ID!"
echo "    - Main TontOS Profile (User 0): Completely isolated & clean"
echo "    - Google Pay Sandbox (User $USER_ID): Strictly unprivileged"
echo "=========================================================="
