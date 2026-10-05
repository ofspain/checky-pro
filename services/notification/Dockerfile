# Build from the repo root (monorepo — needs the parent POM and every sibling module POM):
#   docker build -f services/notification/Dockerfile -t notification-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/auth/pom.xml services/auth/
COPY services/crypto/pom.xml services/crypto/
COPY services/notification/pom.xml services/notification/
RUN mvn -q -pl services/notification dependency:go-offline
COPY services/notification/src services/notification/src
RUN mvn -q -pl services/notification package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/services/notification/target/notification-service.jar app.jar
USER nonroot
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
