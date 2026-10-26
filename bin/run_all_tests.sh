#!/usr/bin/env bash
# ==============================================================================
# TontOS Unified System Test Suite (Forwarder)
# Runs the full verification suite (or fast mode with --fast).
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/run_full_suite.sh" "$@"
