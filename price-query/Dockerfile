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
# Common module
COPY price-common/build.gradle price-common/build.gradle
COPY price-common/src price-common/src
# ClickHouse database module
COPY modules/price-db-clickhouse/build.gradle modules/price-db-clickhouse/build.gradle
COPY modules/price-db-clickhouse/src modules/price-db-clickhouse/src
# Main application module
COPY price-query/build.gradle price-query/build.gradle
COPY price-query/src price-query/src

# Build the application with Spring Boot JAR (Gradle cache persisted between builds)
RUN --mount=type=cache,id=gradle-price-query,target=/root/.gradle \
    find /root/.gradle -name "*.lock" -delete 2>/dev/null || true && \
    ./gradlew :price-query:bootJar --no-daemon

# Stage 3: Runtime with JRE only
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy built Spring Boot JAR from builder stage (exclude plain jar)
COPY --from=builder /app/price-query/build/libs/price-query-*-SNAPSHOT.jar app.jar

# Expose HTTP API port
EXPOSE 8080

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