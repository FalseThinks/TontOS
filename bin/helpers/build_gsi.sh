#!/usr/bin/env bash
# ==============================================================================
# TontOS Universal Multi-Architecture GSI (Generic System Image) Build Automation
# Target Architectures:
#   - arm64  (Universal 64-bit ARM smartphones: Snapdragon, Tensor, MediaTek, Exynos)
#   - arm    (Universal 32-bit ARM smartphones: Android Go / entry-level / legacy)
#   - x86_64 (Universal 64-bit x86: Emulators, PCs, Chromebooks ARC, tablets)
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
OUTPUT_ROOT="${OUTPUT_DIR:-$ROOT_DIR/out/gsi}"
CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}"

# Default parameters
ARCH="arm64"
PRODUCT_TYPE="tontos" # "tontos" or "aosp"
CUSTOM_TARGET=""
USE_DOCKER=false
SKIP_APK=false
NO_COMPRESS=false
CLEAN_BUILD=false
DRY_RUN=false

# Dynamic parallel job calculation based on hardware specs (~2GB RAM / worker)
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

show_help() {
    cat <<EOF
Usage: $0 [OPTIONS]

Automated GSI (Generic System Image) build engine for TontOS / Android Treble.

Supported Architectures:
  --arch=arm64       ARM 64-bit universal (Modern smartphones: Snapdragon, MediaTek, Tensor) [DEFAULT]
  --arch=arm         ARM 32-bit universal (Entry-level devices / Android Go / IoT)
  --arch=x86_64      x86 64-bit universal (Emulators, x86 tablets, PCs)
  --arch=all         Build all architectures sequentially (arm64, arm, x86_64)

Options:
  --product=tontos   Native TontOS GSI (with Launcher and bundled FOSS suite) [DEFAULT]
  --product=aosp     Pure AOSP GSI with vendor overlay
  --target=TARGET    Override AOSP lunch target
  --jobs=N, -j N     Number of build threads (default: $BUILD_JOBS)
  --docker           Compile inside isolated Docker container (Dockerfile.aosp)
  --skip-apk         Skip pre-build of TontOSLauncher.apk
  --no-compress      Do not compress final system.img with xz
  --clean            Clean out directory before compiling
  --dry-run          Validate lunch combo and vendor synchronization without executing 'm systemimage'
  --help, -h         Show this help message
EOF
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --arch=*) ARCH="${1#*=}" ;;
        -a) ARCH="$2"; shift ;;
        --product=*) PRODUCT_TYPE="${1#*=}" ;;
        --target=*) CUSTOM_TARGET="${1#*=}" ;;
        --jobs=*) BUILD_JOBS="${1#*=}" ;;
        -j) BUILD_JOBS="$2"; shift ;;
        --docker) USE_DOCKER=true ;;
        --skip-apk) SKIP_APK=true ;;
        --no-compress) NO_COMPRESS=true ;;
        --clean) CLEAN_BUILD=true ;;
        --dry-run) DRY_RUN=true ;;
        --help|-h) show_help ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Run '$0 --help' for available options." >&2
            exit 1
            ;;
    esac
    shift
done

resolve_lunch_target() {
    local target_arch="$1"
    if [[ -n "$CUSTOM_TARGET" ]]; then
        echo "$CUSTOM_TARGET"
        return
    fi

    if [[ "$PRODUCT_TYPE" == "tontos" ]]; then
        case "$target_arch" in
            arm64)  echo "tontos_gsi_arm64-trunk_staging-userdebug" ;;
            arm)    echo "tontos_gsi_arm-trunk_staging-userdebug" ;;
            x86_64) echo "tontos_gsi_x86_64-trunk_staging-userdebug" ;;
            *)
                echo "ERROR: Unsupported architecture: $target_arch" >&2
                exit 1
                ;;
        esac
    else
        case "$target_arch" in
            arm64)  echo "aosp_arm64-trunk_staging-userdebug" ;;
            arm)    echo "aosp_arm-trunk_staging-userdebug" ;;
            x86_64) echo "aosp_x86_64-trunk_staging-userdebug" ;;
            *)
                echo "ERROR: Unsupported architecture: $target_arch" >&2
                exit 1
                ;;
        esac
    fi
}

