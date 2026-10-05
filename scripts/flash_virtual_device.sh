#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TontOS: Clean Virtual Device Provisioning & Zero-State Flash Harness
# Wipes, flashes, and provisions a clean Android Virtual Device (AVD) into TontOS.
# ==============================================================================

AVD_NAME="medium_phone"
EMULATOR_BIN="$HOME/Android/Sdk/emulator/emulator"
WIPE_DATA=false
COLD_BOOT=false
RESTART_EMULATOR=false

for arg in "$@"; do
    case "$arg" in
        --wipe-data|-w)
            WIPE_DATA=true
            ;;
        --cold-boot|-c)
            COLD_BOOT=true
            ;;
        --restart|-r)
            RESTART_EMULATOR=true
            ;;
        --help|-h)
            echo "Usage: $0 [--wipe-data] [--cold-boot] [--restart]"
            echo "  --wipe-data, -w:   Wipe /data partition (factory reset virtual device)"
            echo "  --cold-boot, -c:   Perform cold boot without loading snapshots"
            echo "  --restart, -r:     Kill currently running emulator instance and start fresh"
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg"
            exit 1
            ;;
    esac
done

echo "=========================================================="
echo " TontOS: Clean Virtual Device Provisioning"
echo " Target AVD: $AVD_NAME"
echo " Wipe Data:  $WIPE_DATA"
echo "=========================================================="

# 1. Check if emulator is running
EMULATOR_RUNNING=false
if adb devices | grep -q "emulator"; then
    EMULATOR_RUNNING=true
fi

if [ "$RESTART_EMULATOR" = true ] || ([ "$EMULATOR_RUNNING" = false ] && [ -x "$EMULATOR_BIN" ]); then
    if [ "$EMULATOR_RUNNING" = true ]; then
        echo "[*] Terminating existing emulator..."
        adb emu kill || true
        sleep 3
    fi

    EMU_ARGS=("-avd" "$AVD_NAME" "-no-audio" "-no-boot-anim")
    if [ "$WIPE_DATA" = true ]; then
        EMU_ARGS+=("-wipe-data")
    fi
    if [ "$COLD_BOOT" = true ]; then
        EMU_ARGS+=("-no-snapshot-load")
    fi

    echo "[*] Launching clean virtual device ($AVD_NAME)..."
    "$EMULATOR_BIN" "${EMU_ARGS[@]}" > /dev/null 2>&1 &
fi

echo "[*] Waiting for virtual device to complete boot..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 1
done
echo "[+] Virtual device online and boot completed."

# 2. Build TontOS Launcher APK
echo "[*] Building TontOS Launcher APK..."
./scripts/build_launcher_apk.sh > /dev/null
echo "[+] TontOS Launcher APK built."

# 3. Aggressive Debloat
echo "[*] Running custom ROM debloater & telemetry purge..."
./scripts/debloat_custom_rom.sh --sweep || true

# 4. Install TontOS Suite
echo "[*] Installing TontOS Launcher..."
adb install -r -g launcher_apk/tontos-launcher.apk

if [ -d "apps" ]; then
    echo "[*] Installing curated FOSS application suite..."
    for apk in apps/*.apk; do
        if [ -f "$apk" ]; then
            echo "    -> Installing $(basename "$apk")..."
            adb install -r -g "$apk" || true
        fi
    done
fi

# 5. Configure System Roles & Hardening
echo "[*] Configuring launcher home activity & permissions..."
adb shell cmd package set-home-activity org.tontos.launcher/.MainActivity || true
adb shell appops set org.tontos.launcher WRITE_SETTINGS allow || true
adb shell appops set org.tontos.launcher SYSTEM_ALERT_WINDOW allow || true

# 6. Disable Notification Shade Expansion (Zero-Dropdown)
echo "[*] Enforcing Zero-Dropdown minimal OS posture..."
adb shell cmd statusbar send-disable-flag statusbar-expansion quick-settings notification-peek 2>/dev/null || true

# 7. Deploy Rust Micro-Daemons
echo "[*] Deploying TontOS Rust binaries..."
./scripts/run_emulator_tontos.sh || true

# 8. Start TontOS Launcher
echo "[*] Starting TontOS Launcher..."
adb shell am start -n org.tontos.launcher/.MainActivity

echo "=========================================================="
echo " [SUCCESS] Clean TontOS Virtual Device Provisioned!"
echo "=========================================================="
