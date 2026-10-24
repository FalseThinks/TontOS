#!/usr/bin/env bash
# ==============================================================================
# TontOS Unified Clean Installer, Builder & Real-Time Performance Monitor
# Orchestrates Host Swap, Workspace Testing, AOSP Build & Cuttlefish VM Boot.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
HELPERS_DIR="$SCRIPT_DIR/helpers"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
if [[ -f "$ROOT_DIR/out/target/product/vsoc_x86_64_only/system.img" ]]; then
    PRODUCT_OUT="$ROOT_DIR/out/target/product/vsoc_x86_64_only"
    HOST_OUT="$ROOT_DIR/out/host/linux-x86"
    if [[ ! -d "$HOST_OUT/bin" && -d "$AOSP_DIR/out/host/linux-x86/bin" ]]; then
        HOST_OUT="$AOSP_DIR/out/host/linux-x86"
    fi
else
    PRODUCT_OUT="$AOSP_DIR/out/target/product/vsoc_x86_64_only"
    HOST_OUT="$AOSP_DIR/out/host/linux-x86"
fi
LOGS_DIR="$ROOT_DIR/logs"

# Default configuration
DO_BUILD=false
BUILD_FROM_SCRATCH=false
RUN_TESTS=true
SETUP_SWAP=true
USE_DOCKER=false
CLEAN_VM_BOOT=true
RUN_CUTTLEFISH=true
EXPLICIT_SKIP_BUILD=false
SAMPLE_INTERVAL=5
MONITOR_DURATION=0 # 0 = infinite until Ctrl+C
BUILD_JOBS=""
VERBOSE=false

print_help() {
    cat << EOF
Usage: ./bin/clean_install_and_monitor.sh [OPTIONS]

Unified Orchestrator: Swap Pre-flight -> Tests -> Build -> Cuttlefish Boot -> Monitoring

Build Options:
  --build              Compile TontOS Cuttlefish before booting
  --docker             Compile AOSP inside isolated Docker container (zero host setup)
  --from-scratch       Clean AOSP output artifacts and rebuild entirely from zero
  --incremental        Trigger incremental AOSP build (preserves out/ state)
  --skip-build         Skip AOSP build and boot existing compiled images (default)

Quality & Memory Options:
  --available          Use currently available free RAM (safe profile, avoids thrashing)
  --total              Use full physical hardware RAM (maximum capacity profile)
  --ram=<4|8|16|32>    Explicitly force a RAM tier (4GB, 8GB, 16GB, or 32GB)
  --run-tests          Run full test suite (Rust, Java, APK, syntax) before build (default)
  --skip-tests         Bypass pre-build test suite
  --setup-swap         Ensure tiered zRAM + 32GB swapfile is active before build (default)
  --skip-swap          Bypass automated swap & zRAM verification

Cuttlefish & Monitoring Options:
  --clean              Factory wipe VM runtime and data partition (default)
  --no-clean           Resume existing VM state without wiping data (--resume)
  --skip-cuttlefish    Stop after tests/build; do not boot Cuttlefish VM
  --interval=SEC       Metric sampling interval in seconds (default: 5)
  --duration=SEC       Stop monitoring after SEC seconds (default: infinite)
  --verbose, -v        Display live boot progress and kernel logs
  --help, -h           Show this help message
EOF
    exit 0
}

