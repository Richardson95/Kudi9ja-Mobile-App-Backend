# syntax=docker/dockerfile:1

# ── Build ──────────────────────────────────────────────────────────────────
# Dependencies are resolved in their own layer, so a code change rebuilds in
# seconds rather than re-downloading the whole tree every push.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests run in CI, not here. A deploy that reruns them doubles the build for a
# result the pipeline already has, and a flaky test would block a rollback.
RUN mvn -B clean package -DskipTests


# ── Run ────────────────────────────────────────────────────────────────────
# A JRE, not a JDK: the compiler has no business in a production image, and
# leaving it out drops roughly 200MB and a good deal of attack surface.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Never root. A process that holds customer money should not be able to write
# to its own binaries.
RUN addgroup -S kudi9ja && adduser -S kudi9ja -G kudi9ja

WORKDIR /app

# Receipts and exports land here. On more than one instance this must be a
# mounted volume or, better, object storage — a receipt written to a container
# filesystem is gone at the next deploy.
RUN mkdir -p /app/var/receipts /app/var/exports && chown -R kudi9ja:kudi9ja /app

COPY --from=build --chown=kudi9ja:kudi9ja /build/target/kudi9ja-backend-1.0.0.jar app.jar

USER kudi9ja

# Render sets PORT; the application already reads it.
ENV PORT=8080
EXPOSE 8080

# Lets the platform tell "starting up" from "broken" — Flyway migrations and
# the settings seeder run before the first request is served.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:${PORT}/actuator/health | grep -q '"status":"UP"' || exit 1

# MaxRAMPercentage rather than a fixed heap: the container's memory limit is
# set by the host, and a hard -Xmx either wastes what it was given or is killed
# for exceeding it.
# 65 rather than 75. The remainder is not spare — metaspace, thread stacks, the
# JIT's own buffers and every native allocation come out of it, and on a 512 MiB
# instance 75% leaves too little for them. The service was killed for exceeding
# its memory limit mid-request, which reaches the customer as a failed
# connection rather than as an error anybody can act on.
#
# ExitOnOutOfMemoryError stays. A JVM that has run out of memory is not a JVM
# worth keeping alive, and dying fast lets the host restart it cleanly rather
# than serving nonsense for the next hour.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
