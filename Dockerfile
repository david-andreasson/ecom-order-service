# syntax=docker/dockerfile:1.6
# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw mvnw

# Ensure wrapper is executable (needed in some environments)
RUN chmod +x mvnw

# Förladda dependencies (cacheas mellan builds)
#TODO kolla om ett bättre alternativ än cachering
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests dependency:go-offline || mvn -q -B -DskipTests dependency:go-offline

# Kopiera källkod och bygg
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -Dmaven.test.skip=true clean package


# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Exponera standardporten för Spring Boot
EXPOSE 8080

# Miljövariabler (kan override:as vid runtime)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50" \
    SPRING_PROFILES_ACTIVE=default

# Skapa datakatalog och sätt generösa rättigheter (dev)
RUN mkdir -p /app/data && chmod -R 777 /app

# Kopiera den byggda applikationen från build-steget (antar exakt en jar i target)
COPY --from=build /workspace/target/*.jar /app/app.jar

# Start
ENTRYPOINT ["java", "-jar", "/app/app.jar"]