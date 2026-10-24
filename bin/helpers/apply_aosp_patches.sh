#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# TontOS: Automated AOSP Patch & Vendor Tree Applicator
# Re-applies TontOS kernel/framework patches (Soong memory guard, Certified Spoof,
# Cross-profile NFC HCE sandbox, WebApp build fix) and synchronizes vendor makefiles.
# ==============================================================================

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
AOSP_DIR="${1:-${AOSP_DIR:-$HOME/aosp/tontos}}"
PATCHES_DIR="$ROOT_DIR/patches"

echo "=========================================================="
echo "  TontOS AOSP Patch & Vendor Tree Synchronization"
echo "  Target AOSP: $AOSP_DIR"
echo "=========================================================="

if [[ ! -d "$AOSP_DIR" ]]; then
    echo "[-] Error: AOSP tree not found at $AOSP_DIR"
    exit 1
fi

apply_patch() {
    local rel_path="$1"
    local patch_file="$2"
    local target_repo="$AOSP_DIR/$rel_path"

    if [[ ! -d "$target_repo" ]]; then
        echo "[-] Warning: Repository $rel_path not found, skipping."
        return 0
    fi

    if [[ ! -f "$patch_file" ]]; then
        echo "[-] Error: Patch file $patch_file not found."
        exit 1
    fi

    echo "[*] Checking patch for $rel_path..."
    if git -C "$target_repo" apply --check "$patch_file" 2>/dev/null; then
        git -C "$target_repo" apply "$patch_file"
        echo "[+] Successfully applied $(basename "$patch_file") to $rel_path"
    elif git -C "$target_repo" apply -R --check "$patch_file" 2>/dev/null; then
        echo "[!] Patch $(basename "$patch_file") already applied in $rel_path."
    else
        echo "[-] Error: Patch $(basename "$patch_file") cannot be applied to $rel_path cleanly."
        exit 1
    fi
}

# 1. Apply core system patches
apply_patch "build/blueprint" "$PATCHES_DIR/build_blueprint.patch"
apply_patch "frameworks/base" "$PATCHES_DIR/frameworks_base.patch"
apply_patch "packages/modules/Nfc" "$PATCHES_DIR/packages_modules_Nfc.patch"
apply_patch "prebuilts/module_sdk/WebApp" "$PATCHES_DIR/prebuilts_module_sdk_WebApp.patch"
apply_patch "system/ca-certificates" "$PATCHES_DIR/system_ca_certificates_emudhra.patch"

# 2. Synchronize TontOS vendor definitions
echo "[*] Synchronizing TontOS vendor tree ($AOSP_DIR/vendor/tontos)..."
mkdir -p "$AOSP_DIR/vendor/tontos/products" "$AOSP_DIR/vendor/tontos/apps/prebuilts"

cp -f "$ROOT_DIR/platform/AndroidProducts.mk" "$AOSP_DIR/vendor/tontos/"
cp -f "$ROOT_DIR/platform/products/"*.mk "$AOSP_DIR/vendor/tontos/products/"
cp -f "$ROOT_DIR/platform/apps/prebuilts/Android.bp" "$AOSP_DIR/vendor/tontos/apps/prebuilts/"
if [[ -d "$ROOT_DIR/apps" ]]; then
    cp -f "$ROOT_DIR/apps/"*.apk "$AOSP_DIR/vendor/tontos/apps/prebuilts/" 2>/dev/null || true
fi
if [[ -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" ]]; then
    cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$AOSP_DIR/vendor/tontos/apps/"
fi

# Ensure out/ symlink exists
if [[ ! -L "$AOSP_DIR/out" && ! -d "$AOSP_DIR/out" ]]; then
    ln -s "$ROOT_DIR/out" "$AOSP_DIR/out"
fi

echo "=========================================================="
echo "[+] All TontOS AOSP patches and vendor makefiles active."
echo "=========================================================="
exit 0
