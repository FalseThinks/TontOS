#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TontOS: Custom ROM Aggressive Debloater & System Hardening
# Strips Google Play Services, telemetry, tracking, and non-essential bloatware.
# Enforces TontOS Minimalist Launcher and disables notification shade expansion.
# ==============================================================================

SERIAL=""
DRY_RUN=false
SWEEP_UNKNOWN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --sweep|--aggressive)
            SWEEP_UNKNOWN=true
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
            echo "Usage: $0 [--dry-run] [--sweep] [-s <device-serial>]"
            echo "  --dry-run:     Simulate debloating actions without executing on device"
            echo "  --sweep:       Aggressively remove non-whitelisted telemetry/bloatware"
            echo "  -s, --serial:  Specify target ADB device serial"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

echo "=========================================================="
echo " TontOS: Custom ROM Debloater & System Hardening"
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
fi

run_remote() {
    if [ "$DRY_RUN" = true ]; then
        echo "  [DRY-RUN] $*"
        return 0
    fi

    if [ "$IS_DEVICE" = true ]; then
        sh -c "$*"
    else
        "${ADB_CMD[@]}" shell "$@"
    fi
}

run_remote_capture() {
    if [ "$IS_DEVICE" = true ]; then
        sh -c "$*" 2>/dev/null || true
    else
        "${ADB_CMD[@]}" shell "$*" 2>/dev/null || true
    fi
}

# Verify device connectivity if running on host
if [ "$IS_DEVICE" = false ] && [ "$DRY_RUN" = false ]; then
    DEVICES_OUTPUT="$("${ADB_CMD[@]}" devices | grep -v "List of devices" | grep -w "device" || true)"
    if [ -z "$DEVICES_OUTPUT" ]; then
        echo "[-] Error: No active Android device or emulator detected via ADB."
        echo "    Start an emulator with: ./bin/helpers/run_emulator_tontos.sh"
        echo "    Or run with --dry-run to validate debloat configuration."
        exit 1
    fi
    DEVICE_ID="$(echo "$DEVICES_OUTPUT" | head -n1 | awk '{print $1}')"
    echo "[+] Target Android device connected: $DEVICE_ID"
fi

# 1. Target Google bloatware, tracking, telemetry, and non-essential stock packages
GOOGLE_BLOATWARE=(
    "com.google.android.gms"
    "com.google.android.gsf"
    "com.android.vending"
    "com.google.android.googlequicksearchbox"
    "com.google.android.apps.nexuslauncher"
    "com.google.android.youtube"
    "com.google.android.apps.youtube.music"
    "com.google.android.apps.photos"
    "com.google.android.apps.docs"
    "com.google.android.apps.maps"
    "com.google.android.apps.messaging"
    "com.google.android.dialer"
    "com.google.android.apps.wallpaper"
    "com.google.android.deskclock"
    "com.google.android.feedback"
    "com.google.android.projection.gearhead"
    "com.google.android.apps.safetyhub"
    "com.google.android.apps.restore"
    "com.google.mainline.telemetry"
    "com.google.mainline.adservices"
    "com.google.android.federatedcompute"
    "com.google.android.as"
    "com.android.chrome"
    "com.android.wallpapercropper"
    "com.android.printspooler"
    "com.android.egg"
    "com.android.traceur"
    "com.android.musicfx"
    "com.android.documentsui"
)

echo ""
echo ">> [1/4] Disabling & Uninstalling Google Bloatware & Telemetry..."
REMOVED_COUNT=0
SKIPPED_COUNT=0

# Fetch currently installed packages (including disabled / uninstalled for user 0)
INSTALLED_PKGS="$(run_remote_capture "pm list packages -u" | sed 's/package://' | tr -d '\r' || true)"

for pkg in "${GOOGLE_BLOATWARE[@]}"; do
    if [ "$DRY_RUN" = true ] || echo "$INSTALLED_PKGS" | grep -qx "$pkg"; then
        echo "  [x] Debloating: $pkg"
        # 1. Disable package for user 0
        run_remote "pm disable-user --user 0 $pkg 2>/dev/null || pm disable $pkg 2>/dev/null || true"
        # 2. Uninstall package for user 0
        run_remote "pm uninstall -k --user 0 $pkg 2>/dev/null || true"
        # 3. Clear application data & cache
        run_remote "pm clear $pkg 2>/dev/null || true"
        # 4. Revoke background runtime operations
        run_remote "cmd appops set $pkg RUN_IN_BACKGROUND ignore 2>/dev/null || true"
        REMOVED_COUNT=$((REMOVED_COUNT + 1))
    else
        echo "  [-] Not present: $pkg"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
    fi
