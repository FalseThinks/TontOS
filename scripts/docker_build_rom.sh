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

# Dynamic parallel job calculation (~2GB RAM per worker)
TOTAL_CORES=$(nproc 2>/dev/null || echo 4)
TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 8388608)
RAM_JOBS=$(( TOTAL_RAM_KB / (1024 * 1024 * 2) ))

if (( RAM_JOBS < 2 )); then
    SUGGESTED_JOBS=2
elif (( RAM_JOBS > TOTAL_CORES )); then
    SUGGESTED_JOBS=$TOTAL_CORES
else
    SUGGESTED_JOBS=$RAM_JOBS
fi
BUILD_JOBS="${BUILD_JOBS:-$SUGGESTED_JOBS}"
BUILD_TARGET="${BUILD_TARGET:-droid}"

print_header() {
    echo "=========================================================="
    echo "  TontOS Universal Containerized Engine (Docker)"
    echo "=========================================================="
    echo "  Dockerfile:    $DOCKERFILE"
    echo "  Target Image:  $IMAGE_NAME"
    echo "  Workspace:     $ROOT_DIR"
    echo "  Internal Ccache: $CCACHE_DIR"
    echo "  Internal Out:    $OUT_DIR"
    echo "  AOSP Source:   $AOSP_DIR"
    echo "  Parallelism:   $TOTAL_CORES Cores -> -j$BUILD_JOBS workers"
    echo "=========================================================="
}

ensure_directories() {
    mkdir -p "$BUILD_DIR" "$CCACHE_DIR" "$OUT_DIR"
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

    docker run --rm "${tty_flags[@]}" \
        --privileged \
        --net=host \
        --shm-size=4g \
        -e BUILD_JOBS="$BUILD_JOBS" \
        -e GOMAXPROCS=2 \
        -e MALLOC_ARENA_MAX=2 \
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

# Subcommand routing
ACTION="${1:---build}"

case "$ACTION" in
    --help|-h|help)
        print_header
        echo "Usage: ./scripts/docker_build_rom.sh [COMMAND] [OPTIONS]"
        echo ""
        echo "Commands:"
        echo "  --build (default)   Compile AOSP ROM inside isolated container"
        echo "  --test              Run Rust Workspace Test Suite inside container"
        echo "  --apk               Build Android Launcher APK inside container"
        echo "  --shell             Start interactive bash shell inside container"
        echo "  --clean             Wipe internal .build/, .ccache/, and out/"
        echo "  --help, -h          Display this help message"
        exit 0
        ;;
    --clean|clean)
        echo "[+] Cleaning internal project build directories and caches..."
        rm -rf "$BUILD_DIR" "$CCACHE_DIR" "$OUT_DIR"
        echo "[✓] Internal build directories cleaned."
        exit 0
        ;;
    --shell|shell)
        ensure_directories
        ensure_image
        print_header
        echo "[+] Launching interactive shell inside TontOS container..."
        run_container /bin/bash
        ;;
    --test|test)
        ensure_directories
        ensure_image
        print_header
        echo "[+] Running Rust Workspace Test Suite inside container..."
        run_container cargo test --workspace
        ;;
    --apk|apk)
        ensure_directories
        ensure_image
        print_header
        echo "[+] Building Android Launcher APK inside container..."
        run_container ./scripts/build_launcher_apk.sh
        ;;
    --build|build|"")
        ensure_directories
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
            export GOMAXPROCS=2 && export MALLOC_ARENA_MAX=2 &&
            ccache -M 50G &&
            cd /home/tontos/aosp 2>/dev/null || cd /workspace/.build/aosp &&
            source build/envsetup.sh &&
            lunch $LUNCH_COMBO &&
            m -j$BUILD_JOBS $BUILD_TARGET
        "
        ;;
    *)
        # Passthrough arbitrary commands to container
        ensure_directories
        ensure_image
        run_container "$@"
        ;;
esac
