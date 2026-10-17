#!/usr/bin/env bash
# ==============================================================================
# TontOS Clean Installer & Real-Time Performance Monitor
# Boots AOSP 17 from scratch, monitors resource consumption, and logs metrics.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
PRODUCT_OUT="$AOSP_DIR/out/target/product/vsoc_x86_64_only"
HOST_OUT="$AOSP_DIR/out/host/linux-x86"
LOGS_DIR="$ROOT_DIR/logs"

# Parameters
SAMPLE_INTERVAL=5
CLEAN_BOOT=true
MONITOR_DURATION=0 # 0 = infinite until Ctrl+C

for arg in "$@"; do
    case "$arg" in
        --no-clean)
            CLEAN_BOOT=false
            ;;
        --interval=*)
            SAMPLE_INTERVAL="${arg#*=}"
            ;;
        --duration=*)
            MONITOR_DURATION="${arg#*=}"
            ;;
        --help|-h)
            echo "Usage: ./scripts/monitor_tontos.sh [OPTIONS]"
            echo "  --no-clean       Resume existing VM state instead of fresh clean install"
            echo "  --interval=SEC   Metric sampling interval in seconds (default: 5)"
            echo "  --duration=SEC   Stop monitoring after SEC seconds (default: infinite)"
            exit 0
            ;;
    esac
done

if [[ ! -d "$PRODUCT_OUT" ]]; then
    echo "[-] ERROR: Compiled ROM images not found at $PRODUCT_OUT" >&2
    echo "    Compile the ROM first with ./scripts/docker_build_rom.sh" >&2
    exit 1
fi

mkdir -p "$LOGS_DIR"
TIMESTAMP="$(date +'%Y%m%d_%H%M%S')"
USAGE_LOG="$LOGS_DIR/tontos_usage_${TIMESTAMP}.log"
LOGCAT_LOG="$LOGS_DIR/tontos_logcat_${TIMESTAMP}.log"

export ANDROID_PRODUCT_OUT="$PRODUCT_OUT"
export ANDROID_HOST_OUT="$HOST_OUT"
export PATH="$HOST_OUT/bin:$PATH"

echo "=========================================================="
echo " TontOS Clean Boot & Performance Monitor (Android 17)"
echo " ROM Output:  $PRODUCT_OUT"
echo " Usage Log:   $USAGE_LOG"
echo " Logcat Log:  $LOGCAT_LOG"
echo " Clean Boot:  $CLEAN_BOOT"
echo " Sample Rate: ${SAMPLE_INTERVAL}s"
echo "=========================================================="

# 1. Stop any existing Cuttlefish instances
echo "[1/4] Stopping active instances and clearing sockets..."
"$SCRIPT_DIR/stop_cuttlefish.sh" >/dev/null 2>&1 || true

if [[ "$CLEAN_BOOT" == true ]]; then
    echo "[2/4] Wiping runtime overlay and caches for 100% clean factory boot..."
    rm -rf "$PRODUCT_OUT"/cuttlefish_assembly* \
           "$PRODUCT_OUT"/cuttlefish_runtime* \
           "$HOME"/cuttlefish_runtime* \
           /tmp/cf_avd_* 2>/dev/null || true
    EXTRA_FLAGS=("--noresume")
else
    echo "[2/4] Preserving existing data partition (--resume)..."
    EXTRA_FLAGS=("--resume=true")
fi

echo "[3/4] Launching Cuttlefish VM (4 Cores, 4GB RAM, SwiftShader)..."
cd "$PRODUCT_OUT"
launch_cvd \
    --daemon \
    --cpus=4 \
    --memory_mb=4096 \
    --start_webrtc=true \
    --gpu_mode=guest_swiftshader \
    --system_image_dir="$PRODUCT_OUT" \
    "${EXTRA_FLAGS[@]}"

# Wait for ADB connection
echo "[4/4] Connecting ADB (127.0.0.1:6520) and waiting for boot completion..."
BOOT_START_TIME=$(date +%s)
adb connect 127.0.0.1:6520 >/dev/null 2>&1 || true

while true; do
    BOOT_STATUS="$(adb -s 127.0.0.1:6520 shell getprop sys.boot_completed 2>/dev/null || echo 0)"
    BOOT_STATUS="${BOOT_STATUS//$'\r'/}"
    if [[ "$BOOT_STATUS" == "1" ]]; then
        break
    fi
    sleep 2
    adb connect 127.0.0.1:6520 >/dev/null 2>&1 || true
done
BOOT_END_TIME=$(date +%s)
BOOT_ELAPSED=$(( BOOT_END_TIME - BOOT_START_TIME ))

echo
echo "=========================================================="
echo " [SUCCESS] TontOS boot completed in ${BOOT_ELAPSED}s!"
echo " WebRTC GUI: https://localhost:8443"
echo "=========================================================="

# Start background logcat
adb -s 127.0.0.1:6520 logcat -v threadtime > "$LOGCAT_LOG" 2>&1 &
LOGCAT_PID=$!

