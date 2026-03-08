#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="${APP_DIR}/frontend"
DIST_DIR="${FRONTEND_DIR}/dist/web-frontend/browser"
BUCKET=""
DISTRIBUTION_ID=""
BUILD_ONLY="false"

usage() {
  cat <<'EOF'
Usage: publish_frontend_s3.sh [--bucket <name>] [--distribution-id <id>] [--build-only]

Builds the Angular frontend. When --bucket is provided, syncs the built files to S3.
When --distribution-id is provided, creates a CloudFront invalidation after sync.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bucket)
      BUCKET="$2"
      shift 2
      ;;
    --distribution-id)
      DISTRIBUTION_ID="$2"
      shift 2
      ;;
    --build-only)
      BUILD_ONLY="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

cd "${FRONTEND_DIR}"
npm ci
npm run build

if [[ ! -d "${DIST_DIR}" ]]; then
  DIST_DIR="${FRONTEND_DIR}/dist/web-frontend"
fi

echo "Frontend dist prepared at ${DIST_DIR}"

if [[ "${BUILD_ONLY}" == "true" ]]; then
  exit 0
fi

if [[ -z "${BUCKET}" ]]; then
  echo "--bucket is required unless --build-only is used" >&2
  exit 1
fi

aws s3 sync "${DIST_DIR}/" "s3://${BUCKET}/" --delete

if [[ -n "${DISTRIBUTION_ID}" ]]; then
  aws cloudfront create-invalidation --distribution-id "${DISTRIBUTION_ID}" --paths '/*' >/dev/null
  echo "CloudFront invalidation submitted for ${DISTRIBUTION_ID}"
fi

echo "Frontend assets synced to s3://${BUCKET}/"
