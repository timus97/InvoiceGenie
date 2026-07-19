#!/usr/bin/env bash
# InvoiceGenie - OWASP Dependency-Check (High+ CVSS fail)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export MAVEN_SKIP_RC=1
echo "==> OWASP Dependency-Check (fail on CVSS >= 7)"
ARGS=(-Psecurity-scan dependency-check:check -DskipTests)
if [ -n "${NVD_API_KEY:-}" ]; then
  ARGS+=("-DnvdApiKey=${NVD_API_KEY}")
fi
mvn "${ARGS[@]}"
echo "Report: target/dependency-check-report.html"