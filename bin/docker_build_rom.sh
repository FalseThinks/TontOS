#!/usr/bin/env bash
# ==============================================================================
# TontOS Universal Containerized Build & Execution Engine (Docker)
# Self-contained: all data, toolchains, caches (.ccache/), and outputs (out/)
# reside inside the repository. Zero host toolchain pollution.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

DOCKERFILE="${DOCKERFILE:-$ROOT_DIR/Dockerfile}"
IMAGE_NAME="${IMAGE_NAME:-tontos-builder:latest}"

# Project-internal directories (all kept inside $ROOT_DIR, ignored by git)
BUILD_DIR="${BUILD_DIR:-$ROOT_DIR/.build}"
CCACHE_DIR="${CCACHE_DIR:-$ROOT_DIR/.ccache}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/out}"
AOSP_DIR="${AOSP_DIR:-$ROOT_DIR/.build/aosp}"

# Allow fallback to external AOSP tree if explicitly provided or present
if [[ ! -d "$AOSP_DIR" ]] && [[ -d "${AOSP_DIR_HOST:-$HOME/aosp/tontos}" ]]; then
    AOSP_DIR="${AOSP_DIR_HOST:-$HOME/aosp/tontos}"
fi

# ==============================================================================
# Dynamic Hardware & Available RAM Profile Engine (4GB / 8GB / 16GB / 32GB)
# ==============================================================================
source "$SCRIPT_DIR/helpers/memory_profile.sh" "$@"
BUILD_TARGET="${BUILD_TARGET:-droid}"

print_header() {
    echo "=========================================================="
    echo "  TontOS Universal Containerized Engine (Docker)"
    echo "=========================================================="
    echo "  Dockerfile:      $DOCKERFILE"
    echo "  Target Image:    $IMAGE_NAME"
    echo "  Workspace:       $ROOT_DIR"
    echo "  Internal Ccache: $CCACHE_DIR"
    echo "  Internal Out:    $OUT_DIR"
    echo "  AOSP Source:     $AOSP_DIR"
    echo "  RAM Profile:     $PROFILE_LABEL"
    echo "  RAM Strategy:    $STRATEGY_DESC"
    echo "  Parallelism:     $TOTAL_CORES Cores -> -j$BUILD_JOBS workers"
    echo "  JVM Heap:        -Xmx$JAVA_XMX | Shared Memory: $SHM_SIZE"
    echo "=========================================================="
}

ensure_directories() {
    mkdir -p "$BUILD_DIR" "$CCACHE_DIR" "$OUT_DIR"
}

ensure_memory_and_swap() {
    local total_ram_kb
    total_ram_kb=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 8388608)
    local total_swap_kb
    total_swap_kb=$(grep SwapTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 0)
    local required_swap_kb=$(( MIN_SWAP_REQUIRED_GB * 1024 * 1024 ))

    # AOSP Baklava requires generous virtual memory during heavy R8 & LLVM link phases.
    if (( total_swap_kb < required_swap_kb )); then
        echo "[+] Active swap ($(( total_swap_kb / 1024 )) MB) is below recommended ($(( required_swap_kb / 1024 )) MB) for $PROFILE_LABEL."
        echo "[+] Auto-configuring tiered NVMe swap to prevent systemd-oomd SIGKILL..."
        if command -v sudo >/dev/null 2>&1; then
            sudo "$SCRIPT_DIR/helpers/setup_host_swap.sh" setup
        else
            echo "[!] Warning: sudo unavailable. Build may run into memory pressure."
        fi
    fi
}

ensure_image() {
    if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
        echo "[+] Building Docker image: $IMAGE_NAME from $DOCKERFILE..."
        docker build -t "$IMAGE_NAME" -f "$DOCKERFILE" "$ROOT_DIR"
    fi
}

run_container() {
    local cmd=("$@")
    local tty_flags=()
    if [[ -t 0 && -t 1 && -z "${CI:-}" && -z "${GITHUB_ACTIONS:-}" ]]; then
        tty_flags=("-it")
    fi

    local aosp_mount=()
    if [[ -d "$AOSP_DIR" ]] && [[ "$AOSP_DIR" != "$ROOT_DIR"* ]]; then
        aosp_mount=("-v" "$AOSP_DIR:/home/tontos/aosp" "-v" "$OUT_DIR:/home/tontos/aosp/out")
    else
        aosp_mount=("-v" "$OUT_DIR:/workspace/out")
    fi

    local host_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
    local sdk_mount=()
    if [[ -d "$host_sdk" ]]; then
        sdk_mount=("-v" "$host_sdk:/home/tontos/Android/Sdk:ro")
    fi

    local platform_flags=()
    if [[ "$(uname -s)" != *"MINGW"* && "$(uname -s)" != *"MSYS"* && "$(uname -s)" != *"CYGWIN"* ]]; then
        platform_flags+=("--net=host" "--oom-score-adj=-500")
    fi

    docker run --rm "${tty_flags[@]}" \
        --privileged \
        "${platform_flags[@]}" \
        --shm-size="$SHM_SIZE" \
        -e BUILD_JOBS="$BUILD_JOBS" \
        -e GOMAXPROCS="$GOMAXPROCS_VAL" \
        -e MALLOC_ARENA_MAX="$MALLOC_ARENA_MAX_VAL" \
        -e _JAVA_OPTIONS="-Xmx$JAVA_XMX" \
        -e USE_CCACHE=1 \
        -e CCACHE_DIR=/workspace/.ccache \
        -v "$ROOT_DIR:/workspace" \
        -v "$CCACHE_DIR:/workspace/.ccache" \
        "${aosp_mount[@]}" \
        "${sdk_mount[@]}" \
        -w "/workspace" \
        "$IMAGE_NAME" \
        "${cmd[@]}"
}

