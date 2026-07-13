# Build from the repo root (monorepo — needs the parent POM):
#   docker build -f services/auth/Dockerfile -t auth-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/auth/pom.xml services/auth/
RUN mvn -q -pl services/auth dependency:go-offline
COPY services/auth/src services/auth/src
RUN mvn -q -pl services/auth package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/services/auth/target/auth-service.jar app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
