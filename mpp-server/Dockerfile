# Multi-stage build for mpp-server
# Stage 1: Build the application
FROM gradle:8.1-jdk17 AS builder

WORKDIR /app

# Copy gradle files first for better caching
COPY gradle ./gradle
COPY gradlew ./
COPY gradle.properties ./
COPY settings.gradle.kts ./
COPY build.gradle.kts ./

# Copy all module build files
COPY mpp-core/build.gradle.kts ./mpp-core/
COPY mpp-ui/build.gradle.kts ./mpp-ui/
COPY mpp-server/build.gradle.kts ./mpp-server/
COPY mpp-codegraph/build.gradle.kts ./mpp-codegraph/

# Download dependencies (cached layer)
RUN ./gradlew :mpp-server:dependencies --no-daemon

# Copy source code
COPY mpp-core/src ./mpp-core/src
COPY mpp-server/src ./mpp-server/src

# Copy other necessary files
COPY mpp-core/AutoDevCore.podspec ./mpp-core/
COPY mpp-core/webpack.config.d ./mpp-core/webpack.config.d
COPY mpp-server/webpack.config.d ./mpp-server/webpack.config.d

# Build fat JAR
RUN ./gradlew :mpp-server:fatJar --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install required runtime dependencies
RUN apk add --no-cache bash curl

# Create user for running the app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the fat JAR from builder
COPY --from=builder /app/mpp-server/build/libs/mpp-server-*-all.jar /app/mpp-server.jar

# Create directory for SQLite database
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
CMD ["java", "-Xmx512m", "-Xms256m", "-jar", "/app/mpp-server.jar"]

