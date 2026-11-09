# ---------- Stage 1: build the jar ----------
# A plain JDK image: Maven itself comes from the Maven Wrapper (mvnw), so the image builds
# with exactly the same Maven version as CI and local development (pinned in
# .mvn/wrapper/maven-wrapper.properties).
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copy the wrapper and the pom first, then download Maven and all dependencies in their own
# layer. As long as these files don't change, Docker reuses this layer and rebuilds after a
# code change take seconds, not minutes.
# `sh mvnw` instead of `./mvnw`: it doesn't depend on the file's executable bit, which can
# get lost when the repo is checked out on Windows.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# A checkout on Windows can give the wrapper Windows line endings (CRLF), which a Linux shell
# can't run; strip them so the image builds the same on every machine.
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties
RUN sh mvnw -B -q dependency:go-offline

COPY lombok.config .
COPY src ./src
# Tests run in the CI pipeline before the image is built (see .github/workflows/ci.yml),
# so they are skipped here to keep the image build fast.
RUN sh mvnw -B -q package -DskipTests

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
