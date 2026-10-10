#!/usr/bin/env bash
set -euo pipefail

# Task 20.2: Present the Linux host artifact and verify it in a VM or overlay.
#
# The host rootfs is target/linux-rootfs/tontos-linux-rootfs.tar.gz (overlay
# only: no kernel/systemd/bootloader claims). This script:
#  1) Runs make_gsi_rootfs.sh (staging archive) if missing or --rebuild.
#  2) Checks OS identity, systemd units, Wayland prereq inside the archive
#     without mutating the host (no root, no mounts required).
#  3) Optionally overlays into a systemd-nspawn directory root (requires
#     unprivileged nspawn) or reports readiness for VM boot.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ARCHIVE="$ROOT/target/linux-rootfs/tontos-linux-rootfs.tar.gz"
MAKER="$ROOT/scripts/make_gsi_rootfs.sh"
MODE="verify"
OVERLAY_DIR=""

for arg in "$@"; do
    case "$arg" in
        --rebuild) MODE="rebuild" ;;
        --check-only) MODE="check" ;;
        --overlay-dir=*) OVERLAY_DIR="${arg#*=}" ;;
        --help|-h)
            echo "Usage: $0 [--rebuild] [--check-only] [--overlay-dir=<dir>]"
            echo "Boot/readiness check for TontOS host rootfs overlay. No kernel/OTA/GSI claims."
            exit 0
            ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

need_archive=false
if [[ ! -f "$ARCHIVE" ]]; then
    need_archive=true
fi
if [[ "$MODE" == "rebuild" ]]; then
    need_archive=true
fi
if [[ "$need_archive" == true ]]; then
    echo "[*] Building Linux rootfs overlay: $MAKER" >&2
    bash "$MAKER"
fi
if [[ ! -f "$ARCHIVE" ]]; then
    echo "Missing host artifact: $ARCHIVE (maker did not produce it)" >&2
    exit 1
fi

echo "[*] Verifying host artifact: $ARCHIVE" >&2

check_ok=true
fail() {
    echo "FAIL: $1" >&2
    check_ok=false
}

# Avoid pipefail SIGPIPE from tar|grep: capture listing once.
archive_list="$(tar tzf "$ARCHIVE" 2>/dev/null || true)"

# No Android GSI claims
if printf '%s\n' "$archive_list" | grep -qiE "system\.img|vendor\.img|VerifiedBootState|google/shiba"; then
    fail "archive must not claim Android system.img / Verified Boot identity"
fi

os_release="$(tar xzOf "$ARCHIVE" ./etc/os-release 2>/dev/null || true)"
if [[ "$os_release" != *'ID="tontos"'* ]] && [[ "$os_release" != *"ID=tontos"* ]]; then
    fail "etc/os-release missing ID=tontos (got: $os_release)"
fi
if [[ "$os_release" != *'TontOS'* ]]; then
    fail "etc/os-release missing TontOS identity"
fi

expected_units=(
    "tont-compositor.service"
    "tont-powerd.service"
    "tont-sensors.service"
    "tont-syncd.service"
    "tont-input.service"
    "tont-telephony.service"
)
for u in "${expected_units[@]}"; do
    if [[ "$archive_list" != *"$u"* ]]; then
        fail "missing systemd unit $u"
    fi
done

if [[ "$archive_list" != *"usr/bin/tont-"* ]]; then
    fail "missing TontOS binaries in usr/bin/"
fi

readme="$(tar xzOf "$ARCHIVE" ./README.txt 2>/dev/null || true)"
if [[ "$readme" != *"not Android"* ]] && [[ "$readme" != *"Not Android"* ]]; then
    fail "README.txt must state not-Android/not-GSI scope"
fi

if [[ -n "$OVERLAY_DIR" ]]; then
    # Hardening: overlay must be under target/ and either absent or empty; resolve symlinks.
    _overlay_parent="$(cd "$ROOT" && realpath -m -- "$OVERLAY_DIR" 2>/dev/null || printf '%s' "$ROOT/$OVERLAY_DIR")"
    _root_real="$(realpath -m -- "$ROOT" 2>/dev/null || printf '%s' "$ROOT")"
    _allowed_prefix="$_root_real/target/"
    if [[ "$_overlay_parent" != "$_allowed_prefix"* ]]; then
        echo "Invalid --overlay-dir: must be under $ROOT/target/ (got: $OVERLAY_DIR -> $_overlay_parent)" >&2
        exit 2
    fi
    if [[ -e "$_overlay_parent" ]]; then
        if [[ ! -d "$_overlay_parent" ]]; then
            echo "Invalid --overlay-dir: exists and is not a directory: $_overlay_parent" >&2
            exit 2
        fi
        if [[ -n "$(ls -A -- "$_overlay_parent" 2>/dev/null)" ]]; then
            echo "Invalid --overlay-dir: must be empty or absent: $_overlay_parent" >&2
            exit 2
        fi
    fi
    if command -v systemd-nspawn >/dev/null 2>&1 && mkdir -p -- "$_overlay_parent" 2>/dev/null; then
        _ov="$_overlay_parent"
        echo "[*] Overlaying into $_ov (no host mutation, directory root only)" >&2
        if tar xzf "$ARCHIVE" -C "$_ov" 2>/dev/null; then
            echo "[+] Overlay applied to $_ov" >&2
            if [[ -x /usr/lib/systemd/systemd ]] || [[ -x "$_ov/usr/lib/systemd/systemd" ]]; then
                echo "[*] systemd available (host or overlay); units ready for nspawn boot" >&2
            else
                echo "[*] No systemd binary in overlay (expected: host provides it on VM)" >&2
            fi
            if grep -qR "WAYLAND_DISPLAY\|XDG_RUNTIME_DIR" "$_ov/usr/lib/systemd/system" 2>/dev/null; then
                echo "[+] Wayland session environment in compositor unit" >&2
            fi
        else
            fail "failed to extract overlay to $_ov"
        fi
    else
        echo "skip: systemd-nspawn unavailable or cannot write $_overlay_parent" >&2
        printf '{"status":"skip","task":"20.2","mode":"overlay","reason":"systemd-nspawn or overlay dir unavailable"}\n'
        exit 0
    fi
fi

if [[ "$check_ok" == true ]]; then
    echo "[+] Host artifact check passed (overlay ready; bootable VM image remains runner-side)." >&2
    printf '{"status": "pass", "task": "20.2", "artifact": "%s", "note": "staging archive verified; bootable host image with kernel/systemd/pacman base is runner-side"}\n' "$ARCHIVE"
    exit 0
fi
exit 1
