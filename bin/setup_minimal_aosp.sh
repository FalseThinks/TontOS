#!/usr/bin/env bash
# ==============================================================================
# TontOS Low-Disk AOSP Minimal Setup & Pruning Engine
# Reduces AOSP checkout footprint from ~350GB down to ~80-110GB via shallow clone,
# blob filtering, and local manifest device/test tree pruning.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

AOSP_TARGET_DIR="${1:-$ROOT_DIR/.build/aosp}"
BRANCH="${AOSP_BRANCH:-android-15.0.0_r1}"
MANIFEST_URL="${MANIFEST_URL:-https://android.googlesource.com/platform/manifest}"

echo "=========================================================="
echo "  TontOS Minimal AOSP Setup & Pruning Engine"
echo "=========================================================="
echo "  Target Directory: $AOSP_TARGET_DIR"
echo "  Branch:           $BRANCH"
echo "  Manifest URL:     $MANIFEST_URL"
echo "=========================================================="

# 1. Preflight Disk Space Check
FREE_KB=$(df -k "$ROOT_DIR" | tail -n 1 | awk '{print $4}')
FREE_GB=$(( FREE_KB / 1024 / 1024 ))

echo "[*] Detected $FREE_GB GB available on current partition."
if (( FREE_GB < 60 )); then
    echo "[!] WARNING: Less than 60 GB free ($FREE_GB GB). Minimal AOSP sync requires at least 70-90 GB."
    echo "    Run './bin/reclaim_disk_space.sh' to recover storage before proceeding."
fi

mkdir -p "$AOSP_TARGET_DIR"
cd "$AOSP_TARGET_DIR"

# 2. Initialize Repo with Shallow & Blobless Clone
echo "[+] Initializing shallow repository (--depth=1 --single-branch)..."
repo init \
    -u "$MANIFEST_URL" \
    -b "$BRANCH" \
    --depth=1 \
    --single-branch \
    --no-clone-bundle \
    --git-lfs

# 3. Create Local Manifest to Prune Bloated Unused Projects
# Discards redundant architectures (arm32, generic car, unused pixel targets, full CTS)
LOCAL_MANIFEST_DIR="$AOSP_TARGET_DIR/.repo/local_manifests"
mkdir -p "$LOCAL_MANIFEST_DIR"

cat << 'EOF' > "$LOCAL_MANIFEST_DIR/tontos_minimal_pruning.xml"
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <!-- Remove unused hardware device trees & Pixel blobs (saves ~40-60GB) -->
  <remove-project name="device/google/akita" />
  <remove-project name="device/google/akita-sepolicy" />
  <remove-project name="device/google/barbet" />
  <remove-project name="device/google/bluejay" />
  <remove-project name="device/google/bluejay-sepolicy" />
  <remove-project name="device/google/bramble" />
  <remove-project name="device/google/cheetah" />
  <remove-project name="device/google/corallium" />
  <remove-project name="device/google/felix" />
  <remove-project name="device/google/felix-sepolicy" />
  <remove-project name="device/google/pantah" />
  <remove-project name="device/google/raven" />
  <remove-project name="device/google/redfin" />
  <remove-project name="device/google/shiba" />
  <remove-project name="device/google/tangorpro" />
  <remove-project name="device/google/zuma" />
  <remove-project name="device/generic/arm64" />
  <remove-project name="device/generic/armv7-a-neon" />
  <remove-project name="device/generic/car" />

  <!-- Remove heavy test suites not required for building TontOS ROM (saves ~25GB) -->
  <remove-project name="platform/cts" />
  <remove-project name="platform/test/vts" />
  <remove-project name="platform/test/vts-testcase/kernel" />
  <remove-project name="platform/test/vts-testcase/hal" />

  <!-- Remove redundant stock AOSP apps replaced by FOSS / TontOS suite -->
  <remove-project name="platform/packages/apps/Music" />
  <remove-project name="platform/packages/apps/QuickSearchBox" />
</manifest>
EOF

echo "[✓] Configured local pruning manifest at .repo/local_manifests/tontos_minimal_pruning.xml"

# 4. Sync Minimal Tree
TOTAL_CORES=$(nproc 2>/dev/null || echo 4)
SYNC_JOBS=$(( TOTAL_CORES > 4 ? 4 : TOTAL_CORES ))

echo "[+] Syncing minimal AOSP tree (-j$SYNC_JOBS, current branch only)..."
repo sync -c -j"$SYNC_JOBS" --no-tags --current-branch --force-sync

echo "=========================================================="
echo "[✓] Minimal AOSP Tree successfully synchronized at $AOSP_TARGET_DIR"
echo "    Total footprint:"
du -sh "$AOSP_TARGET_DIR" || true
echo "=========================================================="
