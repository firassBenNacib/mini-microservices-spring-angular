#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-}"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-${SMOKE_BACKEND_BASE_URL:-}}"
SMOKE_AUTH_EMAIL="${SMOKE_AUTH_EMAIL:-}"
SMOKE_AUTH_PASSWORD="${SMOKE_AUTH_PASSWORD:-}"
REQUIRE_AUTH_SMOKE="${REQUIRE_AUTH_SMOKE:-false}"

usage() {
  cat <<'EOF'
Usage: smoke_test.sh --base-url <url> [--backend-base-url <url>]

Optional environment:
  SMOKE_AUTH_EMAIL
  SMOKE_AUTH_PASSWORD
  REQUIRE_AUTH_SMOKE=true
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --backend-base-url)
      BACKEND_BASE_URL="$2"
      shift 2
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

if [[ -z "${BASE_URL}" ]]; then
  usage >&2
  exit 1
fi

BASE_URL="${BASE_URL%/}"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-${BASE_URL}}"
BACKEND_BASE_URL="${BACKEND_BASE_URL%/}"
TMP_DIR="$(mktemp -d)"
COOKIE_JAR="${TMP_DIR}/cookies.txt"
trap 'rm -rf "${TMP_DIR}"' EXIT

csrf_token() {
  [[ -f "${COOKIE_JAR}" ]] || return 0
  awk '$6 == "XSRF-TOKEN" {print $7}' "${COOKIE_JAR}" | tail -n 1
}

request() {
  local target_base_url="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local headers_file="${TMP_DIR}/headers.txt"
  local body_file="${TMP_DIR}/body.txt"
  local -a curl_args=(
    -sS
    -X "${method}"
    -D "${headers_file}"
    -o "${body_file}"
    -w '%{http_code}'
    -H 'Accept: application/json, text/html'
    -b "${COOKIE_JAR}"
    -c "${COOKIE_JAR}"
  )
  local token
  local status

  rm -f "${headers_file}" "${body_file}"

  if [[ -n "${body}" ]]; then
    curl_args+=(-H 'Content-Type: application/json' --data "${body}")
  fi

  if [[ "${method}" != "GET" && "${method}" != "HEAD" && "${method}" != "OPTIONS" ]]; then
    token="$(csrf_token || true)"
    if [[ -n "${token}" ]]; then
      curl_args+=(-H "X-XSRF-TOKEN: ${token}")
    fi
  fi

  status="$(curl "${curl_args[@]}" "${target_base_url}${path}")"

  printf '%s\n' "${status}" > "${TMP_DIR}/status.txt"
}

assert_status() {
  local expected="$1"
  local actual
  actual="$(<"${TMP_DIR}/status.txt")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Expected HTTP ${expected}, got ${actual}." >&2
    cat "${TMP_DIR}/body.txt" >&2
    exit 1
  fi
}

assert_body_contains() {
  local needle="$1"
  if ! grep -Fq "${needle}" "${TMP_DIR}/body.txt"; then
    echo "Response body did not contain expected text: ${needle}" >&2
    cat "${TMP_DIR}/body.txt" >&2
    exit 1
  fi
}

echo "Smoke check: frontend root"
request "${BASE_URL}" GET /
assert_status 200
if ! grep -Eiq '<!doctype html|<html' "${TMP_DIR}/body.txt"; then
  echo "Root response did not look like HTML." >&2
  cat "${TMP_DIR}/body.txt" >&2
  exit 1
fi

echo "Smoke check: gateway health"
request "${BACKEND_BASE_URL}" GET /gateway/health
assert_status 200
assert_body_contains '"status":"ok"'

echo "Smoke check: auth session"
request "${BACKEND_BASE_URL}" GET /auth/session
assert_status 200
assert_body_contains '"authenticated":false'

echo "Smoke check: api health"
request "${BACKEND_BASE_URL}" GET /api/health
assert_status 200
assert_body_contains '"status":"ok"'

if [[ -n "${SMOKE_AUTH_EMAIL}" && -n "${SMOKE_AUTH_PASSWORD}" ]]; then
  echo "Smoke check: auth login"
  login_payload="$(python3 - <<'PY'
import json, os
print(json.dumps({
    "email": os.environ["SMOKE_AUTH_EMAIL"],
    "password": os.environ["SMOKE_AUTH_PASSWORD"],
}))
PY
)"
  request "${BACKEND_BASE_URL}" POST /auth/login "${login_payload}"
  login_status="$(<"${TMP_DIR}/status.txt")"
  if [[ "${login_status}" != "200" ]]; then
    if [[ "${REQUIRE_AUTH_SMOKE}" == "true" ]]; then
      echo "Authenticated smoke checks are required, but headless login returned HTTP ${login_status}." >&2
      cat "${TMP_DIR}/body.txt" >&2
      exit 1
    fi
    echo "Skipping authenticated smoke checks; headless login returned HTTP ${login_status}."
    cat "${TMP_DIR}/body.txt"
    echo
    echo "Unauthenticated deployment smoke checks still passed for ${BASE_URL}"
    exit 0
  fi
  assert_body_contains '"authenticated":true'

  echo "Smoke check: authenticated session"
  request "${BACKEND_BASE_URL}" GET /auth/session
  assert_status 200
  assert_body_contains '"authenticated":true'

  echo "Smoke check: protected api message"
  request "${BACKEND_BASE_URL}" GET /api/message
  assert_status 200
  assert_body_contains 'Microservices deployed and working'

  echo "Smoke check: protected audit recent"
  request "${BACKEND_BASE_URL}" GET '/audit/recent?limit=5'
  assert_status 200
  assert_body_contains "${SMOKE_AUTH_EMAIL}"
else
  if [[ "${REQUIRE_AUTH_SMOKE}" == "true" ]]; then
    echo "Authenticated smoke checks are required, but SMOKE_AUTH_EMAIL and SMOKE_AUTH_PASSWORD are not set." >&2
    exit 1
  fi
  echo "Skipping authenticated smoke checks; SMOKE_AUTH_EMAIL and SMOKE_AUTH_PASSWORD are not set."
fi

echo "Smoke tests passed for ${BASE_URL}"
