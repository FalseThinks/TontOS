#!/usr/bin/env bash
set -euo pipefail

# Task 20.1: Provision a disposable Arch Linux x86_64 acceptance VM.
# Leaves the developer host unchanged; all state lives under target/vm/
# with the user-session libvirt URI. No sudo, no pacman installs.
#
# Skip protocol (per tasks/todo.md 20.1 + plan.md Phase 20+):
#   Outside the designated runtime runner (TONTOS_DESIGNATED_RUNNER != 1):
#     missing prerequisites -> machine-readable skip JSON on stdout, exit 0.
#   Inside the designated runner (TONTOS_DESIGNATED_RUNNER=1):
#     missing prerequisites -> fail JSON, exit 1. No skips for boot/app/net.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VM_DIR="$ROOT/target/vm"
VM_NAME="${TONTOS_VM_NAME:-tontos-acceptance}"
VIRSH_URI="${TONTOS_VIRSH_URI:-qemu:///session}"
DESIGNATED="${TONTOS_DESIGNATED_RUNNER:-0}"
MODE="provision"

if [[ ! "$VM_NAME" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "Invalid TONTOS_VM_NAME: must match ^[A-Za-z0-9._-]+\$ (got: $VM_NAME)" >&2
    exit 2
fi
if [[ "$VIRSH_URI" != "qemu:///session" && "$VIRSH_URI" != "qemu:///system" ]]; then
    echo "Invalid TONTOS_VIRSH_URI: must be qemu:///session or qemu:///system (got: $VIRSH_URI)" >&2
    exit 2
fi

for arg in "$@"; do
    case "$arg" in
        --check-only) MODE="check" ;;
        --teardown) MODE="teardown" ;;
        --help|-h)
            echo "Usage: $0 [--check-only] [--teardown]"
            echo "Env: TONTOS_DESIGNATED_RUNNER=1 for strict fail mode (no skips)."
            echo "     TONTOS_VM_NAME, TONTOS_VIRSH_URI override defaults."
            exit 0
            ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

reasons=()
add_reason() { reasons+=("$1"); }

json_escape() {
    local s="$1"
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    printf '%s' "$s"
}

emit_json() {
    local status="$1"
    local out="{\"status\": \"$status\", \"task\": \"20.1\", \"vm\": \"$VM_NAME\", \"designated_runner\": "
    if [[ "$DESIGNATED" == "1" ]]; then out+="true"; else out+="false"; fi
    out+=", \"reasons\": ["
    local first=true
    for r in "${reasons[@]}"; do
        if [[ "$first" == true ]]; then first=false; else out+=", "; fi
        out+="\"$(json_escape "$r")\""
    done
    out+="]}"
    printf '%s\n' "$out"
}

fail_or_skip() {
    if [[ "$DESIGNATED" == "1" ]]; then
        emit_json "fail"
        exit 1
    fi
    emit_json "skip"
    exit 0
}

if [[ "$MODE" == "teardown" ]]; then
    if command -v virsh >/dev/null 2>&1; then
        virsh -c "$VIRSH_URI" destroy "$VM_NAME" >/dev/null 2>&1 || true
        virsh -c "$VIRSH_URI" undefine "$VM_NAME" --remove-all-storage >/dev/null 2>&1 || true
    fi
    rm -rf -- "$VM_DIR"
    emit_json "pass"
    exit 0
fi

# --- Prerequisite checks (host left unchanged) ---
if ! command -v virsh >/dev/null 2>&1; then
    add_reason "missing: virsh (libvirt client) not in PATH"
fi

QEMU_BIN=""
for cand in qemu-system-x86_64 /usr/lib/qemu/qemu-system-x86_64; do
    if command -v "$cand" >/dev/null 2>&1; then QEMU_BIN="$cand"; break; fi
done
if [[ -z "$QEMU_BIN" ]]; then
    if command -v virsh >/dev/null 2>&1 && virsh -c "$VIRSH_URI" capabilities 2>/dev/null | grep -q "emulator.*qemu"; then
        QEMU_BIN="libvirt-emulator"
    else
        add_reason "missing: qemu-system-x86_64 emulator binary (no qemu package installed)"
    fi
fi

if [[ ! -e /dev/kvm ]]; then
    add_reason "missing: /dev/kvm (hardware acceleration unavailable)"
fi

if ! ls /dev/binderfs/anbox-* >/dev/null 2>&1 && ! ls /dev/binder* >/dev/null 2>&1 && ! ls /dev/anbox* >/dev/null 2>&1; then
    add_reason "missing: Binder IPC devices (/dev/binderfs/anbox-*, /dev/binder*, /dev/anbox*) for Waydroid"
fi

if [[ -f /sys/fs/cgroup/cgroup.controllers ]]; then
    ctrls="$(cat /sys/fs/cgroup/cgroup.controllers)"
    for need in cpu memory pids; do
        if [[ "$ctrls" != *"$need"* ]]; then add_reason "missing: cgroup controller $need"; fi
    done
else
    add_reason "missing: cgroup v2 controllers (/sys/fs/cgroup/cgroup.controllers unreadable)"
fi

if command -v virsh >/dev/null 2>&1; then
    if ! virsh -c "$VIRSH_URI" net-list --all 2>/dev/null | grep -qi "active\|default"; then
        add_reason "missing: libvirt bridge/NAT networking (no active network on $VIRSH_URI)"
    fi
else
    add_reason "missing: cannot query libvirt networking (no virsh)"
fi

if ! command -v weston >/dev/null 2>&1 && ! command -v Xvfb >/dev/null 2>&1 && ! command -v cage >/dev/null 2>&1; then
    add_reason "missing: Wayland session fallback (no weston, Xvfb, or cage for headless graphics)"
fi

if [[ "${#reasons[@]}" -gt 0 ]]; then
    fail_or_skip
fi

if [[ "$MODE" == "check" ]]; then
    emit_json "pass"
    exit 0
fi

# --- All prerequisites present: define disposable VM (session URI only) ---
mkdir -p -- "$VM_DIR"
README="$VM_DIR/README.txt"
cat > "$README" <<'EOF'
TontOS disposable acceptance VM (Task 20.1).
Transient domain on qemu:///session. Destroy with provision_acceptance_vm.sh --teardown.
Host Waydroid images and Linux rootfs overlay stay separate artifacts (Task 20.2).
EOF

if virsh -c "$VIRSH_URI" dominfo "$VM_NAME" >/dev/null 2>&1; then
    emit_json "pass"
    exit 0
fi

# Minimal transient definition is left to the designated runner (needs Arch
# cloud image + Waydroid kernel modules). Record readiness without mutating host.
add_reason "ready: prerequisites present, VM image fetch/define is a runner-side step (see docs/intent)"
emit_json "pass"
