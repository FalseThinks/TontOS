#!/usr/bin/env bash
# ==============================================================================
# TontOS Unified Clean Installer, Builder & Real-Time Performance Monitor
# Orchestrates Host Swap, Testing, AOSP Build, Cuttlefish VM Boot & Metrics.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
HELPERS_DIR="$SCRIPT_DIR/helpers"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
LOGS_DIR="$ROOT_DIR/logs"

# Select product and host tool output directories
if [[ -f "$ROOT_DIR/out/target/product/vsoc_x86_64_only/system.img" ]]; then
    PRODUCT_OUT="$ROOT_DIR/out/target/product/vsoc_x86_64_only"
    HOST_OUT="$ROOT_DIR/out/host/linux-x86"
    [[ ! -d "$HOST_OUT/bin" && -d "$AOSP_DIR/out/host/linux-x86/bin" ]] && HOST_OUT="$AOSP_DIR/out/host/linux-x86"
else
    PRODUCT_OUT="$AOSP_DIR/out/target/product/vsoc_x86_64_only"
    HOST_OUT="$AOSP_DIR/out/host/linux-x86"
fi

# Default options
DO_BUILD=false
BUILD_FROM_SCRATCH=false
RUN_TESTS=true
SETUP_SWAP=true
USE_DOCKER=false
CLEAN_VM_BOOT=true
RUN_CUTTLEFISH=true
EXPLICIT_SKIP_BUILD=false
SAMPLE_INTERVAL=5
MONITOR_DURATION=0
BUILD_JOBS=""
VERBOSE=false

print_help() {
    cat << EOF
Usage: ./bin/clean_install_and_monitor.sh [OPTIONS]

Build Options:
  --build              Compile TontOS Cuttlefish before booting
  --docker             Compile AOSP inside isolated Docker container
  --from-scratch       Clean AOSP output artifacts and rebuild entirely
  --incremental        Trigger incremental AOSP build (preserves out/)
  --skip-build         Skip AOSP build and boot existing compiled images (default)

Quality & Memory Options:
  --run-tests          Run test suite before build (default)
  --skip-tests         Bypass pre-build test suite
  --setup-swap         Ensure swapfile + zRAM active before build (default)
  --skip-swap          Bypass automated swap verification

Cuttlefish & Monitoring Options:
  --clean              Factory wipe VM runtime and data partition (default)
  --no-clean           Resume existing VM state without wiping data
  --skip-cuttlefish    Stop after tests/build; do not boot VM
  --interval=SEC       Metric sampling interval in seconds (default: 5)
  --duration=SEC       Stop monitoring after SEC seconds (default: infinite)
  --verbose, -v        Display live boot progress
  --help, -h           Show this help message
EOF
    exit 0
}

# Parse options
for arg in "$@"; do
    case "$arg" in
        --verbose|-v)        VERBOSE=true ;;
        --build)             DO_BUILD=true ;;
        --docker)            USE_DOCKER=true; DO_BUILD=true; SETUP_SWAP=false ;;
        --from-scratch)      DO_BUILD=true; BUILD_FROM_SCRATCH=true ;;
        --incremental)       DO_BUILD=true; BUILD_FROM_SCRATCH=false ;;
        --skip-build|--no-build) DO_BUILD=false; EXPLICIT_SKIP_BUILD=true ;;
        --run-tests)         RUN_TESTS=true ;;
        --skip-tests)        RUN_TESTS=false ;;
        --setup-swap)        SETUP_SWAP=true ;;
        --skip-swap)         SETUP_SWAP=false ;;
        --clean)             CLEAN_VM_BOOT=true ;;
        --no-clean)          CLEAN_VM_BOOT=false ;;
        --skip-cuttlefish|--no-cuttlefish) RUN_CUTTLEFISH=false ;;
        --interval=*)        SAMPLE_INTERVAL="${arg#*=}" ;;
        --duration=*)        MONITOR_DURATION="${arg#*=}" ;;
        --jobs=*|-j*)        BUILD_JOBS="${arg#*=}" ;;
        --available|--total|--ram=*) ;; # Consumed by memory_profile.sh
        --help|-h)           print_help ;;
        *) echo "[-] Unknown option: $arg" >&2; exit 1 ;;
    esac
done

