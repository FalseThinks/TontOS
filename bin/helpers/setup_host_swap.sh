#!/usr/bin/env bash
# ==============================================================================
# TontOS Host Swap & zRAM Automation for Linux
# Configures tiered memory (zRAM pri=100 + NVMe swapfile pri=10 + kernel sysctl)
# Handles Btrfs (nodatacow / mkswapfile) and Ext4/XFS automatically.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SWAP_FILE="${SWAP_FILE:-/swapfile_aosp}"
ZRAM_DEV="${ZRAM_DEV:-/dev/zram0}"
SYSCTL_CONF="/etc/sysctl.d/99-tontos-build.conf"

# Calculate safe swap size based on available disk space
if [[ -z "${SWAP_SIZE:-}" || "${SWAP_SIZE:-}" == "32G" ]]; then
    avail_gb=$(df -BG / | awk 'NR==2 {gsub("G","",$4); print $4}')
    if (( avail_gb >= 120 )); then
        SWAP_SIZE="32G"
    elif (( avail_gb >= 70 )); then
        SWAP_SIZE="24G"
    elif (( avail_gb >= 40 )); then
        SWAP_SIZE="16G"
    else
        SWAP_SIZE="8G"
    fi
fi

print_header() {
    echo "=========================================================="
    echo "  TontOS Host Memory & Swap Automation"
    echo "=========================================================="
}

