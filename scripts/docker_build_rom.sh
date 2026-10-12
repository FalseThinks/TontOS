#!/usr/bin/env bash
# ==============================================================================
# TontOS Docker Build Helper (Parallelized AOSP ROM Builder)
# Mounts the local AOSP source tree and runs the compilation inside Docker.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}"
DOCKERFILE="${DOCKERFILE:-$ROOT_DIR/Dockerfile.aosp}"
IMAGE_NAME="${IMAGE_NAME:-tontos-builder:latest}"

# Dynamic parallel job calculation based on hardware specs (Rule: ~2GB RAM / worker)
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
BUILD_TARGET="${BUILD_TARGET:-bootimage systemimage vbmetaimage}"

echo "=== TontOS Docker ROM Build Helper (Parallel High-Performance) ==="
echo "Dockerfile:    $DOCKERFILE"
echo "Target Image:  $IMAGE_NAME"
echo "AOSP Source:   $AOSP_DIR"
echo "CCache Dir:    $CCACHE_DIR (50GB Max)"
echo "Parallelism:   $TOTAL_CORES CPU Cores -> -j$BUILD_JOBS workers"
echo "Build Target:  $BUILD_TARGET"
echo

if [[ ! -d "$AOSP_DIR" ]]; then
    echo "ERROR: AOSP tree not found at $AOSP_DIR" >&2
    echo "Set AOSP_DIR=/path/to/aosp or clone AOSP first." >&2
    exit 1
fi

mkdir -p "$CCACHE_DIR"

# 1. Build Docker image if not already built
if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
    echo "Building Docker image: $IMAGE_NAME from $DOCKERFILE..."
    docker build -t "$IMAGE_NAME" -f "$DOCKERFILE" "$ROOT_DIR"
fi

# 2. Run compilation inside container
echo "Launching compilation container with $BUILD_JOBS parallel threads..."
TTY_FLAGS=()
if [[ -t 0 && -t 1 && -z "${CI:-}" && -z "${GITHUB_ACTIONS:-}" ]]; then
    TTY_FLAGS=("-it")
fi

if [[ $# -eq 0 ]]; then
    # Interactive compilation by default using multi-threaded Ninja & ccache
    docker run --rm "${TTY_FLAGS[@]}" \
        --net=host \
        --shm-size=2g \
        -e BUILD_JOBS="$BUILD_JOBS" \
        -e USE_CCACHE=1 \
        -e CCACHE_DIR=/home/tontos/.ccache \
        -v "$AOSP_DIR:/home/tontos/aosp" \
        -v "$CCACHE_DIR:/home/tontos/.ccache" \
        -w "/home/tontos/aosp" \
        "$IMAGE_NAME" \
        /bin/bash -c "ccache -M 50G && source build/envsetup.sh && lunch tontos_cf_x86_64-bp4a-userdebug && m -j$BUILD_JOBS $BUILD_TARGET"
else
    # Execute user arguments
    docker run --rm "${TTY_FLAGS[@]}" \
        --net=host \
        --shm-size=2g \
        -e BUILD_JOBS="$BUILD_JOBS" \
        -e USE_CCACHE=1 \
        -e CCACHE_DIR=/home/tontos/.ccache \
        -v "$AOSP_DIR:/home/tontos/aosp" \
        -v "$CCACHE_DIR:/home/tontos/.ccache" \
        -w "/home/tontos/aosp" \
        "$IMAGE_NAME" \
        "$@"
fi
