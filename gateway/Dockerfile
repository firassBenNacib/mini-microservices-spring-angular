# syntax=docker/dockerfile:1.10
FROM nginx:1.28-alpine@sha256:15e96e59aa3b0aada3a121296e3bce117721f42d88f5f64217ef4b18f458c6ab

COPY nginx.main.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY nginx.local.conf /etc/nginx/templates/default.local.conf.template
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

USER nginx

CMD ["/usr/local/bin/docker-entrypoint.sh"]
