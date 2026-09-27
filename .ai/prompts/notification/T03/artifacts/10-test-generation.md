# notification · T03 · Phase 10 — Test Generation

All tests deferred from Phase 6 (per that phase's own explicit rule) and tracked forward at Phase 9
are added here. `package.md` §8's 19 named tests still require feature code this task doesn't add
(consumer, preference resolution, template rendering, in-app controllers) — none apply directly,
mirroring T02's own Phase 1/10 finding. 11 new test files, 41 new tests (9 T01 + 13 T02 unaffected =
63 total).

## Files created

- `common/config/EmailPropertiesTest.java` — 3 tests.
- `common/config/InappPropertiesTest.java` — 2 tests.
- `common/config/RetryPropertiesTest.java` — 5 tests.
- `common/config/LinkPropertiesTest.java` — 3 tests.
- `common/config/LinkPropertiesStartupValidationTest.java` — 4 tests.
- `common/ResourceServerTestController.java` (test-only, no `@Test` methods).
- `common/PublicEndpointsTest.java` — 13 tests.
- `common/ResourceServerConfigIntegrationTest.java` — 3 tests.
- `ApplicationPropertiesSecurityConfigTest.java` — 4 tests.
- `ApplicationPropertiesJpaConfigTest.java` — 4 tests.

## Test manifest

