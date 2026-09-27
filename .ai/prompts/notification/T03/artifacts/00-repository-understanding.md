# notification · T03 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` is a consume-only Kafka fan-out layer (L2): it reacts to events from `auth`
and `payments` and delivers over email + in-app channels, owning the `notifications` Postgres
schema. Package-by-feature under `com.themistra.notification`: `consumer`, `preference`,
`template`, `delivery`, `channel`, `inapp`, `common` (`design.md` §6). As of T02, only the schema
exists (8 tables via `V1__notifications_baseline.sql`, `V2` grants, `V3` seed templates) and the
bare skeleton application class (T01) — no package under `com.themistra.notification` other than
the root exists yet; no entity/repository/config class has been written.

No `application.properties` exists yet — T02's own Phase 4 disposition explicitly deferred it to
this task ("Notification's own `tasks.md` task 3 is explicitly titled 'Config & resource server' —
creating `application.properties` in T02 would preempt that task's own deliverable").

## 2. Existing code this task touches

**Already exists (not to be duplicated or reworked):**
- `services/notification/pom.xml` (T01) — already declares
  `spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server` (both
  needed, issuer starter excluded), `spring-boot-starter-validation`, `spring-boot-starter-actuator`
  + `micrometer-registry-prometheus`. Everything this task needs dependency-wise is already present
  — T03 is a config/wiring task, not a dependency-adding one.
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
  (T01) — bare `@SpringBootApplication`, explicitly documented as *not yet* carrying
  `@ConfigurationPropertiesScan` because no `@ConfigurationProperties` class exists yet. This task is
  exactly the one that changes that (mirrors `crypto-service`'s own
  `CryptoServiceApplication.java`, whose Javadoc says "`@ConfigurationPropertiesScan` added in T03,
  the first task to introduce any [`@ConfigurationProperties`]").
- `services/notification/src/main/resources/db/migration/V1-V3` (T02) — the schema this task's
  config eventually feeds into; not touched by T03 (no entity code is in this task's scope either —
  `design.md` §6 assigns entities to their own feature-module tasks).
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  and `NotificationBaselineMigrationIntegrationTest.java` — existing regression guards; not to be
  modified by this task except where genuinely relevant (T01's own file is out of scope per its own
  task boundary; T02's own file already asserts "no `application.properties` exists" in a comment —
  that comment will become stale once T03 lands, worth flagging to whichever phase writes the actual
  implementation, not fixed here in Phase 0).

**New in this task (per `design.md` §6):**
- `common/config/*Properties.java` — validated `@ConfigurationProperties` records for email, link
  base URL, retry, and in-app transport (the four config groups `design.md` §4c's "New configuration
  keys" block lists).
- `common/ResourceServerConfig.java` — JWT resource-server wiring for the in-app surface (L8).
- `common/PublicEndpoints.java` — the actuator-only allowlist.
- `application.properties` itself — does not exist yet anywhere in this module.

## 3. Established patterns to follow

**Validated `@ConfigurationProperties`** — `services/crypto`'s own T03 is the direct, working
precedent (same task number, same kind of task: "Config & resource server" is literally this task's
own title mirroring crypto's). Pattern, per
`services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java` and its
5 siblings: a Java `record` annotated `@ConfigurationProperties(prefix = "themistra.<service>.<group>")`
+ `@Validated`, using `jakarta.validation` annotations (`@Min`, etc.) for simple numeric constraints
and a compact constructor for conditional/cross-field validation (e.g., "if `enabled=true`, `baseUrl`
must be non-blank" — both directions checked, not just the obvious one). `@ConfigurationPropertiesScan`
is added to the Application class in the same task that introduces the first such record — never
before, never left for a later task to add.

**Resource server** — `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java`
+ `PublicEndpoints.java` is the direct precedent for a service that only *validates* JWTs minted by
`auth-service` (unlike `auth` itself, which is the issuer with its own two-chain SAS+application
split — not the pattern to mirror here). Key shape: `@Configuration @EnableWebSecurity`, one
`SecurityFilterChain` bean, `SessionCreationPolicy.STATELESS`, CSRF disabled (stateless bearer-only
API), `PublicEndpoints.PATTERNS` permitted, everything else `.anyRequest().authenticated()`,
`JwtDecoder` left as Spring Boot's own autoconfigured bean sourced from
`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`/`issuer-uri` (both required together — Spring
Boot only adds the issuer validator when `issuer-uri` is set), RFC 9457 `problem+json` bodies for
401/403 via custom `AuthenticationEntryPoint`/`AccessDeniedHandler` beans (matches `agents.md`'s own
"Errors are RFC 9457" rule). `PublicEndpoints` is a small `final` class with a `String[] PATTERNS`
constant and a private constructor; a sweep test (`PublicEndpointsTest`) asserts no other
`permitAll()` exists.

**`application.properties`** — flat only (`agents.md`, both services' own rule). Every property this
task introduces should follow crypto's own established idioms: `${ENV_VAR:local-default}` syntax for
anything externally injected, inline comments citing the task/finding that justified a non-obvious
value, `management.endpoints.web.exposure.include=health,info,prometheus` +
`management.endpoint.health.probes.enabled=true` (required or the `PublicEndpoints`-declared paths
404 regardless of security config — a real gap crypto's own Phase 7/9 caught), and
`spring.datasource.*` pointed at the least-privilege runtime role (`notification_app`, per T02),
never the migration owner.

## 4. Testing conventions

Unit tests: plain JUnit, no Spring context needed for a config record's own validation logic (a
record's compact constructor can be tested by direct construction, `assertThatThrownBy(() -> new
XProperties(...))`, mirroring how `ScreeningProperties`'s own conditional-requirement logic would be
tested). ArchUnit for module-boundary enforcement (L11, not yet written for notification-service —
no feature modules exist yet to police). Contract tests validate consumed payloads against
`contracts/events/*` (not this task's own scope — no consumer exists yet). Testcontainers
(Postgres + Kafka) for integration — the resource-server wiring itself would need either a
`@SpringBootTest` slice with a mocked/local JWKS, or an integration test mirroring crypto's own
`ResourceServerConfigIntegrationTest` (not yet read in detail — flagged for Phase 1).

## 5. Known gaps / unknowns

- **I do not know** the concrete mechanism by which "startup fails on missing config in non-local
  profiles" (L10, this task's own AC-equivalent language) is meant to differ *by profile* in this
  codebase. Neither `services/auth` nor `services/crypto` has an `application-{profile}.properties`
  file — only one flat `application.properties` exists per service, and `crypto`'s own validated
  properties (e.g. `ScreeningProperties`) fail on invalid/missing values universally, not
  conditionally on `spring.profiles.active`. Whether "non-local profiles" real values are meant to
  arrive via env-var injection with no `:default` fallback (so the property is genuinely absent
  outside `local`, tripping Spring Boot's own placeholder-resolution failure) or via a not-yet-created
  profile-specific properties file is unresolved — a genuine open question for Phase 1/2, not
  something to guess at here.
- **I do not know** whether the in-app SSE/websocket surface (O3, not yet decided — `design.md` marks
  it "Recommended: SSE" but still open) has any bearing on this task's own resource-server wiring
  beyond the generic `SecurityFilterChain`. The concrete `InappStreamController`/`InappReadController`
  classes L8 protects don't exist yet (task 13) — this task wires the *filter chain*, not those
  endpoints.
- The email transport (O2/Q2 — SES vs SendGrid vs SMTP) is still open in the spec; this task's own
  "email" config group (`themistra.notification.email.from`, `.transport`) is a generic placeholder
  per `design.md` §4c, not vendor-specific — matches `ScreeningProperties`'s own "vendor unresolved,
  fields stay generic" precedent exactly.
- `NotificationBaselineMigrationIntegrationTest`'s class Javadoc currently asserts "no
  `application.properties` exists yet" — this will become stale the moment this task lands a real
  one. Not this phase's job to fix (Phase 0 is read-only), but worth surfacing now so Phase 2/6
  accounts for it rather than leaving a stale claim in a T02 file this task isn't scoped to modify.