has_compiled_images() {
    [[ -f "$PRODUCT_OUT/boot.img" && -f "$PRODUCT_OUT/system.img" ]]
}

# 1. Swap Pre-flight
if [[ "$SETUP_SWAP" == true ]]; then
    TOTAL_SWAP_KB="$(grep SwapTotal /proc/meminfo | awk '{print $2}')"
    if [[ "$TOTAL_SWAP_KB" -lt 20971520 ]] || ! grep -q "/swapfile_aosp" /proc/swaps 2>/dev/null; then
        echo "[+] Configuring tiered host swap..."
        "$HELPERS_DIR/setup_host_swap.sh" setup || true
    fi
fi

# 2. Test Suite
if [[ "$RUN_TESTS" == true ]]; then
    echo "[+] Running test suite..."
    "$SCRIPT_DIR/run_all_tests.sh" --fast
fi

# 3. Launcher APK & Sync
echo "[+] Building TontOS Launcher APK..."
"$SCRIPT_DIR/build_launcher_apk.sh"
if [[ -d "$AOSP_DIR/vendor/tontos" ]]; then
    mkdir -p "$AOSP_DIR/vendor/tontos/products" "$AOSP_DIR/vendor/tontos/apps/prebuilts"
    cp -f "$ROOT_DIR/platform/products/"* "$AOSP_DIR/vendor/tontos/products/" 2>/dev/null || true
    cp -f "$ROOT_DIR/platform/apps/prebuilts/Android.bp" "$AOSP_DIR/vendor/tontos/apps/prebuilts/" 2>/dev/null || true
    cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$AOSP_DIR/vendor/tontos/apps/prebuilts/" 2>/dev/null || true
    cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$AOSP_DIR/vendor/tontos/apps/" 2>/dev/null || true
fi

# 4. AOSP Build (if requested or missing images)
if [[ "$DO_BUILD" == true ]] || { [[ "$RUN_CUTTLEFISH" == true ]] && ! has_compiled_images && [[ "$EXPLICIT_SKIP_BUILD" != true ]]; }; then
    if [[ "$USE_DOCKER" == true ]]; then
        "$SCRIPT_DIR/docker_build_rom.sh" --build "$@"
    else
        source "$HELPERS_DIR/memory_profile.sh" "$@"
        (
            cd "$AOSP_DIR"
            unset OUT_DIR
            export USE_CCACHE=1 CCACHE_DIR="$ROOT_DIR/.ccache"
            source build/envsetup.sh
            lunch tontos_cf_x86_64-trunk_staging-userdebug
            m -j"$BUILD_JOBS"
        )
    fi
fi

[[ "$RUN_CUTTLEFISH" != true ]] && exit 0

# 5. Cuttlefish VM Boot & Monitoring
if ! has_compiled_images; then
    echo "[-] ERROR: Compiled ROM images not found at: $PRODUCT_OUT" >&2
    exit 1
fi

mkdir -p "$LOGS_DIR"
TIMESTAMP="$(date +'%Y%m%d_%H%M%S')"
USAGE_LOG="$LOGS_DIR/tontos_usage_${TIMESTAMP}.log"
LOGCAT_LOG="$LOGS_DIR/tontos_logcat_${TIMESTAMP}.log"

export ANDROID_PRODUCT_OUT="$PRODUCT_OUT" ANDROID_HOST_OUT="$HOST_OUT" PATH="$HOST_OUT/bin:$PATH"

"$HELPERS_DIR/stop_cuttlefish.sh" >/dev/null 2>&1 || true

if [[ -f "$PRODUCT_OUT/super.img" ]] && file "$PRODUCT_OUT/super.img" | grep -q "Android sparse image"; then
    echo "[+] Converting super.img to raw for crosvm..."
    "$HOST_OUT/bin/simg2img" "$PRODUCT_OUT/super.img" "$PRODUCT_OUT/super.raw.img"
    mv -f "$PRODUCT_OUT/super.raw.img" "$PRODUCT_OUT/super.img"
fi

EXTRA_FLAGS=("--resume=true")
if [[ "$CLEAN_VM_BOOT" == true ]]; then
    rm -rf "$PRODUCT_OUT"/cuttlefish_assembly* "$PRODUCT_OUT"/cuttlefish_runtime* "$HOME"/cuttlefish "$HOME"/cuttlefish_runtime* /tmp/cf_avd_* /tmp/cf_env_* 2>/dev/null || true
    EXTRA_FLAGS=("--noresume")