stage_vendor_files() {
    echo "[*] Synchronizing components and vendor tree into AOSP ($AOSP_DIR)..."
    local vendor_dir="$AOSP_DIR/vendor/tontos"
    mkdir -p "$vendor_dir/apps/TontOSLauncher/src"
    mkdir -p "$vendor_dir/apps/prebuilts"
    mkdir -p "$vendor_dir/products"

    # 1. Launcher APK and source
    if [[ -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" ]]; then
        cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$vendor_dir/apps/TontOSLauncher/TontOSLauncher.apk"
    fi
    if [[ -f "$ROOT_DIR/launcher_apk/AndroidManifest.xml" ]]; then
        cp -f "$ROOT_DIR/launcher_apk/AndroidManifest.xml" "$vendor_dir/apps/TontOSLauncher/AndroidManifest.xml"
    fi
    if [[ -d "$ROOT_DIR/launcher_apk/src" ]]; then
        rsync -a "$ROOT_DIR/launcher_apk/src/" "$vendor_dir/apps/TontOSLauncher/src/"
    fi

    # 2. Product makefiles
    if [[ -d "$ROOT_DIR/platform/products" ]]; then
        cp -f "$ROOT_DIR/platform/products/"*.mk "$vendor_dir/products/" 2>/dev/null || true
    fi
    if [[ -f "$ROOT_DIR/platform/AndroidProducts.mk" ]]; then
        cp -f "$ROOT_DIR/platform/AndroidProducts.mk" "$vendor_dir/AndroidProducts.mk"
    fi

    # 3. Permissions XMLs
    if [[ -d "$ROOT_DIR/platform/config" ]]; then
        cp -f "$ROOT_DIR/platform/config/"*.xml "$vendor_dir/products/" 2>/dev/null || true
    fi

    # 4. FOSS prebuilts if present
    if [[ -d "$ROOT_DIR/apps" ]]; then
        cp -rf "$ROOT_DIR/apps/"*.apk "$vendor_dir/apps/prebuilts/" 2>/dev/null || true
    fi
}

build_single_gsi() {
    local target_arch="$1"
    local lunch_target
    lunch_target=$(resolve_lunch_target "$target_arch")
    local arch_out_dir="$OUTPUT_ROOT/$target_arch"
    mkdir -p "$arch_out_dir"

    echo "=========================================================="
    echo " Building TontOS GSI for Architecture: $target_arch"
    echo " Lunch Target:   $lunch_target"
    echo " AOSP Directory: $AOSP_DIR"
    echo " Output Dir:     $arch_out_dir"
    echo " Parallelism:    -j$BUILD_JOBS ($TOTAL_CORES cores detected)"
    echo " Build Mode:     $([ "$USE_DOCKER" = true ] && echo "Docker" || echo "Native Host")"
    echo "=========================================================="

    if [[ "$CLEAN_BUILD" = true ]]; then
        echo "[*] Cleaning out/target/product in AOSP..."
        rm -rf "$AOSP_DIR/out/target/product"
    fi

    if [[ "$DRY_RUN" = true ]]; then
        echo "[*] DRY-RUN MODE: Validating environment setup and lunch..."
        (
            cd "$AOSP_DIR"
            # shellcheck disable=SC1091
            source build/envsetup.sh
            lunch "$lunch_target"
        )
        echo "[+] Validation of target $lunch_target completed successfully."
        return 0
    fi

    # System image compilation
    echo "[*] Initiating 'systemimage' compilation for $lunch_target..."
    if [[ "$USE_DOCKER" = true ]]; then
        "$SCRIPT_DIR/docker_build_rom.sh" /bin/bash -c \
            "source build/envsetup.sh && lunch $lunch_target && m -j$BUILD_JOBS systemimage"
    else
        (
            cd "$AOSP_DIR"
            # shellcheck disable=SC1091
            source build/envsetup.sh
            lunch "$lunch_target"
            m -j"$BUILD_JOBS" systemimage
        )
    fi

    # Locate generated system.img
    echo "[*] Locating generated system.img..."
    local system_img=""
    local candidate_dirs=(
        "$AOSP_DIR/out/target/product/tontos_gsi_$target_arch"
        "$AOSP_DIR/out/target/product/aosp_$target_arch"
        "$AOSP_DIR/out/target/product/generic_$target_arch"
        "$AOSP_DIR/out/target/product/generic"
    )

    for d in "${candidate_dirs[@]}"; do
        if [[ -f "$d/system.img" ]]; then
            system_img="$d/system.img"
            break
        fi
    done

    if [[ -z "$system_img" || ! -f "$system_img" ]]; then
        system_img=$(find "$AOSP_DIR/out/target/product" -type f -name "system.img" 2>/dev/null | head -n 1 || true)
    fi

    if [[ -z "$system_img" || ! -f "$system_img" ]]; then
        echo "ERROR: system.img not found after building $lunch_target" >&2
        exit 1
    fi

    local gsi_output_img="$arch_out_dir/tontos-gsi-$target_arch.img"
    echo "[+] Copying $system_img -> $gsi_output_img"
    cp -f "$system_img" "$gsi_output_img"

    # Verify generated image format
    echo "[*] Image metadata:"
    file "$gsi_output_img" || true
    ls -lh "$gsi_output_img"

    # Generate flashing script for user
    local flash_script="$arch_out_dir/flash_gsi.sh"
    cat > "$flash_script" <<FLASH_EOF
#!/usr/bin/env bash
# ==============================================================================
# TontOS GSI Quick Flash Script ($target_arch)
# Connect your smartphone via USB with USB Debugging & OEM Unlocked enabled.
# ==============================================================================
set -e

IMG_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
IMG_FILE="\$IMG_DIR/tontos-gsi-$target_arch.img"

if [[ ! -f "\$IMG_FILE" && -f "\$IMG_FILE.xz" ]]; then
    echo "[*] Decompressing \$IMG_FILE.xz..."
    xz -d -k "\$IMG_FILE.xz"
fi

if [[ ! -f "\$IMG_FILE" ]]; then
    echo "ERROR: \$IMG_FILE not found." >&2
    exit 1
fi

echo "=== Flashing TontOS GSI ($target_arch) ==="
echo "1. Rebooting to fastbootd..."
adb reboot fastboot || fastboot reboot fastboot

echo "2. Waiting for fastboot device..."
fastboot wait-for-device

echo "3. Flashing system partition..."
fastboot flash system "\$IMG_FILE"

echo "4. Wipe data / factory reset? (Recommended for fresh GSI install)"
read -r -p "Execute fastboot -w? [y/N]: " resp
if [[ "\$resp" =~ ^[yY]$ ]]; then
    fastboot -w
fi

echo "5. Rebooting phone..."
fastboot reboot
echo "=== Installation completed successfully ==="
FLASH_EOF
    chmod +x "$flash_script"

    # Compress if enabled
    if [[ "$NO_COMPRESS" = false ]]; then
        echo "[*] Generating multithreaded xz compressed archive..."
        xz -T0 -k -f "$gsi_output_img"
        sha256sum "$gsi_output_img.xz" > "$gsi_output_img.xz.sha256"
        echo "[+] Compressed archive created: $gsi_output_img.xz"
        echo "[+] SHA256: $(cat "$gsi_output_img.xz.sha256")"
    else
        sha256sum "$gsi_output_img" > "$gsi_output_img.sha256"
    fi

    echo "=========================================================="
    echo " GSI $target_arch successfully generated at $arch_out_dir"
    echo "=========================================================="
}

# --- Main Execution ---

if [[ ! -d "$AOSP_DIR" ]]; then
    echo "ERROR: AOSP tree not found at $AOSP_DIR" >&2
    echo "Configure AOSP_DIR=/path/to/aosp or run bin/helpers/sync_aosp.sh first." >&2
    exit 1
fi

mkdir -p "$OUTPUT_ROOT"
mkdir -p "$CCACHE_DIR"

# 1. Compile signed TontOS Launcher APK if not skipped
if [[ "$SKIP_APK" = false ]]; then
    echo "[*] Step 1: Compiling signed TontOS Launcher APK..."
    "$ROOT_DIR/bin/build_launcher_apk.sh"
fi

# 2. Stage vendor files into AOSP tree
stage_vendor_files

# 3. Build for selected architecture(s)
if [[ "$ARCH" == "all" ]]; then
    echo "[*] Compiling full architecture suite: arm64, arm, x86_64"
    build_single_gsi "arm64"
    build_single_gsi "arm"
    build_single_gsi "x86_64"
else
    build_single_gsi "$ARCH"
fi

echo
echo "=== GSI Build Process Completed Successfully ==="
echo "Images available at: $OUTPUT_ROOT"
