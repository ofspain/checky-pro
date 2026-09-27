# notification · T03 · Phase 2 — Task Implementation Brief

## Task

Add four validated `@ConfigurationProperties` records (email, link base URL, retry, in-app
transport), wire a JWT resource-server `SecurityFilterChain` for the in-app surface (L8), add
`PublicEndpoints` (actuator-only allowlist), and create `services/notification/src/main/resources/application.properties`
(does not exist anywhere in this module yet).

## Purpose

Lays down the config-validation and security foundation every later feature task depends on: no
feature task should need to add its own ad-hoc property binding or security wiring — this task
establishes the one place both live, mirroring `crypto-service`'s own T03.

## Scope

**In:**
- `EmailProperties` (`themistra.notification.email.{from,transport}`), `LinkProperties`
  (`themistra.notification.link.base-url`), `RetryProperties`
  (`themistra.notification.retry.{max-attempts,initial-backoff-seconds,max-backoff-seconds}`),
  `InappProperties` (`themistra.notification.inapp.transport`) — each its own `record`,
  `@ConfigurationProperties`-annotated with its own prefix, `@Validated`, under
  `common/config/`.
- `ResourceServerConfig` — one `SecurityFilterChain` bean: stateless, CSRF disabled,
  `PublicEndpoints.PATTERNS` permitted, everything else `.anyRequest().authenticated()`, JWT
  validated via Spring Boot's autoconfigured `JwtDecoder` (`jwk-set-uri` + `issuer-uri`), RFC 9457
  `problem+json` 401/403 bodies (mirrors `crypto-service`'s own `ResourceServerConfig` exactly — no
  custom scope/authority check here, since T03 only wires the filter chain; the endpoints L8 protects
  don't exist until task 13).
- `PublicEndpoints` — `String[] PATTERNS` = actuator health/info/prometheus paths only. No
  `.well-known` entry (unlike crypto's own — notification-service publishes no verification-keys
  endpoint; nothing in this spec calls for one).
- `application.properties` — every key from `design.md` §4c's "New configuration keys" block, plus
  `spring.datasource.*` (pointed at `notification_app`, T02's own least-privilege role, never the
  migration owner), `spring.jpa.hibernate.ddl-auto=validate` + `spring.jpa.open-in-view=false`,
  `spring.flyway.enabled=false` (migrations only via the Maven plugin, mirroring both siblings),
  `spring.kafka.bootstrap-servers` (shared broker, port 9094 per the established local convention),
  `spring.security.oauth2.resourceserver.jwt.{jwk-set-uri,issuer-uri}` (pointed at the local
  auth-service instance, mirroring crypto's own binding), `management.endpoints.web.exposure.include=health,info,prometheus`
  + `management.endpoint.health.probes.enabled=true` (required or `PublicEndpoints`'s declared paths
  404 regardless of security config — crypto's own Phase 7/9 finding, applies identically here).
- `NotificationServiceApplication` — add `@ConfigurationPropertiesScan` (the annotation this task's
  own first `@ConfigurationProperties` class requires; T01's own Javadoc anticipated this).
- `T01SkeletonRegressionTest.java` — flip one line: `.doesNotContain("@ConfigurationPropertiesScan")`
  → `.contains("@ConfigurationPropertiesScan")`. `@EnableScheduling`/`@EnableSchedulerLock` stay
  correctly absent (task 14's own scope) — this is the only line this task touches in that file.

**Out:**
- Any feature module (`consumer`, `preference`, `template`, `delivery`, `channel`, `inapp`) or its
  entities/repositories/services — later tasks' own scope per `design.md` §6.
- `EmailChannel`/`InAppChannel` implementations, `InappStreamController`/`InappReadController` —
  `ResourceServerConfig` wires the filter chain they will sit behind; it does not create them.
- ArchUnit module-boundary enforcement (L11) — task 16's own scope.
- Resolving Q2 (email transport vendor), Q3 (SSE vs WebSocket — already defaulted to `sse` in
  `design.md`'s own VERBATIM config block), Q4 (link base URL's real value), Q6 (real retry/backoff
  values beyond the VERBATIM defaults) — this task adds the *config keys*, not their real-world
  values; those remain each later task's own concern.

## Business Rules

No individual R-numbered requirement is behaviorally implemented here (config/security foundation
only). R1/R2/R5 depend on `LinkProperties` existing; R16/R17 depend on `ResourceServerConfig`
existing; neither requirement's own behavior is built by this task.

## Locked Decisions

- **L8.** In-app surface is zero-trust: JWT validated as an OAuth2 resource server against the Auth
  JWKS; `PublicEndpoints` allows only actuator. (`sub`-scoping of results is task 13's own concern —
  this task authenticates the request, it does not scope a query.)
- **L10.** No secret/credential committed; validated `@ConfigurationProperties` fail startup on
  missing/invalid config in non-local profiles.

## Dependencies

`spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server` (T01, both
present, issuer starter excluded), `spring-boot-starter-validation` (T01, present — needed for
`jakarta.validation` annotations), `spring-boot-starter-actuator` (T01, present). T02's
`notification_app` role/schema (the datasource this task's `application.properties` points at). No
new Maven dependency.

## Inputs

`design.md` §4c's "New configuration keys" VERBATIM block (the exact property names/defaults);
`services/crypto`'s own `common/config/*Properties.java`, `ResourceServerConfig.java`,
`PublicEndpoints.java`, and `application.properties`'s own "Resource server" section as direct
structural precedent (same task number, same kind of task).

## Outputs

Four new `*Properties.java` records, `ResourceServerConfig.java`, `PublicEndpoints.java`, a new
`application.properties`, a `@ConfigurationPropertiesScan`-bearing `NotificationServiceApplication`,
an updated `T01SkeletonRegressionTest.java` (one line).

## State Changes

None to any database. No new table, no new migration — this task is JVM-config and in-memory
security wiring only.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/common/config/EmailProperties.java`
- `.../common/config/LinkProperties.java`
- `.../common/config/RetryProperties.java`
- `.../common/config/InappProperties.java`
- `.../common/ResourceServerConfig.java`
- `.../common/PublicEndpoints.java`
- `services/notification/src/main/resources/application.properties`

## Files to Modify

- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
  (add `@ConfigurationPropertiesScan` only)
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (flip the one `@ConfigurationPropertiesScan` assertion only — no other line)

## Files NOT to Modify

- `services/notification/pom.xml` (no new dependency needed).
- `services/notification/src/main/resources/db/migration/V1-V3` and
  `NotificationBaselineMigrationIntegrationTest.java` (T02's own deliverable; its stale "no
  `application.properties` exists" Javadoc line is a documentation nit flagged in Phase 0/1, not a
  functional dependency — left to a future pass, not this task).
- Every file under `spec/`.
- `services/auth`, `services/crypto` (precedent only).

## Acceptance Criteria

1. **AC1.** `EmailProperties`, `LinkProperties`, `RetryProperties`, `InappProperties` each exist,
   each `@ConfigurationProperties`+`@Validated`, bound to the exact prefixes/keys `design.md` §4c
   specifies.
2. **AC2.** `application.properties` exists, contains every key from that block plus the standard
   datasource/JPA/Flyway/Kafka/actuator/resource-server sections, following the sibling services'
   established `${ENV_VAR:default}` idiom.
3. **AC3.** `ResourceServerConfig` exists with one `SecurityFilterChain` bean: stateless, CSRF
   disabled, `PublicEndpoints.PATTERNS` permitted, all else authenticated, RFC 9457 401/403 bodies.
4. **AC4.** `PublicEndpoints.PATTERNS` contains only the 3 actuator paths — no other entry, no other
   `permitAll()` anywhere in the module (swept by test).
5. **AC5.** Startup genuinely fails outside the `local` profile when a required value is missing —
   concretely, `LinkProperties.baseUrl` is the one field with no non-blank VERBATIM default
   (`design.md`'s own `${AUTH_EMAIL_LINK_BASE_URL:}` binds blank locally); its own non-blank
   requirement must therefore be conditional on the active profile, unlike the other three groups'
   fields, which all have real non-blank VERBATIM defaults (`no-reply@checky.pro`, `ses`, `5`, `30`,
   `3600`, `sse`) and so can use unconditional validation that never trips locally regardless of
   profile — a working resolution of Phase 1's own open question, not a new requirement; subject to
   Phase 3 challenge like any other design choice.
6. **AC6.** `NotificationServiceApplication` carries `@ConfigurationPropertiesScan`;
   `T01SkeletonRegressionTest`'s corresponding assertion is updated; `@EnableScheduling`/
   `@EnableSchedulerLock` remain correctly absent.

## Required Tests

- Per-record validation tests (valid construction succeeds; invalid/missing required fields throw) —
  mirrors `ScreeningProperties`'s own conditional-requirement test style for `LinkProperties`
  specifically; simple `@NotBlank`/`@Min`-style tests for the other three.
- `PublicEndpoints` sweep test (mirrors `crypto-service`'s own `PublicEndpointsTest`): no
  `permitAll()` pattern exists outside the declared allowlist.
- A resource-server integration test proving an unauthenticated request to a non-public path is
  rejected (401, `problem+json`) and a validly-signed JWT is accepted (mirrors `crypto-service`'s own
  `ResourceServerConfigIntegrationTest` — Phase 5 to confirm its exact shape before duplicating it).
- `T01SkeletonRegressionTest`'s updated assertion (existing file, one-line change) plus a new
  assertion in this task's own test file(s) that `application.properties` exists with the expected
  keys (mirrors `crypto-service`'s own `ApplicationPropertiesSecurityConfigTest`).

## Constraints

- **Module boundaries (L11-adjacent, though L11 itself is task 16's scope):** everything this task
  adds lives in `common`/`common/config` — no feature-module package exists yet to violate.
- **Security:** `ResourceServerConfig` must not weaken to `permitAll()` anywhere outside
  `PublicEndpoints`; CSRF disable is justified only because this is a stateless bearer-only API (no
  session/cookie auth ever exists on this service).
- **Secrets discipline (L10, L4):** no real credential/secret value committed anywhere in
  `application.properties` — only `local-only`-style placeholders or externally-injected env vars
  with safe local defaults.
- **Thread-safety / transactions:** not applicable — no repository, no transactional boundary in this
  task's own scope.
- **Null handling:** `@ConfigurationProperties` records use Java's own null-unfriendliness plus
  `jakarta.validation` to fail fast rather than propagate a null/blank value into later code.

## Open Questions

No blockers. AC5's own mechanism is resolved as a working decision above (profile-conditional
validation confined to `LinkProperties.baseUrl` alone), subject to Phase 3 challenge like any other
design choice in this brief.