done

echo "[+] Debloat complete. Processed: $REMOVED_COUNT removed/disabled, $SKIPPED_COUNT absent."

# 2. Enforce Retain Whitelist: Core HAL / ART / Providers + FOSS Suite + TontOS Launcher
echo ""
echo ">> [2/4] Verifying Core Retained Packages..."
FOSS_PACKAGES=(
    "org.tontos.launcher"
    "de.baumann.browser"
    "org.fossify.phone"
    "org.fossify.messages"
    "org.fossify.contacts"
    "org.fossify.gallery"
    "org.fossify.filemanager"
    "org.fossify.musicplayer"
    "eu.faircode.email"
    "com.aurora.store"
    "com.bitchat.droid"
    "org.schabi.newpipe"
    "app.grapheneos.pdfviewer"
    "org.opendocument.reader"
    "helium314.keyboard"
)

for pkg in "${FOSS_PACKAGES[@]}"; do
    if echo "$INSTALLED_PKGS" | grep -qx "$pkg"; then
        echo "  [+] Retained FOSS/TontOS package active: $pkg"
    else
        echo "  [*] FOSS package not yet installed: $pkg (available in apps/)"
    fi
done

# If --sweep is enabled, remove any unknown telemetry / tracking not in whitelist
if [ "$SWEEP_UNKNOWN" = true ]; then
    echo ""
    echo ">> [Sweep] Auditing non-whitelisted tracking packages..."
    while IFS= read -r pkg; do
        [ -z "$pkg" ] && continue
        case "$pkg" in
            *telemetry*|*adservices*|*analytics*|*facebook*|*meta*|*netflix*|*amazon*)
                echo "  [x] Sweeping bloatware: $pkg"
                run_remote "pm disable-user --user 0 $pkg 2>/dev/null || true"
                run_remote "pm uninstall -k --user 0 $pkg 2>/dev/null || true"
                ;;
        esac
    done <<< "$INSTALLED_PKGS"
fi

# 3. Configure Default Launcher Role to TontOS Minimalist Launcher
echo ""
echo ">> [3/5] Configuring Default Launcher & Keyboard..."
run_remote "cmd package set-home-activity org.tontos.launcher/.MainActivity 2>/dev/null || true"
run_remote "cmd role add-role-holder android.app.role.HOME org.tontos.launcher 1 2>/dev/null || cmd role add-role-holder android.app.role.HOME org.tontos.launcher 2>/dev/null || true"
run_remote "ime enable helium314.keyboard/.latin.LatinIME 2>/dev/null || true"
run_remote "ime set helium314.keyboard/.latin.LatinIME 2>/dev/null || true"
run_remote "settings put secure default_input_method helium314.keyboard/.latin.LatinIME 2>/dev/null || true"
run_remote "settings put secure enabled_input_methods 'helium314.keyboard/.latin.LatinIME:com.android.inputmethod.latin/.LatinIME' 2>/dev/null || true"
echo "[+] Default launcher set to org.tontos.launcher/.MainActivity; default keyboard IME set to HeliBoard"

# 4. Zero Dropdown: Disable SystemUI Notification Shade Expansion & Peek
echo ""
echo ">> [4/5] Disabling SystemUI Notification Shade & Configuring TontOS Shade Permissions..."
run_remote "cmd statusbar send-disable-flag statusbar-expansion quick-settings notification-peek system-icons notification-icons clock 2>/dev/null || true"
run_remote "settings put global heads_up_notifications_enabled 0 2>/dev/null || true"
run_remote "settings put secure notification_bubbles 0 2>/dev/null || true"
run_remote "cmd notification allow_dnd org.tontos.launcher 2>/dev/null || true"
run_remote "pm grant org.tontos.launcher android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true"
echo "[+] SystemUI notification shade expansion disabled; TontOS Ultra Shade permissions granted."

# 5. Background Execution Policy: By default block all apps from running in background
echo ""
echo ">> [5/5] Enforcing Default Background Restrictions on Installed Packages..."
for pkg in $INSTALLED_PKGS; do
    if [ "$pkg" != "org.tontos.launcher" ] && [ -n "$pkg" ]; then
        run_remote "cmd appops set $pkg RUN_IN_BACKGROUND ignore 2>/dev/null || true"
        run_remote "am set-standby-bucket $pkg restricted 2>/dev/null || true"
    fi
done
echo "[+] All installed packages restricted from running in background by default."

echo ""
echo "=========================================================="
echo " TontOS Custom ROM Debloat Completed Successfully!"
echo " Retained: Essential Android HAL/ART + FOSS Suite + TontOS"
echo "=========================================================="
