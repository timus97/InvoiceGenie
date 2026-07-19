#!/usr/bin/env bash
# Start Quarkus (dev/H2) + Next.js GUI.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> InvoiceGenie dual-run (API :8080 + UI :3000)"

mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.kafka.devservices.enabled=false quarkus:dev &
API_PID=$!
trap 'kill $API_PID 2>/dev/null || true' EXIT

sleep 3
cd "$ROOT/web"
[ -d node_modules ] || npm install
[ -f .env.local ] || cp .env.example .env.local
npm run dev