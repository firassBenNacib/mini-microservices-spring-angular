#!/bin/sh
set -eu

: "${AUTH_SERVICE_UPSTREAM:=auth-service:8081}"
: "${API_SERVICE_UPSTREAM:=api-service:8082}"
: "${AUDIT_SERVICE_UPSTREAM:=audit-service:8084}"
: "${NOTIFICATION_SERVICE_UPSTREAM:=notification-service:8090}"
: "${FRONTEND_UPSTREAM:=}"

export AUTH_SERVICE_UPSTREAM API_SERVICE_UPSTREAM AUDIT_SERVICE_UPSTREAM \
  NOTIFICATION_SERVICE_UPSTREAM FRONTEND_UPSTREAM

cp /etc/nginx/nginx.conf /tmp/nginx.conf
sed -i 's|include /etc/nginx/conf.d/\*.conf;|include /tmp/default.conf;|' /tmp/nginx.conf

template="/etc/nginx/templates/default.conf.template"
# shellcheck disable=SC2016
variables='$AUTH_SERVICE_UPSTREAM $API_SERVICE_UPSTREAM $AUDIT_SERVICE_UPSTREAM $NOTIFICATION_SERVICE_UPSTREAM'

if [ -n "${FRONTEND_UPSTREAM:-}" ]; then
  template="/etc/nginx/templates/default.local.conf.template"
  variables="$variables \$FRONTEND_UPSTREAM"
fi

envsubst "$variables" < "$template" > /tmp/default.conf
exec nginx -c /tmp/nginx.conf -g 'daemon off;'
