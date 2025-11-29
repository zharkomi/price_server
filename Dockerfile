# Multi-stage build for Market Price Server

# Stage 1: Download dependencies
FROM eclipse-temurin:21-jdk-jammy AS dependencies

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Stage 2: Build with sources
FROM dependencies AS builder

# Copy source code
COPY src src

# Build the application with fat JAR
RUN ./gradlew fatJar --no-daemon

# Stage 3: Runtime with JRE only
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy built fat JAR from builder stage
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Expose HTTP API port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]