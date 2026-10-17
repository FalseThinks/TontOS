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
echo " Deteniendo Cuttlefish Virtual Device (TontOS)..."
echo "=========================================================="

# 1. Ejecutar stop_cvd nativo de AOSP
if [[ -x "$HOST_OUT/bin/stop_cvd" ]]; then
    export PATH="$HOST_OUT/bin:$PATH"
    "$HOST_OUT/bin/stop_cvd" 2>/dev/null || true
fi

# 2. Desconectar ADB
adb disconnect 127.0.0.1:6520 2>/dev/null || true
adb disconnect 0.0.0.0:6520 2>/dev/null || true

# 3. Limpiar cualquier proceso residual
pkill -f "run_cvd" 2>/dev/null || true
pkill -f "crosvm" 2>/dev/null || true

echo "[+] Cuttlefish VM detenida y sockets liberados con éxito."
