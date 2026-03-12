# syntax=docker/dockerfile:1.10
FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS build

WORKDIR /app

COPY pom.xml ./
RUN --mount=type=cache,id=m2-mailer-service,sharing=locked,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,id=m2-mailer-service,sharing=locked,target=/root/.m2 mvn -q -DskipTests package

FROM busybox:1.37.0-uclibc@sha256:e3652a00a2fabd16ce889f0aa32c38eec347b997e73bd09e69c962ec7f8732ee AS healthcheck-tools

FROM eclipse-temurin:17-jre@sha256:ff692acf872589e72ca71b416b3e949ec19f35ebe8abb5365d2f31bce298e762

WORKDIR /app

RUN groupadd --system app && \
    useradd --system --gid app --create-home --home-dir /home/app app

COPY --from=build --chown=app:app /app/target/*.jar /app/app.jar
COPY --from=healthcheck-tools /bin/busybox /busybox

USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD ["/busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8083/health"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
