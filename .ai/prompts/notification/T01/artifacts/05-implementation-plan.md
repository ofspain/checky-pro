# notification · T01 · Phase 5 — Implementation Plan

## Files to modify

- Root `pom.xml` — `<modules>` block.

## Files to create

- `services/notification/pom.xml`
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`

## Exact edits

### 1. Root `pom.xml` — `<modules>` block

```xml
  <modules>
    <!-- Listed in dependency order. libs/java/* modules join here as they gain code and must
         precede any service module that depends on them. -->
    <module>services/auth</module>
    <module>services/crypto</module>
    <module>services/notification</module>
  </modules>
```

### 2. `services/notification/pom.xml` — full content

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.themistra</groupId>
    <artifactId>checky-pro</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>notification-service</artifactId>
  <name>notification-service</name>
  <description>Themistra Notification Service — idempotent event fan-out over email and in-app channels</description>

  <!-- Testcontainers pinned to the same version as auth/crypto for the same Docker-API-handshake
       reason (see docker-testcontainers-handshake-issue) - not something specific to those services. -->
  <properties>
    <testcontainers.version>1.21.4</testcontainers.version>
  </properties>

  <!-- O2/Q2 (T01 Phase 2/4): Amazon SES chosen over SendGrid/SMTP - AWS-native, matches the platform's
       existing KMS usage pattern in both sibling services, no new vendor/secret-type/auth paradigm.
       Same BOM version as auth/crypto's own KMS usage, kept aligned (T01SkeletonRegressionTest
       cross-service version check). -->
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>bom</artifactId>
        <version>2.50.2</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Web + validation -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Security: resource server only - this service validates the in-app stream/read API's caller
         JWTs against Auth's JWKS (L8); it never issues tokens. -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-oauth2-resource-server</artifactId>
    </dependency>

    <!-- Persistence: JPA + Flyway DDL-only migrations (D-005 convention) -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- Kafka: this service is primarily a consumer (L2) -->
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Email transport (O2/Q2): Amazon SES via the modern v2 client -->
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>sesv2</artifactId>
    </dependency>

    <!-- Observability -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>io.micrometer</groupId>
      <artifactId>micrometer-registry-prometheus</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>kafka</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Testing stage: module-boundary enforcement (L11) + async/scheduled assertions -->
    <dependency>
      <groupId>com.tngtech.archunit</groupId>
      <artifactId>archunit-junit5</artifactId>
      <version>1.3.0</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <version>4.2.2</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <finalName>notification-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
      <!-- Local-dev only, mirrors services/auth/pom.xml and services/crypto/pom.xml exactly except
           schemas: runs solely via explicit `mvn flyway:migrate`, never during package/verify/CI or
           the production Docker build. -->
      <plugin>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-maven-plugin</artifactId>
        <version>11.7.2</version>
        <configuration>
          <url>jdbc:postgresql://localhost:5432/checky</url>
          <user>checky</user>
          <password>checky-local-only</password>
          <schemas>notifications</schemas>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 3. `NotificationServiceApplication.java` — full content

```java
package com.themistra.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Themistra Notification Service — idempotent Kafka consumer fanning domain events out to
 * email and in-app channels.
 *
 * <p>Spec: spec/notification-service/design.md. Standing rules: spec/notification-service/agents.md.</p>
 *
 * <p>Bare skeleton (T01) - no {@code @ConfigurationPropertiesScan}/{@code @EnableScheduling}/
 * {@code @EnableSchedulerLock} yet, since no {@code @ConfigurationProperties} class or scheduled job
 * exists yet either. Add each annotation in the task that actually introduces the thing it enables,
 * mirroring crypto-service's own T01 discipline - never mirror a sibling's annotations blindly.</p>
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

### 4. `T01SkeletonRegressionTest.java` — design (exact assertions, not full source — Phase 6 writes it)

Mirrors `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java`'s own
established style (plain JUnit, "content must be present/absent" scan — no production code exists yet
for a structural/ArchUnit rule to analyze). Methods:

1. `rootPomRegistersNotificationServiceAfterAuthAndCrypto()` — root `pom.xml` contains
   `<module>services/notification</module>`, positioned after both existing modules (string-index
   comparison, mirroring crypto's own `rootPomRegistersCryptoServiceAfterAuthServiceWithOrderingComment`).
2. `notificationPomDeclaresTheRequiredDependenciesAndExcludesTheIssuerStarterAndOutbox()` — asserts
   presence of every artifact in the Phase 4 frozen list (both resource-server artifacts named
   explicitly, `sesv2`, all three test starters, both module-boundary/async test libraries) and absence
   of `oauth2-authorization-server`.
3. `sharedDependencyVersionsStayAlignedWithAuthAndCrypto()` — `testcontainers.version`, `archunit-junit5`,
   `awaitility`, and the AWS SDK `bom` version all match both sibling poms (extends crypto's own
   two-service check to three).
4. `finalNameAndFlywayPluginMirrorTheSiblingConvention()` — `<finalName>notification-service</finalName>`;
   `flyway-maven-plugin` version `11.7.2`; `<schemas>notifications</schemas>`.
5. `applicationClassIsBareWithOnlyTheMainMethod()` — `NotificationServiceApplication.java` contains
   `@SpringBootApplication`, `SpringApplication.run`, package `com.themistra.notification`; does **not**
   contain `@ConfigurationPropertiesScan`, `@EnableScheduling`, or `@EnableSchedulerLock`.
6. `sesV2ClientCanActuallyBeConstructed()` — Phase 4 Finding #4's resolution: builds a real
   `SesV2Client` via `SesV2Client.builder().region(Region.US_EAST_1).credentialsProvider(
   StaticCredentialsProvider.create(AwsBasicCredentials.create("test","test"))).build()` inside a
   try-with-resources and asserts non-null. No real AWS call is made — SDK v2 client construction
   resolves the HTTP-client implementation and validates configuration synchronously, which is exactly
   where a missing HTTP-client artifact would throw. This either confirms or refutes Finding #4's
   concern empirically, in this task, per the Phase 4 decision.

## Execution order

1. Apply the root `pom.xml` edit.
2. Create `services/notification/pom.xml` exactly as pinned.
3. Run `mvn -pl services/notification -am dependency:resolve` to confirm every declared artifact
   resolves (AC2) before writing any Java.
4. Create `NotificationServiceApplication.java` exactly as pinned.
5. Run `mvn -pl services/notification -am verify` to confirm `package`/`repackage` succeeds (AC4) —
   this is the exact crypto-service-T01 failure mode being guarded against; must be checked for real,
   not assumed.
6. Create `T01SkeletonRegressionTest.java` per the design above.
7. Run `mvn -pl services/notification -am test -Dtest=T01SkeletonRegressionTest` to confirm all 6
   methods pass, including the real `SesV2Client` construction (Finding #4).
8. Run a full `mvn -pl services/notification -am verify` for the final record.
9. Write Phase 6's implementation notes with the real result of every step above — including whichever
   way Finding #4's empirical check actually lands.
