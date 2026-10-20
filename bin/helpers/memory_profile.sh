#!/usr/bin/env bash
# ==============================================================================
# TontOS Dynamic Memory Profile & Concurrency Engine
# Adapts build parameters to currently AVAILABLE RAM vs TOTAL physical RAM.
# Warns user and offers interactive choice or automated flags.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================

MEM_STRATEGY="" # 'available' (safe default) or 'total' (aggressive)
EXPLICIT_RAM="${RAM_PROFILE:-}"

for arg in "$@"; do
    case "$arg" in
        --help|-h|help)
            MEM_STRATEGY="${MEM_STRATEGY:-available}"
            ;;
        --mem-mode=available|--mode=available|--available)
            MEM_STRATEGY="available"
            ;;
        --mem-mode=total|--mode=total|--total|--force-total)
            MEM_STRATEGY="total"
            ;;
        --ram=*)
            EXPLICIT_RAM="${arg#*=}"
            ;;
        --ram)
            # handled if next arg exists in parent loop
            ;;
    esac
done

TOTAL_CORES=$(nproc 2>/dev/null || echo 4)
TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 8388608)
AVAIL_RAM_KB=$(grep MemAvailable /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 4194304)
SWAP_FREE_KB=$(grep SwapFree /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 0)

TOTAL_RAM_GB=$(( (TOTAL_RAM_KB + 524288) / 1048576 ))
AVAIL_RAM_GB=$(( (AVAIL_RAM_KB + 524288) / 1048576 ))
SWAP_FREE_GB=$(( (SWAP_FREE_KB + 524288) / 1048576 ))

# Detect real disk-backed swap (excludes in-RAM zRAM devices)
DISK_SWAP_KB=$(awk '$2 == "file" || ($2 == "partition" && $1 !~ /zram/) {sum += $3} END {print int(sum)}' /proc/swaps 2>/dev/null || echo 0)
DISK_SWAP_GB=$(( (DISK_SWAP_KB + 524288) / 1048576 ))

# Automatically activate existing disk swapfile if available and inactive
if (( DISK_SWAP_GB == 0 )) && [[ -f "/swapfile_aosp" ]]; then
    if swapon -p 10 /swapfile_aosp 2>/dev/null; then
        DISK_SWAP_KB=$(awk '$2 == "file" || ($2 == "partition" && $1 !~ /zram/) {sum += $3} END {print int(sum)}' /proc/swaps 2>/dev/null || echo 0)
        DISK_SWAP_GB=$(( (DISK_SWAP_KB + 524288) / 1048576 ))
    elif command -v sudo >/dev/null 2>&1; then
        # Try non-interactive sudo (works in WSL2 passwordless sudo and cached sudo)
        if sudo -n swapon -p 10 /swapfile_aosp 2>/dev/null; then
            DISK_SWAP_KB=$(awk '$2 == "file" || ($2 == "partition" && $1 !~ /zram/) {sum += $3} END {print int(sum)}' /proc/swaps 2>/dev/null || echo 0)
            DISK_SWAP_GB=$(( (DISK_SWAP_KB + 524288) / 1048576 ))
        fi
    fi
fi

get_profile_name() {
    local gb=$1
    if (( gb <= 6 )); then
        echo "4"
    elif (( gb <= 12 )); then
        echo "8"
    elif (( gb <= 24 )); then
        echo "16"
    else
        echo "32"
    fi
}

PROFILE_FOR_AVAIL=$(get_profile_name "$AVAIL_RAM_GB")
PROFILE_FOR_TOTAL=$(get_profile_name "$TOTAL_RAM_GB")

# Interactive selection if in terminal and user did not specify explicit flag
if [[ -z "$EXPLICIT_RAM" && -z "$MEM_STRATEGY" && -t 0 && -t 1 && -z "${CI:-}" && "$PROFILE_FOR_AVAIL" != "$PROFILE_FOR_TOTAL" ]]; then
    echo "=========================================================="
    echo "  [!] TontOS Dynamic Memory Strategy Check"
    echo "=========================================================="
    echo "  Total Physical RAM:    ${TOTAL_RAM_GB} GB (Hardware capacity)"
    echo "  Currently Available:   ${AVAIL_RAM_GB} GB (Free RAM at this moment)"
    echo "  In-RAM zRAM Active:    ${SWAP_FREE_GB} GB"
    if (( DISK_SWAP_GB > 0 )); then
        echo "  Disk Swap Active:      ${DISK_SWAP_GB} GB (NVMe backed - Safe for heavy concurrency)"
    else
        echo "  Disk Swap Active:      0 GB (WARNING: No disk swapfile mounted!)"
    fi
    echo "----------------------------------------------------------"
    echo "  [1] AVAILABLE MEMORY (Recommended / Safe):"
    echo "      Profile ${PROFILE_FOR_AVAIL}GB -> Prevents system freeze & avoids thrashing."
    echo ""
    echo "  [2] FULL PC MEMORY (Maximum throughput):"
    echo "      Profile ${PROFILE_FOR_TOTAL}GB -> Higher concurrency, close other heavy apps."
    echo "=========================================================="
    
    USER_CHOICE=""
    read -r -t 10 -p "Continue with available [1] or full PC RAM [2]? (1/2, default 1 in 10s): " USER_CHOICE || true
    echo ""
    if [[ "${USER_CHOICE:-1}" == "2" ]]; then
        MEM_STRATEGY="total"
        echo "[+] Selected strategy: FULL PC HARDWARE RAM (${TOTAL_RAM_GB}GB)"
    else
        MEM_STRATEGY="available"
        echo "[+] Selected strategy: CURRENTLY AVAILABLE RAM (${AVAIL_RAM_GB}GB)"
    fi
