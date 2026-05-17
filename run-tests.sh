#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

RESULTS_FILE="test-results.txt"

echo "=== Factotum AI Test Suite ==="
echo ""

# Clean previous results
> "$RESULTS_FILE"

{
    echo "Factotum AI - Test Results"
    echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "=========================================="
    echo ""
} >> "$RESULTS_FILE"

# Build the project first (skip tests during build)
echo "[1/3] Building project..."
mvn clean package -DskipTests -q 2>&1 | tee -a "$RESULTS_FILE" || true
BUILD_EXIT=$?
if [ $BUILD_EXIT -ne 0 ]; then
    echo "" >> "$RESULTS_FILE"
    echo "BUILD FAILED (exit code: $BUILD_EXIT)" >> "$RESULTS_FILE"
    cat "$RESULTS_FILE"
    exit 1
fi
echo "[1/3] Build complete."

# Run tests for factotum-core module
echo ""
echo "[2/3] Running core tests..."
CORE_OUTPUT=$(mktemp)
mvn -pl factotum-core test 2>&1 | tee "$CORE_OUTPUT"
CORE_EXIT=${PIPESTATUS[0]}

{
    echo "--- Core Module Tests ---"
    cat "$CORE_OUTPUT"
    echo ""
} >> "$RESULTS_FILE"

# Run tests for factotum-cli module
echo "[3/3] Running CLI tests..."
CLI_OUTPUT=$(mktemp)
mvn -pl factotum-cli test 2>&1 | tee "$CLI_OUTPUT"
CLI_EXIT=${PIPESTATUS[0]}

{
    echo "--- CLI Module Tests ---"
    cat "$CLI_OUTPUT"
    echo ""
} >> "$RESULTS_FILE"

# Summary
echo ""
echo "=========================================="
echo "Test Results:"
echo "  Core module: exit code $CORE_EXIT"
echo "  CLI module:  exit code $CLI_EXIT"
echo ""

if [ $CORE_EXIT -eq 0 ] && [ $CLI_EXIT -eq 0 ]; then
    echo "All tests passed."
else
    if [ $CORE_EXIT -ne 0 ]; then
        echo "Core module tests FAILED (exit code: $CORE_EXIT)"
    fi
    if [ $CLI_EXIT -ne 0 ]; then
        echo "CLI module tests FAILED (exit code: $CLI_EXIT)"
    fi
fi

echo ""
echo "Full results saved to: $RESULTS_FILE"

# Append summary to results file
{
    echo "=========================================="
    echo "Summary:"
    if [ $CORE_EXIT -eq 0 ] && [ $CLI_EXIT -eq 0 ]; then
        echo "  Result: ALL PASSED"
    else
        echo "  Result: SOME FAILED"
        [ $CORE_EXIT -ne 0 ] && echo "  Core module: FAILED (exit code: $CORE_EXIT)"
        [ $CLI_EXIT -ne 0 ] && echo "  CLI module:  FAILED (exit code: $CLI_EXIT)"
    fi
} >> "$RESULTS_FILE"

cat "$RESULTS_FILE"

# Cleanup temp files
rm -f "$CORE_OUTPUT" "$CLI_OUTPUT"

if [ $CORE_EXIT -ne 0 ] || [ $CLI_EXIT -ne 0 ]; then
    exit 1
fi
