#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Stopping Factotum AI ==="

# Stop the Quarkus JVM process — exclude this script's own PID and parent shell
MY_PID=$$
PARENT_PID=${PPID:-0}

QUARKUS_PIDS=$(pgrep -f "factotum-core-dev.jar" 2>/dev/null | grep -vE "^${MY_PID}$|^${PARENT_PID}$" || true)
if [ -n "$QUARKUS_PIDS" ]; then
    echo "Stopping Core..."
    for pid in $QUARKUS_PIDS; do
        kill -TERM "$pid" 2>/dev/null || true
    done
    # Wait up to 10s for graceful shutdown
    for i in $(seq 1 10); do
        QUARKUS_PIDS=$(pgrep -f "factotum-core-dev.jar" 2>/dev/null | grep -vE "^${MY_PID}$|^${PARENT_PID}$" || true)
        [ -z "$QUARKUS_PIDS" ] && break
        sleep 1
    done
    # Force kill if still running
    QUARKUS_PIDS=$(pgrep -f "factotum-core-dev.jar" 2>/dev/null | grep -vE "^${MY_PID}$|^${PARENT_PID}$" || true)
    if [ -n "$QUARKUS_PIDS" ]; then
        for pid in $QUARKUS_PIDS; do
            kill -KILL "$pid" 2>/dev/null || true
        done
    fi
    echo "Core stopped."
else
    echo "No Core process found."
fi

# Stop docker-compose services
echo "Stopping PostgreSQL..."
docker compose down --remove-orphans 2>/dev/null || true

echo "All services stopped."
