#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TontOS: Multi-User Sandbox Profile Lifecycle Automation
# Provides CLI control for isolated DAC user profiles (User 10+), Zero-RAM
# process freezing, and GrapheneOS-style AppOps telemetry denial.
# ==============================================================================

ACTION="status"
SERIAL=""
DRY_RUN=false
PROFILE_NAME="TontOS Sandbox"
TARGET_USER_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --status)
            ACTION="status"
            shift
            ;;
        --create)
            ACTION="create"
            shift
            ;;
        --start)
            ACTION="start"
            shift
            ;;
        --pause)
            ACTION="pause"
            shift
            ;;
        --destroy)
            ACTION="destroy"
            shift
            ;;
        --shield)
            ACTION="shield"
            shift
            ;;
        --name)
            PROFILE_NAME="$2"
            shift 2
            ;;
        --user-id)
            TARGET_USER_ID="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
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
            echo "Usage: $0 [action] [options]"
            echo ""
            echo "Actions:"
            echo "  --status      Inspect sandbox user ID and running state (default)"
            echo "  --create      Create isolated secondary user profile and apply telemetry shield"
            echo "  --start       Start and unlock sandbox user profile"
            echo "  --pause       Force-stop sandbox profile (Zero-RAM freeze mode)"
            echo "  --destroy     Purge and remove sandbox profile and storage"
            echo "  --shield      Apply strict AppOps telemetry restrictions to GMS packages"
            echo ""
            echo "Options:"
            echo "  --name <name>       Custom sandbox profile name (default: 'TontOS Sandbox')"
            echo "  --user-id <id>      Explicit user ID (default: auto-detected >= 10)"
            echo "  --dry-run           Print commands without executing"
            echo "  -s, --serial <sn>   Target ADB device serial"
            exit 0
            ;;
        *)
            echo "[-] Error: Unknown argument: $1"
            echo "Run '$0 --help' for usage."
            exit 1
            ;;
    esac
done

# Determine execution environment (Host ADB vs. On-Device Shell)
if [ -z "${ANDROID_ROOT:-}" ] && ! command -v getprop >/dev/null 2>&1; then
    IS_DEVICE=false
    ADB_BIN="$(command -v adb || true)"
    if [ -z "$ADB_BIN" ]; then
        echo "[-] Error: 'adb' command not found in host PATH."
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

resolve_user_id() {
    if [ -n "$TARGET_USER_ID" ]; then
        echo "$TARGET_USER_ID"
        return 0
    fi

    local user_list
    user_list=$(query_cmd "pm list users 2>/dev/null" || true)

    # Search for UserInfo{<id>:<name>:...} where id >= 10
    local found_id
    found_id=$(echo "$user_list" | grep -E "UserInfo\{[1-9][0-9]+:" | head -n1 | sed -E 's/.*UserInfo\{([0-9]+):.*/\1/' || true)

    if [ -n "$found_id" ]; then
        echo "$found_id"
    else
        echo ""
    fi
}

apply_telemetry_shield() {
    local uid="$1"
    echo "[*] Enforcing GrapheneOS-style AppOps telemetry denial for User $uid..."

    local target_pkgs=(
        "com.google.android.gms"
        "com.google.android.gsf"
        "com.android.vending"
    )

    local denied_ops=(
        "BODY_SENSORS"
        "HIGH_SAMPLING_RATE_SENSORS"
        "ACTIVITY_RECOGNITION"
        "ACCESS_BACKGROUND_LOCATION"
        "READ_CONTACTS"
        "READ_CALL_LOG"
    )

    for pkg in "${target_pkgs[@]}"; do
        for op in "${denied_ops[@]}"; do
            run_cmd "cmd appops set --user $uid $pkg $op deny 2>/dev/null || true"
        done
    done
    echo "[+] Telemetry shield successfully applied to User $uid."
}

