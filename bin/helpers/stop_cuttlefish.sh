#!/usr/bin/env bash
# ==============================================================================
# TontOS Cuttlefish Virtual Device Stopper
# Stops running Cuttlefish instances and cleans up virtual sockets.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
HOST_OUT="$AOSP_DIR/out/host/linux-x86"

echo "=========================================================="
echo " Stopping Cuttlefish Virtual Device (TontOS)..."
echo "=========================================================="

# 1. Execute native AOSP stop_cvd
if [[ -x "$HOST_OUT/bin/stop_cvd" ]]; then
    export PATH="$HOST_OUT/bin:$PATH"
    "$HOST_OUT/bin/stop_cvd" 2>/dev/null || true
fi

# 2. Disconnect ADB
adb disconnect 127.0.0.1:6520 2>/dev/null || true
adb disconnect 0.0.0.0:6520 2>/dev/null || true

# 3. Terminate residual Cuttlefish processes
pkill -9 -f "run_cvd|crosvm|launch_cvd|webRTC|adb_connector" 2>/dev/null || true

# 4. Clean up sockets and ephemeral instance directories
rm -rf "$HOME/cuttlefish" /tmp/cf_avd_* /tmp/cf_env_* "$HOME/cuttlefish_runtime"* 2>/dev/null || true

echo "[+] Cuttlefish VM stopped and sockets cleared successfully."
