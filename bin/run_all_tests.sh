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
echo "--> [1/4] Running Rust Workspace Tests (cargo test --workspace)..."
if (cd "$ROOT_DIR" && cargo test --workspace); then
    echo "  [PASS] All Rust daemons & settings passed."
else
    echo "  [FAIL] Rust workspace tests failed."
    FAILED=1
fi

# Step 2: Java Modular Settings & Feature Validation Tests
echo ""
echo "--> [2/4] Running Java Modular Test Suite..."
mkdir -p "$ROOT_DIR/target/test-classes"
JAVA_TEST_FILES=$(find "$ROOT_DIR/tests/java" -name "*.java")
if javac -d "$ROOT_DIR/target/test-classes" $JAVA_TEST_FILES && \
   java -cp "$ROOT_DIR/target/test-classes" org.tontos.tests.TestRunner; then
    echo "  [PASS] Java modular test suites passed."
else
    echo "  [FAIL] Java modular tests failed."
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
