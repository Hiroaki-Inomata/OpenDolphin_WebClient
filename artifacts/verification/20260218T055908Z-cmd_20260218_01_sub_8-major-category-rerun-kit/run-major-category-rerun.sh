#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${QA_BASE_URL:-http://localhost:5173}"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-major-category-rerun}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${SCRIPT_DIR}/runs/${RUN_ID}}"
LOCK_DIR="${RERUN_LOCK_DIR:-${SCRIPT_DIR}/.major-category-rerun.lock}"
LOCK_PID_FILE="${LOCK_DIR}/pid"

mkdir -p "$ARTIFACT_DIR"

if mkdir "$LOCK_DIR" 2>/dev/null; then
  echo "$$" > "$LOCK_PID_FILE"
  trap 'rm -f "$LOCK_PID_FILE"; rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT INT TERM
else
  lock_pid="$(cat "$LOCK_PID_FILE" 2>/dev/null || echo unknown)"
  echo "busy: another rerun is active (pid=${lock_pid})." >&2
  echo "hint: wait until no major-category-rerun-check.mjs process exists, then retry." >&2
  exit 42
fi

RUN_ID="$RUN_ID" \
ARTIFACT_DIR="$ARTIFACT_DIR" \
QA_BASE_URL="$BASE_URL" \
node "$SCRIPT_DIR/major-category-rerun-check.mjs" || rc=$?
rc="${rc:-0}"

echo "summary: $ARTIFACT_DIR/summary.md"
echo "json:    $ARTIFACT_DIR/summary.json"
exit "$rc"
