#!/usr/bin/env bash
# ==============================================================================
# TontOS Docker Build Helper (Alpine)
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

echo "=== TontOS Docker ROM Build Helper ==="
echo "Dockerfile:    $DOCKERFILE"
echo "Target Image:  $IMAGE_NAME"
echo "AOSP Source:   $AOSP_DIR"
echo "CCache Dir:    $CCACHE_DIR"
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
echo "Launching compilation container..."
TTY_FLAGS=()
if [[ -t 0 && -t 1 ]]; then
    TTY_FLAGS=("-it")
fi

if [[ $# -eq 0 ]]; then
    # Interactive shell by default when run in terminal
    docker run --rm "${TTY_FLAGS[@]}" \
        --net=host \
        -v "$AOSP_DIR:/home/tontos/aosp" \
        -v "$CCACHE_DIR:/home/tontos/.ccache" \
        -w "/home/tontos/aosp" \
        "$IMAGE_NAME" \
        /bin/bash -c "source build/envsetup.sh && lunch tontos_cf_x86_64-bp4a-userdebug && SOONG_JOBS=1 m -j1 droid"
else
    # Execute user arguments
    docker run --rm "${TTY_FLAGS[@]}" \
        --net=host \
        -v "$AOSP_DIR:/home/tontos/aosp" \
        -v "$CCACHE_DIR:/home/tontos/.ccache" \
        -w "/home/tontos/aosp" \
        "$IMAGE_NAME" \
        "$@"
fi
