# notification · T03 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/common/config/EmailProperties.java`
2. `services/notification/src/main/java/com/themistra/notification/common/config/LinkProperties.java`
3. `services/notification/src/main/java/com/themistra/notification/common/config/RetryProperties.java`
4. `services/notification/src/main/java/com/themistra/notification/common/config/InappProperties.java`
5. `services/notification/src/main/java/com/themistra/notification/common/config/LinkPropertiesStartupValidation.java`
6. `services/notification/src/main/java/com/themistra/notification/common/PublicEndpoints.java`
7. `services/notification/src/main/java/com/themistra/notification/common/ResourceServerConfig.java`
8. `services/notification/src/main/resources/application.properties`
9. `services/notification/src/test/java/com/themistra/notification/common/ResourceServerTestController.java`
   (test scope only — Finding #3; not shipped in `src/main`)
10. `services/notification/src/test/java/com/themistra/notification/common/config/EmailPropertiesTest.java`
11. `services/notification/src/test/java/com/themistra/notification/common/config/LinkPropertiesTest.java`
12. `services/notification/src/test/java/com/themistra/notification/common/config/RetryPropertiesTest.java`
13. `services/notification/src/test/java/com/themistra/notification/common/config/InappPropertiesTest.java`
14. `services/notification/src/test/java/com/themistra/notification/common/config/LinkPropertiesStartupValidationTest.java`
15. `services/notification/src/test/java/com/themistra/notification/common/PublicEndpointsTest.java`
16. `services/notification/src/test/java/com/themistra/notification/common/ResourceServerConfigIntegrationTest.java`
17. `services/notification/src/test/java/com/themistra/notification/ApplicationPropertiesSecurityConfigTest.java`
18. `services/notification/src/test/java/com/themistra/notification/ApplicationPropertiesJpaConfigTest.java`

## Files to modify

1. `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
   — add `@ConfigurationPropertiesScan`.
2. `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
   — flip the one `@ConfigurationPropertiesScan` assertion (line ~198) from `doesNotContain` to
   `contains`. No other line touched.

No files outside this list. `pom.xml`, T02's migration/test files, and everything under `spec/` are
untouched, per frozen brief.

## Public methods (signatures)

Records expose their canonical accessors implicitly; only the record component shape and any
non-trivial method are listed.

**`EmailProperties`** (`@ConfigurationProperties(prefix = "themistra.notification.email")`, `@Validated`)
```java
public record EmailProperties(
    @NotBlank String from,
    @NotBlank String transport
) {}
```
No format/enum constraint on either field (Finding #4 — deferred to the task that implements
`EmailChannel`).

**`LinkProperties`** (`@ConfigurationProperties(prefix = "themistra.notification.link")`, `@Validated`)
```java
public record LinkProperties(
    String baseUrl
) {}
```
Deliberately unconstrained here — `design.md`'s own VERBATIM default binds this blank in `local`.
Its non-blank requirement outside `local` is enforced by `LinkPropertiesStartupValidation`, not by
`@NotBlank` on this record (an unconditional `@NotBlank` would break the `local` profile, which the
spec's own default value requires to succeed).

**`RetryProperties`** (`@ConfigurationProperties(prefix = "themistra.notification.retry")`, `@Validated`)
```java
public record RetryProperties(
    @Min(1) int maxAttempts,
    @Min(1) int initialBackoffSeconds,
    @Min(1) int maxBackoffSeconds
) {
    public RetryProperties {
        if (maxBackoffSeconds < initialBackoffSeconds) {
            throw new IllegalStateException(
                "themistra.notification.retry.max-backoff-seconds must be >= initial-backoff-seconds");
        }
    }
}
```
Cross-field check per Finding #5, mirroring `ScreeningProperties`'s own compact-constructor style.

**`InappProperties`** (`@ConfigurationProperties(prefix = "themistra.notification.inapp")`, `@Validated`)
```java
public record InappProperties(
    @NotBlank String transport
) {}
```
No enum constraint (Finding #4 — deferred).

**`LinkPropertiesStartupValidation`** (`@Component`, `@Profile("!local")`)
```java
@Component
@Profile("!local")
class LinkPropertiesStartupValidation {
    LinkPropertiesStartupValidation(LinkProperties linkProperties) {
        if (linkProperties.baseUrl() == null || linkProperties.baseUrl().isBlank()) {
            throw new IllegalStateException(
                "themistra.notification.link.base-url is required outside the local profile");
        }
    }
}
```
Package-private — nothing outside `common.config` constructs this directly; Spring does, via
constructor injection, only when the active profile is not `local` (Finding #1).

**`PublicEndpoints`** (`public final class`, no instances)
```java
public final class PublicEndpoints {
    public static final String[] PATTERNS = {
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus"
    };
    private PublicEndpoints() {}
}
```
3 patterns, not crypto's 4 — no `.well-known` entry (nothing in this spec calls for one).

**`ResourceServerConfig`** (`@Configuration`, `@EnableWebSecurity`) — Finding #6
```java
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint problemJsonAuthenticationEntryPoint,
            AccessDeniedHandler problemJsonAccessDeniedHandler) throws Exception;

    @Bean
    public AuthenticationEntryPoint problemJsonAuthenticationEntryPoint(ObjectMapper objectMapper);

    @Bean
    public AccessDeniedHandler problemJsonAccessDeniedHandler(ObjectMapper objectMapper);
}
```
Structural mirror of crypto's own class (Finding #2's clarified wording): stateless session policy,
CSRF disabled, `PublicEndpoints.PATTERNS` permitted, `.anyRequest().authenticated()` — no
`/internal/v1/**`-style scope/authority line, since no such endpoints exist in this task's scope.

## Private methods

- `ResourceServerConfig.writeProblemJson(HttpServletResponse, ObjectMapper, HttpStatus, String, String)`
  — static helper shared by both handler beans, mirrors crypto's own identical private method.

## Entities used

None. This task adds no `@Entity`.

## Repositories used

None.

## Services used

None. No `@Service` class in this task's scope — pure config/security wiring.

## Unit / integration tests required

1. `EmailPropertiesTest` — valid construction succeeds; blank `from`/`transport` each throw
   (`@NotBlank` via `@Validated` binding, or direct constructor `assertThatThrownBy` if tested
   without a Spring context — Phase 6 to decide the lighter-weight option, consistent with plain-JUnit
   testing conventions per `agents.md`).
2. `LinkPropertiesTest` — valid non-blank construction succeeds; blank/null construction also
   succeeds (this record itself has no `@NotBlank` — the point of Finding #1's fix living elsewhere).
3. `RetryPropertiesTest` — valid construction succeeds; `maxAttempts`/`initialBackoffSeconds`/
   `maxBackoffSeconds` each `< 1` throw; `maxBackoffSeconds < initialBackoffSeconds` throws (Finding #5).
4. `InappPropertiesTest` — valid construction succeeds; blank `transport` throws.
5. `LinkPropertiesStartupValidationTest` — a `@SpringBootTest`-level test (or narrower slice that
   still exercises real Spring profile-conditional bean creation) proving: `spring.profiles.active=dev`
   (or `staging`/`prod`) with no `AUTH_EMAIL_LINK_BASE_URL` fails context startup with the exact
   exception; `spring.profiles.active=local` with no base URL does NOT fail (Finding #1).
6. `PublicEndpointsTest` — static assertion `PublicEndpoints.PATTERNS` contains exactly the 3 paths;
   `@WebMvcTest`+`MockMvc` parameterized test proving those 3 are not blocked (`isNotIn(401, 403)`)
   and the 6 sensitive actuator paths return 401 (Finding #7), using the new
   `ResourceServerTestController`.
7. `ResourceServerConfigIntegrationTest` — unauthenticated request to
   `ResourceServerTestController`'s secured endpoint → 401 `problem+json`; validly-signed JWT → 200.
8. `ApplicationPropertiesSecurityConfigTest` — asserts the security/actuator/resource-server keys
   exist with expected values (Finding #8's list).
9. `ApplicationPropertiesJpaConfigTest` — asserts the datasource/JPA/Flyway/Kafka keys exist with
   expected values (Finding #8's list).
10. `T01SkeletonRegressionTest`'s existing suite, re-run with its one updated assertion — proves the
    flip doesn't regress anything else T01 established.

## Execution order

1. `EmailProperties`, `LinkProperties`, `RetryProperties`, `InappProperties` (no dependencies on
   anything else new).
2. `LinkPropertiesStartupValidation` (depends on `LinkProperties` existing).
3. `PublicEndpoints` (no dependencies).
4. `ResourceServerConfig` (depends on `PublicEndpoints`).
5. `application.properties` (must exist before any Spring-context-based test in steps 6-10 can run;
   needs every key the 4 properties records + `ResourceServerConfig` + the datasource/JPA/Flyway/
   Kafka sections require).
6. `NotificationServiceApplication` — add `@ConfigurationPropertiesScan` (needed for steps 1-4's
   records to actually bind in any Spring-context test).
7. `T01SkeletonRegressionTest` — flip the one assertion (now true).
8. `ResourceServerTestController` (test-only; needed by steps 9-10).
9. Unit tests for the 4 properties records + `LinkPropertiesStartupValidationTest` (steps 1-2's own
   proof).
10. `PublicEndpointsTest`, `ResourceServerConfigIntegrationTest`,
    `ApplicationPropertiesSecurityConfigTest`, `ApplicationPropertiesJpaConfigTest` (need
    `application.properties` + `ResourceServerTestController` + `@ConfigurationPropertiesScan` all in
    place first).
