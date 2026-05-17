#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Ensure the CLI module is built
if [ ! -f factotum-cli/target/factotum-cli-1.0-JAVA.jar ]; then
    echo "CLI JAR not found. Building..."
    mvn package -pl factotum-cli -am -DskipTests -q
fi

exec java -jar factotum-cli/target/factotum-cli-1.0-JAVA.jar "$@"
