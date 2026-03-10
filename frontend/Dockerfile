# syntax=docker/dockerfile:1.10
FROM node:20-alpine@sha256:09e2b3d9726018aecf269bd35325f46bf75046a643a66d28360ec71132750ec8 AS build

WORKDIR /app

COPY package.json package-lock.json angular.json tsconfig.json tsconfig.app.json tsconfig.spec.json ./

RUN --mount=type=cache,target=/root/.npm npm ci

COPY src ./src

RUN npm run build && \
    if [ -d "dist/web-frontend/browser" ]; then \
      cp -R dist/web-frontend/browser/* dist/web-frontend/; \
    fi

FROM nginx:1.28@sha256:810ad1346ec7fd3d0a246c178f2b82e73a43640c691774405adfd38a751ecce8

COPY nginx.main.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/web-frontend /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8080/ >/dev/null || exit 1

USER nginx

CMD ["nginx", "-g", "daemon off;"]
