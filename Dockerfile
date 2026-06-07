# syntax=docker/dockerfile:1.7

# ---- Build stage -------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /home/app

# Cache the Gradle wrapper and dependency resolution first
COPY gradlew settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

# Build the bootJar
COPY build.gradle.kts ./
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user; /app/data is where the SQLite file lives.
# On Render, mount a persistent disk at /app/data (see render.yaml) so the file
# survives redeploys.
RUN groupadd --system app \
 && useradd  --system --gid app --home /app --shell /usr/sbin/nologin app \
 && mkdir -p /app/data \
 && chown -R app:app /app
USER app

COPY --from=build /home/app/build/libs/*.jar /app/app.jar

# Render injects PORT at runtime; default to 8080 for local docker runs.
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
