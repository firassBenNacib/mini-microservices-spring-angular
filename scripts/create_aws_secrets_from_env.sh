#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: create_aws_secrets_from_env.sh --environment <prod|nonprod> [--env-file <path>] [--region <aws-region>]

Creates or updates the application Secrets Manager entries from the local .env file and
prints a paste-ready Terraform snippet for ecs_services.<service>.secret_arns values.
EOF
}

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENVIRONMENT=""
ENV_FILE="${APP_DIR}/.env"
AWS_REGION="${AWS_REGION:-eu-west-1}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment)
      ENVIRONMENT="$2"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --region)
      AWS_REGION="$2"
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

if [[ "${ENVIRONMENT}" != "prod" && "${ENVIRONMENT}" != "nonprod" ]]; then
  echo "--environment must be prod or nonprod" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Env file not found: ${ENV_FILE}" >&2
  exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required" >&2
  exit 1
fi

set -a

source "${ENV_FILE}"
set +a

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing ${name} in ${ENV_FILE}" >&2
    exit 1
  fi
}

require_env APP_JWT_SECRET
require_env APP_DEMO_USER_EMAIL
require_env APP_DEMO_USER_PASSWORD
require_env AUDIT_API_KEY
require_env MAILER_API_KEY
require_env NOTIFY_API_KEY
require_env SPRING_MAIL_USERNAME
require_env SPRING_MAIL_PASSWORD
require_env TWILIO_ACCOUNT_SID
require_env TWILIO_AUTH_TOKEN
require_env TWILIO_FROM_NUMBER

declare -A SECRET_VALUES=(
  ["app-jwt-secret"]="${APP_JWT_SECRET}"
  ["app-demo-user-email"]="${APP_DEMO_USER_EMAIL}"
  ["app-demo-user-password"]="${APP_DEMO_USER_PASSWORD}"
  ["audit-api-key"]="${AUDIT_API_KEY}"
  ["mailer-api-key"]="${MAILER_API_KEY}"
  ["notify-api-key"]="${NOTIFY_API_KEY}"
  ["smtp-username"]="${SPRING_MAIL_USERNAME}"
  ["smtp-password"]="${SPRING_MAIL_PASSWORD}"
  ["twilio-account-sid"]="${TWILIO_ACCOUNT_SID}"
  ["twilio-auth-token"]="${TWILIO_AUTH_TOKEN}"
  ["twilio-from-number"]="${TWILIO_FROM_NUMBER}"
)

declare -A SECRET_ARNS

upsert_secret() {
  local secret_name="$1"
  local secret_value="$2"
  local full_name="${ENVIRONMENT}/${secret_name}"

  if aws secretsmanager describe-secret \
    --region "${AWS_REGION}" \
    --secret-id "${full_name}" >/dev/null 2>&1; then
    aws secretsmanager put-secret-value \
      --region "${AWS_REGION}" \
      --secret-id "${full_name}" \
      --secret-string "${secret_value}" >/dev/null
  else
    aws secretsmanager create-secret \
      --region "${AWS_REGION}" \
      --name "${full_name}" \
      --secret-string "${secret_value}" >/dev/null
  fi

  SECRET_ARNS["${secret_name}"]="$(aws secretsmanager describe-secret \
    --region "${AWS_REGION}" \
    --secret-id "${full_name}" \
    --query 'ARN' \
    --output text)"
}

for secret_name in "${!SECRET_VALUES[@]}"; do
  upsert_secret "${secret_name}" "${SECRET_VALUES[${secret_name}]}"
done

cat <<EOF
# Paste the relevant entries into your Terraform tfvars.

# auth-service.secret_arns
APP_JWT_SECRET         = "${SECRET_ARNS[app-jwt-secret]}"
APP_DEMO_USER_EMAIL    = "${SECRET_ARNS[app-demo-user-email]}"
APP_DEMO_USER_PASSWORD = "${SECRET_ARNS[app-demo-user-password]}"
AUDIT_API_KEY          = "${SECRET_ARNS[audit-api-key]}"

# api-service.secret_arns
APP_JWT_SECRET = "${SECRET_ARNS[app-jwt-secret]}"
MAILER_API_KEY = "${SECRET_ARNS[mailer-api-key]}"
AUDIT_API_KEY  = "${SECRET_ARNS[audit-api-key]}"
NOTIFY_API_KEY = "${SECRET_ARNS[notify-api-key]}"

# audit-service.secret_arns
APP_JWT_SECRET = "${SECRET_ARNS[app-jwt-secret]}"
AUDIT_API_KEY  = "${SECRET_ARNS[audit-api-key]}"

# mailer-service.secret_arns
MAILER_API_KEY       = "${SECRET_ARNS[mailer-api-key]}"
SPRING_MAIL_USERNAME = "${SECRET_ARNS[smtp-username]}"
SPRING_MAIL_PASSWORD = "${SECRET_ARNS[smtp-password]}"

# notification-service.secret_arns
NOTIFY_API_KEY     = "${SECRET_ARNS[notify-api-key]}"
TWILIO_ACCOUNT_SID = "${SECRET_ARNS[twilio-account-sid]}"
TWILIO_AUTH_TOKEN  = "${SECRET_ARNS[twilio-auth-token]}"
TWILIO_FROM_NUMBER = "${SECRET_ARNS[twilio-from-number]}"
EOF
