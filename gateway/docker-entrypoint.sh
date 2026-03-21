#!/bin/sh
set -eu

: "${AUTH_SERVICE_UPSTREAM:=auth-service:8081}"
: "${API_SERVICE_UPSTREAM:=api-service:8082}"
: "${AUDIT_SERVICE_UPSTREAM:=audit-service:8084}"
: "${NOTIFICATION_SERVICE_UPSTREAM:=notification-service:8090}"
: "${DNS_RESOLVER:=169.254.169.253}"
: "${FRONTEND_PUBLIC_ORIGIN:=}"
: "${FRONTEND_UPSTREAM:=}"

if [ -z "${FRONTEND_PUBLIC_ORIGIN}" ]; then
  if [ -n "${FRONTEND_UPSTREAM}" ]; then
    FRONTEND_PUBLIC_ORIGIN="http://127.0.0.1:8080"
  else
    echo "FRONTEND_PUBLIC_ORIGIN must be set when FRONTEND_UPSTREAM is not configured." >&2
    exit 1
  fi
fi

export AUTH_SERVICE_UPSTREAM API_SERVICE_UPSTREAM AUDIT_SERVICE_UPSTREAM \
  NOTIFICATION_SERVICE_UPSTREAM DNS_RESOLVER FRONTEND_PUBLIC_ORIGIN FRONTEND_UPSTREAM

runtime_dir="${NGINX_RUNTIME_DIR:-/var/cache/nginx/runtime}"
mkdir -p "${runtime_dir}"

runtime_nginx_conf="${runtime_dir}/nginx.conf"
runtime_default_conf="${runtime_dir}/default.conf"

cp /etc/nginx/nginx.conf "${runtime_nginx_conf}"
sed -i "s|include /etc/nginx/conf.d/\\*.conf;|include ${runtime_default_conf};|" "${runtime_nginx_conf}"

template="/etc/nginx/templates/default.conf.template"
# shellcheck disable=SC2016
variables='$AUTH_SERVICE_UPSTREAM $API_SERVICE_UPSTREAM $AUDIT_SERVICE_UPSTREAM $NOTIFICATION_SERVICE_UPSTREAM $DNS_RESOLVER $FRONTEND_PUBLIC_ORIGIN'

if [ -n "${FRONTEND_UPSTREAM:-}" ]; then
  template="/etc/nginx/templates/default.local.conf.template"
  variables="$variables \$FRONTEND_UPSTREAM"
fi

envsubst "$variables" < "$template" > "${runtime_default_conf}"
exec nginx -c "${runtime_nginx_conf}" -g 'daemon off;'
