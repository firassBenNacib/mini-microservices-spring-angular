#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

COMPOSE_CMD=(docker compose -f docker-compose.yml -f docker-compose.build.yml)
ENV_EXAMPLE=".env.local.example"
ENV_FILE=".env"
TMP_DIR="$(mktemp -d)"
ENV_BACKUP="${TMP_DIR}/env.backup"
ARTIFACT_DIR="${ROOT_DIR}/integration-artifacts"
had_env=0

cleanup() {
  local exit_code=$?
  mkdir -p "${ARTIFACT_DIR}"
  printf 'Integration workflow result: %s\n' "$([[ "${exit_code}" -eq 0 ]] && echo success || echo failure)" \
    > "${ARTIFACT_DIR}/summary.txt"
  DOCKERHUB_USERNAME=local "${COMPOSE_CMD[@]}" ps -a \
    > "${ARTIFACT_DIR}/compose-ps.txt" || true
  DOCKERHUB_USERNAME=local "${COMPOSE_CMD[@]}" logs --no-color \
    > "${ARTIFACT_DIR}/compose-logs.txt" || true
  if (( exit_code != 0 )); then
    "${COMPOSE_CMD[@]}" ps || true
    "${COMPOSE_CMD[@]}" logs --no-color --tail=200 || true
  fi
  DOCKERHUB_USERNAME=local "${COMPOSE_CMD[@]}" down --remove-orphans --volumes || true
  if [[ "${had_env}" -eq 1 ]]; then
    mv "${ENV_BACKUP}" "${ENV_FILE}"
  else
    rm -f "${ENV_FILE}"
  fi
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

rm -rf "${ARTIFACT_DIR}"

if [[ -f "${ENV_FILE}" ]]; then
  cp "${ENV_FILE}" "${ENV_BACKUP}"
  had_env=1
fi

cp "${ENV_EXAMPLE}" "${ENV_FILE}"

DEMO_EMAIL="$(grep '^APP_DEMO_USER_EMAIL=' "${ENV_FILE}" | cut -d= -f2-)"
DEMO_PASSWORD="$(grep '^APP_DEMO_USER_PASSWORD=' "${ENV_FILE}" | cut -d= -f2-)"

DOCKERHUB_USERNAME=local "${COMPOSE_CMD[@]}" down --remove-orphans --volumes >/dev/null 2>&1 || true
DOCKERHUB_USERNAME=local "${COMPOSE_CMD[@]}" up -d --build

for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:8085/gateway/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

BASE_URL="http://127.0.0.1:8085" \
SMOKE_AUTH_EMAIL="${DEMO_EMAIL}" \
SMOKE_AUTH_PASSWORD="${DEMO_PASSWORD}" \
REQUIRE_AUTH_SMOKE=true \
bash "${ROOT_DIR}/scripts/smoke_test.sh" --base-url "http://127.0.0.1:8085"
