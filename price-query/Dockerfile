# Multi-stage build for Market Price Server

# Stage 1: Download dependencies
FROM eclipse-temurin:21-jdk-jammy AS dependencies

WORKDIR /app

# Copy Gradle wrapper and build files from the project root
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY price-query/build.gradle price-query/build.gradle
COPY price-query/src price-query/src


# Download dependencies (cached layer)
RUN ./gradlew :price-query:dependencies --no-daemon

# Stage 2: Build with sources
FROM dependencies AS builder

# Build the application with Spring Boot JAR
RUN ./gradlew :price-query:bootJar --no-daemon

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