#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building Factotum AI ==="
mvn clean package -DskipTests

echo ""
echo "Build complete."
echo "  Core JAR:   factotum-core/target/quarkus-app/quarkus-run.jar"
echo "  CLI binary: mvn -pl factotum-cli native:compile"
