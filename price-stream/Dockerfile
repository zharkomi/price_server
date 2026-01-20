# syntax=docker/dockerfile:1
# Multi-stage build for Market Price Server

# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Gradle wrapper and build files from the project root
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY price-common/build.gradle price-common/build.gradle
COPY price-common/src price-common/src
COPY price-stream/build.gradle price-stream/build.gradle
COPY price-stream/src price-stream/src

# Build the application with fat JAR (Gradle cache persisted between builds)
RUN --mount=type=cache,id=gradle-price-stream,target=/root/.gradle \
    find /root/.gradle -name "*.lock" -delete 2>/dev/null || true && \
    ./gradlew :price-stream:fatJar --no-daemon

# Stage 3: Runtime with JRE only
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy built fat JAR from builder stage
COPY --from=builder /app/price-stream/build/libs/*-all.jar app.jar

# Expose HTTP API port and WebSocket streaming port
EXPOSE 8080 8081

# Run the application with optimized JVM parameters
ENTRYPOINT ["java", \
    "-Xms500m", \
    "-Xmx1g", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs/heap_dump.hprof", \
    "-Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10M", \
    "-jar", "app.jar"]
