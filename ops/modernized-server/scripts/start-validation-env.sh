#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/ops/modernized-server/config/server-modernized.validation.env}"
SAMPLE_ENV="$ROOT_DIR/ops/modernized-server/config/server-modernized.validation.env.sample"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-opendolphin_validation}"

if [[ ! -f "$ENV_FILE" ]]; then
  cat <<EOF >&2
Validation env file not found: $ENV_FILE
Copy sample and set real values:
  cp "$SAMPLE_ENV" "$ENV_FILE"
EOF
  exit 1
fi

echo "[validation-env] compose config check..."
docker compose \
  --project-name "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$ROOT_DIR/docker-compose.modernized.dev.yml" \
  -f "$ROOT_DIR/docker-compose.modernized.validation.yml" \
  config >/dev/null

echo "[validation-env] starting containers..."
docker compose \
  --project-name "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$ROOT_DIR/docker-compose.modernized.dev.yml" \
  -f "$ROOT_DIR/docker-compose.modernized.validation.yml" \
  up -d --build --force-recreate

echo "[validation-env] status"
docker compose \
  --project-name "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$ROOT_DIR/docker-compose.modernized.dev.yml" \
  -f "$ROOT_DIR/docker-compose.modernized.validation.yml" \
  ps
