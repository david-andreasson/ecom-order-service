# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw mvnw

# Förladda dependencies (cacheas mellan builds)
#TODO kolla om ett bättre alternativ än cachning
RUN ./mvnw -q -B -DskipTests dependency:go-offline || mvn -q -B -DskipTests dependency:go-offline

# Kopiera källkod och bygg
COPY src/ src/
RUN mvn -q -B -DskipTests clean package


RUN JAR_FILE=$(ls target/*-SNAPSHOT.jar || ls target/*.jar | head -n 1) \ 
    && cp "$JAR_FILE" /workspace/app.jar


# Runtime stage
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Exponera standardporten för Spring Boot
EXPOSE 8080

# Miljövariabler (kan override:as vid runtime)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50" \
    SPRING_PROFILES_ACTIVE=default

# Kopiera den byggda applikationen från build-steget
COPY --from=build /workspace/app.jar /app/app.jar

# Kör som nonroot (distroless-basen sätter redan en nonroot-user)
ENTRYPOINT ["/usr/bin/java", "-jar", "/app/app.jar"]