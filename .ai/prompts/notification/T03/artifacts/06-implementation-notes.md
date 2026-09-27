# notification · T03 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). Only `src/main` files touched, plus the two justified,
narrow `T01SkeletonRegressionTest.java` line/assertion updates explained under Deviations — no new
test file authored (Phase 10 scope, per this phase's own rule; mirrors `crypto-service`'s own T03,
which deferred all of `InternalTestController`/`PublicEndpointsTest`/etc. to its own Phase 10).

## Files created

- `common/config/EmailProperties.java` — `themistra.notification.email.{from,transport}`, both
  `@NotBlank`. No format/enum constraint (Finding #4).
- `common/config/LinkProperties.java` — `themistra.notification.link.base-url`, deliberately
  unconstrained (the field the spec's own VERBATIM default binds blank in `local`).
- `common/config/RetryProperties.java` — `themistra.notification.retry.*`, `@Min(1)` on all three
  fields plus the compact-constructor cross-field check (`maxBackoffSeconds >= initialBackoffSeconds`,
  Finding #5).
- `common/config/InappProperties.java` — `themistra.notification.inapp.transport`, `@NotBlank`.
- `common/config/LinkPropertiesStartupValidation.java` — package-private `@Component`
  `@Profile("!local")`; constructor throws `IllegalStateException` if `LinkProperties.baseUrl()` is
  blank (Finding #1's concrete mechanism).
- `common/PublicEndpoints.java` — 3-path allowlist (health/info/prometheus only; no `.well-known`
  entry, unlike crypto's own 4-path version).
- `common/ResourceServerConfig.java` — one `SecurityFilterChain`: stateless, CSRF disabled,
  `PublicEndpoints.PATTERNS` permitted, `.anyRequest().authenticated()`, RFC 9457 401/403 handler
  beans. No `/internal/v1/**` scope-authority line (Finding #2 — no such endpoints exist here).
  `@Configuration` + `@EnableWebSecurity` present (Finding #6).
- `application.properties` — new file. Standard datasource (`notification_app`, T02's own
  least-privilege role, `search_path` set to `notifications, public`), JPA (`ddl-auto=validate`,
  harmless with zero entities), Flyway disabled at runtime, Kafka bootstrap servers, resource-server
  `jwk-set-uri`/`issuer-uri` pointed at the local `auth-service` instance, actuator exposure, and all
  8 `themistra.notification.*` keys from `design.md` §4c's own VERBATIM block.

## Files modified

- `NotificationServiceApplication.java` — added `@ConfigurationPropertiesScan`; Javadoc updated to
  record that this is the task introducing it, `@EnableScheduling`/`@EnableSchedulerLock` still
  correctly absent.
- `T01SkeletonRegressionTest.java` — two changes, both required by the frozen brief / forced by
  reality (see Deviations):
  1. `applicationClassIsBareWithOnlyTheMainMethod()`'s `@ConfigurationPropertiesScan` assertion
     flipped from `doesNotContain` to `contains` (frozen brief's own explicit, planned change).
  2. `noExtraProductionClassesExistBeyondTheBareApplicationClass()` (renamed
     `noExtraProductionClassesExistBeyondT03sOwnAuthorizedSet`) — expected file list widened from
     `["NotificationServiceApplication.java"]` to the 8 files T03's own frozen brief authorizes,
     named explicitly rather than loosened to a count check.

## Deviation from the plan, flagged not hidden

The Phase 5 plan anticipated exactly one `T01SkeletonRegressionTest.java` change (the
`@ConfigurationPropertiesScan` assertion, per the frozen brief). Actually implementing the task
surfaced a **second, unanticipated** conflict: `noExtraProductionClassesExistBeyondTheBareApplicationClass()`
asserts the main source tree contains exactly one `.java` file — a T01-era invariant that this task's
own 7 new production files necessarily end. Neither Phase 1, 2, nor 4 caught this (none re-read this
specific test method's own body; Phase 0/1 only noted the `@ConfigurationPropertiesScan` line).
Left unfixed, the build would go red the moment these files landed, until Phase 10. Fixed here
(not deferred) for the same reason the planned change was: this is a correction to an existing
assertion made stale by this task's own authorized main-code change, not new test content — the
distinction the phase's own "no tests here" rule is drawing. The updated assertion names all 8 files
explicitly (not "at least N files"), so it still catches a real future regression (a stray,
unauthorized file) exactly as strictly as the original.

## Verification performed

- `mvn -pl services/notification -am verify` — 22 tests, 0 failures (9 `T01SkeletonRegressionTest`,
  both updated assertions passing; 13 `NotificationBaselineMigrationIntegrationTest`, unaffected).
  Clean `package`/`repackage`.
- Real app boot, `local` profile (default, no override): `mvn -pl services/notification
  spring-boot:run` — started cleanly in 1.7s, Tomcat on 8080, no `LinkPropertiesStartupValidation`
  bean created (profile-gated out), confirming AC5 doesn't regress the `local` profile.
- Real app boot, `dev` profile, no `AUTH_EMAIL_LINK_BASE_URL` set: `mvn -pl services/notification
  spring-boot:run -Dspring-boot.run.profiles=dev` — context refresh genuinely failed:
  `BeanCreationException` wrapping `IllegalStateException: themistra.notification.link.base-url is
  required outside the local profile`, exactly the exception `LinkPropertiesStartupValidation`'s own
  constructor throws. AC5 proven in both directions, not just the pass case.

## Acceptance criteria mapping

- **AC1** — 4 records exist, `@ConfigurationProperties`+`@Validated`, exact prefixes/keys. ✅
- **AC2** — `application.properties` exists with every required key. ✅
- **AC3** — `ResourceServerConfig`'s one `SecurityFilterChain` bean, stateless/CSRF-disabled/
  allowlist-permitted/RFC 9457. ✅
- **AC4** — `PublicEndpoints.PATTERNS` is exactly the 3 actuator paths. ✅ (sweep test itself is
  Phase 10's own job, per this task's required-tests list.)
- **AC5** — real, bidirectional proof above (not merely described). ✅
- **AC6** — `@ConfigurationPropertiesScan` present; `T01SkeletonRegressionTest` updated (both
  necessary assertions, one planned, one discovered during implementation). ✅
