#!/usr/bin/env bash
# ==============================================================================
# TontOS Host & Build Disk Space Optimizer
# Safely reclaims storage while preserving built ROM images and core trees.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

HOST_AOSP_OUT="${AOSP_OUT:-$HOME/aosp/tontos/out}"
PROJECT_OUT="$ROOT_DIR/out"
CARGO_TARGET="$ROOT_DIR/target"

echo "=========================================================="
echo "  TontOS Disk Space Optimizer"
echo "=========================================================="
df -h "$ROOT_DIR" | tail -n 1 | awk '{print "  Current Available Space: " $4 " (" $5 " used)"}'
echo "=========================================================="

clean_obsolete_aosp_out() {
    if [[ -d "$HOST_AOSP_OUT" ]]; then
        local size
        size=$(du -sh "$HOST_AOSP_OUT" 2>/dev/null | awk '{print $1}')
        echo "[+] Removing obsolete host AOSP out directory ($size): $HOST_AOSP_OUT..."
        rm -rf "$HOST_AOSP_OUT"
        echo "[✓] Cleaned $HOST_AOSP_OUT"
    else
        echo "[-] No obsolete host AOSP out directory found at $HOST_AOSP_OUT."
    fi
}

prune_intermediates() {
    local intermediates="$PROJECT_OUT/soong/.intermediates"
    if [[ -d "$intermediates" ]]; then
        local size
        size=$(du -sh "$intermediates" 2>/dev/null | awk '{print $1}')
        echo "[+] Pruning Soong build intermediate cache ($size) while keeping target ROM images..."
        rm -rf "$intermediates"
        echo "[✓] Intermediate cache pruned."
    else
        echo "[-] No intermediate cache found at $intermediates."
    fi
}

clean_cargo() {
    if [[ -d "$CARGO_TARGET" ]]; then
        local size
        size=$(du -sh "$CARGO_TARGET" 2>/dev/null | awk '{print $1}')
        echo "[+] Cleaning Rust cargo target directory ($size)..."
        rm -rf "$CARGO_TARGET"
        echo "[✓] Cargo target cleaned."
    fi
}

clean_all() {
    clean_obsolete_aosp_out
    prune_intermediates
    clean_cargo
}

MODE="${1:---all}"

case "$MODE" in
    --clean-obsolete)
        clean_obsolete_aosp_out
        ;;
    --prune-intermediates)
        prune_intermediates
        ;;
    --cargo)
        clean_cargo
        ;;
    --all)
        clean_all
        ;;
    --help|-h)
        echo "Usage: ./bin/reclaim_disk_space.sh [OPTION]"
        echo "  --all                 Clean obsolete out dirs, intermediates, and cargo target"
        echo "  --clean-obsolete      Remove duplicate host ~/aosp/tontos/out (frees ~84GB)"
        echo "  --prune-intermediates Prune .intermediates in project out/ (keeps compiled ROMs)"
        echo "  --cargo               Clean cargo target/ directory"
        exit 0
        ;;
    *)
        echo "Unknown option: $MODE"
        exit 1
        ;;
esac

echo "=========================================================="
df -h "$ROOT_DIR" | tail -n 1 | awk '{print "  Updated Available Space: " $4 " (" $5 " used)"}'
echo "=========================================================="
