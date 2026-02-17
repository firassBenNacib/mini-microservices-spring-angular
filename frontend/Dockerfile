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

FROM alpine:3.20@sha256:a4f4213abb84c497377b8544c81b3564f313746700372ec4fe84653e4fb03805 AS tools
RUN apk add --no-cache busybox

FROM nginx:1.28-alpine@sha256:15e96e59aa3b0aada3a121296e3bce117721f42d88f5f64217ef4b18f458c6ab

RUN apk add --no-cache nginx-mod-http-brotli

COPY nginx.main.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/web-frontend /usr/share/nginx/html
COPY --from=tools /bin/busybox /busybox

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD ["/busybox","wget","-qO-","http://127.0.0.1:8080/"]

USER nginx

CMD ["nginx", "-g", "daemon off;"]