cleanup() {
    echo -e "\n[*] Terminating performance monitor and flushing logs..."
    if kill -0 "$LOGCAT_PID" 2>/dev/null; then
        kill "$LOGCAT_PID" 2>/dev/null || true
    fi
    echo "[+] System usage log saved to: $USAGE_LOG"
    echo "[+] System logcat log saved to: $LOGCAT_LOG"
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# Gather system baseline
{
    echo "=========================================================="
    echo " TontOS System Performance Baseline"
    echo " Date: $(date -R)"
    echo " Boot Time: ${BOOT_ELAPSED} seconds"
    echo " OS Release: $(adb -s 127.0.0.1:6520 shell getprop ro.build.version.release)"
    echo " Build ID:   $(adb -s 127.0.0.1:6520 shell getprop ro.build.id)"
    echo " Model:      $(adb -s 127.0.0.1:6520 shell getprop ro.product.model)"
    echo " Kernel:     $(adb -s 127.0.0.1:6520 shell uname -r)"
    echo " SELinux:    $(adb -s 127.0.0.1:6520 shell getenforce)"
    echo "=========================================================="
    echo
} | tee -a "$USAGE_LOG"

echo "Streaming live metrics (Press Ctrl+C to stop)..."
echo "----------------------------------------------------------------------------------"
printf "%-8s | %-12s | %-10s | %-12s | %-12s | %s\n" "TIME" "RAM USED" "ZRAM" "LAUNCHER" "SYS_SERVER" "TOP CPU"
echo "----------------------------------------------------------------------------------"

TOTAL_SAMPLES=0
START_MONITOR=$(date +%s)

while true; do
    TIMESTAMP_NOW="$(date +'%H:%M:%S')"
    
    # 1. Memory stats
    MEMINFO="$(adb -s 127.0.0.1:6520 shell cat /proc/meminfo 2>/dev/null || true)"
    MEM_TOTAL_KB=$(echo "$MEMINFO" | grep MemTotal | awk '{print $2}' || echo 0)
    MEM_AVAIL_KB=$(echo "$MEMINFO" | grep MemAvailable | awk '{print $2}' || echo 0)
    SWAP_TOTAL_KB=$(echo "$MEMINFO" | grep SwapTotal | awk '{print $2}' || echo 0)
    SWAP_FREE_KB=$(echo "$MEMINFO" | grep SwapFree | awk '{print $2}' || echo 0)
    
    if [[ -n "$MEM_TOTAL_KB" && -n "$MEM_AVAIL_KB" && "$MEM_TOTAL_KB" -gt 0 ]]; then
        MEM_USED_MB=$(( (MEM_TOTAL_KB - MEM_AVAIL_KB) / 1024 ))
        MEM_TOTAL_MB=$(( MEM_TOTAL_KB / 1024 ))
        RAM_STR="${MEM_USED_MB}M/${MEM_TOTAL_MB}M"
    else
        RAM_STR="N/A"
    fi
    
    if [[ -n "$SWAP_TOTAL_KB" && "$SWAP_TOTAL_KB" -gt 0 ]]; then
        SWAP_USED_MB=$(( (SWAP_TOTAL_KB - SWAP_FREE_KB) / 1024 ))
        ZRAM_STR="${SWAP_USED_MB}M"
    else
        ZRAM_STR="0M"
    fi

    # 2. Key Process Memory
    LAUNCHER_RSS="$(adb -s 127.0.0.1:6520 shell pidof org.tontos.launcher 2>/dev/null || true)"
    if [[ -n "$LAUNCHER_RSS" ]]; then
        LAUNCHER_MEM_KB="$(adb -s 127.0.0.1:6520 shell cat /proc/$LAUNCHER_RSS/statm 2>/dev/null | awk '{print $2*4}' || echo 0)"
        LAUNCHER_STR="$(( LAUNCHER_MEM_KB / 1024 ))MB"
    else
        LAUNCHER_STR="Inactive"
    fi

    SYS_SERVER_PID="$(adb -s 127.0.0.1:6520 shell pidof system_server 2>/dev/null || true)"
    if [[ -n "$SYS_SERVER_PID" ]]; then
        SYS_MEM_KB="$(adb -s 127.0.0.1:6520 shell cat /proc/$SYS_SERVER_PID/statm 2>/dev/null | awk '{print $2*4}' || echo 0)"
        SYS_SERVER_STR="$(( SYS_MEM_KB / 1024 ))MB"
    else
        SYS_SERVER_STR="N/A"
    fi

    # 3. Top process by CPU
    TOP_CPU="$(adb -s 127.0.0.1:6520 shell top -b -n 1 -m 5 2>/dev/null | sed -n '5p' | awk '{print $9"% " $12}' || echo 'N/A')"
    
    # 4. Formatted Terminal Output
    printf "%-8s | %-12s | %-10s | %-12s | %-12s | %s\n" \
        "$TIMESTAMP_NOW" "$RAM_STR" "$ZRAM_STR" "$LAUNCHER_STR" "$SYS_SERVER_STR" "$TOP_CPU"
    
    # 5. Append structured entry to log
    echo "[$TIMESTAMP_NOW] RAM: $RAM_STR | ZRAM: $ZRAM_STR | Launcher: $LAUNCHER_STR | SystemServer: $SYS_SERVER_STR | Top: $TOP_CPU" >> "$USAGE_LOG"

    TOTAL_SAMPLES=$(( TOTAL_SAMPLES + 1 ))

    if [[ "$MONITOR_DURATION" -gt 0 ]]; then
        NOW=$(date +%s)
        if (( NOW - START_MONITOR >= MONITOR_DURATION )); then
            echo "[*] Reached monitoring duration limit (${MONITOR_DURATION}s)."
            break
        fi
    fi

    sleep "$SAMPLE_INTERVAL"
done
