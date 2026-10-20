# Build from the repo root (monorepo: needs the parent POM and every sibling module POM):
#   docker build -f services/auth/Dockerfile -t auth-service .
#
# Every module the root pom.xml declares has its pom.xml copied below. Maven validates the whole
# reactor even with -pl, so leaving one out fails the build ("Child module ... does not exist").
# DockerfileModulePomsTest checks this against the root pom's own module list.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/auth/pom.xml services/auth/
COPY services/crypto/pom.xml services/crypto/
COPY services/notification/pom.xml services/notification/
COPY services/payment/pom.xml services/payment/
RUN mvn -q -pl services/auth dependency:go-offline
COPY services/auth/src services/auth/src
RUN mvn -q -pl services/auth package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/services/auth/target/auth-service.jar app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
