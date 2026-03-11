#!/usr/bin/env bash
set -euo pipefail

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required" >&2
  exit 1
fi

: "${DB_HOST:?DB_HOST is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

DB_PORT=${DB_PORT:-5432}
DB_SSLMODE=${DB_SSLMODE:-require}
RUN_ID=${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}

CONN="postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=${DB_SSLMODE}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[module-payload-migration] run_id=${RUN_ID}"
psql "${CONN}" -v run_id="${RUN_ID}" -f "${SCRIPT_DIR}/module-payload-migrate-once.sql"
psql "${CONN}" -f "${SCRIPT_DIR}/module-payload-verify.sql"

echo "[module-payload-migration] completed run_id=${RUN_ID}"
