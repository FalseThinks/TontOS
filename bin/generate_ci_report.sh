#!/usr/bin/env bash
# Forwarding wrapper for CI compatibility
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/helpers/generate_ci_report.sh" "$@"