| Test method | Verifies | AC / requirement |
|---|---|---|
| `EmailPropertiesTest.bindsFromTheRealPrefixAndKeyNames` | Real Spring binding from `themistra.notification.email.*`, not just constructor logic (self-review Finding 1) | AC1 |
| `EmailPropertiesTest.failsWhenFromIsBlank` / `failsWhenTransportIsBlank` | `@NotBlank` enforced via real binding | AC1 |
| `InappPropertiesTest.bindsFromTheRealPrefixAndKeyName` / `failsWhenTransportIsBlank` | Same, `themistra.notification.inapp.transport` | AC1 |
| `RetryPropertiesTest.bindsFromTheRealPrefixAndKeyNames` | Real binding, all 3 fields | AC1 |
| `RetryPropertiesTest.failsWhenMaxAttemptsIsNonPositive` / `failsWhenInitialBackoffIsNonPositive` | `@Min(1)` enforced via real binding | AC1 |
| `RetryPropertiesTest.failsWhenMaxBackoffIsBelowInitialBackoff` | Cross-field check (Kimi Phase 3 Finding #5) | AC1 |
| `RetryPropertiesTest.succeedsWhenMaxBackoffEqualsInitialBackoff` | Boundary: `>=` allowed, not just `>` | AC1 |
| `LinkPropertiesTest.constructsWithNullOrBlankBaseUrl` | Record itself is deliberately unconstrained | AC1, AC5 |
| `LinkPropertiesTest.bindsFromTheRealPrefixAndKeyName` / `bindsSuccessfullyWithBlankValue...` | Real binding, both non-blank and blank cases succeed at the record level | AC1 |
| `LinkPropertiesStartupValidationTest.localProfileBootsCleanWithNoBaseUrl` | `local` profile: validator not created, no failure | AC5 |
| `LinkPropertiesStartupValidationTest.nonLocalProfileFailsWithNoBaseUrl` | `dev` profile + blank URL: fails with the intended `IllegalStateException` (Kimi Phase 8 Finding #4) | AC5 |
| `LinkPropertiesStartupValidationTest.nonLocalProfileBootsCleanWhenBaseUrlIsSet` | `dev` profile + real URL: no failure | AC5 |
| `LinkPropertiesStartupValidationTest.stagingAndProdProfilesAlsoFailWithNoBaseUrl` | `staging`/`prod` also covered, not just `dev` | AC5 |
| `PublicEndpointsTest.patternsListExactlyTheThreeDeclaredPaths` | Static: exactly 3 patterns, no more/fewer (Kimi Phase 8 Finding #2) | AC4 |
| `PublicEndpointsTest.declaredPublicPathsAreNotBlockedBySecurity` (×5) | Behavioral: the 3 declared paths (+2 health sub-paths) aren't blocked | AC4 |
| `PublicEndpointsTest.sensitiveActuatorPathsAreNotPublic` (×6) | Behavioral: `/actuator/env` etc. require auth | AC4 |
| `PublicEndpointsTest.nonPublicApplicationPathIsNotPublic` | An arbitrary non-actuator path is also not accidentally public | AC4 |
| `ResourceServerConfigIntegrationTest.unauthenticatedRequestToNonPublicPathIsRejected` | 401 + `WWW-Authenticate: Bearer` + `problem+json` body (Kimi Phase 8 Finding #3) | AC3 |
| `ResourceServerConfigIntegrationTest.authenticatedJwtRequestToNonPublicPathIsAccepted` | Valid JWT → 2xx | AC3 |
| `ResourceServerConfigIntegrationTest.nonBearerAuthorizationSchemeIsRejected` | Basic-scheme header treated as anonymous, still rejected | AC3 |
| `ApplicationPropertiesSecurityConfigTest.declaresJwkSetUriAndIssuerUri` | Committed keys present (Kimi Phase 8 Finding #5) | AC2 |
| `...exposesExactlyHealthInfoAndPrometheusOverActuator` | Actuator exposure keys correct | AC2 |
| `...declaresAllFourNotificationConfigGroupKeys` | All 8 `themistra.notification.*` keys present | AC2 |
| `...declaresAFreeServerPortAwayFromAuthServices8080` | `server.port` set and not `8080` (Kimi Phase 8 Finding #6) | AC2 |
| `ApplicationPropertiesJpaConfigTest.ddlAutoIsValidateAndOpenInViewIsDisabled` | JPA keys correct | AC2 |
| `...flywayIsDisabledAtRuntime` | Flyway disabled at runtime | AC2 |
| `...datasourceUsesTheLeastPrivilegeRuntimeRole` | `notification_app`, `search_path` narrowed | AC2 |
| `...kafkaBootstrapServersIsDeclared` | Kafka key present | AC2 |

## Negative-proof (mutation testing)

Mutated `ResourceServerConfig`'s `.anyRequest().authenticated()` to `.anyRequest().permitAll()`,
re-ran `ResourceServerConfigIntegrationTest` + `PublicEndpointsTest` alone: 9 of 16 tests failed
immediately (both the direct authentication-rejection tests and, transitively, several
sensitive-actuator-path checks that depend on the same rule) — confirms these tests would catch a
real security regression, not just document intended behavior. Reverted; `git status -s` on
`ResourceServerConfig.java` empty afterward; full suite re-verified green.

## Verification

`mvn -pl services/notification -am verify` — 63 tests, 0 failures (9 `T01SkeletonRegressionTest` +
13 `NotificationBaselineMigrationIntegrationTest`, both unaffected, + 41 new). No production code
modified in this phase (the mutation above was reverted before the final verification run).

## Addendum (post Phase 11) — all 8 gaps accepted and added

Kimi's Phase 11 review raised 8 gaps. All 8 verified against the actual test/source files before
acting, all genuine, all added (63 tests → 67):

- **Gap #1** (`spring.profiles.active=local` unguarded) — added `activeProfileDefaultsToLocal` to
  `ApplicationPropertiesSecurityConfigTest`.
- **Gap #2** (`management.endpoint.health.show-details=never` unasserted) — folded into
  `exposesExactlyHealthInfoAndPrometheusOverActuator`.
- **Gap #3** (`PublicEndpointsTest` not a true static sweep) — added
  `resourceServerConfigContainsNoPermitAllOutsidePublicEndpoints`, reading `ResourceServerConfig.java`
  as text and asserting exactly one `.permitAll()` call, scoped to `PublicEndpoints.PATTERNS`. A real
  mutation test (adding a second, forgotten `.permitAll()` path, rerunning `PublicEndpointsTest`)
  confirmed this specific new check catches it while the 13 pre-existing behavioral tests in the same
  class do not — exactly the gap in kind Kimi's own finding described. Reverted clean afterward.
- **Gap #4** (staging/prod loop only asserted `hasFailed()`) — tightened to the same
  `IllegalStateException` + message-fragment assertions the `dev` test already used.
- **Gap #5** (no-active-profile default case untested) — added
  `defaultProfileWithNoActiveProfileAlsoFailsWithNoBaseUrl`.
- **Gap #6** (`connection-init-sql` order unasserted) — tightened `.contains("notifications")` to an
  exact-value assertion (`SET search_path TO notifications, public`).
- **Gap #7** (403 handler untriggered, no coverage) — added
  `accessDeniedHandlerWritesRfc9457ProblemJson`, invoking `problemJsonAccessDeniedHandler` directly
  with a real `MockHttpServletRequest`/`MockHttpServletResponse` (Kimi's own preferred option (a) —
  immediate coverage over a TODO comment).
- **Gap #8** (password placeholder discipline unasserted) — added to
  `datasourceUsesTheLeastPrivilegeRuntimeRole`: asserts `spring.datasource.password` starts with
  `${DB_PASSWORD:` and ends with `}`.

**Verification:** `mvn -pl services/notification -am verify` — 67 tests, 0 failures (9 + 13 + 45
across the T03 test files, all previously-existing tests unaffected). A second mutation test (Gap #3's
own check) performed and reverted clean (`git status -s` on `ResourceServerConfig.java` empty
afterward).
