#!/usr/bin/env bash
# InvoiceGenie — start local dev server against Docker PostgreSQL
# Prerequisites: docker compose up -d postgres adminer
# Usage: ./scripts/dev-up.sh [--port 8082] [--skip-build]
set -euo pipefail

PORT=8080
SKIP_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) PORT="${2:?}"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    -h|--help) echo "Usage: $0 [--port PORT] [--skip-build]"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> InvoiceGenie local dev (profile=dev, PostgreSQL)"
echo "    Port: $PORT"
echo "    Repo: $ROOT"

# Load credentials from .env if present
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source <(grep -E '^(POSTGRES_|QUARKUS_DATASOURCE_)' .env | sed 's/\r$//')
  set +a
fi

export QUARKUS_DATASOURCE_JDBC_URL="${QUARKUS_DATASOURCE_JDBC_URL:-jdbc:postgresql://localhost:5432/invoicegenie}"
# Host-side Quarkus must use localhost, not docker service hostname
if [[ "$QUARKUS_DATASOURCE_JDBC_URL" == *"//postgres:"* ]]; then
  export QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://localhost:5432/${POSTGRES_DB:-invoicegenie}"
fi
export QUARKUS_DATASOURCE_USERNAME="${QUARKUS_DATASOURCE_USERNAME:-${POSTGRES_USER:-ar}}"
export QUARKUS_DATASOURCE_PASSWORD="${QUARKUS_DATASOURCE_PASSWORD:-${POSTGRES_PASSWORD:-ar}}"

echo "    JDBC: $QUARKUS_DATASOURCE_JDBC_URL"
echo "    User: $QUARKUS_DATASOURCE_USERNAME"

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
echo "    Health:  http://localhost:$PORT/q/health"
echo "    Swagger: http://localhost:$PORT/q/swagger-ui/"
echo "    Adminer: http://localhost:8081"

mvn -pl ar-bootstrap \
  -Dquarkus.profile=dev \
  -Dquarkus.http.port="$PORT" \
  -Dquarkus.kafka.devservices.enabled=false \
  quarkus:dev