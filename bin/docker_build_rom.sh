#!/usr/bin/env bash
# ==============================================================================
# TontOS Universal Containerized Build & Execution Engine (Docker)
# Self-contained: toolchains, caches (.ccache/), and outputs (out/) kept in-repo.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

DOCKERFILE="${DOCKERFILE:-$ROOT_DIR/Dockerfile}"
IMAGE_NAME="${IMAGE_NAME:-tontos-builder:latest}"
BUILD_DIR="${BUILD_DIR:-$ROOT_DIR/.build}"
CCACHE_DIR="${CCACHE_DIR:-$ROOT_DIR/.ccache}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/out}"
AOSP_DIR="${AOSP_DIR:-$ROOT_DIR/.build/aosp}"
[[ ! -d "$AOSP_DIR" && -d "${AOSP_DIR_HOST:-$HOME/aosp/tontos}" ]] && AOSP_DIR="${AOSP_DIR_HOST:-$HOME/aosp/tontos}"

# Load memory strategy and concurrency profile
source "$SCRIPT_DIR/helpers/memory_profile.sh" "$@"
BUILD_TARGET="${BUILD_TARGET:-droid}"

print_header() {
    echo "=========================================================="
    echo "  TontOS Containerized Build Engine (Docker)"
    echo "=========================================================="
    echo "  Image:       $IMAGE_NAME"
    echo "  Out:         $OUT_DIR"
    echo "  AOSP:        $AOSP_DIR"
    echo "  RAM Profile: $PROFILE_LABEL (-j$BUILD_JOBS)"
    echo "=========================================================="
}

ensure_setup() {
    mkdir -p "$BUILD_DIR" "$CCACHE_DIR" "$OUT_DIR"
    if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
        echo "[+] Building Docker image $IMAGE_NAME..."
        docker build -t "$IMAGE_NAME" -f "$DOCKERFILE" "$ROOT_DIR"
    fi
}

run_container() {
    local tty_flags=()
    [[ -t 0 && -t 1 && -z "${CI:-}" && -z "${GITHUB_ACTIONS:-}" ]] && tty_flags=("-it")

    local aosp_mount=("-v" "$OUT_DIR:/workspace/out" "-v" "$ROOT_DIR:$ROOT_DIR")
    if [[ -d "$AOSP_DIR" && "$AOSP_DIR" != "$ROOT_DIR"* ]]; then
        aosp_mount=("-v" "$AOSP_DIR:/home/tontos/aosp" "-v" "$AOSP_DIR:$AOSP_DIR" "-v" "$OUT_DIR:/home/tontos/aosp/out" "-v" "$ROOT_DIR:$ROOT_DIR")
    fi

    local sdk_mount=()
    local host_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
    [[ -d "$host_sdk" ]] && sdk_mount=("-v" "$host_sdk:/home/tontos/Android/Sdk:ro")

    docker run --rm "${tty_flags[@]}" \
        --privileged --net=host --oom-score-adj=-500 \
        --shm-size="$SHM_SIZE" \
        -e BUILD_JOBS="$BUILD_JOBS" \
        -e GOMAXPROCS="$GOMAXPROCS_VAL" \
        -e MALLOC_ARENA_MAX="$MALLOC_ARENA_MAX_VAL" \
        -e _JAVA_OPTIONS="-Xmx$JAVA_XMX" \
        -e JAVA_OPTS="-Xmx$JAVA_XMX" \
        -e GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx$JAVA_XMX" \
        -e KOTLIN_OPTS="-J-Xmx$JAVA_XMX" \
        -e USE_CCACHE=1 -e CCACHE_DIR=/workspace/.ccache \
        -v "$ROOT_DIR:/workspace" -v "$CCACHE_DIR:/workspace/.ccache" \
        "${aosp_mount[@]}" "${sdk_mount[@]}" \
        -w "/workspace" \
        "$IMAGE_NAME" \
        "$@"
}

sync_vendor_tree() {
    if [[ -d "$AOSP_DIR/vendor/tontos" ]]; then
        echo "[+] Syncing platform configs, blueprints and launcher APK into AOSP vendor..."
        mkdir -p "$AOSP_DIR/vendor/tontos/products" "$AOSP_DIR/vendor/tontos/apps/prebuilts"
        cp -f "$ROOT_DIR/platform/products/"* "$AOSP_DIR/vendor/tontos/products/" 2>/dev/null || true
        cp -f "$ROOT_DIR/platform/apps/prebuilts/Android.bp" "$AOSP_DIR/vendor/tontos/apps/prebuilts/" 2>/dev/null || true
        [[ -d "$ROOT_DIR/apps" ]] && cp -f "$ROOT_DIR/apps/"*.apk "$AOSP_DIR/vendor/tontos/apps/prebuilts/" 2>/dev/null || true
        cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$AOSP_DIR/vendor/tontos/apps/prebuilts/" 2>/dev/null || true
        cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$AOSP_DIR/vendor/tontos/apps/" 2>/dev/null || true
    fi
}

ACTION="${1:-build}"
case "$ACTION" in
    --help|-h|help)
        print_header
        echo "Usage: ./bin/docker_build_rom.sh [COMMAND] [OPTIONS]"
        echo "Commands:"
        echo "  --build (default)   Compile AOSP ROM inside container"
        echo "  --test              Run test suite inside container"
        echo "  --apk               Build Launcher APK inside container"
        echo "  --shell             Start interactive container shell"
        echo "  --clean             Wipe .build/, .ccache/, and out/"
        exit 0
        ;;
    --clean|clean)
        echo "[+] Cleaning build directories and caches..."
        rm -rf "$BUILD_DIR" "$CCACHE_DIR" "$OUT_DIR"
        exit 0
        ;;
esac

ensure_setup
print_header

case "$ACTION" in
    --shell|shell)
        run_container /bin/bash
        ;;
    --test|test)
        run_container ./bin/run_full_suite.sh --fast
        ;;
    --apk|apk)
        run_container ./bin/build_launcher_apk.sh
        ;;
    --build|build|*)
        if [[ ! -d "$AOSP_DIR" ]]; then
            echo "[-] Notice: AOSP tree not found at $AOSP_DIR. Running containerized test suite..."
            run_container ./bin/run_full_suite.sh --fast
            exit 0
        fi
        sync_vendor_tree
        LUNCH_COMBO="${LUNCH_COMBO:-tontos_cf_x86_64-trunk_staging-userdebug}"
        echo "[+] Launching parallel AOSP compilation inside container (-j$BUILD_JOBS)..."
        run_container /bin/bash -c "
            ccache -M 50G &&
            if [ -d /home/tontos/aosp ]; then cd /home/tontos/aosp; else cd /workspace/.build/aosp; fi &&
            source build/envsetup.sh &&
            lunch $LUNCH_COMBO &&
            m -j$BUILD_JOBS $BUILD_TARGET
        "
        ;;
esac
