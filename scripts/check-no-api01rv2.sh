#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PATTERN='/api01rv2'
TARGET_DIR='web-client'

echo "[check-no-api01rv2] scanning ${TARGET_DIR} for ${PATTERN}"

if command -v rg >/dev/null 2>&1; then
  if rg -n --hidden --glob '!**/node_modules/**' --glob '!**/dist/**' --glob '!**/coverage/**' --glob '!**/test-results/**' --glob '!**/.vite/**' "$PATTERN" "$TARGET_DIR"; then
    echo "[check-no-api01rv2] found forbidden path '${PATTERN}' in ${TARGET_DIR}" >&2
    exit 1
  fi
else
  if grep -R -n --exclude-dir=node_modules --exclude-dir=dist --exclude-dir=coverage --exclude-dir=test-results --exclude-dir=.vite "$PATTERN" "$TARGET_DIR"; then
    echo "[check-no-api01rv2] found forbidden path '${PATTERN}' in ${TARGET_DIR}" >&2
    exit 1
  fi
fi

echo "[check-no-api01rv2] ok"
