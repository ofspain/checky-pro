STATUS: FROZEN

# notification · T03 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings independently verified against `services/crypto`'s own precedent files before
disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | Profile-conditional `LinkProperties.baseUrl` validation mechanism unspecified | High | **ACCEPTED** | A new, small `@Component` (`LinkPropertiesStartupValidation`, in `common/config`), annotated `@Profile("!local")`, whose constructor takes `LinkProperties` and throws `IllegalStateException` if `baseUrl()` is null/blank. Being a `@Component`, Spring only instantiates it — and so only runs the check — when the active profile is not `local`; the failure happens eagerly at context-startup wiring, not lazily on first use. Required test: `@SpringBootTest` with `spring.profiles.active=dev` (or any non-`local` value) and no `AUTH_EMAIL_LINK_BASE_URL` set must fail context startup with this exception; a corresponding `local`-profile test must NOT fail. |
| 2 | "Mirrors crypto exactly" contradicts "no scope/authority check" | Medium | **ACCEPTED** | TIB wording replaced: `ResourceServerConfig` mirrors the *structural shape* of crypto's filter chain (stateless, CSRF disabled, `PublicEndpoints` permitAll, JWT bearer, RFC 9457 entry/denied handlers, `.anyRequest().authenticated()`) but omits the `/internal/v1/**` scope-authority line entirely — no such endpoints exist in this task's scope. |
| 3 | No test-only controller listed for the resource-server integration test | Medium | **ACCEPTED** | Added to Files to Create (test sources): `services/notification/src/test/java/com/themistra/notification/common/ResourceServerTestController.java` — a minimal `@RestController` with one arbitrary secured `GET` endpoint, mirroring crypto's own `InternalTestController`. |
| 4 | Email/transport value validation (format, enum) unspecified | Low-Medium | **ACCEPTED (option a)** | No format/enum validation beyond `@NotBlank`/`@Min` in T03. `EmailProperties.transport` and `InappProperties.transport` stay plain, unconstrained strings — restricting them to a fixed value set is deferred to the tasks that implement the corresponding channels (mirrors `ScreeningProperties`'s own "vendor unresolved, fields stay generic" precedent; also avoids prematurely locking out a test-only `fake` transport value before any channel/test infrastructure exists to define one). |
| 5 | No cross-field validation between `initial-backoff-seconds` and `max-backoff-seconds` | Low | **ACCEPTED** | `RetryProperties`'s compact constructor adds: `maxBackoffSeconds >= initialBackoffSeconds`, else `IllegalStateException`, mirroring `ScreeningProperties`'s own compact-constructor cross-field check style. Required test: constructing with `max < initial` throws. |
| 6 | `@Configuration`/`@EnableWebSecurity` on `ResourceServerConfig` not stated | Low | **ACCEPTED** | Explicitly required, matching crypto's precedent exactly. |
| 7 | `PublicEndpoints` sweep-test scope ambiguous (static scan vs. behavioral) | Low | **ACCEPTED** | Both required, mirroring crypto's own `PublicEndpointsTest` exactly (minus its `.well-known` entry — notification-service has only 3 patterns, not 4): (a) a static assertion that `PublicEndpoints.PATTERNS` contains exactly `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`; (b) a `@WebMvcTest`+`MockMvc` parameterized test proving those 3 paths are not blocked by security (`isNotIn(401, 403)`) and that sensitive actuator paths (`/actuator/env`, `/actuator/beans`, `/actuator/configprops`, `/actuator/loggers`, `/actuator/heapdump`, `/actuator/threaddump`) return 401. |
| 8 | `application.properties` key coverage only names one of crypto's two precedent tests | Low | **ACCEPTED** | Required Tests now names both: a security/actuator-key test (mirrors `ApplicationPropertiesSecurityConfigTest`) and a JPA/datasource/Flyway/Kafka-key test (mirrors `ApplicationPropertiesJpaConfigTest`), asserting at minimum: `spring.datasource.url`, `spring.datasource.username=notification_app`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.flyway.enabled=false`, `spring.kafka.bootstrap-servers`, `spring.application.name`, `management.endpoints.web.exposure.include=health,info,prometheus`, `management.endpoint.health.probes.enabled=true`, `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, `spring.security.oauth2.resourceserver.jwt.issuer-uri`, plus all 8 `themistra.notification.*` keys from `design.md` §4c. |

## Task

Unchanged from Phase 2, with all 8 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- `common/config/LinkPropertiesStartupValidation.java` — new, `@Profile("!local")`-gated
  constructor-validation component (Finding #1).
- `ResourceServerConfig`'s own Javadoc/comment states the structural-mirror-not-exact-mirror
  relationship to crypto's own class explicitly (Finding #2); `@Configuration` +
  `@EnableWebSecurity` required (Finding #6).
- `services/notification/src/test/java/com/themistra/notification/common/ResourceServerTestController.java`
  — new, test-only (Finding #3).
- `RetryProperties`'s compact constructor cross-field check (Finding #5).
- `PublicEndpoints` test covers both static-scan and MockMvc-behavioral cases (Finding #7).
- Two `application.properties` key-coverage tests, not one, with the exact key list above (Finding #8).

**Out (unchanged from Phase 2, plus):** format/enum validation on `EmailProperties.transport` /
`InappProperties.transport` (Finding #4) — explicitly deferred, not merely omitted by oversight.

## Business Rules

Unchanged from Phase 2 (none individually implemented; R1/R2/R5 depend on `LinkProperties`,
R16/R17 depend on `ResourceServerConfig`).

## Locked Decisions

Unchanged from Phase 2: L8, L10.

## Dependencies

Unchanged from Phase 2.

## Inputs

Unchanged from Phase 2, plus: `services/crypto`'s own `InternalTestController.java`,
`PublicEndpointsTest.java`, `ApplicationPropertiesSecurityConfigTest.java`,
`ApplicationPropertiesJpaConfigTest.java` as direct structural precedent for the newly-specified
tests (Findings #3, #7, #8).

## Outputs

Unchanged from Phase 2, plus: `LinkPropertiesStartupValidation.java`,
`ResourceServerTestController.java` (test-only).

## State Changes

None (unchanged from Phase 2).

## Files to Create

Phase 2's list, plus:
- `services/notification/src/main/java/com/themistra/notification/common/config/LinkPropertiesStartupValidation.java`
- `services/notification/src/test/java/com/themistra/notification/common/ResourceServerTestController.java`

## Files to Modify

Unchanged from Phase 2: `NotificationServiceApplication.java` (add
`@ConfigurationPropertiesScan` only), `T01SkeletonRegressionTest.java` (flip the one assertion only).

## Files NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 2's AC1-AC6, with AC5 now naming the concrete mechanism
(`LinkPropertiesStartupValidation`, `@Profile("!local")`) rather than describing it abstractly, and
AC1 clarified to explicitly exclude format/enum validation on the two `transport` fields (Finding #4).

## Required Tests

`NotificationBaselineMigrationIntegrationTest` is unaffected (T02's own file, not touched). This
task's own required tests, per the dispositions above:
- Per-record validation tests for all four `*Properties` records, including `RetryProperties`'s new
  cross-field check (Finding #5).
- `LinkPropertiesStartupValidation`'s own `@SpringBootTest`-level fail/pass proof across profiles
  (Finding #1).
- `PublicEndpoints` static-scan + MockMvc-behavioral test, using the new `ResourceServerTestController`
  (Findings #3, #7).
- A resource-server integration test (unauthenticated → 401 `problem+json`; valid JWT → 200), also
  using `ResourceServerTestController`.
- Two `application.properties` key-coverage tests (security/actuator; JPA/datasource/Flyway/Kafka),
  per the exact key list in Finding #8's disposition.
- `T01SkeletonRegressionTest`'s updated single-line assertion.

## Constraints

Unchanged from Phase 2, plus: `EmailProperties.transport`/`InappProperties.transport` must remain
unconstrained strings in this task (Finding #4) — a future task narrowing them to an enum/allowed-set
is not a violation of this task's own design, since nothing here promises otherwise.

## Open Questions

No blockers. All 8 Phase 3 findings resolved above.