for arg in "$@"; do
    case "$arg" in
        --verbose|-v)
            VERBOSE=true
            ;;
        --build)
            DO_BUILD=true
            ;;
        --docker)
            USE_DOCKER=true
            DO_BUILD=true
            SETUP_SWAP=false
            ;;
        --from-scratch)
            DO_BUILD=true
            BUILD_FROM_SCRATCH=true
            ;;
        --incremental)
            DO_BUILD=true
            BUILD_FROM_SCRATCH=false
            ;;
        --skip-build|--no-build)
            DO_BUILD=false
            EXPLICIT_SKIP_BUILD=true
            ;;
        --run-tests)
            RUN_TESTS=true
            ;;
        --skip-tests)
            RUN_TESTS=false
            ;;
        --setup-swap)
            SETUP_SWAP=true
            ;;
        --skip-swap)
            SETUP_SWAP=false
            ;;
        --clean)
            CLEAN_VM_BOOT=true
            ;;
        --no-clean)
            CLEAN_VM_BOOT=false
            ;;
        --skip-cuttlefish|--no-cuttlefish)
            RUN_CUTTLEFISH=false
            ;;
        --interval=*)
            SAMPLE_INTERVAL="${arg#*=}"
            ;;
        --duration=*)
            MONITOR_DURATION="${arg#*=}"
            ;;
        --jobs=*)
            BUILD_JOBS="-j${arg#*=}"
            ;;
        -j*)
            BUILD_JOBS="$arg"
            ;;
        --available|--mem-mode=available|--mode=available)
            # Memory strategy handled by memory_profile.sh
            ;;
        --total|--force-total|--mem-mode=total|--mode=total)
            # Memory strategy handled by memory_profile.sh
            ;;
        --ram=*|--ram)
            # RAM tier override handled by memory_profile.sh
            ;;
        --help|-h)
            print_help
            ;;
        *)
            echo "[-] Unknown option: $arg" >&2
            echo "    Run with --help for usage." >&2
            exit 1
            ;;
    esac
done

echo "=========================================================="
echo "  TontOS Unified Clean-Install & Lifecycle Orchestrator"
echo "=========================================================="
echo "  Setup Swap & zRAM: $SETUP_SWAP"
echo "  Execute Tests:     $RUN_TESTS"
echo "  Build AOSP:        $DO_BUILD (From scratch: $BUILD_FROM_SCRATCH)"
echo "  Launch Cuttlefish: $RUN_CUTTLEFISH (Clean boot: $CLEAN_VM_BOOT)"
echo "  AOSP Directory:    $AOSP_DIR"
echo "=========================================================="

# ------------------------------------------------------------------------------
# Phase 1: Host Swap & Memory Pre-flight
# ------------------------------------------------------------------------------
if [[ "$SETUP_SWAP" == true ]]; then
    echo ""
    echo "--> [1/5] Verifying Host Swap & zRAM Configuration..."
    TOTAL_SWAP_KB="$(grep SwapTotal /proc/meminfo | awk '{print $2}')"
    # If swap is under 20GB, ensure swapfile + zRAM are configured
    if [[ "$TOTAL_SWAP_KB" -lt 20971520 ]] || ! grep -q "/swapfile_aosp" /proc/swaps 2>/dev/null; then
        echo "    Configuring tiered memory (zRAM + 32GB NVMe swapfile)..."
        "$HELPERS_DIR/setup_host_swap.sh" setup || {
            echo "[!] Warning: Sudo needed for swap configuration. Proceeding with existing memory."
        }
    else
        echo "    [PASS] Tiered host swap is active (${TOTAL_SWAP_KB} kB available)."
    fi
fi

# ------------------------------------------------------------------------------
# Phase 2: System Quality & Test Gates
# ------------------------------------------------------------------------------
if [[ "$RUN_TESTS" == true ]]; then
    echo ""
    echo "--> [2/5] Running Unified System Test Suite..."
    "$SCRIPT_DIR/run_all_tests.sh"
fi

# ------------------------------------------------------------------------------
# Phase 3: Android Launcher Compilation & AOSP Build
# ------------------------------------------------------------------------------
echo ""
echo "--> [3/5] Building TontOS Launcher APK..."
"$SCRIPT_DIR/build_launcher_apk.sh"

if [[ -d "$AOSP_DIR/vendor/tontos" ]]; then
    echo "    Syncing latest platform makefiles, blueprints and APK to AOSP vendor tree..."
    mkdir -p "$AOSP_DIR/vendor/tontos/products" "$AOSP_DIR/vendor/tontos/apps/prebuilts"
    cp -f "$ROOT_DIR/platform/products/"*.mk "$AOSP_DIR/vendor/tontos/products/" 2>/dev/null || true
    cp -f "$ROOT_DIR/platform/apps/prebuilts/Android.bp" "$AOSP_DIR/vendor/tontos/apps/prebuilts/" 2>/dev/null || true
    cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$AOSP_DIR/vendor/tontos/apps/" 2>/dev/null || true
