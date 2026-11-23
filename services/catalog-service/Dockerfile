# Stage 1: Build Quarkus Fast-JAR
FROM maven:3.9-eclipse-temurin-26-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: Lightweight non-root runtime
FROM eclipse-temurin:24-jre-alpine
WORKDIR /work
RUN apk update && apk upgrade --no-cache && \
    addgroup -S quarkus && adduser -S quarkus -G quarkus

COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/lib/ /work/lib/
COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/*.jar /work/
COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/app/ /work/app/
COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/quarkus/ /work/quarkus/

USER quarkus
EXPOSE 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dquarkus.http.host=0.0.0.0", "-jar", "/work/quarkus-run.jar"]