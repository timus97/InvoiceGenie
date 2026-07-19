#!/usr/bin/env bash
# InvoiceGenie - run unit tests with JaCoCo coverage reports
# Usage: ./scripts/coverage.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-}"
export MAVEN_SKIP_RC=1

echo "==> Running tests with JaCoCo (prepare-agent + report + report-aggregate)"
mvn clean test verify -DskipITs

echo ""
echo "==> Coverage reports:"
echo "    Per-module:  */target/site/jacoco/index.html"
echo "    Aggregate:   target/site/jacoco-aggregate/index.html (if generated)"
find . -path "*/target/site/jacoco/index.html" 2>/dev/null | head -20 || true
ls -la target/site/jacoco-aggregate/index.html 2>/dev/null || true

echo ""
echo "Done."