fi

has_compiled_images() {
    [[ -f "$PRODUCT_OUT/boot.img" && -f "$PRODUCT_OUT/system.img" ]]
}

if [[ "$DO_BUILD" == true ]] || { [[ "$RUN_CUTTLEFISH" == true ]] && ! has_compiled_images && [[ "$EXPLICIT_SKIP_BUILD" != true ]]; }; then
    echo ""
    if ! has_compiled_images && [[ "$DO_BUILD" != true ]]; then
        echo "[!] Target boot.img/system.img not found in $PRODUCT_OUT."
        echo "--> Auto-initiating AOSP Target Build (tontos_cf_x86_64)..."
    else
        echo "--> Compiling AOSP Target (tontos_cf_x86_64)..."
    fi

    if [[ ! -d "$AOSP_DIR" ]]; then
        echo "[-] ERROR: AOSP directory not found at $AOSP_DIR" >&2
        exit 1
    fi

    if [[ "$BUILD_FROM_SCRATCH" == true ]]; then
        echo "    Wiping previous output for 100% clean rebuild..."
        rm -rf "$PRODUCT_OUT"
    fi

    if [[ "$USE_DOCKER" == true ]]; then
        echo "    Delegating compilation to isolated Docker container ($ROOT_DIR/bin/docker_build_rom.sh)..."
        "$SCRIPT_DIR/docker_build_rom.sh" --build "$@"
        echo "    [PASS] Containerized AOSP build completed successfully."
    else
        echo "    Determining hardware & available memory profile..."
        source "$HELPERS_DIR/memory_profile.sh" "$@"
        echo "    Invoking Soong build in $AOSP_DIR ($STRATEGY_DESC, -j$BUILD_JOBS)..."
        (
            cd "$AOSP_DIR"
            unset OUT_DIR
            export USE_CCACHE=1
            export CCACHE_DIR="$ROOT_DIR/.ccache"
            export GOMAXPROCS=$GOMAXPROCS_VAL
            export MALLOC_ARENA_MAX=$MALLOC_ARENA_MAX_VAL
            export _JAVA_OPTIONS="-Xmx$JAVA_XMX"
            source build/envsetup.sh
            lunch tontos_cf_x86_64-trunk_staging-userdebug
            m -j$BUILD_JOBS
        )
        echo "    [PASS] AOSP build completed successfully."
    fi
fi

if [[ "$RUN_CUTTLEFISH" != true ]]; then
    echo ""
    echo "=========================================================="
    echo " [SUCCESS] Pipeline complete. (--skip-cuttlefish specified)"
    echo "=========================================================="
    exit 0
fi

# ------------------------------------------------------------------------------
# Phase 4: Prepare Cuttlefish Environment
# ------------------------------------------------------------------------------
if ! has_compiled_images; then
    echo ""
    echo "[-] ERROR: Compiled ROM images (boot.img, system.img) not found at: $PRODUCT_OUT" >&2
    if [[ "$EXPLICIT_SKIP_BUILD" == true ]]; then
        echo "    You specified --skip-build, but Cuttlefish VM requires boot.img to boot." >&2
        echo "    Point to prebuilts with AOSP_DIR=<path> or compile once with:" >&2
        echo "    ./scripts/clean_install_and_monitor.sh --build" >&2
    else
        echo "    Run with --build to compile first." >&2
    fi
    exit 1
fi

mkdir -p "$LOGS_DIR"
TIMESTAMP="$(date +'%Y%m%d_%H%M%S')"
USAGE_LOG="$LOGS_DIR/tontos_usage_${TIMESTAMP}.log"
LOGCAT_LOG="$LOGS_DIR/tontos_logcat_${TIMESTAMP}.log"

export ANDROID_PRODUCT_OUT="$PRODUCT_OUT"
export ANDROID_HOST_OUT="$HOST_OUT"
export PATH="$HOST_OUT/bin:$PATH"

