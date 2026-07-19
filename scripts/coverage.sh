#!/usr/bin/env bash
# InvoiceGenie - run unit tests with JaCoCo coverage reports
# Usage: ./scripts/coverage.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export MAVEN_SKIP_RC=1

echo "==> Running tests with JaCoCo"
mvn clean test

echo ""
echo "==> Per-module line coverage:"
MIN=80
FAILED=0
while IFS= read -r -d '' f; do
  # shellcheck disable=SC2016
  python - "$f" <<'PY' || true
import sys, xml.etree.ElementTree as ET
path = sys.argv[1]
root = ET.parse(path).getroot()
line = next((c for c in root.findall("counter") if c.get("type") == "LINE"), None)
if line is None:
    sys.exit(0)
missed = int(line.get("missed", 0))
covered = int(line.get("covered", 0))
total = missed + covered
pct = round(100.0 * covered / total, 1) if total else 0.0
mod = path.split("/target/")[0].rsplit("/", 1)[-1]
print(f"    {mod:<28} {pct:5.1f}%  ({covered}/{total} lines)")
if pct < 80:
    sys.exit(2)
PY
  rc=$?
  if [ "$rc" -eq 2 ]; then FAILED=1; fi
done < <(find . -path "*/target/site/jacoco/jacoco.xml" -print0 2>/dev/null)

if [ "$FAILED" -ne 0 ]; then
  echo "FAIL: one or more modules below ${MIN}% line coverage"
  exit 1
fi
echo "PASS: all modules meet ${MIN}% line coverage floor"