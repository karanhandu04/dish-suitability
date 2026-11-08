# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace

# Copy the pom first and download dependencies in their own layer. As long as pom.xml doesn't
# change, Docker reuses this layer and rebuilds after a code change take seconds, not minutes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY lombok.config .
COPY src ./src
# Tests run in the CI pipeline before the image is built (see .github/workflows/ci.yml),
# so they are skipped here to keep the image build fast.
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: small runtime image ----------
# JRE only (no JDK, no Maven, no source code), so the image is smaller and has less attack surface.
FROM eclipse-temurin:25-jre-alpine

# Run as a non-root user.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/dish-suitability.jar app.jar
USER app

# 8080 for plain HTTP (default), 8443 when the "tls" profile is active (as in docker-compose)
EXPOSE 8080 8443

# Size the heap from the container's memory limit, and restart cleanly instead of limping on
# after an OutOfMemoryError. Can be overridden at runtime.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Docker marks the container unhealthy if /health stops answering. (wget ships with Alpine.)
# HEALTHCHECK_URL lets the same image be checked over HTTPS; the self-signed local certificate
# is why the check doesn't verify it (it only asks "is the app answering?").
ENV HEALTHCHECK_URL=http://localhost:8080/health
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- --no-check-certificate "$HEALTHCHECK_URL" || exit 1

# `exec` makes Java PID 1, so it receives SIGTERM and shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