echo ""
echo "--> [4/5] Preparing Cuttlefish Environment..."
"$HELPERS_DIR/stop_cuttlefish.sh" >/dev/null 2>&1 || true

if [[ -f "$PRODUCT_OUT/super.img" ]] && file "$PRODUCT_OUT/super.img" | grep -q "Android sparse image"; then
    echo "    Converting super.img from sparse to raw for Cuttlefish crosvm composite disk..."
    "$HOST_OUT/bin/simg2img" "$PRODUCT_OUT/super.img" "$PRODUCT_OUT/super.raw.img"
    mv -f "$PRODUCT_OUT/super.raw.img" "$PRODUCT_OUT/super.img"
fi

if [[ "$CLEAN_VM_BOOT" == true ]]; then
    echo "    Wiping runtime overlays for clean factory boot..."
    rm -rf "$PRODUCT_OUT"/cuttlefish_assembly* \
           "$PRODUCT_OUT"/cuttlefish_runtime* \
           "$HOME"/cuttlefish \
           "$HOME"/cuttlefish_runtime* \
           /tmp/cf_avd_* \
           /tmp/cf_env_* 2>/dev/null || true
    EXTRA_FLAGS=("--noresume")
else
    echo "    Preserving user data partition (--resume)..."
    EXTRA_FLAGS=("--resume=true")
fi

# ------------------------------------------------------------------------------
# Phase 5: Launch & Live Performance Monitoring
# ------------------------------------------------------------------------------
echo ""
echo "--> [5/5] Booting Cuttlefish VM & Launching Metrics Monitor..."
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

echo "    Waiting for ADB boot completion (127.0.0.1:6520)..."
BOOT_START_TIME=$(date +%s)
adb connect 127.0.0.1:6520 >/dev/null 2>&1 || true

while true; do
    BOOT_STATUS="$(adb -s 127.0.0.1:6520 shell getprop sys.boot_completed 2>/dev/null || echo 0)"
    BOOT_STATUS="${BOOT_STATUS//$'\r'/}"
    if [[ "$BOOT_STATUS" == "1" ]]; then
        break
    fi

    # Check for dm-verity or kernel panic failure
    if grep -q "dm-verity device corrupted" "$HOME/cuttlefish/instances/cvd-1/logs/kernel.log" 2>/dev/null; then
        echo "" >&2
        echo "[-] FATAL: Guest kernel detected corrupted dm-verity partition table." >&2
        echo "    Check logs at $HOME/cuttlefish/instances/cvd-1/logs/kernel.log" >&2
        "$HELPERS_DIR/stop_cuttlefish.sh" >/dev/null 2>&1 || true
        exit 1
    fi

    if [[ "$VERBOSE" == true ]]; then
        STAGE="$(grep -E "init: |Android Runtime|SurfaceFlinger|Starting phase" "$HOME/cuttlefish/instances/cvd-1/logs/kernel.log" 2>/dev/null | tail -n 1 || true)"
        if [[ -n "$STAGE" ]]; then
            printf "\r    [Boot Stage] %-70s" "${STAGE:0:70}"
        fi
    fi

    sleep 2
    adb connect 127.0.0.1:6520 >/dev/null 2>&1 || true
done
BOOT_END_TIME=$(date +%s)
BOOT_ELAPSED=$(( BOOT_END_TIME - BOOT_START_TIME ))

echo ""
echo "=========================================================="
echo " [SUCCESS] TontOS Minimalist booted in ${BOOT_ELAPSED}s!"
echo " WebRTC GUI:  https://localhost:8443"
echo " Usage Log:   $USAGE_LOG"
echo " Logcat Log:  $LOGCAT_LOG"
echo " Press Ctrl+C to terminate monitoring and capture report."
echo "=========================================================="

adb -s 127.0.0.1:6520 logcat -v threadtime > "$LOGCAT_LOG" 2>&1 &
LOGCAT_PID=$!

cleanup() {
    echo ""
    echo "[!] Stopping monitor and collecting final metrics..."
    kill "$LOGCAT_PID" 2>/dev/null || true
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
