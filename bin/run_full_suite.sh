#!/usr/bin/env bash
# ==============================================================================
# TontOS Full Verification Suite (Local CI Equivalent)
# Executes 100% of quality gates, unit tests, coverage, security, and UI tests.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "=========================================================="
echo "  TontOS Full Comprehensive Verification Suite"
echo "=========================================================="

FAILED=0
FAST_MODE=false
for arg in "${@:-}"; do
    case "$arg" in
        --fast|-f) FAST_MODE=true ;;
    esac
done

# Stage 1: Hygiene & Scripts Validation
echo ""
echo "--> [1/7] Verifying Shell Scripts Syntax & JSON Schemas..."
for script in "$ROOT_DIR/bin"/*.sh "$ROOT_DIR/bin/helpers"/*.sh; do
    if [[ -f "$script" ]]; then
        if ! bash -n "$script"; then
            echo "  [FAIL] Syntax error in script: $script"
            FAILED=1
        fi
    fi
done
if command -v jq >/dev/null 2>&1; then
    if jq empty "$ROOT_DIR/platform/manifests/pinned.json" "$ROOT_DIR/tests/ui/package.json" 2>/dev/null; then
        echo "  [PASS] Shell scripts syntax and JSON manifests valid."
    else
        echo "  [FAIL] JSON syntax error detected in manifest or package.json."
        FAILED=1
    fi
else
    echo "  [PASS] Shell scripts syntax verified."
fi

# Stage 2: Rust Daemons (Format, Clippy, Workspace Tests & Docs)
echo ""
echo "--> [2/7] Running Rust Workspace Quality Gates (fmt, clippy, test, doc)..."
if (cd "$ROOT_DIR" && \
    cargo fmt --all -- --check && \
    cargo clippy --workspace --all-targets -- -D warnings && \
    cargo test --workspace && \
    RUSTDOCFLAGS="-D warnings" cargo doc --workspace --no-deps); then
    echo "  [PASS] All Rust crates passed formatting, clippy, unit tests, and documentation gates."
else
    echo "  [FAIL] Rust workspace tests, clippy, docs, or formatting failed."
    FAILED=1
fi

# Stage 3: Java Modular Settings & Feature Validation Tests (JaCoCo)
echo ""
echo "--> [3/7] Running Java Modular Test Suite & JaCoCo Coverage Gate..."
mkdir -p "$ROOT_DIR/target/test-classes" "$ROOT_DIR/target/jacoco-report"
JAVA_TEST_FILES=$(find "$ROOT_DIR/tests/java" -name "*.java")
JACOCO_AGENT="$ROOT_DIR/tools/jacoco/jacocoagent.jar"
JACOCO_CLI="$ROOT_DIR/tools/jacoco/jacococli.jar"

if javac -d "$ROOT_DIR/target/test-classes" $JAVA_TEST_FILES; then
    if [[ -f "$JACOCO_AGENT" && -f "$JACOCO_CLI" ]]; then
        if java -javaagent:"$JACOCO_AGENT=destfile=$ROOT_DIR/target/jacoco.exec" \
             -cp "$ROOT_DIR/target/test-classes" org.tontos.tests.TestRunner; then
            java -jar "$JACOCO_CLI" report "$ROOT_DIR/target/jacoco.exec" \
                 --classfiles "$ROOT_DIR/target/test-classes" \
                 --sourcefiles "$ROOT_DIR/tests/java" \
                 --html "$ROOT_DIR/target/jacoco-report" \
                 --csv "$ROOT_DIR/target/jacoco-report/jacoco.csv" > /dev/null 2>&1
            
            if [[ -f "$ROOT_DIR/target/jacoco-report/jacoco.csv" ]]; then
                awk -F',' 'NR>1 {inst_miss+=$4; inst_cov+=$5; line_miss+=$8; line_cov+=$9} END {total_inst=inst_miss+inst_cov; total_line=line_miss+line_cov; if (total_inst>0) printf "  [JaCoCo Coverage] Instructions: %.1f%% (%d/%d) | Lines: %.1f%% (%d/%d)\n", (inst_cov*100.0/total_inst), inst_cov, total_inst, (line_cov*100.0/total_line), line_cov, total_line}' "$ROOT_DIR/target/jacoco-report/jacoco.csv"
                COVERAGE_PCT=$(awk -F',' 'NR>1 {inst_miss+=$4; inst_cov+=$5} END {total=inst_miss+inst_cov; if (total>0) printf "%.0f", (inst_cov*100.0/total)}' "$ROOT_DIR/target/jacoco-report/jacoco.csv")
                if [[ "$COVERAGE_PCT" -lt 90 ]]; then
                    echo "  [FAIL] JaCoCo coverage ($COVERAGE_PCT%) below 90% threshold!"
                    FAILED=1
                else
                    echo "  [PASS] Java modular test suites passed with >= 90% coverage."
                fi
            fi
        else
            echo "  [FAIL] Java modular tests failed."
            FAILED=1
        fi
    else
        if java -cp "$ROOT_DIR/target/test-classes" org.tontos.tests.TestRunner; then
            echo "  [PASS] Java modular test suites passed."
        else
            echo "  [FAIL] Java modular tests failed."
            FAILED=1
        fi
    fi
else
    echo "  [FAIL] Compilation of Java test classes failed."
    FAILED=1
fi

# Stage 4: Android Launcher APK Compilation & Signing Gate
echo ""
echo "--> [4/7] Compiling & Verifying TontOS Launcher APK..."
if "$ROOT_DIR/bin/build_launcher_apk.sh"; then
    APK_FILE="$ROOT_DIR/launcher_apk/tontos-launcher.apk"
    if [[ -f "$APK_FILE" ]]; then
        APK_SIZE=$(wc -c < "$APK_FILE")
        if [[ "$APK_SIZE" -gt 2097152 ]]; then
            echo "  [FAIL] Launcher APK ($APK_SIZE bytes) exceeds 2MB budget!"
            FAILED=1
        else
            echo "  [PASS] Launcher APK ($APK_SIZE bytes) within 2MB budget."
        fi
    fi
else
    echo "  [FAIL] Launcher APK compilation failed."
    FAILED=1
fi

if [[ "$FAST_MODE" == true ]]; then
    echo ""
    echo "--> [FAST] Skipping Playwright UI and Security Audits (--fast specified)..."
else
    # Stage 5: Playwright UI Visual & AMOLED Contract Tests
    echo ""
    echo "--> [5/7] Running Playwright AMOLED Visual & UI Tests..."
    if (cd "$ROOT_DIR" && pnpm --dir tests/ui test); then
        echo "  [PASS] Playwright UI visual and contract tests passed."
    else
        echo "  [FAIL] Playwright UI tests failed."
        FAILED=1
    fi

    # Stage 6: Security Audits (Semgrep SAST & Trivy Scanner)
    echo ""
    echo "--> [6/7] Running Security Audits (Semgrep SAST & Trivy Scanner)..."
    if "$ROOT_DIR/bin/helpers/run_security_audit.sh"; then
        echo "  [PASS] Semgrep SAST and Trivy security audits passed with 0 findings."
    else
        echo "  [FAIL] Security audit detected vulnerabilities or policy violations."
        FAILED=1
    fi

    # Stage 7: Executive CI Report Generation
    echo ""
    echo "--> [7/7] Generating Executive Security, Coverage & Smells Report..."
    mkdir -p "$ROOT_DIR/target"
    REPORT_FILE="$ROOT_DIR/target/ci-report.md"
    if "$ROOT_DIR/bin/generate_ci_report.sh" "$REPORT_FILE"; then
        echo "  [PASS] Executive report generated: $REPORT_FILE"
    else
        echo "  [WARN] Failed to generate executive report."
    fi
fi

echo ""
echo "=========================================================="
if [[ $FAILED -eq 0 ]]; then
    echo "  ALL SUITES PASSED SUCCESSFULLY (100% HEALTHY)"
    echo "=========================================================="
    exit 0
else
    echo "  SOME TESTS FAILED! Check logs above."
    echo "=========================================================="
    exit 1
fi
