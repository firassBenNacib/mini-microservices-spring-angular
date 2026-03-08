#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${APP_DIR}/.artifacts/dockerhub"
mkdir -p "${OUT_DIR}"

DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-}"
DOCKERHUB_NAMESPACE="${DOCKERHUB_NAMESPACE:-${DOCKERHUB_USERNAME}}"
DOCKERHUB_TOKEN="${DOCKERHUB_TOKEN:-}"
IMAGE_TAG="${IMAGE_TAG:-$(date -u +%Y%m%d%H%M%S)}"
PUSH_LATEST="${PUSH_LATEST:-false}"
SERVICES_CSV="${SERVICES:-}"
INCLUDE_FRONTEND_IMAGE="${INCLUDE_FRONTEND_IMAGE:-true}"

if [[ -z "${DOCKERHUB_USERNAME}" ]]; then
  echo "DOCKERHUB_USERNAME is required" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if [[ -n "${DOCKERHUB_TOKEN}" ]]; then
  printf '%s' "${DOCKERHUB_TOKEN}" | docker login --username "${DOCKERHUB_USERNAME}" --password-stdin >/dev/null
elif [[ ! -f "${HOME}/.docker/config.json" ]]; then
  echo "Set DOCKERHUB_TOKEN or run docker login first" >&2
  exit 1
fi

declare -A BUILD_CONTEXTS=(
  ["auth-service"]="${APP_DIR}/backend/auth-service"
  ["api-service"]="${APP_DIR}/backend/api-service"
  ["audit-service"]="${APP_DIR}/backend/audit-service"
  ["mailer-service"]="${APP_DIR}/backend/mailer-service"
  ["notification-service"]="${APP_DIR}/backend/notification-service"
  ["gateway"]="${APP_DIR}/gateway"
)

declare -A IMAGE_NAMES=(
  ["auth-service"]="mini-spring-auth"
  ["api-service"]="mini-spring-api"
  ["audit-service"]="mini-spring-audit"
  ["mailer-service"]="mini-spring-mailer"
  ["notification-service"]="mini-spring-notification"
  ["gateway"]="mini-spring-gateway"
)

all_services=(auth-service api-service audit-service mailer-service notification-service gateway)
if [[ "${INCLUDE_FRONTEND_IMAGE}" == "true" ]]; then
  BUILD_CONTEXTS["frontend"]="${APP_DIR}/frontend"
  IMAGE_NAMES["frontend"]="mini-spring-frontend"
  all_services+=(frontend)
fi

if [[ -n "${SERVICES_CSV}" ]]; then
  IFS=',' read -r -a services <<<"${SERVICES_CSV}"
else
  services=("${all_services[@]}")
fi

for service in "${services[@]}"; do
  if [[ -z "${BUILD_CONTEXTS[${service}]:-}" ]]; then
    echo "Unknown service: ${service}" >&2
    exit 1
  fi
done

declare -A IMAGE_REFS
JSON_OUTPUT="${OUT_DIR}/dockerhub-images.${IMAGE_TAG}.json"

write_outputs() {
  local published=()
  local service

  for service in "${services[@]}"; do
    if [[ -n "${IMAGE_REFS[${service}]:-}" ]]; then
      published+=("${service}")
    fi
  done

  {
    printf "{\n"
    for index in "${!published[@]}"; do
      service="${published[${index}]}"
      suffix=","
      if [[ "${index}" -eq $((${#published[@]} - 1)) ]]; then
        suffix=""
      fi
      printf '  "%s": "%s"%s\n' "${service}" "${IMAGE_REFS[${service}]}" "${suffix}"
    done
    printf "}\n"
  } > "${JSON_OUTPUT}"
}

write_outputs

for service in "${services[@]}"; do
  context="${BUILD_CONTEXTS[${service}]}"
  image_name="${IMAGE_NAMES[${service}]}"
  image_ref="${DOCKERHUB_NAMESPACE}/${image_name}:${IMAGE_TAG}"

  echo "Building ${service} from ${context}"
  docker build -t "${image_ref}" "${context}"
  docker push "${image_ref}"

  if [[ "${PUSH_LATEST}" == "true" ]]; then
    latest_ref="${DOCKERHUB_NAMESPACE}/${image_name}:latest"
    docker tag "${image_ref}" "${latest_ref}"
    docker push "${latest_ref}"
  fi

  IMAGE_REFS["${service}"]="${image_ref}"
  echo "Published ${service}: ${image_ref}"
  write_outputs
done

echo "Image refs written to ${JSON_OUTPUT}"
