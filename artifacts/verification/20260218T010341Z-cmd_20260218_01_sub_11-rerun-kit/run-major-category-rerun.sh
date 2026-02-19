#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${QA_BASE_URL:-http://localhost:5173}"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-major-category-rerun}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${SCRIPT_DIR}/runs/${RUN_ID}}"

mkdir -p "$ARTIFACT_DIR"

RUN_ID="$RUN_ID" \
ARTIFACT_DIR="$ARTIFACT_DIR" \
QA_BASE_URL="$BASE_URL" \
node "$SCRIPT_DIR/major-category-rerun-check.mjs"

echo "summary: $ARTIFACT_DIR/summary.md"
echo "json:    $ARTIFACT_DIR/summary.json"
