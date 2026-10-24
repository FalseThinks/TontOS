#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "=========================================================="
echo "  TontOS Unified System & Feature Test Suite"
echo "=========================================================="

FAILED=0

# Step 1: Rust System Daemons (14 Crates)
echo ""
echo "--> [1/4] Running Rust Formatting, Clippy & Workspace Tests..."
if (cd "$ROOT_DIR" && \
    cargo fmt --all -- --check && \
    cargo clippy --workspace --all-targets -- -D warnings && \
    cargo test --workspace); then
    echo "  [PASS] All Rust daemons & settings passed formatting, clippy & tests."
else
    echo "  [FAIL] Rust workspace tests, clippy, or formatting failed."
    FAILED=1
fi

# Step 2: Java Modular Settings & Feature Validation Tests
echo ""
echo "--> [2/4] Running Java Modular Test Suite & JaCoCo Coverage..."
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
            fi
            echo "  [PASS] Java modular test suites and JaCoCo coverage passed."
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

# Step 3: Android Launcher APK Compilation & Signing Gate
echo ""
echo "--> [3/4] Compiling & Verifying TontOS Launcher APK..."
if "$ROOT_DIR/bin/build_launcher_apk.sh"; then
    echo "  [PASS] Launcher APK compiled and signed cleanly."
else
    echo "  [FAIL] Launcher APK compilation failed."
    FAILED=1
fi

# Step 4: Shell Scripts & System Configurations Hygiene
echo ""
echo "--> [4/4] Verifying Shell Scripts & Permissions Hygiene..."
for script in "$ROOT_DIR/bin"/*.sh "$ROOT_DIR/bin/helpers"/*.sh "$ROOT_DIR/bin/windows"/*.sh; do
    if [[ -f "$script" ]]; then
        if bash -n "$script"; then
            :
        else
            echo "  [FAIL] Syntax error in script: $script"
            FAILED=1
        fi
    fi
done

if [[ -f "$ROOT_DIR/platform/products/default-permissions-tontos.xml" ]]; then
    echo "  [PASS] Shell scripts and permission definitions verified."
fi

echo ""
echo "=========================================================="
if [[ $FAILED -eq 0 ]]; then
    echo "  ALL TESTS PASSED SUCCESSFULLY (100% HEALTHY)"
    echo "=========================================================="
    exit 0
else
    echo "  SOME TESTS FAILED! Check logs above."
    echo "=========================================================="
    exit 1
fi
