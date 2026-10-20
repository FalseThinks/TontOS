#!/usr/bin/env bash
# ==============================================================================
# TontOS Windows & WSL2 Environment Validator & Configurator
# Ensures optimal virtualization parameters, filesystem safety, and memory allocations.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

echo "=========================================================="
echo "  TontOS Windows (WSL2) Environment Validator"
echo "=========================================================="

# 1. Detect WSL2
IS_WSL=false
if grep -qi "microsoft" /proc/version 2>/dev/null || [[ -n "${WSL_DISTRO_NAME:-}" ]]; then
    IS_WSL=true
fi

if [[ "$IS_WSL" != "true" ]]; then
    echo "[-] Notice: Current host is native Linux, not WSL2."
    echo "    This script is designed to validate Windows WSL2 subsystems."
    echo "    Generating reference .wslconfig template for Windows peers anyway..."
else
    echo "[✓] Microsoft WSL2 environment detected: ${WSL_DISTRO_NAME:-Default}"
fi

# 2. Filesystem Check (CRITICAL: Never run AOSP builds on /mnt/c or NTFS)
CURRENT_FS=$(df -T "$ROOT_DIR" | tail -n 1 | awk '{print $2}')
if [[ "$ROOT_DIR" == /mnt/* ]] || [[ "$CURRENT_FS" == "drvfs" ]] || [[ "$CURRENT_FS" == "9p" ]] || [[ "$CURRENT_FS" == "fuse.ntfs"* ]]; then
    echo ""
    echo "[!] CRITICAL FAILURE: Workspace is located on a Windows NTFS mount ($ROOT_DIR)!"
    echo "    NTFS does NOT support:"
    echo "      1. POSIX symlinks used extensively by AOSP & Soong."
    echo "      2. Linux case-sensitivity (xt_DSCP.h vs xt_dscp.h collision bricks build)."
    echo "      3. Native Linux file permissions and extended attributes."
    echo "    SOLUTION:"
    echo "      Move workspace to native Linux ext4 filesystem inside WSL:"
    echo "      mkdir -p ~/workspace && cp -r $ROOT_DIR ~/workspace/TontOS"
    echo ""
    exit 1
else
    echo "[✓] Native Linux ext4/btrfs filesystem confirmed: $CURRENT_FS ($ROOT_DIR)"
fi

# 3. Memory & Virtual Swap Verification
TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 0)
TOTAL_SWAP_KB=$(grep SwapTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 0)
TOTAL_RAM_MB=$(( TOTAL_RAM_KB / 1024 ))
TOTAL_SWAP_MB=$(( TOTAL_SWAP_KB / 1024 ))
TOTAL_VIRT_MB=$(( TOTAL_RAM_MB + TOTAL_SWAP_MB ))

echo "[*] Memory Status: ${TOTAL_RAM_MB}MB RAM | ${TOTAL_SWAP_MB}MB Swap (Total Virtual: ${TOTAL_VIRT_MB}MB)"

if (( TOTAL_VIRT_MB < 24000 )); then
    echo ""
    echo "[!] WARNING: Insufficient virtual memory (${TOTAL_VIRT_MB}MB < 24,000MB)!"
    echo "    AOSP LLVM linking and R8 optimization will trigger OOM SIGKILL."
    echo "    Windows WSL2 defaults to 50% RAM and limited swap."
fi

# 4. Generate Recommended Windows .wslconfig
WSLCONFIG_TEMPLATE="$ROOT_DIR/wslconfig.sample"
cat << 'EOF' > "$WSLCONFIG_TEMPLATE"
# TontOS Recommended Windows WSL2 Configuration
# Copy this file to: %USERPROFILE%\.wslconfig  (e.g., C:\Users\<YourUser>\.wslconfig)
# Then restart WSL in Windows PowerShell: wsl --shutdown

[wsl2]
# Allocate at least 12GB RAM to WSL (adjust to 75% of your host RAM)
memory=12GB

# Allocate generous swap on fast SSD/NVMe (prevents OOM during ROM compilation)
swap=24GB

# Enable all CPU cores for parallel Ninja/Soong compilation
processors=16

# Enable nested virtualization for Cuttlefish/KVM acceleration inside WSL
nestedVirtualization=true

# Keep page cache clean
pageReporting=true
EOF

echo "[✓] Generated reference configuration: $WSLCONFIG_TEMPLATE"

# 5. Check Docker Availability
if command -v docker >/dev/null 2>&1; then
    if docker info >/dev/null 2>&1; then
        echo "[✓] Docker is running and accessible."
    else
        echo "[!] Docker CLI found, but Docker daemon is not running or accessible without sudo."
        echo "    Ensure 'Docker Desktop -> Settings -> Resources -> WSL Integration' is enabled."
    fi
else
    echo "[!] Docker CLI not found. Install Docker inside WSL or enable Docker Desktop WSL integration."
fi

echo "=========================================================="
echo "[✓] WSL2 verification complete."
echo "=========================================================="