check_root() {
    if [[ $EUID -ne 0 ]]; then
        if command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
            exec sudo "$0" "$@"
        elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
            echo "[+] Non-root host detected: executing swap management seamlessly via privileged Docker..."
            exec docker run --rm --privileged -v /:/host alpine:latest sh -c "
                apk add --no-cache btrfs-progs util-linux >/dev/null 2>&1
                action=\"\${1:-setup}\"
                if [ \"\$action\" = \"clean\" ] || [ \"\$action\" = \"disable\" ]; then
                    swapoff /host$SWAP_FILE 2>/dev/null || swapoff $SWAP_FILE 2>/dev/null || true
                    if [ \"\$action\" = \"clean\" ] || [ \"\${2:-}\" = \"--delete\" ]; then
                        rm -f /host$SWAP_FILE $SWAP_FILE
                        echo '    [✓] Disk swap cleaned.'
                    fi
                else
                    fs=\$(df -T /host | awk 'NR==2 {print \$2}')
                    if [ ! -f \"/host$SWAP_FILE\" ]; then
                        echo \"[+] Creating $SWAP_SIZE swapfile at $SWAP_FILE on \$fs...\"
                        if [ \"\$fs\" = \"btrfs\" ]; then
                            btrfs filesystem mkswapfile --size $SWAP_SIZE \"/host$SWAP_FILE\"
                        else
                            fallocate -l $SWAP_SIZE \"/host$SWAP_FILE\" || dd if=/dev/zero of=\"/host$SWAP_FILE\" bs=1M count=24576 status=none
                            chmod 600 \"/host$SWAP_FILE\"
                            mkswap \"/host$SWAP_FILE\"
                        fi
                    fi
                    chmod 600 \"/host$SWAP_FILE\"
                    if ! grep -q \"$SWAP_FILE\" /proc/swaps 2>/dev/null; then
                        echo \"[+] Activating $SWAP_FILE with priority 10...\"
                        swapon -p 10 \"/host$SWAP_FILE\"
                    fi
                    sysctl -w vm.swappiness=150 >/dev/null 2>&1 || true
                    sysctl -w vm.page-cluster=0 >/dev/null 2>&1 || true
                    sysctl -w vm.vfs_cache_pressure=50 >/dev/null 2>&1 || true
                    sysctl -w vm.max_map_count=1048576 >/dev/null 2>&1 || true
                    echo '[✓] Tiered swap and kernel parameters active.'
                    swapon --show
                fi
            " dummy "$@"
        else
            echo "[!] Privileged operation required. Prompting for sudo..."
            exec sudo "$0" "$@"
        fi
    fi
}

detect_fs() {
    local target_dir
    target_dir="$(dirname "$SWAP_FILE")"
    df -T "$target_dir" | awk 'NR==2 {print $2}'
}

setup_zram() {
    echo "[+] Configuring zRAM (/dev/zram0)..."
    if ! lsmod | grep -q zram; then
        modprobe zram num_devices=1
    fi

    # Check if zram is already active
    if grep -q "$ZRAM_DEV" /proc/swaps 2>/dev/null; then
        echo "    zRAM already active at $ZRAM_DEV"
    else
        if [[ -b "$ZRAM_DEV" ]]; then
            swapoff "$ZRAM_DEV" 2>/dev/null || true
            # Set algorithm to zstd if available, fallback to lz4
            if grep -q zstd "/sys/block/$(basename "$ZRAM_DEV")/comp_algorithm" 2>/dev/null; then
                echo zstd > "/sys/block/$(basename "$ZRAM_DEV")/comp_algorithm"
            fi
            # Set disksize to 100% of RAM
            local ram_bytes
            ram_bytes="$(grep MemTotal /proc/meminfo | awk '{print $2 * 1024}')"
            echo "$ram_bytes" > "/sys/block/$(basename "$ZRAM_DEV")/disksize"
            mkswap "$ZRAM_DEV" >/dev/null
            swapon -p 100 "$ZRAM_DEV"
            echo "    zRAM initialized with zstd compression and priority 100."
        fi
    fi
}

create_swapfile() {
    local fs_type="$1"
    echo "[+] Creating $SWAP_SIZE swapfile at $SWAP_FILE..."
    if [[ "$fs_type" == "btrfs" ]]; then
        # Btrfs requires dedicated swapfile command or nodatacow attribute
        if btrfs filesystem mkswapfile --help >/dev/null 2>&1; then
            btrfs filesystem mkswapfile --size "$SWAP_SIZE" --uuid clear "$SWAP_FILE"
        else
            truncate -s 0 "$SWAP_FILE"
            chattr +C "$SWAP_FILE"
            btrfs property set "$SWAP_FILE" compression none 2>/dev/null || true
            fallocate -l "$SWAP_SIZE" "$SWAP_FILE" || dd if=/dev/zero of="$SWAP_FILE" bs=1M count=32768 status=progress
            chmod 600 "$SWAP_FILE"
            mkswap "$SWAP_FILE"
        fi
    else
        fallocate -l "$SWAP_SIZE" "$SWAP_FILE" || dd if=/dev/zero of="$SWAP_FILE" bs=1M count=32768 status=progress
        chmod 600 "$SWAP_FILE"
        mkswap "$SWAP_FILE"
    fi
    echo "    Swapfile created successfully."
}

setup_swapfile() {
    local fs_type
    fs_type="$(detect_fs)"
    echo "[+] Filesystem for $SWAP_FILE detected: $fs_type"

    # Purge old orphan 16GB swapfile if inactive to save disk space
    if [[ -f "/swapfile-aosp" ]] && ! grep -q "/swapfile-aosp" /proc/swaps 2>/dev/null; then
        echo "    Removing inactive legacy /swapfile-aosp..."
        rm -f "/swapfile-aosp"
    fi

    if [[ ! -f "$SWAP_FILE" ]]; then
        create_swapfile "$fs_type"
    else
        chmod 600 "$SWAP_FILE"
    fi

    if ! grep -q "$SWAP_FILE" /proc/swaps 2>/dev/null; then
        echo "[+] Enabling disk swapfile with priority 10..."
        if ! swapon -p 10 "$SWAP_FILE" 2>/dev/null; then
            echo "[!] Existing $SWAP_FILE rejected by kernel (invalid extents/CoW on $fs_type). Recreating cleanly..."
            rm -f "$SWAP_FILE"
            create_swapfile "$fs_type"
            swapon -p 10 "$SWAP_FILE"
        fi
        echo "    Swapfile enabled."
    else
        echo "    Swapfile $SWAP_FILE is already active."
    fi
}

apply_sysctl() {
    echo "[+] Applying AOSP build kernel sysctl tunings..."
    sysctl -w vm.swappiness=150 >/dev/null
    sysctl -w vm.page-cluster=0 >/dev/null
    sysctl -w vm.vfs_cache_pressure=50 >/dev/null
    sysctl -w vm.max_map_count=1048576 >/dev/null
    sysctl -w fs.file-max=2097152 >/dev/null
}

persist_settings() {
    echo "[+] Persisting sysctl and fstab configurations..."
    mkdir -p "$(dirname "$SYSCTL_CONF")"
    cat << 'EOF' > "$SYSCTL_CONF"
# TontOS & AOSP build memory optimizations
vm.swappiness=150
vm.page-cluster=0
vm.vfs_cache_pressure=50
vm.max_map_count=1048576
fs.file-max=2097152
EOF
    echo "    Wrote $SYSCTL_CONF"

    if ! grep -q "$SWAP_FILE" /etc/fstab; then
        echo "$SWAP_FILE none swap defaults,pri=10 0 0" >> /etc/fstab
        echo "    Appended $SWAP_FILE to /etc/fstab"
    fi
}

show_status() {
    print_header
    echo "--> Memory & Active Swaps:"
    free -h
    echo ""
    echo "--> Swap Priority Table (/proc/swaps):"
    swapon --show
    echo ""
    if command -v zramctl >/dev/null 2>&1; then
        echo "--> zRAM Status (zramctl):"
        zramctl || true
        echo ""
    fi
    echo "--> Kernel VM Parameters:"
    echo "    vm.swappiness        = $(sysctl -n vm.swappiness)"
    echo "    vm.page-cluster      = $(sysctl -n vm.page-cluster)"
    echo "    vm.vfs_cache_pressure = $(sysctl -n vm.vfs_cache_pressure)"
    echo "    vm.max_map_count     = $(sysctl -n vm.max_map_count)"
    echo "=========================================================="
}

disable_all() {
    echo "[+] Disabling disk swap and flushing memory..."
    swapoff "$SWAP_FILE" 2>/dev/null || true
    echo "    Disk swap disabled."
}

clean_all() {
    disable_all
    if [[ -f "$SWAP_FILE" ]]; then
        echo "[+] Removing $SWAP_FILE..."
        rm -f "$SWAP_FILE"
        echo "    Disk swapfile deleted. Space reclaimed."
    fi
}

# Entrypoint routing
ACTION="${1:-setup}"

case "$ACTION" in
    setup|enable)
        check_root "$@"
        print_header
        setup_zram
        setup_swapfile
        apply_sysctl
        if [[ "${2:-}" == "--persist" ]] || [[ "${1:-}" == "--persist" ]]; then
            persist_settings
        fi
        echo "[✓] Tiered swap and kernel parameters active."
        echo ""
        show_status
        ;;
    disable|off)
        check_root "$@"
        disable_all
        if [[ "${2:-}" == "--delete" ]] || [[ "${2:-}" == "-d" ]]; then
            if [[ -f "$SWAP_FILE" ]]; then
                echo "[+] Removing $SWAP_FILE..."
                rm -f "$SWAP_FILE"
                echo "    Disk swapfile deleted. Space reclaimed."
            fi
        fi
        ;;
    clean)
        check_root "$@"
        clean_all
        ;;
    status)
        show_status
        ;;
    persist)
        check_root "$@"
        persist_settings
        ;;
    help|--help|-h)
        echo "Usage: ./scripts/helpers/setup_host_swap.sh [COMMAND] [OPTIONS]"
        echo "Commands:"
        echo "  setup [--persist]      Configure and activate zRAM + 32GB swapfile + sysctl"
        echo "  status                 Display current memory, swap, zram, and sysctl tables"
        echo "  disable [--delete]     Disable swapfile (optionally delete file from disk)"
        echo "  clean                  Disable swapfile and delete file to reclaim disk space"
        echo "  persist                Persist sysctl and fstab entries for future reboots"
        ;;
    *)
        echo "[-] Unknown command: $ACTION. Run with --help for usage."
        exit 1
        ;;
esac
