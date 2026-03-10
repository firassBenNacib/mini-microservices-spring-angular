# syntax=docker/dockerfile:1.10
FROM nginx:1.28@sha256:810ad1346ec7fd3d0a246c178f2b82e73a43640c691774405adfd38a751ecce8

COPY nginx.main.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY nginx.local.conf /etc/nginx/templates/default.local.conf.template
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8080/gateway/health >/dev/null || exit 1

USER nginx

CMD ["/usr/local/bin/docker-entrypoint.sh"]
