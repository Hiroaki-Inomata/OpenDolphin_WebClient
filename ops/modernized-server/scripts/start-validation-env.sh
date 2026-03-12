#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/ops/modernized-server/config/server-modernized.validation.env}"
SAMPLE_ENV="$ROOT_DIR/ops/modernized-server/config/server-modernized.validation.env.sample"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-opendolphin_validation}"
DOCKER_SOCKET_PATH="${DOCKER_SOCKET_PATH:-$HOME/.docker/run/docker.sock}"
DOCKER_PING_TIMEOUT_SECONDS="${DOCKER_PING_TIMEOUT_SECONDS:-8}"

if [[ ! -f "$ENV_FILE" ]]; then
  cat <<EOF >&2
Validation env file not found: $ENV_FILE
Copy sample and set real values:
  cp "$SAMPLE_ENV" "$ENV_FILE"
EOF
  exit 1
fi

if [[ ! -S "$DOCKER_SOCKET_PATH" ]]; then
  echo "[validation-env] docker socket not found: $DOCKER_SOCKET_PATH" >&2
  echo "[validation-env] ensure Docker Desktop/daemon is running before cutover." >&2
  exit 1
fi

if ! curl --silent --show-error --max-time "$DOCKER_PING_TIMEOUT_SECONDS" \
  --unix-socket "$DOCKER_SOCKET_PATH" \
  http://localhost/_ping >/dev/null; then
  echo "[validation-env] docker daemon is not responding via $DOCKER_SOCKET_PATH" >&2
  echo "[validation-env] aborting before compose up to avoid indefinite hang." >&2
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
