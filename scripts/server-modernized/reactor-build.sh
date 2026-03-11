#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-}"
TESTS="${2:-}"

if [[ -z "${MODE}" ]]; then
  echo "usage: $0 <compile|tests> [test-pattern]" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT_DIR}"

BASE_ARGS=(-B -ntp -f pom.server-modernized.xml -pl server-modernized -am)

if [[ "${MODE}" == "compile" ]]; then
  # Keep reactor order verification + test source compile in one command.
  mvn "${BASE_ARGS[@]}" -DskipTests test-compile
  exit 0
fi

if [[ "${MODE}" == "tests" ]]; then
  if [[ -z "${TESTS}" ]]; then
    echo "tests mode requires test pattern argument" >&2
    exit 1
  fi
  EXTRA_ARGS=()
  if [[ -n "${MAVEN_EXTRA_ARGS:-}" ]]; then
    # shellcheck disable=SC2206
    EXTRA_ARGS=(${MAVEN_EXTRA_ARGS})
  fi
  mvn "${BASE_ARGS[@]}" "${EXTRA_ARGS[@]}" -Dtest="${TESTS}" -Dsurefire.failIfNoSpecifiedTests=false test
  exit 0
fi

echo "unknown mode: ${MODE}" >&2
exit 1
