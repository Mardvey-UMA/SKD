#!/bin/bash
set -e

SERVICES=(auth-service user-service user-interactions-service subscription-service feed-service api-gateway)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Building all services..."
for svc in "${SERVICES[@]}"; do
    echo ""
    echo "=== Building $svc ==="
    cd "$SCRIPT_DIR/$svc"
    ./gradlew bootJar -x test --no-daemon
    rm -f build/libs/*-plain.jar
    echo "=== $svc built successfully ==="
done

echo ""
echo "All services built successfully."
