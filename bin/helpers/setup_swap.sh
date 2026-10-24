#!/usr/bin/env bash
# ==============================================================================
# TontOS Automated Swap & Memory Auto-Mount Engine (BTRFS & Ext4 Native)
# Automatically creates and enables /swapfile_aosp (32GB) with NoCOW on BTRFS.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SWAP_FILE="/swapfile_aosp"
TARGET_USER="${SUDO_USER:-${USER:-paproka}}"

is_swap_active() {
    awk -v sf="$SWAP_FILE" '$1 == sf {found=1} END {exit !found}' /proc/swaps 2>/dev/null
}

if is_swap_active; then
    echo "[+] Swapfile $SWAP_FILE is already active."
    exit 0
fi

SWAP_SETUP_CMD="
set -euo pipefail
SWAP_FILE=\"$SWAP_FILE\"

# Check if existing swapfile works
if ! swapon -p 10 \"\$SWAP_FILE\" 2>/dev/null; then
    echo \"[*] Preparing valid NoCOW swapfile on BTRFS...\"
    swapoff \"\$SWAP_FILE\" 2>/dev/null || true
    rm -f \"\$SWAP_FILE\"
    if command -v btrfs >/dev/null 2>&1 && [[ \"\$(stat -f -c %T / 2>/dev/null)\" == \"btrfs\" ]]; then
        btrfs filesystem mkswapfile --size 32g \"\$SWAP_FILE\"
    else
        truncate -s 0 \"\$SWAP_FILE\"
        chattr +C \"\$SWAP_FILE\" 2>/dev/null || true
        fallocate -l 32G \"\$SWAP_FILE\"
        chmod 600 \"\$SWAP_FILE\"
        mkswap \"\$SWAP_FILE\"
    fi
    swapon -p 10 \"\$SWAP_FILE\"
fi

# Ensure fstab entry for automatic mount on every system boot
if ! grep -q \"\$SWAP_FILE\" /etc/fstab 2>/dev/null; then
    echo \"\$SWAP_FILE none swap defaults,pri=10 0 0\" >> /etc/fstab
    echo \"[+] Added \$SWAP_FILE to /etc/fstab for boot persistence.\"
fi

# Ensure passwordless sudo rule for swapon/swapoff for automated background builds
SWAPON_BIN=\$(which swapon || echo \"/usr/bin/swapon\")
SWAPOFF_BIN=\$(which swapoff || echo \"/usr/bin/swapoff\")
cat <<EOF > /etc/sudoers.d/aosp_swap
$TARGET_USER ALL=(ALL) NOPASSWD: \$SWAPON_BIN, \$SWAPOFF_BIN
EOF
chmod 0440 /etc/sudoers.d/aosp_swap
echo \"[+] Configured passwordless swapon rule in /etc/sudoers.d/aosp_swap.\"
"

# 1. Direct swapon attempt
if swapon -p 10 "$SWAP_FILE" 2>/dev/null; then
    echo "[+] Activated $SWAP_FILE directly."
    exit 0
fi

# 2. Passwordless sudo attempt
if sudo -n bash -c "$SWAP_SETUP_CMD" 2>/dev/null; then
    echo "[+] Configured and activated $SWAP_FILE via passwordless sudo."
    exit 0
fi

# 3. Graphical desktop elevation via pkexec (Polkit KDE / GNOME)
if command -v pkexec >/dev/null 2>&1 && [[ -n "${DISPLAY:-}" || -n "${WAYLAND_DISPLAY:-}" ]]; then
    echo "[*] Triggering desktop authorization modal via Polkit agent..."
    if pkexec bash -c "$SWAP_SETUP_CMD"; then
        echo "[+] Configured and activated $SWAP_FILE via Polkit."
        exit 0
    fi
fi

# 4. Interactive terminal sudo prompt
if [[ -t 0 ]] && command -v sudo >/dev/null 2>&1; then
    echo "[*] Requesting sudo password in terminal..."
    if sudo bash -c "$SWAP_SETUP_CMD"; then
        echo "[+] Configured and activated $SWAP_FILE via sudo."
        exit 0
    fi
fi

echo "[!] Failed to activate $SWAP_FILE automatically."
exit 1
