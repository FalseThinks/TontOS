#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    cat <<'EOF'
Usage: scripts/patch_play_integrity.sh

Unsupported. This tool does not change Android properties, device identity,
Verified Boot state, or Play Integrity / CTS results.
EOF
    exit 0
fi

cat >&2 <<'EOF'
ERROR: Play Integrity / CTS spoofing is unsupported.

This script will not change Android properties, device identity, bootloader or
Verified Boot state, or Google services. System properties are not a means to
establish hardware-backed attestation or platform certification.
EOF
exit 1
