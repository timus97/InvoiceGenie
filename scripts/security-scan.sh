#!/usr/bin/env bash
# InvoiceGenie dependency security scan (Linux/macOS/Git Bash)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SKIP_NPM=0
REPORT_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --skip-npm) SKIP_NPM=1 ;;
    --report-only) REPORT_ONLY=1 ;;
  esac
done

# Load .env without printing secrets
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env" 2>/dev/null || true
  set +a
fi

if [[ -n "${NVD_API_KEY:-}" ]]; then
  echo "NVD_API_KEY loaded (length=${#NVD_API_KEY})"
else
  echo "NVD_API_KEY not set — scan uses OSS Index / cached NVD only"
fi

FAIL_CVSS=7
if [[ "$REPORT_ONLY" -eq 1 ]]; then FAIL_CVSS=11; fi

echo "==> Maven OWASP Dependency-Check (profile: security-scan)"
MVN_ARGS=(-Psecurity-scan "-Ddependency.check.failBuildOnCVSS=${FAIL_CVSS}" org.owasp:dependency-check-maven:aggregate)
if [[ -n "${NVD_API_KEY:-}" ]]; then
  MVN_ARGS=("-DnvdApiKey=${NVD_API_KEY}" "${MVN_ARGS[@]}")
fi
mvn "${MVN_ARGS[@]}"

mkdir -p "$ROOT/docs/security"
[[ -f "$ROOT/target/dependency-check/dependency-check-report.html" ]] && \
  cp "$ROOT/target/dependency-check/dependency-check-report.html" "$ROOT/docs/security/" && \
  echo "Maven report: $ROOT/target/dependency-check/dependency-check-report.html"
[[ -f "$ROOT/target/dependency-check/dependency-check-report.json" ]] && \
  cp "$ROOT/target/dependency-check/dependency-check-report.json" "$ROOT/docs/security/"

if [[ "$SKIP_NPM" -eq 0 ]]; then
  echo "==> npm audit (web/)"
  (cd "$ROOT/web" && npm audit --omit=dev) || {
    code=$?
    if [[ "$REPORT_ONLY" -eq 0 ]]; then exit "$code"; fi
  }
fi

echo "Security scan complete."
