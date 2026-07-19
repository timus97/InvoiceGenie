#!/usr/bin/env bash
# InvoiceGenie — start local dev server (H2 in-memory, no Postgres)
# Usage: ./scripts/dev-up.sh [--port 8082] [--skip-build]
set -euo pipefail

PORT=8080
SKIP_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      PORT="${2:?}"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [--port PORT] [--skip-build]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> InvoiceGenie local dev (profile=dev, H2 in-memory)"
echo "    Port: $PORT"
echo "    Repo: $ROOT"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java not found on PATH. Install JDK 17+." >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: mvn not found on PATH. Install Maven 3.9+." >&2
  exit 1
fi

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "==> mvn clean install -DskipTests"
  mvn clean install -DskipTests
  echo "==> mvn -pl ar-bootstrap -am compile -DskipTests"
  mvn -pl ar-bootstrap -am compile -DskipTests
fi

echo "==> Starting Quarkus (Ctrl+C to stop)"
echo "    Health:  http://localhost:${PORT}/q/health"
echo "    Swagger: http://localhost:${PORT}/q/swagger-ui/"
echo "    API:     http://localhost:${PORT}/api/v1/invoices"
echo ""

exec mvn -pl ar-bootstrap \
  -Dquarkus.profile=dev \
  -Dquarkus.http.port="${PORT}" \
  -Dquarkus.kafka.devservices.enabled=false \
  quarkus:dev