fi

echo "[+] Booting Cuttlefish VM..."
cd "$PRODUCT_OUT"
launch_cvd \
    --daemon \
    --report_anonymous_usage_stats=n \
    --cpus=4 \
    --memory_mb=4096 \
    --start_webrtc=true \
    --gpu_mode=guest_swiftshader \
    --system_image_dir="$PRODUCT_OUT" \
    "${EXTRA_FLAGS[@]}"

echo "[+] Waiting for ADB boot completion (127.0.0.1:6520)..."
BOOT_START_TIME=$(date +%s)
adb connect 127.0.0.1:6520 >/dev/null 2>&1 || true

while true; do
    BOOT_STATUS="$(adb -s 127.0.0.1:6520 shell getprop sys.boot_completed 2>/dev/null || echo 0)"
    [[ "${BOOT_STATUS//$'\r'/}" == "1" ]] && break
    sleep 2
    adb connect 127.0.0.1:6520 >/dev/null 2>&1 || true
done
BOOT_ELAPSED=$(( $(date +%s) - BOOT_START_TIME ))

echo "=========================================================="
echo " [SUCCESS] TontOS booted in ${BOOT_ELAPSED}s!"
echo " WebRTC GUI:  https://localhost:8443"
echo " Usage Log:   $USAGE_LOG"
echo " Logcat Log:  $LOGCAT_LOG"
echo "=========================================================="

adb -s 127.0.0.1:6520 logcat -v threadtime > "$LOGCAT_LOG" 2>&1 &
LOGCAT_PID=$!

cleanup() {
    kill "$LOGCAT_PID" 2>/dev/null || true
    echo ""
    echo "=========================================================="
    echo " [SUMMARY] TontOS Live Session Closed"
    echo " Logcat: $LOGCAT_LOG"
    echo " Metrics: $USAGE_LOG"
    echo "=========================================================="
    exit 0
}
trap cleanup SIGINT SIGTERM

echo "Timestamp,HostMemFreeMB,HostSwapUsedMB,GuestMemTotalKB,GuestMemFreeKB,GuestCachedKB,LauncherPSS_KB" > "$USAGE_LOG"
MONITOR_START=$(date +%s)

while true; do
    NOW=$(date +%s)
    if [[ "$MONITOR_DURATION" -gt 0 ]] && [[ $(( NOW - MONITOR_START )) -ge "$MONITOR_DURATION" ]]; then
        cleanup
    fi

    HOST_MEM_FREE=$(free -m | awk '/^Mem:/ {print $4}')
    HOST_SWAP_USED=$(free -m | awk '/^Swap:/ {print $3}')
    MEMINFO_RAW="$(adb -s 127.0.0.1:6520 shell cat /proc/meminfo 2>/dev/null || true)"
    GUEST_MEM_TOTAL=$(echo "$MEMINFO_RAW" | awk '/^MemTotal:/ {print $2}')
    GUEST_MEM_FREE=$(echo "$MEMINFO_RAW" | awk '/^MemFree:/ {print $2}')
    GUEST_CACHED=$(echo "$MEMINFO_RAW" | awk '/^Cached:/ {print $2}')
    LAUNCHER_PSS="$(adb -s 127.0.0.1:6520 shell dumpsys meminfo org.tontos.launcher 2>/dev/null | awk '/TOTAL PSS:/ {print $3}' || echo 0)"
    LAUNCHER_PSS="${LAUNCHER_PSS:-0}"

    TIMESTAMP_SAMPLE="$(date +'%Y-%m-%d %H:%M:%S')"
    echo "$TIMESTAMP_SAMPLE,$HOST_MEM_FREE,$HOST_SWAP_USED,$GUEST_MEM_TOTAL,$GUEST_MEM_FREE,$GUEST_CACHED,$LAUNCHER_PSS" >> "$USAGE_LOG"

    printf "\r[METRICS] Host Swap: %s MB | Guest Free: %s KB | Launcher PSS: %s KB" \
           "$HOST_SWAP_USED" "${GUEST_MEM_FREE:-0}" "${LAUNCHER_PSS:-0}"

    sleep "$SAMPLE_INTERVAL"
done
