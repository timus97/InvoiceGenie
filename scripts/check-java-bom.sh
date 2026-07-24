#!/usr/bin/env bash
# Fail if any *.java under the repo has a UTF-8 BOM (EF BB BF).
# STORY-QA-001 — BOM breaks Quarkus live reload / multi-agent compile.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FOUND=0
while IFS= read -r -d '' f; do
  # Read first 3 bytes
  hex=$(head -c 3 "$f" | od -An -tx1 | tr -d ' \n')
  if [ "$hex" = "efbbbf" ]; then
    echo "UTF-8 BOM: $f"
    FOUND=1
  fi
done < <(find "$ROOT" -name '*.java' -type f \
  ! -path '*/target/*' ! -path '*/.git/*' -print0)

if [ "$FOUND" -ne 0 ]; then
  echo "ERROR: UTF-8 BOM detected on Java sources (STORY-QA-001). Re-save as UTF-8 without BOM."
  exit 1
fi
echo "OK: no UTF-8 BOM on *.java"
