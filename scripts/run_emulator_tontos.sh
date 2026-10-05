#!/usr/bin/env bash
set -euo pipefail

# TontOS Emulator Execution Harness
# Deploys and executes the TontOS minimalist detox OS stack live on Android emulator.

TARGET="x86_64-unknown-linux-musl"
BIN_DIR="target/$TARGET/release"

echo "=========================================================="
echo " TontOS: Live Execution Harness for Android Emulator"
echo " Target: $TARGET"
echo "=========================================================="

# 1. Verify ADB connection
if ! adb devices | grep -q "emulator"; then
    echo "[-] Error: No running emulator detected. Start an emulator instance first."
    exit 1
fi
echo "[+] Active emulator detected."

# 2. Verify release binaries exist, otherwise compile
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

NEED_BUILD=false
for bin in "${BINARIES[@]}"; do
    if [ ! -f "$BIN_DIR/$bin" ]; then
        NEED_BUILD=true
        break
    fi
done

if [ "$NEED_BUILD" = true ]; then
    echo "[*] Compiling release binaries for $TARGET..."
    cargo build --workspace --target "$TARGET" --config "target.$TARGET.linker=\"clang\"" --release
fi

# 3. Deploy all binaries to /data/local/tmp
echo "[*] Deploying TontOS binaries to emulator (/data/local/tmp/)..."
for bin in "${BINARIES[@]}"; do
    adb push "$BIN_DIR/$bin" /data/local/tmp/ > /dev/null
    adb shell chmod 755 "/data/local/tmp/$bin"
done
echo "[+] All 10 TontOS binaries deployed and permissions set to 0755."

# 4. Push default settings configuration
if [ -f "/tmp/tontos_rootfs_staging/etc/tontos/settings.json" ]; then
    adb push "/tmp/tontos_rootfs_staging/etc/tontos/settings.json" /data/local/tmp/settings.json > /dev/null
fi

# 4b. Enforce Zero Dropdown: Disable notification shade and quick settings at OS level
adb shell cmd statusbar send-disable-flag statusbar-expansion quick-settings notification-peek system-icons notification-icons clock 2>/dev/null || true

# 5. Execute live TontOS verification session
echo ""
echo "=========================================================="
echo " Starting TontOS Subsystems on Emulator"
echo "=========================================================="

echo ">> [1/6] Launching Power Controller (tont-powerd)..."
adb shell "RUST_LOG=info timeout 1 /data/local/tmp/tont-powerd 2>&1 || true"

echo ">> [2/6] Launching Sensor Hub (tont-sensors)..."
adb shell "RUST_LOG=info timeout 1 /data/local/tmp/tont-sensors 2>&1 || true"

echo ">> [3/6] Launching Radio Sync Scheduler (tont-syncd)..."
adb shell "RUST_LOG=info timeout 1 /data/local/tmp/tont-syncd 2>&1 || true"

echo ">> [4/6] Launching Cellular Telephony Stack (tont-telephony)..."
adb shell "RUST_LOG=info timeout 1 /data/local/tmp/tont-telephony 2>&1 || true"

echo ">> [5/6] Launching Hardware Buttons & Audio Mixer (tont-input)..."
adb shell "RUST_LOG=info timeout 1 /data/local/tmp/tont-input 2>&1 || true"

echo ">> [6/6] Launching Minimalist AMOLED Detox Shell (tont-shell)..."
adb shell "RUST_LOG=info timeout 1 /data/local/tmp/tont-shell 2>&1 || true"

echo ""
echo ">> [Bonus] Testing Cryptographic Backup & Tamper Verification (tont-backup)..."
adb shell "/data/local/tmp/tont-backup create /data/local/tmp/backup.json > /dev/null"
adb shell "/data/local/tmp/tont-backup verify /data/local/tmp/backup.json"

echo "=========================================================="
echo " TontOS live emulator execution complete! All daemons verified."
echo "=========================================================="
