#!/usr/bin/env bash
# ==============================================================================
# TontOS Security Auditing Gate: Semgrep SAST & Trivy Vulnerability Scanner
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT_DIR"

SEMGREP_BIN="$(command -v semgrep || echo "$HOME/.local/bin/semgrep")"
TRIVY_BIN="$(command -v trivy || echo "")"
DOCKER_BIN="$(command -v docker || echo "")"

FAILED=0

echo "----------------------------------------------------------"
echo "  1. Running Semgrep SAST Static Analysis..."
echo "----------------------------------------------------------"
if [[ -x "$SEMGREP_BIN" ]]; then
    if "$SEMGREP_BIN" scan --config auto \
        --exclude-rule yaml.github-actions.security.github-actions-mutable-action-tag.github-actions-mutable-action-tag \
        --exclude-rule package_managers.dependabot.dependabot-missing-cooldown.dependabot-missing-cooldown \
        --exclude-rule java.android.security.exported_activity.exported_activity \
        --exclude-rule java.lang.security.audit.command-injection-process-builder.command-injection-process-builder \
        --exclude target \
        --exclude tools \
        --exclude apps \
        --exclude .git \
        --exclude .ccache \
        --error \
        --quiet \
        .; then
        echo "  [PASS] Semgrep SAST analysis completed with 0 findings."
    else
        echo "  [FAIL] Semgrep detected security findings."
        FAILED=1
    fi
else
    echo "  [WARN] Semgrep not found; skipping SAST scan."
fi

echo ""
echo "----------------------------------------------------------"
echo "  2. Running Trivy Vulnerability & Secret Scanner..."
echo "----------------------------------------------------------"
if [[ -n "$TRIVY_BIN" ]]; then
    if "$TRIVY_BIN" fs \
        --skip-dirs target,.git,tools,apps,.ccache \
        --severity CRITICAL,HIGH \
        --exit-code 1 \
        .; then
        echo "  [PASS] Trivy filesystem scan completed with 0 high/critical vulnerabilities."
    else
        echo "  [FAIL] Trivy detected high or critical vulnerabilities."
        FAILED=1
    fi
elif [[ -n "$DOCKER_BIN" ]] && "$DOCKER_BIN" info >/dev/null 2>&1; then
    if "$DOCKER_BIN" run --rm \
        -v "$ROOT_DIR:/src:ro" \
        -v "$HOME/.cache/trivy:/root/.cache/" \
        aquasec/trivy:latest fs \
        --skip-dirs target,.git,tools,apps,.ccache \
        --severity CRITICAL,HIGH \
        --exit-code 1 \
        /src; then
        echo "  [PASS] Trivy (Docker) filesystem scan completed with 0 high/critical vulnerabilities."
    else
        echo "  [FAIL] Trivy detected high or critical vulnerabilities."
        FAILED=1
    fi
else
    echo "  [WARN] Neither Trivy nor Docker available; skipping Trivy scan."
fi

exit "$FAILED"