# Subcommand & Argument routing
ACTION=""
PASSTHROUGH_ARGS=()
for arg in "$@"; do
    case "$arg" in
        --help|-h|help)
            ACTION="help"
            ;;
        --clean|clean)
            ACTION="clean"
            ;;
        --shell|shell)
            ACTION="shell"
            ;;
        --test|test)
            ACTION="test"
            ;;
        --apk|apk)
            ACTION="apk"
            ;;
        --build|build)
            ACTION="build"
            ;;
        --available|--mem-mode=*|--mode=*|--total|--force-total|--ram=*|--ram)
            # Memory options consumed by memory_profile.sh
            ;;
        *)
            PASSTHROUGH_ARGS+=("$arg")
            ;;
    esac
done

if [[ -z "$ACTION" ]]; then
    if (( ${#PASSTHROUGH_ARGS[@]} > 0 )); then
        ACTION="passthrough"
    else
        ACTION="build"
    fi
fi

case "$ACTION" in
    help)
        print_header
        echo "Usage: ./bin/docker_build_rom.sh [COMMAND] [OPTIONS]"
        echo ""
        echo "Commands:"
        echo "  --build (default)   Compile AOSP ROM inside isolated container"
        echo "  --test              Run Rust Workspace Test Suite inside container"
        echo "  --apk               Build Android Launcher APK inside container"
        echo "  --shell             Start interactive bash shell inside container"
        echo "  --clean             Wipe internal .build/, .ccache/, and out/"
        echo "  --help, -h          Display this help message"
        echo ""
        echo "Hardware Options:"
        echo "  --available         Adapt to currently available free RAM (safe default)"
        echo "  --total             Adapt to total physical PC RAM capacity"
        echo "  --ram=4|8|16|32     Explicitly enforce RAM profile and concurrency"
        exit 0
        ;;
    clean)
        echo "[+] Cleaning internal project build directories and caches..."
        rm -rf "$BUILD_DIR" "$CCACHE_DIR" "$OUT_DIR"
        if [[ -f "/swapfile_aosp" ]] && command -v sudo >/dev/null 2>&1; then
            echo "[+] Cleaning AOSP swapfile..."
            sudo "$SCRIPT_DIR/helpers/setup_host_swap.sh" clean 2>/dev/null || true
        fi
        echo "[✓] Internal build directories cleaned."
        exit 0
        ;;
    shell)
        ensure_directories
        ensure_image
        print_header
        echo "[+] Launching interactive shell inside TontOS container..."
        run_container /bin/bash
        ;;
    test)
        ensure_directories
        ensure_image
        print_header
        echo "[+] Running Rust Workspace Test Suite inside container..."
        run_container cargo test --workspace
        ;;
    apk)
        ensure_directories
        ensure_image
        print_header
        echo "[+] Building Android Launcher APK inside container..."
        run_container ./bin/build_launcher_apk.sh
        ;;
    build)
        ensure_directories
        ensure_memory_and_swap
        ensure_image
        print_header
        if [[ ! -d "$AOSP_DIR" ]]; then
            echo "[-] Notice: AOSP tree not found at $AOSP_DIR."
            echo "    To build the full AOSP ROM, clone AOSP into .build/aosp or provide AOSP_DIR=/path."
            echo "    Running Rust & APK containerized validation instead..."
            run_container /bin/bash -c "cargo test --workspace"
            exit 0
        fi
        LUNCH_COMBO="${LUNCH_COMBO:-tontos_cf_x86_64-trunk_staging-userdebug}"
        echo "[+] Launching parallel AOSP compilation inside container (-j$BUILD_JOBS)..."
        run_container /bin/bash -c "
            export GOMAXPROCS=$GOMAXPROCS_VAL && export MALLOC_ARENA_MAX=$MALLOC_ARENA_MAX_VAL &&
            export _JAVA_OPTIONS=\"-Xmx$JAVA_XMX\" &&
            ccache -M 50G &&
            cd /home/tontos/aosp 2>/dev/null || cd /workspace/.build/aosp &&
            source build/envsetup.sh &&
            lunch $LUNCH_COMBO &&
            m -j$BUILD_JOBS $BUILD_TARGET
        "
        ;;
    passthrough|*)
        ensure_directories
        ensure_image
        run_container "${PASSTHROUGH_ARGS[@]}"
        ;;
esac