echo "=========================================================="
echo "  TontOS Sandbox Profile Management Engine"
echo "  Action: $ACTION | Environment: $([ "$IS_DEVICE" = true ] && echo "Device" || echo "Host ADB")"
echo "=========================================================="

case "$ACTION" in
    status)
        USER_ID=$(resolve_user_id)
        if [ -z "$USER_ID" ]; then
            echo "[STATUS] Sandbox Profile: NOT_CREATED"
            echo "         No secondary user profile (UID >= 10) detected."
        else
            STATE_OUT=$(query_cmd "am get-started-user-state $USER_ID 2>/dev/null" || true)
            if echo "$STATE_OUT" | grep -qiE "RUNNING|BOOTING"; then
                echo "[STATUS] Sandbox Profile: RUNNING (User ID: $USER_ID)"
                echo "         State: $STATE_OUT"
            else
                echo "[STATUS] Sandbox Profile: PAUSED / ZERO-RAM (User ID: $USER_ID)"
                echo "         State: $STATE_OUT (All processes terminated)"
            fi
        fi
        ;;

    create)
        USER_ID=$(resolve_user_id)
        if [ -n "$USER_ID" ]; then
            echo "[!] Sandbox profile already exists with User ID: $USER_ID"
            exit 0
        fi

        echo "[*] Creating isolated profile '$PROFILE_NAME'..."
        CREATE_OUT=$(query_cmd "pm create-user \"$PROFILE_NAME\" 2>/dev/null || cmd user create-user \"$PROFILE_NAME\" 2>/dev/null" || true)
        echo "$CREATE_OUT"

        NEW_ID=$(echo "$CREATE_OUT" | grep -oE "(id [0-9]+|UserInfo\{[0-9]+)" | grep -oE "[0-9]+" | head -n1 || true)
        if [ -z "$NEW_ID" ]; then
            NEW_ID=$(resolve_user_id)
        fi

        if [ -n "$NEW_ID" ] && [ "$NEW_ID" -ge 10 ]; then
            echo "[+] Successfully created Sandbox Profile User ID: $NEW_ID"
            apply_telemetry_shield "$NEW_ID"
        else
            echo "[-] Failed to create sandbox profile. Output: $CREATE_OUT"
            exit 1
        fi
        ;;

    start)
        USER_ID=$(resolve_user_id)
        if [ -z "$USER_ID" ]; then
            echo "[-] Error: No sandbox profile exists to start. Run --create first."
            exit 1
        fi
        echo "[*] Starting Sandbox Profile User ID: $USER_ID..."
        run_cmd "am start-user $USER_ID"
        echo "[+] Sandbox Profile User $USER_ID started."
        ;;

    pause)
        USER_ID=$(resolve_user_id)
        if [ -z "$USER_ID" ]; then
            echo "[-] Error: No sandbox profile exists to pause."
            exit 1
        fi
        echo "[*] Force-stopping Sandbox Profile User ID: $USER_ID (Zero-RAM mode)..."
        run_cmd "am stop-user -f $USER_ID"
        echo "[+] Sandbox Profile User $USER_ID paused (Zero-RAM active)."
        ;;

    destroy)
        USER_ID=$(resolve_user_id)
        if [ -z "$USER_ID" ]; then
            echo "[!] No sandbox profile exists to destroy."
            exit 0
        fi
        echo "[*] Permanently destroying Sandbox Profile User ID: $USER_ID..."
        run_cmd "pm remove-user $USER_ID 2>/dev/null || cmd user remove-user $USER_ID"
        echo "[+] Sandbox Profile User $USER_ID destroyed."
        ;;

    shield)
        USER_ID=$(resolve_user_id)
        if [ -z "$USER_ID" ]; then
            echo "[-] Error: No sandbox profile exists. Run --create first."
            exit 1
        fi
        apply_telemetry_shield "$USER_ID"
        ;;
esac

echo "=========================================================="
exit 0
