# syntax=docker/dockerfile:1.6
# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw mvnw

# Ensure wrapper is executable (needed in some environments)
RUN chmod +x mvnw

# Preload dependencies (cached between builds)
# TODO: evaluate a better alternative than caching
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests dependency:go-offline || mvn -q -B -DskipTests dependency:go-offline

# Copy sources and build
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -Dmaven.test.skip=true clean package


# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Expose default Spring Boot port
EXPOSE 8080

# Environment variables (override at runtime if needed)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50" \
    SPRING_PROFILES_ACTIVE=default

# Create data directory and set permissive access (dev)
RUN mkdir -p /app/data && chmod -R 777 /app

# Copy built application from the build stage (assumes a single jar in target)
COPY --from=build /workspace/target/*.jar /app/app.jar

# Start
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "/app/app.jar"]