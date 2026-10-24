#!/usr/bin/env bash
# ==============================================================================
# TontOS Universal ARM64 GSI Build Shortcut
# Delegates to the unified build_gsi.sh script
# ==============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/build_gsi.sh" --arch=arm64 "$@"
