# Build from the repo root (monorepo — needs the parent POM):
#   docker build -f services/crypto/Dockerfile -t crypto-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/crypto/pom.xml services/crypto/
RUN mvn -q -pl services/crypto dependency:go-offline
COPY services/crypto/src services/crypto/src
RUN mvn -q -pl services/crypto package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/services/crypto/target/crypto-service.jar app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
