#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${APP_DIR}/.artifacts/ecr"
mkdir -p "${OUT_DIR}"

CLOUD_PROVIDER_REGION="${CLOUD_PROVIDER_REGION:-${AWS_REGION:-eu-west-1}}"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-microservices}"
IMAGE_TAG="${IMAGE_TAG:-$(date -u +%Y%m%d%H%M%S)}"
INCLUDE_FRONTEND_IMAGE="${INCLUDE_FRONTEND_IMAGE:-true}"
SERVICES_CSV="${SERVICES:-}"

if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${CLOUD_PROVIDER_REGION}.amazonaws.com"

declare -A BUILD_CONTEXTS=(
  ["gateway"]="${APP_DIR}/gateway"
  ["auth-service"]="${APP_DIR}/backend/auth-service"
  ["api-service"]="${APP_DIR}/backend/api-service"
  ["audit-service"]="${APP_DIR}/backend/audit-service"
  ["mailer-service"]="${APP_DIR}/backend/mailer-service"
  ["notification-service"]="${APP_DIR}/backend/notification-service"
)

all_services=(gateway auth-service api-service audit-service mailer-service notification-service)
if [[ "${INCLUDE_FRONTEND_IMAGE}" == "true" ]]; then
  BUILD_CONTEXTS["frontend"]="${APP_DIR}/frontend"
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

aws ecr get-login-password --region "${CLOUD_PROVIDER_REGION}" | docker login --username AWS --password-stdin "${ECR_REGISTRY}" >/dev/null

declare -A IMAGE_REFS
JSON_OUTPUT="${OUT_DIR}/ecr-images.${IMAGE_TAG}.json"
HCL_OUTPUT="${OUT_DIR}/ecs-service-images.${IMAGE_TAG}.hcl"

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

  {
    printf "# Paste these image refs into ecs_services.<service>.image in your tfvars.\n"
    for service in "${published[@]}"; do
      printf "\"%s\" = \"%s\"\n" "${service}" "${IMAGE_REFS[${service}]}"
    done
  } > "${HCL_OUTPUT}"
}

ensure_repository() {
  local repository_name="$1"
  aws ecr describe-repositories --region "${CLOUD_PROVIDER_REGION}" --repository-names "${repository_name}" >/dev/null 2>&1 || \
    aws ecr create-repository --region "${CLOUD_PROVIDER_REGION}" --repository-name "${repository_name}" >/dev/null
}

write_outputs

for service in "${services[@]}"; do
  context="${BUILD_CONTEXTS[${service}]}"
  repository_name="${IMAGE_NAMESPACE}/${service}"
  image_tag_ref="${ECR_REGISTRY}/${repository_name}:${IMAGE_TAG}"

  echo "Building ${service} from ${context}"
  ensure_repository "${repository_name}"
  docker build -t "${image_tag_ref}" "${context}"
  docker push "${image_tag_ref}"

  digest="$(aws ecr describe-images \
    --region "${CLOUD_PROVIDER_REGION}" \
    --repository-name "${repository_name}" \
    --image-ids imageTag="${IMAGE_TAG}" \
    --query 'imageDetails[0].imageDigest' \
    --output text)"

  IMAGE_REFS["${service}"]="${ECR_REGISTRY}/${repository_name}@${digest}"
  echo "Published ${service}: ${IMAGE_REFS[${service}]}"
  write_outputs
done

echo "Image refs written to ${JSON_OUTPUT}"
echo "Paste-ready snippet written to ${HCL_OUTPUT}"