fi

MEM_STRATEGY="${MEM_STRATEGY:-available}"

if [[ -n "$EXPLICIT_RAM" ]]; then
    ACTIVE_PROFILE="$EXPLICIT_RAM"
    STRATEGY_DESC="Manual Override (--ram=$EXPLICIT_RAM)"
elif [[ "$MEM_STRATEGY" == "total" ]]; then
    ACTIVE_PROFILE="$PROFILE_FOR_TOTAL"
    STRATEGY_DESC="Total PC Hardware Capacity (${TOTAL_RAM_GB}GB physical)"
else
    ACTIVE_PROFILE="$PROFILE_FOR_AVAIL"
    STRATEGY_DESC="Currently Available RAM (${AVAIL_RAM_GB}GB free of ${TOTAL_RAM_GB}GB)"
fi

case "$ACTIVE_PROFILE" in
    4|4g|4G|4GB)
        PROFILE_LABEL="4GB (Ultra-Low RAM / High Swap Tier)"
        SELECTED_JOBS=1
        JAVA_XMX="2g"
        GOMAXPROCS_VAL=1
        MALLOC_ARENA_MAX_VAL=1
        MIN_SWAP_REQUIRED_GB=28
        SHM_SIZE="2g"
        ;;
    8|8g|8G|8GB)
        PROFILE_LABEL="8GB (Low RAM / Balanced Swap)"
        SELECTED_JOBS=$(( TOTAL_CORES >= 4 && AVAIL_RAM_GB >= 10 ? 4 : 2 ))
        JAVA_XMX="4g"
        GOMAXPROCS_VAL=2
        MALLOC_ARENA_MAX_VAL=2
        MIN_SWAP_REQUIRED_GB=24
        SHM_SIZE="4g"
        ;;
    16|16g|16G|16GB)
        PROFILE_LABEL="16GB (Standard Workstation)"
        SELECTED_JOBS=$(( TOTAL_CORES > 6 ? 6 : (TOTAL_CORES > 2 ? TOTAL_CORES - 1 : 2) ))
        JAVA_XMX="6g"
        GOMAXPROCS_VAL=2
        MALLOC_ARENA_MAX_VAL=2
        MIN_SWAP_REQUIRED_GB=16
        SHM_SIZE="4g"
        ;;
    32|32g|32G|32GB|*)
        PROFILE_LABEL="32GB+ (High Performance Multi-Core)"
        SELECTED_JOBS=$(( TOTAL_CORES > 16 ? 16 : (TOTAL_CORES > 2 ? TOTAL_CORES - 2 : 2) ))
        JAVA_XMX="12g"
        GOMAXPROCS_VAL=4
        MALLOC_ARENA_MAX_VAL=4
        MIN_SWAP_REQUIRED_GB=8
        SHM_SIZE="8g"
        ;;
esac

# Enforce strict safety clamp if NO disk swapfile is active
if (( DISK_SWAP_GB == 0 )); then
    if (( SELECTED_JOBS > 2 )); then
        SELECTED_JOBS=2
    fi
    JAVA_XMX="3g"
    GOMAXPROCS_VAL=1
    MALLOC_ARENA_MAX_VAL=1
    STRATEGY_DESC="$STRATEGY_DESC [DISK SWAP OFF: Clamped to -j2 / -Xmx3g]"
fi

BUILD_JOBS="$SELECTED_JOBS"

export TOTAL_RAM_GB AVAIL_RAM_GB SWAP_FREE_GB
export ACTIVE_PROFILE PROFILE_LABEL STRATEGY_DESC
export BUILD_JOBS JAVA_XMX GOMAXPROCS_VAL MALLOC_ARENA_MAX_VAL MIN_SWAP_REQUIRED_GB SHM_SIZE
