#!/usr/bin/env bash
# ==============================================================================
# TontOS Cuttlefish Virtual Device Runner (Android 17)
# Launches the freshly compiled AOSP 17 TontOS ROM in Cuttlefish VM.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
PRODUCT_OUT="$AOSP_DIR/out/target/product/vsoc_x86_64_only"
HOST_OUT="$AOSP_DIR/out/host/linux-x86"

if [[ ! -d "$PRODUCT_OUT" ]]; then
    echo "ERROR: Product out directory not found at $PRODUCT_OUT" >&2
    exit 1
fi

echo "=========================================================="
echo " Launching TontOS (Android 17 / API 37) on Cuttlefish VM"
echo " Product Out: $PRODUCT_OUT"
echo " Host Tools:  $HOST_OUT"
echo "=========================================================="

export ANDROID_PRODUCT_OUT="$PRODUCT_OUT"
export ANDROID_HOST_OUT="$HOST_OUT"
export PATH="$HOST_OUT/bin:$PATH"

# Stop any previous Cuttlefish instance
if command -v stop_cvd >/dev/null 2>&1; then
    echo "[*] Stopping previous CVD instances if running..."
    stop_cvd >/dev/null 2>&1 || true
fi

echo "[*] Launching Cuttlefish in daemon mode..."
cd "$PRODUCT_OUT"
launch_cvd \
    --daemon \
    --report_anonymous_usage_stats=n \
    --cpus=4 \
    --memory_mb=4096 \
    --start_webrtc=true \
    --gpu_mode=guest_swiftshader \
    --system_image_dir="$PRODUCT_OUT"

echo
echo "=========================================================="
echo " [SUCCESS] TontOS Android 17 Cuttlefish VM started!"
echo " Access UI in your browser: https://localhost:8443"
echo " Connect ADB: adb connect 127.0.0.1:6520"
echo " Stop VM:     stop_cvd"
echo "=========================================================="
