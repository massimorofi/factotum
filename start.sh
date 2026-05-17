#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Starting Factotum AI ==="

# Start PostgreSQL via docker-compose
echo "[1/2] Starting PostgreSQL (pgmq)..."
docker compose up -d postgres

# Wait for DB health
echo "Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
    if docker compose exec -T postgres pg_isready -U factotum > /dev/null 2>&1; then
        echo "PostgreSQL is ready."
        break
    fi
    sleep 1
done

# Build and start the Quarkus core
echo "[2/2] Starting Factotum Core (Quarkus)..."
mvn -pl factotum-core quarkus:dev -q &
CORE_PID=$!
echo "Core started. Listening on http://localhost:8080"

# Keep script alive so Ctrl+C can be caught
trap 'stop.sh' EXIT
wait $CORE_PID
